package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shifts",
    indices = [
        Index(value = ["localStartDate"]),
    ],
)
internal data class ShiftEntity(
    @PrimaryKey val id: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val zoneId: String,
    val localStartDate: String,
    val objectiveNameSnapshot: String,
    val objectiveAbbreviationSnapshot: String,
    val objectiveAddressSnapshot: String?,
    val startTimeSnapshot: String,
    val endTimeSnapshot: String,
    val colorArgbSnapshot: Int,
    val position: String?,
    val status: String,
    val sourceObjectiveId: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
