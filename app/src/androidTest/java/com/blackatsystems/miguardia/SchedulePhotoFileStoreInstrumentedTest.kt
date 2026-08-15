package com.blackatsystems.miguardia

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
}
