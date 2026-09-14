package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteWithAttachments
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ShiftNoteRepository {
    fun observeForShift(shiftId: UUID): Flow<List<ShiftNote>>
    suspend fun getById(id: UUID): ShiftNote?
    suspend fun insert(note: ShiftNote)
    suspend fun update(note: ShiftNote)
    suspend fun delete(id: UUID)
    fun observeWithAttachmentsForShift(shiftId: UUID): Flow<List<ShiftNoteWithAttachments>> =
        observeForShift(shiftId).map { notes -> notes.map { ShiftNoteWithAttachments(it, emptyList()) } }
    suspend fun getWithAttachments(id: UUID): ShiftNoteWithAttachments? =
        getById(id)?.let { ShiftNoteWithAttachments(it, emptyList()) }
    suspend fun saveWithAttachments(note: ShiftNote, attachments: List<ShiftNoteAttachment>) {
        require(attachments.isEmpty()) { "Este repositorio no admite adjuntos." }
        if (getById(note.id) == null) insert(note) else update(note)
    }
    suspend fun allAttachmentStorageKeys(): Set<String> = emptySet()
}
