package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "independent_extra_work_records",
    foreignKeys = [
        ForeignKey(
            entity = WorkConfigurationRootEntity::class,
            parentColumns = ["timelineId"],
            childColumns = ["timelineId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = WorkConfigurationRevisionEntity::class,
            parentColumns = ["id", "timelineId", "sector"],
            childColumns = ["configurationRevisionId", "timelineId", "sector"],
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
            entity = ObjectiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["objectiveId"],
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
        ForeignKey(
            entity = ExtraWorkClassEntity::class,
            parentColumns = ["id", "timelineId", "sector"],
            childColumns = ["extraWorkClassId", "timelineId", "sector"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["timelineId", "sector", "ownerLocalDate"]),
        Index(value = ["timelineId", "sector", "startEpochMillis", "endEpochMillis"]),
        Index(value = ["configurationRevisionId", "timelineId", "sector"]),
        Index(value = ["workPlaceId", "timelineId", "sector", "objectiveId"]),
        Index(value = ["objectiveId"]),
        Index(value = ["workTypeId", "timelineId", "sector"]),
        Index(value = ["templateId", "timelineId", "sector", "workPlaceId", "objectiveId", "workTypeId"]),
        Index(value = ["extraWorkClassId", "timelineId", "sector"]),
    ],
)
internal data class IndependentExtraWorkRecordEntity(
    @PrimaryKey val id: String,
    val timelineId: String,
    val sector: String,
    val configurationRevisionId: String,
    val workPlaceId: String,
    val objectiveId: String,
    val workTypeId: String,
    val templateId: String?,
    val extraWorkClassId: String,
    val ownerLocalDate: String,
    val zoneId: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val workPlaceNameSnapshot: String,
    val workPlaceAbbreviationSnapshot: String,
    val workPlaceAddressSnapshot: String?,
    val workTypeNameSnapshot: String,
    val workTypeBehaviorSnapshot: String,
    val colorArgbSnapshot: Int,
    val positionSnapshot: String?,
    val classNameSnapshot: String,
    val helpsMeetHoursReferenceSnapshot: Boolean,
    val showDedicatedSummarySnapshot: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
