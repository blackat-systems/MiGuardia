package com.blackatsystems.miguardia

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.backup.BackupPhotoFiles
import com.blackatsystems.miguardia.backup.LocalDataMutationGate
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoteRepository
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsCoordinator
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsPersistedState
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsSurface
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import java.io.File
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite and private-file restore primitives, with deterministic suspension barriers.
 * This isolates R04; it does not claim a new SAF, journal or physical navigation check. */
@RunWith(AndroidJUnit4::class)
class Stage4NoteRestoreConcurrencyTest {
    @Test fun startupReconciliationCannotDeleteAnAttachmentConfirmedByConcurrentRestore() = runBlocking {
        exerciseRace(reconciliation = true)
    }

    @Test fun noteDeletionCleanupCannotDeleteAnAttachmentConfirmedByConcurrentRestore() = runBlocking {
        exerciseRace(reconciliation = false)
    }

    private suspend fun exerciseRace(reconciliation: Boolean) = kotlinx.coroutines.coroutineScope {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "stage4-note-restore-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val context = IsolatedContext(base, root)
        val databaseName = "stage4-note-restore-${UUID.randomUUID()}.db"
        val store = LocalDataStore.create(context, databaseName)
        val coordinatorJob = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val coordinatorScope = CoroutineScope(coroutineContext + coordinatorJob)
        val operationEntered = CompletableDeferred<Unit>()
        val releaseOperation = CompletableDeferred<Unit>()
        try {
            val shift = Shift(UUID.randomUUID(), NOW, NOW.plusSeconds(3_600), ZoneOffset.UTC,
                DATE, "Institución sintética", "SINT", null, LocalTime.NOON, LocalTime.of(13, 0),
                0xff405080.toInt(), null, ShiftStatus.PLANNED, UUID.randomUUID(), NOW, NOW)
            val write = V2AppTestFixture.writeFor(store, shift, DATE)
            store.v2Shifts.insert(write)
            val note = ShiftNote(UUID.randomUUID(), shift.id, "Nota sintética restaurable", NOW, NOW)
            val id = UUID.randomUUID()
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val bytes = try {
                ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    output.toByteArray()
                }
            } finally { bitmap.recycle() }
            val attachment = ShiftNoteAttachment(id, note.id, "note_$id.png", "image/png",
                bytes.size.toLong(), 1, 1, 0, NOW, NOW)
            store.shiftNotes.saveWithAttachments(note, listOf(attachment))
            val desired = store.backups.capture()
            val photoFiles = BackupPhotoFiles(context)
            val incoming = File(root, "incoming").also { check(it.mkdirs()) }
            File(incoming, attachment.storageKey).writeBytes(bytes)
            photoFiles.replaceFrom(desired, incoming)
            photoFiles.verify(desired)
            if (reconciliation) {
                store.shiftNotes.delete(note.id)
                SchedulePhotoFileStore(context).discardUnreferenced(attachment.storageKey)
            }
            val gate = LocalDataMutationGate()
            val repository = object : ShiftNoteRepository by store.shiftNotes {
                override suspend fun allAttachmentStorageKeys(): Set<String> {
                    val captured = store.shiftNotes.allAttachmentStorageKeys()
                    if (reconciliation && !operationEntered.isCompleted) {
                        operationEntered.complete(Unit)
                        releaseOperation.await()
                    }
                    return captured
                }

                override suspend fun delete(id: UUID) {
                    store.shiftNotes.delete(id)
                    if (!reconciliation) {
                        operationEntered.complete(Unit)
                        releaseOperation.await()
                    }
                }
            }
            val coordinator = ExceptionsCoordinator(store.holidays, repository, store.shifts,
                Clock.fixed(NOW, ZoneOffset.UTC), UUID::randomUUID, SchedulePhotoFileStore(context),
                coordinatorScope, ExceptionsPersistedState(ExceptionsSurface.NONE, YearMonth.from(DATE)), {},
                mutationGate = gate)
            // The first child is the startup reconciliation launch; the other observes holidays.
            val reconciliationJob = coordinatorJob.children.first()
            if (!reconciliation) {
                withTimeout(TIMEOUT) { reconciliationJob.join() }
                coordinator.deleteNote(note.id)
            }
            withTimeout(TIMEOUT) { operationEntered.await() }
            val expectedCurrent = store.backups.capture()
            val restoreEntered = CompletableDeferred<Unit>()
            val restore = async(start = CoroutineStart.UNDISPATCHED) {
                gate.withExclusiveMutation {
                    restoreEntered.complete(Unit)
                    store.backups.replaceWithWriteBarrier(expectedCurrent, desired,
                        beforeReplace = {},
                        afterReplace = { photoFiles.replaceFrom(desired, incoming) })
                    photoFiles.verify(desired)
                }
            }
            // If restore acquired the gate, finish it before resuming the stale cleanup.
            // If the note operation owns the gate, finish that operation first instead.
            // UNDISTPATCHED makes the gate-attempt observation deterministic, without a sleep.
            if (restoreEntered.isCompleted) withTimeout(TIMEOUT) { restore.await() }
            releaseOperation.complete(Unit)
            withTimeout(TIMEOUT) {
                restore.await()
                if (reconciliation) reconciliationJob.join()
                else coordinator.uiState.first { !it.isSaving }
            }
            assertEquals(desired, store.backups.capture())
            assertEquals(setOf(attachment.storageKey), store.shiftNotes.allAttachmentStorageKeys())
            assertTrue("A confirmed restored note must retain its private bytes",
                File(context.filesDir, "schedule_photos/${attachment.storageKey}").isFile)
            photoFiles.verify(desired)
        } finally {
            releaseOperation.complete(Unit)
            coordinatorJob.cancelAndJoin()
            store.close()
            base.deleteDatabase(databaseName)
            check(root.canonicalFile.parentFile == base.cacheDir.canonicalFile)
            root.deleteRecursively()
        }
    }

    private class IsolatedContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no-backup").also { it.mkdirs() }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-02T12:00:00Z")
        val DATE: LocalDate = LocalDate.of(2026, 10, 2)
        const val TIMEOUT = 30_000L
    }
}
