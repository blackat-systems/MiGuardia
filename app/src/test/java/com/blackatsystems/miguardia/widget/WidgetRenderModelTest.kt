package com.blackatsystems.miguardia.widget

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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRenderModelTest {
    @Test
    fun `complete event includes allowed context countdown and simultaneous count`() {
        val event = event()
        val text = buildWidgetRenderText(
            projection(
                events = listOf(event),
                total = 4,
                countdown = WidgetCountdown(event.start, countsToEnd = false),
            ),
        )

        assertEquals("PRÓXIMA JORNADA", text.title)
        assertEquals("Turno ficticio · Objetivo ficticio (OF) · Puesto: Acceso", text.primary)
        assertTrue(requireNotNull(text.schedule).contains("19:00–07:00"))
        assertEquals("4 eventos a la vez", text.simultaneous)
        assertEquals("Comienza en %s", text.countdownFormat)
        assertTrue(text.rows.single().contains("OF"))
    }

    @Test
    fun `reduced event keeps date and time without type place abbreviation position or color`() {
        val reducedEvent = event().copy(details = null)
        val text = buildWidgetRenderText(
            projection(events = listOf(reducedEvent), privacy = WidgetPrivacy.REDUCED),
        )
        val allText = listOfNotNull(text.title, text.primary, text.schedule, *text.rows.toTypedArray())
            .joinToString(" ")

        assertEquals("PRÓXIMO EVENTO", text.title)
        assertTrue(allText.contains("29/08"))
        assertTrue(allText.contains("19:00–07:00"))
        assertFalse(allText.contains("Turno ficticio"))
        assertFalse(allText.contains("Objetivo ficticio"))
        assertFalse(allText.contains("OF"))
        assertFalse(allText.contains("Acceso"))
    }

    @Test
    fun `hidden event exposes only the generic privacy sentence`() {
        val text = buildWidgetRenderText(
            projection(
                events = emptyList(),
                privacy = WidgetPrivacy.HIDDEN,
                total = 0,
                countdown = null,
            ),
        )
        val allText = listOfNotNull(text.title, text.primary, text.schedule, text.simultaneous)
            .joinToString(" ")

        assertEquals("MIGUARDIA", text.title)
        assertEquals("Tenés información en MiGuardia.", text.primary)
        assertFalse(allText.contains("29/08"))
        assertFalse(allText.contains("19:00"))
        assertNull(text.countdownFormat)
        assertTrue(text.rows.isEmpty())
    }

    @Test
    fun `global theme resolves light dark and system without an instance override`() {
        val light = resolveWidgetPalette(AppThemeMode.LIGHT, systemDark = true)
        val dark = resolveWidgetPalette(AppThemeMode.DARK, systemDark = false)

        assertEquals(light, resolveWidgetPalette(AppThemeMode.SYSTEM, systemDark = false))
        assertEquals(dark, resolveWidgetPalette(AppThemeMode.SYSTEM, systemDark = true))
        assertFalse(light.background == dark.background)
        assertFalse(light.primaryText == dark.primaryText)
    }

    @Test
    fun `legacy size cutoff requires both expanded dimensions`() {
        assertFalse(isExpandedWidgetSize(249f, 360f))
        assertFalse(isExpandedWidgetSize(250f, 359f))
        assertTrue(isExpandedWidgetSize(250f, 360f))
    }

    private fun projection(
        events: List<WidgetEventPresentation>,
        privacy: WidgetPrivacy = WidgetPrivacy.COMPLETE,
        total: Int = events.size,
        countdown: WidgetCountdown? = null,
    ) = WidgetProjection(
        referenceInstant = NOW,
        mode = WidgetMode.AUTOMATIC,
        privacy = privacy,
        size = WidgetSize.EXPANDED,
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

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val NOW: Instant = Instant.parse("2026-08-29T15:00:00Z")
        val DATE: LocalDate = LocalDate.of(2026, 8, 29)
        val START: Instant = DATE.atTime(19, 0).atZone(ZONE).toInstant()
        val END: Instant = DATE.plusDays(1).atTime(7, 0).atZone(ZONE).toInstant()
        val SHIFT_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000001")
    }
}
