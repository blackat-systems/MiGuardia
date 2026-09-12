package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "shift_notification_reminders",
    primaryKeys = ["shiftId", "leadMinutes"],
    foreignKeys = [
        ForeignKey(
            entity = ShiftNotificationConfigEntity::class,
            parentColumns = ["shiftId"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ShiftNotificationReminderEntity(
    val shiftId: String,
    val leadMinutes: Long,
)
