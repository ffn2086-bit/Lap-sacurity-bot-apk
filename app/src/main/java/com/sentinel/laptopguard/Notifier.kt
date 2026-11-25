package com.sentinel.laptopguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    const val CH_ALERT = "breach_alerts"
    const val CH_SERVICE = "monitor_service"
    const val ID_SERVICE = 1001
    const val ID_ALERT = 3001

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val alert = NotificationChannel(
            CH_ALERT, "Security breach alerts", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Full-screen red alert when your laptop is accessed"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attrs)
            enableLights(true)
            lightColor = 0xFFFF1E27.toInt()
        }
        nm.createNotificationChannel(alert)

        val service = NotificationChannel(
            CH_SERVICE, "Guard service", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the connection to your laptop agent alive"
        }
        nm.createNotificationChannel(service)
    }

    fun foregroundNotification(ctx: Context): Notification {
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            ctx, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("Pachu Lap Security active")
            .setContentText("Watching your laptop")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    fun showAlert(ctx: Context, a: AlertInfo) {
        ensureChannels(ctx)
        val i = a.putInto(Intent(ctx, FullScreenAlertActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, 2001, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = "${a.laptop} • ${a.reason}"
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(a.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n${fmtTime(a.time)}\nIP: ${a.publicIp}"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFFFF1E27.toInt())
            .setAutoCancel(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_ALERT, n)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted: the overlay activity launch still works.
        }
    }
}
