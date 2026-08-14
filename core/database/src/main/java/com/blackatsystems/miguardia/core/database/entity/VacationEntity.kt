package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vacations",
    indices = [
        Index(value = ["startDate"]),
        Index(value = ["endDateInclusive"]),
    ],
)
internal data class VacationEntity(
    @PrimaryKey val id: String,
    val startDate: String,
    val endDateInclusive: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
