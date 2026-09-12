package com.blackatsystems.miguardia.ui.vacation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.OverlappingVacationException
import com.blackatsystems.miguardia.core.domain.repository.VacationMedicalLeaveConflictException
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface VacationUuidProvider {
    fun newUuid(): UUID
}

class VacationViewModel(
    private val repository: VacationRepository,
    private val clock: Clock,
    private val uuidProvider: VacationUuidProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val zone = AppDefaults.zoneId()
    private val writeMutex = Mutex()
    private val initialMonth = savedStateHandle.get<String>(MONTH_KEY)?.let(YearMonth::parse)
        ?: YearMonth.now(clock.withZone(zone))
    private val initialSurface = savedStateHandle.get<String>(SURFACE_KEY)
        ?.let(VacationSurface::valueOf)
        ?: VacationSurface.NONE
    private val initialDraft = VacationDraft(
        editingId = savedStateHandle.get<String>(EDITING_ID_KEY)?.let(UUID::fromString),
        observedVacation = restoreVacation(OBSERVED_EDIT_PREFIX),
        startDate = savedStateHandle.get<String>(START_DATE_KEY)?.let(LocalDate::parse),
        endDateInclusive = savedStateHandle.get<String>(END_DATE_KEY)?.let(LocalDate::parse),
        isDirty = savedStateHandle[DIRTY_KEY] ?: false,
    ).let { draft ->
        if (draft.editingId == draft.observedVacation?.id) draft
        else draft.copy(observedVacation = null)
    }
    private val _uiState = MutableStateFlow(
        VacationUiState(
            surface = initialSurface,
            visibleMonth = initialMonth,
            draft = initialDraft,
            pendingDelete = restoreVacation(PENDING_DELETE_PREFIX),
        ),
    )
    val uiState: StateFlow<VacationUiState> = _uiState
    private var observationJob: Job? = null

    init {
        observeMonth(initialMonth)
    }

    fun openList(month: YearMonth = _uiState.value.visibleMonth) {
        setMonth(month)
        clearDraft()
        clearMessage()
        setSurface(VacationSurface.LIST)
    }

    fun openCreate(month: YearMonth, selectedDate: LocalDate? = null) {
        setMonth(month)
        clearMessage()
        val initialDate = selectedDate ?: if (YearMonth.now(clock.withZone(zone)) == month) {
            LocalDate.now(clock.withZone(zone))
        } else {
            month.atDay(1)
        }
        setDraft(VacationDraft(startDate = initialDate, endDateInclusive = initialDate))
        setSurface(VacationSurface.EDITOR)
    }

    fun edit(vacation: Vacation) {
        setMonth(YearMonth.from(vacation.startDate))
        clearMessage()
        setDraft(
            VacationDraft(
                editingId = vacation.id,
                observedVacation = vacation,
                startDate = vacation.startDate,
                endDateInclusive = vacation.endDateInclusive,
            ),
        )
        setSurface(VacationSurface.EDITOR)
    }

    fun previousMonth() = setMonth(_uiState.value.visibleMonth.minusMonths(1))
    fun nextMonth() = setMonth(_uiState.value.visibleMonth.plusMonths(1))

    fun updateStartDate(date: LocalDate) {
        setDraft(_uiState.value.draft.copy(startDate = date, isDirty = true))
    }

    fun updateEndDate(date: LocalDate) {
        setDraft(_uiState.value.draft.copy(endDateInclusive = date, isDirty = true))
    }

    fun save() {
        val draft = _uiState.value.draft
        val start = draft.startDate ?: return showError("Elegí la fecha inicial.")
        val end = draft.endDateInclusive ?: return showError("Elegí la fecha final.")
        if (end.isBefore(start)) {
            return showError("La fecha final no puede ser anterior a la inicial.")
        }
        val expected = draft.observedVacation
        if (draft.editingId != null && expected == null) {
            return showError("No pudimos recuperar la versión que estabas editando. Volvé a abrir el período.")
        }
        launchWrite {
            val now = normalizedInstant(clock.instant())
            val vacation = Vacation(
                id = expected?.id ?: uuidProvider.newUuid(),
                startDate = start,
                endDateInclusive = end,
                createdAt = expected?.createdAt ?: now,
                updatedAt = expected?.let { nextUpdateInstant(it.updatedAt, now) } ?: now,
            )
            if (expected == null) repository.insert(vacation)
            else repository.update(expected, vacation)
            clearDraft()
            setSurface(VacationSurface.LIST)
            _uiState.update { it.copy(infoMessage = "Vacaciones guardadas.") }
        }
    }

    fun requestBack() {
        when (_uiState.value.surface) {
            VacationSurface.NONE -> Unit
            VacationSurface.LIST -> close()
            VacationSurface.EDITOR -> if (_uiState.value.draft.isDirty) {
                _uiState.update { it.copy(showDiscardConfirmation = true) }
            } else {
                clearDraft()
                clearMessage()
                setSurface(VacationSurface.LIST)
            }
        }
    }

    fun dismissDiscard() = _uiState.update { it.copy(showDiscardConfirmation = false) }

    fun confirmDiscard() {
        clearDraft()
        clearMessage()
        setSurface(VacationSurface.LIST)
    }

    fun requestDelete(vacation: Vacation) {
        saveVacation(PENDING_DELETE_PREFIX, vacation)
        _uiState.update { it.copy(pendingDelete = vacation) }
    }

    fun dismissDelete() {
        clearVacation(PENDING_DELETE_PREFIX)
        _uiState.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val expected = _uiState.value.pendingDelete ?: return
        launchWrite {
            repository.delete(expected)
            clearVacation(PENDING_DELETE_PREFIX)
            _uiState.update {
                it.copy(pendingDelete = null, infoMessage = "Período eliminado.")
            }
        }
    }

    fun close() {
        clearDraft()
        setSurface(VacationSurface.NONE)
        _uiState.update {
            it.copy(
                showDiscardConfirmation = false,
                pendingDelete = null,
                errorMessage = null,
                infoMessage = null,
            )
        }
        clearVacation(PENDING_DELETE_PREFIX)
    }

    fun retry() = observeMonth(_uiState.value.visibleMonth)
    fun clearMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    private fun setMonth(month: YearMonth) {
        if (_uiState.value.visibleMonth == month) return
        savedStateHandle[MONTH_KEY] = month.toString()
        _uiState.update { it.copy(visibleMonth = month) }
        observeMonth(month)
    }

    private fun observeMonth(month: YearMonth) {
        observationJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        observationJob = viewModelScope.launch {
            repository.observeOverlapping(month.atDay(1), month.atEndOfMonth())
                .catch {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "No pudimos cargar las vacaciones.")
                    }
                }
                .collect { vacations ->
                    if (_uiState.value.visibleMonth == month) {
                        _uiState.update {
                            it.copy(vacations = vacations, isLoading = false, errorMessage = null)
                        }
                    }
                }
        }
    }

    private fun launchWrite(block: suspend () -> Unit) {
        viewModelScope.launch {
            writeMutex.withLock {
                _uiState.update { it.copy(isSaving = true, errorMessage = null, infoMessage = null) }
                try {
                    block()
                } catch (_: OverlappingVacationException) {
                    showError("Ese período se superpone con otras vacaciones existentes.")
                } catch (_: VacationMedicalLeaveConflictException) {
                    showError("Las vacaciones no pueden superponerse con una carpeta médica.")
                } catch (_: ConflictingLocalWriteException) {
                    showError(
                        "Este período cambió desde que lo abriste. No se guardó nada; volvé a abrirlo para continuar.",
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    showError("No pudimos guardar el cambio. Revisá los datos y reintentá.")
                } finally {
                    _uiState.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message, infoMessage = null) }
    }

    private fun setSurface(surface: VacationSurface) {
        savedStateHandle[SURFACE_KEY] = surface.name
        _uiState.update { it.copy(surface = surface, showDiscardConfirmation = false) }
    }

    private fun setDraft(draft: VacationDraft) {
        savedStateHandle[EDITING_ID_KEY] = draft.editingId?.toString()
        savedStateHandle[START_DATE_KEY] = draft.startDate?.toString()
        savedStateHandle[END_DATE_KEY] = draft.endDateInclusive?.toString()
        savedStateHandle[DIRTY_KEY] = draft.isDirty
        if (draft.observedVacation == null) clearVacation(OBSERVED_EDIT_PREFIX)
        else saveVacation(OBSERVED_EDIT_PREFIX, draft.observedVacation)
        _uiState.update { it.copy(draft = draft, showDiscardConfirmation = false) }
    }

    private fun clearDraft() {
        savedStateHandle.remove<String>(EDITING_ID_KEY)
        savedStateHandle.remove<String>(START_DATE_KEY)
        savedStateHandle.remove<String>(END_DATE_KEY)
        savedStateHandle[DIRTY_KEY] = false
        clearVacation(OBSERVED_EDIT_PREFIX)
        _uiState.update { it.copy(draft = VacationDraft(), showDiscardConfirmation = false) }
    }

    private fun restoreVacation(prefix: String): Vacation? = runCatching {
        Vacation(
            id = UUID.fromString(savedStateHandle.get<String>("$prefix.id") ?: return null),
            startDate = LocalDate.parse(
                savedStateHandle.get<String>("$prefix.startDate") ?: return null,
            ),
            endDateInclusive = LocalDate.parse(
                savedStateHandle.get<String>("$prefix.endDateInclusive") ?: return null,
            ),
            createdAt = Instant.parse(
                savedStateHandle.get<String>("$prefix.createdAt") ?: return null,
            ),
            updatedAt = Instant.parse(
                savedStateHandle.get<String>("$prefix.updatedAt") ?: return null,
            ),
        )
    }.getOrNull()

    private fun saveVacation(prefix: String, vacation: Vacation) {
        savedStateHandle["$prefix.id"] = vacation.id.toString()
        savedStateHandle["$prefix.startDate"] = vacation.startDate.toString()
        savedStateHandle["$prefix.endDateInclusive"] = vacation.endDateInclusive.toString()
        savedStateHandle["$prefix.createdAt"] = vacation.createdAt.toString()
        savedStateHandle["$prefix.updatedAt"] = vacation.updatedAt.toString()
    }

    private fun clearVacation(prefix: String) {
        savedStateHandle.remove<String>("$prefix.id")
        savedStateHandle.remove<String>("$prefix.startDate")
        savedStateHandle.remove<String>("$prefix.endDateInclusive")
        savedStateHandle.remove<String>("$prefix.createdAt")
        savedStateHandle.remove<String>("$prefix.updatedAt")
    }

    private fun normalizedInstant(value: Instant): Instant = Instant.ofEpochMilli(value.toEpochMilli())

    private fun nextUpdateInstant(previous: Instant, now: Instant): Instant =
        maxOf(now, Instant.ofEpochMilli(Math.addExact(previous.toEpochMilli(), 1L)))

    class Factory(
        private val repository: VacationRepository,
        private val clock: Clock = Clock.system(AppDefaults.zoneId()),
        private val uuidProvider: VacationUuidProvider = VacationUuidProvider(UUID::randomUUID),
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(VacationViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return VacationViewModel(
                repository,
                clock,
                uuidProvider,
                extras.createSavedStateHandle(),
            ) as T
        }
    }

    private companion object {
        const val SURFACE_KEY = "vacation.surface"
        const val MONTH_KEY = "vacation.month"
        const val EDITING_ID_KEY = "vacation.editingId"
        const val START_DATE_KEY = "vacation.startDate"
        const val END_DATE_KEY = "vacation.endDate"
        const val DIRTY_KEY = "vacation.dirty"
        const val OBSERVED_EDIT_PREFIX = "vacation.observedEdit"
        const val PENDING_DELETE_PREFIX = "vacation.pendingDelete"
    }
}
