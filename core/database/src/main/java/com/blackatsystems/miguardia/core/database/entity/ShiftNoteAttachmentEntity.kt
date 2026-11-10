package com.blackatsystems.miguardia.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shift_note_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ShiftNoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("noteId"),
        Index(value = ["noteId", "position"], unique = true),
        Index(value = ["storageKey"], unique = true),
    ],
)
internal data class ShiftNoteAttachmentEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val storageKey: String,
    val mimeType: String,
    val byteSize: Long,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val position: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
