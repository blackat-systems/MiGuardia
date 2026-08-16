package com.blackatsystems.miguardia.weather

import com.blackatsystems.miguardia.core.domain.weather.WeatherFailure
import com.blackatsystems.miguardia.core.domain.weather.WeatherFailureKind
import com.blackatsystems.miguardia.core.domain.weather.WeatherForecast
import com.blackatsystems.miguardia.core.domain.weather.WeatherHour
import com.blackatsystems.miguardia.core.domain.weather.WeatherLocation
import com.blackatsystems.miguardia.core.domain.weather.wmoWeatherCondition
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal sealed interface WeatherClientResult {
    data class Success(val forecast: WeatherForecast) : WeatherClientResult
    data class Failure(val error: WeatherFailure) : WeatherClientResult
}

internal fun interface WeatherForecastClient {
    suspend fun fetch(location: WeatherLocation): WeatherClientResult
}

internal fun interface WeatherConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

internal class OpenMeteoForecastClient(
    private val clock: Clock,
    private val connectionFactory: WeatherConnectionFactory = WeatherConnectionFactory { url -> url.openConnection() as HttpURLConnection },
    private val parser: OpenMeteoJsonParser = OpenMeteoJsonParser(),
) : WeatherForecastClient {
    override suspend fun fetch(location: WeatherLocation): WeatherClientResult = withContext(Dispatchers.IO) {
        try {
            val url = buildOpenMeteoUrl(location)
            val connection = connectionFactory.open(url)
            if (connection !is HttpsURLConnection || url.protocol != "https" || url.host != HOST) {
                connection.disconnect()
                return@withContext WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.CLIENT_ERROR))
            }
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            try {
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val contentType = connection.contentType.orEmpty().substringBefore(';').trim()
                        if (contentType != "application/json") {
                            WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.INVALID_RESPONSE))
                        } else {
                            val body = connection.inputStream.use { input -> readLimited(input) }
                            WeatherClientResult.Success(parser.parse(body, location, clock.instant()))
                        }
                    }
                    429 -> WeatherClientResult.Failure(
                        WeatherFailure(WeatherFailureKind.RATE_LIMITED, parseRetryAfter(connection.getHeaderField("Retry-After"), clock.instant())),
                    )
                    in 400..499 -> WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.CLIENT_ERROR))
                    in 500..599 -> WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.SERVER_ERROR))
                    else -> WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.CLIENT_ERROR))
                }
            } finally {
                connection.disconnect()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: SocketTimeoutException) {
            WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.OFFLINE_OR_TIMEOUT))
        } catch (_: UnknownHostException) {
            WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.OFFLINE_OR_TIMEOUT))
        } catch (_: ConnectException) {
            WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.OFFLINE_OR_TIMEOUT))
        } catch (_: InvalidWeatherBodyException) {
            WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.INVALID_RESPONSE))
        } catch (_: IOException) {
            WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.OFFLINE_OR_TIMEOUT))
        } catch (_: Exception) {
            WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.INVALID_RESPONSE))
        }
    }

    private suspend fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > MAX_RESPONSE_BYTES) throw InvalidWeatherBodyException()
            output.write(buffer, 0, read)
        }
        if (output.size() == 0) throw InvalidWeatherBodyException()
        return output.toByteArray()
    }

    private companion object {
        const val HOST = "api.open-meteo.com"
        const val CONNECT_TIMEOUT_MILLIS = 4_000
        const val READ_TIMEOUT_MILLIS = 6_000
        const val MAX_RESPONSE_BYTES = 1024 * 1024
        const val USER_AGENT = "MiGuardia-Weather/1"
    }
}

private class InvalidWeatherBodyException : IOException()

internal fun buildOpenMeteoUrl(location: WeatherLocation): URL {
    val hourly = "temperature_2m,apparent_temperature,precipitation_probability,precipitation," +
        "weather_code,wind_speed_10m,wind_gusts_10m,wind_direction_10m"
    return URL(
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${location.latitude}" +
            "&longitude=${location.longitude}" +
            "&hourly=$hourly" +
            "&forecast_days=16" +
            "&timezone=America%2FArgentina%2FCordoba" +
            "&timeformat=unixtime" +
            "&temperature_unit=celsius" +
            "&precipitation_unit=mm" +
            "&wind_speed_unit=kmh",
    )
}

internal class OpenMeteoJsonParser {
    fun parse(body: ByteArray, location: WeatherLocation, fetchedAt: Instant): WeatherForecast {
        val text = body.toString(Charsets.UTF_8).trim()
        if (!text.startsWith('{')) throw IllegalArgumentException("JSON inválido")
        val root = JSONObject(text)
        require(root.optString("timezone") == location.zoneId.id)
        val units = root.requireObject("hourly_units")
        require(units.optString("time") == "unixtime")
        require(units.optString("temperature_2m") == "°C")
        require(units.optString("apparent_temperature") == "°C")
        require(units.optString("precipitation_probability") == "%")
        require(units.optString("precipitation") == "mm")
        require(units.optString("weather_code") == "wmo code")
        require(units.optString("wind_speed_10m") == "km/h")
        require(units.optString("wind_gusts_10m") == "km/h")
        require(units.optString("wind_direction_10m") == "°")
        val hourly = root.requireObject("hourly")
        val times = hourly.requireArray("time")
        val temperatures = hourly.requireAligned("temperature_2m", times.length())
        val apparent = hourly.requireAligned("apparent_temperature", times.length())
        val probabilities = hourly.requireAligned("precipitation_probability", times.length())
        val precipitation = hourly.requireAligned("precipitation", times.length())
        val weatherCodes = hourly.requireAligned("weather_code", times.length())
        val wind = hourly.requireAligned("wind_speed_10m", times.length())
        val gusts = hourly.requireAligned("wind_gusts_10m", times.length())
        val directions = hourly.requireAligned("wind_direction_10m", times.length())
        require(times.length() in 1..16 * 24)
        val instants = List(times.length()) { index ->
            val seconds = times.requireLong(index)
            Instant.ofEpochSecond(seconds)
        }
        require(instants.zipWithNext().all { (left, right) -> Duration.between(left, right) == Duration.ofHours(1) })
        val hours = instants.mapIndexed { index, from ->
            val until = instants.getOrNull(index + 1) ?: from.plusSeconds(3600)
            WeatherHour(
                validFrom = from,
                validUntilExclusive = until,
                temperatureCelsius = temperatures.optionalDouble(index),
                apparentTemperatureCelsius = apparent.optionalDouble(index),
                precipitationMillimeters = precipitation.optionalDouble(index),
                precipitationProbabilityPercent = probabilities.optionalInteger(index),
                weatherCode = weatherCodes.optionalInteger(index),
                condition = wmoWeatherCondition(weatherCodes.optionalInteger(index)),
                windSpeedKmh = wind.optionalDouble(index),
                windGustKmh = gusts.optionalDouble(index),
                windDirectionDegrees = directions.optionalDouble(index),
            )
        }
        return WeatherForecast(
            providerId = "open-meteo",
            location = location,
            fetchedAt = fetchedAt,
            coverageStart = hours.first().validFrom,
            coverageEndExclusive = hours.last().validUntilExclusive,
            hours = hours,
        )
    }
}

private fun JSONObject.requireObject(key: String): JSONObject = optJSONObject(key)
    ?: throw IllegalArgumentException("Falta $key")

private fun JSONObject.requireArray(key: String): JSONArray = optJSONArray(key)
    ?: throw IllegalArgumentException("Falta $key")

private fun JSONObject.requireAligned(key: String, expected: Int): JSONArray = requireArray(key).also {
    require(it.length() == expected)
}

private fun JSONArray.requireLong(index: Int): Long {
    val value = opt(index) as? Number ?: throw IllegalArgumentException("Número inválido")
    val asDouble = value.toDouble()
    require(asDouble.isFinite() && asDouble == value.toLong().toDouble())
    return value.toLong()
}

private fun JSONArray.optionalDouble(index: Int): Double? {
    val value = opt(index)
    if (value == null || value == JSONObject.NULL) return null
    val number = (value as? Number)?.toDouble() ?: throw IllegalArgumentException("Número inválido")
    require(number.isFinite())
    return number
}

private fun JSONArray.optionalInteger(index: Int): Int? {
    val value = optionalDouble(index) ?: return null
    require(value == value.toInt().toDouble())
    return value.toInt()
}

private fun parseRetryAfter(value: String?, now: Instant): Duration? {
    if (value.isNullOrBlank()) return null
    value.toLongOrNull()?.takeIf { it >= 0L }?.let { return Duration.ofSeconds(it) }
    val retryAt = runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
        ?: return null
    return Duration.between(now, retryAt).takeUnless(Duration::isNegative)
}
