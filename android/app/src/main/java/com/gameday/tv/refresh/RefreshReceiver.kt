package com.gameday.tv.refresh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gameday.tv.GameDayApp
import com.gameday.tv.core.Diag

/**
 * Best-effort background refresh. Android may stop a background receiver after a few seconds;
 * if that happens the guide simply stays as cached, and the next app start refreshes because
 * staleness is derived from the persisted last-refresh timestamp.
 */
class RefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = GameDayApp.container(context)
        if (!container.settings.autoRefresh) return

        val pending = goAsync()
        Thread({
            try {
                val result = container.guideRepository.refresh()
                container.settings.lastRefreshMs = System.currentTimeMillis()
                container.settings.lastRefreshOk = result.ok
                container.settings.lastRefreshMessage =
                    if (result.ok) result.message else (result.errors.firstOrNull() ?: result.message)
                Diag.info("Refresh", "Background refresh: ${result.message}")
            } catch (e: Exception) {
                Diag.warn("Refresh", "Background refresh failed: ${e.javaClass.simpleName}")
            } finally {
                try {
                    pending.finish()
                } catch (e: Exception) {
                    // Receiver already finished by the system; nothing to do.
                }
            }
        }, "gameday-refresh").start()
    }
}
