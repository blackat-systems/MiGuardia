package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "work_configuration_revisions",
    foreignKeys = [
        ForeignKey(
            entity = WorkConfigurationRootEntity::class,
            parentColumns = ["timelineId"],
            childColumns = ["timelineId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = PerPeriodHoursDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["perPeriodDefinitionId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["timelineId", "effectiveFrom"], unique = true),
        Index(value = ["id", "timelineId", "sector"], unique = true),
        Index(value = ["perPeriodDefinitionId"]),
    ],
)
internal data class WorkConfigurationRevisionEntity(
    @PrimaryKey val id: String,
    val timelineId: String,
    val effectiveFrom: String,
    val sector: String,
    val availabilityLabel: String?,
    val hoursReferenceKind: String,
    val periodKind: String?,
    val weeklyFirstDayIso: Int?,
    val cycleAnchorDate: String?,
    val cycleLengthDays: Int?,
    val requiredMinutes: Long?,
    val perPeriodDefinitionId: String?,
    val hoursReferenceStartedOn: String? = null,
)
