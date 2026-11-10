package com.blackatsystems.miguardia

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.content.FileProvider
import com.blackatsystems.miguardia.core.domain.model.CancellationEvidence
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchedulePhotoFileStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val isolatedFilesDir = File(context.cacheDir, "schedule-photo-store-test-${UUID.randomUUID()}")
    private val isolatedContext = object : ContextWrapper(context) {
        override fun getFilesDir(): File = isolatedFilesDir
    }
    private val root = File(isolatedFilesDir, "schedule_photos")
    private val fileStore = SchedulePhotoFileStore(isolatedContext)

    @Before
    fun prepare() {
        root.deleteRecursively()
        root.mkdirs()
    }

    @After
    fun cleanUp() {
        isolatedFilesDir.deleteRecursively()
    }

    @Test
    fun reconciliationKeepsOnlyTheExactVersionReferencedByMetadata() = runBlocking {
        val owner = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val expected = "${owner}_a1b2c3d4.jpg"
        val stale = "$owner.jpg"
        val orphan = "20000000-0000-0000-0000-000000000002.png"
        File(root, expected).writeText("expected")
        File(root, stale).writeText("stale")
        File(root, orphan).writeText("orphan")
        File(root, "$owner.webp.tmp").writeText("partial")

        fileStore.reconcile { id -> if (id == owner) expected else null }

        assertTrue(File(root, expected).exists())
        assertFalse(File(root, stale).exists())
        assertFalse(File(root, orphan).exists())
        assertFalse(File(root, "$owner.webp.tmp").exists())
    }

    @Test
    fun reconciliationRestoresTrashWhenMetadataStillReferencesIt() = runBlocking {
        val owner = UUID.fromString("30000000-0000-0000-0000-000000000003")
        val expected = "$owner.jpg"
        File(root, "$expected.trash").writeText("recoverable")

        fileStore.reconcile { id -> if (id == owner) expected else null }

        assertTrue(File(root, expected).exists())
        assertFalse(File(root, "$expected.trash").exists())
    }

    @Test
    fun photoAndOwnerReconciliationPreserveReferencedTrashInEitherOrder() = runBlocking {
        for (photosFirst in listOf(true, false)) {
            root.listFiles().orEmpty().forEach { check(it.delete()) }
            val noteKey = "note_${UUID.randomUUID()}.png"
            val evidenceKey = "cancel_${UUID.randomUUID()}_1234abcd.png"
            val noteBytes = "synthetic private note bytes".toByteArray()
            val evidenceBytes = "synthetic cancellation evidence".toByteArray()
            // Isolated recovery-state fixture. This does not kill an app or emulate a
            // physical crash: metadata references keys whose bytes are still in trash.
            File(root, "$noteKey.trash").writeBytes(noteBytes)
            File(root, "$evidenceKey.trash").writeBytes(evidenceBytes)
            val orphanPhoto = File(root, "${UUID.randomUUID()}.jpg").apply { writeText("orphan") }
            if (photosFirst) fileStore.reconcile { null }
            fileStore.reconcileNoteAttachments(setOf(noteKey))
            fileStore.reconcileCancellationEvidence(setOf(evidenceKey))
            if (!photosFirst) fileStore.reconcile { null }
            assertTrue("Note trash must survive photos-first=$photosFirst", File(root, noteKey).isFile)
            assertTrue("Evidence trash must survive photos-first=$photosFirst", File(root, evidenceKey).isFile)
            assertArrayEquals(noteBytes, File(root, noteKey).readBytes())
            assertArrayEquals(evidenceBytes, File(root, evidenceKey).readBytes())
            assertFalse(File(root, "$noteKey.trash").exists())
            assertFalse(File(root, "$evidenceKey.trash").exists())
            assertFalse(orphanPhoto.exists())
        }
    }

    @Test
    fun photoReconciliationLeavesNamespacedTemporaryFilesForTheirOwners() = runBlocking {
        val noteTemp = "note_${UUID.randomUUID()}.png.tmp"
        val noteTrash = "note_${UUID.randomUUID()}.png.trash"
        val evidenceTemp = "cancel_${UUID.randomUUID()}_abcd1234.png.tmp"
        val evidenceTrash = "cancel_${UUID.randomUUID()}_1234abcd.png.trash"
        val ownFiles = listOf(noteTemp, noteTrash, evidenceTemp, evidenceTrash)
        ownFiles.forEach { File(root, it).writeText("synthetic pending owner cleanup") }
        fileStore.reconcile { null }
        assertEquals(ownFiles.toSet(), root.listFiles().orEmpty().map { it.name }.toSet())
        fileStore.reconcileNoteAttachments(emptySet())
        fileStore.reconcileCancellationEvidence(emptySet())
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun failedMetadataDeletionRestoresTheOriginalFile() = runBlocking {
        val owner = UUID.fromString("40000000-0000-0000-0000-000000000004")
        val key = "$owner.jpg"
        val original = File(root, key).apply { writeText("recoverable") }

        try {
            fileStore.removeRecoverably(key) { error("Room failure") }
            fail("La eliminación debía fallar")
        } catch (_: IllegalStateException) {
            // Expected: the file must be restored before propagating the failure.
        }

        assertTrue(original.exists())
        assertFalse(File(root, "$key.trash").exists())
    }

    @Test
    fun failedImportWithExistingIdentityNeverDeletesConfirmedBytes() = runBlocking {
        val owner = UUID.randomUUID()
        val key = "note_$owner.png"
        val confirmed = Base64.getDecoder().decode(ONE_PIXEL_PNG_BASE64)
        File(root, key).writeBytes(confirmed)
        val source = imageSource("unreadable-image".toByteArray())
        try {
            try {
                fileStore.import(sourceUri(source), owner, namespace = SchedulePhotoFileStore.NOTE_NAMESPACE)
                fail("Existing identity must be rejected")
            } catch (_: Exception) { }
            assertTrue(File(root, key).exists())
            assertArrayEquals(confirmed, File(root, key).readBytes())
            assertFalse(File(root, "$key.tmp").exists())
        } finally { source.delete() }
    }

    @Test
    fun unreadableAndOversizedImagesLeaveNoOwnFilesAndPreserveEarlierImage() = runBlocking {
        val retained = File(root, "note_${UUID.randomUUID()}.png").apply {
            writeBytes(Base64.getDecoder().decode(ONE_PIXEL_PNG_BASE64))
        }
        val expected = retained.readBytes()
        for (oversized in listOf(false, true)) {
            val source = imageSource("unreadable-image".toByteArray())
            if (oversized) java.io.RandomAccessFile(source, "rw").use {
                it.setLength(com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupContract.MAX_SINGLE_PHOTO_BYTES + 1)
            }
            try {
                try {
                    fileStore.import(sourceUri(source), UUID.randomUUID(), namespace = SchedulePhotoFileStore.NOTE_NAMESPACE)
                    fail("Invalid image must be rejected")
                } catch (_: Exception) { }
                assertEquals(listOf(retained.name), root.listFiles().orEmpty().map { it.name })
                assertArrayEquals(expected, retained.readBytes())
            } finally { source.delete() }
        }
    }

    @Test
    fun noSpaceRejectsBeforeCreatingFilesAndPreservesEarlierImage() = runBlocking {
        val retained = File(root, "note_${UUID.randomUUID()}.png").apply { writeText("confirmed") }
        val source = imageSource(Base64.getDecoder().decode(ONE_PIXEL_PNG_BASE64))
        try {
            try {
                SchedulePhotoFileStore(isolatedContext, draftOnly = false, allocatableBytes = { 0L })
                    .import(sourceUri(source), UUID.randomUUID(), namespace = SchedulePhotoFileStore.NOTE_NAMESPACE)
                fail("No-space preflight must reject")
            } catch (error: java.io.IOException) {
                assertEquals("Sin espacio disponible", error.message)
            }
            assertEquals(listOf(retained.name), root.listFiles().orEmpty().map { it.name })
            assertEquals("confirmed", retained.readText())
        } finally { source.delete() }
    }

    private fun imageSource(bytes: ByteArray) = File(context.filesDir,
        "reports/artifacts/stage4-image-${UUID.randomUUID()}.png").also {
        it.parentFile!!.mkdirs()
        it.writeBytes(bytes)
    }

    private fun sourceUri(source: File) = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", source)

    @Test
    fun cancellationNamespaceRoundTripsFromPrivateStoreIntoTheDomainModel() = runBlocking {
        val owner = UUID.fromString("50000000-0000-0000-0000-000000000005")
        val source = File(context.filesDir, "reports/artifacts/cancel-source-${UUID.randomUUID()}.png").also {
            it.parentFile?.mkdirs()
            it.writeBytes(Base64.getDecoder().decode(ONE_PIXEL_PNG_BASE64))
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", source)
            val stored = fileStore.import(
                uri = uri,
                ownerId = owner,
                versioned = true,
                namespace = SchedulePhotoFileStore.CANCEL_NAMESPACE,
            )
            val evidence = CancellationEvidence(
                storageKey = stored.storageKey,
                mimeType = stored.mimeType,
                byteSize = stored.byteSize,
                pixelWidth = stored.width,
                pixelHeight = stored.height,
                sha256 = MessageDigest.getInstance("SHA-256").digest(fileStore.file(stored.storageKey).readBytes())
                    .joinToString("") { "%02x".format(it) },
            )

            assertTrue(evidence.storageKey.startsWith("cancel_${owner}_"))
            assertEquals("image/png", evidence.mimeType)
            assertTrue(fileStore.file(evidence.storageKey).isFile)
        } finally {
            source.delete()
        }
    }

    private companion object {
        const val ONE_PIXEL_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZQAAAABJRU5ErkJggg=="
    }
}
