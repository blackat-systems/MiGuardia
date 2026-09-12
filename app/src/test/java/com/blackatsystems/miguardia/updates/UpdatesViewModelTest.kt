package com.blackatsystems.miguardia.updates

import androidx.lifecycle.SavedStateHandle
import java.io.*
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdatesViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private class Operations(val file: File) : UpdateOperations {
        var current = updateInstalled
        var checks = 0
        var downloads = 0
        var validations = 0
        var hold: CompletableDeferred<Unit>? = null
        var holdRelease: CompletableDeferred<Unit>? = null
        var error: Exception? = null
        var lateProgress: ((Long) -> Unit)? = null
        private var retryMillis = 0L
        override fun retryRemainingMillis() = retryMillis
        override fun installed() = current
        override suspend fun release(): UpdateRelease? { checks++; holdRelease?.await(); error?.let { retryMillis = (it as? UpdateServiceFailure)?.retryMillis ?: 0; throw it }; return updateRelease() }
        override suspend fun select(release: UpdateRelease, installed: InstalledUpdateApp) = UpdateSelection(release, installed, this, 0)
        override suspend fun download(selection: UpdateSelection, allowed: () -> Boolean, progress: (Long) -> Unit, verifying: () -> Unit): VerifiedUpdate {
            downloads++; lateProgress = progress
            hold?.await()
            check(allowed()); verifying(); return VerifiedUpdate(selection, file)
        }
        override suspend fun validate(artifact: VerifiedUpdate, allowed: () -> Boolean) { validations++; check(allowed()); check(current == artifact.selection.installed) }
        override fun discard(artifact: VerifiedUpdate) = Unit
    }
    private class Exporter : UpdateExporter {
        var writes = 0
        override suspend fun save(artifact: VerifiedUpdate, document: UpdateDocument, allowed: () -> Boolean): UpdateSaveVerification {
            check(allowed()); writes++; return UpdateSaveVerification.READ_BACK
        }
    }
    private val document = object : UpdateDocument {
        override fun openEmptyOutput(): OutputStream = error("Exporter double must not open a provider")
        override fun openInput(): InputStream? = error("No read")
        override fun discardCreated() = Unit
    }
    private fun operations() = Operations(temporary.newFile().apply { writeBytes(updateBytes) })
    private fun model(scope: CoroutineScope, operations: Operations, exporter: Exporter = Exporter(), saved: SavedStateHandle = SavedStateHandle()) =
        UpdatesViewModel(operations, exporter, saved, Clock.fixed(updateNow, ZoneOffset.UTC), testScope = scope)
    private suspend fun UpdatesViewModel.await(stage: UpdateStage) { withTimeout(5000) { state.first { it.stage == stage } } }
    private suspend fun UpdatesViewModel.ready() { checkRelease(); await(UpdateStage.AVAILABLE); download(); await(UpdateStage.READY) }

    @Test fun openingRecreationAndDoubleTapsNeverStartAnImplicitOrDuplicateRequest(): Unit = runBlocking {
        val operations = operations()
        val model = model(this, operations)
        model.attachHost(this) { true }; model.open()
        assertEquals(0, operations.checks); assertEquals("2.0.0-beta.1", model.state.value.installed!!.versionName)
        model.checkRelease(); model.checkRelease(); model.await(UpdateStage.AVAILABLE)
        assertEquals(1, operations.checks)
        operations.hold = CompletableDeferred()
        model.download(); model.download()
        withTimeout(5000) { while (operations.downloads == 0) yield() }
        model.pauseHost(); model.onBackground(true); model.detachHost(this)
        model.attachHost(Any()) { true }; operations.hold!!.complete(Unit)
        model.await(UpdateStage.READY)
        assertEquals(1, operations.downloads); assertEquals(1, operations.checks)
        model.close()
    }
    @Test fun realBackgroundCancelsTransferAndLateProgressCannotReviveIt(): Unit = runBlocking {
        val operations = operations().apply { hold = CompletableDeferred() }
        val model = model(this, operations)
        model.attachHost(this) { true }; model.open(); model.checkRelease(); model.await(UpdateStage.AVAILABLE)
        model.download()
        withTimeout(5000) { while (operations.downloads == 0) yield() }
        model.pauseHost(); model.onBackground(false)
        model.await(UpdateStage.INTERRUPTED)
        operations.lateProgress!!(999)
        model.attachHost(Any()) { true }
        assertEquals(UpdateStage.INTERRUPTED, model.state.value.stage); assertEquals(0L, model.state.value.bytes)
        assertEquals(1, operations.downloads); model.close()
    }
    @Test fun cancelledSelectorCannotWriteAnotherOperationsApkOrBeReplacedBeforeItsCallback(): Unit = runBlocking {
        val operations = operations(); val exporter = Exporter(); val saved = SavedStateHandle()
        val model = model(this, operations, exporter, saved)
        model.attachHost(this) { true }; model.open(); model.ready()
        var launches = 0
        model.prepareSave { launches++ }; model.await(UpdateStage.PICKER)
        model.cancel(); model.ready()
        model.prepareSave { launches++ }
        assertEquals(1, launches); assertEquals(UpdateStage.READY, model.state.value.stage)
        model.documentReturned(document)
        assertEquals(0, exporter.writes); assertEquals(UpdateStage.READY, model.state.value.stage)
        model.prepareSave { launches++ }; model.await(UpdateStage.PICKER)
        assertEquals(2, launches)
        model.documentReturned(document); model.await(UpdateStage.SAVED)
        assertEquals(1, exporter.writes); model.close()
    }
    @Test fun resultBeforeResumeWaitsAndAClosedGateNeverWritesBehindTheLock(): Unit = runBlocking {
        val operations = operations(); val exporter = Exporter()
        val model = model(this, operations, exporter)
        model.attachHost(this) { true }; model.open(); model.ready()
        model.prepareSave {}; model.await(UpdateStage.PICKER)
        model.pauseHost(); model.onBackground(false); model.documentReturned(document)
        assertEquals(0, exporter.writes)
        model.attachHost(this) { false }
        assertEquals(UpdateStage.INTERRUPTED, model.state.value.stage); assertEquals(0, exporter.writes)
        model.attachHost(this) { true }; assertEquals(0, exporter.writes); model.close()
    }
    @Test fun resultBeforeResumeWritesOnceOnlyAfterReturningToAnAllowedRoot(): Unit = runBlocking {
        val operations = operations(); val exporter = Exporter()
        val model = model(this, operations, exporter)
        model.attachHost(this) { true }; model.open(); model.ready()
        model.prepareSave {}; model.await(UpdateStage.PICKER)
        model.pauseHost(); model.onBackground(false); model.documentReturned(document)
        assertEquals(0, exporter.writes)
        model.attachHost(this) { true }; model.await(UpdateStage.SAVED)
        model.documentReturned(document); assertEquals(1, exporter.writes); model.close()
    }
    @Test fun processRestorationKeepsOnlyBooleansAndDiscardsStalePickerResultWithoutRequests(): Unit = runBlocking {
        val operations = operations(); val exporter = Exporter()
        val saved = SavedStateHandle(mapOf("updates.open" to true, "updates.interrupted" to true, "updates.pickerPending" to true))
        val model = model(this, operations, exporter, saved)
        model.documentReturned(document); model.attachHost(this) { true }
        assertEquals(UpdateStage.INTERRUPTED, model.state.value.stage)
        assertEquals(0, operations.checks); assertEquals(0, operations.downloads); assertEquals(0, exporter.writes)
        saved.keys().forEach { assertTrue(saved.get<Any>(it) is Boolean) }; model.close()
    }
    @Test fun installedVersionChangeInvalidatesReadyArtifactAndPickerCancellationDoesNotSave(): Unit = runBlocking {
        val operations = operations(); val exporter = Exporter()
        val model = model(this, operations, exporter)
        model.attachHost(this) { true }; model.open(); model.ready()
        model.prepareSave {}; model.await(UpdateStage.PICKER); model.documentReturned(null)
        assertEquals(UpdateStage.READY, model.state.value.stage); assertEquals(0, exporter.writes)
        operations.current = updateInstalled.copy(identity = updateInstalled.identity.copy(versionCode = 3))
        model.attachHost(this) { true }
        assertEquals(UpdateStage.INTERRUPTED, model.state.value.stage)
        var launches = 0; model.prepareSave { launches++ }; assertEquals(0, launches); model.close()
    }
    @Test fun retryAfterAndFeedbackErrorsNeverLoopOrClaimCurrentVersion(): Unit = runBlocking {
        for (error in listOf(UpdateServiceFailure(429, "RATE_LIMITED", updateNow.plusSeconds(600), 600000), UpdateServiceFailure(403, "ORIGIN_DENIED", null))) {
            val operations = operations().apply { this.error = error }
            val model = model(this, operations)
            model.attachHost(this) { true }; model.open(); model.checkRelease()
            model.await(if (error.code == "ORIGIN_DENIED") UpdateStage.UNAVAILABLE else UpdateStage.UNAVAILABLE)
            assertEquals(1, operations.checks)
            if (error.status == 429) {
                model.close(); model.open()
                assertEquals(error.retryAt, model.state.value.retryAt)
                model.checkRelease(); yield(); assertEquals(1, operations.checks)
            }
            model.close()
        }
    }
    @Test fun gateClosingDuringResponseCannotLeaveAnIdleRequestPermanentlyBusyAfterGateReopens(): Unit = runBlocking {
        val operations = operations().apply { holdRelease = CompletableDeferred() }
        val model = model(this, operations)
        var gate = true
        model.attachHost(this) { gate }; model.open(); model.checkRelease()
        withTimeout(5000) { while (operations.checks == 0) yield() }
        gate = false; operations.holdRelease!!.complete(Unit)
        model.await(UpdateStage.INTERRUPTED)
        assertFalse(model.state.value.busy)
        gate = true
        model.checkRelease(); model.await(UpdateStage.AVAILABLE)
        assertEquals(2, operations.checks); model.close()
    }
}
