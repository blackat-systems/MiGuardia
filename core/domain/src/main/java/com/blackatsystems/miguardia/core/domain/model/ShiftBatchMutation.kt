package com.blackatsystems.miguardia.core.domain.model

import java.util.UUID

data class ShiftBatchMutation(
    val shiftIdsToDelete: Set<UUID> = emptySet(),
    val shiftsToInsert: List<Shift> = emptyList(),
)
