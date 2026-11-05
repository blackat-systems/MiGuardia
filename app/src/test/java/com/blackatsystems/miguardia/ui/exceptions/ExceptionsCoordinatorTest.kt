package com.blackatsystems.miguardia.ui.exceptions

import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.HolidayBatchMutation
import com.blackatsystems.miguardia.core.domain.model.HolidayConflictPolicy
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteWithAttachments
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoteRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.ui.photos.NoteImageSelection
import com.blackatsystems.miguardia.ui.photos.NoteAttachmentFiles
import com.blackatsystems.miguardia.ui.photos.StoredPhoto
import com.blackatsystems.miguardia.backup.LocalDataMutationGate
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExceptionsCoordinatorTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach(CoroutineScope::cancel)
    }

    @Test
    fun imageSelectionCapacityAcceptsExactFitAndRejectsWholeExcessBeforeImport() = runBlocking {
        for (existing in listOf(0, 1, 29, 30)) {
            val available = 30 - existing
            val imported = mutableListOf<Int>()
            val result = NoteImageSelection.importBatch((0 until available).toList(), existing,
                importImage = { source, position -> imported += source; position }, discard = { error("No rollback expected") })
            assertEquals((existing until 30).toList(), result)
            imported.clear()
            try {
                NoteImageSelection.importBatch((0..available).toList(), existing,
                    importImage = { source, _ -> imported += source; source }, discard = { error("No import should start") })
                org.junit.Assert.fail("The whole excess must be rejected")
            } catch (error: IllegalArgumentException) {
                assertTrue(error.message!!.contains("No se agregó ninguna imagen"))
            }
            assertTrue(imported.isEmpty())
        }
    }

    @Test
    fun imageSelectionReadFailureOrCancellationRollsBackOnlyNewImages() = runBlocking {
        for (failure in listOf(java.io.IOException("Lectura interrumpida"), kotlinx.coroutines.CancellationException("Cancelado"))) {
            val privateImages = mutableSetOf("confirmed", "previous-draft")
            try {
                NoteImageSelection.importBatch(listOf("new-a", "new-b"), 2,
                    importImage = { source, _ -> if (source == "new-b") throw failure else source.also(privateImages::add) },
                    discard = { images -> privateImages.removeAll(images.toSet()) })
                org.junit.Assert.fail("The selection must fail")
            } catch (error: Exception) {
                assertTrue(error === failure)
            }
            assertEquals(setOf("confirmed", "previous-draft"), privateImages)
        }
    }

    @Test
    fun cancelledPickerNeverImportsOrClearsExistingImages() = runBlocking {
        val imported = NoteImageSelection.importBatch(emptyList<String>(), 30,
            importImage = { _, _ -> error("No file should be read") }, discard = { error("No file should be removed") })
        assertTrue(imported.isEmpty())
    }

    @Test
    fun coroutineCancellationStillCompletesSuspendingImageCleanup() = runBlocking {
        val files = mutableSetOf("previous")
        val operation = async {
            NoteImageSelection.importBatch(listOf("new", "cancel"), 1,
                importImage = { source, _ ->
                    if (source == "cancel") {
                        kotlinx.coroutines.currentCoroutineContext().cancel()
                        kotlinx.coroutines.yield()
                    }
                    source.also(files::add)
                },
                discard = { imported -> kotlinx.coroutines.yield(); files.removeAll(imported.toSet()) })
        }
        try { operation.await(); org.junit.Assert.fail("Expected cancellation") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(setOf("previous"), files)
    }

    @Test
    fun doubleNoteSaveWhileWritePendingCreatesOneAggregate() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val notes = FakeNoteRepository().apply { saveRelease = release }
        val coordinator = coordinator(notes = notes)
        coordinator.openNotes(SHIFT)
        coordinator.updateNoteDraft { it.copy(body = "Nota sintética") }
        coordinator.saveNote()
        assertTrue(coordinator.uiState.value.isSaving)
        coordinator.saveNote()
        assertEquals(1, notes.saveCalls)
        release.complete(Unit)
        assertFalse(coordinator.uiState.value.isSaving)
        assertEquals("Nota sintética", notes.saved!!.note.body)
        assertEquals(NoteDraft(), coordinator.uiState.value.noteDraft)
    }

    @Test
    fun rejectedNoteSavePreservesDraftForExplicitRetry() {
        val notes = FakeNoteRepository().apply { failNextSave = true }
        val coordinator = coordinator(notes = notes)
        coordinator.openNotes(SHIFT)
        coordinator.updateNoteDraft { it.copy(body = "Nota a conservar") }
        val before = coordinator.uiState.value.noteDraft
        coordinator.saveNote()
        assertEquals(before.body, coordinator.uiState.value.noteDraft.body)
        assertEquals(before.attachments, coordinator.uiState.value.noteDraft.attachments)
        assertNull(notes.saved)
        assertFalse(coordinator.uiState.value.isSaving)
        coordinator.saveNote()
        assertEquals("Nota a conservar", notes.saved!!.note.body)
        assertEquals(2, notes.saveCalls)
    }

    @Test
    fun lostNoteSaveAcknowledgementRetriesSameIdentityAfterRecreation() {
        val notes = FakeNoteRepository().apply { failAfterNextSave = true }
        val persisted = mutableListOf<ExceptionsPersistedState>()
        val first = coordinator(notes = notes, persist = persisted::add)
        first.openNotes(SHIFT)
        first.updateNoteDraft { it.copy(body = "Nota sin duplicar") }
        first.saveNote()
        assertEquals(1, notes.count)
        val committed = requireNotNull(notes.saved)
        assertEquals("Nota sin duplicar", first.uiState.value.noteDraft.body)
        val restored = coordinator(notes = notes, initial = persisted.last(), uuidSeed = 200L)
        restored.saveNote()
        assertEquals("A retry must not insert another note after a lost acknowledgement", 1, notes.count)
        assertEquals(committed.note.id, requireNotNull(notes.saved).note.id)
        assertEquals(NoteDraft(), restored.uiState.value.noteDraft)
    }

    @Test
    fun startupReconciliationCannotDeleteAttachmentFromConcurrentRestore() = runBlocking {
        noteRestoreRace(reconciliation = true)
    }

    @Test
    fun deletionCleanupCannotDeleteAttachmentFromConcurrentRestore() = runBlocking {
        noteRestoreRace(reconciliation = false)
    }

    @Test
    fun missingImageKeepsDraftAndRejectsBrokenReferenceBeforeSaving() {
        val image = ShiftNoteAttachment(uuid(801), uuid(802), "note_${uuid(801)}.png", "image/png", 10, 1, 1, 0, NOW, NOW)
        val notes = FakeNoteRepository()
        val draft = NoteDraft(body = "Nota conservada", attachments = listOf(image))
        val coordinator = coordinator(notes = notes, noteFiles = FakeNoteFiles(),
            initial = ExceptionsPersistedState(ExceptionsSurface.NOTES, MONTH, shiftId = SHIFT.id, noteDraft = draft))
        coordinator.saveNote()
        assertEquals(0, notes.saveCalls)
        assertEquals(draft.body, coordinator.uiState.value.noteDraft.body)
        assertEquals(draft.attachments, coordinator.uiState.value.noteDraft.attachments)
        assertTrue(coordinator.uiState.value.errorMessage!!.contains("Una imagen ya no está disponible"))
        assertFalse(coordinator.uiState.value.isSaving)
    }

    @Test
    fun cancellingAfterLostAcknowledgementPreservesTheConfirmedImage() {
        val image = ShiftNoteAttachment(uuid(811), uuid(812), "note_${uuid(811)}.png", "image/png", 10, 1, 1, 0, NOW, NOW)
        val notes = FakeNoteRepository().apply { failAfterNextSave = true }
        val files = FakeNoteFiles().apply { keys += image.storageKey }
        val coordinator = coordinator(notes = notes, noteFiles = files,
            initial = ExceptionsPersistedState(ExceptionsSurface.NOTES, MONTH, shiftId = SHIFT.id,
                noteDraft = NoteDraft(body = "Nota confirmada", attachments = listOf(image))))
        coordinator.saveNote()
        assertEquals(1, notes.count)
        coordinator.cancelNoteEdit()
        assertEquals(NoteDraft(), coordinator.uiState.value.noteDraft)
        assertTrue(files.exists(image.storageKey))
        assertEquals(1, notes.count)
    }

    @Test
    fun deletingEditedNoteCleansUnsavedImagesButPreservesFreshReferences() = runBlocking {
        val note = ShiftNote(uuid(901), SHIFT.id, "Nota editada", NOW, NOW)
        val other = ShiftNote(uuid(902), SHIFT.id, "Nota restaurada", NOW, NOW)
        fun image(id: Long, noteId: UUID) = ShiftNoteAttachment(uuid(id), noteId,
            "note_${uuid(id)}.png", "image/png", 10, 1, 1, 0, NOW, NOW)
        val original = image(903, note.id)
        val added = image(904, note.id)
        val referencedElsewhere = image(905, other.id)
        val notes = FakeNoteRepository()
        notes.saveWithAttachments(note, listOf(original))
        notes.saveWithAttachments(other, listOf(referencedElsewhere))
        val files = FakeNoteFiles().apply { keys += setOf(original.storageKey, referencedElsewhere.storageKey) }
        val coordinator = coordinator(notes = notes, noteFiles = files)
        coordinator.openNotes(SHIFT)
        coordinator.editNote(requireNotNull(notes.getWithAttachments(note.id)))
        // Post-import fixture: the original was removed from the draft, a new image was
        // imported, and restore has since confirmed another draft key in a different note.
        files.keys += added.storageKey
        coordinator.updateNoteDraft { it.copy(attachments = listOf(added, referencedElsewhere.copy(noteId = note.id, position = 1))) }

        coordinator.deleteNote(note.id)

        assertNull(notes.getWithAttachments(note.id))
        assertEquals(other, notes.getWithAttachments(other.id)!!.note)
        assertEquals(setOf(referencedElsewhere.storageKey), files.keys)
        assertEquals(NoteDraft(), coordinator.uiState.value.noteDraft)
    }

    @Test
    fun deletingNoteAfterLostAcknowledgementAlsoClearsItsPendingDraft() {
        val notes = FakeNoteRepository().apply { failAfterNextSave = true }
        val coordinator = coordinator(notes = notes)
        coordinator.openNotes(SHIFT)
        coordinator.updateNoteDraft { it.copy(body = "Nota confirmada sin respuesta") }
        coordinator.saveNote()
        val id = requireNotNull(notes.saved).note.id
        coordinator.deleteNote(id)
        assertEquals(0, notes.count)
        assertEquals(NoteDraft(), coordinator.uiState.value.noteDraft)
    }

    @Test
    fun deletingAnotherNotePreservesTheCurrentDraftAndItsPrivateImages() = runBlocking {
        val note = ShiftNote(uuid(911), SHIFT.id, "Nota en edición", NOW, NOW)
        val other = ShiftNote(uuid(912), SHIFT.id, "Otra nota", NOW, NOW)
        val image = ShiftNoteAttachment(uuid(913), note.id, "note_${uuid(913)}.png", "image/png", 10, 1, 1, 0, NOW, NOW)
        val notes = FakeNoteRepository()
        notes.saveWithAttachments(note, emptyList())
        notes.saveWithAttachments(other, emptyList())
        val files = FakeNoteFiles()
        val coordinator = coordinator(notes = notes, noteFiles = files)
        coordinator.openNotes(SHIFT)
        coordinator.editNote(requireNotNull(notes.getWithAttachments(note.id)))
        files.keys += image.storageKey
        coordinator.updateNoteDraft { it.copy(attachments = listOf(image)) }
        val before = coordinator.uiState.value.noteDraft
        coordinator.deleteNote(other.id)
        assertEquals(before, coordinator.uiState.value.noteDraft)
        assertTrue(files.exists(image.storageKey))
        assertEquals(note, notes.getWithAttachments(note.id)!!.note)
    }

    /** Real coordinator and gate; fake SQL/files expose the interleaving without Android. */
    private suspend fun noteRestoreRace(reconciliation: Boolean) = kotlinx.coroutines.coroutineScope {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val notes = FakeNoteRepository()
        val files = FakeNoteFiles()
        val gate = LocalDataMutationGate()
        val id = uuid(790)
        val note = ShiftNote(uuid(791), SHIFT.id, "Nota restaurable", NOW, NOW)
        val image = ShiftNoteAttachment(id, note.id, "note_$id.png", "image/png", 10, 1, 1, 0, NOW, NOW)
        if (!reconciliation) {
            notes.saveWithAttachments(note, listOf(image))
            files.keys += image.storageKey
            notes.deleteBarrier = entered to release
        } else notes.referencesBarrier = entered to release
        val coordinator = coordinator(notes = notes, noteFiles = files, mutationGate = gate)
        try {
            if (!reconciliation) coordinator.deleteNote(note.id)
            kotlinx.coroutines.withTimeout(5_000) { entered.await() }
            val restoreEntered = CompletableDeferred<Unit>()
            val restore = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                gate.withExclusiveMutation {
                    restoreEntered.complete(Unit)
                    notes.saveWithAttachments(note, listOf(image))
                    files.keys += image.storageKey
                }
            }
            if (restoreEntered.isCompleted) restore.await()
            release.complete(Unit)
            kotlinx.coroutines.withTimeout(5_000) { restore.await() }
            assertEquals(setOf(image.storageKey), notes.allAttachmentStorageKeys())
            assertTrue("A confirmed restored note must retain its private bytes", files.keys.contains(image.storageKey))
        } finally { release.complete(Unit) }
    }

    @Test
    fun detailImageExcessKeepsDraftAndReportsHowToAdjustSelection() {
        val images = (0 until 29).map { index ->
            val id = uuid(500L + index)
            ShiftNoteAttachment(id, uuid(499), "note_$id.jpg", "image/jpeg", 123, 20, 10, index, NOW, NOW)
        }
        val draft = NoteDraft(body = "Nota conservada", attachments = images)
        val coordinator = coordinator(initial = ExceptionsPersistedState(ExceptionsSurface.NOTES, MONTH,
            shiftId = SHIFT.id, noteDraft = draft))
        val unreadSelection = object : AbstractList<android.net.Uri>() {
            override val size = 2
            override fun get(index: Int): android.net.Uri = error("Selection must be rejected before reading any URI")
        }
        coordinator.addNoteImages(unreadSelection)
        assertEquals(draft, coordinator.uiState.value.noteDraft)
        assertEquals(NoteImageSelection.rejectionMessage(29, 2), coordinator.uiState.value.errorMessage)
        assertFalse(coordinator.uiState.value.isSaving)
    }

    @Test
    fun backCancellationClosesHolidaySelectionWithoutRestoreOrWrites() {
        val holidays = FakeHolidayRepository()
        val persisted = mutableListOf<ExceptionsPersistedState>()
        val coordinator = coordinator(holidays = holidays, persist = persisted::add)
        coordinator.beginHolidaySelection(MONTH)
        coordinator.updateHolidaySelection(MONTH, setOf(DATE, DATE.plusDays(1)))

        coordinator.cancelHolidaySelection()

        assertFalse(coordinator.uiState.value.holidaySelectionActive)
        assertEquals(ExceptionsSurface.HOLIDAYS, coordinator.uiState.value.surface)
        assertEquals(setOf(DATE, DATE.plusDays(1)), coordinator.uiState.value.holidayDraft.selectedDates)
        assertFalse(persisted.last().holidaySelectionActive)
        assertEquals(0, holidays.applyCalls)
        assertTrue(holidays.mutations.isEmpty())
    }

    @Test
    fun multiDateConflictsRequireAPolicyAndThenUseOneAtomicBatch() {
        val existing = Holiday(uuid(1), DATE, "Existente", NOW, NOW)
        val holidays = FakeHolidayRepository(listOf(existing))
        val coordinator = coordinator(holidays = holidays)
        coordinator.updateHolidayDraft {
            it.copy(
                datesText = listOf(DATE, DATE.plusDays(1)).joinToString(","),
                name = "Feriado ficticio",
            )
        }

        coordinator.saveHolidays()

        assertEquals(setOf(DATE), coordinator.uiState.value.holidayDraft.conflictDates)
        assertTrue(holidays.mutations.isEmpty())

        coordinator.saveHolidays(HolidayConflictPolicy.REPLACE)

        val mutation = holidays.mutations.single()
        assertEquals(HolidayConflictPolicy.REPLACE, mutation.conflictPolicy)
        assertEquals(setOf(DATE, DATE.plusDays(1)), mutation.holidaysToSave.mapTo(linkedSetOf(), Holiday::date))
        assertEquals(HolidayDraft(), coordinator.uiState.value.holidayDraft)
    }

    @Test
    fun mainCalendarSelectionAndDraftSurviveRecreationUntilSaveSucceeds() {
        val persisted = mutableListOf<ExceptionsPersistedState>()
        val coordinator = coordinator(persist = persisted::add)

        coordinator.updateHolidayDraft { it.copy(name = "Feriado ficticio") }
        coordinator.beginHolidaySelection(MONTH)
        coordinator.updateHolidaySelection(MONTH, setOf(DATE, DATE.plusDays(1)))

        val restored = coordinator(initial = persisted.last())
        assertTrue(restored.uiState.value.holidaySelectionActive)
        assertEquals(ExceptionsSurface.NONE, restored.uiState.value.surface)
        assertEquals(setOf(DATE, DATE.plusDays(1)), restored.uiState.value.holidayDraft.selectedDates)
        assertEquals("Feriado ficticio", restored.uiState.value.holidayDraft.name)

        restored.confirmHolidaySelection(MONTH, restored.uiState.value.holidayDraft.selectedDates)
        assertFalse(restored.uiState.value.holidaySelectionActive)
        assertEquals(ExceptionsSurface.HOLIDAYS, restored.uiState.value.surface)

        restored.saveHolidays()
        assertEquals(HolidayDraft(), restored.uiState.value.holidayDraft)
    }

    @Test
    fun holidayKeepAndCancelPreserveTheDeclaredCommandAndDraftBoundary() {
        val existing = Holiday(uuid(1), DATE, "Existente", NOW, NOW)
        val dates = setOf(DATE, DATE.plusDays(1))
        val keptRepository = FakeHolidayRepository(listOf(existing))
        val kept = coordinator(holidays = keptRepository)
        kept.updateHolidayDraft { it.copy(datesText = dates.sorted().joinToString(","), name = "Nombre propio") }
        kept.saveHolidays()
        assertEquals(setOf(DATE), kept.uiState.value.holidayDraft.conflictDates)
        assertEquals(0, keptRepository.applyCalls)
        kept.saveHolidays(HolidayConflictPolicy.KEEP_EXISTING)
        assertEquals(1, keptRepository.applyCalls)
        val command = keptRepository.mutations.single()
        assertEquals(HolidayConflictPolicy.KEEP_EXISTING, command.conflictPolicy)
        assertTrue(command.holidayIdsToDelete.isEmpty())
        assertEquals(dates, command.holidaysToSave.mapTo(linkedSetOf(), Holiday::date))
        assertTrue(command.holidaysToSave.all { it.name == "Nombre propio" })

        val cancelledRepository = FakeHolidayRepository(listOf(existing))
        val cancelled = coordinator(holidays = cancelledRepository)
        cancelled.updateHolidayDraft { it.copy(datesText = dates.sorted().joinToString(","), name = "Borrador conservado") }
        cancelled.saveHolidays()
        val draft = cancelled.uiState.value.holidayDraft
        cancelled.cancelHolidayConflict()
        assertEquals(0, cancelledRepository.applyCalls)
        assertTrue(cancelledRepository.mutations.isEmpty())
        assertEquals(draft.selectedDates, cancelled.uiState.value.holidayDraft.selectedDates)
        assertEquals(draft.name, cancelled.uiState.value.holidayDraft.name)
        assertTrue(cancelled.uiState.value.holidayDraft.conflictDates.isEmpty())
    }

    @Test
    fun failedSaveKeepsSelectedDatesAndNameForRetry() {
        val holidays = FakeHolidayRepository().apply {
            failure = IllegalStateException("Fallo ficticio")
        }
        val coordinator = coordinator(holidays = holidays)
        coordinator.updateHolidayDraft {
            it.copy(datesText = DATE.toString(), name = "Nombre conservado")
        }

        coordinator.saveHolidays()

        assertEquals(setOf(DATE), coordinator.uiState.value.holidayDraft.selectedDates)
        assertEquals("Nombre conservado", coordinator.uiState.value.holidayDraft.name)
        assertEquals("Fallo ficticio", coordinator.uiState.value.errorMessage)
    }

    @Test
    fun editingAHolidayReturnsToMainCalendarWithOneSelectedDate() {
        val holiday = Holiday(uuid(1), DATE, "Existente", NOW, NOW)
        val coordinator = coordinator(holidays = FakeHolidayRepository(listOf(holiday)))

        coordinator.editHoliday(holiday)

        assertTrue(coordinator.uiState.value.holidaySelectionActive)
        assertEquals(ExceptionsSurface.NONE, coordinator.uiState.value.surface)
        assertEquals(setOf(DATE), coordinator.uiState.value.holidayDraft.selectedDates)
        assertEquals("Existente", coordinator.uiState.value.holidayDraft.name)
    }

    @Test
    fun suspendedBatchRejectsDoubleSaveCloseAndSurfaceReplacement() = runBlocking {
        val holidays = FakeHolidayRepository()
        val gate = CompletableDeferred<Unit>()
        holidays.gate = gate
        val coordinator = coordinator(holidays = holidays)
        coordinator.updateHolidayDraft { it.copy(datesText = DATE.toString()) }

        coordinator.saveHolidays()
        coordinator.saveHolidays()
        coordinator.close()
        coordinator.openNotes(SHIFT)

        assertTrue(coordinator.uiState.value.isSaving)
        assertEquals(ExceptionsSurface.HOLIDAYS, coordinator.uiState.value.surface)
        assertEquals(1, holidays.applyCalls)

        gate.complete(Unit)

        assertFalse(coordinator.uiState.value.isSaving)
        assertEquals(1, holidays.mutations.size)
    }

    @Test
    fun savedNotesSurfaceRestoresTheExactShiftAndCloseClearsIt() {
        val persisted = mutableListOf<ExceptionsPersistedState>()
        val coordinator = coordinator(
            shifts = FakeShiftRepository(listOf(SHIFT)),
            initial = ExceptionsPersistedState(
                surface = ExceptionsSurface.NOTES,
                holidayMonth = MONTH,
                shiftId = SHIFT.id,
            ),
            persist = persisted::add,
        )

        assertEquals(ExceptionsSurface.NOTES, coordinator.uiState.value.surface)
        assertEquals(SHIFT, coordinator.uiState.value.selectedShift)
        assertEquals(SHIFT.id, persisted.last().shiftId)

        coordinator.close()

        assertEquals(ExceptionsSurface.NONE, coordinator.uiState.value.surface)
        assertNull(persisted.last().shiftId)
    }

    @Test
    fun restoredImageOnlyDraftCanBeSavedAsAnAtomicNoteAggregate() {
        val noteId = uuid(20)
        val attachment = ShiftNoteAttachment(
            id = uuid(21),
            noteId = noteId,
            storageKey = "note_00000000-0000-0000-0000-000000000021.jpg",
            mimeType = "image/jpeg",
            byteSize = 123L,
            pixelWidth = 20,
            pixelHeight = 10,
            position = 0,
            createdAt = NOW,
            updatedAt = NOW,
        )
        val notes = FakeNoteRepository()
        val coordinator = coordinator(
            notes = notes,
            initial = ExceptionsPersistedState(
                surface = ExceptionsSurface.NOTES,
                holidayMonth = MONTH,
                shiftId = SHIFT.id,
                noteDraft = NoteDraft(attachments = listOf(attachment)),
            ),
        )

        coordinator.saveNote()

        val saved = requireNotNull(notes.saved)
        assertEquals(noteId, saved.note.id)
        assertEquals("", saved.note.body)
        assertEquals(listOf(attachment.id), saved.attachments.map(ShiftNoteAttachment::id))
        assertEquals(NoteDraft(), coordinator.uiState.value.noteDraft)
    }

    private fun coordinator(
        holidays: FakeHolidayRepository = FakeHolidayRepository(),
        notes: FakeNoteRepository = FakeNoteRepository(),
        shifts: FakeShiftRepository = FakeShiftRepository(listOf(SHIFT)),
        initial: ExceptionsPersistedState = ExceptionsPersistedState(
            surface = ExceptionsSurface.HOLIDAYS,
            holidayMonth = MONTH,
        ),
        persist: (ExceptionsPersistedState) -> Unit = {},
        uuidSeed: Long = 100L,
        noteFiles: NoteAttachmentFiles? = null,
        mutationGate: LocalDataMutationGate = LocalDataMutationGate(),
    ): ExceptionsCoordinator {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also(scopes::add)
        var nextUuid = uuidSeed
        return ExceptionsCoordinator(
            holidays = holidays,
            notes = notes,
            shifts = shifts,
            clock = CLOCK,
            uuidProvider = { uuid(nextUuid++) },
            scope = scope,
            initialState = initial,
            persist = persist,
            noteFiles = noteFiles,
            mutationGate = mutationGate,
        )
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 17)
        val MONTH: YearMonth = YearMonth.from(DATE)
        val NOW: Instant = Instant.parse("2026-08-23T12:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val SHIFT = Shift(
            id = uuid(10),
            startAt = DATE.atTime(8, 0).atZone(ZONE).toInstant(),
            endAt = DATE.atTime(16, 0).atZone(ZONE).toInstant(),
            zoneId = ZONE,
            localStartDate = DATE,
            objectiveNameSnapshot = "Lugar ficticio",
            objectiveAbbreviationSnapshot = "LFI",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(8, 0),
            endTimeSnapshot = LocalTime.of(16, 0),
            colorArgbSnapshot = 0xFF336699.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = uuid(11),
            createdAt = NOW,
            updatedAt = NOW,
        )

        fun uuid(value: Long): UUID = UUID(0L, value)
    }
}

private class FakeHolidayRepository(initial: List<Holiday> = emptyList()) : HolidayRepository {
    private val rows = MutableStateFlow(initial)
    val mutations = mutableListOf<HolidayBatchMutation>()
    var gate: CompletableDeferred<Unit>? = null
    var applyCalls: Int = 0
    var failure: Exception? = null

    override fun observeBetween(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<Holiday>> = rows

    override suspend fun getById(id: UUID): Holiday? = rows.value.firstOrNull { it.id == id }
    override suspend fun getByDate(date: LocalDate): Holiday? = rows.value.firstOrNull { it.date == date }

    override suspend fun insert(holiday: Holiday) {
        rows.value = rows.value + holiday
    }

    override suspend fun update(holiday: Holiday) {
        rows.value = rows.value.map { if (it.id == holiday.id) holiday else it }
    }

    override suspend fun delete(id: UUID) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun applyBatch(mutation: HolidayBatchMutation) {
        applyCalls++
        gate?.await()
        failure?.let { throw it }
        mutations += mutation
    }
}

private class FakeNoteRepository : ShiftNoteRepository {
    var referencesBarrier: Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>>? = null
    var deleteBarrier: Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>>? = null
    var saveRelease: CompletableDeferred<Unit>? = null
    var saveCalls = 0
    var failNextSave = false
    var failAfterNextSave = false
    private val aggregates = MutableStateFlow<List<ShiftNoteWithAttachments>>(emptyList())
    val count get() = aggregates.value.size
    var saved: ShiftNoteWithAttachments? = null

    override fun observeForShift(shiftId: UUID): Flow<List<ShiftNote>> =
        aggregates.map { rows -> rows.filter { it.note.shiftId == shiftId }.map(ShiftNoteWithAttachments::note) }
    override fun observeWithAttachmentsForShift(shiftId: UUID): Flow<List<ShiftNoteWithAttachments>> =
        aggregates.map { rows -> rows.filter { it.note.shiftId == shiftId } }
    override suspend fun getById(id: UUID): ShiftNote? =
        aggregates.value.firstOrNull { it.note.id == id }?.note
    override suspend fun getWithAttachments(id: UUID): ShiftNoteWithAttachments? =
        aggregates.value.firstOrNull { it.note.id == id }

    override suspend fun insert(note: ShiftNote) {
        aggregates.value = aggregates.value + ShiftNoteWithAttachments(note, emptyList())
    }

    override suspend fun update(note: ShiftNote) {
        aggregates.value = aggregates.value.map { row ->
            if (row.note.id == note.id) row.copy(note = note) else row
        }
    }

    override suspend fun saveWithAttachments(note: ShiftNote, attachments: List<ShiftNoteAttachment>) {
        saveCalls++
        saveRelease?.await()
        if (failNextSave) { failNextSave = false; error("Fallo sintético al guardar") }
        val aggregate = ShiftNoteWithAttachments(note, attachments)
        saved = aggregate
        aggregates.value = aggregates.value.filterNot { it.note.id == note.id } + aggregate
        if (failAfterNextSave) { failAfterNextSave = false; error("Confirmación sintética perdida después de guardar") }
    }

    override suspend fun delete(id: UUID) {
        aggregates.value = aggregates.value.filterNot { it.note.id == id }
        deleteBarrier?.let { (entered, release) -> deleteBarrier = null; entered.complete(Unit); release.await() }
    }

    override suspend fun allAttachmentStorageKeys(): Set<String> {
        val captured = aggregates.value.flatMap { it.attachments }.mapTo(linkedSetOf()) { it.storageKey }
        referencesBarrier?.let { (entered, release) -> referencesBarrier = null; entered.complete(Unit); release.await() }
        return captured
    }
}

private class FakeNoteFiles : NoteAttachmentFiles {
    val keys = linkedSetOf<String>()
    override suspend fun import(uri: android.net.Uri, id: UUID): StoredPhoto = error("Import is outside this fixture")
    override suspend fun discardUnreferenced(storageKey: String) { keys.remove(storageKey) }
    override suspend fun reconcileNoteAttachments(expectedStorageKeys: Set<String>) { keys.retainAll(expectedStorageKeys) }
    override fun exists(storageKey: String) = storageKey in keys
}

private class FakeShiftRepository(private val rows: List<Shift>) : ShiftRepository {
    override fun observeHasAny(): Flow<Boolean> = MutableStateFlow(rows.isNotEmpty())

    override fun observeStartingBetween(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ): Flow<List<Shift>> = MutableStateFlow(
        rows.filter { it.localStartDate in startDateInclusive..endDateInclusive },
    )

    override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> =
        MutableStateFlow(rows.filter { it.endAt > instantExclusive })

    override suspend fun getById(id: UUID): Shift? = rows.firstOrNull { it.id == id }
}
