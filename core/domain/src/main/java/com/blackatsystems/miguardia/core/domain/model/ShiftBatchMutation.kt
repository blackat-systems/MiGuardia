package com.blackatsystems.miguardia.core.domain.model

import java.time.LocalDate
import java.util.UUID

data class ShiftBatchMutation(
    val shiftIdsToDelete: Set<UUID> = emptySet(),
    val shiftsToInsert: List<Shift> = emptyList(),
    val shiftsToUpdate: List<Shift> = emptyList(),
    val explicitDayStatusDatesToClear: Set<LocalDate> = emptySet(),
)
