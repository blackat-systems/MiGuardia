package com.blackatsystems.miguardia.core.domain.model

import java.time.Instant
import java.util.UUID

enum class ShiftNoveltyType {
    ADDITIONAL_TIME,
    EARLY_DEPARTURE,
    ABSENCE,
    CANCELLATION,
    SECOND_SHIFT,
    OTHER,
}
data class ShiftNovelty(
    val id: UUID,
    val shiftId: UUID,
    val type: ShiftNoveltyType,
    val description: String?,
    val relatedShiftId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

sealed interface ShiftNoveltyMutation {
    data class SaveInformative(val novelty: ShiftNovelty) : ShiftNoveltyMutation

    /** A null novelty means returning the shift to PLANNED. */
    data class ChangeStatus(
        val updatedShift: Shift,
        val novelty: ShiftNovelty?,
    ) : ShiftNoveltyMutation

    data class ApplyFormalChange(
        val updatedShift: Shift,
        val change: FormalShiftChange,
    ) : ShiftNoveltyMutation

    data class RestoreOriginalPlan(
        val restoredShift: Shift,
        val expectedFinal: ShiftOperationalSnapshot,
    ) : ShiftNoveltyMutation

    data class CreateSecondShift(
        val novelty: ShiftNovelty,
        val secondShift: Shift,
    ) : ShiftNoveltyMutation

    data class DeleteInformative(val noveltyId: UUID) : ShiftNoveltyMutation

    data class DeleteSecondShift(
        val noveltyId: UUID,
        val secondShiftId: UUID,
    ) : ShiftNoveltyMutation
}
