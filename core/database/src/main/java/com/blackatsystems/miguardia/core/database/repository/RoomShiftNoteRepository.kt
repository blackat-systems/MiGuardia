package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.dao.ShiftNoteDao
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteWithAttachments
import com.blackatsystems.miguardia.core.domain.model.normalized
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoteRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomShiftNoteRepository(private val database: MiGuardiaV2Database) : ShiftNoteRepository {
    private val dao: ShiftNoteDao = database.shiftNoteDao()
    private val attachmentDao = database.shiftNoteAttachmentDao()
    override fun observeForShift(shiftId: UUID): Flow<List<ShiftNote>> =
        dao.observeForShift(shiftId.toString()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: UUID): ShiftNote? = dao.getById(id.toString())?.toDomain()

    override suspend fun insert(note: ShiftNote) = constrained { dao.insert(note.normalized().toEntity()) }

    override suspend fun update(note: ShiftNote) = constrained {
        val normalized = note.normalized()
        val existing = dao.getById(normalized.id.toString())
            ?: throw InvalidLocalDataException("No existe la nota indicada.")
        if (existing.shiftId != normalized.shiftId.toString()) {
            throw InvalidLocalDataException("La nota no pertenece a la guardia indicada.")
        }
        if (dao.update(normalized.toEntity()) == 0) {
            throw InvalidLocalDataException("No existe la nota indicada.")
        }
    }

    override suspend fun delete(id: UUID) {
        dao.delete(id.toString())
    }

    override fun observeWithAttachmentsForShift(shiftId: UUID): Flow<List<ShiftNoteWithAttachments>> =
        dao.observeForShift(shiftId.toString()).map { rows ->
            val attachments = if (rows.isEmpty()) {
                emptyList()
            } else {
                attachmentDao.getForNotes(rows.map { it.id }).map { it.toDomain() }
            }.groupBy(ShiftNoteAttachment::noteId)
            rows.map { row ->
                val note = row.toDomain()
                ShiftNoteWithAttachments(note, attachments[note.id].orEmpty())
            }
        }

    override suspend fun getWithAttachments(id: UUID): ShiftNoteWithAttachments? = database.withTransaction {
        val note = dao.getById(id.toString())?.toDomain() ?: return@withTransaction null
        ShiftNoteWithAttachments(
            note = note,
            attachments = attachmentDao.getForNote(id.toString()).map { it.toDomain() },
        )
    }

    override suspend fun saveWithAttachments(
        note: ShiftNote,
        attachments: List<ShiftNoteAttachment>,
    ) = constrained {
        val normalized = if (attachments.isEmpty()) {
            note.normalized()
        } else {
            if (note.updatedAt < note.createdAt) {
                throw InvalidLocalDataException("La modificación no puede ser anterior a la creación.")
            }
            note.copy(body = note.body.trim())
        }
        require(attachments.size <= 30) { "Una nota admite hasta 30 imágenes." }
        require(attachments.map { it.id }.distinct().size == attachments.size)
        require(attachments.map { it.storageKey }.distinct().size == attachments.size)
        attachments.forEachIndexed { index, attachment ->
            require(attachment.noteId == normalized.id && attachment.position == index)
            require(attachment.byteSize > 0 && attachment.pixelWidth > 0 && attachment.pixelHeight > 0)
            require(attachment.mimeType in setOf("image/jpeg", "image/png", "image/webp"))
        }
        database.withTransaction {
            val existing = dao.getById(normalized.id.toString())
            if (existing == null) {
                dao.insert(normalized.toEntity())
            } else {
                if (existing.shiftId != normalized.shiftId.toString()) {
                    throw InvalidLocalDataException("La nota no pertenece a la guardia indicada.")
                }
                if (dao.update(normalized.toEntity()) == 0) {
                    throw InvalidLocalDataException("No existe la nota indicada.")
                }
                attachmentDao.deleteForNote(normalized.id.toString())
            }
            attachmentDao.insertAll(attachments.map { it.toEntity() })
        }
    }

    override suspend fun allAttachmentStorageKeys(): Set<String> =
        attachmentDao.getAll().mapTo(linkedSetOf()) { it.storageKey }

    private suspend fun <T> constrained(block: suspend () -> T): T = try {
        block()
    } catch (error: SQLiteConstraintException) {
        throw InvalidLocalDataException("No se pudo guardar la nota de guardia.", error)
    }
}
