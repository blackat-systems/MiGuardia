package com.blackatsystems.miguardia.reports

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.blackatsystems.miguardia.core.domain.report.MonthlyWorkReportProjection
import com.blackatsystems.miguardia.core.domain.report.ReportAvailabilityRow
import com.blackatsystems.miguardia.core.domain.report.ReportReferenceState
import com.blackatsystems.miguardia.core.domain.report.ReportSituationKind
import com.blackatsystems.miguardia.core.domain.report.ReportWorkKind
import com.blackatsystems.miguardia.core.domain.report.ReportWorkRow
import com.blackatsystems.miguardia.core.domain.report.ReportWorkState
import com.blackatsystems.miguardia.core.domain.model.AvailabilityTemporalState
import com.blackatsystems.miguardia.core.domain.summary.SummaryMetric
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalFamily
import com.blackatsystems.miguardia.core.domain.summary.SummaryValueUnit
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoBitmapDecoder
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

class PdfReportWriter internal constructor(
    private val photoDecoder: SchedulePhotoBitmapDecoder = SchedulePhotoBitmapDecoder(),
) {
    fun write(
        projection: MonthlyWorkReportProjection,
        assets: FrozenReportAssets,
        output: OutputStream,
    ) {
        require(assets.photos.size == projection.photos.size)
        assets.photos.forEachIndexed { index, photo ->
            require(photo.stableOrder == index && photo.caption == projection.photos[index].caption)
        }
        val document = PdfDocument()
        try {
            val layout = ReportPdfLayout(document, projection)
            drawCover(layout, projection)
            drawSummary(layout, projection)
            drawReferences(layout, projection)
            drawWorkRows(layout, projection)
            drawAvailability(layout, projection)
            drawSituations(layout, projection)
            drawNotes(layout, projection)
            drawPhotos(layout, assets)
            layout.finish()
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun drawCover(layout: ReportPdfLayout, projection: MonthlyWorkReportProjection) {
        layout.title("Informe local de MiGuardia")
        layout.keyValue("Mes", projection.month.toString())
        layout.keyValue("Estado", projection.statusText)
        layout.keyValue("Generado", projection.generatedAt.localDateTime(projection.zoneId))
        layout.keyValue(
            "Rubros presentes",
            projection.sectors.joinToString { it.displayName }.ifBlank { "Sin rubro para el mes" },
        )
        projection.privateInclusions.displayName?.let { layout.keyValue("Nombre o apodo", it) }
        if (!projection.hasActivity) {
            layout.notice("Sin actividad registrada")
        }
        layout.paragraph(
            "Este archivo es una fotografía local del mes. La disponibilidad se informa por separado y las " +
                "clasificaciones nocturnas, feriados y fines de semana no agregan horas al total.",
        )
    }

    private fun drawSummary(layout: ReportPdfLayout, projection: MonthlyWorkReportProjection) {
        layout.section("Resumen mensual")
        projection.summary.essentials.totalWorked?.let { layout.metric(it) }
        projection.summary.essentials.regularWorked?.let { layout.metric(it) }
        projection.summary.essentials.extras?.let { layout.metric(it) }
        projection.summary.essentials.pendingScheduled?.let { layout.metric(it) }
        projection.summary.availability?.let { availability ->
            layout.metric(availability.programmed)
            layout.metric(availability.effectiveElapsed)
            availability.replacedElapsed?.let { layout.metric(it) }
            availability.pending?.let { layout.metric(it) }
            layout.metric(availability.projectedEffectiveAtEnd)
        }
        projection.summary.optionalSections.forEach { section ->
            layout.subsection(section.family.reportLabel())
            section.metrics.forEach(layout::metric)
        }
    }

    private fun drawReferences(layout: ReportPdfLayout, projection: MonthlyWorkReportProjection) {
        layout.section("Metas de horas")
        if (projection.references.isEmpty()) {
            layout.paragraph("No hay una meta aplicable para este mes.")
            return
        }
        layout.beginTable("Período · estado · meta · avance")
        projection.references.forEach { reference ->
            layout.tableRow(
                listOf(
                    "${reference.startInclusive.displayDate()} al ${reference.endExclusive.minusDays(1).displayDate()}",
                    reference.state.displayLabel(),
                    "Meta: ${reference.targetMinutes?.let(::readableMinutes) ?: "no corresponde"}",
                    "Avance: ${reference.contributingMinutes?.let(::readableMinutes) ?: "no calculable"}",
                    "Faltante: ${reference.missingMinutes?.let(::readableMinutes) ?: "no calculable"}",
                    "Superación: ${reference.excessMinutes?.let(::readableMinutes) ?: "no calculable"}",
                ),
            )
        }
        layout.endTable()
    }

    private fun drawWorkRows(layout: ReportPdfLayout, projection: MonthlyWorkReportProjection) {
        layout.section("Detalle cronológico de jornadas")
        if (projection.workRows.isEmpty()) {
            layout.paragraph("Sin jornadas ni extras independientes registrados.")
            return
        }
        layout.beginTable("Fecha · estado · lugar y tipo · horarios · minutos contabilizados")
        projection.workRows.forEach { row -> layout.tableRow(row.pdfLines()) }
        layout.endTable()
    }

    private fun drawAvailability(layout: ReportPdfLayout, projection: MonthlyWorkReportProjection) {
        layout.section("Disponibilidad")
        layout.paragraph("La disponibilidad no se suma al trabajo ni al avance de la meta de horas.")
        if (projection.availabilityRows.isEmpty()) {
            layout.paragraph("Sin disponibilidad registrada.")
            return
        }
        layout.beginTable("Fecha · intervalo · estado · minutos de disponibilidad")
        projection.availabilityRows.forEach { row -> layout.tableRow(row.pdfLines()) }
        layout.endTable()
    }

    private fun drawSituations(layout: ReportPdfLayout, projection: MonthlyWorkReportProjection) {
        layout.section("Situaciones")
        if (projection.situations.isEmpty()) {
            layout.paragraph("Sin situaciones registradas.")
            return
        }
        layout.beginTable("Fecha · situación")
        projection.situations.forEach { situation ->
            layout.tableRow(
                listOf(
                    situation.date.displayDate(),
                    "${situation.kind.displayLabel()} · ${situation.label}",
                ),
            )
        }
        layout.endTable()
    }

    private fun drawNotes(layout: ReportPdfLayout, projection: MonthlyWorkReportProjection) {
        if (projection.notes.isEmpty()) return
        layout.section("Notas incluidas conscientemente")
        projection.notes.forEach { note ->
            layout.subsection("${note.date.displayDate()} · ${note.context}")
            layout.paragraph(note.body)
        }
    }

    private fun drawPhotos(layout: ReportPdfLayout, assets: FrozenReportAssets) {
        if (assets.photos.isEmpty()) return
        assets.photos.forEach { photo ->
            layout.photoPage(photo.caption) { destination ->
                val decoded = photoDecoder.decode(photo.file, MAX_PHOTO_DIMENSION)
                    ?: throw ReportAssetException("Una foto congelada no se pudo decodificar para el PDF.")
                try {
                    drawScaledBitmap(layout.canvas, decoded.bitmap, destination)
                } finally {
                    decoded.bitmap.recycle()
                }
            }
        }
    }

    private fun drawScaledBitmap(canvas: Canvas, bitmap: Bitmap, destination: RectF) {
        val scale = min(destination.width() / bitmap.width, destination.height() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = destination.left + (destination.width() - width) / 2f
        val top = destination.top + (destination.height() - height) / 2f
        val target = RectF(left, top, left + width, top + height)
        canvas.drawBitmap(bitmap, null, target, BitmapPaint)
    }

    private companion object {
        const val MAX_PHOTO_DIMENSION: Int = 1600
        val BitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    }
}

private class ReportPdfLayout(
    private val document: PdfDocument,
    private val projection: MonthlyWorkReportProjection,
) {
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var y = ContentTop
    private var repeatingTableHeader: String? = null
    private var hasBodyContent = false

    val canvas: Canvas
        get() = requireNotNull(page).canvas

    init {
        newPage()
    }

    fun finish() {
        finishCurrentPage()
    }

    fun title(value: String) {
        ensureSpace(40f)
        canvas.drawText(value, Left, y + TitlePaint.textSize, TitlePaint)
        y += 36f
        hasBodyContent = true
    }

    fun section(value: String) {
        endTable()
        ensureSpace(34f)
        y += 8f
        canvas.drawText(value, Left, y + SectionPaint.textSize, SectionPaint)
        y += 27f
        canvas.drawLine(Left, y, Right, y, DividerPaint)
        y += 10f
        hasBodyContent = true
    }

    fun subsection(value: String) {
        val lines = wrap(value, SubsectionPaint, ContentWidth)
        val totalHeight = lines.size * SubsectionLineHeight + SubsectionBottomSpacing
        if (totalHeight <= MaximumBodyHeight) ensureSpace(totalHeight)
        lines.forEach { line ->
            ensureSpace(SubsectionLineHeight)
            canvas.drawText(line, Left, y + SubsectionPaint.textSize, SubsectionPaint)
            y += SubsectionLineHeight
            hasBodyContent = true
        }
        y += SubsectionBottomSpacing
    }

    fun keyValue(label: String, value: String) {
        drawWrapped("$label: $value", BodyPaint, extraBottom = 3f)
    }

    fun notice(value: String) {
        val lines = wrap(value, NoticePaint, ContentWidth - 20f)
        val height = lines.size * NoticeLineHeight + 16f
        ensureSpace(height)
        canvas.drawRoundRect(RectF(Left, y, Right, y + height), 6f, 6f, NoticeBackgroundPaint)
        var baseline = y + 10f + NoticePaint.textSize
        lines.forEach { line ->
            canvas.drawText(line, Left + 10f, baseline, NoticePaint)
            baseline += NoticeLineHeight
        }
        y += height + 8f
        hasBodyContent = true
    }

    fun paragraph(value: String) {
        val paragraphs = value.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        paragraphs.forEachIndexed { index, paragraph ->
            if (paragraph.isEmpty()) {
                ensureSpace(BodyLineHeight)
                y += BodyLineHeight
            } else {
                wrap(paragraph, BodyPaint, ContentWidth).forEach { line ->
                    ensureSpace(BodyLineHeight)
                    canvas.drawText(line, Left, y + BodyPaint.textSize, BodyPaint)
                    y += BodyLineHeight
                    hasBodyContent = true
                }
            }
            if (index != paragraphs.lastIndex) y += 2f
        }
        y += 6f
    }

    fun metric(metric: SummaryMetric) {
        val value = if (metric.unit == SummaryValueUnit.MINUTES) {
            readableMinutes(metric.value)
        } else {
            metric.value.toString()
        }
        drawWrapped("${metric.label}: $value", BodyPaint, extraBottom = 2f)
    }

    fun beginTable(header: String) {
        repeatingTableHeader = header
        drawTableHeader(header)
    }

    fun endTable() {
        repeatingTableHeader = null
    }

    fun tableRow(values: List<String>) {
        val lines = values.flatMap { value -> wrap(value, TablePaint, ContentWidth - 16f) }
        val completeHeight = rowHeight(lines.size)
        if (completeHeight <= FooterTop - y) {
            drawRowFragment(lines)
            return
        }
        if (completeHeight <= freshTableBodyCapacity()) {
            startFreshTablePage()
            drawRowFragment(lines)
            return
        }
        var offset = 0
        while (offset < lines.size) {
            var count = linesThatFit(FooterTop - y)
            if (count <= 0) {
                startFreshTablePage()
                count = linesThatFit(FooterTop - y)
                require(count > 0) { "El encabezado de la tabla no deja espacio para una fila" }
            }
            val end = minOf(lines.size, offset + count)
            drawRowFragment(lines.subList(offset, end))
            offset = end
            if (offset < lines.size) startFreshTablePage()
        }
    }

    fun photoPage(caption: String, draw: (RectF) -> Unit) {
        endTable()
        if (hasBodyContent) newPage()
        subsection(caption)
        if (FooterTop - (y + 8f) < MinimumPhotoHeight) {
            newPage()
            subsection("Foto (continuación)")
        }
        val destination = RectF(Left, y + 8f, Right, FooterTop - 12f)
        require(destination.height() > 0f)
        draw(destination)
        y = FooterTop
        hasBodyContent = true
    }

    private fun drawWrapped(value: String, paint: Paint, extraBottom: Float) {
        wrap(value, paint, ContentWidth).forEach { line ->
            ensureSpace(BodyLineHeight)
            canvas.drawText(line, Left, y + paint.textSize, paint)
            y += BodyLineHeight
            hasBodyContent = true
        }
        y += extraBottom
    }

    private fun drawTableHeader(header: String) {
        val lines = wrap(header, TableHeaderPaint, ContentWidth - 16f)
        val height = lines.size * TableHeaderLineHeight + 14f
        ensureSpace(height, repeatHeader = false)
        canvas.drawRect(RectF(Left, y, Right, y + height), HeaderBackgroundPaint)
        var baseline = y + 7f + TableHeaderPaint.textSize
        lines.forEach { line ->
            canvas.drawText(line, Left + 8f, baseline, TableHeaderPaint)
            baseline += TableHeaderLineHeight
        }
        y += height
        hasBodyContent = true
    }

    private fun startFreshTablePage() {
        newPage()
        repeatingTableHeader?.let(::drawTableHeader)
    }

    private fun freshTableBodyCapacity(): Float {
        val headerHeight = repeatingTableHeader?.let(::tableHeaderHeight) ?: 0f
        return FooterTop - ContentTop - headerHeight
    }

    private fun tableHeaderHeight(header: String): Float =
        wrap(header, TableHeaderPaint, ContentWidth - 16f).size * TableHeaderLineHeight + 14f

    private fun rowHeight(lineCount: Int): Float = maxOf(34f, lineCount * TableLineHeight + 16f)

    private fun linesThatFit(availableHeight: Float): Int {
        if (availableHeight < 34f) return 0
        return ((availableHeight - 16f) / TableLineHeight).toInt().coerceAtLeast(1)
    }

    private fun drawRowFragment(lines: List<String>) {
        val height = rowHeight(lines.size)
        require(y + height <= FooterTop + 0.001f) { "Un fragmento de fila excede el área imprimible" }
        canvas.drawRect(RectF(Left, y, Right, y + height), RowBackgroundPaint)
        var baseline = y + 8f + TablePaint.textSize
        lines.forEach { line ->
            canvas.drawText(line, Left + 8f, baseline, TablePaint)
            baseline += TableLineHeight
        }
        canvas.drawLine(Left, y + height, Right, y + height, DividerPaint)
        y += height
        hasBodyContent = true
    }

    private fun ensureSpace(required: Float, repeatHeader: Boolean = true) {
        if (y + required <= FooterTop) return
        newPage()
        if (repeatHeader) repeatingTableHeader?.let(::drawTableHeader)
    }

    private fun newPage() {
        finishCurrentPage()
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(PageWidth, PageHeight, pageNumber).create()
        page = document.startPage(info)
        canvas.drawColor(Color.WHITE)
        drawPageHeader()
        y = ContentTop
        hasBodyContent = false
    }

    private fun finishCurrentPage() {
        val current = page ?: return
        current.canvas.drawLine(Left, FooterTop, Right, FooterTop, DividerPaint)
        current.canvas.drawText("Página $pageNumber", Right - 55f, FooterTop + 18f, FooterPaint)
        document.finishPage(current)
        page = null
    }

    private fun drawPageHeader() {
        canvas.drawText("MiGuardia · Informe ${projection.month}", Left, 29f, HeaderPaint)
        canvas.drawLine(Left, 40f, Right, 40f, DividerPaint)
    }

    private fun wrap(value: String, paint: Paint, width: Float): List<String> {
        if (value.isEmpty()) return listOf("")
        return value.replace("\r\n", "\n").replace('\r', '\n').split('\n').flatMap { paragraph ->
            if (paragraph.isEmpty()) return@flatMap listOf("")
            val result = mutableListOf<String>()
            var remaining = paragraph
            while (remaining.isNotEmpty()) {
                var count = paint.breakText(remaining, true, width, null).coerceAtLeast(1)
                if (count < remaining.length) {
                    val preferred = remaining.substring(0, count).lastIndexOf(' ')
                    if (preferred > 0) count = preferred
                }
                val line = remaining.substring(0, count).trimEnd()
                result += line
                remaining = remaining.substring(count).trimStart()
            }
            result
        }
    }

    private companion object {
        const val PageWidth = 595
        const val PageHeight = 842
        const val Left = 40f
        const val Right = 555f
        const val ContentTop = 54f
        const val FooterTop = 810f
        const val ContentWidth = Right - Left
        const val MaximumBodyHeight = FooterTop - ContentTop
        const val BodyLineHeight = 15f
        const val SubsectionLineHeight = 17f
        const val SubsectionBottomSpacing = 7f
        const val NoticeLineHeight = 16f
        const val TableLineHeight = 13f
        const val TableHeaderLineHeight = 14f
        const val MinimumPhotoHeight = 120f

        fun paint(size: Float, color: Int = Color.BLACK, bold: Boolean = false): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
            }

        val HeaderPaint = paint(10f, Color.DKGRAY, bold = true)
        val FooterPaint = paint(9f, Color.DKGRAY)
        val TitlePaint = paint(22f, bold = true)
        val SectionPaint = paint(16f, bold = true)
        val SubsectionPaint = paint(12f, bold = true)
        val BodyPaint = paint(10.5f)
        val NoticePaint = paint(11f, bold = true)
        val TablePaint = paint(9f)
        val TableHeaderPaint = paint(9.5f, bold = true)
        val DividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 180, 180)
            strokeWidth = 0.8f
        }
        val HeaderBackgroundPaint = Paint().apply { color = Color.rgb(225, 237, 247) }
        val RowBackgroundPaint = Paint().apply { color = Color.WHITE }
        val NoticeBackgroundPaint = Paint().apply { color = Color.rgb(244, 247, 250) }
    }
}

private fun ReportWorkRow.pdfLines(): List<String> {
    val intervals = if (kind == ReportWorkKind.INDEPENDENT_EXTRA) {
        listOf("Intervalo: ${actualStart.localDateTime(zoneId)} — ${actualEnd.localDateTime(zoneId)}")
    } else {
        listOf(
            "Plan: ${plannedStart.localDateTime(zoneId)} — ${plannedEnd.localDateTime(zoneId)}",
            if (actualStart != null && actualEnd != null) {
                "Real: ${actualStart.localDateTime(zoneId)} — ${actualEnd.localDateTime(zoneId)}"
            } else {
                "Real: no informado"
            },
        )
    }
    val extras = extraBreakdown.joinToString("; ") { "${it.className}: ${readableMinutes(it.minutes)}" }
        .ifBlank { "Sin extras" }
    return buildList {
        add("${ownerLocalDate.displayDate()} · ${state.reportLabel()} · ${if (kind == ReportWorkKind.SHIFT) "Jornada" else "Extra independiente"}")
        add("${sector.displayName} · $workPlace · $workType")
        addAll(intervals)
        add("Minutos contabilizados: $accountedMinutes (${readableMinutes(accountedMinutes)})")
        add("$REGULAR_WORK_LABEL: ${readableMinutes(regularMinutes)} · Extras: $extras")
        if (pendingMinutes > 0L) add("Trabajo pendiente programado: ${readableMinutes(pendingMinutes)}")
        add("Nocturnidad: ${readableMinutes(nightMinutes)} · Feriado: ${readableMinutes(holidayMinutes)} · Fin de semana: ${readableMinutes(weekendMinutes)}")
        position?.let { add("Puesto o función: $it") }
    }
}

private fun ReportAvailabilityRow.pdfLines(): List<String> = listOf(
    "${ownerLocalDate.displayDate()} · ${sector.displayName} · $label · ${state.reportLabel()}",
    "${start.localDateTime(zoneId)} — ${end.localDateTime(zoneId)}",
    "Programada: ${readableMinutes(programmedMinutes)} · Efectiva: ${readableMinutes(effectiveMinutes)}",
    "Reemplazada: ${readableMinutes(replacedMinutes)} · Pendiente: ${readableMinutes(pendingMinutes)} · Proyectada: ${readableMinutes(projectedMinutes)}",
)

private fun Instant?.localDateTime(zoneId: ZoneId): String = this
    ?.atZone(zoneId)
    ?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", SpanishArgentina))
    ?: "—"

private fun LocalDate.displayDate(): String = format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

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

private val SpanishArgentina: Locale = Locale.forLanguageTag("es-AR")
