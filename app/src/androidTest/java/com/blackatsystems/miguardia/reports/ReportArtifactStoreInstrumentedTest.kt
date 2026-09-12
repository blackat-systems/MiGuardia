package com.blackatsystems.miguardia.reports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.report.ReportFormat
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportArtifactStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.filesDir, ReportArtifactStore.ARTIFACT_DIRECTORY)
    private lateinit var store: ReportArtifactStore

    @Before
    fun setUp() {
        root.deleteRecursively()
        store = ReportArtifactStore(
            context,
            Clock.fixed(Instant.parse("2026-08-29T15:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun xlsxArtifactHasTheExactZipSignatureWellFormedOoxmlAndNoForbiddenParts() = runBlocking {
        val projection = reportProjectionFixture(note = "Nota elegida: ñ, ¿qué pasó?")
        val artifact = store.create(
            format = ReportFormat.XLSX,
            suggestedFileName = "MiGuardia_2026-08_informe_parcial.xlsx",
            protectedArtifact = null,
        ) { output -> XlsxReportWriter().write(projection, output) }

        assertEquals(ReportFormat.XLSX.mimeType, artifact.format.mimeType)
        assertEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04).toList(), artifact.file.readBytes().take(4))
        ZipFile(artifact.file).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue(
                names.containsAll(
                    setOf(
                        "[Content_Types].xml",
                        "_rels/.rels",
                        "xl/workbook.xml",
                        "xl/_rels/workbook.xml.rels",
                        "xl/styles.xml",
                        "xl/worksheets/sheet1.xml",
                        "xl/worksheets/sheet2.xml",
                        "xl/worksheets/sheet3.xml",
                        "xl/worksheets/sheet4.xml",
                        "xl/worksheets/sheet5.xml",
                    ),
                ),
            )
            assertFalse(names.any { it.contains("externalLink", true) || it.contains("media/") || it.endsWith("vbaProject.bin") })
            names.filter { it.endsWith(".xml") || it.endsWith(".rels") }.forEach { name ->
                zip.getInputStream(zip.getEntry(name)).use { input ->
                    DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                        .newDocumentBuilder()
                        .parse(input)
                }
            }
        }
    }

    @Test
    fun cleanupKeepsAtMostThreeRecentArtifactsAndProtectsTheCurrentSession() = runBlocking {
        val projection = reportProjectionFixture()
        suspend fun create(protected: File?) = store.create(
            ReportFormat.XLSX,
            "MiGuardia_2026-08_informe_parcial.xlsx",
            protected,
        ) { output -> XlsxReportWriter().write(projection, output) }

        val current = create(null)
        repeat(3) { create(current.file) }

        val remaining = root.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".tmp") }
        assertEquals(ReportArtifactStore.MAX_ARTIFACTS, remaining.size)
        assertTrue(current.file.exists())
        assertFalse(root.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }

    @Test
    fun failedValidationRemovesTheTemporaryAndPreservesThePreviousValidArtifact() = runBlocking {
        val valid = store.create(
            ReportFormat.XLSX,
            "MiGuardia_2026-08_informe_parcial.xlsx",
            null,
        ) { output -> XlsxReportWriter().write(reportProjectionFixture(), output) }

        val failure = runCatching {
            store.create(
                ReportFormat.PDF,
                "MiGuardia_2026-08_informe_parcial.pdf",
                valid.file,
            ) { output -> output.write("archivo incompleto".toByteArray()) }
        }.exceptionOrNull()

        assertTrue(failure is ReportArtifactException)
        assertTrue(valid.file.exists())
        assertFalse(root.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        assertEquals(1, root.listFiles().orEmpty().count { it.extension == ReportFormat.XLSX.extension })
        assertEquals(0, root.listFiles().orEmpty().count { it.extension == ReportFormat.PDF.extension })
    }
}
