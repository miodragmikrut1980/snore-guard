package rs.snoreguard.mvp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Alerts are ordinary Android notifications; Huawei Health decides if GT 2 vibrates. */
object AlertNotifier {
    const val MONITOR_CHANNEL = "monitor_status_v1"
    private const val SILENT_CHANNEL = "watch_alert_silent_v1"
    private const val VIBRATE_CHANNEL = "watch_alert_vibrate_v1"
    const val ONGOING_NOTIFICATION = 42

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val monitor = NotificationChannel(MONITOR_CHANNEL, "Praćenje hrkanja", NotificationManager.IMPORTANCE_LOW)
        monitor.description = "Stalna informacija da mikrofon radi"
        monitor.setSound(null, null)
        monitor.enableVibration(false)
        nm.createNotificationChannel(monitor)

        val silent = NotificationChannel(SILENT_CHANNEL, "Upozorenja za sat (bez vibracije telefona)", NotificationManager.IMPORTANCE_HIGH)
        silent.setSound(null, null)
        silent.enableVibration(false)
        silent.description = "Tihi alarm za GT 2; vibracija sata zavisi od Huawei Health"
        nm.createNotificationChannel(silent)

        val vibrate = NotificationChannel(VIBRATE_CHANNEL, "Upozorenja (sa vibracijom telefona)", NotificationManager.IMPORTANCE_HIGH)
        vibrate.setSound(null, null)
        vibrate.enableVibration(true)
        vibrate.vibrationPattern = longArrayOf(0, 180, 160, 180)
        nm.createNotificationChannel(vibrate)
    }

    fun monitorNotification(context: Context): Notification {
        val intent = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(context, 1,
            Intent(context, MonitorService::class.java).setAction(MonitorService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(context, MONITOR_CHANNEL)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle("SnoreGuard prati zvuk tokom noći")
            .setContentText("Mikrofon je aktivan. Dodirni za statistiku.")
            .setContentIntent(intent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "Zaustavi", stop).build())
            .build()
    }

    /** Each burst step is a NEW notification: never overwrite the previous step. */
    fun sendWatchAlert(context: Context, test: Boolean, step: Int = 1) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = if (Settings(context).phoneVibration) VIBRATE_CHANNEL else SILENT_CHANNEL
        val prefs = context.getSharedPreferences("snoreguard_alert_ids", Context.MODE_PRIVATE)
        val previous = prefs.getInt("last", 99)
        val next = if (previous >= 999_999) 100 else maxOf(100, previous + 1)
        // Retain a small notification history for the Huawei bridge, not an unlimited flood.
        if (next > 129) nm.cancel(next - 30)
        prefs.edit().putInt("last", next).apply()
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle(if (test) "SnoreGuard — TEST ${step}/3" else "SnoreGuard — hrkanje ${step}/3")
            .setContentText(if (test) "Alarm ${step}/3 • proveri vibraciju GT 2" else "Upozorenje ${step}/3 • hrkanje se nastavlja")
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        nm.notify(next, notification)
    }
}
