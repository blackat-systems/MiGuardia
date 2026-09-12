package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medical_leaves",
    indices = [
        Index(value = ["startDate"]),
        Index(value = ["endDateInclusive"]),
    ],
)
internal data class MedicalLeaveEntity(
    @PrimaryKey val id: String,
    val startDate: String,
    val endDateInclusive: String,
    val privateNote: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
