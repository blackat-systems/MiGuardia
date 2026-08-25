package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shift_actual_records",
    foreignKeys = [
        ForeignKey(
            entity = ShiftWorkSnapshotEntity::class,
            parentColumns = ["shiftId", "timelineId", "sector"],
            childColumns = ["shiftId", "timelineId", "sector"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["shiftId", "timelineId", "sector"], unique = true),
    ],
)
internal data class ShiftActualRecordEntity(
    @PrimaryKey val shiftId: String,
    val timelineId: String,
    val sector: String,
    val actualStartEpochMillis: Long,
    val actualEndEpochMillis: Long,
    val differenceReason: String,
    val explanation: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
