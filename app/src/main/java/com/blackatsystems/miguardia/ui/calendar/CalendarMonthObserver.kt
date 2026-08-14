package com.blackatsystems.miguardia.ui.calendar

import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

data class CalendarMonthSourceData(
    val shifts: List<Shift>,
    val explicitStatuses: List<ExplicitDayStatus>,
    val medicalLeaves: List<MedicalLeave>,
    val holidays: List<Holiday> = emptyList(),
)

class CalendarMonthObserver(
    private val shiftRepository: ShiftRepository,
    private val explicitDayStatusRepository: ExplicitDayStatusRepository,
    private val medicalLeaveRepository: MedicalLeaveRepository,
    private val holidayRepository: HolidayRepository? = null,
) {
    fun observe(month: YearMonth): Flow<CalendarMonthSourceData> {
        val startDate = month.atDay(1)
        val endDate = month.atEndOfMonth()
        return combine(
            shiftRepository.observeStartingBetween(startDate, endDate),
            explicitDayStatusRepository.observeBetween(startDate, endDate),
            medicalLeaveRepository.observeIntersecting(startDate, endDate),
            holidayRepository?.observeBetween(startDate, endDate) ?: flowOf(emptyList()),
        ) { shifts, explicitStatuses, medicalLeaves, holidays ->
            CalendarMonthSourceData(shifts, explicitStatuses, medicalLeaves, holidays)
        }
    }
}
