package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValueMutation
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValueWriteResult
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationReferenceMutation
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationReferenceWriteResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface WorkConfigurationRepository {
    fun observe(): Flow<WorkConfigurationHistory?>

    suspend fun get(): WorkConfigurationHistory?

    suspend fun createInitial(
        timelineId: UUID,
        firstRevision: EffectiveRevision<WorkConfiguration>,
    )

    suspend fun addRevision(
        timelineId: UUID,
        revision: EffectiveRevision<WorkConfiguration>,
    )

    suspend fun applyReferenceMutation(
        mutation: WorkConfigurationReferenceMutation,
    ): WorkConfigurationReferenceWriteResult = throw UnsupportedOperationException(
        "Este repositorio todavía no implementa la mutación atómica de referencia",
    )

    suspend fun applyPerPeriodHoursValueMutation(
        mutation: PerPeriodHoursValueMutation,
    ): PerPeriodHoursValueWriteResult = throw UnsupportedOperationException(
        "Este repositorio todavía no implementa la mutación atómica del valor por período",
    )
}
