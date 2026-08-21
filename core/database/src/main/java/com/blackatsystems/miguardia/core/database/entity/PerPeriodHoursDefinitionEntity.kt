package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "per_period_hours_definitions",
    foreignKeys = [
        ForeignKey(
            entity = WorkConfigurationRootEntity::class,
            parentColumns = ["timelineId"],
            childColumns = ["timelineId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["timelineId"])],
)
internal data class PerPeriodHoursDefinitionEntity(
    @PrimaryKey val id: String,
    val timelineId: String,
    val periodKind: String,
    val weeklyFirstDayIso: Int?,
    val cycleAnchorDate: String?,
    val cycleLengthDays: Int?,
)
