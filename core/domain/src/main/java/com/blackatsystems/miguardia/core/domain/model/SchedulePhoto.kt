package com.blackatsystems.miguardia.core.domain.model

import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class SchedulePhoto(
    val id: UUID,
    val month: YearMonth,
    val objectiveId: UUID?,
    val objectiveNameSnapshot: String?,
    val objectiveAbbreviationSnapshot: String?,
    val storageKey: String,
    val mimeType: String,
    val byteSize: Long,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
