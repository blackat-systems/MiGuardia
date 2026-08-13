package com.blackatsystems.miguardia.ui.summary

import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class SummaryMonthSourceData(
    val shifts: List<Shift>,
    val explicitStatuses: List<ExplicitDayStatus>,
    val medicalLeaves: List<MedicalLeave>,
)

class SummaryMonthObserver(
    private val shiftRepository: ShiftRepository,
    private val explicitDayStatusRepository: ExplicitDayStatusRepository,
    private val medicalLeaveRepository: MedicalLeaveRepository,
) {
    fun observe(month: YearMonth): Flow<SummaryMonthSourceData> = combine(
        shiftRepository.observeStartingBetween(month.atDay(1), month.atEndOfMonth()),
        explicitDayStatusRepository.observeBetween(month.atDay(1), month.atEndOfMonth()),
        medicalLeaveRepository.observeIntersecting(month.atDay(1), month.atEndOfMonth()),
    ) { shifts, explicitStatuses, medicalLeaves ->
        SummaryMonthSourceData(shifts, explicitStatuses, medicalLeaves)
    }
}
