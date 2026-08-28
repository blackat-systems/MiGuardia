package com.blackatsystems.miguardia.ui.nextevent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardProjection
import com.blackatsystems.miguardia.core.domain.repository.AvailabilityWindowRepository
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.IndependentExtraWorkRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import java.time.Duration
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive

class NextEventViewModel(
    shifts: V2ShiftRepository,
    availabilityWindows: AvailabilityWindowRepository,
    explicitDayStatuses: ExplicitDayStatusRepository,
    vacations: VacationRepository,
    medicalLeaves: MedicalLeaveRepository,
    shiftActuals: ShiftActualRepository,
    independentExtras: IndependentExtraWorkRepository,
    workConfiguration: WorkConfigurationRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val temporalDelay: TemporalDelay = TemporalDelay { duration ->
        kotlinx.coroutines.delay(duration.toMillis().coerceAtLeast(1L))
    },
) : ViewModel() {
    private val retryToken = MutableStateFlow(0L)
    private var lastValidResult: TodayCardProjection? = null
    private val observer = NextEventObserver(
        shifts = shifts,
        availabilityWindows = availabilityWindows,
        explicitDayStatuses = explicitDayStatuses,
        vacations = vacations,
        medicalLeaves = medicalLeaves,
        shiftActuals = shiftActuals,
        independentExtras = independentExtras,
        workConfiguration = workConfiguration,
        clock = clock,
        zoneId = zoneId,
        temporalDelay = temporalDelay,
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<NextEventUiState> = retryToken
        .flatMapLatest {
            observer.observeStates()
                .map { observation ->
                    when (observation) {
                        is NextEventObservation.Loading -> NextEventUiState(
                            loadState = NextEventLoadState.LOADING,
                            result = currentLastValidResult(observation.date),
                        )

                        is NextEventObservation.Content -> {
                            lastValidResult = observation.projection
                            NextEventUiState(
                                loadState = NextEventLoadState.CONTENT,
                                result = observation.projection,
                            )
                        }
                    }
                }
                .retryWhen { error, _ ->
                    if (error is CancellationException) return@retryWhen false
                    val currentDate = clock.instant().atZone(zoneId).toLocalDate()
                    val failedDate = (error as? NextEventObservationFailure)
                        ?.observedDate
                        ?: currentDate
                    if (failedDate != currentDate) return@retryWhen true
                    emit(
                        NextEventUiState(
                            loadState = NextEventLoadState.ERROR,
                            result = currentLastValidResult(currentDate),
                            errorMessage = "No pudimos actualizar los eventos laborales de hoy.",
                        ),
                    )
                    awaitCivilDateChange(failedDate)
                    true
                }
                .catch {
                    emit(
                        NextEventUiState(
                            loadState = NextEventLoadState.ERROR,
                            result = currentLastValidResult(),
                            errorMessage = "No pudimos actualizar los eventos laborales de hoy.",
                        ),
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000L,
                replayExpirationMillis = 0L,
            ),
            initialValue = NextEventUiState(),
        )

    fun retry() {
        retryToken.update { it + 1L }
    }

    private fun currentLastValidResult(
        date: LocalDate = clock.instant().atZone(zoneId).toLocalDate(),
    ): TodayCardProjection? = lastValidResult?.takeIf { result ->
        result.date == date
    }

    private suspend fun awaitCivilDateChange(failedDate: LocalDate) {
        while (currentCoroutineContext().isActive) {
            val now = clock.instant()
            val currentDate = now.atZone(zoneId).toLocalDate()
            if (currentDate != failedDate) return
            val nextMidnight = currentDate.plusDays(1).atStartOfDay(zoneId).toInstant()
            val duration = Duration.between(now, nextMidnight).let { candidate ->
                if (candidate.isNegative || candidate.isZero) Duration.ofMillis(1L) else candidate
            }
            temporalDelay.await(duration)
        }
    }

    class Factory(
        private val shifts: V2ShiftRepository,
        private val availabilityWindows: AvailabilityWindowRepository,
        private val explicitDayStatuses: ExplicitDayStatusRepository,
        private val vacations: VacationRepository,
        private val medicalLeaves: MedicalLeaveRepository,
        private val shiftActuals: ShiftActualRepository,
        private val independentExtras: IndependentExtraWorkRepository,
        private val workConfiguration: WorkConfigurationRepository,
        private val clock: Clock = Clock.system(AppDefaults.zoneId()),
        private val zoneId: ZoneId = AppDefaults.zoneId(),
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NextEventViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return NextEventViewModel(
                shifts = shifts,
                availabilityWindows = availabilityWindows,
                explicitDayStatuses = explicitDayStatuses,
                vacations = vacations,
                medicalLeaves = medicalLeaves,
                shiftActuals = shiftActuals,
                independentExtras = independentExtras,
                workConfiguration = workConfiguration,
                clock = clock,
                zoneId = zoneId,
            ) as T
        }
    }
}
