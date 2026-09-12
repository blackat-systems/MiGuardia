package com.blackatsystems.miguardia.reports

import com.blackatsystems.miguardia.core.domain.report.MonthlyWorkReportProjection
import com.blackatsystems.miguardia.core.domain.report.ReportMonthState
import com.blackatsystems.miguardia.core.domain.report.ReportNoteKind
import com.blackatsystems.miguardia.core.domain.report.ReportNoteRow
import com.blackatsystems.miguardia.core.domain.report.ReportPhotoRow
import com.blackatsystems.miguardia.core.domain.report.ReportPrivateInclusions
import com.blackatsystems.miguardia.core.domain.report.ReportSituationKind
import com.blackatsystems.miguardia.core.domain.report.ReportSituationRow
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryEssentials
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryProjection
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

internal fun reportProjectionFixture(
    note: String? = null,
    photoCaption: String? = null,
): MonthlyWorkReportProjection {
    val month = YearMonth.of(2026, 8)
    val notes = note?.let {
        listOf(ReportNoteRow(0, LocalDate.of(2026, 8, 29), ReportNoteKind.SHIFT, "Jornada histórica", it))
    }.orEmpty()
    val photos = photoCaption?.let { listOf(ReportPhotoRow(0, it)) }.orEmpty()
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
            ReportSituationRow(
                stableOrder = 0,
                date = LocalDate.of(2026, 8, 29),
                kind = ReportSituationKind.UNDEFINED,
                label = "? · situación española",
            ),
        ),
        notes = notes,
        photos = photos,
        privateInclusions = ReportPrivateInclusions(
            displayName = null,
            positionsIncluded = false,
            shiftNotesIncluded = notes.isNotEmpty(),
            medicalNotesIncluded = false,
            photosIncluded = photos.isNotEmpty(),
        ),
    )
}
