package com.linode.manager.data.ssh

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the user chose on the connect screen (secrets excluded). */
data class SshProfile(
    val linodeId: Int,
    val label: String,
    val host: String,
    val port: Int = 22,
    val username: String = "root",
    val useKey: Boolean = true,
    val keyFingerprint: String? = null,
    val tmuxName: String? = null,
    val autoReconnect: Boolean = true,
    // Separate field (not a "kind" enum) so profiles saved by 2.0.x, which
    // only have tmuxName, keep loading unchanged.
    val herdrName: String? = null,
) {
    /** Known-hosts identity: plain host on 22, host:port otherwise. */
    val hostId: String get() = if (port == 22) host else "$host:$port"

    /** The server-side session manager in use, if any (herdr wins if both set). */
    val multiplexer: Multiplexer?
        get() =
            when {
                herdrName != null -> Multiplexer.HERDR
                tmuxName != null -> Multiplexer.TMUX
                else -> null
            }

    val sessionName: String? get() = herdrName ?: tmuxName

    /** Channel command for this profile, or null for a plain login shell. */
    fun channelCommand(): String? = multiplexer?.channelCommand(sessionName!!)

    fun withSession(
        m: Multiplexer?,
        name: String?,
    ): SshProfile =
        copy(
            tmuxName = name.takeIf { m == Multiplexer.TMUX },
            herdrName = name.takeIf { m == Multiplexer.HERDR },
        )
}

sealed interface SshStatus {
    data object Connecting : SshStatus

    data object Connected : SshStatus

    data class HostKeyPrompt(
        val key: HostKeyInfo,
    ) : SshStatus

    data class Reconnecting(
        val attempt: Int,
        val inSeconds: Int,
        val reason: String,
    ) : SshStatus

    data class Disconnected(
        val message: String?,
        val isError: Boolean,
    ) : SshStatus
}

data class RemoteEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
)

data class UploadProgress(
    val fileName: String,
    val sent: Long,
    val total: Long,
    val done: Boolean = false,
    val error: String? = null,
)

/**
 * One terminal: an SSH connection, its pty channel and the libvterm
 * emulator that renders it. Lives in the app scope (not a screen), so
 * navigating away — or backgrounding the app — keeps it running.
 *
 * Threading: Compose state is mutated on Main only; transport work runs on
 * IO. Every connection attempt takes a new [SessionGate] generation so late
 * callbacks from a dead reader can never tear down a newer, healthy link.
 */
class TerminalSession(
    profile: SshProfile,
    private val auth: SshAuth,
    private val hostKeys: HostKeyStore,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
) {
    var profile by mutableStateOf(profile)
        private set
    var status by mutableStateOf<SshStatus>(SshStatus.Connecting)
        private set
    var connectionInfo by mutableStateOf<String?>(null)
        private set
    var upload by mutableStateOf<UploadProgress?>(null)
        private set
    val log = mutableStateListOf<String>()

    /** True while the transport is up or being re-established. */
    val isLive: Boolean
        get() = status is SshStatus.Connected || status is SshStatus.Connecting || status is SshStatus.Reconnecting

    private val gate = SessionGate()

    @Volatile private var client: SshClient? = null

    @Volatile private var shell: SshShell? = null

    @Volatile private var cols = 80

    @Volatile private var rows = 24
    private var hadSession = false

    /** False until the first successful login: failures then mean "fix the settings". */
    val everConnected: Boolean get() = hadSession
    private var userClosed = false
    private var attempt = 0
    private var reconnectJob: Job? = null
    private var keepAliveJob: Job? = null
    private var uploadJob: Job? = null

    @Volatile private var uploadCancelled = false
    private val outbox = Channel<ByteArray>(Channel.UNLIMITED)

    val emulator: TerminalEmulator =
        TerminalEmulatorFactory.create(
            initialRows = rows,
            initialCols = cols,
            defaultForeground = Color(TERM_FG),
            defaultBackground = Color(TERM_BG),
            onKeyboardInput = { bytes -> outbox.trySend(bytes) },
            onResize = { dims -> onTerminalResized(dims.columns, dims.rows) },
        )

    init {
        // Single writer: keystrokes reach the channel in order, never interleaved.
        scope.launch(Dispatchers.IO) {
            for (bytes in outbox) {
                val sh = shell ?: continue
                try {
                    sh.stdin.write(bytes)
                    sh.stdin.flush()
                } catch (_: Exception) {
                    // The reader notices the dead channel and drives reconnect.
                }
            }
        }
        connect()
    }

    fun note(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = "$ts $msg"
        scope.launch(Dispatchers.Main) {
            log.add(line)
            while (log.size > 300) log.removeAt(0)
        }
    }

    // ---------------------------------------------------------------- input

    /** Send raw bytes to the remote side (extra-keys bar, paste). */
    fun send(text: String) {
        outbox.trySend(text.toByteArray(Charsets.UTF_8))
    }

    private fun onTerminalResized(
        c: Int,
        r: Int,
    ) {
        if (c <= 0 || r <= 0 || (c == cols && r == rows)) return
        cols = c
        rows = r
        val sh = shell ?: return
        scope.launch(Dispatchers.IO) {
            try {
                sh.resize(c, r)
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------ lifecycle

    private fun updateStatus(s: SshStatus) {
        status = s
        onChanged()
    }

    private fun connect() {
        reconnectJob?.cancel()
        reconnectJob = null
        stopTransport()
        val gen = gate.next()
        userClosed = false
        updateStatus(SshStatus.Connecting)
        val p = profile
        val target = SshTarget(p.host, p.port, p.username, auth)
        val trusted = hostKeys.get(p.hostId)
        scope.launch(Dispatchers.IO) {
            try {
                val c = SshClient.connect(target, trusted, log = ::note)
                val command = p.channelCommand()
                val sh =
                    try {
                        c.openShell(cols, rows, command)
                    } catch (e: Exception) {
                        c.close()
                        throw e
                    }
                withContext(Dispatchers.Main) {
                    if (!gate.isCurrent(gen)) {
                        sh.close()
                        c.close()
                        return@withContext
                    }
                    client = c
                    shell = sh
                    connectionInfo = c.info
                    note("shell open ${cols}x$rows" + (p.multiplexer?.let { " (${it.label}: ${p.sessionName})" } ?: ""))
                    if (hadSession) {
                        emulator.writeInput("\r\n\u001b[2m── reconnected ──\u001b[0m\r\n".toByteArray())
                    }
                    hadSession = true
                    attempt = 0
                    updateStatus(SshStatus.Connected)
                    startReader(gen, sh)
                    startKeepAlive(gen, sh, c)
                    syncPtySize(sh)
                }
            } catch (e: HostKeyUnknownException) {
                note("unknown host key ${e.presented.fingerprint}")
                onMain(gen) { updateStatus(SshStatus.HostKeyPrompt(e.presented)) }
            } catch (e: HostKeyChangedException) {
                note("HOST KEY CHANGED: now ${e.presented.fingerprint}, trusted ${e.trustedFingerprint}")
                onMain(gen) { updateStatus(SshStatus.Disconnected(SshClient.friendly(e), isError = true)) }
            } catch (e: SshAuthException) {
                note("auth failed: ${e.message}")
                onMain(gen) { updateStatus(SshStatus.Disconnected(SshClient.friendly(e), isError = true)) }
            } catch (e: Exception) {
                note("connect failed: $e")
                onMain(gen) { onLinkLost(SshClient.friendly(e)) }
            }
        }
    }

    /** The view may have resized while the channel was opening; catch up. */
    private fun syncPtySize(sh: SshShell) {
        val c = cols
        val r = rows
        scope.launch(Dispatchers.IO) {
            try {
                sh.resize(c, r)
            } catch (_: Exception) {
            }
        }
    }

    private fun onMain(
        gen: Int,
        block: () -> Unit,
    ) {
        scope.launch(Dispatchers.Main) { if (gate.isCurrent(gen)) block() }
    }

    private fun startReader(
        gen: Int,
        sh: SshShell,
    ) {
        val input: InputStream = sh.stdout
        scope.launch(Dispatchers.IO) {
            val buf = ByteArray(16 * 1024)
            // Per channel: libvterm drops SCO cursor save/restore (see class doc).
            val sco = ScoCursorTranslator()
            var rx = 0L
            var reason: String? = null
            try {
                while (isActive) {
                    val n = input.read(buf)
                    if (n < 0) break
                    rx += n
                    val out = sco.feed(buf, n)
                    if (out.isNotEmpty()) emulator.writeInput(out, 0, out.size)
                }
            } catch (e: Exception) {
                reason = SshClient.friendly(e)
            }
            val linkAlive = client?.isConnected == true
            val exit = if (linkAlive) sh.awaitExitStatus(1500) else null
            note("channel closed rx=${rx}B exit=$exit link=${if (linkAlive) "up" else "down"}")
            onMain(gen) {
                when {
                    userClosed -> Unit
                    // The remote shell exited (user typed `exit`): a normal end.
                    exit != null && client?.isConnected == true -> {
                        stopTransport()
                        updateStatus(SshStatus.Disconnected("Session ended (exit status $exit).", isError = false))
                    }
                    else -> onLinkLost(reason ?: client?.lostReason?.let { SshClient.friendly(it) } ?: "Connection lost.")
                }
            }
        }
    }

    /**
     * Mobile links die silently (NAT timeouts, Wi-Fi ↔ cellular). Probe the
     * channel every 15 s; no answer within 12 s means the link is gone —
     * close it so the reader wakes up and reconnect kicks in.
     */
    private fun startKeepAlive(
        gen: Int,
        sh: SshShell,
        c: SshClient,
    ) {
        keepAliveJob?.cancel()
        keepAliveJob =
            scope.launch(Dispatchers.IO) {
                while (isActive && gate.isCurrent(gen)) {
                    delay(KEEPALIVE_MS)
                    probe(sh, c)
                }
            }
    }

    private suspend fun probe(
        sh: SshShell,
        c: SshClient,
    ) {
        val ping =
            scope.async(Dispatchers.IO) {
                try {
                    sh.ping()
                    true
                } catch (_: Exception) {
                    false
                }
            }
        val ok = withTimeoutOrNull(PING_TIMEOUT_MS) { ping.await() }
        if (ok != true) {
            note("keepalive failed (${if (ok == null) "no reply" else "error"}) — closing link")
            c.close()
        }
    }

    /** Called when the device gets a (new) network. */
    fun onNetworkAvailable() {
        when (status) {
            is SshStatus.Reconnecting -> {
                note("network available — retrying now")
                connect()
            }
            is SshStatus.Connected -> {
                val sh = shell
                val c = client
                if (sh != null && c != null) scope.launch(Dispatchers.IO) { probe(sh, c) }
            }
            else -> Unit
        }
    }

    private fun onLinkLost(reason: String) {
        stopTransport()
        if (userClosed) return
        if (!hadSession || !profile.autoReconnect || attempt >= MAX_ATTEMPTS) {
            updateStatus(SshStatus.Disconnected(reason, isError = true))
            return
        }
        attempt++
        val wait = reconnectDelaySec(attempt).toInt()
        note("reconnect #$attempt in ${wait}s ($reason)")
        reconnectJob =
            scope.launch(Dispatchers.Main) {
                for (left in wait downTo 1) {
                    updateStatus(SshStatus.Reconnecting(attempt, left, reason))
                    delay(1000)
                }
                connect()
            }
    }

    private fun stopTransport() {
        keepAliveJob?.cancel()
        keepAliveJob = null
        val sh = shell
        val c = client
        shell = null
        client = null
        if (sh != null || c != null) {
            scope.launch(Dispatchers.IO) {
                sh?.close()
                c?.close()
            }
        }
    }

    // ------------------------------------------------------- user actions

    fun trustHostKey() {
        val st = status as? SshStatus.HostKeyPrompt ?: return
        hostKeys.put(profile.hostId, st.key.storeValue())
        note("trusted host key ${st.key.fingerprint} for ${profile.hostId}")
        connect()
    }

    fun rejectHostKey() {
        userClosed = true
        updateStatus(SshStatus.Disconnected("Host key not trusted.", isError = false))
    }

    fun retryNow() {
        attempt = 0
        note("manual reconnect")
        connect()
    }

    fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        userClosed = true
        updateStatus(SshStatus.Disconnected("Reconnect cancelled.", isError = false))
    }

    /** Drop the connection but keep the screen (can reconnect later). */
    fun disconnect() {
        userClosed = true
        gate.next()
        reconnectJob?.cancel()
        reconnectJob = null
        uploadJob?.cancel()
        stopTransport()
        note("disconnected by user")
        updateStatus(SshStatus.Disconnected("Disconnected.", isError = false))
    }

    /** Final teardown; the session object is discarded afterwards. */
    fun close() {
        disconnect()
        outbox.close()
    }

    fun setAutoReconnect(enabled: Boolean) {
        profile = profile.copy(autoReconnect = enabled)
        if (!enabled && status is SshStatus.Reconnecting) cancelReconnect()
    }

    /**
     * Switch the terminal to a session of [kind] (or a plain shell when
     * [name] is null) by reopening the channel on the live connection.
     */
    fun switchSession(
        kind: Multiplexer,
        name: String?,
    ) {
        val clean = name?.let { sanitizeTmuxName(it) }?.ifBlank { null }
        profile = profile.withSession(kind, clean)
        note("switch to ${clean?.let { "${kind.label} '$it'" } ?: "plain shell"}")
        emulator.clearScreen()
        if (client?.isConnected == true) reopenChannel() else connect()
    }

    private fun reopenChannel() {
        val c = client ?: return connect()
        keepAliveJob?.cancel()
        val old = shell
        shell = null
        val gen = gate.next()
        updateStatus(SshStatus.Connecting)
        val command = profile.channelCommand()
        scope.launch(Dispatchers.IO) {
            old?.close()
            try {
                val sh = c.openShell(cols, rows, command)
                onMain(gen) {
                    shell = sh
                    updateStatus(SshStatus.Connected)
                    startReader(gen, sh)
                    startKeepAlive(gen, sh, c)
                    syncPtySize(sh)
                }
            } catch (e: Exception) {
                note("reopen failed: $e")
                onMain(gen) { onLinkLost(SshClient.friendly(e)) }
            }
        }
    }

    /** Detach from the multiplexer (it keeps running); drops to a login shell. */
    fun detachSession() {
        val m = profile.multiplexer ?: return
        send(m.detachKeys)
        note("${m.label} detached (session keeps running on the server)")
        profile = profile.withSession(null, null)
    }

    suspend fun listSessions(kind: Multiplexer): Result<List<RemoteSession>> =
        withContext(Dispatchers.IO) {
            val c = client ?: return@withContext Result.failure(IllegalStateException("Not connected"))
            runCatching { kind.parseSessions(c.exec(kind.listCommand)) }
        }

    // ------------------------------------------------------------- SFTP

    private fun <T> withSftp(block: (com.trilead.ssh2.SFTPv3Client) -> T): T {
        val c = client ?: throw IllegalStateException("Not connected — reconnect first.")
        val sftp = c.sftp()
        try {
            return block(sftp)
        } finally {
            sftp.close()
        }
    }

    suspend fun remoteHome(): String =
        withContext(Dispatchers.IO) {
            try {
                withSftp { it.canonicalPath(".") }
            } catch (_: Exception) {
                "/root"
            }
        }

    suspend fun listRemote(path: String): Result<List<RemoteEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                withSftp { sftp ->
                    sftp
                        .ls(path)
                        .filter { it.filename != "." && it.filename != ".." }
                        .map {
                            RemoteEntry(
                                name = it.filename,
                                path = path.trimEnd('/') + "/" + it.filename,
                                isDir = it.attributes?.isDirectory == true,
                                size = it.attributes?.size ?: 0L,
                                modified = (it.attributes?.mtime ?: 0L) * 1000L,
                            )
                        }.sortedWith(compareByDescending<RemoteEntry> { it.isDir }.thenBy { it.name.lowercase() })
                }
            }.recoverCatching { throw Exception(friendlySftp(it)) }
        }

    suspend fun makeRemoteDir(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { withSftp { it.mkdir(path, 493) } }.recoverCatching { throw Exception(friendlySftp(it)) }
        }

    fun startUpload(
        fileName: String,
        size: Long,
        remotePath: String,
        open: () -> InputStream,
    ) {
        uploadJob?.cancel()
        uploadCancelled = false
        upload = UploadProgress(fileName, 0, size)
        note("upload $fileName (${size}B) → $remotePath")
        uploadJob =
            scope.launch(Dispatchers.IO) {
                var sent = 0L
                var lastEmit = 0L
                val result =
                    runCatching {
                        open().use { input ->
                            withSftp { sftp ->
                                val h = sftp.createFileTruncate(remotePath)
                                try {
                                    val buf = ByteArray(32 * 1024)
                                    while (true) {
                                        if (uploadCancelled) throw Exception("Upload cancelled.")
                                        val n = input.read(buf)
                                        if (n < 0) break
                                        sftp.write(h, sent, buf, 0, n)
                                        sent += n
                                        val now = System.currentTimeMillis()
                                        if (now - lastEmit > 150) {
                                            lastEmit = now
                                            val s = sent
                                            scope.launch(Dispatchers.Main) { upload = upload?.copy(sent = s) }
                                        }
                                    }
                                } finally {
                                    sftp.closeFile(h)
                                }
                            }
                        }
                    }
                withContext(Dispatchers.Main) {
                    upload =
                        result.fold(
                            onSuccess = {
                                note("upload complete: $remotePath")
                                UploadProgress(fileName, sent, maxOf(size, sent), done = true)
                            },
                            onFailure = {
                                val msg = if (uploadCancelled) "Upload cancelled." else friendlySftp(it)
                                note("upload failed: $msg")
                                UploadProgress(fileName, sent, size, error = msg)
                            },
                        )
                }
            }
    }

    fun cancelUpload() {
        uploadCancelled = true
    }

    fun clearUpload() {
        if (upload?.let { it.done || it.error != null } == true) upload = null
    }

    companion object {
        const val TERM_BG = 0xFF0D1117
        const val TERM_FG = 0xFFD7DEE6
        private const val KEEPALIVE_MS = 15_000L
        private const val PING_TIMEOUT_MS = 12_000L
        private const val MAX_ATTEMPTS = 30

        fun friendlySftp(e: Throwable): String {
            val m = e.message ?: e.toString()
            return when {
                m.contains("No such file", true) -> "That remote path doesn't exist."
                m.contains("Permission denied", true) -> "Permission denied on the server."
                m.contains("Failure", true) -> "The server refused (does it already exist?)."
                else -> m.take(300)
            }
        }
    }
}
