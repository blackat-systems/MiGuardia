package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recurring_plans",
    foreignKeys = [
        ForeignKey(
            entity = WorkConfigurationRootEntity::class,
            parentColumns = ["timelineId"],
            childColumns = ["timelineId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["timelineId", "sector"])],
)
internal data class RecurringPlanEntity(
    @PrimaryKey val id: String,
    val timelineId: String,
    val sector: String,
    val createdAtEpochMillis: Long,
)
