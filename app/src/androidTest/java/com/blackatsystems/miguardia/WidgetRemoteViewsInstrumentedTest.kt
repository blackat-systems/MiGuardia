package com.blackatsystems.miguardia

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.widget.WidgetContentState
import com.blackatsystems.miguardia.core.domain.widget.WidgetCountdown
import com.blackatsystems.miguardia.core.domain.widget.WidgetEventDetails
import com.blackatsystems.miguardia.core.domain.widget.WidgetEventKind
import com.blackatsystems.miguardia.core.domain.widget.WidgetEventPresentation
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetNavigation
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjection
import com.blackatsystems.miguardia.core.domain.widget.WidgetSize
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.widget.WidgetRemoteViewsRenderer
import com.blackatsystems.miguardia.widget.resolveWidgetPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRemoteViewsInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val renderer = WidgetRemoteViewsRenderer(context)

    @Test
    fun initialLayoutIsSafeAndNeverReusesTheFictitiousPreviewContent() {
        val view = RemoteViews(context.packageName, R.layout.widget_next_event_compact).apply(context, null)
        val text = view.allText().joinToString(" ")

        assertEquals("WIDGET DE INICIO", view.findViewById<TextView>(R.id.widget_title).text.toString())
        assertEquals(
            "Configurá este widget para empezar.",
            view.findViewById<TextView>(R.id.widget_primary).text.toString(),
        )
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_schedule).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_countdown).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_simultaneous).visibility)
        assertFalse(text.contains("Turno ficticio"))
        assertFalse(text.contains("Objetivo Norte"))
        assertFalse(text.contains("19:00"))
    }

    @Test
    fun previewLayoutUsesOnlyRemoteViewsSupportedClasses() {
        val preview = RemoteViews(context.packageName, R.layout.widget_next_event_preview).apply(context, null)

        assertNotNull(preview)
    }

    @Test
    fun compactAndExpandedRenderChronometerRowsAndFreshWeatherAtMonotonicBase() {
        val event = event()
        val simultaneous = event().copy(
            identity = NextEventIdentity.Shift(SECOND_SHIFT_ID),
            navigation = WidgetNavigation.Shift(SECOND_SHIFT_ID, DATE),
        )
        val compact = renderer.createViews(
            projection(WidgetSize.COMPACT, listOf(event), total = 2),
            appWidgetId = 71,
            weatherText = null,
            palette = resolveWidgetPalette(AppThemeMode.LIGHT, false),
            elapsedRealtime = 50_000L,
        ).apply(context, null)
        val expanded = renderer.createViews(
            projection(WidgetSize.EXPANDED, listOf(event, simultaneous), total = 2),
            appWidgetId = 71,
            weatherText = "Clima: lluvia · 12–18 °C",
            palette = resolveWidgetPalette(AppThemeMode.DARK, true),
            elapsedRealtime = 50_000L,
        ).apply(context, null)

        val compactChronometer = compact.findViewById<Chronometer>(R.id.widget_countdown)
        assertTrue(compactChronometer.isCountDown)
        assertEquals(50_000L + (START.toEpochMilli() - NOW.toEpochMilli()), compactChronometer.base)
        assertNull(compact.findViewById<View>(R.id.widget_row_1))
        assertEquals(View.VISIBLE, expanded.findViewById<View>(R.id.widget_row_1).visibility)
        assertEquals(
            "Clima: lluvia · 12–18 °C\nDatos meteorológicos: Open-Meteo",
            expanded.findViewById<TextView>(R.id.widget_weather).text.toString(),
        )
        assertTrue(
            expanded.findViewById<View>(R.id.widget_configure).layoutParams.height >=
                (47 * context.resources.displayMetrics.density).toInt(),
        )
    }

    @Test
    fun lateCountdownBoundaryIsHiddenInsteadOfRunningAtZero() {
        val view = renderer.createViews(
            projection(
                WidgetSize.COMPACT,
                listOf(event()),
                total = 1,
                countdown = WidgetCountdown(NOW.minusSeconds(1), countsToEnd = false),
            ),
            appWidgetId = 73,
            weatherText = null,
            palette = resolveWidgetPalette(AppThemeMode.LIGHT, false),
            elapsedRealtime = 50_000L,
        ).apply(context, null)

        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_countdown).visibility)
    }

    @Test
    fun hiddenRemoteViewsContainNoForbiddenLaborTextOrCountdown() {
        val projection = projection(
            size = WidgetSize.EXPANDED,
            events = emptyList(),
            total = 0,
            privacy = WidgetPrivacy.HIDDEN,
            countdown = null,
        )
        val view = renderer.createViews(
            projection,
            appWidgetId = 72,
            weatherText = "Clima prohibido",
            palette = resolveWidgetPalette(AppThemeMode.LIGHT, false),
        ).apply(context, null)
        val text = view.allText().joinToString(" ")

        assertEquals("Tenés información en MiGuardia.", view.findViewById<TextView>(R.id.widget_primary).text.toString())
        listOf("Objetivo ficticio", "Turno ficticio", "19:00", "29/08", "Clima prohibido").forEach {
            assertFalse(text.contains(it))
        }
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_countdown).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_weather).visibility)
    }

    @Test
    fun navigationConfigurationAndWeatherPendingIntentsAreImmutableAndInstanceScoped() {
        val navigation = WidgetNavigation.Shift(SHIFT_ID, DATE)
        val first = renderer.navigationPendingIntentForTest(81, navigation)
        val second = renderer.navigationPendingIntentForTest(82, navigation)
        val configure = renderer.configurationPendingIntentForTest(81)
        val weather = renderer.weatherPendingIntentForTest(81)

        assertFalse(first == second)
        listOf(first, second, configure, weather).forEach { pendingIntent ->
            assertEquals(context.packageName, pendingIntent.creatorPackage)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) assertTrue(pendingIntent.isImmutable)
        }
        assertNotNull(configure)
        assertNotNull(weather)
    }

    @Test
    fun collidingUuidHashesStillProduceDistinctShiftPendingIntents() {
        val firstId = UUID.fromString("48f9dff2-13f3-4851-a1d7-928d2345728e")
        val secondId = UUID.fromString("8d67af3b-689e-4c1b-a8a9-9335b3278c12")
        assertEquals(firstId.toString().hashCode(), secondId.toString().hashCode())

        val first = renderer.navigationPendingIntentForTest(81, WidgetNavigation.Shift(firstId, DATE))
        val second = renderer.navigationPendingIntentForTest(81, WidgetNavigation.Shift(secondId, DATE))

        assertFalse(first == second)
    }

    private fun projection(
        size: WidgetSize,
        events: List<WidgetEventPresentation>,
        total: Int,
        privacy: WidgetPrivacy = WidgetPrivacy.COMPLETE,
        countdown: WidgetCountdown? = WidgetCountdown(START, countsToEnd = false),
    ) = WidgetProjection(
        referenceInstant = NOW,
        mode = WidgetMode.AUTOMATIC,
        privacy = privacy,
        size = size,
        state = WidgetContentState.EVENTS,
        events = events,
        totalSimultaneousEvents = total,
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
            workTypeName = "Turno ficticio",
            placeName = "Objetivo ficticio",
            placeAbbreviation = "OF",
            position = "Acceso",
            availabilityLabel = null,
            isResumption = false,
            colorArgb = 0xFF315DA8.toInt(),
        ),
        navigation = WidgetNavigation.Shift(SHIFT_ID, DATE),
    )

    private fun View.allText(): List<String> = buildList {
        if (this@allText is TextView) add(this@allText.text.toString())
        if (this@allText is ViewGroup) {
            for (index in 0 until childCount) addAll(getChildAt(index).allText())
        }
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val NOW: Instant = Instant.parse("2026-08-29T15:00:00Z")
        val DATE: LocalDate = LocalDate.of(2026, 8, 29)
        val START: Instant = DATE.atTime(19, 0).atZone(ZONE).toInstant()
        val END: Instant = DATE.plusDays(1).atTime(7, 0).atZone(ZONE).toInstant()
        val SHIFT_ID: UUID = UUID.fromString("94000000-0000-0000-0000-000000000001")
        val SECOND_SHIFT_ID: UUID = UUID.fromString("94000000-0000-0000-0000-000000000002")
    }
}
