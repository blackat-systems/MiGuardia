package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.blackatsystems.miguardia.core.database.entity.ShiftNoteAttachmentEntity

@Dao
internal interface ShiftNoteAttachmentDao {
    @Query("SELECT * FROM shift_note_attachments ORDER BY noteId, position, id")
    suspend fun getAll(): List<ShiftNoteAttachmentEntity>

    @Query("SELECT * FROM shift_note_attachments WHERE noteId = :noteId ORDER BY position, id")
    suspend fun getForNote(noteId: String): List<ShiftNoteAttachmentEntity>

    @Query("SELECT * FROM shift_note_attachments WHERE noteId IN (:noteIds) ORDER BY noteId, position, id")
    suspend fun getForNotes(noteIds: List<String>): List<ShiftNoteAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<ShiftNoteAttachmentEntity>)

    @Query("DELETE FROM shift_note_attachments WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)
}
