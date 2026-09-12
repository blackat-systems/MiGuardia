package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.RecurringPlanAggregate
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanExpectation
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanMutation
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrence
import com.blackatsystems.miguardia.core.domain.model.RecurringProtectionExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWriteExpectation
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface RecurringPlanRepository {
    fun observePlans(timelineId: UUID, sector: WorkSector): Flow<List<RecurringPlanAggregate>>

    suspend fun getPlan(planId: UUID): RecurringPlanAggregate?

    suspend fun getOccurrenceForShift(shiftId: UUID): RecurringOccurrence?

    suspend fun captureProtection(
        shiftIds: Set<UUID>,
        startDateInclusive: LocalDate? = null,
        endDateInclusive: LocalDate? = null,
    ): RecurringProtectionExpectation
}

interface V2RecurringShiftRepository : V2ShiftRepository {
    suspend fun applyRecurringPlanMutation(
        mutation: RecurringPlanMutation,
        expectedPlan: RecurringPlanExpectation,
        expectedOccupancy: ShiftOccupancyExpectation,
        expectedPairs: V2ShiftWriteExpectation,
        expectedProtection: RecurringProtectionExpectation,
    )
}
