package com.blackatsystems.miguardia.ui.exceptions

import android.net.Uri
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
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteWithAttachments
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import com.blackatsystems.miguardia.core.domain.repository.LocalDataException
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoteRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
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
    val holidaySelectionActive: Boolean = false,
    val holidayDraft: HolidayDraft = HolidayDraft(),
    val noteDraft: NoteDraft = NoteDraft(),
)

private const val MAX_NOTE_IMAGES = 30
private const val ATTACHMENT_FIELD_SEPARATOR = "|"

private fun ShiftNoteAttachment.encodeForSavedState(): String = listOf(
    id,
    noteId,
    storageKey,
    mimeType,
    byteSize,
    pixelWidth,
    pixelHeight,
    position,
    createdAt.toEpochMilli(),
    updatedAt.toEpochMilli(),
).joinToString(ATTACHMENT_FIELD_SEPARATOR)

private fun decodeSavedAttachment(raw: String): ShiftNoteAttachment? = runCatching {
    val fields = raw.split(ATTACHMENT_FIELD_SEPARATOR)
    require(fields.size == 10)
    ShiftNoteAttachment(
        id = UUID.fromString(fields[0]),
        noteId = UUID.fromString(fields[1]),
        storageKey = fields[2],
        mimeType = fields[3],
        byteSize = fields[4].toLong(),
        pixelWidth = fields[5].toInt(),
        pixelHeight = fields[6].toInt(),
        position = fields[7].toInt(),
        createdAt = java.time.Instant.ofEpochMilli(fields[8].toLong()),
        updatedAt = java.time.Instant.ofEpochMilli(fields[9].toLong()),
    )
}.getOrNull()

internal class ExceptionsCoordinator(
    private val holidays: HolidayRepository,
    private val notes: ShiftNoteRepository,
    private val shifts: ShiftRepository,
    private val clock: Clock,
    private val uuidProvider: () -> UUID,
    private val imageStore: SchedulePhotoFileStore? = null,
    private val scope: CoroutineScope,
    private val initialState: ExceptionsPersistedState,
    private val persist: (ExceptionsPersistedState) -> Unit,
) {
    private val writeMutex = Mutex()
    private val _uiState = MutableStateFlow(
        ExceptionsUiState(
            surface = initialState.surface.takeUnless { initialState.holidaySelectionActive }
                ?: ExceptionsSurface.NONE,
            holidayMonth = initialState.holidayMonth,
            holidaySelectionActive = initialState.holidaySelectionActive,
            holidayDraft = initialState.holidayDraft,
        ),
    )
    val uiState: StateFlow<ExceptionsUiState> = _uiState.asStateFlow()

    private var holidayJob: Job? = null
    private var noteJob: Job? = null

    init {
        scope.launch {
            val draftKeys = initialState.noteDraft.attachments.mapTo(linkedSetOf(), ShiftNoteAttachment::storageKey)
            runCatching {
                imageStore?.reconcileNoteAttachments(notes.allAttachmentStorageKeys() + draftKeys)
            }
        }
        when {
            initialState.surface == ExceptionsSurface.NOTES && initialState.shiftId != null -> {
                scope.launch {
                    try {
                        val restoredShift = shifts.getById(initialState.shiftId)
                        if (restoredShift == null) {
                            setSurface(ExceptionsSurface.NONE, null)
                        } else {
                            activateNotes(restoredShift, restoreDraft = true)
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
        discardNewDraftFiles(_uiState.value.noteDraft)
        noteJob?.cancel()
        _uiState.update {
            it.copy(
                surface = ExceptionsSurface.HOLIDAYS,
                holidayMonth = month,
                holidaySelectionActive = false,
                selectedShift = null,
                notes = emptyList(),
                noteItems = emptyList(),
                noteDraft = NoteDraft(),
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
            )
        }
        setSurface(ExceptionsSurface.HOLIDAYS, null)
        setHolidayMonth(month)
    }

    fun beginHolidaySelection(month: YearMonth = _uiState.value.holidayMonth) {
        if (_uiState.value.isSaving) return
        discardNewDraftFiles(_uiState.value.noteDraft)
        noteJob?.cancel()
        _uiState.update {
            it.copy(
                surface = ExceptionsSurface.NONE,
                holidayMonth = month,
                holidaySelectionActive = true,
                selectedShift = null,
                notes = emptyList(),
                noteItems = emptyList(),
                noteDraft = NoteDraft(),
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
            )
        }
        persistCurrentState()
        observeHolidays(month)
    }

    fun updateHolidaySelection(month: YearMonth, dates: Set<LocalDate>) {
        if (_uiState.value.isSaving || !_uiState.value.holidaySelectionActive) return
        if (dates.any { YearMonth.from(it) != month }) return
        val editing = _uiState.value.holidayDraft.editingId != null
        if (editing && dates.size > 1) return
        val monthChanged = _uiState.value.holidayMonth != month
        val datesText = dates.sorted().joinToString(",")
        _uiState.update {
            it.copy(
                holidayMonth = month,
                holidayDraft = it.holidayDraft.copy(
                    datesText = datesText,
                    conflictDates = emptySet(),
                    pendingPolicy = null,
                ),
                errorMessage = null,
            )
        }
        persistCurrentState()
        if (monthChanged) observeHolidays(month)
    }

    fun confirmHolidaySelection(month: YearMonth, dates: Set<LocalDate>) {
        if (_uiState.value.isSaving || !_uiState.value.holidaySelectionActive) return
        if (dates.isEmpty()) return showError("Elegí al menos una fecha en el calendario.")
        if (dates.any { YearMonth.from(it) != month }) {
            return showError("Elegí fechas de un mismo mes.")
        }
        if (_uiState.value.holidayDraft.editingId != null && dates.size != 1) {
            return showError("La edición de un feriado admite una sola fecha.")
        }
        holidayJob?.cancel()
        _uiState.update {
            it.copy(
                surface = ExceptionsSurface.HOLIDAYS,
                holidayMonth = month,
                holidaySelectionActive = false,
                holidayDraft = it.holidayDraft.copy(
                    datesText = dates.sorted().joinToString(","),
                    conflictDates = emptySet(),
                    pendingPolicy = null,
                ),
                isLoading = true,
                errorMessage = null,
            )
        }
        persistCurrentState()
        observeHolidays(month)
    }

    fun cancelHolidaySelection() {
        if (_uiState.value.isSaving || !_uiState.value.holidaySelectionActive) return
        _uiState.update {
            it.copy(
                surface = ExceptionsSurface.HOLIDAYS,
                holidaySelectionActive = false,
                errorMessage = null,
            )
        }
        persistCurrentState()
    }

    fun openNotes(shift: Shift) {
        if (_uiState.value.isSaving) return
        activateNotes(shift, restoreDraft = false)
    }

    fun close() {
        if (_uiState.value.isSaving) return
        discardNewDraftFiles(_uiState.value.noteDraft)
        holidayJob?.cancel()
        noteJob?.cancel()
        _uiState.update {
            it.copy(
                surface = ExceptionsSurface.NONE,
                holidaySelectionActive = false,
                selectedShift = null,
                holidays = emptyList(),
                notes = emptyList(),
                noteItems = emptyList(),
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
        persistCurrentState()
    }

    fun editHoliday(holiday: Holiday) {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                surface = ExceptionsSurface.NONE,
                holidaySelectionActive = true,
                holidayDraft = HolidayDraft(
                    editingId = holiday.id,
                    datesText = holiday.date.toString(),
                    name = holiday.name.orEmpty(),
                ),
                errorMessage = null,
            )
        }
        persistCurrentState()
    }

    fun cancelHolidayEdit() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(holidayDraft = HolidayDraft(), errorMessage = null) }
        persistCurrentState()
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
                persistCurrentState()
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
            persistCurrentState()
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
        persistCurrentState()
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
        persistCurrentState()
    }

    fun updateNoteDraft(transform: (NoteDraft) -> NoteDraft) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(noteDraft = transform(it.noteDraft), errorMessage = null) }
        persistCurrentState()
    }

    fun editNote(item: ShiftNoteWithAttachments) {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                noteDraft = NoteDraft(
                    editingId = item.note.id,
                    body = item.note.body,
                    attachments = item.attachments,
                    originalAttachmentIds = item.attachments.mapTo(linkedSetOf(), ShiftNoteAttachment::id),
                ),
                errorMessage = null,
            )
        }
        persistCurrentState()
    }

    fun addNoteImages(uris: List<Uri>) {
        if (uris.isEmpty() || _uiState.value.isSaving) return
        val store = imageStore ?: return showError("No pudimos preparar el almacenamiento de imágenes.")
        val available = (MAX_NOTE_IMAGES - _uiState.value.noteDraft.attachments.size).coerceAtLeast(0)
        val accepted = uris.take(available)
        if (accepted.isEmpty()) return showError("Esta nota ya tiene el máximo de 30 imágenes.")
        launchWrite {
            val imported = mutableListOf<ShiftNoteAttachment>()
            val noteId = _uiState.value.noteDraft.editingId ?: pendingNoteId()
            try {
                accepted.forEach { uri ->
                    val attachmentId = uuidProvider()
                    val stored = store.import(
                        uri = uri,
                        ownerId = attachmentId,
                        namespace = SchedulePhotoFileStore.NOTE_NAMESPACE,
                    )
                    val now = clock.instant()
                    imported += ShiftNoteAttachment(
                        id = attachmentId,
                        noteId = noteId,
                        storageKey = stored.storageKey,
                        mimeType = stored.mimeType,
                        byteSize = stored.byteSize,
                        pixelWidth = stored.width,
                        pixelHeight = stored.height,
                        position = _uiState.value.noteDraft.attachments.size + imported.size,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
                _uiState.update { state ->
                    val current = state.noteDraft
                    state.copy(
                        noteDraft = current.copy(attachments = current.attachments + imported),
                        infoMessage = if (uris.size > accepted.size) {
                            "Se agregaron ${accepted.size} imágenes. Se rechazó el excedente del máximo de 30."
                        } else null,
                    )
                }
                persistCurrentState()
            } catch (error: Exception) {
                imported.forEach { runCatching { store.discardUnreferenced(it.storageKey) } }
                throw error
            }
        }
    }

    fun removeNoteImage(id: UUID) {
        if (_uiState.value.isSaving) return
        val draft = _uiState.value.noteDraft
        val target = draft.attachments.firstOrNull { it.id == id } ?: return
        val updated = draft.attachments.filterNot { it.id == id }.mapIndexed { index, item ->
            item.copy(position = index)
        }
        _uiState.update { it.copy(noteDraft = draft.copy(attachments = updated), errorMessage = null) }
        persistCurrentState()
        if (id !in draft.originalAttachmentIds) {
            scope.launch { runCatching { imageStore?.discardUnreferenced(target.storageKey) } }
        }
    }

    fun cancelNoteEdit() {
        if (_uiState.value.isSaving) return
        discardNewDraftFiles(_uiState.value.noteDraft)
        _uiState.update { it.copy(noteDraft = NoteDraft(), errorMessage = null) }
        persistCurrentState()
    }

    fun saveNote() {
        val shift = _uiState.value.selectedShift ?: return
        val draft = _uiState.value.noteDraft
        launchWrite {
            val noteId = draft.editingId ?: pendingNoteId()
            val existing = draft.editingId?.let { notes.getWithAttachments(it) }
            if (draft.editingId != null && existing == null) {
                throw IllegalStateException("La nota ya no existe.")
            }
            val now = clock.instant()
            val note = ShiftNote(
                id = noteId,
                shiftId = shift.id,
                body = draft.body,
                createdAt = existing?.note?.createdAt ?: now,
                updatedAt = now,
            )
            if (note.body.isBlank() && draft.attachments.isEmpty()) {
                throw IllegalArgumentException("Escribí una nota o agregá al menos una imagen.")
            }
            val attachments = draft.attachments.mapIndexed { index, item ->
                item.copy(noteId = noteId, position = index, updatedAt = now)
            }
            notes.saveWithAttachments(note, attachments)
            val retainedIds = attachments.mapTo(hashSetOf(), ShiftNoteAttachment::id)
            existing?.attachments.orEmpty().filter { it.id !in retainedIds }.forEach { removed ->
                runCatching { imageStore?.discardUnreferenced(removed.storageKey) }
            }
            _uiState.update {
                it.copy(noteDraft = NoteDraft(), infoMessage = "Nota guardada.")
            }
            persistCurrentState()
        }
    }

    fun deleteNote(id: UUID) = launchWrite {
        val existing = notes.getWithAttachments(id)
        notes.delete(id)
        existing?.attachments.orEmpty().forEach { attachment ->
            runCatching { imageStore?.discardUnreferenced(attachment.storageKey) }
        }
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

    private fun activateNotes(shift: Shift, restoreDraft: Boolean) {
        if (!restoreDraft) discardNewDraftFiles(_uiState.value.noteDraft)
        holidayJob?.cancel()
        _uiState.update {
            it.copy(
                surface = ExceptionsSurface.NOTES,
                holidaySelectionActive = false,
                selectedShift = shift,
                holidays = emptyList(),
                notes = emptyList(),
                noteItems = emptyList(),
                holidayDraft = HolidayDraft(),
                noteDraft = if (restoreDraft) initialState.noteDraft else NoteDraft(),
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
            notes.observeWithAttachmentsForShift(shiftId)
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
                            it.copy(
                                notes = rows.map(ShiftNoteWithAttachments::note),
                                noteItems = rows,
                                isLoading = false,
                                errorMessage = null,
                            )
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

    private fun pendingNoteId(): UUID {
        val existing = _uiState.value.noteDraft.attachments.firstOrNull()?.noteId
        return existing ?: uuidProvider()
    }

    private fun discardNewDraftFiles(draft: NoteDraft) {
        val keys = draft.attachments
            .filter { it.id !in draft.originalAttachmentIds }
            .map(ShiftNoteAttachment::storageKey)
        if (keys.isNotEmpty()) scope.launch {
            keys.forEach { runCatching { imageStore?.discardUnreferenced(it) } }
        }
    }

    private fun setSurface(surface: ExceptionsSurface, shiftId: UUID?) {
        _uiState.update { it.copy(surface = surface) }
        persist(
            ExceptionsPersistedState(
                surface = surface,
                holidayMonth = _uiState.value.holidayMonth,
                shiftId = shiftId,
                holidaySelectionActive = _uiState.value.holidaySelectionActive,
                holidayDraft = _uiState.value.holidayDraft,
                noteDraft = _uiState.value.noteDraft,
            ),
        )
    }

    private fun persistCurrentState() {
        persist(
            ExceptionsPersistedState(
                surface = _uiState.value.surface,
                holidayMonth = _uiState.value.holidayMonth,
                shiftId = _uiState.value.selectedShift?.id,
                holidaySelectionActive = _uiState.value.holidaySelectionActive,
                holidayDraft = _uiState.value.holidayDraft,
                noteDraft = _uiState.value.noteDraft,
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
    imageStore: SchedulePhotoFileStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val coordinator = ExceptionsCoordinator(
        holidays = holidays,
        notes = notes,
        shifts = shifts,
        clock = clock,
        uuidProvider = uuidProvider,
        imageStore = imageStore,
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
            holidaySelectionActive = savedStateHandle[HOLIDAY_SELECTION_ACTIVE_KEY] ?: false,
            holidayDraft = HolidayDraft(
                editingId = savedStateHandle.get<String>(HOLIDAY_EDITING_ID_KEY)
                    ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() },
                datesText = savedStateHandle[HOLIDAY_DATES_KEY] ?: "",
                name = savedStateHandle[HOLIDAY_NAME_KEY] ?: "",
                conflictDates = savedStateHandle.get<ArrayList<String>>(HOLIDAY_CONFLICT_DATES_KEY)
                    .orEmpty()
                    .mapNotNull { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
                    .toCollection(linkedSetOf()),
            ),
            noteDraft = NoteDraft(
                editingId = savedStateHandle.get<String>(NOTE_EDITING_ID_KEY)
                    ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() },
                body = savedStateHandle[NOTE_BODY_KEY] ?: "",
                attachments = savedStateHandle.get<ArrayList<String>>(NOTE_ATTACHMENTS_KEY)
                    .orEmpty()
                    .mapNotNull(::decodeSavedAttachment)
                    .sortedBy(ShiftNoteAttachment::position),
                originalAttachmentIds = savedStateHandle.get<ArrayList<String>>(NOTE_ORIGINAL_IDS_KEY)
                    .orEmpty()
                    .mapNotNull { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
                    .toSet(),
            ),
        ),
        persist = { state ->
            savedStateHandle[SURFACE_KEY] = state.surface.name
            savedStateHandle[HOLIDAY_MONTH_KEY] = state.holidayMonth.toString()
            savedStateHandle[HOLIDAY_SELECTION_ACTIVE_KEY] = state.holidaySelectionActive
            savedStateHandle[HOLIDAY_DATES_KEY] = state.holidayDraft.datesText
            savedStateHandle[HOLIDAY_NAME_KEY] = state.holidayDraft.name
            savedStateHandle[HOLIDAY_CONFLICT_DATES_KEY] =
                ArrayList(state.holidayDraft.conflictDates.sorted().map(LocalDate::toString))
            savedStateHandle[NOTE_BODY_KEY] = state.noteDraft.body
            savedStateHandle[NOTE_ATTACHMENTS_KEY] =
                ArrayList(state.noteDraft.attachments.map(ShiftNoteAttachment::encodeForSavedState))
            savedStateHandle[NOTE_ORIGINAL_IDS_KEY] =
                ArrayList(state.noteDraft.originalAttachmentIds.map(UUID::toString))
            if (state.noteDraft.editingId == null) {
                savedStateHandle.remove<String>(NOTE_EDITING_ID_KEY)
            } else {
                savedStateHandle[NOTE_EDITING_ID_KEY] = state.noteDraft.editingId.toString()
            }
            if (state.holidayDraft.editingId == null) {
                savedStateHandle.remove<String>(HOLIDAY_EDITING_ID_KEY)
            } else {
                savedStateHandle[HOLIDAY_EDITING_ID_KEY] = state.holidayDraft.editingId.toString()
            }
            if (state.shiftId == null) {
                savedStateHandle.remove<String>(SHIFT_ID_KEY)
            } else {
                savedStateHandle[SHIFT_ID_KEY] = state.shiftId.toString()
            }
        },
    )

    val uiState: StateFlow<ExceptionsUiState> = coordinator.uiState

    fun openHolidays(month: YearMonth) = coordinator.openHolidays(month)
    fun beginHolidaySelection(month: YearMonth) = coordinator.beginHolidaySelection(month)
    fun updateHolidaySelection(month: YearMonth, dates: Set<LocalDate>) =
        coordinator.updateHolidaySelection(month, dates)
    fun confirmHolidaySelection(month: YearMonth, dates: Set<LocalDate>) =
        coordinator.confirmHolidaySelection(month, dates)
    fun cancelHolidaySelection() = coordinator.cancelHolidaySelection()
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
    fun editNote(note: ShiftNoteWithAttachments) = coordinator.editNote(note)
    fun addNoteImages(uris: List<Uri>) = coordinator.addNoteImages(uris)
    fun removeNoteImage(id: UUID) = coordinator.removeNoteImage(id)
    fun cancelNoteEdit() = coordinator.cancelNoteEdit()
    fun saveNote() = coordinator.saveNote()
    fun deleteNote(id: UUID) = coordinator.deleteNote(id)
    fun retry() = coordinator.retry()
    fun clearMessage() = coordinator.clearMessage()

    class Factory(
        private val holidays: HolidayRepository,
        private val notes: ShiftNoteRepository,
        private val shifts: ShiftRepository,
        private val imageStore: SchedulePhotoFileStore,
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
                imageStore = imageStore,
                savedStateHandle = extras.createSavedStateHandle(),
            ) as T
        }
    }

    private companion object {
        const val SURFACE_KEY = "exceptions.surface"
        const val HOLIDAY_MONTH_KEY = "exceptions.holidayMonth"
        const val SHIFT_ID_KEY = "exceptions.shiftId"
        const val HOLIDAY_SELECTION_ACTIVE_KEY = "exceptions.holidaySelectionActive"
        const val HOLIDAY_EDITING_ID_KEY = "exceptions.holidayEditingId"
        const val HOLIDAY_DATES_KEY = "exceptions.holidayDates"
        const val HOLIDAY_NAME_KEY = "exceptions.holidayName"
        const val HOLIDAY_CONFLICT_DATES_KEY = "exceptions.holidayConflictDates"
        const val NOTE_EDITING_ID_KEY = "exceptions.noteEditingId"
        const val NOTE_BODY_KEY = "exceptions.noteBody"
        const val NOTE_ATTACHMENTS_KEY = "exceptions.noteAttachments"
        const val NOTE_ORIGINAL_IDS_KEY = "exceptions.noteOriginalIds"
    }
}
