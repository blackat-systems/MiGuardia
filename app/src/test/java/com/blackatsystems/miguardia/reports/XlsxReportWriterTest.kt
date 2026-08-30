package com.blackatsystems.miguardia.reports

import com.blackatsystems.miguardia.core.domain.report.MonthlyWorkReportProjection
import com.blackatsystems.miguardia.core.domain.report.ReportMonthState
import com.blackatsystems.miguardia.core.domain.report.ReportNoteKind
import com.blackatsystems.miguardia.core.domain.report.ReportNoteRow
import com.blackatsystems.miguardia.core.domain.report.ReportPrivateInclusions
import com.blackatsystems.miguardia.core.domain.report.ReportSituationKind
import com.blackatsystems.miguardia.core.domain.report.ReportSituationRow
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryEssentials
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryProjection
import com.blackatsystems.miguardia.core.domain.summary.SummaryContribution
import com.blackatsystems.miguardia.core.domain.summary.SummaryContributionKind
import com.blackatsystems.miguardia.core.domain.summary.SummaryMetric
import com.blackatsystems.miguardia.core.domain.summary.SummaryValueUnit
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxReportWriterTest {
    @Test
    fun writesDeterministicWellFormedOoxmlWithFiveStableSheets() {
        val projection = projection("Nota española: ñ, á, ¿bien?")
        val first = write(projection)
        val second = write(projection)

        assertArrayEquals(first, second)
        assertArrayEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04), first.copyOfRange(0, 4))
        val entries = unzip(first)
        assertTrue(
            entries.keys.containsAll(
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
        entries.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.forEach { (_, bytes) ->
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(ByteArrayInputStream(bytes))
        }
        val workbook = entries.getValue("xl/workbook.xml").toString(Charsets.UTF_8)
        assertTrue(workbook.contains("name=\"Resumen\""))
        assertTrue(workbook.contains("name=\"Jornadas\""))
        assertTrue(workbook.contains("name=\"Disponibilidad\""))
        assertTrue(workbook.contains("name=\"Situaciones\""))
        assertTrue(workbook.contains("name=\"Notas\""))
    }

    @Test
    fun treatsFormulaPrefixesAsLiteralInlineTextAndNormalizesInvalidXmlControls() {
        val dangerous = "=HYPERLINK(\"https://invalid.example\")\u0001 +texto -texto @texto"
        val bytes = write(projection(dangerous))
        val entries = unzip(bytes)
        val notes = entries.getValue("xl/worksheets/sheet5.xml").toString(Charsets.UTF_8)

        assertTrue(notes.contains("t=\"inlineStr\""))
        assertTrue(notes.contains("=HYPERLINK"))
        assertFalse(notes.contains("<f>"))
        assertFalse(notes.contains('\u0001'))
        assertTrue(notes.contains('\uFFFD'))
    }

    @Test
    fun splitsLongNotesWithoutTruncatingOrBreakingSurrogatePairs() {
        val source = "😀" + "x".repeat(70_000) + "fin"
        val chunks = splitExcelText(source)

        assertEquals(source, chunks.joinToString(""))
        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.length <= XlsxReportWriter.MAX_EXCEL_CELL_CHARACTERS })
        assertTrue(chunks.none { it.lastOrNull()?.let(Character::isHighSurrogate) == true })

        val xml = unzip(write(projection(source)))
            .getValue("xl/worksheets/sheet5.xml")
            .toString(Charsets.UTF_8)
        assertTrue(xml.contains(">1</v>"))
        assertTrue(xml.contains(">2</v>"))
        assertTrue(xml.contains(">3</v>"))
        assertTrue(xml.contains("fin"))
    }

    @Test
    fun encodesDatesAndCanonicalCountsAsNumericCellsWithoutFormulas() {
        val entries = unzip(write(projection("texto")))
        val situations = entries.getValue("xl/worksheets/sheet4.xml").toString(Charsets.UTF_8)
        val summary = entries.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)

        assertTrue(situations.contains("s=\"2\" t=\"n\""))
        assertTrue(summary.contains("s=\"7\" t=\"n\""))
        assertFalse(
            entries.filterKeys { it.startsWith("xl/worksheets/") }
                .values
                .any { it.toString(Charsets.UTF_8).contains("<f>") || it.toString(Charsets.UTF_8).contains("<f ") },
        )
    }

    @Test
    fun writesAnOptionalFixtureForIndependentReaderVerification() {
        val destination = System.getenv("MIGUARDIA_XLSX_FIXTURE")?.takeIf(String::isNotBlank)
            ?: return
        File(destination).apply {
            parentFile?.mkdirs()
            writeBytes(write(projection("Nota de apertura independiente: ñ, á y ¿?")))
        }
    }

    @Test
    fun neverSerializesTechnicalIdsPathsAddressesReasonsOrInternalExplanations() {
        val forbidden = listOf(
            "Dirección privada 123",
            "97000000-0000-0000-0000-000000000099",
            "C:\\private\\schedule_photos\\secret.jpg",
            "differenceReason privado",
            "explicación interna privada",
        )
        val internalContribution = SummaryContribution(
            id = "internal-id",
            sourceId = forbidden[1],
            ownerLocalDate = LocalDate.of(2026, 8, 29),
            start = null,
            end = null,
            value = 0L,
            unit = SummaryValueUnit.MINUTES,
            kind = SummaryContributionKind.REGULAR_WORK,
            sourceLabel = forbidden[2],
            workPlaceLabel = forbidden[0],
            explanation = "${forbidden[3]} · ${forbidden[4]}",
        )
        val safeMetric = SummaryMetric(
            id = "safe-total",
            label = "Total trabajado",
            value = 0L,
            unit = SummaryValueUnit.MINUTES,
            contributions = listOf(internalContribution),
        )
        val base = projection("Nota aprobada")
        val guarded = base.copy(
            summary = base.summary.copy(
                essentials = MonthlySummaryEssentials(safeMetric, null, null, null),
                hasContent = true,
            ),
        )
        val xmlText = unzip(write(guarded)).values.joinToString("\n") { it.toString(Charsets.UTF_8) }

        forbidden.forEach { token -> assertFalse("No debe exportarse $token", xmlText.contains(token)) }
        assertTrue(xmlText.contains("Total trabajado"))
        assertTrue(xmlText.contains("Nota aprobada"))
    }

    private fun write(projection: MonthlyWorkReportProjection): ByteArray =
        ByteArrayOutputStream().also { XlsxReportWriter().write(projection, it) }.toByteArray()

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes), Charsets.UTF_8).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes())
                zip.closeEntry()
            }
        }
    }

    private fun projection(note: String): MonthlyWorkReportProjection {
        val month = YearMonth.of(2026, 8)
        return MonthlyWorkReportProjection(
            month = month,
            generatedAt = Instant.parse("2026-08-29T12:34:00Z"),
            zoneId = ZoneOffset.UTC,
            monthState = ReportMonthState.PartialAsOf(LocalDate.of(2026, 8, 29)),
            sectors = emptyList(),
            summary = MonthlySummaryProjection(
                month = month,
                essentials = MonthlySummaryEssentials(null, null, null, null),
                compliance = emptyList(),
                availability = null,
                optionalSections = emptyList(),
                hasContent = false,
            ),
            references = emptyList(),
            workRows = emptyList(),
            availabilityRows = emptyList(),
            situations = listOf(
                ReportSituationRow(0, LocalDate.of(2026, 8, 29), ReportSituationKind.UNDEFINED, "=literal"),
            ),
            notes = listOf(
                ReportNoteRow(0, LocalDate.of(2026, 8, 29), ReportNoteKind.SHIFT, "Contexto", note),
            ),
            photos = emptyList(),
            privateInclusions = ReportPrivateInclusions(
                displayName = null,
                positionsIncluded = false,
                shiftNotesIncluded = true,
                medicalNotesIncluded = false,
                photosIncluded = false,
            ),
        )
    }
}
