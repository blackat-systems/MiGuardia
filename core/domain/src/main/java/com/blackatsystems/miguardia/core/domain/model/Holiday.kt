package com.blackatsystems.miguardia.core.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Holiday(
    val id: UUID,
    val date: LocalDate,
    val name: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
enum class HolidayConflictPolicy {
    REPLACE,
    KEEP_EXISTING,
}

data class HolidayBatchMutation(
    val holidayIdsToDelete: Set<UUID> = emptySet(),
    val holidaysToSave: List<Holiday> = emptyList(),
    val conflictPolicy: HolidayConflictPolicy = HolidayConflictPolicy.KEEP_EXISTING,
)
