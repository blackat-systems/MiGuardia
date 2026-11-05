package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowDraft
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.buildAvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.shift.buildV2ShiftWrite
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/** Inputs shared by the local component tests and the isolated Android fixture; no received store. */
internal class Stage12TemporalRecipe(val namespace: UUID, val timestamp: Instant) {
    fun id(label: String): UUID = UUID.nameUUIDFromBytes("$namespace:temporal:$label".toByteArray(Charsets.UTF_8))
    val timelineId = id("timeline")
    val revisionId = id("revision")
    val objectiveId = id("objective")
    val placeId = id("place")
    val typeId = id("type")
    val templateId = id("template")
    val history = WorkConfigurationHistory(
        EffectiveDateTimeline(timelineId, listOf(EffectiveRevision(revisionId, LocalDate.of(2026, 1, 1),
            WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, AvailabilityLabel.PASSIVE_GUARD)))),
        PerPeriodHoursValues(emptyList()),
    )
    val objective = Objective(objectiveId, "Hospital Temporal", "HTP", "Calle de prueba 120", null, true, timestamp, timestamp)
    val place = WorkPlace(placeId, timelineId, WorkSector.NURSING, objectiveId, true, timestamp, timestamp)
    val type = WorkType.create(typeId, timelineId, WorkSector.NURSING, "Enfermería temporal", timestamp)
    val color = 0xFF336699.toInt()
    val position = "Puesto temporal 7"

    fun configuration(date: LocalDate) = ResolvedWorkConfigurationRevision.resolve(history, date)
    fun template(start: LocalTime, end: LocalTime) = WorkTemplate(templateId, timelineId, WorkSector.NURSING,
        placeId, objectiveId, typeId, start, end, color, true, timestamp, timestamp)

    /** Actual production builder. It does not construct its own expected result. */
    fun buildShift(literal: Stage12TemporalLiteralOracle.Literal, shiftId: UUID = id(literal.key)): V2ShiftWrite =
        buildV2ShiftWrite(shiftId, literal.ownerDate, objective, place, type,
            template(LocalDateTime.parse(literal.localStart).toLocalTime(), LocalDateTime.parse(literal.localEnd).toLocalTime()),
            configuration(literal.ownerDate), position, timestamp, Stage12TemporalLiteralOracle.zone)

    /** All 16 + 10 fields, before the actual write and independent of the builder/readback. */
    fun expectedShift(literal: Stage12TemporalLiteralOracle.Literal, shiftId: UUID = id(literal.key)): V2ShiftWrite =
        V2ShiftWrite(
            Shift(shiftId, literal.start, literal.end, Stage12TemporalLiteralOracle.zone, literal.ownerDate,
                "Hospital Temporal", "HTP", "Calle de prueba 120", LocalDateTime.parse(literal.localStart).toLocalTime(),
                LocalDateTime.parse(literal.localEnd).toLocalTime(), 0xFF336699.toInt(), "Puesto temporal 7", ShiftStatus.PLANNED,
                objectiveId, timestamp, timestamp),
            ShiftWorkSnapshot(shiftId, timelineId, WorkSector.NURSING, revisionId, placeId, objectiveId, templateId,
                typeId, "Enfermería temporal", WorkTypeBehavior.ACTIVE_WORK),
        )

    fun buildWindow(literal: Stage12TemporalLiteralOracle.Literal, windowId: UUID = id(literal.key)): AvailabilityWindowRecord =
        buildAvailabilityWindowRecord(AvailabilityWindowDraft(windowId, literal.ownerDate, Stage12TemporalLiteralOracle.zone,
            literal.start, literal.end), configuration(literal.ownerDate), timestamp)

    /** All 11 persisted fields, independent of the builder/readback. */
    fun expectedWindow(literal: Stage12TemporalLiteralOracle.Literal, windowId: UUID = id(literal.key)) =
        AvailabilityWindowRecord(windowId, timelineId, WorkSector.NURSING, revisionId, literal.ownerDate,
            Stage12TemporalLiteralOracle.zone, literal.start, literal.end, "Guardia pasiva", timestamp, timestamp)
}
