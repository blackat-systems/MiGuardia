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
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        assertEquals("29/08/2026 · 19:00–07:00 (+1 día)", text.schedule)
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
        assertTrue(allText.contains("29/08/2026 · 19:00–07:00 (+1 día)"))
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
    fun availabilityWindowsWithEqualClockTimesDistinguishOneHourFromTwentyFiveHours() {
        val ownerDate = LocalDate.of(2026, 10, 23)
        val start = ownerDate.atTime(8, 0).atZone(ZONE).toInstant()
        val oneHour = availabilityEvent(
            ownerDate = ownerDate,
            start = start,
            end = ownerDate.atTime(9, 0).atZone(ZONE).toInstant(),
        )
        val nextDayEnd = ownerDate.plusDays(1).atTime(9, 0).atZone(ZONE).toInstant()
        val twentyFiveHours = oneHour.copy(
            identity = NextEventIdentity.Availability(SECOND_WINDOW_ID, start, nextDayEnd),
            end = nextDayEnd,
        )

        val oneHourText = buildWidgetRenderText(projection(events = listOf(oneHour)))
        val twentyFiveHourText = buildWidgetRenderText(projection(events = listOf(twentyFiveHours)))

        assertNotEquals(
            "The visible interval must distinguish a one-hour window from a twenty-five-hour window.",
            oneHourText.schedule,
            twentyFiveHourText.schedule,
        )
        assertEquals("23/10/2026 · 08:00–09:00", oneHourText.schedule)
        assertEquals("23/10/2026 · 08:00–09:00 (+1 día)", twentyFiveHourText.schedule)
    }

    @Test
    fun resumedAvailabilityShowsItsSegmentDateAndKeepsTheOwnerNavigationDate() {
        val ownerDate = LocalDate.of(2026, 10, 23)
        val segmentDate = ownerDate.plusDays(1)
        val resumed = availabilityEvent(
            ownerDate = ownerDate,
            start = segmentDate.atTime(4, 0).atZone(ZONE).toInstant(),
            end = segmentDate.atTime(12, 0).atZone(ZONE).toInstant(),
            isResumption = true,
        )
        val source = projection(events = listOf(resumed)).copy(navigation = resumed.navigation)

        val text = buildWidgetRenderText(source)

        assertEquals(WidgetNavigation.Date(ownerDate), source.navigation)
        assertEquals(WidgetNavigation.Date(ownerDate), source.events.single().navigation)
        assertEquals(ownerDate, source.events.single().ownerLocalDate)
        assertEquals("24/10/2026 · 04:00–12:00", text.schedule)
        assertEquals("Retén · reanudada", text.primary)
        assertEquals(listOf("Retén · 24/10/2026 · 04:00–12:00"), text.rows)
    }

    @Test
    fun resumedMultiDayScheduleRespectsAllPrivacyLevelsWithoutChangingNavigation() {
        val ownerDate = LocalDate.of(2026, 10, 23)
        val resumed = availabilityEvent(
            ownerDate,
            ownerDate.plusDays(1).atTime(4, 15).atZone(ZONE).toInstant(),
            ownerDate.plusDays(2).atTime(8, 30).atZone(ZONE).toInstant(),
            isResumption = true,
        )
        WidgetPrivacy.entries.forEach { privacy ->
            val visibleEvent = if (privacy == WidgetPrivacy.REDUCED) resumed.copy(details = null) else resumed
            val source = projection(listOf(visibleEvent), privacy = privacy, total = 3)
                .copy(navigation = WidgetNavigation.Date(ownerDate))
            val text = buildWidgetRenderText(source)
            val allText = listOfNotNull(text.title, text.primary, text.schedule, text.simultaneous, *text.rows.toTypedArray()).joinToString(" ")

            assertEquals(WidgetNavigation.Date(ownerDate), source.navigation)
            if (privacy == WidgetPrivacy.HIDDEN) {
                assertNull(text.schedule)
                assertNull(text.simultaneous)
                assertNull(text.countdownFormat)
                assertTrue(text.rows.isEmpty())
                listOf("24/10", "25/10", "04:15", "08:30", "+1", "Retén", "reanudada").forEach {
                    assertFalse("Hidden privacy leaked $it", allText.contains(it))
                }
            } else {
                assertEquals("24/10/2026 · 04:15–08:30 (+1 día)", text.schedule)
                if (privacy == WidgetPrivacy.REDUCED) {
                    assertFalse(allText.contains("Retén"))
                    assertFalse(allText.contains("reanudada"))
                }
            }
        }
    }

    @Test
    fun equalLookingShiftsKeepBothUuidsAndTheirHistoricalPositionColorAndOwnerDestinations() {
        val first = event()
        val second = first.copy(
            identity = NextEventIdentity.Shift(SECOND_WINDOW_ID),
            navigation = WidgetNavigation.Shift(SECOND_WINDOW_ID, DATE),
        )
        val source = projection(listOf(first, second))

        val text = buildWidgetRenderText(source)

        assertEquals(2, text.rows.size)
        assertEquals(text.rows[0], text.rows[1])
        assertNotEquals(source.events[0].identity, source.events[1].identity)
        assertEquals(WidgetNavigation.Shift(SHIFT_ID, DATE), source.events[0].navigation)
        assertEquals(WidgetNavigation.Shift(SECOND_WINDOW_ID, DATE), source.events[1].navigation)
        assertEquals("Acceso", first.details?.position)
        assertEquals(0xFF315DA8.toInt(), first.details?.colorArgb)
        assertTrue(text.rows.all { it.contains("Puesto: Acceso") })
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
    fun `widget text and actions retain normal text contrast for every theme resolution`() {
        AppThemeMode.entries.forEach { theme ->
            listOf(false, true).forEach { systemDark ->
                val palette = resolveWidgetPalette(theme, systemDark)
                listOf(palette.primaryText, palette.secondaryText, palette.action).forEach { text ->
                    val ratio = contrastRatio(text, palette.background)
                    assertTrue("$theme systemDark=$systemDark contrast=$ratio", ratio >= 4.5)
                }
                assertTrue(contrastRatio(palette.defaultAccent, palette.background) >= 3.0)
            }
        }
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

    private fun availabilityEvent(
        ownerDate: LocalDate,
        start: Instant,
        end: Instant,
        isResumption: Boolean = false,
    ) = WidgetEventPresentation(
        identity = NextEventIdentity.Availability(WINDOW_ID, start, end),
        ownerLocalDate = ownerDate,
        zoneId = ZONE,
        start = start,
        end = end,
        isActive = false,
        details = WidgetEventDetails(
            kind = WidgetEventKind.AVAILABILITY,
            workTypeName = null,
            placeName = null,
            placeAbbreviation = null,
            position = null,
            availabilityLabel = "Retén",
            isResumption = isResumption,
            colorArgb = null,
        ),
        navigation = WidgetNavigation.Date(ownerDate),
    )

    private fun contrastRatio(first: Int, second: Int): Double {
        fun luminance(argb: Int): Double {
            fun channel(shift: Int): Double {
                val component = ((argb ushr shift) and 0xFF) / 255.0
                return if (component <= 0.04045) component / 12.92
                else ((component + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (maxOf(firstLuminance, secondLuminance) + 0.05) /
            (minOf(firstLuminance, secondLuminance) + 0.05)
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val NOW: Instant = Instant.parse("2026-08-29T15:00:00Z")
        val DATE: LocalDate = LocalDate.of(2026, 8, 29)
        val START: Instant = DATE.atTime(19, 0).atZone(ZONE).toInstant()
        val END: Instant = DATE.plusDays(1).atTime(7, 0).atZone(ZONE).toInstant()
        val SHIFT_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000001")
        val WINDOW_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000012")
        val SECOND_WINDOW_ID: UUID = UUID.fromString("93000000-0000-0000-0000-000000000013")
    }
}
