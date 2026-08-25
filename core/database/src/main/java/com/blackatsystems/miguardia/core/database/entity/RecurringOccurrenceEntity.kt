package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "recurring_occurrences",
    primaryKeys = ["planId", "localDate"],
    foreignKeys = [
        ForeignKey(
            entity = RecurringPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = RecurringPlanRevisionEntity::class,
            parentColumns = ["id", "planId"],
            childColumns = ["revisionId", "planId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["revisionId", "planId"]),
        Index(value = ["shiftId"], unique = true),
    ],
)
internal data class RecurringOccurrenceEntity(
    val planId: String,
    val localDate: String,
    val revisionId: String,
    val shiftId: String?,
    val state: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
