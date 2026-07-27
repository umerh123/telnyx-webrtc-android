package com.telnyx.webrtc.sdk.utility

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.telnyx.webrtc.sdk.R
import com.telnyx.webrtc.sdk.ui.MainActivity

/**
 * Keeps the process alive with a low-priority, silent, ongoing notification
 * while the user is logged in. This is separate from the call-ringing
 * notification (TelecomCallNotificationManager) and exists purely so
 * aggressive OEM battery managers (Xiaomi/MIUI, Infinix/XOS, Oppo/Vivo,
 * Samsung "sleeping apps", etc.) are far less likely to kill this app
 * before an incoming-call push notification can arrive and wake it.
 *
 * Started after a successful login, stopped on logout.
 */
class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "swiftbyte_keep_alive_channel"
        private const val NOTIFICATION_ID = 999

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY tells Android to try to recreate this service if it's
        // ever killed, rather than leaving it dead.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Swiftbyte Dialer status",
                NotificationManager.IMPORTANCE_MIN, // lowest priority: no sound, hidden from lock screen, minimized in shade
            ).apply {
                description = "Keeps Swiftbyte Dialer ready to receive calls"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Swiftbyte Dialer")
            .setContentText("Ready to receive calls")
            .setSmallIcon(R.drawable.ic_app_icon_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openAppIntent)
            .build()
    }
}
