package com.linode.manager.data.ssh

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * App-wide registry of terminal sessions (one per Linode). Keeps a
 * foreground service running while any session is live so Android doesn't
 * kill the connection when the user switches apps, and nudges sessions when
 * the network changes so they recover immediately instead of timing out.
 */
class SshSessionManager(
    context: Context,
    private val hostKeys: HostKeyStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Observable from Compose. Keyed by Linode ID. */
    val sessions = mutableStateMapOf<Int, TerminalSession>()

    private var serviceRunning = false

    init {
        try {
            val cm = appContext.getSystemService(ConnectivityManager::class.java)
            cm?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        scope.launch { sessions.values.forEach { it.onNetworkAvailable() } }
                    }
                },
            )
        } catch (_: Exception) {
        }
    }

    fun get(linodeId: Int): TerminalSession? = sessions[linodeId]

    fun start(
        profile: SshProfile,
        auth: SshAuth,
    ): TerminalSession {
        sessions.remove(profile.linodeId)?.close()
        val s = TerminalSession(profile, auth, hostKeys, scope) { refreshService() }
        sessions[profile.linodeId] = s
        refreshService()
        return s
    }

    fun close(linodeId: Int) {
        sessions.remove(linodeId)?.close()
        refreshService()
    }

    fun closeAll() {
        sessions.keys.toList().forEach { close(it) }
    }

    val liveCount: Int get() = sessions.values.count { it.isLive }

    fun liveLabels(): List<String> = sessions.values.filter { it.isLive }.map { it.profile.label }

    private fun refreshService() {
        val live = liveCount
        try {
            if (live > 0) {
                val i = Intent(appContext, SshService::class.java)
                if (!serviceRunning) {
                    ContextCompat.startForegroundService(appContext, i)
                    serviceRunning = true
                } else {
                    appContext.startService(i.setAction(SshService.ACTION_REFRESH))
                }
            } else if (serviceRunning) {
                appContext.stopService(Intent(appContext, SshService::class.java))
                serviceRunning = false
            }
        } catch (_: Exception) {
            // Starting a foreground service from the background can be
            // refused; the session still works while the app is visible.
        }
    }
}

/** Remembers the last connection settings per Linode (never secrets). */
class SshProfileStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("ssh_profiles", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(linodeId: Int): SshProfile? =
        try {
            prefs
                .getString("p$linodeId", null)
                ?.let { gson.fromJson(it, SshProfile::class.java) }
                // Gson bypasses Kotlin null-safety; drop records missing fields.
                ?.takeIf { p -> (p.host as String?) != null && (p.username as String?) != null && (p.label as String?) != null }
        } catch (_: Exception) {
            null
        }

    fun save(profile: SshProfile) {
        prefs.edit().putString("p${profile.linodeId}", gson.toJson(profile)).apply()
    }
}
