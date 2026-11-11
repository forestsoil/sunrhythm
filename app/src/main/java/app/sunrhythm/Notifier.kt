package app.sunrhythm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager

object Notifier {
    const val ALERT = "alert"
    const val NUDGE = "nudge"

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(ALERT, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
        }
        nm.createNotificationChannel(ch)
        nm.createNotificationChannel(
            NotificationChannel(NUDGE, "Hourly movement", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun build(c: Context, channel: String, title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(c, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true)
            .setOnlyAlertOnce(true).build()
    }

    fun alert(c: Context, id: Int, title: String, text: String) {
        channels(c)
        c.getSystemService(NotificationManager::class.java)
            .notify(id, build(c, ALERT, title, text))
    }

    fun quiet(c: Context, id: Int, title: String, text: String) {
        channels(c)
        c.getSystemService(NotificationManager::class.java)
            .notify(id, build(c, NUDGE, title, text))
    }
}
