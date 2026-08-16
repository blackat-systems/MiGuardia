package com.blackatsystems.miguardia

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.weather.WeatherFailureKind
import com.blackatsystems.miguardia.core.domain.weather.WeatherLocation
import com.blackatsystems.miguardia.weather.OpenMeteoForecastClient
import com.blackatsystems.miguardia.weather.WeatherClientResult
import com.blackatsystems.miguardia.weather.WeatherConnectionFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.security.Principal
import java.security.cert.Certificate
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenMeteoClientInstrumentedTest {
    @Test
    fun valid200ParsesCanonicalForecast() = runBlocking {
        val result = client(FakeConnection(200, VALID_JSON.toByteArray())).fetch(LOCATION)
        assertTrue(result is WeatherClientResult.Success)
        assertEquals(2, (result as WeatherClientResult.Success).forecast.hours.size)
    }

    @Test
    fun rateLimitPreservesRetryAfter() = runBlocking {
        val result = client(FakeConnection(429, retryAfter = "120")).fetch(LOCATION)
        assertTrue(result is WeatherClientResult.Failure)
        val failure = (result as WeatherClientResult.Failure).error
        assertEquals(WeatherFailureKind.RATE_LIMITED, failure.kind)
        assertEquals(120L, failure.retryAfter?.seconds)
    }

    @Test
    fun redirectsAndServerErrorsAreTypedWithoutFollowingThem() = runBlocking {
        val redirect = client(FakeConnection(302)).fetch(LOCATION) as WeatherClientResult.Failure
        val server = client(FakeConnection(503)).fetch(LOCATION) as WeatherClientResult.Failure
        assertEquals(WeatherFailureKind.CLIENT_ERROR, redirect.error.kind)
        assertEquals(WeatherFailureKind.SERVER_ERROR, server.error.kind)
    }

    @Test
    fun htmlAndOversizedBodiesAreRejected() = runBlocking {
        val html = client(FakeConnection(200, "<html>error</html>".toByteArray(), "text/html")).fetch(LOCATION)
        val large = client(FakeConnection(200, ByteArray(1024 * 1024 + 1) { 'x'.code.toByte() })).fetch(LOCATION)
        assertEquals(WeatherFailureKind.INVALID_RESPONSE, (html as WeatherClientResult.Failure).error.kind)
        assertEquals(WeatherFailureKind.INVALID_RESPONSE, (large as WeatherClientResult.Failure).error.kind)
    }

    @Test
    fun socketTimeoutIsTypedAsOfflineWithoutRetryLoop() = runBlocking {
        val result = client(FakeConnection(200, timeoutOnRead = true)).fetch(LOCATION)
        assertEquals(WeatherFailureKind.OFFLINE_OR_TIMEOUT, (result as WeatherClientResult.Failure).error.kind)
    }

    private fun client(connection: FakeConnection) = OpenMeteoForecastClient(
        clock = Clock.fixed(FETCHED_AT, ZoneId.of("UTC")),
        connectionFactory = WeatherConnectionFactory { connection },
    )

    private class FakeConnection(
        private val code: Int,
        private val body: ByteArray = ByteArray(0),
        private val type: String = "application/json",
        private val retryAfter: String? = null,
        private val timeoutOnRead: Boolean = false,
    ) : HttpsURLConnection(URL("https://api.open-meteo.com/v1/forecast")) {
        override fun getResponseCode(): Int = code
        override fun getContentType(): String = type
        override fun getHeaderField(name: String?): String? = if (name.equals("Retry-After", true)) retryAfter else null
        override fun getInputStream(): InputStream {
            if (timeoutOnRead) throw SocketTimeoutException("fixture")
            return ByteArrayInputStream(body)
        }
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
        override fun getCipherSuite(): String = "TLS_AES_128_GCM_SHA256"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getPeerPrincipal(): Principal? = null
        override fun getLocalPrincipal(): Principal? = null
    }

    private companion object {
        val FETCHED_AT: Instant = Instant.parse("2026-04-15T10:00:00Z")
        val LOCATION = WeatherLocation(
            "cordoba-capital", "Córdoba Capital, Argentina", -31.4201, -64.1888, ZoneId.of("America/Argentina/Cordoba"),
        )
        const val VALID_JSON = """
            {"timezone":"America/Argentina/Cordoba","hourly_units":{"time":"unixtime","temperature_2m":"°C","apparent_temperature":"°C","precipitation_probability":"%","precipitation":"mm","weather_code":"wmo code","wind_speed_10m":"km/h","wind_gusts_10m":"km/h","wind_direction_10m":"°"},"hourly":{"time":[1776312000,1776315600],"temperature_2m":[20,21],"apparent_temperature":[19,20],"precipitation_probability":[40,50],"precipitation":[0.2,0.4],"weather_code":[61,0],"wind_speed_10m":[12,10],"wind_gusts_10m":[20,18],"wind_direction_10m":[180,190]}}
        """
    }
}
