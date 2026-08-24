package com.blackatsystems.miguardia.ui.exceptions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.HolidayBatchMutation
import com.blackatsystems.miguardia.core.domain.model.HolidayConflictPolicy
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import com.blackatsystems.miguardia.core.domain.repository.LocalDataException
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoteRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

internal data class ExceptionsPersistedState(
    val surface: ExceptionsSurface,
    val holidayMonth: YearMonth,
    val shiftId: UUID? = null,
)

internal class ExceptionsCoordinator(
    private val holidays: HolidayRepository,
    private val notes: ShiftNoteRepository,
    private val shifts: ShiftRepository,
    private val clock: Clock,
    private val uuidProvider: () -> UUID,
    private val scope: CoroutineScope,
    initialState: ExceptionsPersistedState,
    private val persist: (ExceptionsPersistedState) -> Unit,
) {
    private val writeMutex = Mutex()
    private val _uiState = MutableStateFlow(
        ExceptionsUiState(
            surface = initialState.surface,
            holidayMonth = initialState.holidayMonth,
        ),
    )
    val uiState: StateFlow<ExceptionsUiState> = _uiState.asStateFlow()

    private var holidayJob: Job? = null
    private var noteJob: Job? = null

    init {
        when {
            initialState.surface == ExceptionsSurface.NOTES && initialState.shiftId != null -> {
                scope.launch {
                    try {
                        val restoredShift = shifts.getById(initialState.shiftId)
                        if (restoredShift == null) {
                            setSurface(ExceptionsSurface.NONE, null)
                        } else {
                            activateNotes(restoredShift)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        setSurface(ExceptionsSurface.NONE, null)
                    }
                }
            }
            initialState.surface == ExceptionsSurface.NOTES -> {
                setSurface(ExceptionsSurface.NONE, null)
                observeHolidays(initialState.holidayMonth)
            }
            else -> observeHolidays(initialState.holidayMonth)
        }
    }

    fun openHolidays(month: YearMonth = _uiState.value.holidayMonth) {
        if (_uiState.value.isSaving) return
        noteJob?.cancel()
        _uiState.update {
            it.copy(
                surface = ExceptionsSurface.HOLIDAYS,
                holidayMonth = month,
                selectedShift = null,
                notes = emptyList(),
                noteDraft = NoteDraft(),
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
            )
        }
        setSurface(ExceptionsSurface.HOLIDAYS, null)
        setHolidayMonth(month)
    }

    fun previousHolidayMonth() {
        if (!_uiState.value.isSaving) setHolidayMonth(_uiState.value.holidayMonth.minusMonths(1))
    }

    fun nextHolidayMonth() {
        if (!_uiState.value.isSaving) setHolidayMonth(_uiState.value.holidayMonth.plusMonths(1))
    }

    fun openNotes(shift: Shift) {
        if (_uiState.value.isSaving) return
        activateNotes(shift)
    }

    fun close() {
        if (_uiState.value.isSaving) return
        holidayJob?.cancel()
        noteJob?.cancel()
        _uiState.update {
            it.copy(
                surface = ExceptionsSurface.NONE,
                selectedShift = null,
                holidays = emptyList(),
                notes = emptyList(),
                holidayDraft = HolidayDraft(),
                noteDraft = NoteDraft(),
                isLoading = false,
                errorMessage = null,
                infoMessage = null,
            )
        }
        setSurface(ExceptionsSurface.NONE, null)
    }

    fun updateHolidayDraft(transform: (HolidayDraft) -> HolidayDraft) {
        if (_uiState.value.isSaving) return
        _uiState.update {
            val updated = transform(it.holidayDraft)
            it.copy(
                holidayDraft = updated.copy(conflictDates = emptySet(), pendingPolicy = null),
                errorMessage = null,
            )
        }
    }

    fun editHoliday(holiday: Holiday) {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                holidayDraft = HolidayDraft(
                    editingId = holiday.id,
                    datesText = holiday.date.toString(),
                    name = holiday.name.orEmpty(),
                ),
                errorMessage = null,
            )
        }
    }

    fun cancelHolidayEdit() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(holidayDraft = HolidayDraft(), errorMessage = null) }
    }

    fun saveHolidays(policy: HolidayConflictPolicy? = null) {
        val draft = _uiState.value.holidayDraft
        val dates = parseDates(draft.datesText)
            ?: return showError("No pudimos leer las fechas elegidas. Volvé a seleccionarlas.")
        if (dates.isEmpty()) return showError("Elegí al menos una fecha en el calendario.")
        if (draft.editingId != null && dates.size != 1) {
            return showError("La edición de un feriado admite una sola fecha.")
        }
        launchWrite {
            val editing = draft.editingId?.let { holidays.getById(it) }
            if (draft.editingId != null && editing == null) {
                throw IllegalStateException("El feriado ya no existe.")
            }
            val existingByDate = dates.mapNotNull { date ->
                holidays.getByDate(date)?.let { date to it }
            }.toMap()
            val conflicts = existingByDate.keys.filterTo(linkedSetOf()) { date ->
                existingByDate[date]?.id != draft.editingId
            }
            if (conflicts.isNotEmpty() && policy == null) {
                _uiState.update {
                    it.copy(
                        holidayDraft = draft.copy(
                            conflictDates = conflicts,
                            pendingPolicy = null,
                        ),
                    )
                }
                return@launchWrite
            }
            val now = clock.instant()
            val incoming = dates.map { date ->
                Holiday(
                    id = editing?.id ?: uuidProvider(),
                    date = date,
                    name = draft.name,
                    createdAt = editing?.createdAt ?: now,
                    updatedAt = now,
                )
            }
            holidays.applyBatch(
                HolidayBatchMutation(
                    holidayIdsToDelete = if (
                        editing != null && policy == HolidayConflictPolicy.REPLACE
                    ) {
                        conflicts.mapNotNullTo(linkedSetOf()) { existingByDate[it]?.id }
                    } else {
                        emptySet()
                    },
                    holidaysToSave = incoming,
                    conflictPolicy = policy ?: HolidayConflictPolicy.KEEP_EXISTING,
                ),
            )
            _uiState.update {
                it.copy(
                    holidayDraft = HolidayDraft(),
                    infoMessage = "Feriados guardados.",
                )
            }
        }
    }

    fun cancelHolidayConflict() {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                holidayDraft = it.holidayDraft.copy(
                    conflictDates = emptySet(),
                    pendingPolicy = null,
                ),
            )
        }
    }

    fun deleteHoliday(id: UUID) = launchWrite {
        holidays.delete(id)
        _uiState.update {
            it.copy(
                holidayDraft = it.holidayDraft.takeUnless { draft -> draft.editingId == id }
                    ?: HolidayDraft(),
                infoMessage = "Feriado eliminado.",
            )
        }
    }

    fun updateNoteDraft(transform: (NoteDraft) -> NoteDraft) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(noteDraft = transform(it.noteDraft), errorMessage = null) }
    }

    fun editNote(note: ShiftNote) {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                noteDraft = NoteDraft(editingId = note.id, body = note.body),
                errorMessage = null,
            )
        }
    }

    fun cancelNoteEdit() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(noteDraft = NoteDraft(), errorMessage = null) }
    }

    fun saveNote() {
        val shift = _uiState.value.selectedShift ?: return
        val draft = _uiState.value.noteDraft
        launchWrite {
            val existing = draft.editingId?.let { notes.getById(it) }
            if (draft.editingId != null && existing == null) {
                throw IllegalStateException("La nota ya no existe.")
            }
            val now = clock.instant()
            val note = ShiftNote(
                id = existing?.id ?: uuidProvider(),
                shiftId = shift.id,
                body = draft.body,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            if (existing == null) notes.insert(note) else notes.update(note)
            _uiState.update {
                it.copy(noteDraft = NoteDraft(), infoMessage = "Nota guardada.")
            }
        }
    }

    fun deleteNote(id: UUID) = launchWrite {
        notes.delete(id)
        _uiState.update {
            it.copy(
                noteDraft = it.noteDraft.takeUnless { draft -> draft.editingId == id }
                    ?: NoteDraft(),
                infoMessage = "Nota eliminada.",
            )
        }
    }

    fun retry() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(errorMessage = null, isLoading = true) }
        when (_uiState.value.surface) {
            ExceptionsSurface.HOLIDAYS -> observeHolidays(_uiState.value.holidayMonth)
            ExceptionsSurface.NOTES -> _uiState.value.selectedShift?.id?.let(::observeNotes)
            ExceptionsSurface.NONE -> _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    private fun activateNotes(shift: Shift) {
        holidayJob?.cancel()
        _uiState.update {
            it.copy(
                surface = ExceptionsSurface.NOTES,
                selectedShift = shift,
                holidays = emptyList(),
                notes = emptyList(),
                holidayDraft = HolidayDraft(),
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
            )
        }
        setSurface(ExceptionsSurface.NOTES, shift.id)
        observeNotes(shift.id)
    }

    private fun setHolidayMonth(month: YearMonth) {
        _uiState.update { it.copy(holidayMonth = month, isLoading = true, errorMessage = null) }
        persistCurrentState()
        observeHolidays(month)
    }

    private fun observeHolidays(month: YearMonth) {
        holidayJob?.cancel()
        holidayJob = scope.launch {
            holidays.observeBetween(month.atDay(1), month.atEndOfMonth())
                .catch {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "No pudimos cargar los feriados. Intentá nuevamente.",
                        )
                    }
                }
                .collect { rows ->
                    if (_uiState.value.holidayMonth == month) {
                        _uiState.update {
                            it.copy(holidays = rows, isLoading = false, errorMessage = null)
                        }
                    }
                }
        }
    }

    private fun observeNotes(shiftId: UUID) {
        noteJob?.cancel()
        noteJob = scope.launch {
            notes.observeForShift(shiftId)
                .catch {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "No pudimos cargar las notas. Intentá nuevamente.",
                        )
                    }
                }
                .collect { rows ->
                    if (_uiState.value.selectedShift?.id == shiftId) {
                        _uiState.update {
                            it.copy(notes = rows, isLoading = false, errorMessage = null)
                        }
                    }
                }
        }
    }

    private fun parseDates(text: String): Set<LocalDate>? = runCatching {
        text.split(',', ';', '\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(LocalDate::parse)
            .toCollection(linkedSetOf())
    }.getOrNull()

    private fun launchWrite(block: suspend () -> Unit) {
        if (_uiState.value.isSaving || !writeMutex.tryLock()) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null, infoMessage = null) }
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError(
                    if (error is LocalDataException) {
                        error.message ?: "No pudimos guardar el cambio."
                    } else {
                        error.message ?: "No pudimos guardar el cambio."
                    },
                )
            } finally {
                _uiState.update { it.copy(isSaving = false) }
                writeMutex.unlock()
            }
        }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message, infoMessage = null) }
    }

    private fun setSurface(surface: ExceptionsSurface, shiftId: UUID?) {
        _uiState.update { it.copy(surface = surface) }
        persist(
            ExceptionsPersistedState(
                surface = surface,
                holidayMonth = _uiState.value.holidayMonth,
                shiftId = shiftId,
            ),
        )
    }

    private fun persistCurrentState() {
        persist(
            ExceptionsPersistedState(
                surface = _uiState.value.surface,
                holidayMonth = _uiState.value.holidayMonth,
                shiftId = _uiState.value.selectedShift?.id,
            ),
        )
    }
}

class ExceptionsViewModel(
    holidays: HolidayRepository,
    notes: ShiftNoteRepository,
    shifts: ShiftRepository,
    clock: Clock,
    uuidProvider: () -> UUID,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val coordinator = ExceptionsCoordinator(
        holidays = holidays,
        notes = notes,
        shifts = shifts,
        clock = clock,
        uuidProvider = uuidProvider,
        scope = viewModelScope,
        initialState = ExceptionsPersistedState(
            surface = savedStateHandle.get<String>(SURFACE_KEY)
                ?.let { raw -> runCatching { ExceptionsSurface.valueOf(raw) }.getOrNull() }
                ?: ExceptionsSurface.NONE,
            holidayMonth = savedStateHandle.get<String>(HOLIDAY_MONTH_KEY)
                ?.let { raw -> runCatching { YearMonth.parse(raw) }.getOrNull() }
                ?: YearMonth.now(clock.withZone(AppDefaults.zoneId())),
            shiftId = savedStateHandle.get<String>(SHIFT_ID_KEY)
                ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() },
        ),
        persist = { state ->
            savedStateHandle[SURFACE_KEY] = state.surface.name
            savedStateHandle[HOLIDAY_MONTH_KEY] = state.holidayMonth.toString()
            if (state.shiftId == null) {
                savedStateHandle.remove<String>(SHIFT_ID_KEY)
            } else {
                savedStateHandle[SHIFT_ID_KEY] = state.shiftId.toString()
            }
        },
    )

    val uiState: StateFlow<ExceptionsUiState> = coordinator.uiState

    fun openHolidays(month: YearMonth) = coordinator.openHolidays(month)
    fun showPreviousHolidayMonth() = coordinator.previousHolidayMonth()
    fun showNextHolidayMonth() = coordinator.nextHolidayMonth()
    fun openNotes(shift: Shift) = coordinator.openNotes(shift)
    fun close() = coordinator.close()
    fun updateHolidayDraft(transform: (HolidayDraft) -> HolidayDraft) =
        coordinator.updateHolidayDraft(transform)
    fun editHoliday(holiday: Holiday) = coordinator.editHoliday(holiday)
    fun cancelHolidayEdit() = coordinator.cancelHolidayEdit()
    fun saveHolidays(policy: HolidayConflictPolicy? = null) = coordinator.saveHolidays(policy)
    fun cancelHolidayConflict() = coordinator.cancelHolidayConflict()
    fun deleteHoliday(id: UUID) = coordinator.deleteHoliday(id)
    fun updateNoteDraft(transform: (NoteDraft) -> NoteDraft) = coordinator.updateNoteDraft(transform)
    fun editNote(note: ShiftNote) = coordinator.editNote(note)
    fun cancelNoteEdit() = coordinator.cancelNoteEdit()
    fun saveNote() = coordinator.saveNote()
    fun deleteNote(id: UUID) = coordinator.deleteNote(id)
    fun retry() = coordinator.retry()
    fun clearMessage() = coordinator.clearMessage()

    class Factory(
        private val holidays: HolidayRepository,
        private val notes: ShiftNoteRepository,
        private val shifts: ShiftRepository,
        private val clock: Clock = Clock.system(AppDefaults.zoneId()),
        private val uuidProvider: () -> UUID = UUID::randomUUID,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(ExceptionsViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ExceptionsViewModel(
                holidays = holidays,
                notes = notes,
                shifts = shifts,
                clock = clock,
                uuidProvider = uuidProvider,
                savedStateHandle = extras.createSavedStateHandle(),
            ) as T
        }
    }

    private companion object {
        const val SURFACE_KEY = "exceptions.surface"
        const val HOLIDAY_MONTH_KEY = "exceptions.holidayMonth"
        const val SHIFT_ID_KEY = "exceptions.shiftId"
    }
}
