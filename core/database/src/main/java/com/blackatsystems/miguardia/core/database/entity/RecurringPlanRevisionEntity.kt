package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recurring_plan_revisions",
    foreignKeys = [
        ForeignKey(
            entity = RecurringPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = WorkTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["planId", "revisionNumber"], unique = true),
        Index(value = ["id", "planId"], unique = true),
        Index(value = ["planId", "effectiveFrom", "revisionNumber"]),
        Index(value = ["templateId"]),
    ],
)
internal data class RecurringPlanRevisionEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val revisionNumber: Int,
    val effectiveFrom: String,
    val kind: String,
    val endDateInclusive: String,
    val patternKind: String,
    val weekdaysMask: Int?,
    val intervalCount: Int?,
    val monthlyOrdinal: String?,
    val monthlyDayOfWeek: Int?,
    val templateId: String,
    val workPlaceId: String,
    val objectiveId: String,
    val workTypeId: String,
    val objectiveNameSnapshot: String,
    val objectiveAbbreviationSnapshot: String,
    val objectiveAddressSnapshot: String?,
    val workTypeNameSnapshot: String,
    val workTypeBehaviorSnapshot: String,
    val startTimeSnapshot: String,
    val endTimeSnapshot: String,
    val colorArgbSnapshot: Int,
    val positionSnapshot: String?,
    val zoneId: String,
    val createdAtEpochMillis: Long,
)
