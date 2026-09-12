package com.blackatsystems.miguardia.core.domain.nextevent

import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal val TestZone: ZoneId = ZoneId.of("America/Argentina/Cordoba")
internal val TestTimelineId: UUID = UUID.fromString("10000000-0000-0000-0000-000000000001")

internal fun testUuid(value: Int): UUID = UUID.fromString(
    "00000000-0000-0000-0000-${value.toString().padStart(12, '0')}",
)

internal fun testWrite(
    id: Int,
    start: String,
    end: String,
    sector: WorkSector = WorkSector.PRIVATE_SECURITY,
    status: ShiftStatus = ShiftStatus.PLANNED,
    workType: String = "Jornada habitual",
    place: String = "Lugar ficticio",
    abbreviation: String = "FIC",
    address: String? = "Calle ficticia 123",
    position: String? = "Acceso",
): V2ShiftWrite {
    val startAt = Instant.parse(start)
    val endAt = Instant.parse(end)
    val shiftId = testUuid(id)
    val localStart = startAt.atZone(TestZone)
    return V2ShiftWrite(
        shift = Shift(
            id = shiftId,
            startAt = startAt,
            endAt = endAt,
            zoneId = TestZone,
            localStartDate = localStart.toLocalDate(),
            objectiveNameSnapshot = place,
            objectiveAbbreviationSnapshot = abbreviation,
            objectiveAddressSnapshot = address,
            startTimeSnapshot = localStart.toLocalTime(),
            endTimeSnapshot = endAt.atZone(TestZone).toLocalTime(),
            colorArgbSnapshot = 0xFF315DA8.toInt(),
            position = position,
            status = status,
            sourceObjectiveId = testUuid(9_000 + id),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        ),
        snapshot = ShiftWorkSnapshot(
            shiftId = shiftId,
            timelineId = TestTimelineId,
            sector = sector,
            configurationRevisionId = testUuid(8_000 + id),
            workPlaceId = testUuid(7_000 + id),
            objectiveId = testUuid(9_000 + id),
            templateId = testUuid(6_000 + id),
            workTypeId = testUuid(5_000 + id),
            workTypeNameSnapshot = workType,
            workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
        ),
    )
}

internal fun testAvailability(
    id: Int,
    start: String,
    end: String,
    label: String = "Guardia pasiva",
    sector: WorkSector = WorkSector.PRIVATE_SECURITY,
): AvailabilityWindowRecord {
    val startAt = Instant.parse(start)
    return AvailabilityWindowRecord(
        id = testUuid(id),
        timelineId = TestTimelineId,
        sector = sector,
        configurationRevisionId = testUuid(4_000 + id),
        ownerLocalDate = startAt.atZone(TestZone).toLocalDate(),
        zoneId = TestZone,
        start = startAt,
        end = Instant.parse(end),
        labelSnapshot = label,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}

internal fun testActual(
    write: V2ShiftWrite,
    start: String = write.shift.startAt.toString(),
    end: String = write.shift.endAt.toString(),
): ShiftActualAggregate = ShiftActualAggregate(
    record = ShiftActualRecord(
        shiftId = write.shift.id,
        timelineId = write.snapshot.timelineId,
        sector = write.snapshot.sector,
        actualStart = Instant.parse(start),
        actualEnd = Instant.parse(end),
        differenceReason = "Horario real ficticio",
        explanation = "Explicacion privada ficticia",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    ),
    extraIntervals = emptyList(),
)

internal fun testExtra(
    id: Int,
    start: String,
    end: String,
): IndependentExtraWorkRecord {
    val startAt = Instant.parse(start)
    return IndependentExtraWorkRecord(
        id = testUuid(id),
        timelineId = TestTimelineId,
        sector = WorkSector.PRIVATE_SECURITY,
        configurationRevisionId = testUuid(3_000 + id),
        workPlaceId = testUuid(2_000 + id),
        objectiveId = testUuid(1_000 + id),
        workTypeId = testUuid(10_000 + id),
        templateId = null,
        extraWorkClassId = testUuid(11_000 + id),
        ownerLocalDate = startAt.atZone(TestZone).toLocalDate(),
        zoneId = TestZone,
        start = startAt,
        end = Instant.parse(end),
        snapshot = IndependentExtraWorkSnapshot(
            workPlaceName = "Lugar extra ficticio",
            workPlaceAbbreviation = "EXT",
            workPlaceAddress = null,
            workTypeName = "Extra",
            workTypeBehavior = WorkTypeBehavior.ACTIVE_WORK,
            colorArgb = 0xFF665E70.toInt(),
            position = null,
            className = "Clase ficticia",
            helpsMeetHoursReference = true,
            showDedicatedSummary = true,
        ),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}

internal fun testInput(
    shifts: List<V2ShiftWrite> = emptyList(),
    availability: List<AvailabilityWindowRecord> = emptyList(),
    actuals: Map<UUID, ShiftActualAggregate> = emptyMap(),
    extras: List<IndependentExtraWorkRecord> = emptyList(),
    statuses: List<ExplicitDayStatus> = emptyList(),
    vacations: List<Vacation> = emptyList(),
    medicalLeaves: List<MedicalLeave> = emptyList(),
): NextEventInput = NextEventInput(
    shifts = shifts,
    availabilityWindows = availability,
    actualsByShiftId = actuals,
    independentExtras = extras,
    explicitDayStatuses = statuses,
    vacations = vacations,
    medicalLeaves = medicalLeaves,
)

internal fun testVacation(id: Int, start: String, end: String): Vacation = Vacation(
    id = testUuid(id),
    startDate = java.time.LocalDate.parse(start),
    endDateInclusive = java.time.LocalDate.parse(end),
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

internal fun testMedicalLeave(id: Int, start: String, end: String): MedicalLeave = MedicalLeave(
    id = testUuid(id),
    startDate = java.time.LocalDate.parse(start),
    endDateInclusive = java.time.LocalDate.parse(end),
    privateNote = "Nota medica privada ficticia",
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
