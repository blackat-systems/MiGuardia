package com.blackatsystems.miguardia.stage14

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.weather.WeatherFailure
import com.blackatsystems.miguardia.core.domain.weather.WeatherFailureKind
import com.blackatsystems.miguardia.core.domain.weather.WeatherLocation
import com.blackatsystems.miguardia.weather.WeatherClientResult
import com.blackatsystems.miguardia.weather.WeatherForecastClient
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherRuntime
import com.blackatsystems.miguardia.widget.NextEventAppWidgetProvider
import com.blackatsystems.miguardia.widget.WidgetBoundaryScheduler
import java.io.File
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Programador de mentira: anota, no toca AlarmManager ni la alarma de producción. */
internal class Stage14WidgetRecordingScheduler : WidgetBoundaryScheduler {
    val scheduled = CopyOnWriteArrayList<Instant>()
    val cancels = AtomicInteger()

    override fun schedule(triggerAt: Instant) {
        scheduled += triggerAt
    }

    override fun cancel() {
        cancels.incrementAndGet()
    }
}

/** Cliente de Clima de mentira: cuenta pedidos y responde «sin red», sin abrir conexiones. */
internal class Stage14WidgetOfflineWeatherClient : WeatherForecastClient {
    val calls = AtomicInteger()

    override suspend fun fetch(location: WeatherLocation): WeatherClientResult {
        calls.incrementAndGet()
        return WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.OFFLINE_OR_TIMEOUT))
    }
}

/** Contexto cuyo `filesDir` es una carpeta temporal: la caché de Clima no cae en `files/` de la app. */
internal class Stage14WidgetFilesContext(
    base: Context,
    private val root: File,
) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = root
}

internal object Stage14WidgetNoObjectives : ObjectiveRepository {
    override fun observeActive(): Flow<List<Objective>> = flowOf(emptyList())
    override fun observeAll(): Flow<List<Objective>> = flowOf(emptyList())
    override suspend fun getById(id: UUID): Objective? = null
}

internal fun stage14WidgetWeatherRuntime(
    context: Context,
    folder: File,
    scope: CoroutineScope,
    clock: Clock,
    client: WeatherForecastClient,
): WeatherRuntime = WeatherRuntime(
    context = Stage14WidgetFilesContext(context, folder),
    preferences = WeatherPreferencesStore(File(folder, "stage14-widget-weather.preferences_pb"), scope),
    objectives = Stage14WidgetNoObjectives,
    clock = clock,
    client = client,
)

/** Consulta de sólo lectura: instancias del Widget real puestas en la pantalla de inicio. */
internal fun stage14InstalledWidgetIds(context: Context): IntArray =
    AppWidgetManager.getInstance(context).getAppWidgetIds(
        ComponentName(context, NextEventAppWidgetProvider::class.java),
    )

/**
 * El motor sólo publica cuando la Application terminó su recuperación de arranque
 * (`LocalRuntimeActivation.kt:10-13`). Acá sólo se lee ese estado, nunca se cambia.
 */
internal fun stage14WidgetStartupIsReady(context: Context, timeoutMillis: Long = 10_000L): Boolean {
    val application = context.applicationContext as? MiGuardiaApplication ?: return false
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        if (application.startupRecoveryGate.isReady) return true
        Thread.sleep(50)
    }
    return application.startupRecoveryGate.isReady
}

/** Espera activa corta para efectos que el motor produce en su propio hilo. */
internal fun stage14WidgetWaitUntil(timeoutMillis: Long = 5_000L, condition: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return true
        Thread.sleep(25)
    }
    return condition()
}

// ---- Segunda vuelta: dobles para observar el motor sin Widget instalado ----

/** Un pedido de dibujo recibido por el dibujante de mentira. */
internal data class Stage14WidgetDraw(
    val id: Int,
    val result: com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult,
    val preferences: com.blackatsystems.miguardia.widget.WidgetInstancePreferences,
    val weatherText: String?,
) {
    val shiftIds: List<UUID>
        get() = result.primaryEvents
            .filterIsInstance<com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem.Shift>()
            .map { it.shiftId }
}

/**
 * Dibujante de mentira: anota cada llamada y nunca consulta ni toca el AppWidgetManager real.
 * Las instancias «puestas» y las «ampliadas» se configuran desde la prueba.
 */
internal class Stage14WidgetRecordingSurface : com.blackatsystems.miguardia.widget.WidgetSurface {
    @Volatile
    var installed: IntArray = IntArray(0)
    val expanded: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    val draws = CopyOnWriteArrayList<Stage14WidgetDraw>()
    val loading = CopyOnWriteArrayList<List<Int>>()
    val errors = CopyOnWriteArrayList<List<Int>>()
    val recoveries = CopyOnWriteArrayList<List<Int>?>()

    override fun installedIds(): IntArray = installed.copyOf()

    override fun isExpandedLayout(appWidgetId: Int): Boolean = appWidgetId in expanded

    override fun renderRecovery(appWidgetIds: IntArray?) {
        recoveries += appWidgetIds?.toList()
    }

    override fun renderLoading(appWidgetIds: IntArray) {
        loading += appWidgetIds.toList()
    }

    override fun renderRecoverableError(appWidgetIds: IntArray) {
        errors += appWidgetIds.toList()
    }

    override fun render(
        appWidgetId: Int,
        result: com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult,
        preferences: com.blackatsystems.miguardia.widget.WidgetInstancePreferences,
        weatherText: String?,
    ) {
        draws += Stage14WidgetDraw(appWidgetId, result, preferences, weatherText)
    }

    fun lastDraw(id: Int): Stage14WidgetDraw? = draws.lastOrNull { it.id == id }

    /** Espera un dibujo (desde `fromIndex`) que cumpla la condición; falla con lo recibido si no llega. */
    fun awaitDraw(
        what: String,
        fromIndex: Int = 0,
        timeoutMillis: Long = 5_000L,
        predicate: (Stage14WidgetDraw) -> Boolean,
    ): Stage14WidgetDraw {
        var found: Stage14WidgetDraw? = null
        stage14WidgetWaitUntil(timeoutMillis) {
            found = draws.drop(fromIndex).firstOrNull(predicate)
            found != null
        }
        return found ?: throw AssertionError(
            "No llegó el dibujo esperado ($what). Recibidos desde $fromIndex: " +
                draws.drop(fromIndex).joinToString { "id=${it.id} jornadas=${it.shiftIds.size} clima=${it.weatherText}" },
        )
    }
}

/** Todo objetivo consultado «existe» y tiene ubicación ficticia para Clima. */
internal object Stage14WidgetLocatedObjectives : ObjectiveRepository {
    const val NAME = "Objetivo ficticio"
    const val LATITUDE = -31.4201
    const val LONGITUDE = -64.1888

    override fun observeActive(): Flow<List<Objective>> = flowOf(emptyList())
    override fun observeAll(): Flow<List<Objective>> = flowOf(emptyList())
    override suspend fun getById(id: UUID): Objective = Objective(
        id = id,
        fullName = NAME,
        abbreviation = "FIC",
        address = null,
        note = null,
        isActive = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        weatherLatitude = LATITUDE,
        weatherLongitude = LONGITUDE,
    )

    fun locationFor(objectiveId: UUID, zoneId: java.time.ZoneId): WeatherLocation =
        WeatherLocation(objectiveId.toString(), NAME, LATITUDE, LONGITUDE, zoneId)
}

/** Pronóstico ficticio por hora entre dos horas en punto. */
internal fun stage14WidgetForecast(
    location: WeatherLocation,
    from: Instant,
    untilExclusive: Instant,
    fetchedAt: Instant,
): com.blackatsystems.miguardia.core.domain.weather.WeatherForecast {
    val hours = generateSequence(from) { it.plusSeconds(3_600) }
        .takeWhile { it < untilExclusive }
        .map { start ->
            com.blackatsystems.miguardia.core.domain.weather.WeatherHour(
                validFrom = start,
                validUntilExclusive = start.plusSeconds(3_600),
                temperatureCelsius = 20.0,
                apparentTemperatureCelsius = 20.0,
                precipitationMillimeters = 0.0,
                precipitationProbabilityPercent = 0,
                weatherCode = 0,
                condition = com.blackatsystems.miguardia.core.domain.weather.WeatherCondition.CLEAR,
                windSpeedKmh = 5.0,
                windGustKmh = 8.0,
                windDirectionDegrees = 90.0,
            )
        }
        .toList()
    return com.blackatsystems.miguardia.core.domain.weather.WeatherForecast(
        providerId = "open-meteo",
        location = location,
        fetchedAt = fetchedAt,
        coverageStart = hours.first().validFrom,
        coverageEndExclusive = hours.last().validUntilExclusive,
        hours = hours,
    )
}

/**
 * Cliente de Clima de mentira con guion: falla («sin red») o responde bien, y puede quedar
 * esperando en `gate` para simular una descarga lenta. Nunca abre una conexión.
 */
internal class Stage14WidgetScriptedWeatherClient(
    private val clock: Clock,
    private val from: Instant,
    private val untilExclusive: Instant,
) : WeatherForecastClient {
    @Volatile
    var succeed: Boolean = false
    @Volatile
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    val calls = AtomicInteger()

    override suspend fun fetch(location: WeatherLocation): WeatherClientResult {
        calls.incrementAndGet()
        gate?.await()
        return if (succeed) {
            WeatherClientResult.Success(stage14WidgetForecast(location, from, untilExclusive, clock.instant()))
        } else {
            WeatherClientResult.Failure(WeatherFailure(WeatherFailureKind.OFFLINE_OR_TIMEOUT))
        }
    }
}

/** Reloj que avanza 1 ms por consulta: como el real, nunca devuelve dos veces el mismo instante. */
internal class Stage14WidgetTickingClock(
    private val start: Instant,
    private val zoneId: java.time.ZoneId,
) : Clock() {
    private val ticks = java.util.concurrent.atomic.AtomicLong()
    override fun getZone(): java.time.ZoneId = zoneId
    override fun withZone(zone: java.time.ZoneId): Clock = Stage14WidgetTickingClock(start, zone)
    override fun instant(): Instant = start.plusMillis(ticks.incrementAndGet())
}
