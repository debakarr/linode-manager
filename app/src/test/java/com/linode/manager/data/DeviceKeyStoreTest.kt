package com.linode.manager.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceKeyStoreTest {
    private val pem = "-----BEGIN PRIVATE KEY-----\nMIIE\n-----END PRIVATE KEY-----\n"
    private val pub = "ssh-rsa AAAAB3NzaC1yc2E pixel-ssh"
    private val fp = "SHA256:abcdEFGH"

    @Test
    fun readsV141ObfuscatedFormat() {
        // Exact shape v1.4.1 wrote (R8 renamed DeviceSshKey's fields a..e).
        val raw = """[{"a":"pixel-ssh","b":"$pub","c":${Gson().toJson(pem)},"d":"$fp","e":1757000000000}]"""
        val keys = parseDeviceKeys(raw)
        assertEquals(1, keys.size)
        with(keys[0]) {
            assertEquals("pixel-ssh", label)
            assertEquals(pub, publicKey)
            assertEquals(pem, privatePem)
            assertEquals(fp, fingerprint)
            assertEquals(1757000000000L, createdAt)
        }
    }

    @Test
    fun readsLegacyFormatWithDifferentLetterOrder() {
        val raw = """[{"x":"$fp","y":${Gson().toJson(pem)},"z":"my key","w":"$pub","v":5}]"""
        val k = parseDeviceKeys(raw).single()
        assertEquals("my key", k.label)
        assertEquals(fp, k.fingerprint)
        assertEquals(pub, k.publicKey)
    }

    @Test
    fun roundTripsCurrentFormat() {
        val key = DeviceSshKey("laptop", pub, pem, fp, 42L)
        val json = Gson().toJson(listOf(key))
        assertTrue(json, json.contains("\"privatePem\""))
        assertEquals(listOf(key), parseDeviceKeys(json))
    }

    @Test
    fun skipsIncompleteOrGarbageRecordsInsteadOfCrashing() {
        assertEquals(emptyList<DeviceSshKey>(), parseDeviceKeys("not json"))
        assertEquals(emptyList<DeviceSshKey>(), parseDeviceKeys("""[{"a":"only a label"}, 3, null]"""))
        val mixed = """[{"a":"broken"},{"label":"ok","publicKey":"$pub","privatePem":${Gson().toJson(
            pem,
        )},"fingerprint":"$fp","createdAt":1}]"""
        assertEquals(listOf("ok"), parseDeviceKeys(mixed).map { it.label })
    }
}
