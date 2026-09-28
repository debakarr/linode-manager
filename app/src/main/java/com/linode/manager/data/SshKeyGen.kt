package com.linode.manager.data

import com.google.gson.annotations.SerializedName
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/**
 * A keypair generated on this device. The private key never leaves the
 * device except when the user explicitly copies it; the public key can be
 * uploaded to the Linode account (used for new Linodes) or pasted into a
 * Linode's ~/.ssh/authorized_keys for SSH access.
 */
data class DeviceSshKey(
    // Names pinned so storage never depends on shrinker/obfuscation again.
    @SerializedName("label") val label: String,
    @SerializedName("publicKey") val publicKey: String,
    @SerializedName("privatePem") val privatePem: String,
    @SerializedName("fingerprint") val fingerprint: String,
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis(),
)

object SshKeyGen {
    /** Generate an RSA keypair and return it with OpenSSH public encoding. */
    fun generateRsa(
        label: String,
        bits: Int = 3072,
    ): DeviceSshKey {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(bits)
        val kp = kpg.generateKeyPair()
        val pub = kp.public as RSAPublicKey
        val wire = encodeSshRsa(pub)
        val b64 = Base64.getEncoder().encodeToString(wire)
        val comment = label.trim().replace(Regex("\\s+"), "-").ifBlank { "linode-manager" }
        val publicKey = "ssh-rsa $b64 $comment"
        val digest = MessageDigest.getInstance("SHA-256").digest(wire)
        val fp = "SHA256:" + Base64.getEncoder().encodeToString(digest).trimEnd('=')
        val privB64 = Base64.getEncoder().encodeToString(kp.private.encoded)
        val pem =
            buildString {
                append("-----BEGIN PRIVATE KEY-----\n")
                privB64.chunked(64).forEach { append(it).append('\n') }
                append("-----END PRIVATE KEY-----\n")
            }
        return DeviceSshKey(
            label = label.trim().ifBlank { "device-key" },
            publicKey = publicKey,
            privatePem = pem,
            fingerprint = fp,
        )
    }

    /** OpenSSH wire format: string "ssh-rsa" + mpint(e) + mpint(n). */
    private fun encodeSshRsa(pub: RSAPublicKey): ByteArray {
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)

        fun writeString(s: ByteArray) {
            dos.writeInt(s.size)
            dos.write(s)
        }

        fun writeMpint(b: ByteArray) {
            var v = b.dropWhile { it == 0.toByte() }.toByteArray()
            if (v.isEmpty()) v = byteArrayOf(0)
            if (v[0] < 0) v = byteArrayOf(0) + v
            dos.writeInt(v.size)
            dos.write(v)
        }
        writeString("ssh-rsa".toByteArray())
        writeMpint(pub.publicExponent.toByteArray())
        writeMpint(pub.modulus.toByteArray())
        dos.flush()
        return out.toByteArray()
    }
}
