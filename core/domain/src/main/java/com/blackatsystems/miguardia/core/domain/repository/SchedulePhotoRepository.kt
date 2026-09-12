package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface SchedulePhotoRepository {
    fun observeForMonth(month: YearMonth): Flow<List<SchedulePhoto>>
    suspend fun getById(id: UUID): SchedulePhoto?
    suspend fun insert(photo: SchedulePhoto)
    suspend fun update(photo: SchedulePhoto)
    suspend fun delete(id: UUID)
}
