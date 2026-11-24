package com.gameday.tv.refresh

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gameday.tv.core.Diag

/**
 * Guide refresh is driven by an inexact repeating alarm rather than a persistent service:
 * on a 1.5 GB Fire TV nothing keeps running (and nothing holds memory) while the app is idle.
 * Every app start re-arms the alarm because alarms do not survive a reboot.
 */
object RefreshScheduler {

    private const val REQUEST_CODE = 1001

    fun schedule(context: Context, intervalHours: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intervalMs = intervalHours.coerceIn(1, 48) * 60L * 60L * 1000L
        val intent = Intent(context, RefreshReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + intervalMs,
                intervalMs,
                pending,
            )
            Diag.info("Refresh", "Scheduled guide refresh every ${intervalHours}h")
        } catch (e: SecurityException) {
            Diag.warn("Refresh", "Could not schedule guide refresh: ${e.javaClass.simpleName}")
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, RefreshReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        alarmManager.cancel(pending)
        Diag.info("Refresh", "Cancelled scheduled guide refresh")
    }
}
