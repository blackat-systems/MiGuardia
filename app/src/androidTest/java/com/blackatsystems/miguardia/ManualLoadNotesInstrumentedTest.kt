package com.blackatsystems.miguardia

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoteRepository
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.ui.management.ManualLoadNotes
import com.blackatsystems.miguardia.ui.management.decodeManualDraft
import com.blackatsystems.miguardia.ui.management.encodeManualDraft
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated synthetic files + explicit fake SQL boundary, no application database or personal files. */
@RunWith(AndroidJUnit4::class)
class ManualLoadNotesInstrumentedTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(app.cacheDir, "manual-note-contract-${UUID.randomUUID()}")
    private val context = object : ContextWrapper(app) { override fun getFilesDir() = root }
    private val repository = Notes()
    private val files = ManualLoadNotes(context, repository)
    private val operation = UUID.randomUUID()
    private val now = Instant.parse("2026-09-16T00:00:00Z")
    private val stored get() = File(root, "schedule_photos")

    @After fun cleanup() { root.deleteRecursively() }

    @Test fun multipleNotesOwnIndependentImageCopiesAndRemovingOnePreservesTheOther() = runBlocking {
        val draft = draft()
        val mutation = V2ShiftBatchMutation(shiftsToInsert = listOf(write(0), write(1)))
        files.commit(mutation, "Nota compartida", listOf(draft), operation) { repository.capture(it) }
        assertEquals(2, repository.rows.size)
        val images = repository.rows.flatMap { it.attachments }
        assertEquals(2, images.map { it.storageKey }.distinct().size)
        assertEquals(2, repository.rows.map { it.note.id }.distinct().size)
        assertTrue(repository.rows.all { it.note.body == "Nota compartida" })
        images.forEach { assertArrayEquals(BYTES, File(stored, it.storageKey).readBytes()) }
        val removed = repository.rows.removeAt(0)
        SchedulePhotoFileStore(context).discardUnreferenced(removed.attachments.single().storageKey)
        assertTrue(File(stored, repository.rows.single().attachments.single().storageKey).exists())
    }

    @Test fun rejectedSqlRemovesPromotedCopiesAndPreservesDraftForRetry() = runBlocking {
        val draft = draft()
        try {
            files.commit(V2ShiftBatchMutation(shiftsToInsert = listOf(write(0), write(1))), "Nota", listOf(draft), operation) {
                error("Synthetic atomic SQL rejection")
            }
            fail("Expected rejection")
        } catch (_: IllegalStateException) { }
        assertTrue(repository.rows.isEmpty())
        assertTrue(stored.listFiles().orEmpty().isEmpty())
        assertTrue(File(root, "manual_load_drafts/${draft.storageKey}").exists())
    }

    @Test fun uncertainSqlReturnNeverDeletesFilesThatWereCommitted() = runBlocking {
        val draft = draft()
        try {
            files.commit(V2ShiftBatchMutation(shiftsToInsert = listOf(write(0))), "Nota", listOf(draft), operation) {
                repository.capture(it)
                error("Synthetic lost acknowledgement after SQL commit")
            }
            fail("Expected lost acknowledgement")
        } catch (_: IllegalStateException) { }
        assertEquals(1, repository.rows.size)
        assertTrue(File(stored, repository.rows.single().attachments.single().storageKey).exists())
    }

    @Test fun cancellingDraftDoesNotDeleteAnyCommittedImageAndDraftMetadataRoundTrips() = runBlocking {
        val draft = draft()
        assertEquals(draft, decodeManualDraft(draft.encodeManualDraft()))
        files.commit(V2ShiftBatchMutation(shiftsToInsert = listOf(write(0))), "Nota", listOf(draft), operation) { repository.capture(it) }
        files.discard(listOf(draft))
        assertFalse(File(root, "manual_load_drafts/${draft.storageKey}").exists())
        assertTrue(File(stored, repository.rows.single().attachments.single().storageKey).exists())
    }

    @Test fun identicalTechnicalRetryReusesExactImagesWithoutCreatingNewCopies() = runBlocking {
        val draft = draft()
        val mutation = V2ShiftBatchMutation(shiftsToInsert = listOf(write(0)))
        files.commit(mutation, "Nota", listOf(draft), operation) { repository.capture(it) }
        val expected = repository.rows.toList()
        files.commit(mutation, "Nota", listOf(draft), operation) { assertEquals(expected, it.notesToInsert) }
        assertEquals(1, stored.listFiles().orEmpty().size)
        assertEquals(expected, repository.rows)
    }

    private fun draft(): ShiftNoteAttachment {
        val id = UUID.randomUUID()
        val key = "note_$id.png"
        File(root, "manual_load_drafts/$key").also { it.parentFile!!.mkdirs(); it.writeBytes(BYTES) }
        return ShiftNoteAttachment(id, operation, key, "image/png", BYTES.size.toLong(), 1, 1, 0, now, now)
    }

    private fun write(day: Long): V2ShiftWrite {
        val id = UUID.nameUUIDFromBytes("$operation:$day".toByteArray())
        val objective = UUID(0, 2)
        val date = LocalDate.of(2026, 9, 16).plusDays(day)
        val shift = Shift(id, date.atTime(8, 0).toInstant(ZoneOffset.UTC), date.atTime(16, 0).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC, date, "Institución sintética", "SYN", null, LocalTime.of(8, 0), LocalTime.of(16, 0),
            0xFF336699.toInt(), null, ShiftStatus.PLANNED, objective, now, now)
        return V2ShiftWrite(shift, ShiftWorkSnapshot(id, UUID(0, 1), WorkSector.NURSING, UUID(0, 3), UUID(0, 4),
            objective, UUID(0, 5), UUID(0, 6), "Jornada", WorkTypeBehavior.ACTIVE_WORK))
    }

    private class Notes : ShiftNoteRepository {
        val rows = mutableListOf<ShiftNoteWithAttachments>()
        fun capture(mutation: V2ShiftBatchMutation) { rows += mutation.notesToInsert }
        override fun observeForShift(shiftId: UUID) = flowOf(rows.filter { it.note.shiftId == shiftId }.map { it.note })
        override suspend fun getById(id: UUID) = rows.firstOrNull { it.note.id == id }?.note
        override suspend fun insert(note: ShiftNote) = error("Not used")
        override suspend fun update(note: ShiftNote) = error("Not used")
        override suspend fun delete(id: UUID) { rows.removeAll { it.note.id == id } }
        override suspend fun allAttachmentStorageKeys() = rows.flatMap { it.attachments }.map { it.storageKey }.toSet()
    }

    private companion object {
        val BYTES = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZQAAAABJRU5ErkJggg==")
    }
}
