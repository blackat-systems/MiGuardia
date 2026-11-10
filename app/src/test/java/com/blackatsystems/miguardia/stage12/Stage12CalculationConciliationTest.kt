package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.report.MonthlyReportBuildOptions
import com.blackatsystems.miguardia.core.domain.report.MonthlyWorkReportProjection
import com.blackatsystems.miguardia.core.domain.report.ReportMonthState
import com.blackatsystems.miguardia.core.domain.report.ReportPrivacySelection
import com.blackatsystems.miguardia.core.domain.report.ReportReferenceState
import com.blackatsystems.miguardia.core.domain.report.ReportWorkKind
import com.blackatsystems.miguardia.core.domain.report.buildMonthlyWorkReport
import com.blackatsystems.miguardia.core.domain.summary.calculateMonthlySummary
import com.blackatsystems.miguardia.core.domain.work.HoursProgress
import com.blackatsystems.miguardia.core.domain.work.WorkProtectionPeriod
import com.blackatsystems.miguardia.core.domain.work.WorkedShiftSource
import com.blackatsystems.miguardia.core.domain.work.calculateHoursProgress
import com.blackatsystems.miguardia.core.domain.work.resolveHoursReferenceSegment
import com.blackatsystems.miguardia.reports.XlsxReportWriter
import com.blackatsystems.miguardia.stage12.Stage12CalculationFixture.Canary
import com.blackatsystems.miguardia.stage12.Stage12CalculationFixture.Expected
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 12.2: una sola fixture concilia Tus horas y tu meta, Resumen, la proyección del informe y el
 * XLSX real. Los números esperados se escriben a mano en [Stage12CalculationFixture.Expected].
 */
class Stage12CalculationConciliationTest {
    private val fixture = Stage12CalculationFixture

    @Test
    fun hoursSummaryReportAndXlsxShowTheSameExactMinutes() {
        val hours = hoursProgress()
        assertEquals(Expected.REGULAR, hours.regularWorkedMinutes)
        assertEquals(Expected.SHIFT_EXTRA_HELPING, hours.extrasByClass.sumOf { it.shiftMinutes })
        assertEquals(Expected.INDEPENDENT_NOT_HELPING, hours.extrasByClass.sumOf { it.independentMinutes })
        assertEquals(Expected.TOTAL, hours.totalWorkedMinutes)
        assertEquals(Expected.HELPS_REFERENCE, hours.helpsMeetReferenceMinutes)
        assertEquals(Expected.DOES_NOT_HELP, hours.doesNotHelpReferenceMinutes)
        assertEquals(Expected.TARGET, hours.targetMinutes)
        assertEquals(Expected.MISSING, hours.missingMinutes)
        assertEquals(0L, hours.excessMinutes)
        val byClass = hours.extrasByClass.associateBy { it.key.name }
        assertEquals(Expected.SHIFT_EXTRA_HELPING, byClass.getValue(fixture.HELPING_CLASS_NAME).totalMinutes)
        assertTrue(byClass.getValue(fixture.HELPING_CLASS_NAME).key.helpsMeetHoursReference)
        assertEquals(Expected.INDEPENDENT_NOT_HELPING, byClass.getValue(fixture.NOT_HELPING_CLASS_NAME).totalMinutes)
        assertFalse(byClass.getValue(fixture.NOT_HELPING_CLASS_NAME).key.helpsMeetHoursReference)

        val summary = calculateMonthlySummary(fixture.summaryInput, fixture.CLOCK, fixture.ZONE)
        assertEquals(Expected.REGULAR, summary.essentials.regularWorked?.value)
        assertEquals(Expected.EXTRAS, summary.essentials.extras?.value)
        assertEquals(Expected.TOTAL, summary.essentials.totalWorked?.value)
        assertEquals(Expected.PENDING, summary.essentials.pendingScheduled?.value ?: 0L)
        val compliance = summary.compliance.single()
        assertEquals(hours, compliance.progress)
        assertEquals(Expected.HELPS_REFERENCE, compliance.contributingWork.value)
        assertEquals(Expected.TARGET, compliance.target?.value)
        assertEquals(Expected.MISSING, compliance.missing?.value)
        val availability = requireNotNull(summary.availability)
        assertEquals(Expected.AVAILABILITY_PROGRAMMED, availability.programmed.value)
        assertEquals(Expected.AVAILABILITY_PROGRAMMED, availability.effectiveElapsed.value)

        val report = report(ReportPrivacySelection())
        assertEquals(summary, report.summary)
        assertEquals(ReportMonthState.ClosedMonth, report.monthState)
        assertEquals(Expected.WORK_ROWS, report.workRows.size)
        assertEquals(Expected.TOTAL, report.workRows.sumOf { it.accountedMinutes })
        assertEquals(Expected.REGULAR, report.workRows.sumOf { it.regularMinutes })
        assertEquals(Expected.EXTRAS, report.workRows.sumOf { row -> row.extraBreakdown.sumOf { it.minutes } })
        assertEquals(
            Expected.INDEPENDENT_NOT_HELPING,
            report.workRows.single { it.kind == ReportWorkKind.INDEPENDENT_EXTRA }.accountedMinutes,
        )
        val night = report.workRows.single { it.ownerLocalDate == fixture.NIGHT_SHIFT_DATE }
        assertEquals(480L, night.accountedMinutes)
        val reference = report.references.single()
        assertEquals(ReportReferenceState.DEFINED, reference.state)
        assertEquals(fixture.MONTH.atDay(1), reference.startInclusive)
        assertEquals(fixture.MONTH.plusMonths(1).atDay(1), reference.endExclusive)
        assertEquals(Expected.TARGET, reference.targetMinutes)
        assertEquals(Expected.HELPS_REFERENCE, reference.contributingMinutes)
        assertEquals(Expected.MISSING, reference.missingMinutes)
        assertEquals(0L, reference.excessMinutes)
        assertEquals(Expected.AVAILABILITY_PROGRAMMED, report.availabilityRows.single().programmedMinutes)

        val xlsx = Stage12XlsxWorkbook.read(write(report))
        val metrics = xlsx.sheet("Resumen").metricValues()
        assertEquals(Expected.TOTAL, metrics.getValue(requireNotNull(summary.essentials.totalWorked).label))
        assertEquals(Expected.REGULAR, metrics.getValue(requireNotNull(summary.essentials.regularWorked).label))
        assertEquals(Expected.EXTRAS, metrics.getValue(requireNotNull(summary.essentials.extras).label))
        assertEquals(Expected.AVAILABILITY_PROGRAMMED, metrics.getValue(availability.programmed.label))
        val goal = xlsx.sheet("Resumen").rowAfterHeader("Meta (min)")
        assertEquals(Expected.TARGET, goal.getValue("D").toLong())
        assertEquals(Expected.HELPS_REFERENCE, goal.getValue("E").toLong())
        assertEquals(Expected.MISSING, goal.getValue("F").toLong())
        assertEquals(0L, goal.getValue("G").toLong())
        val workSheet = xlsx.sheet("Jornadas")
        assertEquals(Expected.WORK_ROWS, workSheet.dataRows().size)
        assertEquals(Expected.TOTAL, workSheet.columnSum("O"))
        assertEquals(Expected.REGULAR, workSheet.columnSum("Q"))
        assertEquals(Expected.EXTRAS, workSheet.columnSum("R"))
        assertEquals(Expected.AVAILABILITY_PROGRAMMED, xlsx.sheet("Disponibilidad").columnSum("I"))
    }

    @Test
    fun xlsxKeepsTheWhitelistWithPrivateOptionsOffAndOn() {
        val closed = Stage12XlsxWorkbook.read(write(report(ReportPrivacySelection(), displayName = NAME))).allText()
        listOf(
            Canary.ADDRESS,
            Canary.DIFFERENCE_REASON,
            Canary.POSITION,
            Canary.SHIFT_NOTE,
            Canary.MEDICAL_NOTE,
            NAME,
        ).forEach { forbidden -> assertFalse("No debe aparecer: $forbidden", closed.contains(forbidden)) }
        assertNoTechnicalIds(closed)

        val opened = Stage12XlsxWorkbook.read(
            write(
                report(
                    ReportPrivacySelection(
                        includeDisplayName = true,
                        includePosition = true,
                        includeShiftNotes = true,
                        includeMedicalNotes = true,
                    ),
                    displayName = NAME,
                ),
            ),
        ).allText()
        listOf(NAME, Canary.POSITION, Canary.SHIFT_NOTE, Canary.MEDICAL_NOTE).forEach { allowed ->
            assertTrue("Debe aparecer al elegirlo: $allowed", opened.contains(allowed))
        }
        listOf(Canary.ADDRESS, Canary.DIFFERENCE_REASON).forEach { forbidden ->
            assertFalse("Nunca debe aparecer: $forbidden", opened.contains(forbidden))
        }
        assertNoTechnicalIds(opened)
    }

    @Test
    fun theSameMonthStillOpenOnItsLastDayGivesTheSameNumbersAsPartialReport() {
        val lastDay = java.time.Clock.fixed(java.time.Instant.parse("2026-09-30T15:00:00Z"), fixture.ZONE)
        val summary = calculateMonthlySummary(fixture.summaryInput, lastDay, fixture.ZONE)
        assertEquals(Expected.TOTAL, summary.essentials.totalWorked?.value)
        assertEquals(Expected.REGULAR, summary.essentials.regularWorked?.value)
        assertEquals(Expected.EXTRAS, summary.essentials.extras?.value)
        val compliance = summary.compliance.single()
        assertEquals(Expected.HELPS_REFERENCE, compliance.contributingWork.value)
        assertEquals(Expected.MISSING, compliance.missing?.value)
        assertEquals(Expected.AVAILABILITY_PROGRAMMED, summary.availability?.effectiveElapsed?.value)
        val report = buildMonthlyWorkReport(
            snapshot = fixture.reportSnapshot(ReportPrivacySelection()),
            options = MonthlyReportBuildOptions(ReportPrivacySelection(), null),
            clock = lastDay,
            zoneId = fixture.ZONE,
        )
        assertEquals(ReportMonthState.PartialAsOf(java.time.LocalDate.of(2026, 9, 30)), report.monthState)
        assertEquals(summary, report.summary)
    }

    @Test
    fun sharedDeviceOracleAcceptsTheRealXlsxAndRejectsALeak() {
        val all = ReportPrivacySelection(
            includeDisplayName = true,
            includePosition = true,
            includeShiftNotes = true,
            includeMedicalNotes = true,
        )
        Stage12CalculationOracle.checkXlsx(write(report(ReportPrivacySelection(), NAME)), false, NAME)
        Stage12CalculationOracle.checkXlsx(write(report(all, NAME)), true, NAME)
        val leak = runCatching { Stage12CalculationOracle.checkXlsx(write(report(all, NAME)), false, NAME) }
        assertTrue("El oráculo debe rechazar opciones privadas no elegidas", leak.isFailure)
    }

    @Test
    fun privateOptionsDoNotChangeAnyNumber() {
        val closed = report(ReportPrivacySelection())
        val opened = report(
            ReportPrivacySelection(
                includeDisplayName = true,
                includePosition = true,
                includeShiftNotes = true,
                includeMedicalNotes = true,
            ),
            displayName = NAME,
        )
        assertEquals(closed.summary, opened.summary)
        assertEquals(closed.references, opened.references)
        assertEquals(closed.availabilityRows, opened.availabilityRows)
        assertEquals(
            closed.workRows.map { listOf(it.accountedMinutes, it.regularMinutes, it.pendingMinutes) },
            opened.workRows.map { listOf(it.accountedMinutes, it.regularMinutes, it.pendingMinutes) },
        )
    }

    private fun hoursProgress(): HoursProgress {
        val segment = requireNotNull(resolveHoursReferenceSegment(fixture.configuration, fixture.MONTH.atDay(1)))
        val actuals = mapOf(fixture.DAY_SHIFT_ID to fixture.dayActual)
        return calculateHoursProgress(
            segment = segment,
            shifts = fixture.shifts.map { WorkedShiftSource(it.shift, actuals[it.shift.id]) },
            independentExtras = listOf(fixture.independentExtra),
            clock = fixture.CLOCK,
            zoneId = fixture.ZONE,
            protectionPeriods = listOf(WorkProtectionPeriod(fixture.MEDICAL_DATE, fixture.MEDICAL_DATE)),
        )
    }

    private fun report(privacy: ReportPrivacySelection, displayName: String? = null): MonthlyWorkReportProjection =
        buildMonthlyWorkReport(
            snapshot = fixture.reportSnapshot(privacy),
            options = MonthlyReportBuildOptions(privacy = privacy, displayName = displayName),
            clock = fixture.CLOCK,
            zoneId = fixture.ZONE,
        )

    private fun write(projection: MonthlyWorkReportProjection): ByteArray =
        ByteArrayOutputStream().also { XlsxReportWriter().write(projection, it) }.toByteArray()

    private fun assertNoTechnicalIds(text: String) {
        assertFalse("No debe haber UUID de la fixture", text.contains("12200000-0000-0000-0000-"))
        assertFalse("No debe haber UUID", UUID_PATTERN.containsMatchIn(text))
    }

    private companion object {
        const val NAME: String = "Apodo canario"
        val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
