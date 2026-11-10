package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "shift_situations",
    primaryKeys = ["shiftId"],
    foreignKeys = [
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["status"]),
        Index(value = ["evidenceStorageKey"], unique = true),
    ],
)
internal data class ShiftSituationEntity(
    val shiftId: String,
    val status: String,
    val reason: String?,
    val privateNote: String?,
    val cancellationActor: String?,
    val notifiedAtEpochMillis: Long?,
    val evidenceStorageKey: String?,
    val evidenceMimeType: String?,
    val evidenceByteSize: Long?,
    val evidencePixelWidth: Int?,
    val evidencePixelHeight: Int?,
    val evidenceSha256: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
