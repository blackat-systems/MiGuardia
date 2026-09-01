package com.blackatsystems.miguardia.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupRecord
import com.blackatsystems.miguardia.core.domain.backup.BackupTable
import com.blackatsystems.miguardia.core.domain.backup.BackupValue
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupSchemaV5
import com.blackatsystems.miguardia.core.domain.backup.InvalidBackupException
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupPhotoFilesInstrumentedTest {
    @Test
    fun resolvedRowAlsoControlsThePhotoBytesWithoutSilentOverwrite() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "backup-photo-origin-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val context = IsolatedFilesContext(base, root)
        val currentBytes = Base64.getDecoder().decode(CURRENT_PNG)
        val incomingBytes = Base64.getDecoder().decode(INCOMING_PNG)
        check(currentBytes.size == incomingBytes.size)
        val livePhoto = File(context.filesDir, "schedule_photos/$STORAGE_KEY").also {
            it.parentFile?.mkdirs()
            it.writeBytes(currentBytes)
        }
        val incomingDirectory = File(root, "incoming").also { it.mkdirs() }
        File(incomingDirectory, STORAGE_KEY).writeBytes(incomingBytes)
        val current = snapshot("Actual")
        val incoming = snapshot("Copia")
        val files = BackupPhotoFiles(context)
        try {
            val keepCurrent = File(root, "keep-current")
            files.materializeDesired(
                snapshot = current,
                incomingPhotoDirectory = incomingDirectory,
                destination = keepCurrent,
                currentSnapshot = current,
                incomingSnapshot = incoming,
            )
            assertTrue(File(keepCurrent, STORAGE_KEY).readBytes().contentEquals(currentBytes))

            val useBackup = File(root, "use-backup")
            files.materializeDesired(
                snapshot = incoming,
                incomingPhotoDirectory = incomingDirectory,
                destination = useBackup,
                currentSnapshot = current,
                incomingSnapshot = incoming,
            )
            assertTrue(File(useBackup, STORAGE_KEY).readBytes().contentEquals(incomingBytes))
            assertTrue(livePhoto.readBytes().contentEquals(currentBytes))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun finalVerificationRejectsDifferentBytesWithTheSameSizeAndDimensions() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "backup-photo-hash-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val context = IsolatedFilesContext(base, root)
        val currentBytes = Base64.getDecoder().decode(CURRENT_PNG)
        val incomingBytes = Base64.getDecoder().decode(INCOMING_PNG)
        check(currentBytes.size == incomingBytes.size)
        File(context.filesDir, "schedule_photos/$STORAGE_KEY").also {
            it.parentFile?.mkdirs()
            it.writeBytes(currentBytes)
        }
        try {
            try {
                BackupPhotoFiles(context).verify(
                    snapshot("Actual"),
                    mapOf(STORAGE_KEY to incomingBytes.sha256()),
                )
                fail("La verificación debía rechazar bytes distintos")
            } catch (_: InvalidBackupException) {
                // Expected: dimensions, MIME and size are insufficient without the authenticated hash.
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun interruptedDirectorySwapKeepsExactlyTheCommittedPhotoSet() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val isolatedRoot = File(base.cacheDir, "backup-photo-swap-${UUID.randomUUID()}").also {
            check(it.mkdirs())
        }
        val context = IsolatedFilesContext(base, isolatedRoot)
        val files = BackupPhotoFiles(context)
        val live = File(context.filesDir, "schedule_photos").also { check(it.mkdirs()) }
        val previous = File(context.filesDir, "schedule_photos.restore-previous").also {
            check(it.mkdirs())
        }
        val unfinished = File(context.filesDir, "schedule_photos.restore-new-${UUID.randomUUID()}").also {
            check(it.mkdirs())
        }
        File(live, "new-marker").writeText("new")
        File(previous, "old-marker").writeText("old")
        File(unfinished, "partial-marker").writeText("partial")
        try {
            files.cleanupInterruptedSwap()

            assertEquals(listOf("new-marker"), live.listFiles().orEmpty().map(File::getName))
            assertFalse(previous.exists())
            assertFalse(unfinished.exists())

            check(live.deleteRecursively())
            check(previous.mkdirs())
            File(previous, "old-marker").writeText("old")
            val abandoned = File(
                context.filesDir,
                "schedule_photos.restore-new-${UUID.randomUUID()}",
            ).also { check(it.mkdirs()) }
            File(abandoned, "partial-marker").writeText("partial")

            files.cleanupInterruptedSwap()

            assertEquals(listOf("old-marker"), live.listFiles().orEmpty().map(File::getName))
            assertFalse(previous.exists())
            assertFalse(abandoned.exists())
        } finally {
            isolatedRoot.deleteRecursively()
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private fun snapshot(objectiveName: String): BackupDatabaseSnapshot = BackupDatabaseSnapshot(
        timelineId = null,
        tables = MiGuardiaBackupSchemaV5.tables.map { spec ->
            BackupTable(
                spec.name,
                spec.columns,
                spec.primaryKey,
                if (spec.name == "schedule_photos") {
                    listOf(
                        BackupRecord(
                            listOf(
                                BackupValue.Text(PHOTO_ID),
                                BackupValue.Text("2026-08"),
                                BackupValue.Null,
                                BackupValue.Text(objectiveName),
                                BackupValue.Text("FIC"),
                                BackupValue.Text(STORAGE_KEY),
                                BackupValue.Text("image/png"),
                                BackupValue.Integer(67),
                                BackupValue.Integer(1),
                                BackupValue.Integer(1),
                                BackupValue.Integer(1_788_131_400_000L),
                                BackupValue.Integer(1_788_131_400_000L),
                            ),
                        ),
                    )
                } else {
                    emptyList()
                },
            )
        },
    )

    private class IsolatedFilesContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
    }

    private companion object {
        const val PHOTO_ID = "44444444-4444-4444-8444-444444444444"
        const val STORAGE_KEY = "55555555-5555-4555-8555-555555555555.png"
        const val CURRENT_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZQAAAABJRU5ErkJggg=="
        const val INCOMING_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAAAAAA6fptVAAAACklEQVR4nGNgAAAAAgABSK+kcQAAAABJRU5ErkJggg=="
    }
}
