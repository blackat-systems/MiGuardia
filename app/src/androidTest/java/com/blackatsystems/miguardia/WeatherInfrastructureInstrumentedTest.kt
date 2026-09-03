package com.blackatsystems.miguardia

import android.Manifest
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.weather.WeatherCondition
import com.blackatsystems.miguardia.core.domain.weather.WeatherLocation
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import com.blackatsystems.miguardia.weather.OpenMeteoJsonParser
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherInfrastructureInstrumentedTest {
    private val location = WeatherLocation(
        "00000000-0000-0000-0000-000000000901",
        "Hospital ficticio",
        -34.6037,
        -58.3816,
        ZoneId.of("America/Argentina/Buenos_Aires"),
    )

    @Test
    fun manifestAllowsOnlyApproximateObjectiveLocationAndRejectsCleartext() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS,
        )
        val permissions = info.requestedPermissions.orEmpty().toSet()
        assertTrue(Manifest.permission.INTERNET in permissions)
        assertTrue(Manifest.permission.ACCESS_COARSE_LOCATION in permissions)
        assertFalse(Manifest.permission.ACCESS_NETWORK_STATE in permissions)
        assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in permissions)
        assertFalse(Manifest.permission.ACCESS_BACKGROUND_LOCATION in permissions)
        assertFalse(Manifest.permission.READ_EXTERNAL_STORAGE in permissions)
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)
    }

    @Test
    fun qaWeatherFilesArePhysicallyUnderQaApplicationId() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assumeTrue(context.packageName.endsWith(".qa"))
        assertTrue(context.filesDir.absolutePath.contains("com.blackatsystems.miguardia.qa"))
        assertFalse(context.filesDir.absolutePath.contains("/com.blackatsystems.miguardia/files"))
    }

    @Test
    fun parserAcceptsCanonicalResponseAndKeepsUnknownCode() {
        val result = OpenMeteoJsonParser().parse(validJson(weatherCode = 777).toByteArray(), location, FETCHED_AT)
        assertEquals(2, result.hours.size)
        assertEquals(WeatherCondition.UNKNOWN, result.hours.first().condition)
        assertEquals(Instant.ofEpochSecond(1_776_312_000L), result.coverageStart)
        assertEquals(Instant.ofEpochSecond(1_776_319_200L), result.coverageEndExclusive)
    }

    @Test
    fun parserKeepsOptionalNullsInsteadOfInventingZeros() {
        val result = OpenMeteoJsonParser().parse(validJson(nullTemperature = true).toByteArray(), location, FETCHED_AT)
        assertNull(result.hours.first().temperatureCelsius)
        assertNull(result.hours.first().apparentTemperatureCelsius)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parserRejectsMisalignedArrays() {
        OpenMeteoJsonParser().parse(validJson().replace("\"temperature_2m\":[20.0,21.0]", "\"temperature_2m\":[20.0]").toByteArray(), location, FETCHED_AT)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parserRejectsUnexpectedUnits() {
        OpenMeteoJsonParser().parse(validJson().replace("\"°C\"", "\"°F\"").toByteArray(), location, FETCHED_AT)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parserRejectsNonHourlyTimestamps() {
        OpenMeteoJsonParser().parse(
            validJson().replace("1776315600", "1776313800").toByteArray(),
            location,
            FETCHED_AT,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun parserRejectsHtml() {
        OpenMeteoJsonParser().parse("<html>error</html>".toByteArray(), location, FETCHED_AT)
    }

    @Test
    fun preferencesUseUniquePrivateFileAndPersistExplicitChoices() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.filesDir, "weather-test-${UUID.randomUUID()}.preferences_pb")
        val store = WeatherPreferencesStore(file, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        assertFalse(store.current().enabled)
        assertEquals(WeatherUnitSystem.CELSIUS, store.current().unitSystem)
        store.enableAfterExplanation()
        store.setUnitSystem(WeatherUnitSystem.FAHRENHEIT)
        store.setIncludeInNotifications(true)
        val persisted = store.current()
        assertTrue(persisted.enabled)
        assertTrue(persisted.providerExplanationAccepted)
        assertTrue(persisted.includeInNotifications)
        assertEquals(WeatherUnitSystem.FAHRENHEIT, persisted.unitSystem)
        file.delete()
        Unit
    }

    private fun validJson(weatherCode: Int = 61, nullTemperature: Boolean = false): String {
        val temperatures = if (nullTemperature) "[null,21.0]" else "[20.0,21.0]"
        val apparent = if (nullTemperature) "[null,20.0]" else "[19.0,20.0]"
        return """
            {
              "timezone":"America/Argentina/Buenos_Aires",
              "hourly_units":{
                "time":"unixtime","temperature_2m":"°C","apparent_temperature":"°C",
                "precipitation_probability":"%","precipitation":"mm","weather_code":"wmo code",
                "wind_speed_10m":"km/h","wind_gusts_10m":"km/h","wind_direction_10m":"°"
              },
              "hourly":{
                "time":[1776312000,1776315600],
                "temperature_2m":$temperatures,
                "apparent_temperature":$apparent,
                "precipitation_probability":[40,50],"precipitation":[0.2,0.4],
                "weather_code":[$weatherCode,0],"wind_speed_10m":[12.0,10.0],
                "wind_gusts_10m":[20.0,18.0],"wind_direction_10m":[180,190]
              }
            }
        """.trimIndent()
    }

    private companion object {
        val FETCHED_AT: Instant = Instant.parse("2026-04-15T10:00:00Z")
    }
}
