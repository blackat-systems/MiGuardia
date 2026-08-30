package com.blackatsystems.miguardia.reports

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.core.domain.report.ReportFormat
import com.blackatsystems.miguardia.core.domain.report.ReportPrivacySelection
import com.blackatsystems.miguardia.core.domain.repository.SchedulePhotoRepository
import com.blackatsystems.miguardia.profile.GuardProfileStore
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportsViewModelInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.cacheDir, "reports-view-model-${UUID.randomUUID()}").apply { mkdirs() }
    private val profileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var profileStore: GuardProfileStore
    private lateinit var generator: FakeReportGenerator
    private lateinit var destination: FakeReportDestination

    @Before
    fun setUp() {
        profileStore = GuardProfileStore(File(root, "profile.preferences_pb"), profileScope)
        generator = FakeReportGenerator(root)
        destination = FakeReportDestination()
    }

    @After
    fun cleanUp() {
        profileScope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun sameSessionRestoresDraftButOpeningANewSessionResetsEveryPrivateOption() = runBlocking {
        val viewModel = createViewModel(
            mapOf(
                "reports.open" to true,
                "reports.month" to MONTH.toString(),
                "reports.format" to ReportFormat.XLSX.name,
                "reports.name" to true,
                "reports.position" to true,
                "reports.shiftNotes" to true,
                "reports.medicalNotes" to true,
                "reports.photos" to arrayListOf<String>(),
                "reports.stage" to ReportsStage.CONTENT.name,
            ),
        )
        val restored = viewModel.awaitStable()

        assertEquals(MONTH, restored.month)
        assertEquals(ReportFormat.XLSX, restored.format)
        assertTrue(restored.privacy.includeDisplayName)
        assertTrue(restored.privacy.includePosition)
        assertTrue(restored.privacy.includeShiftNotes)
        assertTrue(restored.privacy.includeMedicalNotes)

        onMain {
            viewModel.close()
            viewModel.open(MONTH)
        }
        val fresh = viewModel.awaitStable()
        assertEquals(ReportFormat.PDF, fresh.format)
        assertEquals(ReportPrivacySelection(), fresh.privacy)
    }

    @Test
    fun everyRestoredBusyStageReturnsToDraftAndNeverPretendsTheArtifactCompleted() = runBlocking {
        listOf(ReportsStage.GENERATING, ReportsStage.SAVING, ReportsStage.SHARING).forEach { interrupted ->
            val viewModel = createViewModel(
                mapOf(
                    "reports.open" to true,
                    "reports.month" to MONTH.toString(),
                    "reports.format" to ReportFormat.PDF.name,
                    "reports.stage" to interrupted.name,
                ),
            )

            val restored = viewModel.awaitStable()

            assertEquals(ReportsStage.CONTENT, restored.stage)
            assertNull(restored.generated)
            assertNull(restored.artifact)
            assertTrue(restored.infoMessage.orEmpty().contains("no se marcó como completada"))
        }
    }

    @Test
    fun doubleTapProducesOneArtifactAndSaveShareCancellationReuseIt() = runBlocking {
        val viewModel = createViewModel(
            mapOf(
                "reports.open" to true,
                "reports.month" to MONTH.toString(),
                "reports.stage" to ReportsStage.CONTENT.name,
            ),
        )
        viewModel.awaitStable()
        generator.gate = CompletableDeferred()

        onMain {
            viewModel.generate()
            viewModel.generate()
        }
        withTimeout(5_000) { viewModel.uiState.first { it.stage == ReportsStage.GENERATING } }
        withTimeout(5_000) {
            while (generator.generateCalls == 0) kotlinx.coroutines.yield()
        }
        assertEquals(1, generator.generateCalls)
        generator.gate?.complete(Unit)
        val ready = withTimeout(5_000) { viewModel.uiState.first { it.stage == ReportsStage.READY } }
        val artifact = requireNotNull(ready.artifact)

        onMain { assertTrue(viewModel.requestSave()) }
        onMain { assertFalse(viewModel.requestSave()) }
        onMain { viewModel.cancelSave() }
        assertEquals(artifact.file, viewModel.uiState.value.artifact?.file)
        assertEquals(ReportsStage.READY, viewModel.uiState.value.stage)

        onMain { assertTrue(viewModel.requestShare()) }
        onMain { assertFalse(viewModel.requestShare()) }
        onMain { viewModel.shareLaunched(false, "Sin aplicación compatible") }
        assertEquals(ReportsStage.ERROR, viewModel.uiState.value.stage)
        onMain { viewModel.retry() }
        assertEquals(ReportsStage.READY, viewModel.uiState.value.stage)
        assertEquals(artifact.file, viewModel.uiState.value.artifact?.file)
    }

    @Test
    fun restoredMissingPhotoRemainsVisibleUntilTheUserExplicitlyUnchecksIt() = runBlocking {
        val missingId = UUID.fromString("18000000-0000-0000-0000-000000000099")
        val viewModel = createViewModel(
            mapOf(
                "reports.open" to true,
                "reports.month" to MONTH.toString(),
                "reports.format" to ReportFormat.PDF.name,
                "reports.photos" to arrayListOf(missingId.toString()),
                "reports.photoExpanded" to true,
                "reports.stage" to ReportsStage.CONTENT.name,
            ),
        )
        val restored = viewModel.awaitStable()
        val choice = restored.availablePhotos.single()
        assertFalse(choice.available)
        assertTrue(choice.selected)
        assertTrue(missingId in restored.privacy.selectedPhotoIds)

        onMain { viewModel.setPhotoSelected(missingId, false) }
        val corrected = viewModel.awaitStable()
        assertTrue(corrected.privacy.selectedPhotoIds.isEmpty())
        assertTrue(corrected.availablePhotos.isEmpty())
    }

    @Test
    fun changingAwayFromPdfClearsPhotoConsentBeforeReturningToPdf() = runBlocking {
        val selectedId = UUID.fromString("18000000-0000-0000-0000-000000000099")
        val savedState = SavedStateHandle(
            mapOf<String, Any?>(
                "reports.open" to true,
                "reports.month" to MONTH.toString(),
                "reports.format" to ReportFormat.PDF.name,
                "reports.photos" to arrayListOf(selectedId.toString()),
                "reports.photoExpanded" to true,
                "reports.stage" to ReportsStage.CONTENT.name,
            ),
        )
        val viewModel = createViewModel(savedState)
        val restored = viewModel.awaitStable()
        assertTrue(restored.photoSelectionExpanded)
        assertTrue(selectedId in restored.privacy.selectedPhotoIds)

        onMain { viewModel.setFormat(ReportFormat.XLSX) }
        val xlsx = viewModel.awaitStable()
        assertFalse(xlsx.photoSelectionExpanded)
        assertTrue(xlsx.privacy.selectedPhotoIds.isEmpty())
        assertTrue(savedState.get<ArrayList<String>>("reports.photos").orEmpty().isEmpty())

        onMain { viewModel.setFormat(ReportFormat.PDF) }
        val pdf = viewModel.awaitStable()
        assertFalse(pdf.photoSelectionExpanded)
        assertTrue(pdf.privacy.selectedPhotoIds.isEmpty())
    }

    @Test
    fun lateSaveResultAfterRecreationDiscardsTheEmptyDestinationAndReportsInterruption() = runBlocking {
        val staleDestination = Uri.parse("content://com.blackatsystems.miguardia.qa.documents/late-empty")
        val viewModel = createViewModel(
            mapOf(
                "reports.open" to true,
                "reports.month" to MONTH.toString(),
                "reports.format" to ReportFormat.PDF.name,
                "reports.stage" to ReportsStage.SAVING.name,
            ),
        )

        onMain { viewModel.saveTo(staleDestination) }

        withTimeout(5_000) {
            while (staleDestination !in destination.discarded) kotlinx.coroutines.yield()
        }
        val restored = withTimeout(5_000) {
            viewModel.uiState.first {
                it.stage in setOf(ReportsStage.CONTENT, ReportsStage.EMPTY) &&
                    it.infoMessage.orEmpty().contains("interrumpió")
            }
        }
        assertEquals(listOf(staleDestination), destination.discarded)
        assertTrue(restored.infoMessage.orEmpty().contains("archivo vacío"))
    }

    private fun createViewModel(saved: Map<String, Any?>): ReportsViewModel =
        createViewModel(SavedStateHandle(saved))

    private fun createViewModel(savedState: SavedStateHandle): ReportsViewModel = ReportsViewModel(
        generator = generator,
        destinationWriter = destination,
        photoRepository = EmptyPhotoRepository,
        profileStore = profileStore,
        clock = Clock.fixed(Instant.parse("2026-08-29T12:00:00Z"), ZoneOffset.UTC),
        zoneId = ZoneOffset.UTC,
        savedState = savedState,
    )

    private suspend fun ReportsViewModel.awaitStable(): ReportsUiState = withTimeout(5_000) {
        uiState.first { it.stage == ReportsStage.CONTENT || it.stage == ReportsStage.EMPTY }
    }

    private fun onMain(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }

    private class FakeReportGenerator(private val root: File) : ReportGenerator {
        var generateCalls: Int = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun preview(
            month: YearMonth,
            format: ReportFormat,
            privacy: ReportPrivacySelection,
        ) = reportProjectionFixture()

        override suspend fun generate(
            month: YearMonth,
            format: ReportFormat,
            privacy: ReportPrivacySelection,
            protectedArtifact: File?,
        ): GeneratedLocalReport {
            generateCalls++
            gate?.await()
            val file = File(root, "generated-$generateCalls.${format.extension}").apply {
                writeBytes(
                    if (format == ReportFormat.PDF) {
                        "%PDF-fixture".toByteArray()
                    } else {
                        byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1)
                    },
                )
            }
            val projection = reportProjectionFixture()
            return GeneratedLocalReport(
                projection,
                ReportArtifact(
                    file,
                    format,
                    "MiGuardia_2026-08_informe_parcial.${format.extension}",
                    file.length(),
                ),
            )
        }
    }

    private class FakeReportDestination : ReportDestination {
        val discarded = mutableListOf<Uri>()

        override suspend fun save(artifact: ReportArtifact, destination: Uri) {
            error("No debe guardar en esta prueba")
        }

        override suspend fun discard(destination: Uri): Boolean {
            discarded += destination
            return true
        }
    }

    private object EmptyPhotoRepository : SchedulePhotoRepository {
        override fun observeForMonth(month: YearMonth): Flow<List<SchedulePhoto>> = flowOf(emptyList())
        override suspend fun getById(id: UUID): SchedulePhoto? = null
        override suspend fun insert(photo: SchedulePhoto) = Unit
        override suspend fun update(photo: SchedulePhoto) = Unit
        override suspend fun delete(id: UUID) = Unit
    }

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
    }
}
