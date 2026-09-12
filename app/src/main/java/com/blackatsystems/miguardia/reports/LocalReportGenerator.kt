package com.blackatsystems.miguardia.reports

import com.blackatsystems.miguardia.core.domain.report.MonthlyReportBuildOptions
import com.blackatsystems.miguardia.core.domain.report.MonthlyReportSnapshotRepository
import com.blackatsystems.miguardia.core.domain.report.MonthlyReportSnapshotRequest
import com.blackatsystems.miguardia.core.domain.report.MonthlyWorkReportProjection
import com.blackatsystems.miguardia.core.domain.report.ReportFormat
import com.blackatsystems.miguardia.core.domain.report.ReportPrivacySelection
import com.blackatsystems.miguardia.core.domain.report.buildMonthlyWorkReport
import com.blackatsystems.miguardia.core.domain.report.suggestedReportFileName
import com.blackatsystems.miguardia.profile.GuardProfileStore
import java.io.File
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GeneratedLocalReport(
    val projection: MonthlyWorkReportProjection,
    val artifact: ReportArtifact,
)

interface ReportGenerator {
    suspend fun preview(
        month: YearMonth,
        format: ReportFormat,
        privacy: ReportPrivacySelection,
    ): MonthlyWorkReportProjection

    suspend fun generate(
        month: YearMonth,
        format: ReportFormat,
        privacy: ReportPrivacySelection,
        protectedArtifact: File?,
    ): GeneratedLocalReport
}

class LocalReportGenerator(
    private val snapshots: MonthlyReportSnapshotRepository,
    private val profiles: GuardProfileStore,
    private val photoStager: ReportPhotoStager,
    private val artifactStore: ReportArtifactStore,
    private val pdfWriter: PdfReportWriter = PdfReportWriter(),
    private val xlsxWriter: XlsxReportWriter = XlsxReportWriter(),
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ReportGenerator {
    override suspend fun preview(
        month: YearMonth,
        format: ReportFormat,
        privacy: ReportPrivacySelection,
    ): MonthlyWorkReportProjection = withContext(Dispatchers.IO) {
        val effectivePrivacy = privacy.forFormat(format)
        val snapshot = snapshots.capture(effectivePrivacy.request(month))
        val frozenName = profiles.current().displayName
        buildMonthlyWorkReport(
            snapshot = snapshot,
            options = MonthlyReportBuildOptions(effectivePrivacy, frozenName),
            clock = clock,
            zoneId = zoneId,
        )
    }

    override suspend fun generate(
        month: YearMonth,
        format: ReportFormat,
        privacy: ReportPrivacySelection,
        protectedArtifact: File?,
    ): GeneratedLocalReport = withContext(Dispatchers.IO) {
        val effectivePrivacy = privacy.forFormat(format)
        val snapshot = snapshots.capture(effectivePrivacy.request(month))
        val frozenName = profiles.current().displayName
        val projection = buildMonthlyWorkReport(
            snapshot = snapshot,
            options = MonthlyReportBuildOptions(effectivePrivacy, frozenName),
            clock = clock,
            zoneId = zoneId,
        )
        val assets = if (format == ReportFormat.PDF) {
            photoStager.freeze(snapshot.selectedPhotos, projection.photos)
        } else {
            FrozenReportAssets.EMPTY
        }
        try {
            val suggestedName = suggestedReportFileName(projection, format)
            val artifact = artifactStore.create(
                format = format,
                suggestedFileName = suggestedName,
                protectedArtifact = protectedArtifact,
            ) { output ->
                when (format) {
                    ReportFormat.PDF -> pdfWriter.write(projection, assets, output)
                    ReportFormat.XLSX -> xlsxWriter.write(projection, output)
                }
            }
            GeneratedLocalReport(projection, artifact)
        } finally {
            photoStager.release(assets)
        }
    }

    private fun ReportPrivacySelection.forFormat(format: ReportFormat): ReportPrivacySelection =
        if (format == ReportFormat.PDF) this else copy(selectedPhotoIds = emptySet())

    private fun ReportPrivacySelection.request(month: YearMonth): MonthlyReportSnapshotRequest =
        MonthlyReportSnapshotRequest(
            month = month,
            includeShiftNotes = includeShiftNotes,
            includeMedicalNotes = includeMedicalNotes,
            selectedPhotoIds = selectedPhotoIds,
        )
}
