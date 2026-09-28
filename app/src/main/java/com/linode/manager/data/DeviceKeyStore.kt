package com.linode.manager.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Encrypted on-device storage for user-generated SSH keypairs. */
class DeviceKeyStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val gson = Gson()

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey =
                MasterKey
                    .Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            EncryptedSharedPreferences.create(
                appContext,
                "linode_device_keys",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            appContext.getSharedPreferences("linode_device_keys_fallback", Context.MODE_PRIVATE)
        }
    }

    fun list(): List<DeviceSshKey> {
        val raw =
            try {
                prefs.getString(KEY, null)
            } catch (_: Exception) {
                null
            } ?: return emptyList()
        val keys = parseDeviceKeys(raw)
        // Rewrite legacy (obfuscated-name) records in the stable format once.
        if (keys.isNotEmpty() && !raw.contains("\"privatePem\"")) save(keys)
        return keys
    }

    fun add(key: DeviceSshKey) {
        save(list() + key)
    }

    fun remove(fingerprint: String) {
        save(list().filterNot { it.fingerprint == fingerprint })
    }

    private fun save(keys: List<DeviceSshKey>) {
        try {
            prefs.edit().putString(KEY, gson.toJson(keys)).apply()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val KEY = "device_ssh_keys"
    }
}

/**
 * Parses stored device keys, tolerating the v1.x format.
 *
 * v1.x ran R8 without a keep rule for [DeviceSshKey], so Gson wrote the
 * obfuscated field names (`{"a":label,"b":publicKey,"c":privatePem,
 * "d":fingerprint,"e":createdAt}`, letters not guaranteed stable across
 * builds). Reading that with real names gave all-null keys and crashed
 * every screen that showed them. Records are therefore recognised by the
 * shape of their values, and anything incomplete is skipped — never
 * returned half-empty.
 */
fun parseDeviceKeys(raw: String): List<DeviceSshKey> {
    val arr =
        try {
            JsonParser.parseString(raw).asJsonArray
        } catch (_: Exception) {
            return emptyList()
        }
    return arr.mapNotNull { el -> (el as? JsonObject)?.let(::parseDeviceKey) }
}

private fun parseDeviceKey(o: JsonObject): DeviceSshKey? =
    try {
        val strings =
            o.entrySet().mapNotNull { (k, v) ->
                if (v.isJsonPrimitive && v.asJsonPrimitive.isString) k to v.asString else null
            }

        fun named(name: String) = o.get(name)?.takeIf { it.isJsonPrimitive }?.asString
        val pub = named("publicKey") ?: strings.firstOrNull { it.second.startsWith("ssh-") }?.second
        val pem = named("privatePem") ?: strings.firstOrNull { it.second.contains("PRIVATE KEY-----") }?.second
        val fp = named("fingerprint") ?: strings.firstOrNull { it.second.startsWith("SHA256:") }?.second
        val label = named("label") ?: strings.map { it.second }.firstOrNull { it != pub && it != pem && it != fp }
        val created =
            o.get("createdAt")?.asLong
                ?: o
                    .entrySet()
                    .firstOrNull { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isNumber }
                    ?.value
                    ?.asLong
                ?: 0L
        if (pub.isNullOrBlank() || pem.isNullOrBlank() || fp.isNullOrBlank()) {
            null
        } else {
            DeviceSshKey(
                label = label?.ifBlank { null } ?: "device-key",
                publicKey = pub,
                privatePem = pem,
                fingerprint = fp,
                createdAt = created,
            )
        }
    } catch (_: Exception) {
        null
    }
