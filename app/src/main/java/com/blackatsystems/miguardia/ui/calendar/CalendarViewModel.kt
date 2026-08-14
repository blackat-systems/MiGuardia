package com.blackatsystems.miguardia.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CalendarViewModel(
    private val shiftRepository: ShiftRepository,
    private val explicitDayStatusRepository: ExplicitDayStatusRepository,
    private val medicalLeaveRepository: MedicalLeaveRepository,
    private val holidayRepository: HolidayRepository? = null,
    private val vacationRepository: VacationRepository? = null,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val zone = AppDefaults.zoneId()
    private val monthObserver = CalendarMonthObserver(
        shiftRepository = shiftRepository,
        explicitDayStatusRepository = explicitDayStatusRepository,
        medicalLeaveRepository = medicalLeaveRepository,
        holidayRepository = holidayRepository,
        vacationRepository = vacationRepository,
    )
    private val initialMonth = savedStateHandle.get<String>(VISIBLE_MONTH_KEY)
        ?.let(YearMonth::parse)
        ?: YearMonth.now(clock.withZone(zone))

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(
        CalendarUiState(
            visibleMonth = initialMonth,
            referenceInstant = clock.instant(),
        ),
    )
    val uiState: kotlinx.coroutines.flow.StateFlow<CalendarUiState> = _uiState

    private var observationJob: Job? = null
    private var boundaryJob: Job? = null

    init {
        observeMonth(initialMonth)
    }

    fun showPreviousMonth() = setVisibleMonth(_uiState.value.visibleMonth.minusMonths(1))

    fun showNextMonth() = setVisibleMonth(_uiState.value.visibleMonth.plusMonths(1))

    fun showCurrentMonth() = setVisibleMonth(YearMonth.now(clock.withZone(zone)))

    fun selectDate(date: LocalDate) {
        if (YearMonth.from(date) == _uiState.value.visibleMonth) {
            _uiState.update { it.copy(selectedDate = date) }
        }
    }

    fun clearSelectedDate() {
        _uiState.update { it.copy(selectedDate = null) }
    }

    fun retry() {
        observeMonth(_uiState.value.visibleMonth)
    }

    private fun setVisibleMonth(month: YearMonth) {
        if (month == _uiState.value.visibleMonth) return
        savedStateHandle[VISIBLE_MONTH_KEY] = month.toString()
        _uiState.update {
            CalendarUiState(
                visibleMonth = month,
                referenceInstant = clock.instant(),
            )
        }
        observeMonth(month)
    }

    private fun observeMonth(month: YearMonth) {
        observationJob?.cancel()
        boundaryJob?.cancel()
        _uiState.update {
            it.copy(
                loadState = CalendarLoadState.LOADING,
                errorMessage = null,
            )
        }

        observationJob = viewModelScope.launch {
            monthObserver.observe(month)
                .catch {
                    boundaryJob?.cancel()
                    _uiState.update { current ->
                        current.copy(
                            loadState = CalendarLoadState.ERROR,
                            errorMessage = "No pudimos cargar este mes.",
                        )
                    }
                }
                .collect { data ->
                    publish(month, data, clock.instant())
                    scheduleNextTemporalBoundary(month, data)
                }
        }
    }

    private fun publish(
        month: YearMonth,
        data: CalendarMonthSourceData,
        now: Instant,
    ) {
        if (_uiState.value.visibleMonth != month) return
        _uiState.update { current ->
            current.copy(
                referenceInstant = now,
                days = projectCalendarMonth(
                    month = month,
                    shifts = data.shifts,
                    explicitDayStatuses = data.explicitStatuses,
                    medicalLeaves = data.medicalLeaves,
                    now = now,
                    holidays = data.holidays,
                    vacations = data.vacations,
                ),
                loadState = CalendarLoadState.CONTENT,
                errorMessage = null,
            )
        }
    }

    private fun scheduleNextTemporalBoundary(month: YearMonth, data: CalendarMonthSourceData) {
        boundaryJob?.cancel()
        boundaryJob = viewModelScope.launch {
            while (isActive && _uiState.value.visibleMonth == month) {
                val now = clock.instant()
                val shiftBoundary = data.shifts
                    .asSequence()
                    .filter { it.status == ShiftStatus.PLANNED }
                    .flatMap { sequenceOf(it.startAt, it.endAt) }
                    .filter { it > now }
                    .minOrNull()
                val nextLocalMidnight = ZonedDateTime.of(
                    LocalDate.now(clock.withZone(zone)).plusDays(1),
                    LocalTime.MIDNIGHT,
                    zone,
                ).toInstant()
                val nextBoundary = listOfNotNull(shiftBoundary, nextLocalMidnight).minOrNull()
                    ?: return@launch
                delay(Duration.between(now, nextBoundary).toMillis().coerceAtLeast(1L))
                val refreshedNow = clock.instant()
                if (refreshedNow <= now) return@launch
                publish(month, data, refreshedNow)
            }
        }
    }

    class Factory(
        private val shiftRepository: ShiftRepository,
        private val explicitDayStatusRepository: ExplicitDayStatusRepository,
        private val medicalLeaveRepository: MedicalLeaveRepository,
        private val holidayRepository: HolidayRepository? = null,
        private val vacationRepository: VacationRepository? = null,
        private val clock: Clock = Clock.system(AppDefaults.zoneId()),
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras: CreationExtras,
        ): T {
            require(modelClass.isAssignableFrom(CalendarViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(
                shiftRepository = shiftRepository,
                explicitDayStatusRepository = explicitDayStatusRepository,
                medicalLeaveRepository = medicalLeaveRepository,
                holidayRepository = holidayRepository,
                vacationRepository = vacationRepository,
                clock = clock,
                savedStateHandle = extras.createSavedStateHandle(),
            ) as T
        }
    }

    private companion object {
        const val VISIBLE_MONTH_KEY = "calendar.visibleMonth"
    }
}
