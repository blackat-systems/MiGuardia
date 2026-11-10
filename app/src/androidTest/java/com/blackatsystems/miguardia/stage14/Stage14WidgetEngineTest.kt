package com.blackatsystems.miguardia.stage14

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.V2AppTestFixture
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.weather.WeatherCacheStore
import com.blackatsystems.miguardia.weather.WeatherForecastClient
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherRuntime
import com.blackatsystems.miguardia.widget.WidgetInstancePreferences
import com.blackatsystems.miguardia.widget.WidgetPreferencesStore
import com.blackatsystems.miguardia.widget.WidgetRuntime
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
 * Etapa 14 · Widget de inicio: el motor real (`WidgetRuntime`) con un dibujante de mentira.
 *
 * Todo es propio de la prueba: base Room creada y borrada, preferencias del Widget y de Clima en
 * carpeta temporal, programador de mentira, cliente de Clima de mentira y reloj fijo
 * (15/08/2026 12:00 de Córdoba). No se consulta ni se toca el AppWidgetManager real, así que corre
 * igual con o sin un Widget puesto en la pantalla de inicio. No se prueba `restoreNow`.
 *
 * El motor sólo dibuja si la Application terminó su recuperación de arranque
 * (`WidgetRuntime.kt:101-106` → `LocalRuntimeActivation.kt:10-13`). Ese estado sólo se lee; si no
 * está listo, las pruebas se saltean. Al cerrar, cada motor se pausa (da de baja su receptor de
 * cambio de configuración) y se cancela su scope.
 */
@RunWith(AndroidJUnit4::class)
class Stage14WidgetEngineTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val engines = mutableListOf<Engine>()

    @Before
    fun requireStartupFinished() {
        assumeTrue("La Application no terminó su arranque.", stage14WidgetStartupIsReady(context))
    }

    @After
    fun closeEngines() {
        closeAllEngines()
    }

    // W022: con una instancia puesta se dibuja el estado vacío; al guardar una jornada el motor
    // vuelve a dibujar solo, ya con esa jornada. X015: dibujar no cambia ninguna tabla.
    @Test
    fun engineRedrawsByItselfWhenAShiftIsSavedAndDrawingNeverChangesTheBase() = withStore { store, databaseName ->
        val engine = engine({ store })
        val configured = instance(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE)
        engine.surface.installed = intArrayOf(ID_A)
        engine.widgetPreferences.save(ID_A, configured)

        engine.runtime.start()

        assertEquals(listOf(listOf(ID_A)), engine.surface.loading.toList())
        val empty = engine.surface.awaitDraw("estado vacío tras start") { it.id == ID_A }
        assertTrue(empty.result.primaryEvents.isEmpty())
        assertNull(empty.weatherText)
        engine.runtime.refreshNow()
        assertTrue(engine.surface.draws.size >= 2)
        assertTrue(engine.surface.draws.all { it.result.primaryEvents.isEmpty() })

        val shift = shift("a1", LocalTime.of(19, 0), LocalTime.of(7, 0))
        val beforeSave = engine.surface.draws.size
        store.v2Shifts.insert(V2AppTestFixture.writeFor(store, shift, EFFECTIVE_FROM))

        // Desde acá nadie llama al motor: el dibujo llega por su propia observación de la base.
        val redrawn = engine.surface.awaitDraw("jornada guardada", fromIndex = beforeSave) { shift.id in it.shiftIds }
        assertEquals(ID_A, redrawn.id)
        assertEquals(configured, redrawn.preferences)
        assertNull(redrawn.weatherText)

        val rowsBefore = tableRows(databaseName)
        assertEquals(1, rowsBefore.getValue("shifts").size)
        val beforeRefreshes = engine.surface.draws.size
        repeat(3) { engine.runtime.refreshNow() }
        engine.runtime.refreshAll()
        engine.runtime.refresh(ID_A)
        assertTrue(stage14WidgetWaitUntil { engine.surface.draws.size >= beforeRefreshes + 5 })
        assertEquals("Dibujar el Widget cambió datos de la base", rowsBefore, tableRows(databaseName))
        assertEquals(0, engine.weatherClient.calls.get())
    }

    // W022 / X013: con Clima habilitado y sin red, la jornada se dibuja igual y primero; cuando el
    // cliente responde bien llega un dibujo nuevo con el texto de Clima.
    @Test
    fun failingWeatherNeverHidesTheShiftAndALaterSuccessAddsTheWeatherText() = withStore { store, _ ->
        val write = insertShift(store, shift("b1", LocalTime.of(19, 0), LocalTime.of(7, 0)))
        val client = Stage14WidgetScriptedWeatherClient(CLOCK, write.shift.startAt, write.shift.endAt)
        val engine = weatherEngine(store, client)

        engine.runtime.start()

        val first = engine.surface.awaitDraw("jornada sin Clima") { write.shift.id in it.shiftIds }
        assertNull("El primer dibujo no espera al Clima", first.weatherText)
        assertTrue("No se pidió Clima", stage14WidgetWaitUntil { client.calls.get() >= 1 })
        engine.runtime.refreshNow(allowWeatherRefresh = false)
        assertTrue(engine.surface.draws.none { it.weatherText != null })
        assertEquals(listOf(write.shift.id), engine.surface.lastDraw(ID_A)?.shiftIds)
        assertTrue(engine.surface.errors.isEmpty())

        client.succeed = true
        val beforeSuccess = engine.surface.draws.size
        withTimeout(5_000) { engine.runtime.refreshNow() }

        val withWeather = engine.surface.awaitDraw("jornada con Clima", fromIndex = beforeSuccess) {
            it.weatherText != null
        }
        assertTrue(withWeather.weatherText, withWeather.weatherText!!.startsWith("Clima: "))
        assertEquals(listOf(write.shift.id), withWeather.shiftIds)
    }

    // W022 / X013: con una descarga de Clima lenta, la jornada ya está dibujada mientras el cliente
    // sigue sin responder; al responder llega el dibujo con Clima.
    @Test
    fun slowWeatherDownloadLeavesTheShiftDrawnFirstAndTheWeatherArrivesLater() = withStore { store, _ ->
        val write = insertShift(store, shift("c1", LocalTime.of(19, 0), LocalTime.of(7, 0)))
        val client = Stage14WidgetScriptedWeatherClient(CLOCK, write.shift.startAt, write.shift.endAt)
        val gate = CompletableDeferred<Unit>()
        client.gate = gate
        client.succeed = true
        val engine = weatherEngine(store, client)
        try {
            engine.runtime.start()

            val first = engine.surface.awaitDraw("jornada antes del Clima") { write.shift.id in it.shiftIds }
            assertNull(first.weatherText)
            assertTrue("No se pidió Clima", stage14WidgetWaitUntil { client.calls.get() >= 1 })
            // El cliente sigue colgado y la jornada ya fue dibujada sin Clima.
            assertFalse(gate.isCompleted)
            assertTrue(engine.surface.draws.none { it.weatherText != null })

            gate.complete(Unit)

            val withWeather = engine.surface.awaitDraw("Clima después de la descarga") { it.weatherText != null }
            assertTrue(withWeather.weatherText, withWeather.weatherText!!.startsWith("Clima: "))
            assertEquals(listOf(write.shift.id), withWeather.shiftIds)
        } finally {
            gate.complete(Unit)
        }
    }

    // W022 («evento no bloqueado por Clima»): mientras una descarga de Clima sigue en curso, un
    // cambio de preferencias de la instancia igual tiene que redibujarse.
    @Test
    fun aChangeMadeWhileWeatherIsDownloadingIsStillDrawnWithoutWaitingForTheDownload() = withStore { store, _ ->
        val write = insertShift(store, shift("c2", LocalTime.of(19, 0), LocalTime.of(7, 0)))
        val client = Stage14WidgetScriptedWeatherClient(CLOCK, write.shift.startAt, write.shift.endAt)
        val gate = CompletableDeferred<Unit>()
        client.gate = gate
        val engine = weatherEngine(store, client)
        try {
            engine.runtime.start()
            engine.surface.awaitDraw("jornada antes del Clima") { write.shift.id in it.shiftIds }
            assertTrue("No se pidió Clima", stage14WidgetWaitUntil { client.calls.get() >= 1 })

            val changed = instance(WidgetMode.NEXT_SHIFT, WidgetPrivacy.COMPLETE, includeWeather = true)
            engine.widgetPreferences.save(ID_A, changed)

            val redrawn = engine.surface.awaitDraw("cambio de modo durante la descarga", timeoutMillis = 3_000) {
                it.preferences == changed
            }
            assertEquals(listOf(write.shift.id), redrawn.shiftIds)
            assertFalse("El dibujo tuvo que esperar a que terminara la descarga", gate.isCompleted)
        } finally {
            gate.complete(Unit)
        }
    }

    // X013: sin red, el motor no puede quedar pidiendo Clima una y otra vez sin un motivo nuevo.
    @Test
    fun failingWeatherIsNotRequestedOverAndOverWithoutANewReason() = withStore { store, _ ->
        val write = insertShift(store, shift("c3", LocalTime.of(19, 0), LocalTime.of(7, 0)))
        // Reloj que avanza, como el real: cada intento deja una marca de hora distinta.
        val ticking = Stage14WidgetTickingClock(NOW, ZONE)
        val client = Stage14WidgetScriptedWeatherClient(ticking, write.shift.startAt, write.shift.endAt)
        val engine = weatherEngine(store, client, ticking)

        engine.runtime.start()

        engine.surface.awaitDraw("jornada sin Clima") { write.shift.id in it.shiftIds }
        assertTrue("No se pidió Clima", stage14WidgetWaitUntil { client.calls.get() >= 1 })
        Thread.sleep(2_000)
        val calls = client.calls.get()
        assertTrue("Sin red y sin cambios, el Clima se pidió $calls veces en 2 segundos", calls <= 3)
    }

    // W011: el texto de Clima llega sólo a la instancia ampliada + Clima encendido + Completa.
    @Test
    fun weatherTextReachesOnlyTheExpandedCompleteInstanceWithItsOwnWeatherSwitchOn() = withStore { store, _ ->
        val write = insertShift(store, shift("d1", LocalTime.of(19, 0), LocalTime.of(7, 0)))
        val engine = engine({ store })
        engine.weatherPreferences.enableAfterExplanation()
        writeCache(engine, write, fetchedAt = NOW.minusSeconds(600))
        val configs = mapOf(
            ID_A to instance(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE, includeWeather = true),
            ID_B to instance(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE, includeWeather = false),
            ID_C to instance(WidgetMode.AUTOMATIC, WidgetPrivacy.REDUCED, includeWeather = true),
            ID_D to instance(WidgetMode.AUTOMATIC, WidgetPrivacy.HIDDEN, includeWeather = true),
            ID_E to instance(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE, includeWeather = true),
        )
        configs.forEach { (id, config) -> engine.widgetPreferences.save(id, config) }
        engine.surface.installed = configs.keys.toIntArray()
        engine.surface.expanded += listOf(ID_A, ID_B, ID_C, ID_D) // ID_E queda compacta

        engine.runtime.refreshNow()

        val shown = engine.surface.lastDraw(ID_A)
        assertTrue("Ampliada + Clima + Completa: ${shown?.weatherText}", shown?.weatherText?.startsWith("Clima: ") == true)
        listOf(ID_B, ID_C, ID_D, ID_E).forEach { id ->
            val draw = requireNotNull(engine.surface.lastDraw(id)) { "La instancia $id no fue dibujada" }
            assertEquals(configs.getValue(id), draw.preferences)
            assertEquals(listOf(write.shift.id), draw.shiftIds)
            assertNull("La instancia $id no debe recibir Clima", draw.weatherText)
        }
        // La caché fresca alcanza: no hace falta pedirle nada al cliente.
        assertEquals(0, engine.weatherClient.calls.get())
    }

    // W011: instancia apagada, Reducida, Oculta o compacta nunca piden Clima al cliente.
    @Test
    fun instancesThatCannotShowWeatherNeverAskTheClientAndAnEligibleOneDoes() = withStore { store, _ ->
        val write = insertShift(store, shift("d2", LocalTime.of(19, 0), LocalTime.of(7, 0)))
        val engine = engine({ store })
        engine.weatherPreferences.enableAfterExplanation()
        engine.widgetPreferences.save(ID_B, instance(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE, includeWeather = false))
        engine.widgetPreferences.save(ID_C, instance(WidgetMode.AUTOMATIC, WidgetPrivacy.REDUCED, includeWeather = true))
        engine.widgetPreferences.save(ID_D, instance(WidgetMode.AUTOMATIC, WidgetPrivacy.HIDDEN, includeWeather = true))
        engine.widgetPreferences.save(ID_E, instance(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE, includeWeather = true))
        engine.surface.installed = intArrayOf(ID_B, ID_C, ID_D, ID_E)
        engine.surface.expanded += listOf(ID_B, ID_C, ID_D)

        engine.runtime.start()
        engine.runtime.refreshNow()

        listOf(ID_B, ID_C, ID_D, ID_E).forEach { id ->
            engine.surface.awaitDraw("instancia $id") { it.id == id && write.shift.id in it.shiftIds }
        }
        assertFalse(stage14WidgetWaitUntil(timeoutMillis = 500) { engine.weatherClient.calls.get() > 0 })
        assertTrue(engine.surface.draws.none { it.weatherText != null })

        // Control: apenas hay una instancia que sí puede mostrar Clima, se pide.
        engine.widgetPreferences.save(ID_A, instance(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE, includeWeather = true))
        engine.surface.expanded += ID_A
        engine.surface.installed = intArrayOf(ID_A, ID_B, ID_C, ID_D, ID_E)
        engine.runtime.refreshNow()

        assertTrue("La instancia apta no pidió Clima", stage14WidgetWaitUntil { engine.weatherClient.calls.get() >= 1 })
    }

    // W011: Clima global apagado o sin explicación aceptada, y caché vieja, parcial o ausente → sin texto.
    @Test
    fun weatherTextNeedsGlobalConsentAndAFreshCompleteCache() = withStore { store, _ ->
        val write = insertShift(store, shift("d3", LocalTime.of(19, 0), LocalTime.of(7, 0)))

        suspend fun weatherTextWith(
            label: String,
            expectsClientCall: Boolean,
            prepare: suspend (Engine) -> Unit,
        ): String? {
            val engine = engine({ store })
            engine.widgetPreferences.save(ID_A, instance(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE, includeWeather = true))
            engine.surface.installed = intArrayOf(ID_A)
            engine.surface.expanded += ID_A
            prepare(engine)
            engine.runtime.refreshNow()
            val draw = requireNotNull(engine.surface.lastDraw(ID_A)) { "$label: no hubo dibujo" }
            assertEquals(label, listOf(write.shift.id), draw.shiftIds)
            if (expectsClientCall) {
                assertTrue("$label: debía pedir Clima", stage14WidgetWaitUntil { engine.weatherClient.calls.get() >= 1 })
            } else {
                assertFalse(
                    "$label: no debía pedir Clima",
                    stage14WidgetWaitUntil(timeoutMillis = 400) { engine.weatherClient.calls.get() > 0 },
                )
            }
            assertTrue("$label: apareció Clima después", engine.surface.draws.none { it.weatherText != null && label != "todo cumplido" })
            engine.close()
            return draw.weatherText
        }

        val complete = weatherTextWith("todo cumplido", expectsClientCall = false) { engine ->
            engine.weatherPreferences.enableAfterExplanation()
            writeCache(engine, write, fetchedAt = NOW.minusSeconds(600))
        }
        assertTrue("Control: con todo cumplido debía haber texto", complete?.startsWith("Clima: ") == true)

        assertNull(
            weatherTextWith("Clima global apagado", expectsClientCall = false) { engine ->
                writeCache(engine, write, fetchedAt = NOW.minusSeconds(600))
            },
        )
        assertNull(
            weatherTextWith("explicación sin aceptar", expectsClientCall = false) { engine ->
                engine.weatherPreferences.setEnabled(true)
                writeCache(engine, write, fetchedAt = NOW.minusSeconds(600))
            },
        )
        assertNull(
            weatherTextWith("caché de hace dos horas", expectsClientCall = true) { engine ->
                engine.weatherPreferences.enableAfterExplanation()
                writeCache(engine, write, fetchedAt = NOW.minusSeconds(7_200))
            },
        )
        assertNull(
            weatherTextWith("caché que cubre sólo dos horas", expectsClientCall = false) { engine ->
                engine.weatherPreferences.enableAfterExplanation()
                writeCache(engine, write, fetchedAt = NOW.minusSeconds(600), untilExclusive = write.shift.startAt.plusSeconds(7_200))
            },
        )
        assertNull(
            weatherTextWith("sin caché", expectsClientCall = true) { engine ->
                engine.weatherPreferences.enableAfterExplanation()
            },
        )
    }

    // W023: sin eventos, el límite es la próxima medianoche local; al quedar sin instancias se cancela.
    @Test
    fun emptyBaseSchedulesTheNextLocalMidnightAndLosingTheLastInstanceCancels() = withStore { store, _ ->
        val engine = engine({ store })
        engine.widgetPreferences.save(ID_A, instance(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE))
        engine.surface.installed = intArrayOf(ID_A)

        engine.runtime.refreshNow()

        assertEquals(NEXT_MIDNIGHT, engine.scheduler.scheduled.last())
        assertEquals(0, engine.scheduler.cancels.get())

        engine.surface.installed = IntArray(0)
        engine.runtime.refreshNow()

        assertTrue(engine.scheduler.cancels.get() >= 1)
        val scheduledBefore = engine.scheduler.scheduled.size
        val drawsBefore = engine.surface.draws.size
        engine.runtime.start()
        assertEquals(scheduledBefore, engine.scheduler.scheduled.size)
        assertEquals(drawsBefore, engine.surface.draws.size)
    }

    // W023: con una jornada próxima hoy, el límite es su inicio (19:00 de Córdoba).
    @Test
    fun upcomingShiftSchedulesItsStart() = withStore { store, _ ->
        val write = insertShift(store, shift("e1", LocalTime.of(19, 0), LocalTime.of(7, 0)))
        listOf(WidgetMode.AUTOMATIC, WidgetMode.NEXT_SHIFT).forEach { mode ->
            val engine = engine({ store })
            engine.widgetPreferences.save(ID_A, instance(mode, WidgetPrivacy.HIDDEN))
            engine.surface.installed = intArrayOf(ID_A)

            engine.runtime.refreshNow()

            assertEquals("$mode", Instant.parse("2026-08-15T22:00:00Z"), write.shift.startAt)
            assertEquals("$mode", write.shift.startAt, engine.scheduler.scheduled.last())
            engine.close()
        }
        // Próximo franco no depende de jornadas: sólo la medianoche.
        val dayOff = engine({ store })
        dayOff.widgetPreferences.save(ID_A, instance(WidgetMode.NEXT_DAY_OFF, WidgetPrivacy.COMPLETE))
        dayOff.surface.installed = intArrayOf(ID_A)
        dayOff.runtime.refreshNow()
        assertEquals(NEXT_MIDNIGHT, dayOff.scheduler.scheduled.last())
    }

    // W023: con una jornada en curso y modo Automático, el límite es su final (16:00 de Córdoba).
    @Test
    fun ongoingShiftInAutomaticSchedulesItsEnd() = withStore { store, _ ->
        val write = insertShift(store, shift("f1", LocalTime.of(8, 0), LocalTime.of(16, 0)))
        val engine = engine({ store })
        engine.widgetPreferences.save(ID_A, instance(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE))
        engine.surface.installed = intArrayOf(ID_A)

        engine.runtime.refreshNow()

        assertEquals(listOf(write.shift.id), engine.surface.lastDraw(ID_A)?.shiftIds)
        assertEquals(Instant.parse("2026-08-15T19:00:00Z"), write.shift.endAt)
        assertEquals(write.shift.endAt, engine.scheduler.scheduled.last())
    }

    // W023: si se está mostrando Clima fresco, el límite es su vencimiento cuando es anterior.
    @Test
    fun shownFreshWeatherSchedulesItsExpiryWhenItComesFirst() = withStore { store, _ ->
        val write = insertShift(store, shift("g1", LocalTime.of(8, 0), LocalTime.of(16, 0)))
        val engine = engine({ store })
        engine.weatherPreferences.enableAfterExplanation()
        val fetchedAt = NOW.minusSeconds(1_800)
        writeCache(engine, write, fetchedAt = fetchedAt)
        engine.widgetPreferences.save(ID_A, instance(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE, includeWeather = true))
        engine.widgetPreferences.save(ID_B, instance(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE, includeWeather = true))
        engine.surface.installed = intArrayOf(ID_A)
        engine.surface.expanded += ID_A

        engine.runtime.refreshNow()

        assertTrue(engine.surface.lastDraw(ID_A)?.weatherText?.startsWith("Clima: ") == true)
        // Una hora después de la descarga, más un segundo: 12:30:01 de Córdoba, antes del fin de la jornada.
        assertEquals(Instant.parse("2026-08-15T15:30:01Z"), engine.scheduler.scheduled.last())

        // La misma instancia en compacto no muestra Clima: vuelve a regir el fin de la jornada.
        engine.surface.expanded.clear()
        engine.runtime.refreshNow()

        assertNull(engine.surface.lastDraw(ID_A)?.weatherText)
        assertEquals(write.shift.endAt, engine.scheduler.scheduled.last())
    }

    // W021: si la lectura falla, las instancias reciben la vista de error y se programa el reintento
    // a la próxima medianoche local; `showLoading` llega al dibujante.
    @Test
    fun readFailureDrawsTheErrorViewAndSchedulesARetryAtTheNextLocalMidnight() = runBlocking<Unit> {
        val engine = engine({ error("Base ficticia ilegible") })
        engine.widgetPreferences.save(ID_A, instance(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE))
        engine.surface.installed = intArrayOf(ID_A, ID_B)

        engine.runtime.start()

        assertEquals(listOf(listOf(ID_A, ID_B)), engine.surface.loading.toList())
        assertTrue("No llegó la vista de error", stage14WidgetWaitUntil { engine.surface.errors.isNotEmpty() })
        assertEquals(listOf(ID_A, ID_B), engine.surface.errors.first())
        assertTrue(stage14WidgetWaitUntil { engine.scheduler.scheduled.isNotEmpty() })
        assertTrue(engine.scheduler.scheduled.all { it == NEXT_MIDNIGHT })
        assertTrue(engine.surface.draws.isEmpty())

        engine.runtime.refresh(ID_B)

        assertTrue(
            "El refresco de una instancia no dibujó su error",
            stage14WidgetWaitUntil { listOf(ID_B) in engine.surface.errors },
        )
        val errorsBeforeRefreshAll = engine.surface.errors.size
        engine.runtime.refreshAll()
        assertTrue(stage14WidgetWaitUntil { engine.surface.errors.size > errorsBeforeRefreshAll })
        assertTrue(engine.scheduler.scheduled.all { it == NEXT_MIDNIGHT })
        assertTrue(engine.surface.draws.isEmpty())

        engine.runtime.showLoading(intArrayOf(ID_B, 0, ID_B, -4))

        assertEquals(listOf(ID_B), engine.surface.loading.last())
    }

    // W014 / W024: cada instancia recibe su propia configuración; borrar una limpia sólo sus
    // preferencias y redibuja la otra.
    @Test
    fun twoInstancesKeepTheirOwnOptionsAndDeletingOneOnlyClearsItAndRedrawsTheOther() = withStore { store, _ ->
        val write = insertShift(store, shift("h1", LocalTime.of(19, 0), LocalTime.of(7, 0)))
        val engine = engine({ store })
        val first = instance(WidgetMode.NEXT_SHIFT, WidgetPrivacy.COMPLETE, includeWeather = true)
        val second = instance(WidgetMode.AUTOMATIC, WidgetPrivacy.REDUCED)
        engine.widgetPreferences.save(ID_A, first)
        engine.widgetPreferences.save(ID_B, second)
        engine.surface.installed = intArrayOf(ID_A, ID_B)

        engine.runtime.refreshNow()

        assertEquals(first, engine.surface.lastDraw(ID_A)?.preferences)
        assertEquals(second, engine.surface.lastDraw(ID_B)?.preferences)
        assertEquals(listOf(write.shift.id), engine.surface.lastDraw(ID_B)?.shiftIds)

        // Android ya no lista la instancia quitada cuando avisa del borrado.
        engine.surface.installed = intArrayOf(ID_B)
        val beforeDelete = engine.surface.draws.size
        engine.runtime.deleteNow(intArrayOf(ID_A))

        assertEquals(mapOf(ID_B to second), engine.widgetPreferences.all())
        val afterDelete = engine.surface.draws.drop(beforeDelete)
        assertTrue("La instancia que queda no fue redibujada", afterDelete.any { it.id == ID_B && it.preferences == second })
        assertTrue("Se dibujó la instancia borrada", afterDelete.none { it.id == ID_A })
        assertEquals(0, engine.scheduler.cancels.get())

        engine.surface.installed = IntArray(0)
        engine.runtime.deleteNow(intArrayOf(ID_B))

        assertTrue(engine.widgetPreferences.all().isEmpty())
        assertTrue(engine.scheduler.cancels.get() >= 1)
    }

    // W004 / W010: una instancia sin configurar se dibuja con la configuración segura por defecto
    // y no cuenta para pedir Clima, aunque esté ampliada y haya una jornada.
    @Test
    fun unconfiguredInstanceIsDrawnWithTheSafeDefaultAndNeverAsksWeather() = withStore { store, _ ->
        val write = insertShift(store, shift("i1", LocalTime.of(19, 0), LocalTime.of(7, 0)))
        val engine = engine({ store })
        engine.weatherPreferences.enableAfterExplanation()
        writeCache(engine, write, fetchedAt = NOW.minusSeconds(600))
        engine.surface.installed = intArrayOf(ID_A)
        engine.surface.expanded += ID_A

        engine.runtime.refreshNow()

        val draw = requireNotNull(engine.surface.lastDraw(ID_A))
        assertEquals(WidgetPreferencesStore.SafeDefault, draw.preferences)
        assertEquals(
            WidgetInstancePreferences(WidgetMode.AUTOMATIC, WidgetPrivacy.HIDDEN, includeWeather = false, configured = false),
            draw.preferences,
        )
        assertNull(draw.weatherText)
        // Sin instancias configuradas, el único límite es la medianoche (no el inicio de la jornada).
        assertEquals(NEXT_MIDNIGHT, engine.scheduler.scheduled.last())
        assertFalse(stage14WidgetWaitUntil(timeoutMillis = 400) { engine.weatherClient.calls.get() > 0 })
        assertTrue(engine.widgetPreferences.all().isEmpty())
    }

    // ---- Soporte ----

    private inner class Engine(
        storeProvider: () -> LocalDataStore,
        client: WeatherForecastClient?,
        clock: Clock,
    ) {
        val folder: File = temporaryFolder.newFolder()
        private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val surface = Stage14WidgetRecordingSurface()
        val scheduler = Stage14WidgetRecordingScheduler()
        val weatherClient = Stage14WidgetOfflineWeatherClient()
        val widgetPreferences = WidgetPreferencesStore(File(folder, "stage14-widget.preferences_pb"), storeScope)
        val weatherPreferences = WeatherPreferencesStore(File(folder, "stage14-weather.preferences_pb"), storeScope)
        private val weather = WeatherRuntime(
            context = Stage14WidgetFilesContext(context, folder),
            preferences = weatherPreferences,
            objectives = Stage14WidgetLocatedObjectives,
            clock = clock,
            client = client ?: weatherClient,
        )
        val runtime = WidgetRuntime(
            context = context,
            localDataStore = storeProvider,
            preferences = widgetPreferences,
            weatherRuntime = weather,
            clock = clock,
            renderer = surface,
            scheduler = scheduler,
        )
        private var closed = false

        fun close() {
            if (closed) return
            closed = true
            surface.installed = IntArray(0)
            // La pausa cancela el trabajo en curso y da de baja el receptor de cambio de configuración
            // (`WidgetRuntime.pauseForRestore` → `stopBackgroundWork`).
            runBlocking { withTimeoutOrNull(5_000) { runtime.pauseForRestore() } }
            runtime.scope.cancel()
            storeScope.cancel()
        }
    }

    private fun engine(
        storeProvider: () -> LocalDataStore,
        client: WeatherForecastClient? = null,
        clock: Clock = CLOCK,
    ): Engine = Engine(storeProvider, client, clock).also { engines += it }

    /** Motor con Clima global habilitado y una instancia ampliada + Clima + Completa. */
    private suspend fun weatherEngine(
        store: LocalDataStore,
        client: WeatherForecastClient,
        clock: Clock = CLOCK,
    ): Engine {
        val engine = engine({ store }, client, clock)
        engine.weatherPreferences.enableAfterExplanation()
        engine.widgetPreferences.save(ID_A, instance(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE, includeWeather = true))
        engine.surface.installed = intArrayOf(ID_A)
        engine.surface.expanded += ID_A
        return engine
    }

    private fun closeAllEngines() {
        engines.toList().forEach { it.close() }
        engines.clear()
    }

    /** Base Room propia: se crea, se usa, se cierran los motores y se borra. */
    private fun withStore(block: suspend (LocalDataStore, String) -> Unit) = runBlocking<Unit> {
        val databaseName = "stage14-widget-engine-${UUID.randomUUID()}.db"
        val store = LocalDataStore.create(context, databaseName)
        try {
            block(store, databaseName)
        } finally {
            closeAllEngines()
            store.close()
            context.deleteDatabase(databaseName)
            // Room deja un bloqueo vacío junto a la base; deleteDatabase no lo borra.
            java.io.File(context.cacheDir, "$databaseName.lck").delete()
        }
    }

    private suspend fun insertShift(store: LocalDataStore, shift: Shift): V2ShiftWrite {
        val write = V2AppTestFixture.writeFor(store, shift, EFFECTIVE_FROM)
        store.v2Shifts.insert(write)
        return write
    }

    private suspend fun writeCache(
        engine: Engine,
        write: V2ShiftWrite,
        fetchedAt: Instant,
        untilExclusive: Instant = write.shift.endAt,
    ) {
        val location = Stage14WidgetLocatedObjectives.locationFor(write.shift.sourceObjectiveId, write.shift.zoneId)
        WeatherCacheStore.forLocation(engine.folder, location)
            .write(stage14WidgetForecast(location, write.shift.startAt, untilExclusive, fetchedAt))
    }

    private fun instance(
        mode: WidgetMode,
        privacy: WidgetPrivacy,
        includeWeather: Boolean = false,
    ) = WidgetInstancePreferences(mode = mode, privacy = privacy, includeWeather = includeWeather, configured = true)

    /** Jornada ficticia del 15/08/2026; si termina antes o a la hora de inicio, termina al día siguiente. */
    private fun shift(key: String, start: LocalTime, end: LocalTime): Shift {
        val endDate = if (end > start) TODAY else TODAY.plusDays(1)
        return Shift(
            id = UUID.nameUUIDFromBytes("stage14-widget-engine-$key".toByteArray()),
            startAt = ZonedDateTime.of(TODAY, start, ZONE).toInstant(),
            endAt = ZonedDateTime.of(endDate, end, ZONE).toInstant(),
            zoneId = ZONE,
            localStartDate = TODAY,
            objectiveNameSnapshot = Stage14WidgetLocatedObjectives.NAME,
            objectiveAbbreviationSnapshot = "FIC",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = start,
            endTimeSnapshot = end,
            colorArgbSnapshot = 0xFF315DA8.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = V2AppTestFixture.PLACEHOLDER_OBJECTIVE_ID,
            createdAt = NOW.minusSeconds(86_400),
            updatedAt = NOW.minusSeconds(86_400),
        )
    }

    /** Contenido completo de cada tabla, leído por una conexión de sólo lectura. */
    private fun tableRows(databaseName: String): Map<String, List<String>> {
        val database = SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        try {
            val tables = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                    "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name",
                null,
            ).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            return tables.associateWith { table ->
                database.rawQuery("SELECT * FROM \"$table\"", null).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(
                                (0 until cursor.columnCount).joinToString("|") { column ->
                                    when (cursor.getType(column)) {
                                        Cursor.FIELD_TYPE_NULL -> "<nulo>"
                                        Cursor.FIELD_TYPE_BLOB ->
                                            cursor.getBlob(column).joinToString("") { byte -> "%02x".format(byte) }
                                        else -> cursor.getString(column)
                                    }
                                },
                            )
                        }
                    }.sorted()
                }
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        val ZONE: ZoneId = AppDefaults.zoneId()
        val TODAY: LocalDate = LocalDate.of(2026, 8, 15)
        val NOW: Instant = Instant.parse("2026-08-15T15:00:00Z") // 12:00 de Córdoba
        val CLOCK: Clock = Clock.fixed(NOW, ZONE)
        val NEXT_MIDNIGHT: Instant = Instant.parse("2026-08-16T03:00:00Z")
        val EFFECTIVE_FROM: LocalDate = LocalDate.of(2026, 8, 1)

        // Identificadores ficticios: sólo viven en el dibujante de mentira.
        const val ID_A = 914_100_001
        const val ID_B = 914_100_002
        const val ID_C = 914_100_003
        const val ID_D = 914_100_004
        const val ID_E = 914_100_005
    }
}
