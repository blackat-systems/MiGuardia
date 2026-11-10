package com.blackatsystems.miguardia.stage14

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.weather.WeatherFailure
import com.blackatsystems.miguardia.core.domain.weather.WeatherFailureKind
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import com.blackatsystems.miguardia.weather.WeatherClientResult
import com.blackatsystems.miguardia.weather.WeatherPreferences
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Stage 14 · Clima in complete notifications: WeatherRuntime.notificationTextFromCache only speaks
 * with a fresh, complete cache and every consent in place. It needs a Context for its files
 * directory, so it runs instrumented over a TemporaryFolder; it never reaches the client.
 */
@RunWith(AndroidJUnit4::class)
class Stage14WeatherNotificationCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    // A236
    @Test
    fun freshCompleteCacheGivesTheNotificationTextInTheChosenUnit() = runBlocking<Unit> {
        val harness = harness("fresh-complete")
        try {
            harness.preferences.enableAfterExplanation()
            harness.preferences.setIncludeInNotifications(true)
            harness.writeCache(stage14WeatherForecast(harness.location))

            assertEquals(
                "Clima: Lluvia · 18–25 °C · lluvia 75 %",
                harness.runtime.notificationTextFromCache(harness.shift, STAGE14_WEATHER_NOW),
            )

            harness.preferences.setUnitSystem(WeatherUnitSystem.FAHRENHEIT)
            assertEquals(
                "Clima: Lluvia · 64–77 °F · lluvia 75 %",
                harness.runtime.notificationTextFromCache(harness.shift, STAGE14_WEATHER_NOW),
            )
            assertEquals(0, harness.client.calls)
        } finally {
            harness.close()
        }
    }

    // A236, C084
    @Test
    fun cacheStopsFeedingNotificationsOnceItIsOlderThanOneHour() = runBlocking<Unit> {
        val harness = harness("freshness")
        try {
            harness.preferences.enableAfterExplanation()
            harness.preferences.setIncludeInNotifications(true)
            harness.writeCache(stage14WeatherForecast(harness.location))
            val fetchedAt = STAGE14_WEATHER_NOW

            // Exactly sixty minutes old is still fresh.
            assertEquals(
                "Clima: Lluvia · 18–25 °C · lluvia 75 %",
                harness.runtime.notificationTextFromCache(harness.shift, fetchedAt.plus(Duration.ofMinutes(60))),
            )
            // Old (visible in the app, but not fresh).
            assertNull(harness.runtime.notificationTextFromCache(harness.shift, fetchedAt.plus(Duration.ofMinutes(61))))
            assertNull(harness.runtime.notificationTextFromCache(harness.shift, fetchedAt.plus(Duration.ofHours(6))))
            // Expired.
            assertNull(harness.runtime.notificationTextFromCache(harness.shift, fetchedAt.plus(Duration.ofHours(7))))
            // A cache "from the future" is not trusted either.
            assertNull(harness.runtime.notificationTextFromCache(harness.shift, fetchedAt.minusSeconds(1)))
            assertEquals(0, harness.client.calls)
        } finally {
            harness.close()
        }
    }

    // A236
    @Test
    fun partialCoverageOrNoCacheNeverGivesNotificationText() = runBlocking<Unit> {
        val harness = harness("partial")
        try {
            harness.preferences.enableAfterExplanation()
            harness.preferences.setIncludeInNotifications(true)

            assertNull(harness.runtime.notificationTextFromCache(harness.shift, STAGE14_WEATHER_NOW))

            // Fresh, but only the first four of the eight hours of the shift.
            harness.writeCache(stage14WeatherForecast(harness.location, hourCount = 4))
            assertNull(harness.runtime.notificationTextFromCache(harness.shift, STAGE14_WEATHER_NOW))

            // Fresh, but entirely after the shift.
            harness.writeCache(
                stage14WeatherForecast(harness.location, from = STAGE14_WEATHER_SHIFT_END, hourCount = 4),
            )
            assertNull(harness.runtime.notificationTextFromCache(harness.shift, STAGE14_WEATHER_NOW))
            assertEquals(0, harness.client.calls)
        } finally {
            harness.close()
        }
    }

    // A236, A234
    @Test
    fun weatherOffOrNotIncludedOrNotExplainedNeverGivesNotificationText() = runBlocking<Unit> {
        val harness = harness("consent")
        try {
            harness.writeCache(stage14WeatherForecast(harness.location))

            // Initial values: Clima off, nothing accepted.
            assertNull(harness.runtime.notificationTextFromCache(harness.shift, STAGE14_WEATHER_NOW))

            // Enabled, but the person did not ask for weather in notifications.
            harness.preferences.enableAfterExplanation()
            assertNull(harness.runtime.notificationTextFromCache(harness.shift, STAGE14_WEATHER_NOW))

            // Asked for it, then turned Clima off.
            harness.preferences.setIncludeInNotifications(true)
            harness.preferences.setEnabled(false)
            assertNull(harness.runtime.notificationTextFromCache(harness.shift, STAGE14_WEATHER_NOW))

            // Enabled and included, but without the accepted provider explanation.
            harness.preferences.replacePortable(
                WeatherPreferences(enabled = true, includeInNotifications = true, providerExplanationAccepted = false),
            )
            assertNull(harness.runtime.notificationTextFromCache(harness.shift, STAGE14_WEATHER_NOW))

            // Control: with the three conditions the same cache does produce text.
            harness.preferences.replacePortable(
                WeatherPreferences(enabled = true, includeInNotifications = true, providerExplanationAccepted = true),
            )
            assertEquals(
                "Clima: Lluvia · 18–25 °C · lluvia 75 %",
                harness.runtime.notificationTextFromCache(harness.shift, STAGE14_WEATHER_NOW),
            )
            assertEquals(0, harness.client.calls)
        } finally {
            harness.close()
        }
    }

    private fun harness(name: String): Stage14WeatherHarness = Stage14WeatherHarness(
        temporaryFolder.newFolder(name),
        Stage14ScriptedWeatherClient {
            WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.UNKNOWN))
        },
    )
}
