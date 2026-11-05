package com.blackatsystems.miguardia.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import android.net.Uri
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.model.CancellationActor
import com.blackatsystems.miguardia.core.domain.model.CancellationEvidence
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftSituation
import com.blackatsystems.miguardia.core.domain.model.ShiftSituationMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftSituationRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAction
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAvailability
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchRepository
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import com.blackatsystems.miguardia.backup.LocalDataMutationGate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.UUID
import java.security.MessageDigest
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
    private val shiftSituationRepository: ShiftSituationRepository? = null,
    private val evidenceFileStore: SchedulePhotoFileStore? = null,
    private val mutationGate: LocalDataMutationGate? = null,
    private val calendarBatchRepository: CalendarBatchRepository? = null,
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
    private val initialSelectionPurpose = savedStateHandle.get<String>(SELECTION_PURPOSE_KEY)
        .let(::calendarSelectionPurposeFromSaved)
    private val initialDetailDate = savedStateHandle.get<String>(DETAIL_DATE_KEY)
        ?: savedStateHandle.get<String>(LEGACY_SELECTED_DATE_KEY)
    private val parsedInitialDetailDate = initialDetailDate
        ?.let(LocalDate::parse)
    private val initialEditSelectedDates = savedStateHandle.get<ArrayList<String>>(EDIT_SELECTED_DATES_KEY)
        .orEmpty()
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .filterTo(linkedSetOf()) { YearMonth.from(it) == initialMonth }
    private val initialEditSelectionConfirmed = savedStateHandle.get<Boolean>(EDIT_SELECTION_CONFIRMED_KEY) == true &&
        initialInteractionMode == CalendarInteractionMode.EDIT &&
        initialEditSelectedDates.isNotEmpty()

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(
        CalendarUiState(
            visibleMonth = initialMonth,
            referenceInstant = clock.instant(),
            detailDate = parsedInitialDetailDate
                ?.takeIf { initialInteractionMode == CalendarInteractionMode.VIEW && YearMonth.from(it) == initialMonth },
            editSelectedDates = initialEditSelectedDates.takeIf {
                initialInteractionMode == CalendarInteractionMode.EDIT
            }.orEmpty(),
            editSelectionConfirmed = initialEditSelectionConfirmed,
            interactionMode = initialInteractionMode,
            selectionPurpose = if (initialInteractionMode == CalendarInteractionMode.EDIT) {
                initialSelectionPurpose ?: CalendarSelectionPurpose.GENERAL
            } else null,
            calendarCollapsed = savedStateHandle.get<Boolean>(CALENDAR_COLLAPSED_KEY) == true,
            lastOpenedDate = savedStateHandle.get<String>(LAST_OPENED_DATE_KEY)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            hasAnyShiftsLoaded = false,
        ),
    )
    val uiState: kotlinx.coroutines.flow.StateFlow<CalendarUiState> = _uiState

    private var observationJob: Job? = null
    private var boundaryJob: Job? = null
    private var shiftPresenceJob: Job? = null

    init {
        observeGlobalShiftPresence()
        observeMonth(initialMonth)
        if (shiftSituationRepository != null && evidenceFileStore != null) {
            viewModelScope.launch {
                val reconcile: suspend () -> Unit = {
                    evidenceFileStore.reconcileCancellationEvidence(
                        shiftSituationRepository.evidenceStorageKeys(),
                    )
                }
                mutationGate?.withExclusiveMutation { reconcile() } ?: reconcile()
            }
        }
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
            savedStateHandle[LAST_OPENED_DATE_KEY] = date.toString()
            _uiState.update { it.copy(detailDate = date, lastOpenedDate = date) }
            inspectSituationsFor(date)
        }
    }

    fun openDate(date: LocalDate) {
        val month = YearMonth.from(date)
        if (month != _uiState.value.visibleMonth) setVisibleMonth(month)
        savedStateHandle[INTERACTION_MODE_KEY] = CalendarInteractionMode.VIEW.name
        savedStateHandle.remove<String>(SELECTION_PURPOSE_KEY)
        savedStateHandle[DETAIL_DATE_KEY] = date.toString()
        savedStateHandle[LAST_OPENED_DATE_KEY] = date.toString()
        savedStateHandle.remove<ArrayList<String>>(EDIT_SELECTED_DATES_KEY)
        savedStateHandle.remove<Boolean>(EDIT_SELECTION_CONFIRMED_KEY)
        _uiState.update {
            it.copy(
                detailDate = date,
                editSelectedDates = emptySet(),
                editSelectionConfirmed = false,
                interactionMode = CalendarInteractionMode.VIEW,
                selectionPurpose = null,
                lastOpenedDate = date,
            )
        }
        inspectSituationsFor(date)
    }

    fun clearSelectedDate() {
        savedStateHandle.remove<String>(DETAIL_DATE_KEY)
        savedStateHandle.remove<String>(LEGACY_SELECTED_DATE_KEY)
        _uiState.update { it.copy(detailDate = null, stage2Expectations = emptyMap()) }
    }

    fun enterEditMode(
        selectedDate: LocalDate? = null,
        purpose: CalendarSelectionPurpose = CalendarSelectionPurpose.GENERAL,
    ) {
        savedStateHandle[INTERACTION_MODE_KEY] = CalendarInteractionMode.EDIT.name
        savedStateHandle[SELECTION_PURPOSE_KEY] = purpose.name
        savedStateHandle.remove<String>(DETAIL_DATE_KEY)
        savedStateHandle.remove<String>(LEGACY_SELECTED_DATE_KEY)
        persistEditSelection(selectedDate?.let(::setOf).orEmpty())
        savedStateHandle.remove<Boolean>(EDIT_SELECTION_CONFIRMED_KEY)
        _uiState.update { it.enterEditing(selectedDate, purpose) }
    }

    fun enterManualLoadSelection(selectedDate: LocalDate? = null) =
        enterEditMode(selectedDate, CalendarSelectionPurpose.MANUAL_LOAD)

    fun enterHolidaySelection(selectedDate: LocalDate? = null) =
        enterEditMode(selectedDate, CalendarSelectionPurpose.HOLIDAY)

    fun setCalendarCollapsed(collapsed: Boolean) {
        savedStateHandle[CALENDAR_COLLAPSED_KEY] = collapsed
        _uiState.update { it.copy(calendarCollapsed = collapsed) }
    }

    fun setSelectionPurpose(purpose: CalendarSelectionPurpose) {
        if (_uiState.value.interactionMode != CalendarInteractionMode.EDIT) return
        savedStateHandle[SELECTION_PURPOSE_KEY] = purpose.name
        _uiState.update { it.copy(selectionPurpose = purpose, editSelectionConfirmed = false) }
        savedStateHandle.remove<Boolean>(EDIT_SELECTION_CONFIRMED_KEY)
    }

    fun previewCalendarBatch(
        action: CalendarBatchAction,
        availability: CalendarBatchAvailability? = null,
    ) {
        val repository = calendarBatchRepository ?: return
        val dates = _uiState.value.editSelectedDates
        if (dates.isEmpty() || _uiState.value.batchBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(batchBusy = true, batchMessage = null, batchPreview = null) }
            try {
                val preview = repository.preview(action, dates, availability)
                _uiState.update { it.copy(batchBusy = false, batchPreview = preview) }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(batchBusy = false, batchMessage = error.message ?: "No se pudo revisar la selección.")
                }
            }
        }
    }

    fun dismissCalendarBatchPreview() = _uiState.update { it.copy(batchPreview = null) }

    fun applyCalendarBatch() {
        val repository = calendarBatchRepository ?: return
        val expected = _uiState.value.batchPreview ?: return
        if (_uiState.value.batchBusy || expected.conflicts.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(batchBusy = true, batchMessage = null) }
            try {
                mutationGate?.withExclusiveMutation { repository.apply(expected) } ?: repository.apply(expected)
                val count = expected.applicable.size
                val label = when (expected.action) {
                    CalendarBatchAction.DAY_OFF -> "Franco"
                    CalendarBatchAction.UNDEFINED -> "Sin definir"
                    CalendarBatchAction.CLEAR -> "Limpieza"
                    CalendarBatchAction.AVAILABILITY -> requireNotNull(expected.availability).label.displayName
                }
                finishEditMode()
                _uiState.update {
                    it.copy(batchBusy = false, batchPreview = null, batchMessage = "$label aplicado en $count ${if (count == 1) "día" else "días"}.")
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(batchBusy = false, batchPreview = null, batchMessage = error.message ?: "No se aplicó ningún cambio.")
                }
            }
        }
    }

    fun clearCalendarBatchMessage() = _uiState.update { it.copy(batchMessage = null) }

    fun toggleEditDate(date: LocalDate) {
        val current = _uiState.value
        val updated = current.toggleEditDate(date)
        if (updated == current) return
        persistEditSelection(updated.editSelectedDates)
        savedStateHandle.remove<Boolean>(EDIT_SELECTION_CONFIRMED_KEY)
        _uiState.value = updated
    }

    fun setEditSelectedDates(dates: Set<LocalDate>) {
        val current = _uiState.value
        if (
            current.interactionMode != CalendarInteractionMode.EDIT ||
            dates.any { YearMonth.from(it) != current.visibleMonth }
        ) return
        persistEditSelection(dates)
        savedStateHandle.remove<Boolean>(EDIT_SELECTION_CONFIRMED_KEY)
        _uiState.update { it.copy(editSelectedDates = dates, editSelectionConfirmed = false) }
    }

    fun clearEditSelection() = setEditSelectedDates(emptySet())

    fun confirmEditSelection() {
        val current = _uiState.value
        val updated = current.confirmEditSelection()
        if (updated == current) return
        savedStateHandle[EDIT_SELECTION_CONFIRMED_KEY] = true
        _uiState.value = updated
    }

    fun resumeEditSelection() {
        savedStateHandle.remove<Boolean>(EDIT_SELECTION_CONFIRMED_KEY)
        _uiState.update(CalendarUiState::resumeEditSelection)
    }

    fun finishEditMode() {
        savedStateHandle[INTERACTION_MODE_KEY] = CalendarInteractionMode.VIEW.name
        savedStateHandle.remove<String>(SELECTION_PURPOSE_KEY)
        savedStateHandle.remove<ArrayList<String>>(EDIT_SELECTED_DATES_KEY)
        savedStateHandle.remove<Boolean>(EDIT_SELECTION_CONFIRMED_KEY)
        _uiState.update(CalendarUiState::finishEditing)
    }

    fun retry() {
        observeMonth(_uiState.value.visibleMonth)
        if (!_uiState.value.hasAnyShiftsLoaded) observeGlobalShiftPresence()
    }

    fun clearStage2Message() = _uiState.update { it.copy(stage2Message = null) }

    fun toggleUndefined(date: LocalDate, current: ExplicitDayStatusType?) {
        launchStage2 {
            explicitDayStatusRepository.updateUndefined(
                date = date,
                expected = current?.let { ExplicitDayStatus(date, it) },
                mark = current != ExplicitDayStatusType.UNDEFINED,
            )
            if (current == ExplicitDayStatusType.UNDEFINED) "Se quitó Sin definir."
            else "El día quedó marcado como Sin definir."
        }
    }

    fun saveMedicalLeave(previous: MedicalLeave?, start: LocalDate, end: LocalDate, note: String?,
        onResult: (Boolean) -> Unit = {},
    ) {
        launchStage2(onResult) {
            require(!end.isBefore(start)) { "La fecha final no puede ser anterior a la inicial." }
            val now = Instant.ofEpochMilli(clock.millis())
            val value = MedicalLeave(
                id = previous?.id ?: UUID.randomUUID(),
                startDate = start,
                endDateInclusive = end,
                privateNote = note?.trim()?.takeIf(String::isNotEmpty),
                createdAt = previous?.createdAt ?: now,
                updatedAt = previous?.let { nextInstant(it.updatedAt, now) } ?: now,
            )
            if (previous == null) medicalLeaveRepository.create(value)
            else medicalLeaveRepository.updateExpected(previous, value)
            "Carpeta médica guardada. Las jornadas se conservaron."
        }
    }

    fun deleteMedicalLeave(expected: MedicalLeave) {
        launchStage2 {
            medicalLeaveRepository.deleteExpected(expected)
            "Carpeta médica eliminada."
        }
    }

    fun changeShiftSituation(
        expected: com.blackatsystems.miguardia.core.domain.model.ShiftSituationExpectation,
        target: ShiftStatus,
        reason: String?,
        note: String?,
        actor: CancellationActor?,
        notifiedAt: Instant?,
        removeActual: Boolean,
        evidenceUri: Uri? = null,
    ) {
        val repository = shiftSituationRepository ?: return
        launchStage2 {
            val operation: suspend () -> String = {
            val now = Instant.ofEpochMilli(clock.millis())
            val oldEvidence = expected.previous?.evidence
            val imported = evidenceUri?.let { uri ->
                require(target == ShiftStatus.CANCELLED)
                val store = requireNotNull(evidenceFileStore)
                val photo = store.import(
                    uri = uri,
                    ownerId = expected.planned.shift.id,
                    versioned = true,
                    replacingStorageKey = oldEvidence?.storageKey,
                    namespace = SchedulePhotoFileStore.CANCEL_NAMESPACE,
                )
                CancellationEvidence(
                    storageKey = photo.storageKey,
                    mimeType = photo.mimeType,
                    byteSize = photo.byteSize,
                    pixelWidth = photo.width,
                    pixelHeight = photo.height,
                    sha256 = sha256(store.file(photo.storageKey)),
                )
            }
            val replacement = if (target == ShiftStatus.PLANNED) null else ShiftSituation(
                shiftId = expected.planned.shift.id,
                status = target,
                reason = reason?.trim()?.takeIf(String::isNotEmpty),
                privateNote = note?.trim()?.takeIf(String::isNotEmpty),
                cancellationActor = actor.takeIf { target == ShiftStatus.CANCELLED },
                notifiedAt = notifiedAt.takeIf { target == ShiftStatus.CANCELLED },
                evidence = (imported ?: oldEvidence).takeIf { target == ShiftStatus.CANCELLED },
                createdAt = expected.previous?.createdAt ?: now,
                updatedAt = expected.previous?.let { nextInstant(it.updatedAt, now) } ?: now,
            )
            val mutation = ShiftSituationMutation(expected, replacement, removeActual)
            try {
                if (oldEvidence != null && target != ShiftStatus.CANCELLED) {
                    requireNotNull(evidenceFileStore).removeRecoverably(oldEvidence.storageKey) {
                        repository.apply(mutation)
                    }
                } else {
                    repository.apply(mutation)
                    if (oldEvidence != null && imported != null && oldEvidence.storageKey != imported.storageKey) {
                        runCatching { evidenceFileStore?.discardUnreferenced(oldEvidence.storageKey) }
                    }
                }
            } catch (error: Exception) {
                imported?.let { runCatching { evidenceFileStore?.discardUnreferenced(it.storageKey) } }
                repository.inspect(expected.planned.shift.id)?.let { refreshed ->
                    _uiState.update { state ->
                        state.copy(stage2Expectations = state.stage2Expectations + (refreshed.planned.shift.id to refreshed))
                    }
                }
                throw error
            }
            repository.inspect(expected.planned.shift.id)?.let { refreshed ->
                _uiState.update { state ->
                    state.copy(stage2Expectations = state.stage2Expectations + (refreshed.planned.shift.id to refreshed))
                }
            }
            when (target) {
                ShiftStatus.PLANNED -> "La jornada volvió a Planificada."
                ShiftStatus.ABSENT -> "La jornada quedó marcada como Ausencia."
                ShiftStatus.CANCELLED -> "La jornada quedó marcada como Cancelada."
            }
            }
            mutationGate?.withExclusiveMutation { operation() } ?: operation()
        }
    }

    private fun inspectSituationsFor(date: LocalDate) {
        val repository = shiftSituationRepository ?: return
        val ids = _uiState.value.days.singleOrNull { it.date == date }
            ?.shifts.orEmpty().map { it.shift.id }
        viewModelScope.launch {
            val expectations = ids.mapNotNull { id -> repository.inspect(id)?.let { id to it } }.toMap()
            if (_uiState.value.detailDate == date) {
                _uiState.update { it.copy(stage2Expectations = expectations) }
            }
        }
    }

    private fun launchStage2(onResult: (Boolean) -> Unit = {}, block: suspend () -> String) {
        if (_uiState.value.stage2Busy) {
            onResult(false)
            return
        }
        _uiState.update { it.copy(stage2Busy = true, stage2Message = null) }
        viewModelScope.launch {
            var succeeded = false
            val message = try {
                block().also { succeeded = true }
            } catch (error: Exception) {
                error.message ?: "No se pudo guardar el cambio. Revisá los datos e intentá nuevamente."
            }
            _uiState.update { it.copy(stage2Busy = false, stage2Message = message) }
            onResult(succeeded)
        }
    }

    private fun nextInstant(previous: Instant, candidate: Instant): Instant =
        if (candidate.isAfter(previous)) candidate else previous.plusMillis(1)

    private fun sha256(file: java.io.File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun setVisibleMonth(month: YearMonth) {
        if (month == _uiState.value.visibleMonth) return
        savedStateHandle[VISIBLE_MONTH_KEY] = month.toString()
        savedStateHandle.remove<String>(DETAIL_DATE_KEY)
        savedStateHandle.remove<String>(LEGACY_SELECTED_DATE_KEY)
        savedStateHandle.remove<ArrayList<String>>(EDIT_SELECTED_DATES_KEY)
        savedStateHandle.remove<Boolean>(EDIT_SELECTION_CONFIRMED_KEY)
        _uiState.update {
            it.copy(
                visibleMonth = month,
                referenceInstant = clock.instant(),
                days = emptyList(),
                detailDate = null,
                editSelectedDates = emptySet(),
                editSelectionConfirmed = false,
                loadState = CalendarLoadState.LOADING,
                errorMessage = null,
            )
        }
        observeMonth(month)
    }

    private fun persistEditSelection(dates: Set<LocalDate>) {
        if (dates.isEmpty()) {
            savedStateHandle.remove<ArrayList<String>>(EDIT_SELECTED_DATES_KEY)
            savedStateHandle.remove<Boolean>(EDIT_SELECTION_CONFIRMED_KEY)
        } else {
            savedStateHandle[EDIT_SELECTED_DATES_KEY] = ArrayList(dates.sorted().map(LocalDate::toString))
        }
    }

    private fun observeGlobalShiftPresence() {
        shiftPresenceJob?.cancel()
        _uiState.update { it.copy(shiftPresenceError = false) }
        shiftPresenceJob = viewModelScope.launch {
            shiftRepository.observeHasAny()
                .catch {
                    _uiState.update { state ->
                        if (state.hasAnyShiftsLoaded) state else state.copy(shiftPresenceError = true)
                    }
                }
                .collect { hasAny ->
                    _uiState.update {
                        it.copy(
                            hasAnyShifts = hasAny,
                            hasAnyShiftsLoaded = true,
                            shiftPresenceError = false,
                        )
                    }
                }
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
        private val shiftSituationRepository: ShiftSituationRepository? = null,
        private val evidenceFileStore: SchedulePhotoFileStore? = null,
        private val mutationGate: LocalDataMutationGate? = null,
        private val calendarBatchRepository: CalendarBatchRepository? = null,
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
                shiftSituationRepository = shiftSituationRepository,
                evidenceFileStore = evidenceFileStore,
                mutationGate = mutationGate,
                calendarBatchRepository = calendarBatchRepository,
            ) as T
        }
    }

    private companion object {
        const val VISIBLE_MONTH_KEY = "calendar.visibleMonth"
        const val INTERACTION_MODE_KEY = "calendar.interactionMode"
        const val SELECTION_PURPOSE_KEY = "calendar.selectionPurpose"
        const val CALENDAR_COLLAPSED_KEY = "calendar.collapsed"
        const val LAST_OPENED_DATE_KEY = "calendar.lastOpenedDate"
        const val DETAIL_DATE_KEY = "calendar.detailDate"
        const val EDIT_SELECTED_DATES_KEY = "calendar.editSelectedDates"
        const val EDIT_SELECTION_CONFIRMED_KEY = "calendar.editSelectionConfirmed"
        const val LEGACY_SELECTED_DATE_KEY = "calendar.selectedDate"
    }
}
