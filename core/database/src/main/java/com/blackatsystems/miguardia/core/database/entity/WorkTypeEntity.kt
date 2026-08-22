package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "work_types",
    foreignKeys = [
        ForeignKey(
            entity = WorkConfigurationRootEntity::class,
            parentColumns = ["timelineId"],
            childColumns = ["timelineId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["timelineId", "sector", "normalizedNameKey"], unique = true),
        Index(value = ["id", "timelineId", "sector"], unique = true),
    ],
)
internal data class WorkTypeEntity(
    @PrimaryKey val id: String,
    val timelineId: String,
    val sector: String,
    val name: String,
    val normalizedNameKey: String,
    val behavior: String,
    val isActive: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
