package com.blackatsystems.miguardia.ui.calendar

import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class CalendarMonthSourceData(
    val shifts: List<Shift>,
    val explicitStatuses: List<ExplicitDayStatus>,
    val medicalLeaves: List<MedicalLeave>,
)

class CalendarMonthObserver(
    private val shiftRepository: ShiftRepository,
    private val explicitDayStatusRepository: ExplicitDayStatusRepository,
    private val medicalLeaveRepository: MedicalLeaveRepository,
) {
    fun observe(month: YearMonth): Flow<CalendarMonthSourceData> {
        val startDate = month.atDay(1)
        val endDate = month.atEndOfMonth()
        return combine(
            shiftRepository.observeStartingBetween(startDate, endDate),
            explicitDayStatusRepository.observeBetween(startDate, endDate),
            medicalLeaveRepository.observeIntersecting(startDate, endDate),
        ) { shifts, explicitStatuses, medicalLeaves ->
            CalendarMonthSourceData(shifts, explicitStatuses, medicalLeaves)
        }
    }
}
