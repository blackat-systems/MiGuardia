package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ShiftNoteRepository {
    fun observeForShift(shiftId: UUID): Flow<List<ShiftNote>>
    suspend fun getById(id: UUID): ShiftNote?
    suspend fun insert(note: ShiftNote)
    suspend fun update(note: ShiftNote)
    suspend fun delete(id: UUID)
}
