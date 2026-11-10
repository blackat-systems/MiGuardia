package com.blackatsystems.miguardia.widget

import com.blackatsystems.miguardia.core.domain.nextevent.NextEventInput
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.widget.WidgetContentState
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetNavigation
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjection
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjectionConfig
import com.blackatsystems.miguardia.core.domain.widget.WidgetSize
import com.blackatsystems.miguardia.core.domain.widget.projectWidget
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Etapa 14 · Widget de inicio: valores iniciales e ID inválido, contra literales. */
class Stage14WidgetDefaultsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: WidgetPreferencesStore

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = WidgetPreferencesStore(File(temporaryFolder.root, "stage14-widget.preferences_pb"), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // W004
    @Test
    fun `a new instance starts automatic hidden without weather and not configured`() {
        val initial = WidgetInstancePreferences()

        assertEquals(WidgetMode.AUTOMATIC, initial.mode)
        assertEquals(WidgetPrivacy.HIDDEN, initial.privacy)
        assertFalse(initial.includeWeather)
        assertFalse(initial.configured)
        assertEquals(
            WidgetInstancePreferences(
                mode = WidgetMode.AUTOMATIC,
                privacy = WidgetPrivacy.HIDDEN,
                includeWeather = false,
                configured = false,
            ),
            WidgetPreferencesStore.SafeDefault,
        )
    }

    // W004: la pantalla de configuración arma su borrador con lo que devuelve current().
    @Test
    fun `an instance that was never saved reads the literal initial values`() = runBlocking {
        val neverSaved = store.current(4711)

        assertEquals(WidgetMode.AUTOMATIC, neverSaved.mode)
        assertEquals(WidgetPrivacy.HIDDEN, neverSaved.privacy)
        assertFalse(neverSaved.includeWeather)
        assertFalse(neverSaved.configured)
        assertTrue(store.all().isEmpty())
    }

    // W004 (ID inválido)
    @Test
    fun `invalid IDs read the safe initial values and can never be saved`() = runBlocking {
        val configured = WidgetInstancePreferences(
            mode = WidgetMode.NEXT_SHIFT,
            privacy = WidgetPrivacy.COMPLETE,
            includeWeather = true,
            configured = true,
        )
        store.save(15, configured)

        listOf(0, -1, Int.MIN_VALUE).forEach { invalidId ->
            assertEquals(WidgetPreferencesStore.SafeDefault, store.current(invalidId))
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { store.save(invalidId, configured) }
            }
            assertFalse(belongsToWidgetProvider(invalidId, intArrayOf(invalidId, 15)))
        }

        assertEquals(mapOf(15 to configured), store.all())
    }

    // W013: si la instancia deja de estar autorizada mientras se guarda, no se escribe nada.
    @Test
    fun `a save whose authorization is lost fails and leaves the previous state untouched`() = runBlocking {
        val previous = WidgetInstancePreferences(
            mode = WidgetMode.NEXT_DAY_OFF,
            privacy = WidgetPrivacy.REDUCED,
            includeWeather = false,
            configured = true,
        )
        store.save(21, previous)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                store.save(
                    21,
                    previous.copy(privacy = WidgetPrivacy.COMPLETE, includeWeather = true),
                    isAuthorized = { false },
                )
            }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                store.save(22, previous, isAuthorized = { false })
            }
        }

        assertEquals(mapOf(21 to previous), store.all())
    }

    // W004 + W021: sin configurar, el Widget sólo pide configurarse y lleva a la configuración.
    @Test
    fun `an unconfigured instance only shows the configuration prompt`() {
        val defaults = WidgetProjectionConfig()
        assertEquals(WidgetMode.AUTOMATIC, defaults.mode)
        assertEquals(WidgetPrivacy.HIDDEN, defaults.privacy)
        assertEquals(WidgetSize.COMPACT, defaults.size)
        assertFalse(defaults.configured)

        val initial = WidgetInstancePreferences()
        WidgetSize.entries.forEach { size ->
            val projection = projectWidget(
                emptyResult(),
                WidgetProjectionConfig(
                    mode = initial.mode,
                    privacy = initial.privacy,
                    size = size,
                    configured = initial.configured,
                ),
            )
            val text = buildWidgetRenderText(projection)

            assertEquals(WidgetContentState.CONFIGURATION_INCOMPLETE, projection.state)
            assertEquals(WidgetPrivacy.HIDDEN, projection.privacy)
            assertEquals(WidgetNavigation.Configure, projection.navigation)
            assertEquals("WIDGET DE INICIO", text.title)
            assertEquals("Configurá este widget para empezar.", text.primary)
            assertNull(text.schedule)
            assertNull(text.simultaneous)
            assertNull(text.countdownFormat)
            assertTrue(text.rows.isEmpty())
        }
    }

    // W005–W007 + W021: textos de «sin eventos» por modo; con privacidad Oculta, sólo la frase genérica.
    @Test
    fun `empty content names each mode and hidden privacy keeps the generic sentence`() {
        val expected = mapOf(
            WidgetMode.NEXT_SHIFT to ("PRÓXIMA JORNADA" to "No hay próximas jornadas."),
            WidgetMode.NEXT_DAY_OFF to ("PRÓXIMO FRANCO" to "No hay francos explícitos próximos."),
            WidgetMode.AUTOMATIC to ("PRÓXIMO EVENTO" to "No hay próximos eventos."),
        )
        assertEquals(WidgetMode.entries.toSet(), expected.keys)

        expected.forEach { (mode, texts) ->
            listOf(WidgetPrivacy.COMPLETE, WidgetPrivacy.REDUCED).forEach { privacy ->
                val text = buildWidgetRenderText(emptyProjection(mode, privacy))
                assertEquals(texts.first, text.title)
                assertEquals(texts.second, text.primary)
                assertNull(text.schedule)
                assertNull(text.countdownFormat)
            }
            val hidden = buildWidgetRenderText(emptyProjection(mode, WidgetPrivacy.HIDDEN))
            assertEquals("MIGUARDIA", hidden.title)
            assertEquals("Tenés información en MiGuardia.", hidden.primary)
        }
    }

    private fun emptyProjection(mode: WidgetMode, privacy: WidgetPrivacy): WidgetProjection {
        val projection = projectWidget(
            emptyResult(),
            WidgetProjectionConfig(mode = mode, privacy = privacy, size = WidgetSize.EXPANDED, configured = true),
        )
        assertEquals(WidgetContentState.EMPTY, projection.state)
        assertEquals(WidgetNavigation.Calendar, projection.navigation)
        return projection
    }

    private fun emptyResult() = projectNextEvent(
        now = NOW,
        zoneId = ZONE,
        input = NextEventInput(
            shifts = emptyList(),
            availabilityWindows = emptyList(),
            actualsByShiftId = emptyMap(),
            independentExtras = emptyList(),
            explicitDayStatuses = emptyList(),
            vacations = emptyList(),
            medicalLeaves = emptyList(),
        ),
    )

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val NOW: Instant = Instant.parse("2026-08-29T15:00:00Z")
    }
}
