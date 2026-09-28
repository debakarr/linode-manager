package com.linode.manager.data.ssh

import com.trilead.ssh2.ChannelCondition
import com.trilead.ssh2.Connection
import com.trilead.ssh2.InteractiveCallback
import com.trilead.ssh2.SFTPv3Client
import com.trilead.ssh2.ServerHostKeyVerifier
import com.trilead.ssh2.Session
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * SSH transport built on ConnectBot's sshlib — the library behind the most
 * widely used Android SSH client. It ships its own ciphers (ChaCha20-Poly1305,
 * AES-GCM/CTR, EtM MACs) and key exchange (curve25519, ML-KEM hybrid), so
 * there is no provider-dependent crypto to glue together or pin.
 *
 * Everything here is blocking and plain JVM: callers run it on an IO thread.
 * It is covered end to end by `SshClientTest` against a real OpenSSH server.
 */

sealed interface SshAuth {
    data class Key(
        val privatePem: String,
    ) : SshAuth

    data class Password(
        val password: String,
    ) : SshAuth
}

data class SshTarget(
    val host: String,
    val port: Int,
    val username: String,
    val auth: SshAuth,
)

/** A server host key as presented during key exchange. */
class HostKeyInfo(
    val algorithm: String,
    val blob: ByteArray,
) {
    val base64: String = Base64.getEncoder().encodeToString(blob)
    val fingerprint: String = fingerprintOf(blob)

    /** Persisted as `"<algorithm> <base64>"`, same shape as known_hosts. */
    fun storeValue(): String = "$algorithm $base64"

    /**
     * Compares key material only. The algorithm label differs between
     * clients for the same key (`ssh-rsa` vs `rsa-sha2-512`), and older app
     * versions stored JSch's label, so the blob is the only stable identity.
     */
    fun matches(stored: String): Boolean {
        val parts = stored.trim().split(Regex("\\s+"))
        val storedBlob = if (parts.size >= 2) parts[1] else parts[0]
        return storedBlob == base64
    }

    companion object {
        fun fingerprintOf(blob: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(blob)
            return "SHA256:" + Base64.getEncoder().encodeToString(digest).trimEnd('=')
        }

        /** Fingerprint of a stored `"<algorithm> <base64>"` value, or null. */
        fun fingerprintOfStored(stored: String): String? =
            try {
                val parts = stored.trim().split(Regex("\\s+"))
                fingerprintOf(Base64.getDecoder().decode(if (parts.size >= 2) parts[1] else parts[0]))
            } catch (_: Exception) {
                null
            }
    }
}

/** The server presented a key we have never seen: ask the user, then retry. */
class HostKeyUnknownException(
    val presented: HostKeyInfo,
) : IOException("Unknown host key ${presented.fingerprint}")

/** The server's key differs from the trusted one: possible MITM, refuse. */
class HostKeyChangedException(
    val presented: HostKeyInfo,
    val trustedFingerprint: String?,
) : IOException("Host key changed (now ${presented.fingerprint})")

class SshAuthException(
    message: String,
) : IOException(message)

class SshClient private constructor(
    private val connection: Connection,
    val info: String,
) : Closeable {
    @Volatile private var closed = false

    @Volatile var lostReason: Throwable? = null
        private set

    val isConnected: Boolean get() = !closed && lostReason == null

    init {
        connection.addConnectionMonitor { reason -> lostReason = reason ?: IOException("Connection lost") }
    }

    /**
     * Open an interactive channel with a pty. With [command] the channel runs
     * that command (used for tmux) instead of the login shell.
     */
    fun openShell(
        cols: Int,
        rows: Int,
        command: String? = null,
    ): SshShell {
        val s = connection.openSession()
        try {
            s.requestPTY("xterm-256color", cols.coerceAtLeast(1), rows.coerceAtLeast(1), 0, 0, null)
            if (command != null) s.execCommand(command) else s.startShell()
        } catch (e: Exception) {
            s.close()
            throw e
        }
        return SshShell(s)
    }

    /** Run a one-shot command without a pty and return stdout + stderr. */
    fun exec(
        command: String,
        timeoutMs: Long = 15_000,
    ): String {
        val s = connection.openSession()
        try {
            s.execCommand(command)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            val deadline = System.currentTimeMillis() + timeoutMs
            val streams = listOf(s.stdout, s.stderr)
            while (true) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) throw IOException("Command timed out")
                val cond =
                    s.waitForCondition(
                        ChannelCondition.STDOUT_DATA or ChannelCondition.STDERR_DATA or ChannelCondition.EOF,
                        left,
                    )
                if (cond and ChannelCondition.TIMEOUT != 0) throw IOException("Command timed out")
                var drained = false
                for (st in streams) {
                    while (st.available() > 0) {
                        val n = st.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        drained = true
                    }
                }
                if (!drained && cond and ChannelCondition.EOF != 0) break
            }
            return out.toString(Charsets.UTF_8.name())
        } finally {
            s.close()
        }
    }

    /** A new SFTP subsystem channel on this connection. Caller closes it. */
    fun sftp(): SFTPv3Client = SFTPv3Client(connection)

    override fun close() {
        closed = true
        try {
            connection.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        /**
         * Ciphers we offer, verified end to end against OpenSSH with
         * Android's JCE provider order (Conscrypt first) by
         * ConscryptCipherMatrixTest. chacha20-poly1305 is deliberately
         * absent: sshlib takes raw "ChaCha20" from the first provider, and
         * Conscrypt's variant doesn't match SSH's construction — every
         * packet fails MAC verification. AES-GCM/CTR go through the same
         * Conscrypt code paths as HTTPS and are hardware-accelerated.
         */
        val PREFERRED_CIPHERS: List<String> =
            listOf(
                "aes256-gcm@openssh.com",
                "aes128-gcm@openssh.com",
                "aes256-ctr",
                "aes128-ctr",
            )

        /**
         * Set after a post-quantum (ML-KEM hybrid) key exchange fails on this
         * runtime. sshlib picks its ML-KEM backend from whatever JCE
         * providers are installed, and a mismatched provider breaks the
         * handshake ("ML-KEM decapsulation failed"). curve25519 is still
         * secure and universally supported, so fall back rather than fail.
         */
        @Volatile var postQuantumDisabled = false
            private set

        /**
         * Connect, verify the host key against [trustedKey] (`null` = never
         * seen), and authenticate. Throws [HostKeyUnknownException],
         * [HostKeyChangedException], [SshAuthException] or IO errors.
         */
        fun connect(
            target: SshTarget,
            trustedKey: String?,
            connectTimeoutMs: Int = 15_000,
            kexTimeoutMs: Int = 30_000,
            log: (String) -> Unit = {},
            ciphers: List<String> = PREFERRED_CIPHERS,
        ): SshClient =
            try {
                connectOnce(target, trustedKey, connectTimeoutMs, kexTimeoutMs, postQuantumDisabled, ciphers, log)
            } catch (e: IOException) {
                val pqFailure =
                    !postQuantumDisabled &&
                        generateSequence<Throwable>(e) { it.cause }.any { it.message?.contains("ML-KEM", ignoreCase = true) == true }
                if (!pqFailure) throw e
                log("post-quantum key exchange failed on this device (${e.cause?.message}); retrying with curve25519")
                postQuantumDisabled = true
                connectOnce(target, trustedKey, connectTimeoutMs, kexTimeoutMs, true, ciphers, log)
            }

        private fun connectOnce(
            target: SshTarget,
            trustedKey: String?,
            connectTimeoutMs: Int,
            kexTimeoutMs: Int,
            classicKexOnly: Boolean,
            ciphers: List<String>,
            log: (String) -> Unit,
        ): SshClient {
            val conn = Connection(target.host, target.port)
            conn.setClient2ServerCiphers(ciphers.toTypedArray())
            conn.setServer2ClientCiphers(ciphers.toTypedArray())
            if (classicKexOnly) {
                conn.setKeyExchangeAlgorithms(
                    com.trilead.ssh2.transport.KexManager
                        .getDefaultKexAlgorithmList()
                        .filterNot { it.contains("mlkem", true) || it.contains("sntrup", true) }
                        .toTypedArray(),
                )
            }
            var presented: HostKeyInfo? = null
            val verifier =
                ServerHostKeyVerifier { _, _, algorithm, key ->
                    val hk = HostKeyInfo(algorithm, key)
                    presented = hk
                    trustedKey != null && hk.matches(trustedKey)
                }
            val info =
                try {
                    log("connecting to ${target.host}:${target.port}")
                    conn.connect(verifier, connectTimeoutMs, kexTimeoutMs)
                } catch (e: Exception) {
                    conn.close()
                    val hk = presented
                    if (hk != null && !(trustedKey != null && hk.matches(trustedKey))) {
                        if (trustedKey == null) throw HostKeyUnknownException(hk)
                        throw HostKeyChangedException(hk, HostKeyInfo.fingerprintOfStored(trustedKey))
                    }
                    throw e
                }
            val summary =
                "kex=${info.keyExchangeAlgorithm} hostkey=${info.serverHostKeyAlgorithm} " +
                    "cipher=${info.clientToServerCryptoAlgorithm} mac=${info.clientToServerMACAlgorithm}"
            log(summary)
            try {
                authenticate(conn, target, log)
            } catch (e: Exception) {
                conn.close()
                throw e
            }
            log("authenticated as ${target.username}")
            return SshClient(conn, summary)
        }

        private fun authenticate(
            conn: Connection,
            target: SshTarget,
            log: (String) -> Unit,
        ) {
            val user = target.username
            val ok =
                when (val a = target.auth) {
                    is SshAuth.Key -> {
                        log("auth: publickey")
                        conn.authenticateWithPublicKey(user, parseKeyPair(a.privatePem))
                    }
                    is SshAuth.Password -> {
                        var done = false
                        if (conn.isAuthMethodAvailable(user, "password")) {
                            log("auth: password")
                            done = conn.authenticateWithPassword(user, a.password)
                        }
                        if (!done && conn.isAuthMethodAvailable(user, "keyboard-interactive")) {
                            log("auth: keyboard-interactive")
                            done =
                                conn.authenticateWithKeyboardInteractive(
                                    user,
                                    InteractiveCallback { _, _, n, _, _ ->
                                        Array(n) { a.password }
                                    },
                                )
                        }
                        done
                    }
                }
            if (!ok) {
                val remaining =
                    try {
                        conn.getRemainingAuthMethods(user).joinToString(", ")
                    } catch (_: Exception) {
                        ""
                    }
                val hint =
                    when {
                        target.auth is SshAuth.Key ->
                            "The server rejected this key. Add its public key to ~/.ssh/authorized_keys for '$user'."
                        remaining.isNotBlank() && !remaining.contains("password") && !remaining.contains("keyboard") ->
                            "Password login is disabled on this server — use a key."
                        else -> "Wrong username or password."
                    }
                throw SshAuthException(
                    "Authentication failed. $hint" + if (remaining.isNotBlank()) " (Server accepts: $remaining)" else "",
                )
            }
        }

        /**
         * Device keys are stored as unencrypted PKCS#8 PEM, which sshlib's
         * PEM decoder does not read, so build the KeyPair ourselves.
         */
        fun parseKeyPair(pem: String): KeyPair {
            val body =
                pem
                    .lines()
                    .filterNot { it.startsWith("-----") }
                    .joinToString("")
                    .trim()
            val der = Base64.getMimeDecoder().decode(body)
            val kf = KeyFactory.getInstance("RSA")
            val priv =
                kf.generatePrivate(PKCS8EncodedKeySpec(der)) as? RSAPrivateCrtKey
                    ?: throw IOException("Unsupported private key (expected RSA)")
            val pub = kf.generatePublic(RSAPublicKeySpec(priv.modulus, priv.publicExponent))
            return KeyPair(pub, priv)
        }

        /** Turn transport exceptions into something a person can act on. */
        fun friendly(e: Throwable): String {
            if (e is SshAuthException) return e.message ?: "Authentication failed."
            if (e is HostKeyChangedException) {
                return "The host key for this server changed (now ${e.presented.fingerprint}). " +
                    "This can mean a man-in-the-middle attack — or the Linode was rebuilt. Connection refused."
            }
            val chain = generateSequence(e) { it.cause }.take(6).toList()
            val all = chain.joinToString(" | ") { "${it.javaClass.simpleName}: ${it.message}" }
            return when {
                chain.any { it is java.net.UnknownHostException } ->
                    "Can't resolve the host — check the address and your connection."
                chain.any { it is java.net.ConnectException } || all.contains("refused", true) ->
                    "Connection refused — is sshd running and port reachable? Check the Linode's firewall."
                chain.any { it is java.net.NoRouteToHostException } || all.contains("unreachable", true) ->
                    "Network unreachable — are you online?"
                chain.any { it is java.net.SocketTimeoutException } ||
                    all.contains("timeout", true) ||
                    all.contains("timed out", true) ->
                    "Connection timed out — the Linode may be offline or blocked by a firewall."
                else -> (chain.lastOrNull()?.message ?: e.message ?: e.toString()).take(300)
            }
        }
    }
}

/** An interactive channel with a pty. */
class SshShell internal constructor(
    private val session: Session,
) : Closeable {
    val stdout: InputStream get() = session.stdout
    val stdin: OutputStream get() = session.stdin

    fun resize(
        cols: Int,
        rows: Int,
    ) {
        session.resizePTY(cols.coerceAtLeast(1), rows.coerceAtLeast(1), 0, 0)
    }

    /**
     * Round-trip a channel request to the server. Blocks until the server
     * answers; callers run it under a watchdog to detect dead links.
     */
    fun ping() = session.ping()

    val exitStatus: Int? get() = session.exitStatus

    /**
     * The exit-status message can trail the channel EOF by a moment; wait
     * briefly so a normal `exit` isn't mistaken for a dropped connection.
     */
    fun awaitExitStatus(timeoutMs: Long): Int? {
        session.exitStatus?.let { return it }
        try {
            session.waitForCondition(ChannelCondition.EXIT_STATUS or ChannelCondition.CLOSED, timeoutMs)
        } catch (_: Exception) {
        }
        return session.exitStatus
    }

    override fun close() {
        try {
            session.close()
        } catch (_: Exception) {
        }
    }
}
