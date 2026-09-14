package com.blackatsystems.miguardia.ui.management

import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class V2ShiftActualDifferenceTest {
    private val shiftId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val timelineId = UUID.fromString("00000000-0000-0000-0000-000000000102")
    private val plannedStart = Instant.parse("2026-09-10T11:00:00Z")
    private val plannedEnd = Instant.parse("2026-09-10T19:00:00Z")
    private val shift = Shift(
        id = shiftId,
        startAt = plannedStart,
        endAt = plannedEnd,
        zoneId = ZoneId.of("America/Argentina/Cordoba"),
        localStartDate = LocalDate.of(2026, 9, 10),
        objectiveNameSnapshot = "Objetivo ficticio",
        objectiveAbbreviationSnapshot = "OBJ",
        objectiveAddressSnapshot = null,
        startTimeSnapshot = LocalTime.of(8, 0),
        endTimeSnapshot = LocalTime.of(16, 0),
        colorArgbSnapshot = 0xFF336699.toInt(),
        position = null,
        status = ShiftStatus.PLANNED,
        sourceObjectiveId = UUID.fromString("00000000-0000-0000-0000-000000000103"),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun describesTheMostDirectSingleBoundaryDifferences() {
        assertEquals(
            "Trabajaste 1 hora más de lo planeado.",
            shift.humanActualDifference(actual(plannedStart, plannedEnd.plusSeconds(3_600))),
        )
        assertEquals(
            "Te fuiste 30 minutos antes.",
            shift.humanActualDifference(actual(plannedStart, plannedEnd.minusSeconds(1_800))),
        )
        assertEquals(
            "Entraste 1 hora más tarde.",
            shift.humanActualDifference(actual(plannedStart.plusSeconds(3_600), plannedEnd)),
        )
        assertEquals(
            "Entraste 15 minutos antes.",
            shift.humanActualDifference(actual(plannedStart.minusSeconds(900), plannedEnd)),
        )
    }

    @Test
    fun describesNetDurationWhenBothBoundariesChanged() {
        assertEquals(
            "Trabajaste 30 minutos más de lo planeado.",
            shift.humanActualDifference(
                actual(plannedStart.plusSeconds(1_800), plannedEnd.plusSeconds(3_600)),
            ),
        )
        assertEquals(
            "Cumpliste la misma cantidad de horas con otro horario.",
            shift.humanActualDifference(
                actual(plannedStart.plusSeconds(1_800), plannedEnd.plusSeconds(1_800)),
            ),
        )
    }

    private fun actual(start: Instant, end: Instant) = ShiftActualAggregate(
        record = ShiftActualRecord(
            shiftId = shiftId,
            timelineId = timelineId,
            sector = WorkSector.PRIVATE_SECURITY,
            actualStart = start,
            actualEnd = end,
            differenceReason = "Ajuste operativo",
            explanation = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        ),
        extraIntervals = emptyList(),
    )
}
