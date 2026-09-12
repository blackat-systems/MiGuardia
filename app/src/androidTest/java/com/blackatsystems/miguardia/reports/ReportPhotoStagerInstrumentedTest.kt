package com.blackatsystems.miguardia.reports

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.core.domain.report.ReportPhotoRow
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportPhotoStagerInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val isolatedFiles = File(context.cacheDir, "report-stager-${UUID.randomUUID()}")
    private val isolatedContext = object : ContextWrapper(context) {
        override fun getFilesDir(): File = isolatedFiles
    }
    private val sourceStore = SchedulePhotoFileStore(isolatedContext)
    private val stager = ReportPhotoStager(isolatedContext, sourceStore, CLOCK)
    private val sourceRoot = File(isolatedFiles, "schedule_photos")
    private val stagingRoot = File(isolatedFiles, "reports/staging")

    @Before
    fun setUp() {
        isolatedFiles.deleteRecursively()
        sourceRoot.mkdirs()
    }

    @After
    fun cleanUp() {
        isolatedFiles.deleteRecursively()
    }

    @Test
    fun freezeCopiesAndValidatesAnOpaquePrivateAssetThenReleaseRemovesIt() = runBlocking {
        val source = createPng("${PHOTO_ID}.png")
        val sourceBytes = source.readBytes()
        val metadata = metadata(source)

        val frozen = stager.freeze(listOf(metadata), listOf(ReportPhotoRow(0, "Lugar histórico")))
        val asset = frozen.photos.single()

        assertTrue(asset.file.isFile)
        assertTrue(asset.file.canonicalPath.startsWith(stagingRoot.canonicalPath + File.separator))
        assertFalse(asset.file.name.contains(PHOTO_ID.toString()))
        assertArrayEquals(sourceBytes, asset.file.readBytes())
        assertEquals("Lugar histórico", asset.caption)
        source.writeBytes("cambio posterior".toByteArray())
        assertArrayEquals(sourceBytes, asset.file.readBytes())

        val session = requireNotNull(frozen.stagingDirectory)
        stager.release(frozen)
        assertFalse(session.exists())
    }

    @Test
    fun aMissingOrChangedOriginalAbortsWithoutLeavingAStagingSession() = runBlocking {
        val source = createPng("${PHOTO_ID}.png")
        val changedMetadata = metadata(source).copy(byteSize = source.length() + 1L)

        val changedFailure = runCatching {
            stager.freeze(listOf(changedMetadata), listOf(ReportPhotoRow(0, "Foto")))
        }.exceptionOrNull()
        assertTrue(changedFailure is ReportAssetException)
        assertTrue(stagingRoot.listFiles().orEmpty().isEmpty())

        source.delete()
        val missingFailure = runCatching {
            stager.freeze(listOf(metadata(source).copy(byteSize = 10L)), listOf(ReportPhotoRow(0, "Foto")))
        }.exceptionOrNull()
        assertTrue(missingFailure is ReportAssetException)
        assertTrue(stagingRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aNewFreezePrunesOnlyExpiredValidOrphansAndKeepsActiveSessions() = runBlocking {
        stagingRoot.mkdirs()
        val expired = stagingDirectory('a').apply {
            assertTrue(setLastModified(NOW.minus(Duration.ofHours(24)).minusMillis(1).toEpochMilli()))
        }
        val exactBoundary = stagingDirectory('b').apply {
            assertTrue(setLastModified(NOW.minus(Duration.ofHours(24)).toEpochMilli()))
        }
        val recent = stagingDirectory('c').apply {
            assertTrue(setLastModified(NOW.minus(Duration.ofHours(1)).toEpochMilli()))
        }
        val invalid = File(stagingRoot, "not-a-report-session").apply {
            mkdirs()
            assertTrue(setLastModified(NOW.minus(Duration.ofDays(2)).toEpochMilli()))
        }

        stager.freeze(emptyList(), emptyList())

        assertFalse(expired.exists())
        assertTrue(exactBoundary.exists())
        assertTrue(recent.exists())
        assertTrue(invalid.exists())

        val source = createPng("${PHOTO_ID}.png")
        val active = stager.freeze(listOf(metadata(source)), listOf(ReportPhotoRow(0, "Foto activa")))
        val activeDirectory = requireNotNull(active.stagingDirectory)
        assertTrue(activeDirectory.setLastModified(NOW.minus(Duration.ofDays(2)).toEpochMilli()))

        ReportPhotoStager(isolatedContext, sourceStore, CLOCK).freeze(emptyList(), emptyList())

        assertTrue(activeDirectory.exists())
        stager.release(active)
        assertFalse(activeDirectory.exists())
    }

    private fun stagingDirectory(character: Char): File = File(
        stagingRoot,
        character.toString().repeat(32),
    ).apply { assertTrue(mkdirs()) }

    private fun createPng(name: String): File {
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(80, 140, 210))
            return File(sourceRoot, name).also { file ->
                FileOutputStream(file).use { output ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun metadata(file: File): SchedulePhoto = SchedulePhoto(
        id = PHOTO_ID,
        month = YearMonth.of(2026, 8),
        objectiveId = null,
        objectiveNameSnapshot = "Lugar histórico",
        objectiveAbbreviationSnapshot = "LH",
        storageKey = file.name,
        mimeType = "image/png",
        byteSize = file.length(),
        pixelWidth = 80,
        pixelHeight = 40,
        createdAt = Instant.parse("2026-08-29T10:00:00Z"),
        updatedAt = Instant.parse("2026-08-29T10:00:00Z"),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-29T12:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val PHOTO_ID: UUID = UUID.fromString("18000000-0000-0000-0000-000000000001")
    }
}
