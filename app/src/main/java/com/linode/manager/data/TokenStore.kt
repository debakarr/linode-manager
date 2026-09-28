package com.linode.manager.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Secure token storage backed by EncryptedSharedPreferences with a
 * plain-SharedPreferences fallback (some emulators lack keystore).
 */
class TokenStore(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey =
                MasterKey
                    .Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            EncryptedSharedPreferences.create(
                appContext,
                "linode_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            appContext.getSharedPreferences("linode_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val _tokenFlow = MutableStateFlow(readToken())
    val tokenFlow: StateFlow<String?> = _tokenFlow.asStateFlow()

    fun readToken(): String? =
        try {
            prefs.getString(KEY_TOKEN, null)?.ifBlank { null }
        } catch (_: Exception) {
            _tokenFlow.value
        }

    fun saveToken(token: String) {
        try {
            prefs.edit().putString(KEY_TOKEN, token.trim()).apply()
        } catch (_: Exception) {
        }
        _tokenFlow.value = token.trim()
    }

    fun clear() {
        try {
            prefs.edit().remove(KEY_TOKEN).apply()
        } catch (_: Exception) {
        }
        _tokenFlow.value = null
    }

    fun hasToken(): Boolean = readToken() != null

    companion object {
        private const val KEY_TOKEN = "linode_pat"
    }
}
