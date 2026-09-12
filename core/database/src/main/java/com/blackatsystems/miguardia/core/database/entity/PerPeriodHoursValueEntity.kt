package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "per_period_hours_values",
    foreignKeys = [
        ForeignKey(
            entity = PerPeriodHoursDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["definitionId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["definitionId", "windowStartInclusive"], unique = true)],
)
internal data class PerPeriodHoursValueEntity(
    @PrimaryKey val id: String,
    val definitionId: String,
    val windowStartInclusive: String,
    val windowEndExclusive: String,
    val requiredMinutes: Long,
)
