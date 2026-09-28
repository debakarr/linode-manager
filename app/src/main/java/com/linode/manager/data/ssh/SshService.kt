package com.linode.manager.data.ssh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.linode.manager.LinodeApp
import com.linode.manager.MainActivity
import com.linode.manager.R

/**
 * Foreground service that exists only to keep the process (and its SSH
 * sockets) alive while terminals are open. All state lives in
 * [SshSessionManager]; this just shows the ongoing notification.
 */
class SshService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val manager = (application as LinodeApp).container.sshSessions
        if (intent?.action == ACTION_DISCONNECT_ALL) {
            manager.closeAll()
            stopSelf()
            return START_NOT_STICKY
        }
        // startForeground must always run after startForegroundService(),
        // even if the last session closed in the meantime (Android 12+
        // otherwise kills the app); stop right after in that case.
        val labels = manager.liveLabels()
        ensureChannel()
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val disconnect =
            PendingIntent.getService(
                this,
                1,
                Intent(this, SshService::class.java).setAction(ACTION_DISCONNECT_ALL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val title =
            if (labels.isEmpty()) {
                "SSH sessions"
            } else if (labels.size ==
                1
            ) {
                "SSH connected · ${labels[0]}"
            } else {
                "${labels.size} SSH sessions connected"
            }
        val notification =
            NotificationCompat
                .Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_terminal)
                .setContentTitle(title)
                .setContentText(if (labels.size == 1) "Tap to return to the terminal" else labels.joinToString(", "))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(open)
                .addAction(0, "Disconnect all", disconnect)
                .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (labels.isEmpty()) stopSelf()
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "SSH sessions", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while an SSH terminal is connected"
                },
            )
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.linode.manager.ssh.REFRESH"
        const val ACTION_DISCONNECT_ALL = "com.linode.manager.ssh.DISCONNECT_ALL"
        private const val CHANNEL = "ssh_sessions"
        private const val NOTIFICATION_ID = 42
    }
}
