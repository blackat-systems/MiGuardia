package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedule_photos",
    indices = [Index(value = ["month"]), Index(value = ["storageKey"], unique = true)],
)
internal data class SchedulePhotoEntity(
    @PrimaryKey val id: String,
    val month: String,
    val objectiveId: String?,
    val objectiveNameSnapshot: String?,
    val objectiveAbbreviationSnapshot: String?,
    val storageKey: String,
    val mimeType: String,
    val byteSize: Long,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
