package com.linode.manager.data.ssh

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.security.Security

/**
 * Runs the transport with Android's JCE provider order (Conscrypt first,
 * BouncyCastle after) against real OpenSSH. This is how the old app's
 * "MAC incorrect" class of bugs shows up on a desktop JVM: sshlib's
 * chacha20-poly1305 fails every packet here, which is why it's not offered.
 */
class ConscryptCipherMatrixTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1)
            Security.insertProviderAt(
                org.bouncycastle.jce.provider
                    .BouncyCastleProvider(),
                2,
            )
            TestSshd.start()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() = TestSshd.stop()
    }

    private val target get() = SshTarget("127.0.0.1", TestSshd.port, TestSshd.user, SshAuth.Key(TestSshd.devicePem))

    private fun transfer(ciphers: List<String>): String =
        runCatching {
            SshClient.connect(target, TestSshd.hostKeyStore, ciphers = ciphers).use { c ->
                c.exec("head -c 300000 /dev/zero | tr '\\000' 'a' | wc -c").trim()
            }
        }.fold({ it }, { e -> generateSequence(e) { it.cause }.joinToString(" <- ") { it.message ?: it.toString() } })

    @Test
    fun everyOfferedCipherWorksWithAndroidProviders() {
        val bad = SshClient.PREFERRED_CIPHERS.associateWith { transfer(listOf(it)) }.filterValues { it != "300000" }
        assertEquals("failing ciphers: $bad", emptyMap<String, String>(), bad)
    }

    @Test
    fun defaultNegotiationWorksWithAndroidProviders() {
        SshClient.connect(target, TestSshd.hostKeyStore).use { c ->
            assertTrue(c.info, c.info.contains("gcm") || c.info.contains("ctr"))
            assertEquals("300000", c.exec("head -c 300000 /dev/zero | tr '\\000' 'a' | wc -c").trim())
            val sh = c.openShell(80, 24)
            sh.stdin.write("echo OK-\$((20+22))\r".toByteArray())
            sh.stdin.flush()
            val buf = ByteArray(4096)
            val sb = StringBuilder()
            val end = System.currentTimeMillis() + 10_000
            while (!sb.contains("OK-42") && System.currentTimeMillis() < end) {
                val n = sh.stdout.read(buf)
                if (n < 0) break
                sb.append(String(buf, 0, n))
            }
            assertTrue(sb.toString(), sb.contains("OK-42"))
            sh.close()
        }
    }

    @Test
    fun chachaIsBrokenHereSoItMustStayExcluded() {
        // Documents why chacha20-poly1305 isn't offered. If sshlib fixes its
        // provider handling this starts passing — then it can be re-enabled.
        val r = transfer(listOf("chacha20-poly1305@openssh.com"))
        assertTrue("chacha unexpectedly works now: $r", r != "300000")
        assertTrue("chacha20-poly1305@openssh.com" !in SshClient.PREFERRED_CIPHERS)
    }
}
