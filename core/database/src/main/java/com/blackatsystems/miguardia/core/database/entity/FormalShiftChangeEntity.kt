package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

internal data class ShiftOperationalSnapshotEntity(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val zoneId: String,
    val localStartDate: String,
    val objectiveName: String,
    val objectiveAbbreviation: String,
    val objectiveAddress: String?,
    val startTime: String,
    val endTime: String,
    val colorArgb: Int,
    val position: String?,
    val status: String,
    val sourceObjectiveId: String?,
    val sourceScheduleCombinationId: String?,
)
@Entity(
    tableName = "formal_shift_changes",
    foreignKeys = [
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["shiftId"], unique = true)],
)
internal data class FormalShiftChangeEntity(
    @PrimaryKey val id: String,
    val shiftId: String,
    val scheduleChanged: Boolean,
    val objectiveChanged: Boolean,
    val description: String?,
    @Embedded(prefix = "original_") val original: ShiftOperationalSnapshotEntity,
    @Embedded(prefix = "final_") val final: ShiftOperationalSnapshotEntity,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
