package com.blackatsystems.miguardia.ui.nextevent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class NextEventViewModel(
    shifts: ShiftRepository,
    explicitDayStatuses: ExplicitDayStatusRepository,
    vacations: VacationRepository,
    clock: Clock,
    zoneId: ZoneId,
    temporalDelay: TemporalDelay = TemporalDelay { duration ->
        kotlinx.coroutines.delay(duration.toMillis().coerceAtLeast(1L))
    },
) : ViewModel() {
    private val retryToken = MutableStateFlow(0L)
    private var lastValidResult: NextEventResult? = null
    private val observer = NextEventObserver(
        shifts = shifts,
        explicitDayStatuses = explicitDayStatuses,
        vacations = vacations,
        clock = clock,
        zoneId = zoneId,
        temporalDelay = temporalDelay,
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<NextEventUiState> = retryToken
        .flatMapLatest {
            observer.observe()
                .map { result ->
                    lastValidResult = result
                    NextEventUiState(
                        loadState = NextEventLoadState.CONTENT,
                        result = result,
                    )
                }
                .onStart {
                    emit(
                        NextEventUiState(
                            loadState = NextEventLoadState.LOADING,
                            result = lastValidResult,
                        ),
                    )
                }
                .catch {
                    emit(
                        NextEventUiState(
                            loadState = NextEventLoadState.ERROR,
                            result = lastValidResult,
                            errorMessage = "No pudimos actualizar el próximo evento.",
                        ),
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = NextEventUiState(),
        )

    fun retry() {
        retryToken.update { it + 1L }
    }

    class Factory(
        private val shifts: ShiftRepository,
        private val explicitDayStatuses: ExplicitDayStatusRepository,
        private val vacations: VacationRepository,
        private val clock: Clock = Clock.system(AppDefaults.zoneId()),
        private val zoneId: ZoneId = AppDefaults.zoneId(),
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NextEventViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return NextEventViewModel(
                shifts = shifts,
                explicitDayStatuses = explicitDayStatuses,
                vacations = vacations,
                clock = clock,
                zoneId = zoneId,
            ) as T
        }
    }
}
