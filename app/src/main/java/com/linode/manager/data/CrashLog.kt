package com.linode.manager.data

import android.content.Context
import android.os.Build
import com.linode.manager.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records an uncaught exception to a private file so the next launch can
 * show it (with a Copy button) instead of the app silently closing.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val header =
                    "Linode Manager ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) + " · thread ${thread.name}\n\n"
                File(appContext.filesDir, FILE).writeText(header + sw.toString().take(20_000))
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        try {
            File(context.filesDir, FILE).takeIf { it.exists() }?.readText()
        } catch (_: Exception) {
            null
        }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE).delete()
        } catch (_: Exception) {
        }
    }
}
