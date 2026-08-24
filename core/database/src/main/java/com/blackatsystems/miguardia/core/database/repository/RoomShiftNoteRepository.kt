package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import com.blackatsystems.miguardia.core.database.dao.ShiftNoteDao
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.normalized
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoteRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomShiftNoteRepository(private val dao: ShiftNoteDao) : ShiftNoteRepository {
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

    private suspend fun <T> constrained(block: suspend () -> T): T = try {
        block()
    } catch (error: SQLiteConstraintException) {
        throw InvalidLocalDataException("No se pudo guardar la nota de guardia.", error)
    }
}
