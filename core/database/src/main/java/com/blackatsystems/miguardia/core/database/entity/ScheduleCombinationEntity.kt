package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedule_combinations",
    foreignKeys = [
        ForeignKey(
            entity = ObjectiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["objectiveId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["objectiveId"]),
        Index(value = ["objectiveId", "startTime", "endTime"], unique = true),
    ],
)
internal data class ScheduleCombinationEntity(
    @PrimaryKey val id: String,
    val objectiveId: String,
    val startTime: String,
    val endTime: String,
    val colorArgb: Int,
    val isActive: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
