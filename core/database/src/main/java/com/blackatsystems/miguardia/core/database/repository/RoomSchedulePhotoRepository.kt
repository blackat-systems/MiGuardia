package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import com.blackatsystems.miguardia.core.database.dao.SchedulePhotoDao
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.core.domain.photo.validated
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.SchedulePhotoRepository
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomSchedulePhotoRepository(private val dao: SchedulePhotoDao) : SchedulePhotoRepository {
    override fun observeForMonth(month: YearMonth): Flow<List<SchedulePhoto>> =
        dao.observeForMonth(month.toString()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: UUID): SchedulePhoto? = dao.getById(id.toString())?.toDomain()

    override suspend fun insert(photo: SchedulePhoto) = translateConflict {
        dao.insert(photo.validated().toEntity())
    }

    override suspend fun update(photo: SchedulePhoto) = translateConflict {
        if (dao.update(photo.validated().toEntity()) == 0) {
            throw InvalidLocalDataException("No existe la foto solicitada.")
        }
    }

    override suspend fun delete(id: UUID) { dao.delete(id.toString()) }

    suspend fun allStorageKeys(): Set<String> = dao.getAllStorageKeys().toSet()

    private suspend fun <T> translateConflict(block: suspend () -> T): T = try {
        block()
    } catch (error: SQLiteConstraintException) {
        throw ConflictingLocalWriteException("No se pudo guardar la foto del cronograma.")
    }
}
