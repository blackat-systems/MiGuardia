package com.blackatsystems.miguardia.weather

import com.blackatsystems.miguardia.core.domain.weather.WeatherFailure
import com.blackatsystems.miguardia.core.domain.weather.WeatherFailureKind
import com.blackatsystems.miguardia.core.domain.weather.WeatherForecast
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.core.domain.weather.WeatherLocation
import com.blackatsystems.miguardia.core.domain.weather.WeatherRefreshResult
import com.blackatsystems.miguardia.core.domain.weather.WeatherRepository
import com.blackatsystems.miguardia.core.domain.weather.weatherFreshness
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DefaultWeatherRepository(
    private val location: WeatherLocation,
    private val client: WeatherForecastClient,
    private val cache: WeatherCacheStore,
    private val preferences: WeatherPreferencesStore,
    private val clock: Clock,
) : WeatherRepository {
    private val mutex = Mutex()
    @Volatile private var activeRefresh: ActiveRefresh? = null

    override suspend fun latest(): WeatherForecast? = cache.read()

    override suspend fun refreshIfStale(force: Boolean): WeatherRefreshResult {
        val callerJob = checkNotNull(currentCoroutineContext()[Job])
        val selection = mutex.withLock {
            activeRefresh?.let { return@withLock RefreshSelection(it, ownsRefresh = false) }
            val cached = cache.read()
            val now = clock.instant()
            val retryUntil = preferences.current().retryAfterUntilEpochMillis?.let(java.time.Instant::ofEpochMilli)
            if (retryUntil != null && retryUntil > now) {
                val result = WeatherRefreshResult.Failure(
                    WeatherFailure(WeatherFailureKind.RATE_LIMITED, Duration.between(now, retryUntil)),
                    cached,
                )
                return@withLock RefreshSelection.immediate(result, callerJob)
            }
            if (!force && cached != null && weatherFreshness(cached.fetchedAt, now) == WeatherFreshness.FRESH) {
                return@withLock RefreshSelection.immediate(
                    WeatherRefreshResult.Success(cached, downloaded = false),
                    callerJob,
                )
            }
            preferences.recordRefreshAttempt(now.toEpochMilli())
            val active = ActiveRefresh(CompletableDeferred(), callerJob, cached)
            activeRefresh = active
            RefreshSelection(active, ownsRefresh = true)
        }
        if (!selection.ownsRefresh) return selection.active.result.await()

        return try {
            val result = download(selection.active.cachedForecast)
            selection.active.result.complete(result)
            result
        } catch (error: CancellationException) {
            selection.active.result.cancel(error)
            throw error
        } finally {
            mutex.withLock {
                if (activeRefresh === selection.active) activeRefresh = null
            }
        }
    }

    override suspend fun clearCache() {
        cache.clear()
    }

    fun cancelActiveRefresh() {
        activeRefresh?.ownerJob?.cancel()
    }

    private suspend fun download(cached: WeatherForecast?): WeatherRefreshResult = when (val result = client.fetch(location)) {
        is WeatherClientResult.Success -> try {
            cache.write(result.forecast)
            preferences.setRetryAfterUntil(null)
            WeatherRefreshResult.Success(result.forecast, downloaded = true)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            WeatherRefreshResult.Failure(
                WeatherFailure(WeatherFailureKind.CACHE_ERROR),
                cached,
            )
        }
        is WeatherClientResult.Failure -> {
            if (result.error.kind == WeatherFailureKind.RATE_LIMITED) {
                result.error.retryAfter?.let { delay ->
                    preferences.setRetryAfterUntil(clock.instant().plus(delay).toEpochMilli())
                }
            }
            WeatherRefreshResult.Failure(result.error, cached)
        }
    }

    private data class ActiveRefresh(
        val result: CompletableDeferred<WeatherRefreshResult>,
        val ownerJob: Job,
        val cachedForecast: WeatherForecast?,
    )

    private data class RefreshSelection(
        val active: ActiveRefresh,
        val ownsRefresh: Boolean,
    ) {
        companion object {
            fun immediate(result: WeatherRefreshResult, callerJob: Job): RefreshSelection = RefreshSelection(
                ActiveRefresh(CompletableDeferred(result), callerJob, cachedForecast = null),
                ownsRefresh = false,
            )
        }
    }
}
