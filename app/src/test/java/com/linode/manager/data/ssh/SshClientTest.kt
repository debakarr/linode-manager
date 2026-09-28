package com.linode.manager.data.ssh

import com.linode.manager.data.SshKeyGen
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.net.ServerSocket

/**
 * End-to-end tests against a real, throwaway OpenSSH server started as the
 * current user on a random localhost port. Skipped when sshd is absent.
 */
class SshClientTest {
    companion object {
        private val port get() = TestSshd.port
        private val dir get() = TestSshd.dir
        private val hostKeyStore get() = TestSshd.hostKeyStore
        private val hostFingerprint get() = TestSshd.hostFingerprint
        private val devicePem get() = TestSshd.devicePem
        private val user get() = TestSshd.user

        @BeforeClass
        @JvmStatic
        fun startSshd() = TestSshd.start()

        @AfterClass
        @JvmStatic
        fun stopSshd() = TestSshd.stop()
    }

    private fun target(pem: String = devicePem) = SshTarget("127.0.0.1", port, user, SshAuth.Key(pem))

    private fun connect(): SshClient = SshClient.connect(target(), hostKeyStore)

    private fun readUntil(
        shell: SshShell,
        marker: String,
        timeoutMs: Long = 10_000,
    ): String {
        val sb = StringBuilder()
        val buf = ByteArray(4096)
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (shell.stdout.available() > 0) {
                val n = shell.stdout.read(buf)
                if (n < 0) break
                sb.append(String(buf, 0, n))
                if (sb.contains(marker)) return sb.toString()
            } else {
                Thread.sleep(20)
            }
        }
        fail("Timed out waiting for '$marker'; got: $sb")
        return sb.toString()
    }

    @Test
    fun unknownHostKeyIsReportedWithFingerprint() {
        try {
            SshClient.connect(target(), trustedKey = null)
            fail("expected HostKeyUnknownException")
        } catch (e: HostKeyUnknownException) {
            assertEquals(hostFingerprint, e.presented.fingerprint)
            assertTrue(e.presented.matches(hostKeyStore))
        }
    }

    @Test
    fun changedHostKeyIsRefused() {
        val other = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIPaJgSs0RfcY8M3m6tr3JrWUdz7UPeq3oKc8M6qLv0nK"
        try {
            SshClient.connect(target(), trustedKey = other)
            fail("expected HostKeyChangedException")
        } catch (e: HostKeyChangedException) {
            assertEquals(hostFingerprint, e.presented.fingerprint)
            assertTrue(SshClient.friendly(e).contains("man-in-the-middle"))
        }
    }

    @Test
    fun legacyStoredKeyWithDifferentLabelStillMatches() {
        val blob = hostKeyStore.split(" ")[1]
        val info =
            HostKeyInfo(
                "ssh-ed25519",
                java.util.Base64
                    .getDecoder()
                    .decode(blob),
            )
        assertTrue(info.matches("ssh-ed25519 $blob"))
        assertTrue(info.matches(blob))
        assertFalse(info.matches("ssh-ed25519 AAAA"))
    }

    @Test
    fun wrongKeyFailsAuthWithHelpfulMessage() {
        val other = SshKeyGen.generateRsa("stranger").privatePem
        try {
            SshClient.connect(target(other), hostKeyStore)
            fail("expected SshAuthException")
        } catch (e: SshAuthException) {
            assertTrue(e.message!!, e.message!!.contains("authorized_keys"))
        }
    }

    @Test
    fun interactiveShellRoundTripsAndResizes() {
        connect().use { c ->
            assertTrue(c.info, c.info.contains("cipher="))
            val shell = c.openShell(80, 24)
            shell.stdin.write("stty size; echo MARK-\$((6*7))\r".toByteArray())
            shell.stdin.flush()
            val out = readUntil(shell, "MARK-42")
            assertTrue(out, out.contains("24 80"))
            shell.resize(132, 40)
            shell.stdin.write("stty size; echo DONE-\$((2+3))\r".toByteArray())
            shell.stdin.flush()
            val out2 = readUntil(shell, "DONE-5")
            assertTrue(out2, out2.contains("40 132"))
            shell.ping()
            shell.close()
        }
    }

    @Test
    fun tmuxChannelCommandAlwaysYieldsAShell() {
        connect().use { c ->
            val shell = c.openShell(80, 24, tmuxChannelCommand("lmtest"))
            // With tmux: attached session; without: fallback banner + login shell.
            shell.stdin.write("echo TMUX-\$((5*5))\r".toByteArray())
            shell.stdin.flush()
            readUntil(shell, "TMUX-25")
            shell.close()
            try {
                c.exec("tmux kill-session -t lmtest 2>/dev/null; true")
            } catch (_: Exception) {
            }
        }
    }

    @Test
    fun herdrMissingFallsBackToAShell() {
        connect().use { c ->
            // Point HOME (and so the PATH herdr is found on) somewhere empty.
            val shell = c.openShell(80, 24, "HOME=/nonexistent; PATH=/usr/bin:/bin; " + herdrChannelCommand("lmtest"))
            val out = readUntil(shell, "normal shell")
            assertTrue(out, out.contains("herdr is not installed"))
            shell.stdin.write("echo HERDR-\$((4*4))\r".toByteArray())
            shell.stdin.flush()
            readUntil(shell, "HERDR-16")
            shell.close()
        }
    }

    @Test
    fun herdrSessionListRunsThroughExec() {
        // Read-only: lists the current user's herdr sessions if herdr exists.
        val herdr = java.io.File(System.getProperty("user.home"), ".local/bin/herdr")
        org.junit.Assume.assumeTrue("herdr not installed", herdr.exists())
        connect().use { c ->
            val out = c.exec(Multiplexer.HERDR.listCommand)
            val sessions = Multiplexer.HERDR.parseSessions(out)
            assertTrue("got: $out", sessions.isNotEmpty())
        }
    }

    @Test
    fun largeOutputArrivesIntact() {
        connect().use { c ->
            val shell = c.openShell(200, 50)
            // Stream 1 MB of 'Q' (absent from the echoed command) through the pty: many packets and window
            // adjusts, the traffic pattern that used to kill JSch sessions.
            shell.stdin.write("stty -echo; head -c 1000000 /dev/zero | tr '\\000' '\\121'; echo; echo END-\$((1+1))\r".toByteArray())
            shell.stdin.flush()
            var xs = 0L
            val buf = ByteArray(65536)
            val tail = StringBuilder()
            val end = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < end) {
                val n = shell.stdout.read(buf)
                if (n < 0) break
                for (i in 0 until n) if (buf[i] == 'Q'.code.toByte()) xs++
                tail.append(String(buf, 0, n))
                if (tail.length > 200) tail.delete(0, tail.length - 200)
                if (tail.contains("END-2")) break
            }
            assertTrue("tail: $tail", tail.contains("END-2"))
            assertEquals(1_000_000L, xs)
            shell.close()
        }
    }

    @Test
    fun execReturnsOutput() {
        connect().use { c ->
            assertEquals("hello 7", c.exec("echo hello \$((3+4))").trim())
            assertTrue(parseTmuxLs(c.exec("echo 'no server running on /tmp/x'")).isEmpty())
        }
    }

    @Test
    fun sftpUploadListAndMkdir() {
        connect().use { c ->
            val sftp = c.sftp()
            try {
                val base = File(dir, "remote").path
                sftp.mkdir(base, 493)
                val data = ByteArray(300_000) { (it % 251).toByte() }
                val h = sftp.createFileTruncate("$base/blob.bin")
                var off = 0
                while (off < data.size) {
                    val n = minOf(32_768, data.size - off)
                    sftp.write(h, off.toLong(), data, off, n)
                    off += n
                }
                sftp.closeFile(h)
                assertTrue(data.contentEquals(File(base, "blob.bin").readBytes()))
                val names = sftp.ls(base).map { it.filename }
                assertTrue(names.toString(), "blob.bin" in names)
                assertTrue(sftp.canonicalPath(".").isNotBlank())
            } finally {
                sftp.close()
            }
        }
    }

    @Test
    fun closedClientReportsDisconnected() {
        val c = connect()
        assertTrue(c.isConnected)
        c.close()
        assertFalse(c.isConnected)
    }

    @Test
    fun connectionRefusedIsFriendly() {
        val dead = ServerSocket(0).use { it.localPort }
        try {
            SshClient.connect(SshTarget("127.0.0.1", dead, user, SshAuth.Key(devicePem)), null, connectTimeoutMs = 3000)
            fail("expected failure")
        } catch (e: Exception) {
            assertTrue(SshClient.friendly(e), SshClient.friendly(e).contains("refused"))
        }
    }
}
