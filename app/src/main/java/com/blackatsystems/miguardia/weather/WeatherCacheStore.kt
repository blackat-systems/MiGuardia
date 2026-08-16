package com.blackatsystems.miguardia.weather

import com.blackatsystems.miguardia.core.domain.weather.WeatherCondition
import com.blackatsystems.miguardia.core.domain.weather.WeatherForecast
import com.blackatsystems.miguardia.core.domain.weather.WeatherHour
import com.blackatsystems.miguardia.core.domain.weather.WeatherLocation
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeatherCacheStore(private val root: File) {
    suspend fun read(): WeatherForecast? = withContext(Dispatchers.IO) {
        val target = File(root, CACHE_FILE)
        if (!target.isFile) return@withContext null
        runCatching { decode(target) }.getOrNull()
    }

    suspend fun write(forecast: WeatherForecast) = withContext(Dispatchers.IO) {
        if (!root.exists() && !root.mkdirs()) throw IOException("No se pudo preparar el caché meteorológico.")
        val temporary = File(root, TEMP_FILE)
        val target = File(root, CACHE_FILE)
        try {
            encode(temporary, forecast)
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        root.listFiles()?.filter { it.name.startsWith(OWNED_PREFIX) }?.forEach(File::delete)
    }

    private fun encode(file: File, forecast: WeatherForecast) {
        val rawOutput = FileOutputStream(file)
        DataOutputStream(BufferedOutputStream(rawOutput)).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeUTF(forecast.providerId)
            output.writeUTF(forecast.location.id)
            output.writeUTF(forecast.location.displayName)
            output.writeDouble(forecast.location.latitude)
            output.writeDouble(forecast.location.longitude)
            output.writeUTF(forecast.location.zoneId.id)
            output.writeLong(forecast.fetchedAt.toEpochMilli())
            output.writeLong(forecast.coverageStart.toEpochMilli())
            output.writeLong(forecast.coverageEndExclusive.toEpochMilli())
            output.writeInt(forecast.hours.size)
            forecast.hours.forEach { hour ->
                output.writeLong(hour.validFrom.toEpochMilli())
                output.writeLong(hour.validUntilExclusive.toEpochMilli())
                output.writeNullableDouble(hour.temperatureCelsius)
                output.writeNullableDouble(hour.apparentTemperatureCelsius)
                output.writeNullableDouble(hour.precipitationMillimeters)
                output.writeNullableInt(hour.precipitationProbabilityPercent)
                output.writeNullableInt(hour.weatherCode)
                output.writeUTF(hour.condition.name)
                output.writeNullableDouble(hour.windSpeedKmh)
                output.writeNullableDouble(hour.windGustKmh)
                output.writeNullableDouble(hour.windDirectionDegrees)
            }
            output.flush()
            rawOutput.fd.sync()
        }
    }

    private fun decode(file: File): WeatherForecast {
        if (file.length() !in 1..MAX_CACHE_BYTES) throw IOException("Caché inválido")
        return DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != VERSION) throw IOException("Versión de caché desconocida")
            val provider = input.readUTF()
            val location = WeatherLocation(
                id = input.readUTF(),
                displayName = input.readUTF(),
                latitude = input.readDouble(),
                longitude = input.readDouble(),
                zoneId = ZoneId.of(input.readUTF()),
            )
            val fetchedAt = Instant.ofEpochMilli(input.readLong())
            val coverageStart = Instant.ofEpochMilli(input.readLong())
            val coverageEnd = Instant.ofEpochMilli(input.readLong())
            val count = input.readInt()
            if (count !in 1..MAX_HOURS) throw IOException("Cantidad de horas inválida")
            val hours = List(count) {
                WeatherHour(
                    validFrom = Instant.ofEpochMilli(input.readLong()),
                    validUntilExclusive = Instant.ofEpochMilli(input.readLong()),
                    temperatureCelsius = input.readNullableDouble(),
                    apparentTemperatureCelsius = input.readNullableDouble(),
                    precipitationMillimeters = input.readNullableDouble(),
                    precipitationProbabilityPercent = input.readNullableInt(),
                    weatherCode = input.readNullableInt(),
                    condition = WeatherCondition.valueOf(input.readUTF()),
                    windSpeedKmh = input.readNullableDouble(),
                    windGustKmh = input.readNullableDouble(),
                    windDirectionDegrees = input.readNullableDouble(),
                )
            }
            if (input.read() != -1) throw IOException("Datos extra en caché")
            WeatherForecast(provider, location, fetchedAt, coverageStart, coverageEnd, hours)
        }
    }

    private fun DataOutputStream.writeNullableDouble(value: Double?) {
        writeBoolean(value != null)
        if (value != null) writeDouble(value)
    }

    private fun DataOutputStream.writeNullableInt(value: Int?) {
        writeBoolean(value != null)
        if (value != null) writeInt(value)
    }

    private fun DataInputStream.readNullableDouble(): Double? = if (readBoolean()) readDouble() else null
    private fun DataInputStream.readNullableInt(): Int? = if (readBoolean()) readInt() else null

    companion object {
        const val DIRECTORY_NAME = "weather_cache"
        const val OWNED_PREFIX = "weather_"
        const val CACHE_FILE = "weather_v1.cache"
        const val TEMP_FILE = "weather_v1.tmp"
        const val MAGIC = 0x4D475743
        const val VERSION = 1
        const val MAX_HOURS = 16 * 24
        const val MAX_CACHE_BYTES = 2L * 1024L * 1024L
        fun inFilesDir(filesDir: File): WeatherCacheStore = WeatherCacheStore(File(filesDir, DIRECTORY_NAME))
    }
}
