package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shift_work_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = WorkConfigurationRootEntity::class,
            parentColumns = ["timelineId"],
            childColumns = ["timelineId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = WorkConfigurationRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["configurationRevisionId"],
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
            entity = WorkTemplateEntity::class,
            parentColumns = ["id", "timelineId", "sector", "workPlaceId", "objectiveId", "workTypeId"],
            childColumns = ["templateId", "timelineId", "sector", "workPlaceId", "objectiveId", "workTypeId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["shiftId", "timelineId", "sector"], unique = true),
        Index(value = ["timelineId"]),
        Index(value = ["configurationRevisionId"]),
        Index(value = ["workPlaceId", "timelineId", "sector", "objectiveId"]),
        Index(value = ["workTypeId", "timelineId", "sector"]),
        Index(value = ["templateId", "timelineId", "sector", "workPlaceId", "objectiveId", "workTypeId"]),
    ],
)
internal data class ShiftWorkSnapshotEntity(
    @PrimaryKey val shiftId: String,
    val timelineId: String,
    val sector: String,
    val configurationRevisionId: String,
    val workPlaceId: String,
    val objectiveId: String,
    val templateId: String,
    val workTypeId: String,
    val workTypeNameSnapshot: String,
    val workTypeBehaviorSnapshot: String,
)
