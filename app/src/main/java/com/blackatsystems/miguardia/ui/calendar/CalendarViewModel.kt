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
    private val initialInteractionMode = savedStateHandle.get<String>(INTERACTION_MODE_KEY)
        .let(::calendarInteractionModeFromSaved)
    private val initialDetailDate = savedStateHandle.get<String>(DETAIL_DATE_KEY)
        ?: savedStateHandle.get<String>(LEGACY_SELECTED_DATE_KEY)
    private val parsedInitialDetailDate = initialDetailDate
        ?.let(LocalDate::parse)
    private val initialEditSelectedDates = savedStateHandle.get<ArrayList<String>>(EDIT_SELECTED_DATES_KEY)
        .orEmpty()
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .filterTo(linkedSetOf()) { YearMonth.from(it) == initialMonth }

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(
        CalendarUiState(
            visibleMonth = initialMonth,
            referenceInstant = clock.instant(),
            detailDate = parsedInitialDetailDate
                ?.takeIf { initialInteractionMode == CalendarInteractionMode.VIEW && YearMonth.from(it) == initialMonth },
            editSelectedDates = initialEditSelectedDates.takeIf {
                initialInteractionMode == CalendarInteractionMode.EDIT
            }.orEmpty(),
            interactionMode = initialInteractionMode,
        ),
    )
    val uiState: kotlinx.coroutines.flow.StateFlow<CalendarUiState> = _uiState

    private var observationJob: Job? = null
    private var boundaryJob: Job? = null

    init {
        observeGlobalShiftPresence()
        observeMonth(initialMonth)
    }

    fun showPreviousMonth() = setVisibleMonth(_uiState.value.visibleMonth.minusMonths(1))

    fun showNextMonth() = setVisibleMonth(_uiState.value.visibleMonth.plusMonths(1))

    fun showCurrentMonth() = setVisibleMonth(YearMonth.now(clock.withZone(zone)))

    fun selectDate(date: LocalDate) {
        if (
            _uiState.value.interactionMode == CalendarInteractionMode.VIEW &&
            YearMonth.from(date) == _uiState.value.visibleMonth
        ) {
            savedStateHandle[DETAIL_DATE_KEY] = date.toString()
            _uiState.update { it.copy(detailDate = date) }
        }
    }

    fun openDate(date: LocalDate) {
        val month = YearMonth.from(date)
        if (month != _uiState.value.visibleMonth) setVisibleMonth(month)
        savedStateHandle[INTERACTION_MODE_KEY] = CalendarInteractionMode.VIEW.name
        savedStateHandle[DETAIL_DATE_KEY] = date.toString()
        savedStateHandle.remove<ArrayList<String>>(EDIT_SELECTED_DATES_KEY)
        _uiState.update {
            it.copy(
                detailDate = date,
                editSelectedDates = emptySet(),
                interactionMode = CalendarInteractionMode.VIEW,
            )
        }
    }

    fun clearSelectedDate() {
        savedStateHandle.remove<String>(DETAIL_DATE_KEY)
        savedStateHandle.remove<String>(LEGACY_SELECTED_DATE_KEY)
        _uiState.update { it.copy(detailDate = null) }
    }

    fun enterEditMode(selectedDate: LocalDate? = null) {
        savedStateHandle[INTERACTION_MODE_KEY] = CalendarInteractionMode.EDIT.name
        savedStateHandle.remove<String>(DETAIL_DATE_KEY)
        savedStateHandle.remove<String>(LEGACY_SELECTED_DATE_KEY)
        persistEditSelection(selectedDate?.let(::setOf).orEmpty())
        _uiState.update { it.enterEditing(selectedDate) }
    }

    fun toggleEditDate(date: LocalDate) {
        val current = _uiState.value
        val updated = current.toggleEditDate(date)
        if (updated == current) return
        persistEditSelection(updated.editSelectedDates)
        _uiState.value = updated
    }

    fun setEditSelectedDates(dates: Set<LocalDate>) {
        val current = _uiState.value
        if (
            current.interactionMode != CalendarInteractionMode.EDIT ||
            dates.any { YearMonth.from(it) != current.visibleMonth }
        ) return
        persistEditSelection(dates)
        _uiState.update { it.copy(editSelectedDates = dates) }
    }

    fun clearEditSelection() = setEditSelectedDates(emptySet())

    fun finishEditMode() {
        savedStateHandle[INTERACTION_MODE_KEY] = CalendarInteractionMode.VIEW.name
        savedStateHandle.remove<ArrayList<String>>(EDIT_SELECTED_DATES_KEY)
        _uiState.update(CalendarUiState::finishEditing)
    }

    fun retry() {
        observeMonth(_uiState.value.visibleMonth)
    }

    private fun setVisibleMonth(month: YearMonth) {
        if (month == _uiState.value.visibleMonth) return
        savedStateHandle[VISIBLE_MONTH_KEY] = month.toString()
        savedStateHandle.remove<String>(DETAIL_DATE_KEY)
        savedStateHandle.remove<String>(LEGACY_SELECTED_DATE_KEY)
        savedStateHandle.remove<ArrayList<String>>(EDIT_SELECTED_DATES_KEY)
        _uiState.update {
            it.copy(
                visibleMonth = month,
                referenceInstant = clock.instant(),
                days = emptyList(),
                detailDate = null,
                editSelectedDates = emptySet(),
                loadState = CalendarLoadState.LOADING,
                errorMessage = null,
            )
        }
        observeMonth(month)
    }

    private fun persistEditSelection(dates: Set<LocalDate>) {
        if (dates.isEmpty()) {
            savedStateHandle.remove<ArrayList<String>>(EDIT_SELECTED_DATES_KEY)
        } else {
            savedStateHandle[EDIT_SELECTED_DATES_KEY] = ArrayList(dates.sorted().map(LocalDate::toString))
        }
    }

    private fun observeGlobalShiftPresence() {
        viewModelScope.launch {
            shiftRepository.observeHasAny()
                .catch { /* Conservamos el valor seguro: no ofrecer una falsa primera carga. */ }
                .collect { hasAny -> _uiState.update { it.copy(hasAnyShifts = hasAny) } }
        }
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
        const val INTERACTION_MODE_KEY = "calendar.interactionMode"
        const val DETAIL_DATE_KEY = "calendar.detailDate"
        const val EDIT_SELECTED_DATES_KEY = "calendar.editSelectedDates"
        const val LEGACY_SELECTED_DATE_KEY = "calendar.selectedDate"
    }
}
