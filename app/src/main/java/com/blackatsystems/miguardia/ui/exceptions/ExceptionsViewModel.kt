package com.blackatsystems.miguardia.ui.exceptions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.FormalShiftChange
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.HolidayBatchMutation
import com.blackatsystems.miguardia.core.domain.model.HolidayConflictPolicy
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNovelty
import com.blackatsystems.miguardia.core.domain.model.ShiftNoveltyMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftNoveltyType
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.toOperationalSnapshot
import com.blackatsystems.miguardia.core.domain.model.withOperationalSnapshot
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.ScheduleCombinationRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoteRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoveltyRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.shift.buildShift
import com.blackatsystems.miguardia.core.domain.shift.editShift
import com.blackatsystems.miguardia.core.domain.shift.evaluateShiftWarnings
import com.blackatsystems.miguardia.core.domain.shift.ShiftPlanningWarning
import com.blackatsystems.miguardia.ui.management.UuidProvider
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

@OptIn(ExperimentalCoroutinesApi::class)
class ExceptionsViewModel(
    private val holidays: HolidayRepository,
    private val notes: ShiftNoteRepository,
    private val novelties: ShiftNoveltyRepository,
    private val shifts: ShiftRepository,
    private val objectives: ObjectiveRepository,
    private val schedules: ScheduleCombinationRepository,
    private val clock: Clock,
    private val uuidProvider: UuidProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val zone = AppDefaults.zoneId()
    private val writeMutex = Mutex()
    private val initialMonth = savedStateHandle.get<String>(HOLIDAY_MONTH_KEY)?.let(YearMonth::parse)
        ?: YearMonth.now(clock.withZone(zone))
    private val _uiState = MutableStateFlow(
        ExceptionsUiState(
            holidayMonth = initialMonth,
            surface = savedStateHandle.get<String>(SURFACE_KEY)
                ?.let(ExceptionsSurface::valueOf)
                ?: ExceptionsSurface.NONE,
        ),
    )
    val uiState: StateFlow<ExceptionsUiState> = _uiState
    private var holidayJob: Job? = null
    private var shiftJob: Job? = null

    init {
        observeHolidayMonth(initialMonth)
        viewModelScope.launch {
            objectives.observeAll().flatMapLatest { objectiveRows ->
                if (objectiveRows.isEmpty()) flowOf(emptyList())
                else combine(objectiveRows.map { objective -> schedules.observeByObjective(objective.id) }) { lists ->
                    objectiveRows.flatMapIndexed { index, objective ->
                        lists[index].map { ExceptionScheduleOption(objective, it) }
                    }
                }
            }.collect { options -> _uiState.update { it.copy(scheduleOptions = options) } }
        }
        savedStateHandle.get<String>(SHIFT_ID_KEY)?.let { raw ->
            viewModelScope.launch { shifts.getById(UUID.fromString(raw))?.let(::openShift) }
        }
    }

    fun openHolidays(month: YearMonth = _uiState.value.holidayMonth) {
        _uiState.update {
            it.copy(
                surface = ExceptionsSurface.HOLIDAYS,
                holidayMonth = month,
                errorMessage = null,
                infoMessage = null,
            )
        }
        savedStateHandle[SURFACE_KEY] = ExceptionsSurface.HOLIDAYS.name
        setHolidayMonth(month)
    }

    fun previousHolidayMonth() = setHolidayMonth(_uiState.value.holidayMonth.minusMonths(1))
    fun nextHolidayMonth() = setHolidayMonth(_uiState.value.holidayMonth.plusMonths(1))

    fun close() {
        shiftJob?.cancel()
        _uiState.update {
            it.copy(
                surface = ExceptionsSurface.NONE,
                selectedShift = null,
                holidayDraft = HolidayDraft(),
                noteDraft = NoteDraft(),
                noveltyDraft = NoveltyDraft(),
                planningWarnings = emptyList(),
                pendingPlanning = null,
                errorMessage = null,
                infoMessage = null,
            )
        }
        savedStateHandle[SURFACE_KEY] = ExceptionsSurface.NONE.name
        savedStateHandle.remove<String>(SHIFT_ID_KEY)
    }

    fun openShift(shift: Shift) {
        _uiState.update {
            it.copy(
                surface = ExceptionsSurface.SHIFT,
                selectedShift = shift,
                notes = emptyList(),
                novelties = emptyList(),
                formalChange = null,
                noteDraft = NoteDraft(),
                noveltyDraft = NoveltyDraft(),
                planningWarnings = emptyList(),
                pendingPlanning = null,
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
            )
        }
        savedStateHandle[SURFACE_KEY] = ExceptionsSurface.SHIFT.name
        savedStateHandle[SHIFT_ID_KEY] = shift.id.toString()
        observeShift(shift.id)
    }

    fun updateHolidayDraft(transform: (HolidayDraft) -> HolidayDraft) =
        _uiState.update { it.copy(holidayDraft = transform(it.holidayDraft), errorMessage = null) }

    fun editHoliday(holiday: Holiday) = _uiState.update {
        it.copy(holidayDraft = HolidayDraft(holiday.id, holiday.date.toString(), holiday.name.orEmpty()))
    }

    fun saveHolidays(policy: HolidayConflictPolicy? = null) {
        val draft = _uiState.value.holidayDraft
        val dates = parseDates(draft.datesText) ?: return showError("Ingresá fechas válidas con formato AAAA-MM-DD, separadas por coma.")
        if (dates.isEmpty()) return showError("Ingresá al menos una fecha.")
        if (draft.editingId != null && dates.size != 1) {
            return showError("La edición de un feriado admite una sola fecha.")
        }
        launchWrite {
            val now = clock.instant()
            val editing = draft.editingId?.let { holidays.getById(it) }
            val existingByDate = dates.mapNotNull { date -> holidays.getByDate(date)?.let { date to it } }.toMap()
            val conflicts = existingByDate.keys.filterTo(linkedSetOf()) { date ->
                existingByDate[date]?.id != draft.editingId
            }
            if (conflicts.isNotEmpty() && policy == null) {
                _uiState.update { it.copy(holidayDraft = draft.copy(conflictDates = conflicts), isSaving = false) }
                return@launchWrite
            }
            val incoming = dates.map { date ->
                Holiday(
                    id = editing?.id ?: uuidProvider.newUuid(),
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
            _uiState.update { it.copy(holidayDraft = HolidayDraft(), infoMessage = "Feriados guardados.") }
        }
    }

    fun cancelHolidayConflict() = _uiState.update {
        it.copy(holidayDraft = it.holidayDraft.copy(conflictDates = emptySet(), pendingPolicy = null))
    }

    fun deleteHoliday(id: UUID) = launchWrite {
        holidays.delete(id)
        _uiState.update { it.copy(infoMessage = "Feriado eliminado.") }
    }

    fun updateNoteDraft(transform: (NoteDraft) -> NoteDraft) =
        _uiState.update { it.copy(noteDraft = transform(it.noteDraft), errorMessage = null) }

    fun editNote(note: ShiftNote) = _uiState.update { it.copy(noteDraft = NoteDraft(note.id, note.body)) }

    fun saveNote() {
        val shift = _uiState.value.selectedShift ?: return
        val draft = _uiState.value.noteDraft
        launchWrite {
            val now = clock.instant()
            val existing = draft.editingId?.let { notes.getById(it) }
            val note = ShiftNote(existing?.id ?: uuidProvider.newUuid(), shift.id, draft.body, existing?.createdAt ?: now, now)
            if (existing == null) notes.insert(note) else notes.update(note)
            _uiState.update { it.copy(noteDraft = NoteDraft(), infoMessage = "Nota guardada.") }
        }
    }

    fun deleteNote(id: UUID) = launchWrite { notes.delete(id) }

    fun updateNoveltyDraft(transform: (NoveltyDraft) -> NoveltyDraft) =
        _uiState.update { it.copy(noveltyDraft = transform(it.noveltyDraft), errorMessage = null) }

    fun editNovelty(novelty: ShiftNovelty) = _uiState.update {
        it.copy(noveltyDraft = NoveltyDraft(novelty.id, novelty.type, novelty.description.orEmpty()))
    }

    fun saveInformativeNovelty() {
        val shift = _uiState.value.selectedShift ?: return
        val draft = _uiState.value.noveltyDraft
        launchWrite {
            val now = clock.instant()
            val existing = draft.editingId?.let { novelties.getById(it) }
            val novelty = ShiftNovelty(
                id = existing?.id ?: uuidProvider.newUuid(),
                shiftId = shift.id,
                type = draft.type,
                description = draft.description,
                relatedShiftId = null,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            novelties.applyMutation(ShiftNoveltyMutation.SaveInformative(novelty))
            _uiState.update { it.copy(noveltyDraft = NoveltyDraft(), infoMessage = "Novedad guardada. No modifica las horas.") }
        }
    }

    fun deleteInformativeNovelty(id: UUID) = launchWrite {
        novelties.applyMutation(ShiftNoveltyMutation.DeleteInformative(id))
    }

    fun changeStatus(status: ShiftStatus, description: String = "") {
        val shift = _uiState.value.selectedShift ?: return
        launchWrite {
            val now = clock.instant()
            val updated = shift.copy(status = status, updatedAt = now)
            val novelty = when (status) {
                ShiftStatus.PLANNED -> null
                ShiftStatus.ABSENT, ShiftStatus.CANCELLED -> ShiftNovelty(
                    id = uuidProvider.newUuid(),
                    shiftId = shift.id,
                    type = if (status == ShiftStatus.ABSENT) ShiftNoveltyType.ABSENCE else ShiftNoveltyType.CANCELLATION,
                    description = description,
                    relatedShiftId = null,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            novelties.applyMutation(ShiftNoveltyMutation.ChangeStatus(updated, novelty))
            refreshSelectedShift()
            _uiState.update { it.copy(infoMessage = "Estado de la guardia actualizado.") }
        }
    }

    fun applyFormalChange(combinationId: UUID, description: String) =
        applyFormalChange(combinationId, description, warningsConfirmed = false)

    private fun applyFormalChange(
        combinationId: UUID,
        description: String,
        warningsConfirmed: Boolean,
    ) {
        val shift = _uiState.value.selectedShift ?: return
        val option = _uiState.value.scheduleOptions.firstOrNull {
            it.combination.id == combinationId && it.objective.isActive && it.combination.isActive
        } ?: return showError("Elegí un objetivo y horario activos.")
        launchWrite {
            val now = clock.instant()
            val updated = editShift(shift, shift.localStartDate, option.objective, option.combination, shift.position, now)
            val existingShifts = shifts.observeStartingBetween(
                updated.localStartDate.minusDays(2),
                updated.localStartDate.plusDays(2),
            ).first().filterNot { it.id == shift.id }
            val warningTexts = evaluateShiftWarnings(existingShifts, listOf(updated)).map(::warningText)
            if (warningTexts.isNotEmpty() && !warningsConfirmed) {
                requestPlanningConfirmation(
                    PendingExceptionPlanning(
                        operation = ExceptionPlanningOperation.FORMAL_CHANGE,
                        combinationId = combinationId,
                        description = description,
                    ),
                    warningTexts,
                )
                return@launchWrite
            }
            val existing = _uiState.value.formalChange
            val original = existing?.original ?: shift.toOperationalSnapshot()
            val change = FormalShiftChange(
                id = existing?.id ?: uuidProvider.newUuid(),
                shiftId = shift.id,
                scheduleChanged = shift.startAt != updated.startAt || shift.endAt != updated.endAt,
                objectiveChanged = shift.objectiveNameSnapshot != updated.objectiveNameSnapshot ||
                    shift.objectiveAbbreviationSnapshot != updated.objectiveAbbreviationSnapshot,
                description = description,
                original = original,
                final = updated.toOperationalSnapshot(),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            novelties.applyMutation(ShiftNoveltyMutation.ApplyFormalChange(updated, change))
            refreshSelectedShift()
            _uiState.update { it.copy(infoMessage = "Cambio formal guardado.") }
        }
    }

    fun restoreOriginalPlan() {
        val shift = _uiState.value.selectedShift ?: return
        val formal = _uiState.value.formalChange ?: return
        launchWrite {
            val restored = shift.withOperationalSnapshot(formal.original, clock.instant())
            novelties.applyMutation(ShiftNoveltyMutation.RestoreOriginalPlan(restored, formal.final))
            refreshSelectedShift()
            _uiState.update { it.copy(infoMessage = "Plan original restaurado.") }
        }
    }

    fun createSecondShift(combinationId: UUID, description: String) =
        createSecondShift(combinationId, description, warningsConfirmed = false)

    private fun createSecondShift(
        combinationId: UUID,
        description: String,
        warningsConfirmed: Boolean,
    ) {
        val origin = _uiState.value.selectedShift ?: return
        val option = _uiState.value.scheduleOptions.firstOrNull {
            it.combination.id == combinationId && it.objective.isActive && it.combination.isActive
        } ?: return showError("Elegí un objetivo y horario activos.")
        launchWrite {
            val now = clock.instant()
            val second = buildShift(uuidProvider.newUuid(), origin.localStartDate, option.objective, option.combination, null, now, origin.zoneId)
            val existingShifts = shifts.observeStartingBetween(
                second.localStartDate.minusDays(2),
                second.localStartDate.plusDays(2),
            ).first()
            val warningTexts = evaluateShiftWarnings(existingShifts, listOf(second)).map(::warningText)
            if (warningTexts.isNotEmpty() && !warningsConfirmed) {
                requestPlanningConfirmation(
                    PendingExceptionPlanning(
                        operation = ExceptionPlanningOperation.SECOND_SHIFT,
                        combinationId = combinationId,
                        description = description,
                    ),
                    warningTexts,
                )
                return@launchWrite
            }
            val novelty = ShiftNovelty(uuidProvider.newUuid(), origin.id, ShiftNoveltyType.SECOND_SHIFT, description, second.id, now, now)
            novelties.applyMutation(ShiftNoveltyMutation.CreateSecondShift(novelty, second))
            _uiState.update { it.copy(infoMessage = "Segunda guardia creada. Se computa como una guardia independiente.") }
        }
    }

    fun deleteSecondShift(novelty: ShiftNovelty) = launchWrite {
        val secondId = novelty.relatedShiftId ?: return@launchWrite
        novelties.applyMutation(ShiftNoveltyMutation.DeleteSecondShift(novelty.id, secondId))
        _uiState.update { it.copy(infoMessage = "Segunda guardia eliminada.") }
    }

    fun confirmPlanningWarnings() {
        val pending = _uiState.value.pendingPlanning ?: return
        _uiState.update { it.copy(planningWarnings = emptyList(), pendingPlanning = null) }
        when (pending.operation) {
            ExceptionPlanningOperation.FORMAL_CHANGE ->
                applyFormalChange(pending.combinationId, pending.description, warningsConfirmed = true)
            ExceptionPlanningOperation.SECOND_SHIFT ->
                createSecondShift(pending.combinationId, pending.description, warningsConfirmed = true)
        }
    }

    fun dismissPlanningWarnings() = _uiState.update {
        it.copy(planningWarnings = emptyList(), pendingPlanning = null)
    }

    fun clearMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    fun retry() {
        _uiState.update { it.copy(errorMessage = null, isLoading = true) }
        when (_uiState.value.surface) {
            ExceptionsSurface.HOLIDAYS -> observeHolidayMonth(_uiState.value.holidayMonth)
            ExceptionsSurface.SHIFT -> _uiState.value.selectedShift?.id?.let(::observeShift)
            ExceptionsSurface.NONE -> Unit
        }
    }

    private fun observeHolidayMonth(month: YearMonth) {
        holidayJob?.cancel()
        holidayJob = viewModelScope.launch {
            holidays.observeBetween(month.atDay(1), month.atEndOfMonth())
                .catch { showError("No pudimos cargar los feriados. Intentá nuevamente.") }
                .collect { rows ->
                _uiState.update { it.copy(holidays = rows, isLoading = false) }
            }
        }
    }

    private fun setHolidayMonth(month: YearMonth) {
        savedStateHandle[HOLIDAY_MONTH_KEY] = month.toString()
        _uiState.update { it.copy(holidayMonth = month, isLoading = true) }
        observeHolidayMonth(month)
    }

    private fun observeShift(id: UUID) {
        shiftJob?.cancel()
        shiftJob = viewModelScope.launch {
            combine(notes.observeForShift(id), novelties.observeForShift(id), novelties.observeFormalChange(id)) { noteRows, noveltyRows, formal ->
                Triple(noteRows, noveltyRows, formal)
            }.catch { showError("No pudimos cargar las notas y novedades. Intentá nuevamente.") }
                .collect { (noteRows, noveltyRows, formal) ->
                _uiState.update { it.copy(notes = noteRows, novelties = noveltyRows, formalChange = formal, isLoading = false) }
            }
        }
    }

    private suspend fun refreshSelectedShift() {
        val id = _uiState.value.selectedShift?.id ?: return
        val refreshed = shifts.getById(id) ?: return close()
        _uiState.update { it.copy(selectedShift = refreshed) }
    }

    private fun parseDates(text: String): Set<LocalDate>? = runCatching {
        text.split(',', ';', '\n').map(String::trim).filter(String::isNotEmpty).map(LocalDate::parse).toCollection(linkedSetOf())
    }.getOrNull()

    private fun launchWrite(block: suspend () -> Unit) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            if (!writeMutex.tryLock()) return@launch
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                block()
            } catch (error: Exception) {
                showError(error.message ?: "No pudimos guardar los cambios.")
            } finally {
                _uiState.update { it.copy(isSaving = false) }
                writeMutex.unlock()
            }
        }
    }

    private fun showError(message: String) = _uiState.update { it.copy(errorMessage = message, isSaving = false) }

    private fun requestPlanningConfirmation(
        pending: PendingExceptionPlanning,
        warnings: List<String>,
    ) = _uiState.update {
        it.copy(
            planningWarnings = warnings,
            pendingPlanning = pending,
            isSaving = false,
        )
    }

    private fun warningText(warning: ShiftPlanningWarning): String = when (warning) {
        is ShiftPlanningWarning.SameDate ->
            "${warning.first.localStartDate}: ya habrá más de una guardia (${warning.first.timeRange()} y ${warning.second.timeRange()})."
        is ShiftPlanningWarning.Overlap ->
            "Las guardias del ${warning.first.localStartDate} ${warning.first.timeRange()} y del ${warning.second.localStartDate} ${warning.second.timeRange()} se superponen."
        is ShiftPlanningWarning.ShortRest -> {
            val totalMinutes = warning.actualRest.toMinutes().coerceAtLeast(0)
            "Entre ${warning.first.localStartDate} ${warning.first.timeRange()} y ${warning.second.localStartDate} ${warning.second.timeRange()} hay ${totalMinutes / 60} h ${totalMinutes % 60} min de descanso."
        }
    }

    private fun Shift.timeRange(): String = "$startTimeSnapshot–$endTimeSnapshot"

    class Factory(
        private val holidays: HolidayRepository,
        private val notes: ShiftNoteRepository,
        private val novelties: ShiftNoveltyRepository,
        private val shifts: ShiftRepository,
        private val objectives: ObjectiveRepository,
        private val schedules: ScheduleCombinationRepository,
        private val clock: Clock = Clock.system(AppDefaults.zoneId()),
        private val uuidProvider: UuidProvider = UuidProvider(UUID::randomUUID),
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(ExceptionsViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ExceptionsViewModel(holidays, notes, novelties, shifts, objectives, schedules, clock, uuidProvider, extras.createSavedStateHandle()) as T
        }
    }

    private companion object {
        const val SURFACE_KEY = "exceptions.surface"
        const val HOLIDAY_MONTH_KEY = "exceptions.holidayMonth"
        const val SHIFT_ID_KEY = "exceptions.shiftId"
    }
}
