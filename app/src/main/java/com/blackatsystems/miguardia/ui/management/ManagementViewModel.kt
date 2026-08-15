package com.blackatsystems.miguardia.ui.management

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.repository.DuplicateObjectiveAbbreviationException
import com.blackatsystems.miguardia.core.domain.repository.DuplicateScheduleCombinationException
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.ScheduleCombinationRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.shift.OccupiedDatePolicy
import com.blackatsystems.miguardia.core.domain.shift.ShiftPlanningWarning
import com.blackatsystems.miguardia.core.domain.shift.areColorsTooSimilar
import com.blackatsystems.miguardia.core.domain.shift.buildShift
import com.blackatsystems.miguardia.core.domain.shift.duplicateShift
import com.blackatsystems.miguardia.core.domain.shift.editShift
import com.blackatsystems.miguardia.core.domain.shift.planShiftBatch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

fun interface UuidProvider {
    fun newUuid(): UUID
}

@OptIn(ExperimentalCoroutinesApi::class)
class ManagementViewModel(
    private val objectiveRepository: ObjectiveRepository,
    private val scheduleRepository: ScheduleCombinationRepository,
    private val shiftRepository: ShiftRepository,
    private val explicitDayStatusRepository: ExplicitDayStatusRepository,
    private val medicalLeaveRepository: MedicalLeaveRepository,
    private val clock: Clock,
    private val uuidProvider: UuidProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val zone = AppDefaults.zoneId()
    private val writeMutex = Mutex()
    private val _uiState = MutableStateFlow(
        ManagementUiState(
            surface = savedStateHandle.get<String>(SURFACE_KEY)
                ?.let(ManagementSurface::valueOf)
                ?: ManagementSurface.NONE,
        ),
    )
    val uiState: StateFlow<ManagementUiState> = _uiState

    init {
        viewModelScope.launch {
            objectiveRepository.observeAll()
                .flatMapLatest { objectives ->
                    if (objectives.isEmpty()) {
                        flowOf(objectives to emptyList<ScheduleOption>())
                    } else {
                        combine(objectives.map { objective ->
                            scheduleRepository.observeByObjective(objective.id)
                        }) { scheduleLists ->
                            objectives to objectives.flatMapIndexed { index, objective ->
                                scheduleLists[index].map { ScheduleOption(objective, it) }
                            }
                        }
                    }
                }
                .collect { (objectives, options) ->
                    _uiState.update { it.copy(objectives = objectives, scheduleOptions = options) }
                }
        }
        viewModelScope.launch {
            scheduleRepository.observeRecentlyUsed().collect { recent ->
                _uiState.update { it.copy(recent = recent) }
            }
        }
    }

    fun openSettings() {
        _uiState.update {
            it.copy(
                surface = ManagementSurface.SETTINGS,
                formReturnSurface = ManagementSurface.NONE,
                errorMessage = null,
            )
        }
        persistSurface(ManagementSurface.SETTINGS)
    }

    fun closeSurface() {
        setSurface(ManagementSurface.NONE)
        _uiState.update {
            it.copy(
                formReturnSurface = ManagementSurface.NONE,
                errorMessage = null,
                infoMessage = null,
                shiftDraft = null,
            )
        }
    }

    fun discardCurrentForm() {
        val state = _uiState.value
        val target = state.formReturnSurface
        _uiState.update {
            it.copy(
                surface = target,
                formReturnSurface = ManagementSurface.NONE,
                shiftDraft = if (state.surface == ManagementSurface.SHIFT_FORM) null else it.shiftDraft,
                errorMessage = null,
            )
        }
        persistSurface(target)
    }

    fun showHidden(show: Boolean) = _uiState.update { it.copy(showHidden = show) }

    fun openObjectiveForm(objective: Objective? = null) {
        val returnSurface = formReturnSurfaceFor(_uiState.value.surface)
        _uiState.update {
            it.copy(
                surface = ManagementSurface.OBJECTIVE_FORM,
                formReturnSurface = returnSurface,
                objectiveDraft = ObjectiveDraft(
                    editingId = objective?.id,
                    fullName = objective?.fullName.orEmpty(),
                    abbreviation = objective?.abbreviation.orEmpty(),
                    address = objective?.address.orEmpty(),
                    note = objective?.note.orEmpty(),
                ),
                errorMessage = null,
            )
        }
        persistSurface(ManagementSurface.OBJECTIVE_FORM)
    }

    fun updateObjectiveDraft(transform: (ObjectiveDraft) -> ObjectiveDraft) =
        _uiState.update { it.copy(objectiveDraft = transform(it.objectiveDraft), errorMessage = null) }

    fun saveObjective() {
        if (_uiState.value.isSaving) return
        val draft = _uiState.value.objectiveDraft
        val normalizedName = draft.fullName.trim()
        val abbreviation = draft.abbreviation.trim().uppercase(Locale.ROOT)
        if (normalizedName.isEmpty() || abbreviation.length !in 2..5) {
            _uiState.update { it.copy(errorMessage = "Ingresá un nombre y una abreviatura de 2 a 5 caracteres.") }
            return
        }
        viewModelScope.launch {
            saving {
                val now = clock.instant()
                val existing = draft.editingId?.let { objectiveRepository.getById(it) }
                val objective = Objective(
                    id = existing?.id ?: uuidProvider.newUuid(),
                    fullName = normalizedName,
                    abbreviation = abbreviation,
                    address = draft.address,
                    note = draft.note,
                    isActive = existing?.isActive ?: true,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
                if (existing == null) objectiveRepository.create(objective) else objectiveRepository.update(objective)
                val target = _uiState.value.formReturnSurface
                _uiState.update {
                    it.copy(
                        surface = target,
                        formReturnSurface = ManagementSurface.NONE,
                        infoMessage = "Objetivo guardado.",
                    )
                }
                persistSurface(target)
            }
        }
    }

    fun hideObjective(id: UUID) = viewModelScope.launch {
        saving {
            objectiveRepository.hide(id, clock.instant())
            _uiState.update { it.copy(infoMessage = "Objetivo ocultado.") }
        }
    }

    fun deleteObjective(id: UUID) = viewModelScope.launch {
        saving {
            objectiveRepository.delete(id)
            _uiState.update { it.copy(infoMessage = "Objetivo y sus horarios eliminados; las guardias históricas se conservaron.") }
        }
    }

    fun openScheduleForm(objectiveId: UUID, combination: ScheduleCombination? = null) {
        val returnSurface = formReturnSurfaceFor(_uiState.value.surface)
        _uiState.update {
            it.copy(
                surface = ManagementSurface.SCHEDULE_FORM,
                formReturnSurface = returnSurface,
                scheduleDraft = ScheduleDraft(
                    editingId = combination?.id,
                    objectiveId = objectiveId,
                    startTime = combination?.startTime?.toString() ?: "19:00",
                    endTime = combination?.endTime?.toString() ?: "07:00",
                    colorArgb = combination?.colorArgb ?: DEFAULT_SCHEDULE_COLOR,
                ),
                errorMessage = null,
            )
        }
        persistSurface(ManagementSurface.SCHEDULE_FORM)
    }

    fun updateScheduleDraft(transform: (ScheduleDraft) -> ScheduleDraft) =
        _uiState.update { it.copy(scheduleDraft = transform(it.scheduleDraft), errorMessage = null) }

    fun saveSchedule() {
        if (_uiState.value.isSaving) return
        val draft = _uiState.value.scheduleDraft
        val objectiveId = draft.objectiveId ?: return showError("Elegí un objetivo.")
        val start = parseTime(draft.startTime) ?: return showError("La hora inicial debe tener formato HH:mm.")
        val end = parseTime(draft.endTime) ?: return showError("La hora final debe tener formato HH:mm.")
        val similar = _uiState.value.scheduleOptions.any {
            it.objective.id == objectiveId && it.combination.id != draft.editingId &&
                areColorsTooSimilar(it.combination.colorArgb, draft.colorArgb)
        }
        viewModelScope.launch {
            saving {
                val now = clock.instant()
                val existing = draft.editingId?.let { scheduleRepository.getById(it) }
                val combination = ScheduleCombination(
                    id = existing?.id ?: uuidProvider.newUuid(),
                    objectiveId = objectiveId,
                    startTime = start,
                    endTime = end,
                    colorArgb = draft.colorArgb,
                    isActive = existing?.isActive ?: true,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
                if (existing == null) scheduleRepository.create(combination) else scheduleRepository.update(combination)
                val target = _uiState.value.formReturnSurface
                _uiState.update {
                    it.copy(
                        surface = target,
                        formReturnSurface = ManagementSurface.NONE,
                        shiftDraft = if (target == ManagementSurface.SHIFT_FORM) {
                            it.shiftDraft?.copy(combinationId = combination.id)
                        } else {
                            it.shiftDraft
                        },
                        infoMessage = if (similar) {
                            "Horario guardado. El color es parecido a otro; se mantiene porque lo confirmaste."
                        } else {
                            "Horario guardado."
                        },
                    )
                }
                persistSurface(target)
            }
        }
    }

    fun hideSchedule(id: UUID) = viewModelScope.launch {
        saving {
            scheduleRepository.hide(id, clock.instant())
            _uiState.update { it.copy(infoMessage = "Horario ocultado.") }
        }
    }

    fun deleteSchedule(id: UUID) = viewModelScope.launch {
        saving {
            scheduleRepository.delete(id)
            _uiState.update { it.copy(infoMessage = "Horario eliminado; las guardias históricas se conservaron.") }
        }
    }

    fun openAddShift(month: YearMonth, date: LocalDate? = null) {
        val initialDate = date?.takeIf { YearMonth.from(it) == month } ?: month.atDay(1)
        _uiState.update {
            it.copy(
                surface = ManagementSurface.SHIFT_FORM,
                formReturnSurface = ManagementSurface.NONE,
                shiftDraft = ShiftDraft(month = month, selectedDates = setOf(initialDate)),
                errorMessage = null,
            )
        }
        persistSurface(ManagementSurface.SHIFT_FORM)
    }

    fun openEditShift(shift: Shift) {
        _uiState.update {
            it.copy(
                surface = ManagementSurface.SHIFT_FORM,
                formReturnSurface = ManagementSurface.NONE,
                shiftDraft = ShiftDraft(
                    month = YearMonth.from(shift.localStartDate),
                    selectedDates = setOf(shift.localStartDate),
                    combinationId = shift.sourceScheduleCombinationId,
                    position = shift.position.orEmpty(),
                    editingShift = shift,
                ),
                errorMessage = null,
            )
        }
        persistSurface(ManagementSurface.SHIFT_FORM)
    }

    fun openDuplicateShift(shift: Shift) {
        _uiState.update {
            it.copy(
                surface = ManagementSurface.SHIFT_FORM,
                formReturnSurface = ManagementSurface.NONE,
                shiftDraft = ShiftDraft(
                    mode = ShiftEntryMode.MULTIPLE,
                    month = YearMonth.from(shift.localStartDate),
                    selectedDates = emptySet(),
                    position = shift.position.orEmpty(),
                    duplicateSource = shift,
                ),
                errorMessage = null,
            )
        }
        persistSurface(ManagementSurface.SHIFT_FORM)
    }

    fun updateShiftMode(mode: ShiftEntryMode) = updateShiftDraft {
        it.copy(mode = mode, selectedDates = it.selectedDates.take(if (mode == ShiftEntryMode.SINGLE) 1 else Int.MAX_VALUE).toSet())
    }

    fun toggleShiftDate(date: LocalDate) = updateShiftDraft { draft ->
        if (YearMonth.from(date) != draft.month) return@updateShiftDraft draft
        val selected = if (draft.mode == ShiftEntryMode.SINGLE) {
            setOf(date)
        } else if (date in draft.selectedDates) {
            draft.selectedDates - date
        } else {
            draft.selectedDates + date
        }
        draft.copy(selectedDates = selected, occupiedDates = emptySet(), warnings = emptyList(), pendingPolicy = null)
    }

    fun chooseShiftCombination(id: UUID) = updateShiftDraft { it.copy(combinationId = id) }
    fun updateShiftPosition(value: String) = updateShiftDraft { it.copy(position = value) }

    fun requestSaveShift(policy: OccupiedDatePolicy? = null, warningsConfirmed: Boolean = false) {
        if (_uiState.value.isSaving) return
        val draft = _uiState.value.shiftDraft ?: return
        if (
            draft.editingShift != null &&
            policy != null &&
            policy !in setOf(OccupiedDatePolicy.ADD_SECOND_SHIFT, OccupiedDatePolicy.CANCEL)
        ) {
            return showError("Al editar una guardia podés conservar la existente como segunda guardia o cancelar.")
        }
        if (draft.selectedDates.isEmpty()) return showError("Elegí al menos una fecha.")
        viewModelScope.launch {
            saving(closeError = false) {
                val dates = draft.selectedDates.sorted()
                val first = dates.first()
                val last = dates.last()
                val existing = shiftRepository.observeStartingBetween(first.minusDays(2), last.plusDays(2)).first()
                val explicit = explicitDayStatusRepository.observeBetween(first, last).first()
                    .filter { it.date in draft.selectedDates }
                val medical = medicalLeaveRepository.observeIntersecting(first, last).first()
                    .filter { leave -> draft.selectedDates.any { it in leave.startDate..leave.endDateInclusive } }
                val now = clock.instant()
                val candidates = when {
                    draft.duplicateSource != null -> dates.map { date ->
                        duplicateShift(draft.duplicateSource, uuidProvider.newUuid(), date, now).copy(
                            position = draft.position.trim().takeIf(String::isNotEmpty),
                        )
                    }
                    else -> {
                        val option = _uiState.value.scheduleOptions.firstOrNull {
                            it.combination.id == draft.combinationId && it.objective.isActive && it.combination.isActive
                        } ?: throw InvalidLocalDataException("Elegí un objetivo y horario activos.")
                        dates.map { date ->
                            if (draft.editingShift != null) {
                                editShift(draft.editingShift, date, option.objective, option.combination, draft.position, now)
                            } else {
                                buildShift(
                                    id = uuidProvider.newUuid(),
                                    date = date,
                                    objective = option.objective,
                                    combination = option.combination,
                                    position = draft.position,
                                    timestamp = now,
                                    zoneId = zone,
                                )
                            }
                        }
                    }
                }
                val occupied = existing
                    .filter { it.id != draft.editingShift?.id && it.localStartDate in draft.selectedDates }
                    .mapTo(linkedSetOf()) { it.localStartDate }
                if (occupied.isNotEmpty() && policy == null) {
                    _uiState.update {
                        it.copy(
                            shiftDraft = draft.copy(occupiedDates = occupied),
                            isSaving = false,
                        )
                    }
                    return@saving
                }
                val chosenPolicy = policy ?: OccupiedDatePolicy.ADD_SECOND_SHIFT
                val plan = planShiftBatch(
                    selectedDates = draft.selectedDates,
                    existingShifts = existing,
                    candidates = candidates,
                    policy = chosenPolicy,
                    editingShiftId = draft.editingShift?.id,
                )
                if (chosenPolicy == OccupiedDatePolicy.CANCEL) {
                    _uiState.update { it.copy(isSaving = false, shiftDraft = draft.copy(occupiedDates = emptySet())) }
                    return@saving
                }
                val coexistence = buildList {
                    explicit.forEach { status ->
                        add(
                            if (status.type == ExplicitDayStatusType.DAY_OFF) {
                                "${status.date.numericDisplayName()}: ya tiene un franco explícito. No se modificará."
                            } else {
                                "${status.date.numericDisplayName()}: ya está marcada sin definir. No se modificará."
                            },
                        )
                    }
                    medical.forEach { leave ->
                        add(
                            "Existe una carpeta médica entre ${leave.startDate.numericDisplayName()} y " +
                                "${leave.endDateInclusive.numericDisplayName()}. No se modificará.",
                        )
                    }
                }
                val warningTexts = plan.warnings.map(::warningText) + coexistence
                if (warningTexts.isNotEmpty() && !warningsConfirmed) {
                    _uiState.update {
                        it.copy(
                            shiftDraft = draft.copy(
                                pendingPolicy = chosenPolicy,
                                occupiedDates = emptySet(),
                                warnings = warningTexts,
                                coexistenceWarnings = coexistence,
                            ),
                            isSaving = false,
                        )
                    }
                    return@saving
                }
                if (draft.editingShift != null) {
                    shiftRepository.update(candidates.single())
                } else {
                    shiftRepository.applyBatch(plan.mutation)
                }
                _uiState.update {
                    it.copy(
                        surface = ManagementSurface.NONE,
                        shiftDraft = null,
                        infoMessage = if (plan.omittedDates.isEmpty()) {
                            "Guardias guardadas."
                        } else {
                            "Guardias guardadas; se conservaron ${plan.omittedDates.size} fechas ocupadas."
                        },
                    )
                }
                persistSurface(ManagementSurface.NONE)
            }
        }
    }

    fun confirmShiftWarnings() {
        val policy = _uiState.value.shiftDraft?.pendingPolicy ?: OccupiedDatePolicy.ADD_SECOND_SHIFT
        requestSaveShift(policy = policy, warningsConfirmed = true)
    }

    fun dismissShiftWarnings() = updateShiftDraft {
        it.copy(
            pendingPolicy = null,
            warnings = emptyList(),
            coexistenceWarnings = emptyList(),
        )
    }

    fun deleteShift(id: UUID) = viewModelScope.launch {
        saving {
            shiftRepository.delete(id)
            _uiState.update { it.copy(infoMessage = "Guardia eliminada.") }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(infoMessage = null, errorMessage = null) }

    private fun updateShiftDraft(transform: (ShiftDraft) -> ShiftDraft) {
        _uiState.update { state ->
            state.shiftDraft?.let { state.copy(shiftDraft = transform(it), errorMessage = null) } ?: state
        }
    }

    private suspend fun saving(closeError: Boolean = true, block: suspend () -> Unit) {
        if (!writeMutex.tryLock()) return
        _uiState.update { it.copy(isSaving = true, errorMessage = if (closeError) null else it.errorMessage) }
        try {
            block()
        } catch (error: DuplicateObjectiveAbbreviationException) {
            showError(error.message ?: "La abreviatura ya está en uso.")
        } catch (error: DuplicateScheduleCombinationException) {
            showError(error.message ?: "Ese horario ya existe.")
        } catch (error: InvalidLocalDataException) {
            showError(error.message ?: "Los datos no son válidos.")
        } catch (_: Exception) {
            showError("No pudimos guardar los cambios. Revisá los datos e intentá nuevamente.")
        } finally {
            _uiState.update { it.copy(isSaving = false) }
            writeMutex.unlock()
        }
    }

    private fun warningText(warning: ShiftPlanningWarning): String = when (warning) {
        is ShiftPlanningWarning.SameDate ->
            "${warning.first.localStartDate.numericDisplayName()}: ya habrá más de una guardia (${warning.first.timeRange()} y ${warning.second.timeRange()})."
        is ShiftPlanningWarning.Overlap ->
            "Las guardias del ${warning.first.localStartDate.numericDisplayName()} ${warning.first.timeRange()} y del ${warning.second.localStartDate.numericDisplayName()} ${warning.second.timeRange()} se superponen."
        is ShiftPlanningWarning.ShortRest -> {
            val hours = warning.actualRest.toMinutes().coerceAtLeast(0) / 60
            val minutes = warning.actualRest.toMinutes().coerceAtLeast(0) % 60
            "Entre ${warning.first.localStartDate.numericDisplayName()} ${warning.first.timeRange()} y ${warning.second.localStartDate.numericDisplayName()} ${warning.second.timeRange()} hay ${hours} h ${minutes} min de descanso."
        }
    }

    private fun parseTime(value: String): LocalTime? = try {
        LocalTime.parse(value.trim())
    } catch (_: Exception) {
        null
    }

    private fun showError(message: String) = _uiState.update { it.copy(errorMessage = message, isSaving = false) }

    private fun Shift.timeRange(): String =
        "${startTimeSnapshot.format(TIME_FORMATTER)}–${endTimeSnapshot.format(TIME_FORMATTER)}"

    private fun LocalDate.numericDisplayName(): String = format(DATE_FORMATTER)

    private fun setSurface(surface: ManagementSurface) {
        _uiState.update { it.copy(surface = surface, errorMessage = null) }
        persistSurface(surface)
    }

    private fun persistSurface(surface: ManagementSurface) {
        savedStateHandle[SURFACE_KEY] = surface.name
    }

    private fun formReturnSurfaceFor(current: ManagementSurface): ManagementSurface = when (current) {
        ManagementSurface.SHIFT_FORM -> ManagementSurface.SHIFT_FORM
        else -> ManagementSurface.SETTINGS
    }

    class Factory(
        private val objectiveRepository: ObjectiveRepository,
        private val scheduleRepository: ScheduleCombinationRepository,
        private val shiftRepository: ShiftRepository,
        private val explicitDayStatusRepository: ExplicitDayStatusRepository,
        private val medicalLeaveRepository: MedicalLeaveRepository,
        private val clock: Clock = Clock.system(AppDefaults.zoneId()),
        private val uuidProvider: UuidProvider = UuidProvider(UUID::randomUUID),
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(ManagementViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ManagementViewModel(
                objectiveRepository,
                scheduleRepository,
                shiftRepository,
                explicitDayStatusRepository,
                medicalLeaveRepository,
                clock,
                uuidProvider,
                extras.createSavedStateHandle(),
            ) as T
        }
    }

    companion object {
        private val DEFAULT_SCHEDULE_COLOR = 0xFF1565C0.toInt()
        private const val SURFACE_KEY = "management.surface"
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
    }
}
