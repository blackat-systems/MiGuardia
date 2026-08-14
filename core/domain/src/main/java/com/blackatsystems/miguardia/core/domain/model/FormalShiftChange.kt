package com.blackatsystems.miguardia.core.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class ShiftOperationalSnapshot(
    val startAt: Instant,
    val endAt: Instant,
    val zoneId: ZoneId,
    val localStartDate: LocalDate,
    val objectiveName: String,
    val objectiveAbbreviation: String,
    val objectiveAddress: String?,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val colorArgb: Int,
    val position: String?,
    val status: ShiftStatus,
    val sourceObjectiveId: UUID?,
    val sourceScheduleCombinationId: UUID?,
)
data class FormalShiftChange(
    val id: UUID,
    val shiftId: UUID,
    val scheduleChanged: Boolean,
    val objectiveChanged: Boolean,
    val description: String?,
    val original: ShiftOperationalSnapshot,
    val final: ShiftOperationalSnapshot,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Shift.toOperationalSnapshot(): ShiftOperationalSnapshot = ShiftOperationalSnapshot(
    startAt = startAt,
    endAt = endAt,
    zoneId = zoneId,
    localStartDate = localStartDate,
    objectiveName = objectiveNameSnapshot,
    objectiveAbbreviation = objectiveAbbreviationSnapshot,
    objectiveAddress = objectiveAddressSnapshot,
    startTime = startTimeSnapshot,
    endTime = endTimeSnapshot,
    colorArgb = colorArgbSnapshot,
    position = position,
    status = status,
    sourceObjectiveId = sourceObjectiveId,
    sourceScheduleCombinationId = sourceScheduleCombinationId,
)

fun Shift.withOperationalSnapshot(
    snapshot: ShiftOperationalSnapshot,
    updatedAt: Instant,
): Shift = copy(
    startAt = snapshot.startAt,
    endAt = snapshot.endAt,
    zoneId = snapshot.zoneId,
    localStartDate = snapshot.localStartDate,
    objectiveNameSnapshot = snapshot.objectiveName,
    objectiveAbbreviationSnapshot = snapshot.objectiveAbbreviation,
    objectiveAddressSnapshot = snapshot.objectiveAddress,
    startTimeSnapshot = snapshot.startTime,
    endTimeSnapshot = snapshot.endTime,
    colorArgbSnapshot = snapshot.colorArgb,
    position = snapshot.position,
    status = snapshot.status,
    sourceObjectiveId = snapshot.sourceObjectiveId,
    sourceScheduleCombinationId = snapshot.sourceScheduleCombinationId,
    updatedAt = updatedAt,
)
