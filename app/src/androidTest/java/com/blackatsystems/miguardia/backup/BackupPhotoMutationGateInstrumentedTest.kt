package com.blackatsystems.miguardia.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.ui.photos.PhotosViewModel
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupPhotoMutationGateInstrumentedTest {
    @Test
    fun restoreGatePreventsPhotoFileAndRoomRowFromBeingSplit() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "backup-photo-gate-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val context = IsolatedFilesContext(base, root)
        val databaseName = "backup-photo-gate-${UUID.randomUUID()}.db"
        val source = File(base.filesDir, "reports/artifacts/gate-${UUID.randomUUID()}.png").also {
            it.parentFile?.mkdirs()
            it.writeBytes(Base64.getDecoder().decode(PNG))
        }
        val sourceUri = FileProvider.getUriForFile(
            base,
            "${base.packageName}.fileprovider",
            source,
        )
        val store = LocalDataStore.create(context, databaseName)
        val gate = LocalDataMutationGate()
        val restoreEntered = CompletableDeferred<Unit>()
        val releaseRestore = CompletableDeferred<Unit>()
        try {
            val restore = launch {
                gate.withExclusiveMutation {
                    restoreEntered.complete(Unit)
                    releaseRestore.await()
                }
            }
            restoreEntered.await()
            val viewModel = PhotosViewModel(
                repository = store.schedulePhotos,
                objectives = store.objectives,
                fileStore = SchedulePhotoFileStore(context),
                savedState = SavedStateHandle(),
                mutationGate = gate,
                clock = CLOCK,
            )
            val import = viewModel.import(listOf(sourceUri))

            delay(100)
            assertTrue(File(context.filesDir, "schedule_photos").listFiles().orEmpty().isEmpty())
            assertTrue(store.schedulePhotos.observeForMonth(MONTH).first().isEmpty())

            releaseRestore.complete(Unit)
            restore.join()
            import.join()

            val photos = store.schedulePhotos.observeForMonth(MONTH).first()
            assertEquals(1, photos.size)
            assertTrue(File(context.filesDir, "schedule_photos/${photos.single().storageKey}").isFile)
        } finally {
            store.close()
            base.deleteDatabase(databaseName)
            source.delete()
            root.deleteRecursively()
        }
    }

    private class IsolatedFilesContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no-backup").also { it.mkdirs() }
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZONE)
        val MONTH: YearMonth = YearMonth.of(2026, 8)
        const val PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZQAAAABJRU5ErkJggg=="
    }
}
