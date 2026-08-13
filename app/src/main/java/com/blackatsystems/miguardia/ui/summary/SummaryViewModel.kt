package com.blackatsystems.miguardia.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.hours.calculateMonthlyHours
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SummaryViewModel(
    shiftRepository: ShiftRepository,
    explicitDayStatusRepository: ExplicitDayStatusRepository,
    medicalLeaveRepository: MedicalLeaveRepository,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val zone = AppDefaults.zoneId()
    private val observer = SummaryMonthObserver(
        shiftRepository,
        explicitDayStatusRepository,
        medicalLeaveRepository,
    )
    private val initialMonth = savedStateHandle.get<String>(VISIBLE_MONTH_KEY)
        ?.let(YearMonth::parse)
        ?: YearMonth.now(clock.withZone(zone))
    private val _uiState = MutableStateFlow(
        SummaryUiState(initialMonth, clock.instant()),
    )
    val uiState: StateFlow<SummaryUiState> = _uiState

    private var observationJob: Job? = null
    private var temporalJob: Job? = null

    init {
        observeMonth(initialMonth)
    }

    fun showPreviousMonth() = setVisibleMonth(_uiState.value.visibleMonth.minusMonths(1))

    fun showNextMonth() = setVisibleMonth(_uiState.value.visibleMonth.plusMonths(1))

    fun showCurrentMonth() = setVisibleMonth(YearMonth.now(clock.withZone(zone)))

    fun retry() = observeMonth(_uiState.value.visibleMonth)

    private fun setVisibleMonth(month: YearMonth) {
        if (month == _uiState.value.visibleMonth) return
        savedStateHandle[VISIBLE_MONTH_KEY] = month.toString()
        _uiState.value = SummaryUiState(month, clock.instant())
        observeMonth(month)
    }

    private fun observeMonth(month: YearMonth) {
        observationJob?.cancel()
        temporalJob?.cancel()
        _uiState.update { it.copy(loadState = SummaryLoadState.LOADING, errorMessage = null) }
        observationJob = viewModelScope.launch {
            observer.observe(month)
                .catch {
                    temporalJob?.cancel()
                    _uiState.update {
                        it.copy(
                            loadState = SummaryLoadState.ERROR,
                            errorMessage = "No pudimos calcular el resumen de este mes.",
                        )
                    }
                }
                .collect { data ->
                    publish(month, data, clock.instant())
                    scheduleTemporalUpdates(month, data)
                }
        }
    }

    private fun publish(month: YearMonth, data: SummaryMonthSourceData, now: Instant) {
        if (_uiState.value.visibleMonth != month) return
        _uiState.value = SummaryUiState(
            visibleMonth = month,
            referenceInstant = now,
            summary = calculateMonthlyHours(
                month = month,
                shifts = data.shifts,
                explicitDayStatuses = data.explicitStatuses,
                medicalLeaves = data.medicalLeaves,
                referenceInstant = now,
                holidayDates = emptySet(),
            ),
            loadState = SummaryLoadState.CONTENT,
        )
    }

    private fun scheduleTemporalUpdates(month: YearMonth, data: SummaryMonthSourceData) {
        temporalJob?.cancel()
        temporalJob = viewModelScope.launch {
            while (isActive && _uiState.value.visibleMonth == month) {
                val now = clock.instant()
                val nextBoundary = nextSummaryUpdateInstant(
                    now = now,
                    zone = zone,
                    shifts = data.shifts,
                    medicalLeaves = data.medicalLeaves,
                )
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
        private val clock: Clock = Clock.system(AppDefaults.zoneId()),
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(SummaryViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return SummaryViewModel(
                shiftRepository,
                explicitDayStatusRepository,
                medicalLeaveRepository,
                clock,
                extras.createSavedStateHandle(),
            ) as T
        }
    }

    private companion object {
        const val VISIBLE_MONTH_KEY = "summary.visibleMonth"
    }
}

internal fun nextSummaryUpdateInstant(
    now: Instant,
    zone: ZoneId,
    shifts: List<Shift>,
    medicalLeaves: List<MedicalLeave>,
): Instant {
    val relevantShifts = shifts.filter {
        it.status == ShiftStatus.PLANNED && medicalLeaves.none { leave ->
            it.localStartDate >= leave.startDate && it.localStartDate <= leave.endDateInclusive
        }
    }
    val active = relevantShifts.any { now >= it.startAt && now < it.endAt }
    val minuteBoundary = if (active) {
        now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES)
    } else {
        null
    }
    val shiftBoundary = relevantShifts
        .asSequence()
        .flatMap { sequenceOf(it.startAt, it.endAt) }
        .filter { it > now }
        .minOrNull()
    val midnightBoundary = now.atZone(zone)
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(zone)
        .toInstant()
    return listOfNotNull(minuteBoundary, shiftBoundary, midnightBoundary).min()
}
