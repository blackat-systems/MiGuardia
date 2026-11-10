package com.blackatsystems.miguardia.ui.notifications

import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorableNotificationLabelTest {
    @Test
    fun resumedAvailabilityUsesActualSegmentDateInsteadOfTheOwnerDate() {
        val event = availability(
            start = local("2026-10-24T04:00"),
            end = local("2026-10-24T12:00"),
            ownerDate = LocalDate.of(2026, 10, 23),
            isResumption = true,
        )

        assertEquals("Retén · 24/10/2026 · 04:00–12:00", event.restorableLabel())
    }

    @Test
    fun shiftsWithTheSameClockEndpointsDistinguishOneHourFromTwentyFiveHours() {
        val oneHour = shift(local("2026-10-23T08:00"), local("2026-10-23T09:00"))
        val twentyFiveHours = shift(local("2026-10-23T08:00"), local("2026-10-24T09:00"))

        assertNotEquals(oneHour.restorableLabel(), twentyFiveHours.restorableLabel())
        assertEquals("OF · 23/10/2026 · 08:00–09:00", oneHour.restorableLabel())
        assertEquals("OF · 23/10/2026 · 08:00–09:00 (+1 día)", twentyFiveHours.restorableLabel())
    }

    @Test
    fun availabilityWithTheSameClockEndpointsDistinguishesOneHourFromTwentyFiveHours() {
        val oneHour = availability(local("2026-10-23T08:00"), local("2026-10-23T09:00"))
        val twentyFiveHours = availability(local("2026-10-23T08:00"), local("2026-10-24T09:00"))

        assertNotEquals(oneHour.restorableLabel(), twentyFiveHours.restorableLabel())
        assertEquals("Retén · 23/10/2026 · 08:00–09:00", oneHour.restorableLabel())
        assertEquals("Retén · 23/10/2026 · 08:00–09:00 (+1 día)", twentyFiveHours.restorableLabel())
    }

    @Test
    fun bothKindsShowBothYearBoundaryDatesUsingTheStoredEventZone() {
        val start = Instant.parse("2027-01-01T01:00:00Z")
        val end = Instant.parse("2027-01-01T04:00:00Z")
        val events = listOf(
            "OF" to shift(start, end, ownerDate = LocalDate.of(2026, 12, 31)),
            "Retén" to availability(start, end, ownerDate = LocalDate.of(2026, 12, 31)),
        )

        events.forEach { (prefix, event) ->
            assertEquals(
                "$prefix · 31/12/2026 22:00 – 01/01/2027 01:00",
                event.restorableLabel(),
            )
        }
    }

    @Test
    fun renderingKeepsHistoricalPrefixesAndRestorationIdentityWithoutChangingTheOwner() {
        val events = listOf(
            "OF" to shift(local("2026-10-24T04:00"), local("2026-10-24T12:00")),
            "Retén" to availability(
                local("2026-10-24T04:00"),
                local("2026-10-24T12:00"),
                isResumption = true,
            ),
        )

        events.forEach { (prefix, event) ->
            val identity = event.identity
            val trackingKey = identity.trackingKey
            val ownerDate = event.ownerLocalDate

            assertTrue(event.restorableLabel().startsWith("$prefix · "))
            assertEquals(identity, event.identity)
            assertEquals(trackingKey, event.identity.trackingKey)
            assertEquals(ownerDate, event.ownerLocalDate)
        }
    }

    private fun shift(
        start: Instant,
        end: Instant,
        ownerDate: LocalDate = LocalDate.of(2026, 10, 23),
    ) = NextEventItem.Shift(
        shiftId = SHIFT_ID,
        start = start,
        end = end,
        zoneId = ZONE,
        ownerLocalDate = ownerDate,
        sector = WorkSector.PRIVATE_SECURITY,
        workTypeNameSnapshot = "Guardia",
        workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
        placeNameSnapshot = "Objetivo ficticio",
        placeAbbreviationSnapshot = "OF",
        startTimeSnapshot = start.atZone(ZONE).toLocalTime(),
        endTimeSnapshot = end.atZone(ZONE).toLocalTime(),
        colorArgbSnapshot = 0,
        positionSnapshot = null,
        hasHistoricalAddress = false,
    )

    private fun availability(
        start: Instant,
        end: Instant,
        ownerDate: LocalDate = LocalDate.of(2026, 10, 23),
        isResumption: Boolean = false,
    ) = NextEventItem.Availability(
        windowId = WINDOW_ID,
        start = start,
        end = end,
        zoneId = ZONE,
        ownerLocalDate = ownerDate,
        labelSnapshot = "Retén",
        isResumption = isResumption,
    )

    private fun local(value: String) = LocalDateTime.parse(value).atZone(ZONE).toInstant()

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val SHIFT_ID: UUID = UUID.fromString("33333333-3333-4333-8333-333333333333")
        val WINDOW_ID: UUID = UUID.fromString("44444444-4444-4444-8444-444444444444")
    }
}
