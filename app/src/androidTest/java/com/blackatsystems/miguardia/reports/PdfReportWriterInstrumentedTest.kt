package com.blackatsystems.miguardia.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.report.ReportNoteKind
import com.blackatsystems.miguardia.core.domain.report.ReportNoteRow
import com.blackatsystems.miguardia.core.domain.report.ReportPhotoRow
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryEssentials
import com.blackatsystems.miguardia.core.domain.summary.SummaryContribution
import com.blackatsystems.miguardia.core.domain.summary.SummaryContributionKind
import com.blackatsystems.miguardia.core.domain.summary.SummaryMetric
import com.blackatsystems.miguardia.core.domain.summary.SummaryValueUnit
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfReportWriterInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.cacheDir, "report-pdf-${UUID.randomUUID()}").apply { mkdirs() }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun everyGeneratedPageOpensAndRendersWithoutAccidentalBlankPages() {
        val longSpanishNote = buildString {
            repeat(420) {
                append("Línea extensa número $it: guardia, situación, ñandú y explicación elegida conscientemente. ")
            }
        }
        val file = writePdf(reportProjectionFixture(note = longSpanishNote), FrozenReportAssets.EMPTY)

        assertArrayEquals("%PDF-".toByteArray(Charsets.US_ASCII), file.readBytes().copyOfRange(0, 5))
        renderEveryPage(file, minimumPages = 2)
    }

    @Test
    fun anOptedInPhotoIsDecodedOnceFromTheFrozenCopyAndItsBytesRemainUntouched() {
        val photo = createPhoto()
        val before = photo.sha256()
        val assets = FrozenReportAssets(
            photos = listOf(
                FrozenReportPhoto(
                    stableOrder = 0,
                    file = photo,
                    mimeType = "image/png",
                    caption = "Foto mensual elegida",
                ),
            ),
            stagingDirectory = root,
        )
        val file = writePdf(reportProjectionFixture(photoCaption = "Foto mensual elegida"), assets)

        renderEveryPage(file, minimumPages = 2)
        assertArrayEquals(before, photo.sha256())
    }

    @Test
    fun oversizedRowsSubsectionsAndPhotoCaptionsArePaginatedWithoutLosingRenderability() {
        val longText = buildString {
            repeat(140) { index -> append("fragmento extenso $index con información elegida y caracteres españoles; ") }
        }
        val photo = createPhoto()
        val base = reportProjectionFixture()
        val projection = base.copy(
            situations = listOf(base.situations.single().copy(label = longText)),
            notes = listOf(
                ReportNoteRow(
                    stableOrder = 0,
                    date = LocalDate.of(2026, 8, 29),
                    kind = ReportNoteKind.SHIFT,
                    context = longText,
                    body = "Nota incluida conscientemente.",
                ),
            ),
            photos = listOf(ReportPhotoRow(0, longText)),
            privateInclusions = base.privateInclusions.copy(
                shiftNotesIncluded = true,
                photosIncluded = true,
            ),
        )
        val assets = FrozenReportAssets(
            photos = listOf(FrozenReportPhoto(0, photo, "image/png", longText)),
            stagingDirectory = root,
        )

        val file = writePdf(projection, assets)

        renderEveryPage(file, minimumPages = 5)
    }

    @Test
    fun internalContributionMetadataIsNeverWrittenIntoPdfBytes() {
        val forbidden = "C:\\private\\schedule_photos\\97000000-secret.jpg · differenceReason privado"
        val base = reportProjectionFixture()
        val metric = SummaryMetric(
            id = "safe-total",
            label = "Total trabajado",
            value = 0L,
            unit = SummaryValueUnit.MINUTES,
            contributions = listOf(
                SummaryContribution(
                    id = "internal-id",
                    sourceId = "97000000-0000-0000-0000-000000000099",
                    ownerLocalDate = java.time.LocalDate.of(2026, 8, 29),
                    start = null,
                    end = null,
                    value = 0L,
                    unit = SummaryValueUnit.MINUTES,
                    kind = SummaryContributionKind.REGULAR_WORK,
                    sourceLabel = forbidden,
                    workPlaceLabel = "Dirección privada 123",
                    explanation = forbidden,
                ),
            ),
        )
        val projection = base.copy(
            summary = base.summary.copy(
                essentials = MonthlySummaryEssentials(metric, null, null, null),
                hasContent = true,
            ),
        )

        val file = writePdf(projection, FrozenReportAssets.EMPTY)
        val raw = file.readBytes().toString(Charsets.ISO_8859_1)

        assertFalse(raw.contains(forbidden))
        assertFalse(raw.contains("Dirección privada 123"))
        assertFalse(raw.contains("97000000-0000-0000-0000-000000000099"))
    }

    private fun writePdf(
        projection: com.blackatsystems.miguardia.core.domain.report.MonthlyWorkReportProjection,
        assets: FrozenReportAssets,
    ): File = File(root, "${UUID.randomUUID()}.pdf").also { file ->
        FileOutputStream(file).use { output -> PdfReportWriter().write(projection, assets, output) }
        assertTrue(file.length() > 5L)
    }

    private fun renderEveryPage(file: File, minimumPages: Int) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertTrue(renderer.pageCount >= minimumPages)
                repeat(renderer.pageCount) { index ->
                    renderer.openPage(index).use { page ->
                        assertTrue(page.width > 0 && page.height > 0)
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            assertTrue("La página ${index + 1} no debe quedar vacía", bitmap.hasBodyInk())
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }

    private fun createPhoto(): File {
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(20, 80, 160))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.YELLOW
                textSize = 54f
            }
            canvas.drawText("Foto elegida", 80f, 190f, paint)
            return File(root, "frozen.png").also { file ->
                FileOutputStream(file).use { output ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun Bitmap.hasBodyInk(): Boolean {
        var y = 48
        val bodyBottom = (height - 34).coerceAtLeast(y)
        while (y < bodyBottom) {
            var x = 0
            while (x < width) {
                if (getPixel(x, y) != Color.WHITE) return true
                x += 2
            }
            y += 2
        }
        return false
    }

    private fun File.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(readBytes())
}
