package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "work_templates",
    foreignKeys = [
        ForeignKey(
            entity = WorkConfigurationRootEntity::class,
            parentColumns = ["timelineId"],
            childColumns = ["timelineId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = WorkPlaceEntity::class,
            parentColumns = ["id", "timelineId", "sector", "objectiveId"],
            childColumns = ["workPlaceId", "timelineId", "sector", "objectiveId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = WorkTypeEntity::class,
            parentColumns = ["id", "timelineId", "sector"],
            childColumns = ["workTypeId", "timelineId", "sector"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = ScheduleCombinationEntity::class,
            parentColumns = ["id"],
            childColumns = ["legacyScheduleCombinationId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["timelineId"]),
        Index(value = ["workPlaceId", "timelineId", "sector", "objectiveId"]),
        Index(value = ["workTypeId", "timelineId", "sector"]),
        Index(value = ["legacyScheduleCombinationId"]),
        Index(value = ["workPlaceId", "workTypeId", "startTime", "endTime"], unique = true),
        Index(
            value = ["id", "timelineId", "sector", "workPlaceId", "objectiveId", "workTypeId"],
            unique = true,
        ),
    ],
)
internal data class WorkTemplateEntity(
    @PrimaryKey val id: String,
    val timelineId: String,
    val sector: String,
    val workPlaceId: String,
    val objectiveId: String,
    val workTypeId: String,
    val startTime: String,
    val endTime: String,
    val colorArgb: Int,
    val isActive: Boolean,
    val legacyScheduleCombinationId: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
