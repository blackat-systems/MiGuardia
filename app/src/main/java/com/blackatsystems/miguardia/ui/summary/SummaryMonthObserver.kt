package com.blackatsystems.miguardia.ui.summary

import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

data class SummaryMonthSourceData(
    val shifts: List<Shift>,
    val explicitStatuses: List<ExplicitDayStatus>,
    val medicalLeaves: List<MedicalLeave>,
    val holidays: List<Holiday> = emptyList(),
    val vacations: List<Vacation> = emptyList(),
)

class SummaryMonthObserver(
    private val shiftRepository: ShiftRepository,
    private val explicitDayStatusRepository: ExplicitDayStatusRepository,
    private val medicalLeaveRepository: MedicalLeaveRepository,
    private val holidayRepository: HolidayRepository? = null,
    private val vacationRepository: VacationRepository? = null,
) {
    fun observe(month: YearMonth): Flow<SummaryMonthSourceData> = combine(
        shiftRepository.observeStartingBetween(month.atDay(1), month.atEndOfMonth()),
        explicitDayStatusRepository.observeBetween(month.atDay(1), month.atEndOfMonth()),
        medicalLeaveRepository.observeIntersecting(month.atDay(1), month.atEndOfMonth()),
        holidayRepository?.observeBetween(month.atDay(1), month.atEndOfMonth().plusDays(1)) ?: flowOf(emptyList()),
        vacationRepository?.observeOverlapping(month.atDay(1), month.atEndOfMonth()) ?: flowOf(emptyList()),
    ) { shifts, explicitStatuses, medicalLeaves, holidays, vacations ->
        SummaryMonthSourceData(shifts, explicitStatuses, medicalLeaves, holidays, vacations)
    }
}
