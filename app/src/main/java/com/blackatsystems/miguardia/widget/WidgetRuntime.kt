package com.blackatsystems.miguardia.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherForecast
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.core.domain.weather.summarizeShiftWeather
import com.blackatsystems.miguardia.core.domain.weather.weatherFreshness
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjectionConfig
import com.blackatsystems.miguardia.core.domain.widget.WidgetSize
import com.blackatsystems.miguardia.core.domain.widget.nextWidgetBoundary
import com.blackatsystems.miguardia.core.domain.widget.projectWidget
import com.blackatsystems.miguardia.ui.nextevent.NextEventSourceData
import com.blackatsystems.miguardia.ui.nextevent.V2WorkEventSourceObserver
import com.blackatsystems.miguardia.weather.WeatherRuntime
import com.blackatsystems.miguardia.weather.WeatherPreferences
import com.blackatsystems.miguardia.weather.formatWeatherForNotification
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class WidgetRuntime(
    private val context: Context,
    private val localDataStore: () -> LocalDataStore,
    val preferences: WidgetPreferencesStore,
    private val weatherRuntime: WeatherRuntime,
    private val clock: Clock = Clock.systemUTC(),
    private val renderer: WidgetRemoteViewsRenderer = WidgetRemoteViewsRenderer(context.applicationContext),
    private val scheduler: WidgetBoundaryScheduler = AndroidWidgetBoundaryScheduler(context.applicationContext),
) {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val manager = AppWidgetManager.getInstance(context.applicationContext)
    private val renderMutex = Mutex()
    private val configurationReceiverLock = Any()
    private val configurationReceiver = WidgetConfigurationChangeReceiver(::refreshAll)
    private var configurationReceiverRegistered = false
    private val restoreBarriers = ConcurrentHashMap<Int, CompletableDeferred<Unit>>()
    private val sources: V2WorkEventSourceObserver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val store = localDataStore()
        V2WorkEventSourceObserver(
            shifts = store.v2Shifts,
            availabilityWindows = store.availabilityWindows,
            shiftActuals = store.shiftActuals,
            independentExtras = store.independentExtraWork,
            explicitDayStatuses = store.explicitDayStatuses,
            vacations = store.vacations,
            medicalLeaves = store.medicalLeaves,
            workConfiguration = store.workConfiguration,
        )
    }
    @Volatile
    private var latestSource: NextEventSourceData? = null
    @Volatile
    private var observationDate: LocalDate? = null
    private var observationJob: Job? = null
    private var weatherRefreshJob: Job? = null

    fun start() {
        val installed = renderer.installedIds().validIds()
        if (installed.isEmpty()) {
            stopBackgroundWork()
            return
        }
        registerConfigurationReceiver()
        renderer.renderLoading(installed)
        ensureObservation()
    }

    internal fun showLoading(appWidgetIds: IntArray) {
        renderer.renderLoading(appWidgetIds.validIds())
    }

    fun refreshAll() {
        scope.launch {
            try {
                refreshNow()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                renderer.renderRecoverableError(renderer.installedIds())
                scheduler.schedule(recoveryBoundary())
            }
        }
    }

    fun refresh(appWidgetId: Int) {
        if (appWidgetId <= 0) return
        scope.launch {
            try {
                refreshNow(intArrayOf(appWidgetId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                renderer.renderRecoverableError(intArrayOf(appWidgetId))
                scheduler.schedule(recoveryBoundary())
            }
        }
    }

    suspend fun refreshNow(
        requestedIds: IntArray? = null,
        allowWeatherRefresh: Boolean = true,
    ) {
        awaitRestorations(requestedIds ?: renderer.installedIds())
        val installed = renderer.installedIds().validIds()
        val installedSet = installed.toSet()
        val targets = requestedIds?.validIds()?.filter { it in installedSet }?.toIntArray() ?: installed
        if (installed.isEmpty() || targets.isEmpty()) {
            stopBackgroundWork()
            return
        }
        ensureObservation()
        val today = currentDate()
        val source = latestSource.takeIf { observationDate == today } ?: withTimeout(SOURCE_TIMEOUT_MILLIS) {
            sources.observe(today).first()
        }.also { latestSource = it }
        val configs = preferences.all()
        renderSnapshot(source, configs, targets, installed, allowWeatherRefresh)
    }

    suspend fun deleteNow(appWidgetIds: IntArray) {
        preferences.delete(appWidgetIds.toList())
        val remaining = renderer.installedIds().validIds().filterNot { it in appWidgetIds.toSet() }.toIntArray()
        if (remaining.isEmpty()) {
            stopBackgroundWork()
        } else {
            refreshNow(remaining)
        }
    }

    fun disabled() {
        stopBackgroundWork()
    }

    internal fun receiverFailed(appWidgetIds: IntArray) {
        val valid = appWidgetIds.validIds()
        renderer.renderRecoverableError(valid)
        if (valid.isNotEmpty()) scheduler.schedule(recoveryBoundary())
    }

    fun registerRestoration(newIds: IntArray) {
        newIds.validIds().forEach { id -> restoreBarriers.putIfAbsent(id, CompletableDeferred()) }
        renderer.renderLoading(newIds.validIds())
    }

    suspend fun restoreNow(oldIds: IntArray, newIds: IntArray) {
        try {
            preferences.remap(oldIds, newIds)
        } finally {
            newIds.validIds().forEach { id -> restoreBarriers.remove(id)?.complete(Unit) }
        }
        refreshNow(newIds)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            newIds.validIds().forEach { id ->
                manager.updateAppWidgetOptions(
                    id,
                    Bundle().apply {
                        putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true)
                    },
                )
            }
            refreshNow(newIds, allowWeatherRefresh = false)
        }
    }

    private fun ensureObservation() {
        val installed = renderer.installedIds().validIds()
        if (installed.isEmpty()) {
            stopBackgroundWork()
            return
        }
        val today = currentDate()
        if (observationDate == today && observationJob?.isActive == true) return
        observationJob?.cancel()
        if (observationDate != today) latestSource = null
        observationDate = today
        observationJob = scope.launch {
            try {
                combine(
                    sources.observe(today),
                    preferences.instances,
                    weatherRuntime.preferences.preferences,
                ) { source, configs, _ -> source to configs }
                    .collect { (source, configs) ->
                        latestSource = source
                        val currentInstalled = renderer.installedIds().validIds()
                        if (currentInstalled.isEmpty()) {
                            stopBackgroundWork()
                        } else {
                            renderSnapshot(
                                source = source,
                                configs = configs,
                                targets = currentInstalled,
                                installed = currentInstalled,
                                allowWeatherRefresh = true,
                            )
                        }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                latestSource = null
                renderer.renderRecoverableError(renderer.installedIds())
                scheduler.schedule(recoveryBoundary())
            }
        }
    }

    private suspend fun renderSnapshot(
        source: NextEventSourceData,
        configs: Map<Int, WidgetInstancePreferences>,
        targets: IntArray,
        installed: IntArray,
        allowWeatherRefresh: Boolean,
    ) = renderMutex.withLock {
        val result = projectNextEvent(
            now = clock.instant(),
            zoneId = AppDefaults.zoneId(),
            input = source.toInput(),
        )
        targets.forEach { id ->
            val config = configs[id] ?: WidgetPreferencesStore.SafeDefault
            val weather = if (renderer.isExpandedLayout(id)) cachedWeatherText(result, config) else null
            renderer.render(id, result, config, weather)
        }
        val installedConfigs = installed.associateWith { id ->
            configs[id] ?: WidgetPreferencesStore.SafeDefault
        }
        val eventBoundary = nextWidgetBoundary(
            result,
            installedConfigs.values.filter { it.configured }.map { it.mode },
        )
        val weatherBoundary = weatherExpiryBoundary(result, installedConfigs)
        scheduler.schedule(weatherBoundary?.let { minOf(eventBoundary, it) } ?: eventBoundary)
        if (
            allowWeatherRefresh && installedConfigs.any { (id, config) ->
                renderer.isExpandedLayout(id) && weatherCanRefresh(result, config)
            }
        ) {
            refreshWeatherAfterLocalRender()
        }
    }

    private suspend fun cachedWeatherText(
        result: NextEventResult,
        config: WidgetInstancePreferences,
    ): String? {
        val shift = weatherShift(result, config) ?: return null
        val global = weatherRuntime.preferences.current()
        val forecast = weatherRuntime.repository.latest()
        return widgetWeatherTextFromCache(shift, global, forecast, result.referenceInstant)
    }

    private suspend fun weatherCanRefresh(
        result: NextEventResult,
        config: WidgetInstancePreferences,
    ): Boolean {
        if (weatherShift(result, config) == null) return false
        val global = weatherRuntime.preferences.current()
        return global.enabled && global.providerExplanationAccepted
    }

    private suspend fun weatherExpiryBoundary(
        result: NextEventResult,
        configs: Map<Int, WidgetInstancePreferences>,
    ): Instant? {
        val global = weatherRuntime.preferences.current()
        if (!global.enabled || !global.providerExplanationAccepted) return null
        val forecast = weatherRuntime.repository.latest() ?: return null
        if (weatherFreshness(forecast.fetchedAt, result.referenceInstant) != WeatherFreshness.FRESH) return null
        val isVisible = configs.any { (id, config) ->
            if (!renderer.isExpandedLayout(id)) return@any false
            val shift = weatherShift(result, config) ?: return@any false
            summarizeShiftWeather(shift.start, shift.end, forecast).coverage == WeatherCoverage.COMPLETE
        }
        if (!isVisible) return null
        return forecast.fetchedAt.plus(Duration.ofMinutes(60)).plusSeconds(1)
            .takeIf { it > result.referenceInstant }
    }

    private fun weatherShift(
        result: NextEventResult,
        config: WidgetInstancePreferences,
    ): NextEventItem.Shift? {
        if (
            !config.configured || !config.includeWeather || config.privacy != WidgetPrivacy.COMPLETE
        ) return null
        val projection = projectWidget(
            result,
            WidgetProjectionConfig(
                mode = config.mode,
                privacy = config.privacy,
                size = WidgetSize.EXPANDED,
                configured = true,
            ),
        )
        val identity = projection.events.firstOrNull()?.identity ?: return null
        return result.events.firstOrNull { it.identity == identity } as? NextEventItem.Shift
    }

    private fun refreshWeatherAfterLocalRender() {
        if (weatherRefreshJob?.isActive == true) return
        weatherRefreshJob = scope.launch {
            try {
                weatherRuntime.refreshIfEnabled(force = false)
                refreshNow(allowWeatherRefresh = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // El evento local ya fue renderizado; Clima se degrada a ausencia de dato.
            }
        }
    }

    private suspend fun awaitRestorations(ids: IntArray) {
        ids.validIds().map { id -> restoreBarriers[id] }.filterNotNull().forEach { it.await() }
    }

    private fun stopBackgroundWork() {
        observationJob?.cancel()
        observationJob = null
        observationDate = null
        latestSource = null
        weatherRefreshJob?.cancel()
        weatherRefreshJob = null
        scheduler.cancel()
        unregisterConfigurationReceiver()
    }

    private fun registerConfigurationReceiver() {
        synchronized(configurationReceiverLock) {
            if (!configurationReceiverRegistered) {
                ContextCompat.registerReceiver(
                    context.applicationContext,
                    configurationReceiver,
                    IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                configurationReceiverRegistered = true
            }
        }
    }

    private fun unregisterConfigurationReceiver() {
        synchronized(configurationReceiverLock) {
            if (configurationReceiverRegistered) {
                runCatching { context.applicationContext.unregisterReceiver(configurationReceiver) }
                configurationReceiverRegistered = false
            }
        }
    }

    private fun currentDate(): LocalDate = clock.instant().atZone(AppDefaults.zoneId()).toLocalDate()

    private fun recoveryBoundary(): Instant = currentDate().plusDays(1)
        .atStartOfDay(AppDefaults.zoneId())
        .toInstant()

    private fun IntArray.validIds(): IntArray = filter { it > 0 }.distinct().toIntArray()

    private companion object {
        const val SOURCE_TIMEOUT_MILLIS = 6_000L
    }
}

internal class WidgetConfigurationChangeReceiver(
    private val onConfigurationChanged: () -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_CONFIGURATION_CHANGED) onConfigurationChanged()
    }
}

internal fun widgetWeatherTextFromCache(
    shift: NextEventItem.Shift,
    global: WeatherPreferences,
    forecast: WeatherForecast?,
    now: Instant,
): String? {
    if (!global.enabled || !global.providerExplanationAccepted) return null
    val cached = forecast ?: return null
    if (weatherFreshness(cached.fetchedAt, now) != WeatherFreshness.FRESH) return null
    val summary = summarizeShiftWeather(shift.start, shift.end, cached)
    if (summary.coverage != WeatherCoverage.COMPLETE) return null
    return formatWeatherForNotification(summary, global.unitSystem)
}

internal suspend fun runWidgetReceiverWork(
    timeoutMillis: Long = 9_000L,
    finish: () -> Unit,
    block: suspend () -> Unit,
): Boolean = try {
    withTimeout(timeoutMillis) { block() }
    true
} catch (_: TimeoutCancellationException) {
    false
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
} finally {
    finish()
}
