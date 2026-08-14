package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shift_novelties",
    foreignKeys = [
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["relatedShiftId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("shiftId"), Index("relatedShiftId")],
)
internal data class ShiftNoveltyEntity(
    @PrimaryKey val id: String,
    val shiftId: String,
    val type: String,
    val description: String?,
    val relatedShiftId: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
