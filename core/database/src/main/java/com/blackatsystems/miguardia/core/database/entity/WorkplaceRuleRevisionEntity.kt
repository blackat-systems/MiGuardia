package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workplace_rule_revisions",
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
    ],
    indices = [
        Index(value = ["workPlaceId", "effectiveFrom"], unique = true),
        Index(value = ["timelineId"]),
        Index(value = ["workPlaceId", "timelineId", "sector", "objectiveId"]),
    ],
)
internal data class WorkplaceRuleRevisionEntity(
    @PrimaryKey val id: String,
    val timelineId: String,
    val sector: String,
    val workPlaceId: String,
    val objectiveId: String,
    val effectiveFrom: String,
    val nightRuleCode: String,
    val nightStartTime: String?,
    val nightEndTime: String?,
    val nightDifferentTreatment: Boolean?,
    val nightShowDedicatedSummary: Boolean?,
    val weekendRuleCode: String,
    val weekendDifferentTreatment: Boolean?,
    val weekendShowDedicatedSummary: Boolean?,
    val holidayDifferentTreatment: Boolean,
    val holidayShowDedicatedSummary: Boolean,
    val createdAtEpochMillis: Long,
)
