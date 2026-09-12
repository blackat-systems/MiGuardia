package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface MedicalLeaveRepository {
    fun observeIntersecting(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<MedicalLeave>>

    suspend fun create(medicalLeave: MedicalLeave)
    suspend fun update(medicalLeave: MedicalLeave)
    suspend fun delete(id: UUID)
}
