package com.blackatsystems.miguardia.reports

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.content.FileProvider
import com.blackatsystems.miguardia.core.domain.report.ReportFormat
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportDestinationWriterInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(
        context.filesDir,
        "${ReportArtifactStore.ARTIFACT_DIRECTORY}/destination-test-${UUID.randomUUID()}",
    ).apply { mkdirs() }
    private val authority = "${context.packageName}.fileprovider"
    private val writer = ReportDestinationWriter(context.contentResolver)

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun saveCopiesTheCompleteArtifactToTheChosenDocument() = runBlocking {
        val sourceBytes = "%PDF-1.4\ncontenido español completo".toByteArray(Charsets.UTF_8)
        val source = File(root, "opaque.pdf").apply { writeBytes(sourceBytes) }
        val destination = File(root, "chosen-document.pdf").apply {
            writeBytes(ByteArray(sourceBytes.size + 4_096) { 0x5A })
        }
        val artifact = ReportArtifact(
            source,
            ReportFormat.PDF,
            "MiGuardia_2026-08_informe_parcial.pdf",
            source.length(),
        )

        writer.save(artifact, FileProvider.getUriForFile(context, authority, destination))

        assertArrayEquals(sourceBytes, destination.readBytes())
        assertEquals(sourceBytes.size.toLong(), destination.length())
        assertArrayEquals(sourceBytes, source.readBytes())
    }

    @Test
    fun failedDestinationNeverDeletesOrChangesTheValidPrivateArtifact() = runBlocking {
        val sourceBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3)
        val source = File(root, "opaque.xlsx").apply { writeBytes(sourceBytes) }
        val artifact = ReportArtifact(
            source,
            ReportFormat.XLSX,
            "MiGuardia_2026-08_informe_parcial.xlsx",
            source.length(),
        )
        val missingDestination = Uri.parse("content://com.blackatsystems.miguardia.missing/document")

        val failure = runCatching { writer.save(artifact, missingDestination) }.exceptionOrNull()

        assertTrue(failure is ReportDestinationException)
        assertTrue(source.exists())
        assertArrayEquals(sourceBytes, source.readBytes())
        assertFalse(File(root, "document").exists())
    }

    @Test
    fun discardRemovesALateEmptyDestination() = runBlocking {
        val destination = File(root, "late-empty.pdf").apply { createNewFile() }

        assertTrue(writer.discard(FileProvider.getUriForFile(context, authority, destination)))
        assertFalse(destination.exists())
    }

    @Test
    fun discardNeverDeletesANonEmptyDocument() = runBlocking {
        val content = "contenido existente".toByteArray(Charsets.UTF_8)
        val destination = File(root, "late-non-empty.pdf").apply { writeBytes(content) }
        val uri = FileProvider.getUriForFile(context, authority, destination)

        assertFalse(writer.discard(uri))
        assertArrayEquals(content, destination.readBytes())
    }

    @Test
    fun directFileUriCannotBeWrittenOrDeleted() = runBlocking {
        val sourceBytes = "%PDF-source".toByteArray(Charsets.US_ASCII)
        val destinationBytes = "archivo-privado".toByteArray(Charsets.UTF_8)
        val source = File(root, "safe-source.pdf").apply { writeBytes(sourceBytes) }
        val destination = File(root, "must-remain.pdf").apply { writeBytes(destinationBytes) }
        val artifact = ReportArtifact(
            source,
            ReportFormat.PDF,
            "MiGuardia_2026-08_informe_parcial.pdf",
            source.length(),
        )
        val directUri = Uri.fromFile(destination)

        val failure = runCatching { writer.save(artifact, directUri) }.exceptionOrNull()

        assertTrue(failure is ReportDestinationException)
        assertFalse(writer.discard(directUri))
        assertArrayEquals(destinationBytes, destination.readBytes())
        assertArrayEquals(sourceBytes, source.readBytes())
    }
}
