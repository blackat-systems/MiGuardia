package com.blackatsystems.miguardia.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "objectives",
    indices = [
        Index(value = ["abbreviation"], unique = true),
    ],
)
internal data class ObjectiveEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val abbreviation: String,
    val address: String?,
    val note: String?,
    val isActive: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val weatherLatitude: Double? = null,
    val weatherLongitude: Double? = null,
)
