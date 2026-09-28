package com.linode.manager.data.ssh

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.security.Security

/**
 * Reproduces a real failure: with Android's provider stack installed
 * (Conscrypt first, BouncyCastle after), sshlib takes its Java-KEM path,
 * mixes providers, and the hybrid mlkem768x25519 exchange with OpenSSH 10
 * dies ("ML-KEM decapsulation failed: Cannot parse input"). The client must fall back to
 * curve25519 instead of failing to connect.
 *
 * Runs in its own JVM (forkEvery = 1) so the provider is installed before
 * sshlib's KexManager picks a backend.
 */
class MlKemFallbackTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            // Android's provider order: Conscrypt first, then BouncyCastle.
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
        fun tearDown() {
            TestSshd.stop()
            Security.removeProvider("BC")
            Security.removeProvider("Conscrypt")
        }
    }

    @Test
    fun brokenPostQuantumBackendFallsBackToCurve25519() {
        val log = mutableListOf<String>()
        val target = SshTarget("127.0.0.1", TestSshd.port, TestSshd.user, SshAuth.Key(TestSshd.devicePem))
        SshClient.connect(target, TestSshd.hostKeyStore, log = { log += it }).use { c ->
            assertTrue(c.info, c.info.contains("kex=curve25519"))
            assertEquals("ok", c.exec("echo ok").trim())
        }
        // Only meaningful when the server actually offers the hybrid group.
        // OpenSSH older than 9.9 (Ubuntu 24.04 ships 9.6) never proposes
        // mlkem768x25519, so curve25519 is negotiated straight away and there
        // is nothing to fall back from — skip rather than fail.
        assumeTrue(
            "sshd does not offer mlkem768x25519; the fallback cannot be exercised here",
            log.any { it.contains("retrying with curve25519") },
        )
        assertTrue(SshClient.postQuantumDisabled)
    }
}
