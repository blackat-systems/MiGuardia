package com.blackatsystems.miguardia.notifications

import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationVisibilityTest {
    @Test
    fun restorableShiftsAreDismissedEligibleEnabledAndStablyOrdered() {
        val later = shift(LATER_ID, NOW.plusSeconds(7200), NOW.plusSeconds(10_800))
        val earlier = shift(EARLIER_ID, NOW.plusSeconds(3600), NOW.plusSeconds(7200))
        val disabled = shift(DISABLED_ID, NOW.plusSeconds(1800), NOW.plusSeconds(5400))

        val result = restorableDismissedShifts(
            now = NOW,
            shifts = listOf(later, disabled, earlier),
            vacations = emptyList(),
            configs = listOf(ShiftNotificationConfig(disabled.id, emptyList())),
            dismissedShiftIds = setOf(later.id.toString(), earlier.id.toString(), disabled.id.toString()),
        )

        assertEquals(listOf(earlier.id, later.id), result.map(Shift::id))
    }

    @Test
    fun restorableShiftsExcludeEndedCancelledVacationAndUnknownIds() {
        val ended = shift(ENDED_ID, NOW.minusSeconds(7200), NOW)
        val cancelled = shift(CANCELLED_ID, NOW.plusSeconds(3600), NOW.plusSeconds(7200), ShiftStatus.CANCELLED)
        val vacation = shift(VACATION_ID, NOW.plusSeconds(5400), NOW.plusSeconds(9000))
        val vacationRange = Vacation(
            id = UUID.fromString("00000000-0000-0000-0000-000000000999"),
            startDate = vacation.localStartDate,
            endDateInclusive = vacation.localStartDate,
            createdAt = NOW,
            updatedAt = NOW,
        )

        val result = restorableDismissedShifts(
            now = NOW,
            shifts = listOf(ended, cancelled, vacation),
            vacations = listOf(vacationRange),
            configs = emptyList(),
            dismissedShiftIds = setOf(
                ended.id.toString(),
                cancelled.id.toString(),
                vacation.id.toString(),
                "not-a-uuid",
            ),
        )

        assertEquals(emptyList<Shift>(), result)
    }

    private fun shift(
        id: UUID,
        start: Instant,
        end: Instant,
        status: ShiftStatus = ShiftStatus.PLANNED,
    ): Shift {
        val zone = ZoneId.of("America/Argentina/Cordoba")
        return Shift(
            id = id,
            startAt = start,
            endAt = end,
            zoneId = zone,
            localStartDate = start.atZone(zone).toLocalDate(),
            objectiveNameSnapshot = "Objetivo ficticio",
            objectiveAbbreviationSnapshot = "QA",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.from(start.atZone(zone)),
            endTimeSnapshot = LocalTime.from(end.atZone(zone)),
            colorArgbSnapshot = 0xff336699.toInt(),
            position = null,
            status = status,
            sourceObjectiveId = null,
            sourceScheduleCombinationId = null,
            createdAt = NOW,
            updatedAt = NOW,
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-01T20:00:00Z")
        val EARLIER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000901")
        val LATER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000902")
        val DISABLED_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000903")
        val ENDED_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000904")
        val CANCELLED_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000905")
        val VACATION_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000906")
    }
}
