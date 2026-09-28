package com.linode.manager.data.ssh

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Trust-on-first-use store for SSH host keys ("type base64" per host). */
class HostKeyStore(
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
                "linode_known_hosts",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            appContext.getSharedPreferences("linode_known_hosts_fallback", Context.MODE_PRIVATE)
        }
    }

    private fun map(): MutableMap<String, String> {
        return try {
            val raw = prefs.getString(KEY, null) ?: return mutableMapOf()
            gson.fromJson<Map<String, String>>(raw, object : TypeToken<Map<String, String>>() {}.type)?.toMutableMap()
                ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    fun get(host: String): String? = map()[host]

    fun put(
        host: String,
        value: String,
    ) {
        val m = map()
        m[host] = value
        try {
            prefs.edit().putString(KEY, gson.toJson(m)).apply()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val KEY = "known_hosts"
    }
}
