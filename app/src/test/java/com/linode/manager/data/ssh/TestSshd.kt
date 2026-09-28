package com.linode.manager.data.ssh

import com.linode.manager.data.SshKeyGen
import org.junit.Assume
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files

/**
 * A throwaway OpenSSH server run as the current user on a random localhost
 * port, trusting one freshly generated device key. Tests are skipped when
 * sshd isn't installed.
 */
object TestSshd {
    private var proc: Process? = null
    var port = 0
        private set
    lateinit var dir: File
        private set
    lateinit var hostKeyStore: String
        private set
    lateinit var hostFingerprint: String
        private set
    lateinit var devicePem: String
        private set
    val user: String = System.getProperty("user.name")

    fun start() {
        val sshd = File("/usr/sbin/sshd")
        val keygen = listOf("/usr/bin/ssh-keygen", "/bin/ssh-keygen").map(::File).firstOrNull { it.exists() }
        Assume.assumeTrue("sshd not installed", sshd.exists() && keygen != null)
        dir = Files.createTempDirectory("sshtest").toFile()
        val hostKey = File(dir, "host_ed25519")
        run(keygen!!.path, "-q", "-t", "ed25519", "-N", "", "-f", hostKey.path)
        val pub = File(dir, "host_ed25519.pub").readText().trim().split(" ")
        hostKeyStore = "${pub[0]} ${pub[1]}"
        hostFingerprint = run(keygen.path, "-l", "-E", "sha256", "-f", hostKey.path + ".pub").split(" ")[1]

        val key = SshKeyGen.generateRsa("test-device")
        devicePem = key.privatePem
        File(dir, "authorized_keys").writeText(key.publicKey + "\n")

        port = ServerSocket(0).use { it.localPort }
        val sftp =
            listOf("/usr/lib/openssh/sftp-server", "/usr/libexec/openssh/sftp-server", "/usr/libexec/sftp-server")
                .firstOrNull { File(it).exists() } ?: "internal-sftp"
        val cfg = File(dir, "sshd_config")
        cfg.writeText(
            """
            Port $port
            ListenAddress 127.0.0.1
            HostKey ${hostKey.path}
            PidFile ${dir.path}/sshd.pid
            AuthorizedKeysFile ${dir.path}/authorized_keys
            StrictModes no
            UsePAM no
            PasswordAuthentication no
            KbdInteractiveAuthentication no
            PubkeyAuthentication yes
            Subsystem sftp $sftp
            """.trimIndent() + "\n",
        )
        proc =
            ProcessBuilder(sshd.path, "-D", "-e", "-f", cfg.path)
                .redirectErrorStream(true)
                .redirectOutput(File(dir, "sshd.log"))
                .start()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200) }
                return
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
        Assume.assumeTrue("sshd did not start: " + File(dir, "sshd.log").readText(), false)
    }

    fun stop() {
        proc?.destroy()
        proc = null
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    private fun run(vararg cmd: String): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out.trim()
    }
}
