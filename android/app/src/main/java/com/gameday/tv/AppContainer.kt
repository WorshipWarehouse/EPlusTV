package com.gameday.tv

import android.content.Context
import android.os.Build
import com.gameday.tv.core.Diag
import com.gameday.tv.core.HttpTransport
import com.gameday.tv.core.KeyValueStore
import com.gameday.tv.core.PrefsKeyValueStore
import com.gameday.tv.core.UrlConnectionTransport
import com.gameday.tv.epg.GuideCache
import com.gameday.tv.epg.GuideRepository
import com.gameday.tv.providers.FavoritesStore
import com.gameday.tv.providers.JsonSourceStore
import com.gameday.tv.providers.SourceStore
import com.gameday.tv.providers.StoreFavoritesStore
import com.gameday.tv.refresh.RefreshScheduler
import com.gameday.tv.storage.AndroidKeystoreSecretStore
import com.gameday.tv.storage.SecretStore
import com.gameday.tv.storage.Settings
import com.gameday.tv.update.AndroidApkInspector
import com.gameday.tv.update.ApkInspector
import com.gameday.tv.update.StreamingApkDownloader
import com.gameday.tv.update.UpdateRepository
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything the app needs, created once per process. There is no server process and no
 * second copy of the guide: the "backend" is this in-process repository layer.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val keyValue: KeyValueStore = PrefsKeyValueStore(
        appContext.getSharedPreferences("gameday", Context.MODE_PRIVATE),
    )
    val settings = Settings(keyValue)
    val secrets: SecretStore = AndroidKeystoreSecretStore(appContext)
    val sourceStore: SourceStore = JsonSourceStore(keyValue, secrets)
    val favorites: FavoritesStore = StoreFavoritesStore(keyValue)
    val transport: HttpTransport = UrlConnectionTransport()
    val guideCache = GuideCache(File(appContext.filesDir, "guide/guide.json"))
    val guideRepository = GuideRepository(transport, sourceStore, guideCache)

    private val inspector: ApkInspector = AndroidApkInspector(appContext)
    val updateRepository = UpdateRepository(
        settings = settings,
        transport = transport,
        downloader = StreamingApkDownloader(),
        inspector = inspector,
        installedVersionCode = BuildConfig.VERSION_CODE.toLong(),
        deviceSdk = Build.VERSION.SDK_INT,
        updateDirectory = File(appContext.filesDir, "updates"),
    )

    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshing = AtomicBoolean(false)
    val lastRefreshResult = AtomicReference<GuideRepository.RefreshResult?>(null)

    fun bootstrap() {
        val migration = settings.migrate()
        if (migration.changed) {
            Diag.info("App", "Settings migrated ${migration.fromVersion} -> ${migration.toVersion}")
        }
        if (settings.autoRefresh) {
            RefreshScheduler.schedule(appContext, settings.refreshIntervalHours)
        }
        Diag.info("App", "GameDay ${BuildConfig.VERSION_NAME} started on API ${Build.VERSION.SDK_INT}")
    }

    fun isRefreshing(): Boolean = refreshing.get()

    /** Single-flight refresh so a screen switch and an alarm cannot run two fetches. */
    fun refreshGuide(onDone: ((GuideRepository.RefreshResult) -> Unit)? = null) {
        if (!refreshing.compareAndSet(false, true)) return
        io.launch {
            val result = try {
                guideRepository.refresh()
            } catch (e: Exception) {
                GuideRepository.RefreshResult(
                    ok = false,
                    channelCount = 0,
                    programCount = 0,
                    errors = listOf(Diag.sanitize(e.message ?: e.javaClass.simpleName)),
                    durationMs = 0L,
                    message = "Guide refresh failed",
                )
            }
            settings.lastRefreshMs = System.currentTimeMillis()
            settings.lastRefreshOk = result.ok
            settings.lastRefreshMessage = if (result.ok) {
                result.message
            } else {
                result.errors.firstOrNull() ?: result.message
            }
            lastRefreshResult.set(result)
            refreshing.set(false)
            withContext(Dispatchers.Main) { onDone?.invoke(result) }
        }
    }

    fun guideStale(maxAgeMs: Long = 15 * 60 * 1000L): Boolean {
        if (guideCache.sizeBytes() == 0L) return true
        val last = settings.lastRefreshMs
        return last == 0L || System.currentTimeMillis() - last > maxAgeMs
    }

    fun rescheduleRefresh() {
        if (settings.autoRefresh) {
            RefreshScheduler.schedule(appContext, settings.refreshIntervalHours)
        } else {
            RefreshScheduler.cancel(appContext)
        }
    }
}
