package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.ExtraWorkClassWriteResult
import com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftActualSaveMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftActualWriteResult
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ShiftActualRepository {
    fun observeExpectation(shiftId: UUID): Flow<ShiftActualExpectation?>

    suspend fun getExpectation(shiftId: UUID): ShiftActualExpectation?

    fun observeExtraWorkClasses(
        timelineId: UUID,
        sector: WorkSector,
    ): Flow<List<ExtraWorkClass>>

    suspend fun save(mutation: ShiftActualSaveMutation): ShiftActualWriteResult

    suspend fun returnToPlanned(expectation: ShiftActualExpectation): ShiftActualWriteResult

    suspend fun saveExtraWorkClass(
        expected: ExtraWorkClass?,
        replacement: ExtraWorkClass,
    ): ExtraWorkClassWriteResult
}
