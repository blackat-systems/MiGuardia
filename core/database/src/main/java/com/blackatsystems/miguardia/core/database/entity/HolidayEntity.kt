package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "holidays",
    indices = [Index(value = ["localDate"], unique = true)],
)
internal data class HolidayEntity(
    @PrimaryKey val id: String,
    val localDate: String,
    val name: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
