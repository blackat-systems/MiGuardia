package com.blackatsystems.miguardia.stage14

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.V2AppTestFixture
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.widget.WidgetContentState
import com.blackatsystems.miguardia.core.domain.widget.WidgetCountdown
import com.blackatsystems.miguardia.core.domain.widget.WidgetEventDetails
import com.blackatsystems.miguardia.core.domain.widget.WidgetEventKind
import com.blackatsystems.miguardia.core.domain.widget.WidgetEventPresentation
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetNavigation
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjection
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjectionConfig
import com.blackatsystems.miguardia.core.domain.widget.WidgetSize
import com.blackatsystems.miguardia.core.domain.widget.projectWidget
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.ui.nextevent.V2WorkEventSourceObserver
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.widget.WidgetRemoteViewsRenderer
import com.blackatsystems.miguardia.widget.buildWidgetRenderText
import com.blackatsystems.miguardia.widget.resolveWidgetPalette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Etapa 14 · Widget de inicio: las vistas reales (`RemoteViews`) aplicadas en un contenedor de prueba.
 * No es un Widget puesto en la pantalla de inicio, no se publica nada y no se guardan imágenes.
 * La letra de Android se simula con un contexto propio; no se cambia ningún ajuste del teléfono.
 */
@RunWith(AndroidJUnit4::class)
class Stage14WidgetRemoteViewsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val renderer = WidgetRemoteViewsRenderer(context)
    private val palette = resolveWidgetPalette(AppThemeMode.LIGHT, false)

    // W020: las vistas del Widget siguen la letra de Android (150 y 200 %).
    @Test
    fun simulatedAndroidFontScaleReallyEnlargesTheWidgetText() {
        HostSize.entries.forEach { host ->
            val standard = inflate(host, TestFontScale.STANDARD).findViewById<TextView>(R.id.widget_title).textSize
            val large = inflate(host, TestFontScale.LARGE).findViewById<TextView>(R.id.widget_title).textSize
            val extraLarge = inflate(host, TestFontScale.EXTRA_LARGE).findViewById<TextView>(R.id.widget_title).textSize

            assertTrue("${host.label}: 150 % no agrandó el título ($standard → $large)", large >= standard * 1.4f)
            assertTrue("${host.label}: 200 % no agrandó el título ($standard → $extraLarge)", extraLarge >= standard * 1.8f)
        }
    }

    // W015 / V015 (base de comparación): con letra estándar, título, texto principal y horario se ven enteros
    // en el menor tamaño que el Widget declara para cada vista.
    @Test
    fun standardFontShowsTitlePrimaryAndScheduleCompleteAtTheDeclaredMinimumSizes() {
        val cut = cutTexts(TestFontScale.STANDARD)

        assertTrue("Textos cortados con letra estándar:\n${cut.joinToString("\n")}", cut.isEmpty())
    }

    // W020 / V015: con letra al 150 y 200 % los textos principales no quedan cortados ni con «…».
    @Test
    fun largeAndExtraLargeFontKeepTitlePrimaryAndScheduleCompleteAtTheDeclaredMinimumSizes() {
        val cut = cutTexts(TestFontScale.LARGE) + cutTexts(TestFontScale.EXTRA_LARGE)

        assertTrue("Textos cortados con letra grande:\n${cut.joinToString("\n")}", cut.isEmpty())
    }

    // W017: con la cuenta regresiva vencida se oculta el contador y el horario absoluto sigue visible.
    @Test
    fun expiredCountdownIsHiddenAndTheAbsoluteScheduleStaysVisible() {
        WidgetSize.entries.forEach { size ->
            listOf(
                WidgetCountdown(NOW.minusSeconds(1), countsToEnd = false),
                WidgetCountdown(NOW, countsToEnd = true),
            ).forEach { expired ->
                val view = renderer.createViews(
                    projection(size, countdown = expired),
                    appWidgetId = APP_WIDGET_ID,
                    weatherText = null,
                    palette = palette,
                    elapsedRealtime = 50_000L,
                ).apply(context, null)
                val schedule = view.findViewById<TextView>(R.id.widget_schedule)

                assertEquals("$size/$expired", View.GONE, view.findViewById<View>(R.id.widget_countdown).visibility)
                assertEquals("$size/$expired", View.VISIBLE, schedule.visibility)
                assertEquals("29/08/2026 · 19:00–07:00 (+1 día)", schedule.text.toString())
                assertTrue(
                    view.findViewById<View>(R.id.widget_root).contentDescription.toString()
                        .contains("29/08/2026 · 19:00–07:00 (+1 día)"),
                )
            }
        }
    }

    // W021: vista de «recuperación de datos», también con letra al 200 %. Lleva al Calendario protegido.
    // Nota: `recoveryViews` lee (sin escribir) la preferencia de tema para elegir colores
    // (WidgetRemoteViewsRenderer.kt:374-380); lo afirmado acá no depende de ese valor.
    @Test
    fun recoveryViewShowsItsTextsWithoutLaborDataAndOpensTheApp() {
        listOf(TestFontScale.STANDARD, TestFontScale.EXTRA_LARGE).forEach { scale ->
            val view = renderer.recoveryViews(APP_WIDGET_ID).apply(fontContext(scale), null)
            val strings = visibleStrings(view)

            assertEquals("MIGUARDIA", view.findViewById<TextView>(R.id.widget_title).text.toString())
            assertEquals(
                "Abrí MiGuardia para terminar la recuperación de tus datos.",
                view.findViewById<TextView>(R.id.widget_primary).text.toString(),
            )
            assertEquals(
                "MIGUARDIA. Abrí MiGuardia para terminar la recuperación de tus datos.",
                view.findViewById<View>(R.id.widget_root).contentDescription.toString(),
            )
            assertEquals(View.GONE, view.findViewById<View>(R.id.widget_schedule).visibility)
            assertEquals(View.GONE, view.findViewById<View>(R.id.widget_countdown).visibility)
            assertEquals(View.GONE, view.findViewById<View>(R.id.widget_simultaneous).visibility)
            assertTrue(view.findViewById<View>(R.id.widget_root).hasOnClickListeners())
            assertFalse(strings.any { it.contains("19:00") || it.contains("2026") || it.contains("Hospital") })
        }
    }

    // W008: con privacidad Completa se ven tipo, lugar y puesto, pero la dirección guardada del objetivo
    // no aparece en ningún texto ni descripción accesible, ni viaja en la proyección del Widget.
    @Test
    fun completePrivacyNeverShowsOrCarriesTheStoredObjectiveAddress() = runBlocking<Unit> {
        val databaseName = "stage14-widget-views-${UUID.randomUUID()}.db"
        val store = LocalDataStore.create(context, databaseName)
        try {
            val shift = shiftWithAddress()
            store.v2Shifts.insert(V2AppTestFixture.writeFor(store, shift, LocalDate.of(2026, 8, 1)))
            val source = withTimeout(5_000) {
                V2WorkEventSourceObserver(
                    shifts = store.v2Shifts,
                    availabilityWindows = store.availabilityWindows,
                    shiftActuals = store.shiftActuals,
                    independentExtras = store.independentExtraWork,
                    explicitDayStatuses = store.explicitDayStatuses,
                    vacations = store.vacations,
                    medicalLeaves = store.medicalLeaves,
                    workConfiguration = store.workConfiguration,
                ).observe(ADDRESS_TODAY).first()
            }
            assertEquals(STORED_ADDRESS, source.shifts.single().shift.objectiveAddressSnapshot)
            val result = projectNextEvent(ADDRESS_NOW, ZONE, source.toInput())
            assertTrue((result.primaryEvents.single() as NextEventItem.Shift).hasHistoricalAddress)

            WidgetSize.entries.forEach { size ->
                val projection = projectWidget(
                    result,
                    WidgetProjectionConfig(
                        mode = WidgetMode.AUTOMATIC,
                        privacy = WidgetPrivacy.COMPLETE,
                        size = size,
                        configured = true,
                    ),
                )
                val view = renderer.createViews(
                    projection,
                    appWidgetId = APP_WIDGET_ID,
                    weatherText = "Clima: despejado · 12–18 °C".takeIf { size == WidgetSize.EXPANDED },
                    palette = palette,
                ).apply(context, null)
                val strings = visibleStrings(view)

                // Prueba de que los datos permitidos sí llegaron a la vista.
                assertTrue("$size: $strings", strings.any { it.contains("Objetivo ficticio") })
                assertTrue("$size: $strings", strings.any { it.contains("Puesto: Acceso") })
                ADDRESS_FRAGMENTS.forEach { fragment ->
                    assertFalse("$size muestra «$fragment»: $strings", strings.any { it.contains(fragment, ignoreCase = true) })
                    assertFalse("$size transporta «$fragment»", projection.toString().contains(fragment, ignoreCase = true))
                    assertFalse(buildWidgetRenderText(projection).toString().contains(fragment, ignoreCase = true))
                }
            }
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun cutTexts(scale: TestFontScale): List<String> = buildList {
        HostSize.entries.forEach { host ->
            val view = inflate(host, scale)
            MAIN_TEXTS.forEach { (label, viewId) ->
                cutReason(view.findViewById(viewId))?.let { reason ->
                    add("${host.label} · letra ${scale.percent} % · $label: $reason")
                }
            }
        }
    }

    private fun inflate(host: HostSize, scale: TestFontScale): View {
        val scaled = fontContext(scale)
        val view = renderer.createViews(
            projection(host.size, countdown = WidgetCountdown(START, countsToEnd = false)),
            appWidgetId = APP_WIDGET_ID,
            weatherText = null,
            palette = palette,
            elapsedRealtime = 50_000L,
        ).apply(scaled, null)
        val density = scaled.resources.displayMetrics.density
        view.measure(
            View.MeasureSpec.makeMeasureSpec((host.widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((host.heightDp * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    /** Contexto nuevo con otra escala de letra; el teléfono y la app no cambian. */
    private fun fontContext(scale: TestFontScale): Context {
        val configuration = Configuration(context.resources.configuration).apply { fontScale = scale.scale }
        return context.createConfigurationContext(configuration)
    }

    /** `null` si el texto se ve entero; si no, por qué queda cortado. */
    private fun cutReason(text: TextView): String? {
        val shown = "«${text.text}»"
        if (text.visibility != View.VISIBLE) return "$shown no está visible"
        val textLayout = text.layout ?: return "$shown no se pudo medir"
        if (textLayout.lineCount == 0) return "$shown quedó sin renglones"
        val allowedLines = text.maxLines.takeIf { it > 0 } ?: Int.MAX_VALUE
        if (textLayout.lineCount > allowedLines) {
            return "$shown ocupa ${textLayout.lineCount} renglones y sólo se ven $allowedLines"
        }
        if ((0 until textLayout.lineCount).any { line -> textLayout.getEllipsisCount(line) > 0 }) {
            return "$shown termina en puntos suspensivos"
        }
        val neededHeight = textLayout.getLineBottom(textLayout.lineCount - 1) +
            text.compoundPaddingTop + text.compoundPaddingBottom
        if (text.height < neededHeight) {
            return "$shown necesita $neededHeight px de alto y recibió ${text.height}"
        }
        return null
    }

    private fun visibleStrings(root: View): List<String> {
        val found = mutableListOf<String>()
        fun visit(view: View) {
            view.contentDescription?.let { found += it.toString() }
            if (view is TextView) found += view.text.toString()
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
        }
        visit(root)
        return found
    }

    private fun projection(size: WidgetSize, countdown: WidgetCountdown?) = WidgetProjection(
        referenceInstant = NOW,
        mode = WidgetMode.AUTOMATIC,
        privacy = WidgetPrivacy.COMPLETE,
        size = size,
        state = WidgetContentState.EVENTS,
        events = listOf(event()),
        totalSimultaneousEvents = 1,
        dayOff = null,
        countdown = countdown,
        navigation = WidgetNavigation.Shift(SHIFT_ID, DATE),
    )

    private fun event() = WidgetEventPresentation(
        identity = NextEventIdentity.Shift(SHIFT_ID),
        ownerLocalDate = DATE,
        zoneId = ZONE,
        start = START,
        end = END,
        isActive = false,
        details = WidgetEventDetails(
            kind = WidgetEventKind.SHIFT,
            workTypeName = "Guardia",
            placeName = "Hospital Norte",
            placeAbbreviation = "HN",
            position = null,
            availabilityLabel = null,
            isResumption = false,
            colorArgb = 0xFF315DA8.toInt(),
        ),
        navigation = WidgetNavigation.Shift(SHIFT_ID, DATE),
    )

    private fun shiftWithAddress(): Shift {
        val date = LocalDate.of(2026, 8, 16)
        return Shift(
            id = UUID.fromString("e1400000-0000-0000-0000-000000000002"),
            startAt = ZonedDateTime.of(date, LocalTime.of(19, 0), ZONE).toInstant(),
            endAt = ZonedDateTime.of(date.plusDays(1), LocalTime.of(7, 0), ZONE).toInstant(),
            zoneId = ZONE,
            localStartDate = date,
            objectiveNameSnapshot = "Objetivo ficticio",
            objectiveAbbreviationSnapshot = "FIC",
            objectiveAddressSnapshot = STORED_ADDRESS,
            startTimeSnapshot = LocalTime.of(19, 0),
            endTimeSnapshot = LocalTime.of(7, 0),
            colorArgbSnapshot = 0xFF315DA8.toInt(),
            position = "Acceso",
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = V2AppTestFixture.PLACEHOLDER_OBJECTIVE_ID,
            createdAt = ADDRESS_NOW,
            updatedAt = ADDRESS_NOW,
        )
    }

    /**
     * Menor espacio en el que se usa cada vista: el mínimo declarado del Widget (180×96 dp,
     * `res/xml/widget_next_event_info.xml`) y el umbral de la vista ampliada (250×360 dp,
     * `WidgetRemoteViewsRenderer.kt:30-33`). Un launcher real suele dar más ancho.
     */
    private enum class HostSize(val label: String, val size: WidgetSize, val widthDp: Int, val heightDp: Int) {
        COMPACT("Compacta 180×96 dp", WidgetSize.COMPACT, 180, 96),
        EXPANDED("Ampliada 250×360 dp", WidgetSize.EXPANDED, 250, 360),
    }

    private companion object {
        val ZONE: ZoneId = AppDefaults.zoneId()
        val NOW: Instant = Instant.parse("2026-08-29T15:00:00Z")
        val DATE: LocalDate = LocalDate.of(2026, 8, 29)
        val START: Instant = DATE.atTime(19, 0).atZone(ZONE).toInstant()
        val END: Instant = DATE.plusDays(1).atTime(7, 0).atZone(ZONE).toInstant()
        val SHIFT_ID: UUID = UUID.fromString("e1400000-0000-0000-0000-000000000003")
        val ADDRESS_TODAY: LocalDate = LocalDate.of(2026, 8, 15)
        val ADDRESS_NOW: Instant = ZonedDateTime.of(ADDRESS_TODAY, LocalTime.NOON, ZONE).toInstant()
        const val STORED_ADDRESS = "Calle Ficticia 9876, Barrio Inventado"
        val ADDRESS_FRAGMENTS = listOf("Calle Ficticia", "9876", "Barrio Inventado")
        val MAIN_TEXTS = listOf(
            "título" to R.id.widget_title,
            "texto principal" to R.id.widget_primary,
            "horario" to R.id.widget_schedule,
        )

        // ID que ningún launcher asignó; sólo arma la identidad de los toques, no se publica nada.
        const val APP_WIDGET_ID = 914_000_003
    }
}
