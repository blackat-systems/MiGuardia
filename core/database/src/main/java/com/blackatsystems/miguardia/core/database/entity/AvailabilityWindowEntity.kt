package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "availability_windows",
    foreignKeys = [
        ForeignKey(
            entity = WorkConfigurationRootEntity::class,
            parentColumns = ["timelineId"],
            childColumns = ["timelineId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = WorkConfigurationRevisionEntity::class,
            parentColumns = ["id", "timelineId", "sector"],
            childColumns = ["configurationRevisionId", "timelineId", "sector"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["timelineId", "sector", "ownerLocalDate"]),
        Index(value = ["timelineId", "sector", "startEpochMillis", "endEpochMillis"]),
        Index(value = ["configurationRevisionId", "timelineId", "sector"]),
    ],
)
internal data class AvailabilityWindowEntity(
    @PrimaryKey val id: String,
    val timelineId: String,
    val sector: String,
    val configurationRevisionId: String,
    val ownerLocalDate: String,
    val zoneId: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val labelSnapshot: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
