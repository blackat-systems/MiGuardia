package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shift_extra_intervals",
    foreignKeys = [
        ForeignKey(
            entity = ShiftActualRecordEntity::class,
            parentColumns = ["shiftId", "timelineId", "sector"],
            childColumns = ["shiftId", "timelineId", "sector"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = ExtraWorkClassEntity::class,
            parentColumns = ["id", "timelineId", "sector"],
            childColumns = ["extraWorkClassId", "timelineId", "sector"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["shiftId", "timelineId", "sector"]),
        Index(value = ["extraWorkClassId", "timelineId", "sector"]),
        Index(value = ["shiftId", "startEpochMillis", "endEpochMillis"], unique = true),
    ],
)
internal data class ShiftExtraIntervalEntity(
    @PrimaryKey val id: String,
    val shiftId: String,
    val timelineId: String,
    val sector: String,
    val extraWorkClassId: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val classNameSnapshot: String,
    val helpsMeetHoursReferenceSnapshot: Boolean,
    val showDedicatedSummarySnapshot: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
