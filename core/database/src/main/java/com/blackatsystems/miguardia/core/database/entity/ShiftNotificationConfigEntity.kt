package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "shift_notification_configs",
    foreignKeys = [
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ShiftNotificationConfigEntity(
    @PrimaryKey val shiftId: String,
)
