package com.blackatsystems.miguardia.core.domain.model

import java.time.Instant
import java.util.UUID

data class ShiftNote(
    val id: UUID,
    val shiftId: UUID,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
