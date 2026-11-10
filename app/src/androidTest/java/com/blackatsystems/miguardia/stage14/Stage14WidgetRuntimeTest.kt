package com.blackatsystems.miguardia.stage14

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.V2AppTestFixture
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjectionConfig
import com.blackatsystems.miguardia.core.domain.widget.WidgetSize
import com.blackatsystems.miguardia.core.domain.widget.projectWidget
import com.blackatsystems.miguardia.ui.nextevent.NextEventSourceData
import com.blackatsystems.miguardia.ui.nextevent.V2WorkEventSourceObserver
import com.blackatsystems.miguardia.widget.WidgetInstancePreferences
import com.blackatsystems.miguardia.widget.WidgetPreferencesStore
import com.blackatsystems.miguardia.widget.WidgetRemoteViewsRenderer
import com.blackatsystems.miguardia.widget.WidgetRuntime
import com.blackatsystems.miguardia.widget.buildWidgetRenderText
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Etapa 14 · Widget de inicio: motor (`WidgetRuntime`) aislado.
 *
 * Límite verificado leyendo el código: el dibujante `WidgetRemoteViewsRenderer` es una clase final
 * (`WidgetRemoteViewsRenderer.kt:35`) y sus instancias salen del `AppWidgetManager` real y del
 * proveedor real (`:39-42`, `:382`). Sin un Widget puesto en la pantalla de inicio, el motor corta
 * antes de observar, dibujar, programar un límite o pedir Clima (`WidgetRuntime.kt:110-113`,
 * `:183-189`, `:252-256`). Acá se prueba lo que sí se observa por el programador de mentira, por las
 * preferencias de la prueba y por la fuente de datos que el motor observa.
 *
 * Todas las pruebas del motor se saltean si el teléfono tiene un Widget real puesto: de lo contrario
 * el motor de la prueba podría dibujar sobre él.
 */
@RunWith(AndroidJUnit4::class)
class Stage14WidgetRuntimeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scheduler = Stage14WidgetRecordingScheduler()
    private val weatherClient = Stage14WidgetOfflineWeatherClient()
    private val storeOpens = AtomicInteger()
    private lateinit var storeScope: CoroutineScope
    private lateinit var preferences: WidgetPreferencesStore
    private lateinit var runtime: WidgetRuntime

    @Before
    fun setUp() {
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val folder = temporaryFolder.newFolder("stage14-widget-runtime")
        preferences = WidgetPreferencesStore(File(folder, "stage14-widget.preferences_pb"), storeScope)
        runtime = WidgetRuntime(
            context = context,
            localDataStore = {
                storeOpens.incrementAndGet()
                error("Sin instancias, el motor no debe abrir ninguna base")
            },
            preferences = preferences,
            weatherRuntime = stage14WidgetWeatherRuntime(context, folder, storeScope, CLOCK, weatherClient),
            clock = CLOCK,
            renderer = WidgetRemoteViewsRenderer(context),
            scheduler = scheduler,
        )
    }

    @After
    fun tearDown() {
        runtime.scope.cancel()
        storeScope.cancel()
    }

    // W023 (sin instancias se cancela la alarma) + X015 (no lee datos laborales ni pide Clima)
    @Test
    fun withoutInstancesStartAndRefreshCancelTheAlarmAndNeverReadWorkDataOrAskWeather() = runBlocking<Unit> {
        assumeNoRealWidget()
        assumeTrue("La Application no terminó su arranque.", stage14WidgetStartupIsReady(context))

        runtime.start()

        assertEquals(1, scheduler.cancels.get())

        runtime.refreshNow()

        assertEquals(2, scheduler.cancels.get())
        runtime.refreshAll()
        runtime.refresh(ABSENT_WIDGET_ID)
        assertTrue(stage14WidgetWaitUntil { scheduler.cancels.get() == 4 })
        assertTrue("Sin instancias no se programa ningún límite", scheduler.scheduled.isEmpty())
        assertEquals(0, storeOpens.get())
        assertEquals(0, weatherClient.calls.get())
    }

    // W024 (borrar limpia sólo esa instancia) + W023 (al no quedar ninguna se cancela la alarma)
    @Test
    fun deletingOneInstanceRemovesOnlyItsPreferencesAndCancelsTheAlarmWhenNoneRemain() = runBlocking<Unit> {
        assumeNoRealWidget()
        val kept = WidgetInstancePreferences(
            mode = WidgetMode.NEXT_DAY_OFF,
            privacy = WidgetPrivacy.REDUCED,
            includeWeather = false,
            configured = true,
        )
        val deleted = WidgetInstancePreferences(
            mode = WidgetMode.NEXT_SHIFT,
            privacy = WidgetPrivacy.COMPLETE,
            includeWeather = true,
            configured = true,
        )
        preferences.save(ABSENT_WIDGET_ID, deleted)
        preferences.save(OTHER_ABSENT_WIDGET_ID, kept)

        runtime.deleteNow(intArrayOf(ABSENT_WIDGET_ID))

        assertEquals(mapOf(OTHER_ABSENT_WIDGET_ID to kept), preferences.all())
        assertEquals(WidgetPreferencesStore.SafeDefault, preferences.current(ABSENT_WIDGET_ID))
        assertEquals(1, scheduler.cancels.get())
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(0, storeOpens.get())
        assertEquals(0, weatherClient.calls.get())

        // Repetir el borrado (reintento de una eliminación diferida) no toca a la otra instancia.
        runtime.deleteNow(intArrayOf(ABSENT_WIDGET_ID, 0, -7))

        assertEquals(mapOf(OTHER_ABSENT_WIDGET_ID to kept), preferences.all())
    }

    // W021: si un aviso del sistema falla o vence, se reprograma un reintento en la próxima medianoche local.
    @Test
    fun receiverFailureReschedulesARetryAtTheNextLocalMidnightAndIgnoresInvalidIds() {
        assumeNoRealWidget()

        runtime.receiverFailed(intArrayOf(0, -3))

        assertTrue("Un ID inválido no programa nada", scheduler.scheduled.isEmpty())

        runtime.receiverFailed(intArrayOf(ABSENT_WIDGET_ID))

        // 29/08/2026 12:00 en Córdoba (UTC-3): la medianoche siguiente es 30/08 00:00 local.
        assertEquals("America/Argentina/Cordoba", AppDefaults.ZONE_ID)
        assertEquals(listOf(Instant.parse("2026-08-30T03:00:00Z")), scheduler.scheduled.toList())
        assertEquals(0, scheduler.cancels.get())
        assertEquals(0, storeOpens.get())
        assertEquals(0, weatherClient.calls.get())
    }

    // W023 / W024: Android avisa que se quitó la última instancia.
    @Test
    fun disabledCancelsTheAlarmWithoutSchedulingAnotherOne() {
        assumeNoRealWidget()

        runtime.disabled()

        assertEquals(1, scheduler.cancels.get())
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(0, storeOpens.get())
    }

    // W024: durante una restauración de Copias el motor queda en pausa y no publica nada.
    @Test
    fun pauseForRestoreStopsBackgroundWorkAndLaterRefreshesDoNothing() = runBlocking<Unit> {
        assumeNoRealWidget()
        assertFalse(runtime.isPausedForRestore)

        runtime.pauseForRestore()

        assertTrue(runtime.isPausedForRestore)
        assertEquals(1, scheduler.cancels.get())

        runtime.start()
        runtime.refreshNow()
        runtime.refreshAll()
        runtime.refresh(ABSENT_WIDGET_ID)

        assertFalse(stage14WidgetWaitUntil(timeoutMillis = 300) { scheduler.cancels.get() != 1 })
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(0, storeOpens.get())
        assertEquals(0, weatherClient.calls.get())
    }

    // W022 (parcial): la fuente que el motor observa (`WidgetRuntime.kt:264-265`) vuelve a emitir sola
    // cuando se guarda una jornada en la base, y el texto del Widget cambia. No prueba el redibujado.
    @Test
    fun theSourceTheEngineObservesReemitsWhenAShiftIsSavedAndTheWidgetTextChanges() = runBlocking<Unit> {
        val databaseName = "stage14-widget-runtime-${UUID.randomUUID()}.db"
        val store = LocalDataStore.create(context, databaseName)
        try {
            val sources = V2WorkEventSourceObserver(
                shifts = store.v2Shifts,
                availabilityWindows = store.availabilityWindows,
                shiftActuals = store.shiftActuals,
                independentExtras = store.independentExtraWork,
                explicitDayStatuses = store.explicitDayStatuses,
                vacations = store.vacations,
                medicalLeaves = store.medicalLeaves,
                workConfiguration = store.workConfiguration,
            )
            val emissions = Channel<NextEventSourceData>(Channel.UNLIMITED)
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                sources.observe(TODAY).collect { emissions.send(it) }
            }

            val empty = widgetText(withTimeout(5_000) { emissions.receive() })
            assertEquals("PRÓXIMO EVENTO", empty.first)
            assertEquals("No hay próximos eventos.", empty.second)
            assertNull(empty.third)

            store.v2Shifts.insert(V2AppTestFixture.writeFor(store, futureShift(), LocalDate.of(2026, 8, 1)))

            val upcoming = withTimeout(5_000) {
                var text = widgetText(emissions.receive())
                while (text.first != "PRÓXIMA JORNADA") text = widgetText(emissions.receive())
                text
            }
            assertTrue(upcoming.second, upcoming.second.contains("Objetivo ficticio"))
            assertEquals("16/08/2026 · 19:00–07:00 (+1 día)", upcoming.third)
            collector.cancelAndJoin()
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    /** Título, texto principal y horario que mostraría una instancia Automático + Completa. */
    private fun widgetText(source: NextEventSourceData): Triple<String, String, String?> {
        val projection = projectWidget(
            projectNextEvent(SOURCE_NOW, ZONE, source.toInput()),
            WidgetProjectionConfig(
                mode = WidgetMode.AUTOMATIC,
                privacy = WidgetPrivacy.COMPLETE,
                size = WidgetSize.EXPANDED,
                configured = true,
            ),
        )
        val text = buildWidgetRenderText(projection)
        return Triple(text.title, text.primary, text.schedule)
    }

    private fun futureShift(): Shift {
        val date = LocalDate.of(2026, 8, 16)
        return Shift(
            id = UUID.fromString("e1400000-0000-0000-0000-000000000001"),
            startAt = ZonedDateTime.of(date, LocalTime.of(19, 0), ZONE).toInstant(),
            endAt = ZonedDateTime.of(date.plusDays(1), LocalTime.of(7, 0), ZONE).toInstant(),
            zoneId = ZONE,
            localStartDate = date,
            objectiveNameSnapshot = "Objetivo ficticio",
            objectiveAbbreviationSnapshot = "FIC",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(19, 0),
            endTimeSnapshot = LocalTime.of(7, 0),
            colorArgbSnapshot = 0xFF315DA8.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = V2AppTestFixture.PLACEHOLDER_OBJECTIVE_ID,
            createdAt = SOURCE_NOW,
            updatedAt = SOURCE_NOW,
        )
    }

    private fun assumeNoRealWidget() {
        assumeTrue(
            "Hay un Widget real en la pantalla de inicio: las pruebas aisladas del motor no corren.",
            stage14InstalledWidgetIds(context).isEmpty(),
        )
    }

    private companion object {
        val ZONE: ZoneId = AppDefaults.zoneId()
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-08-29T15:00:00Z"), ZONE)
        val TODAY: LocalDate = LocalDate.of(2026, 8, 15)
        val SOURCE_NOW: Instant = ZonedDateTime.of(TODAY, LocalTime.NOON, ZONE).toInstant()

        // IDs que ningún launcher asignó: el sistema ignora los dibujos dirigidos a ellos.
        const val ABSENT_WIDGET_ID = 914_000_001
        const val OTHER_ABSENT_WIDGET_ID = 914_000_002
    }
}
