package com.blackatsystems.miguardia.reports

import com.blackatsystems.miguardia.core.domain.report.MonthlyWorkReportProjection
import com.blackatsystems.miguardia.core.domain.report.ReportAvailabilityRow
import com.blackatsystems.miguardia.core.domain.report.ReportNoteKind
import com.blackatsystems.miguardia.core.domain.report.ReportReferenceState
import com.blackatsystems.miguardia.core.domain.report.ReportSituationKind
import com.blackatsystems.miguardia.core.domain.report.ReportWorkKind
import com.blackatsystems.miguardia.core.domain.report.ReportWorkRow
import com.blackatsystems.miguardia.core.domain.report.ReportWorkState
import com.blackatsystems.miguardia.core.domain.model.AvailabilityTemporalState
import com.blackatsystems.miguardia.core.domain.summary.SummaryMetric
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalFamily
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalSection
import com.blackatsystems.miguardia.core.domain.summary.SummaryValueUnit
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxReportWriter {
    fun write(projection: MonthlyWorkReportProjection, output: OutputStream) {
        val sheets = buildSheets(projection)
        val zip = ZipOutputStream(output, StandardCharsets.UTF_8)
        try {
            zip.setLevel(9)
            zip.writeEntry("[Content_Types].xml", contentTypes(sheets.size))
            zip.writeEntry("_rels/.rels", RootRelationships)
            zip.writeEntry("xl/workbook.xml", workbook(sheets))
            zip.writeEntry("xl/_rels/workbook.xml.rels", workbookRelationships(sheets.size))
            zip.writeEntry("xl/styles.xml", Styles)
            sheets.forEachIndexed { index, sheet ->
                zip.writeEntry("xl/worksheets/sheet${index + 1}.xml", worksheet(sheet))
            }
            zip.finish()
            zip.flush()
        } catch (error: Exception) {
            runCatching { zip.finish() }
            throw error
        }
    }

    private fun buildSheets(projection: MonthlyWorkReportProjection): List<Sheet> = buildList {
        add(Sheet("Resumen", summaryRows(projection), listOf(26, 34, 22, 20, 26, 20, 20)))
        add(
            Sheet(
                "Jornadas",
                workRows(projection),
                listOf(13, 16, 18, 20, 28, 24, 16, 12, 16, 12, 16, 12, 16, 12, 20, 16, 18, 16, 24, 18, 18, 20, 24),
            ),
        )
        add(
            Sheet(
                "Disponibilidad",
                availabilityRows(projection.availabilityRows),
                listOf(13, 18, 24, 16, 12, 16, 12, 18, 18, 18, 20, 18, 18),
            ),
        )
        add(Sheet("Situaciones", situationRows(projection), listOf(13, 24, 32)))
        if (projection.notes.isNotEmpty()) {
            add(Sheet("Notas", noteRows(projection), listOf(13, 20, 28, 14, 60)))
        }
    }

    private fun summaryRows(projection: MonthlyWorkReportProjection): List<Row> = buildList {
        add(row(text("Informe local de MiGuardia", Style.TITLE)))
        add(row(text("Mes", Style.HEADER), text(projection.month.toString())))
        add(row(text("Estado", Style.HEADER), text(projection.statusText)))
        add(
            row(
                text("Generado", Style.HEADER),
                dateTime(projection.generatedAt, projection.zoneId),
            ),
        )
        add(
            row(
                text("Rubros presentes", Style.HEADER),
                text(projection.sectors.joinToString { it.displayName }.ifBlank { "Sin rubro para el mes" }),
            ),
        )
        projection.privateInclusions.displayName?.let { add(row(text("Nombre o apodo", Style.HEADER), text(it))) }
        if (!projection.hasActivity) add(row(text("Sin actividad registrada", Style.WARNING)))
        add(emptyRow())
        add(row(text("Resumen mensual", Style.SECTION)))
        add(metricHeader())
        projection.summary.essentials.totalWorked?.let { add(metricRow("Trabajo", it)) }
        projection.summary.essentials.regularWorked?.let { add(metricRow("Trabajo", it)) }
        projection.summary.essentials.extras?.let { add(metricRow("Trabajo", it)) }
        projection.summary.essentials.pendingScheduled?.let { add(metricRow("Trabajo", it)) }
        projection.summary.availability?.let { availability ->
            add(metricRow("Disponibilidad", availability.programmed))
            add(metricRow("Disponibilidad", availability.effectiveElapsed))
            availability.replacedElapsed?.let { add(metricRow("Disponibilidad", it)) }
            availability.pending?.let { add(metricRow("Disponibilidad", it)) }
            add(metricRow("Disponibilidad", availability.projectedEffectiveAtEnd))
        }
        projection.summary.optionalSections.forEach { section ->
            addOptionalSection(section)
        }
        add(emptyRow())
        add(row(text("Metas de horas", Style.SECTION)))
        add(
            row(
                text("Desde", Style.HEADER),
                text("Hasta", Style.HEADER),
                text("Estado", Style.HEADER),
                text("Meta (min)", Style.HEADER),
                text("Horas que cuentan (min)", Style.HEADER),
                text("Faltante (min)", Style.HEADER),
                text("Superación (min)", Style.HEADER),
            ),
        )
        projection.references.forEach { reference ->
            add(
                row(
                    date(reference.startInclusive),
                    date(reference.endExclusive.minusDays(1)),
                    text(reference.state.displayLabel()),
                    reference.targetMinutes.numberOrBlank(),
                    reference.contributingMinutes.numberOrBlank(),
                    reference.missingMinutes.numberOrBlank(),
                    reference.excessMinutes.numberOrBlank(),
                ),
            )
        }
    }

    private fun MutableList<Row>.addOptionalSection(section: SummaryOptionalSection) {
        section.metrics.forEach { metric -> add(metricRow(section.family.reportLabel(), metric)) }
    }

    private fun metricHeader(): Row = row(
        text("Sección", Style.HEADER),
        text("Concepto", Style.HEADER),
        text("Minutos / cantidad", Style.HEADER),
        text("Lectura", Style.HEADER),
    )

    private fun metricRow(section: String, metric: SummaryMetric): Row = row(
        text(section),
        text(metric.label),
        number(metric.value),
        text(if (metric.unit == SummaryValueUnit.MINUTES) readableMinutes(metric.value) else metric.value.toString()),
    )

    private fun workRows(projection: MonthlyWorkReportProjection): List<Row> = buildList {
        add(
            row(
                text("Fecha", Style.HEADER),
                text("Estado", Style.HEADER),
                text("Clase de fila", Style.HEADER),
                text("Rubro", Style.HEADER),
                text("Lugar", Style.HEADER),
                text("Tipo", Style.HEADER),
                text("Inicio planificado - fecha", Style.HEADER),
                text("Inicio planificado - hora", Style.HEADER),
                text("Fin planificado - fecha", Style.HEADER),
                text("Fin planificado - hora", Style.HEADER),
                text("Inicio real - fecha", Style.HEADER),
                text("Inicio real - hora", Style.HEADER),
                text("Fin real - fecha", Style.HEADER),
                text("Fin real - hora", Style.HEADER),
                text("Minutos contabilizados", Style.HEADER),
                text("Horas legibles", Style.HEADER),
                text("$REGULAR_WORK_LABEL (min)", Style.HEADER),
                text("Minutos extra", Style.HEADER),
                text("Clase extra", Style.HEADER),
                text("Minutos nocturnos", Style.HEADER),
                text("Minutos en feriado", Style.HEADER),
                text("Minutos de fin de semana", Style.HEADER),
                text("Puesto o función", Style.HEADER),
            ),
        )
        projection.workRows.forEach { work ->
            val plannedStart = work.plannedStart?.atZone(work.zoneId)
            val plannedEnd = work.plannedEnd?.atZone(work.zoneId)
            val actualStart = work.actualStart?.atZone(work.zoneId)
            val actualEnd = work.actualEnd?.atZone(work.zoneId)
            add(
                row(
                    date(work.ownerLocalDate),
                    text(work.state.reportLabel()),
                    text(if (work.kind == ReportWorkKind.SHIFT) "Jornada" else "Extra independiente"),
                    text(work.sector.displayName),
                    text(work.workPlace),
                    text(work.workType),
                    plannedStart?.toLocalDate().dateOrBlank(),
                    plannedStart?.toLocalTime().timeOrBlank(),
                    plannedEnd?.toLocalDate().dateOrBlank(),
                    plannedEnd?.toLocalTime().timeOrBlank(),
                    actualStart?.toLocalDate().dateOrBlank(),
                    actualStart?.toLocalTime().timeOrBlank(),
                    actualEnd?.toLocalDate().dateOrBlank(),
                    actualEnd?.toLocalTime().timeOrBlank(),
                    number(work.accountedMinutes),
                    text(readableMinutes(work.accountedMinutes)),
                    number(work.regularMinutes),
                    number(work.extraBreakdown.sumOf { it.minutes }),
                    text(work.extraBreakdown.joinToString("; ") { "${it.className}: ${it.minutes} min" }),
                    number(work.nightMinutes),
                    number(work.holidayMinutes),
                    number(work.weekendMinutes),
                    text(work.position.orEmpty()),
                ),
            )
        }
    }

    private fun availabilityRows(rows: List<ReportAvailabilityRow>): List<Row> = buildList {
        add(
            row(
                text("Fecha", Style.HEADER),
                text("Rubro", Style.HEADER),
                text("Disponibilidad", Style.HEADER),
                text("Inicio - fecha", Style.HEADER),
                text("Inicio - hora", Style.HEADER),
                text("Fin - fecha", Style.HEADER),
                text("Fin - hora", Style.HEADER),
                text("Estado", Style.HEADER),
                text("Programada (min)", Style.HEADER),
                text("Efectiva (min)", Style.HEADER),
                text("Reemplazada (min)", Style.HEADER),
                text("Pendiente (min)", Style.HEADER),
                text("Proyectada (min)", Style.HEADER),
            ),
        )
        rows.forEach { value ->
            val start = value.start.atZone(value.zoneId)
            val end = value.end.atZone(value.zoneId)
            add(
                row(
                    date(value.ownerLocalDate),
                    text(value.sector.displayName),
                    text(value.label),
                    date(start.toLocalDate()),
                    time(start.toLocalTime()),
                    date(end.toLocalDate()),
                    time(end.toLocalTime()),
                    text(value.state.reportLabel()),
                    number(value.programmedMinutes),
                    number(value.effectiveMinutes),
                    number(value.replacedMinutes),
                    number(value.pendingMinutes),
                    number(value.projectedMinutes),
                ),
            )
        }
    }

    private fun situationRows(projection: MonthlyWorkReportProjection): List<Row> = buildList {
        add(row(text("Fecha", Style.HEADER), text("Situación", Style.HEADER), text("Marca segura", Style.HEADER)))
        projection.situations.forEach { situation ->
            add(
                row(
                    date(situation.date),
                    text(situation.kind.displayLabel()),
                    text(situation.label),
                ),
            )
        }
    }

    private fun noteRows(projection: MonthlyWorkReportProjection): List<Row> = buildList {
        add(
            row(
                text("Fecha", Style.HEADER),
                text("Tipo", Style.HEADER),
                text("Contexto", Style.HEADER),
                text("Continuación", Style.HEADER),
                text("Nota", Style.HEADER),
            ),
        )
        projection.notes.forEach { note ->
            splitExcelText(note.body).forEachIndexed { index, chunk ->
                add(
                    row(
                        date(note.date),
                        text(if (note.kind == ReportNoteKind.SHIFT) "Jornada" else "Carpeta médica privada"),
                        text(note.context),
                        number((index + 1).toLong()),
                        text(chunk),
                    ),
                )
            }
        }
    }

    private fun worksheet(sheet: Sheet): String = buildString {
        append(XmlHeader)
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews>")
        append("<sheetFormatPr defaultRowHeight=\"15\"/>")
        append("<cols>")
        sheet.columnWidths.forEachIndexed { index, width ->
            val oneBased = index + 1
            append("<col min=\"").append(oneBased)
                .append("\" max=\"").append(oneBased)
                .append("\" width=\"").append(width)
                .append("\" customWidth=\"1\"/>")
        }
        append("</cols>")
        append("<sheetData>")
        sheet.rows.forEachIndexed { rowIndex, row ->
            val oneBasedRow = rowIndex + 1
            val height = when {
                row.cells.any { it.styleOrNull() == Style.HEADER } -> 34
                row.cells.any { it.styleOrNull() == Style.TITLE } -> 24
                else -> null
            }
            append("<row r=\"").append(oneBasedRow).append('"')
            height?.let { append(" ht=\"").append(it).append("\" customHeight=\"1\"") }
            append('>')
            row.cells.forEachIndexed { columnIndex, cell ->
                append(cellXml(cell, cellReference(columnIndex, oneBasedRow)))
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun cellXml(cell: Cell, reference: String): String = when (cell) {
        is Cell.Text -> {
            val value = sanitizeXmlText(cell.value)
            val preserve = if (value.startsWith(' ') || value.endsWith(' ') || value.contains('\n')) {
                " xml:space=\"preserve\""
            } else {
                ""
            }
            "<c r=\"$reference\" s=\"${cell.style.index}\" t=\"inlineStr\"><is><t$preserve>" +
                escapeXml(value) + "</t></is></c>"
        }
        is Cell.Number -> "<c r=\"$reference\" s=\"${cell.style.index}\" t=\"n\"><v>${cell.value}</v></c>"
        is Cell.DecimalNumber ->
            "<c r=\"$reference\" s=\"${cell.style.index}\" t=\"n\"><v>${cell.value.toPlainString()}</v></c>"
        Cell.Blank -> "<c r=\"$reference\"/>"
    }

    private fun workbook(sheets: List<Sheet>): String = buildString {
        append(XmlHeader)
        append(
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">",
        )
        append("<bookViews><workbookView/></bookViews><sheets>")
        sheets.forEachIndexed { index, sheet ->
            append("<sheet name=\"")
                .append(escapeXmlAttribute(sheet.name))
                .append("\" sheetId=\"")
                .append(index + 1)
                .append("\" r:id=\"rId")
                .append(index + 1)
                .append("\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRelationships(sheetCount: Int): String = buildString {
        append(XmlHeader)
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        repeat(sheetCount) { index ->
            append(
                "<Relationship Id=\"rId${index + 1}\" " +
                    "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" " +
                    "Target=\"worksheets/sheet${index + 1}.xml\"/>",
            )
        }
        append(
            "<Relationship Id=\"rId${sheetCount + 1}\" " +
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" " +
                "Target=\"styles.xml\"/>",
        )
        append("</Relationships>")
    }

    private fun contentTypes(sheetCount: Int): String = buildString {
        append(XmlHeader)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        repeat(sheetCount) { index ->
            append("<Override PartName=\"/xl/worksheets/sheet${index + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun ZipOutputStream.writeEntry(name: String, contents: String) {
        val entry = ZipEntry(name).apply {
            time = NormalizedZipTimeMillis
            lastModifiedTime = NormalizedZipFileTime
            lastAccessTime = NormalizedZipFileTime
            creationTime = NormalizedZipFileTime
        }
        putNextEntry(entry)
        write(contents.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }

    private fun cellReference(columnIndex: Int, row: Int): String {
        var value = columnIndex + 1
        val column = StringBuilder()
        while (value > 0) {
            val remainder = (value - 1) % 26
            column.append(('A'.code + remainder).toChar())
            value = (value - 1) / 26
        }
        return column.reverse().append(row).toString()
    }

    private fun text(value: String, style: Style = Style.TEXT): Cell = Cell.Text(value, style)
    private fun number(value: Long, style: Style = Style.NUMBER): Cell = Cell.Number(value, style)
    private fun date(value: LocalDate): Cell = Cell.Number(
        ChronoUnit.DAYS.between(ExcelEpoch, value),
        Style.DATE,
    )
    private fun time(value: LocalTime): Cell = Cell.DecimalNumber(
        BigDecimal.valueOf(value.toSecondOfDay().toLong())
            .divide(SecondsPerDay, 12, RoundingMode.HALF_UP)
            .stripTrailingZeros(),
        Style.TIME,
    )
    private fun dateTime(value: Instant, zoneId: ZoneId): Cell {
        val local = LocalDateTime.ofInstant(value, zoneId)
        val day = BigDecimal.valueOf(ChronoUnit.DAYS.between(ExcelEpoch, local.toLocalDate()))
        val fraction = BigDecimal.valueOf(local.toLocalTime().toSecondOfDay().toLong())
            .divide(SecondsPerDay, 12, RoundingMode.HALF_UP)
        return Cell.DecimalNumber(day.add(fraction), Style.DATE_TIME)
    }
    private fun row(vararg cells: Cell): Row = Row(cells.toList())
    private fun emptyRow(): Row = Row(emptyList())
    private fun Long?.numberOrBlank(): Cell = this?.let(::number) ?: Cell.Blank
    private fun LocalDate?.dateOrBlank(): Cell = this?.let(::date) ?: Cell.Blank
    private fun LocalTime?.timeOrBlank(): Cell = this?.let(::time) ?: Cell.Blank

    private data class Sheet(
        val name: String,
        val rows: List<Row>,
        val columnWidths: List<Int>,
    ) {
        init {
            require(columnWidths.isNotEmpty())
            require(rows.all { it.cells.size <= columnWidths.size })
        }
    }
    private data class Row(val cells: List<Cell>)

    private sealed interface Cell {
        data class Text(val value: String, val style: Style) : Cell
        data class Number(val value: Long, val style: Style) : Cell
        data class DecimalNumber(val value: BigDecimal, val style: Style) : Cell
        data object Blank : Cell
    }

    private fun Cell.styleOrNull(): Style? = when (this) {
        is Cell.Text -> style
        is Cell.Number -> style
        is Cell.DecimalNumber -> style
        Cell.Blank -> null
    }

    private enum class Style(val index: Int) {
        TEXT(0),
        HEADER(1),
        DATE(2),
        TIME(3),
        NUMBER(4),
        TITLE(5),
        SECTION(6),
        DATE_TIME(7),
        WARNING(8),
    }

    companion object {
        internal const val MAX_EXCEL_CELL_CHARACTERS: Int = 32_767
        private val ExcelEpoch: LocalDate = LocalDate.of(1899, 12, 30)
        private val SecondsPerDay: BigDecimal = BigDecimal.valueOf(86_400L)
        private const val NormalizedZipTimeMillis: Long = 315_532_800_000L
        private val NormalizedZipFileTime: FileTime = FileTime.fromMillis(NormalizedZipTimeMillis)
        private const val XmlHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        private const val RootRelationships =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" " +
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" " +
                "Target=\"xl/workbook.xml\"/></Relationships>"
        private const val Styles =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<numFmts count=\"4\">" +
                "<numFmt numFmtId=\"164\" formatCode=\"dd/mm/yyyy\"/>" +
                "<numFmt numFmtId=\"165\" formatCode=\"hh:mm\"/>" +
                "<numFmt numFmtId=\"166\" formatCode=\"dd/mm/yyyy hh:mm\"/>" +
                "<numFmt numFmtId=\"167\" formatCode=\"0\"/>" +
                "</numFmts>" +
                "<fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                "<font><b/><sz val=\"16\"/><name val=\"Calibri\"/></font></fonts>" +
                "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>" +
                "<fill><patternFill patternType=\"gray125\"/></fill>" +
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD9EAF7\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>" +
                "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"9\">" +
                "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf>" +
                "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
                "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
                "<xf numFmtId=\"167\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
                "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
                "<xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
                "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
                "</styleSheet>"
    }
}

internal fun splitExcelText(value: String): List<String> {
    val sanitized = sanitizeXmlText(value)
    if (sanitized.isEmpty()) return listOf("")
    val result = mutableListOf<String>()
    var start = 0
    while (start < sanitized.length) {
        var end = minOf(start + XlsxReportWriter.MAX_EXCEL_CELL_CHARACTERS, sanitized.length)
        if (end < sanitized.length && Character.isHighSurrogate(sanitized[end - 1])) end--
        result += sanitized.substring(start, end)
        start = end
    }
    return result
}

internal fun sanitizeXmlText(value: String): String = buildString(value.length) {
    value.codePoints().forEach { codePoint ->
        if (
            codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD ||
            codePoint in 0x20..0xD7FF || codePoint in 0xE000..0xFFFD ||
            codePoint in 0x10000..0x10FFFF
        ) {
            appendCodePoint(codePoint)
        } else {
            append('\uFFFD')
        }
    }
}

private fun escapeXml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun escapeXmlAttribute(value: String): String = escapeXml(value)
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun readableMinutes(minutes: Long): String {
    val sign = if (minutes < 0L) "−" else ""
    val absolute = kotlin.math.abs(minutes)
    return "$sign${absolute / 60} h ${absolute % 60} min"
}

private fun ReportWorkState.reportLabel(): String = when (this) {
    ReportWorkState.SCHEDULED -> "Programada"
    ReportWorkState.IN_PROGRESS -> "En curso"
    ReportWorkState.COMPLETED -> "Completada"
    ReportWorkState.ABSENT -> "Ausencia"
    ReportWorkState.CANCELLED -> "Cancelada"
}

private fun AvailabilityTemporalState.reportLabel(): String = when (this) {
    AvailabilityTemporalState.FUTURE -> "Futura"
    AvailabilityTemporalState.IN_PROGRESS -> "En curso"
    AvailabilityTemporalState.COMPLETED -> "Completada"
    AvailabilityTemporalState.PROTECTED -> "Protegida"
}

private fun SummaryOptionalFamily.reportLabel(): String = when (this) {
    SummaryOptionalFamily.NIGHTS -> "Noches"
    SummaryOptionalFamily.HOLIDAYS -> "Feriados"
    SummaryOptionalFamily.WEEKENDS -> "Fines de semana"
    SummaryOptionalFamily.PLANNED_VS_ACTUAL -> "Planificado frente a real"
    SummaryOptionalFamily.WORK_PLACES -> "Lugares de trabajo"
    SummaryOptionalFamily.WORK_TYPES -> "Tipos de trabajo"
    SummaryOptionalFamily.EXTRA_CLASSES -> "Clases extra"
    SummaryOptionalFamily.SITUATIONS -> "Situaciones"
}

private fun ReportReferenceState.displayLabel(): String = when (this) {
    ReportReferenceState.PENDING_SETUP -> "Pendiente de configurar"
    ReportReferenceState.NOT_USED -> "No utilizada"
    ReportReferenceState.UNKNOWN -> "Desconocida"
    ReportReferenceState.MISSING_VALUE_FOR_PERIOD -> "Valor faltante para el período"
    ReportReferenceState.DEFINED -> "Definida"
}

private fun ReportSituationKind.displayLabel(): String = when (this) {
    ReportSituationKind.DAY_OFF -> "Franco"
    ReportSituationKind.UNDEFINED -> "Estado sin definir"
    ReportSituationKind.VACATION -> "Vacaciones"
    ReportSituationKind.MEDICAL_LEAVE -> "Carpeta médica"
    ReportSituationKind.ABSENCE -> "Ausencia"
    ReportSituationKind.CANCELLATION -> "Cancelación"
}
