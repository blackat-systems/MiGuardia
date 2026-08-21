package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursEntry
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
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

    suspend fun createPerPeriodValue(
        timelineId: UUID,
        entry: PerPeriodHoursEntry,
    )

    suspend fun updatePerPeriodValue(
        timelineId: UUID,
        entry: PerPeriodHoursEntry,
    )
}
