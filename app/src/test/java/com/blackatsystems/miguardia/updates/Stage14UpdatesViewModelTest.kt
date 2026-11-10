package com.blackatsystems.miguardia.updates

import androidx.lifecycle.SavedStateHandle
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.UnknownHostException
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Etapa 14 · Actualizaciones (A275, A276, A279, A280, A281) con dobles en memoria; sin red ni archivos de la app. */
class Stage14UpdatesViewModelTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Operations(val file: File) : UpdateOperations {
        @Volatile var answer: UpdateRelease? = updateRelease()
        @Volatile var error: Exception? = null
        @Volatile var retryMillis = 0L
        @Volatile var checks = 0
        @Volatile var downloads = 0
        @Volatile var holdRelease: CompletableDeferred<Unit>? = null
        @Volatile var holdDownload: CompletableDeferred<Unit>? = null
        @Volatile var holdVerified: CompletableDeferred<Unit>? = null
        @Volatile var progress: ((Long) -> Unit)? = null
        override fun retryRemainingMillis() = retryMillis
        override fun installed() = updateInstalled
        override suspend fun release(): UpdateRelease? {
            checks++
            holdRelease?.await()
            error?.let { retryMillis = (it as? UpdateServiceFailure)?.retryMillis ?: 0; throw it }
            return answer
        }
        override suspend fun select(release: UpdateRelease, installed: InstalledUpdateApp) = UpdateSelection(release, installed, this, 0)
        override suspend fun download(selection: UpdateSelection, allowed: () -> Boolean, progress: (Long) -> Unit, verifying: () -> Unit): VerifiedUpdate {
            downloads++
            this.progress = progress
            holdDownload?.await()
            check(allowed())
            verifying()
            holdVerified?.await()
            return VerifiedUpdate(selection, file)
        }
        override suspend fun validate(artifact: VerifiedUpdate, allowed: () -> Boolean) { check(allowed()) }
        override fun discard(artifact: VerifiedUpdate) = Unit
    }

    private class Exporter(private val result: UpdateSaveVerification) : UpdateExporter {
        @Volatile var writes = 0
        override suspend fun save(artifact: VerifiedUpdate, document: UpdateDocument, allowed: () -> Boolean): UpdateSaveVerification {
            check(allowed()); writes++; return result
        }
    }

    private val document = object : UpdateDocument {
        override fun openEmptyOutput(): OutputStream = error("Exporter double must not open a provider")
        override fun openInput(): InputStream? = error("No read")
        override fun discardCreated() = Unit
    }

    private fun operations() = Operations(temporary.newFile().apply { writeBytes(updateBytes) })
    private fun model(scope: CoroutineScope, operations: Operations, exporter: UpdateExporter = Exporter(UpdateSaveVerification.READ_BACK)) =
        UpdatesViewModel(operations, exporter, SavedStateHandle(), Clock.fixed(updateNow, ZoneOffset.UTC), testScope = scope)
    private suspend fun UpdatesViewModel.awaitState(predicate: (UpdatesState) -> Boolean) { withTimeout(5000) { state.first(predicate) } }
    private suspend fun UpdatesViewModel.await(stage: UpdateStage) = awaitState { it.stage == stage }
    private fun UpdatesViewModel.opened(owner: Any): UpdatesViewModel { attachHost(owner) { true }; open(); return this }

    // A275
    @Test fun eachServiceAnswerLeadsToItsOwnStageAndOnlyAnAvailableReleaseCanBeDownloaded(): Unit = runBlocking {
        val newer = updateRelease()
        val cases = listOf(
            null to UpdateStage.NO_RELEASE,
            newer.copy(versionCode = updateInstalled.identity.versionCode) to UpdateStage.CURRENT,
            newer.copy(versionCode = updateInstalled.identity.versionCode - 1) to UpdateStage.CURRENT,
            newer to UpdateStage.AVAILABLE,
            newer.copy(serverIncompatible = true) to UpdateStage.INCOMPATIBLE,
            newer.copy(minAndroid = updateInstalled.deviceApi + 1) to UpdateStage.INCOMPATIBLE,
        )
        for ((answer, expected) in cases) {
            val operations = operations().apply { this.answer = answer }
            val model = model(this, operations).opened(this)
            assertEquals(UpdateStage.IDLE, model.state.value.stage)
            model.checkRelease()
            assertEquals(UpdateStage.CHECKING, model.state.value.stage)
            model.awaitState { !it.busy }
            val state = model.state.value
            assertEquals("answer=$answer", expected, state.stage)
            assertEquals(answer, state.release)
            assertNull(state.message)
            assertEquals(updateInstalled, state.installed)
            assertEquals(1, operations.checks)
            if (expected != UpdateStage.AVAILABLE) {
                model.download(); yield(); delay(50)
                assertEquals("Only an available release may be downloaded", 0, operations.downloads)
                assertEquals(expected, model.state.value.stage)
            }
            model.close()
        }
    }

    // A276
    @Test fun downloadProgressAdvancesInStateUntilTheVerifiedApkIsReady(): Unit = runBlocking {
        val operations = operations().apply { holdDownload = CompletableDeferred(); holdVerified = CompletableDeferred() }
        val model = model(this, operations).opened(this)
        model.checkRelease(); model.await(UpdateStage.AVAILABLE)
        val total = model.state.value.release!!.byteSize
        assertEquals(updateBytes.size.toLong(), total)
        model.download()
        assertEquals(UpdateStage.DOWNLOADING, model.state.value.stage)
        assertEquals(0L, model.state.value.bytes)
        withTimeout(5000) { while (operations.progress == null) yield() }
        val report = operations.progress!!
        var previous = 0L
        for (bytes in listOf(1L, total / 2, total)) {
            report(bytes)
            val state = model.state.value
            assertEquals(UpdateStage.DOWNLOADING, state.stage)
            assertEquals(bytes, state.bytes)
            assertTrue(state.bytes > previous)
            assertEquals(total, state.release!!.byteSize)
            previous = bytes
        }
        operations.holdDownload!!.complete(Unit)
        model.await(UpdateStage.VERIFYING)
        assertEquals(total, model.state.value.bytes)
        operations.holdVerified!!.complete(Unit)
        model.await(UpdateStage.READY)
        assertEquals(1, operations.downloads)
        model.close()
    }

    // A279
    @Test fun savedStateTellsARereadDestinationFromBytesOnlyVerifiedWhileWriting(): Unit = runBlocking {
        for (verification in UpdateSaveVerification.entries) {
            val operations = operations()
            val exporter = Exporter(verification)
            val model = model(this, operations, exporter).opened(this)
            model.checkRelease(); model.await(UpdateStage.AVAILABLE)
            model.download(); model.await(UpdateStage.READY)
            assertNull(model.state.value.saveVerification)
            model.prepareSave {}; model.await(UpdateStage.PICKER)
            assertNull(model.state.value.saveVerification)
            model.documentReturned(document); model.await(UpdateStage.SAVED)
            assertEquals(verification, model.state.value.saveVerification)
            assertEquals(1, exporter.writes)
            assertFalse(model.state.value.busy)
            model.close()
        }
        assertEquals(setOf(UpdateSaveVerification.READ_BACK, UpdateSaveVerification.WRITE_ONLY), UpdateSaveVerification.entries.toSet())
    }

    // A280
    @Test fun userCancelDuringSearchLeavesRestWithoutReleaseOrLaterRequests(): Unit = runBlocking {
        val operations = operations().apply { holdRelease = CompletableDeferred() }
        val model = model(this, operations).opened(this)
        model.checkRelease()
        withTimeout(5000) { while (operations.checks == 0) yield() }
        assertEquals(UpdateStage.CHECKING, model.state.value.stage)
        model.cancel()
        assertEquals(UpdateStage.CANCELLED, model.state.value.stage)
        assertFalse(model.state.value.busy)
        operations.holdRelease!!.complete(Unit)
        delay(150)
        val state = model.state.value
        assertEquals("A late answer must not revive the cancelled search", UpdateStage.CANCELLED, state.stage)
        assertNull(state.release); assertNull(state.message); assertEquals(0L, state.bytes)
        assertEquals(1, operations.checks); assertEquals(0, operations.downloads)
        model.download(); yield()
        assertEquals(0, operations.downloads)
        var launches = 0
        model.prepareSave { launches++ }; yield()
        assertEquals(0, launches)
        // Rest, not a dead end: an explicit new search is still possible.
        operations.holdRelease = null
        model.checkRelease(); model.await(UpdateStage.AVAILABLE)
        assertEquals(2, operations.checks)
        model.close()
    }

    // A280
    @Test fun userCancelDuringDownloadLeavesRestWithoutArtifactOrLaterRequests(): Unit = runBlocking {
        val operations = operations().apply { holdDownload = CompletableDeferred() }
        val exporter = Exporter(UpdateSaveVerification.READ_BACK)
        val model = model(this, operations, exporter).opened(this)
        model.checkRelease(); model.await(UpdateStage.AVAILABLE)
        model.download()
        withTimeout(5000) { while (operations.progress == null) yield() }
        operations.progress!!(7)
        assertEquals(7L, model.state.value.bytes)
        model.cancel()
        assertEquals(UpdateStage.CANCELLED, model.state.value.stage)
        assertFalse(model.state.value.busy)
        operations.progress!!(999)
        operations.holdDownload!!.complete(Unit)
        delay(150)
        val state = model.state.value
        assertEquals("A late transfer must not revive the cancelled download", UpdateStage.CANCELLED, state.stage)
        assertNull(state.release); assertNull(state.message); assertEquals(0L, state.bytes)
        assertEquals(1, operations.checks); assertEquals(1, operations.downloads)
        // No artifact survives: nothing can be offered to the selector or written.
        var launches = 0
        model.prepareSave { launches++ }; yield()
        model.documentReturned(document); yield()
        assertEquals(0, launches); assertEquals(0, exporter.writes)
        model.download(); yield()
        assertEquals("Download needs a new explicit search first", 1, operations.downloads)
        assertEquals(UpdateStage.CANCELLED, model.state.value.stage)
        model.close()
    }

    // A281
    @Test fun missingConnectionShowsRecoverableErrorWithoutWaitAndAllowsSearchingAgain(): Unit = runBlocking {
        for (offline in listOf<Exception>(UnknownHostException("offline.invalid"), IOException("Update transfer timed out"))) {
            val operations = operations().apply { error = offline }
            val model = model(this, operations).opened(this)
            model.checkRelease(); model.await(UpdateStage.ERROR)
            val state = model.state.value
            assertFalse(state.busy)
            assertEquals("No pudimos completar o verificar la operación. Revisá la conexión y el espacio disponible y volvé a buscar. Tus datos de MiGuardia siguen en el teléfono.", state.message)
            assertNull(state.release); assertNull(state.retryAt); assertEquals(0L, state.retryRemainingMillis)
            assertEquals(1, operations.checks)
            delay(100)
            assertEquals("A failed search must not retry by itself", 1, operations.checks)
            operations.error = null
            model.checkRelease(); model.await(UpdateStage.AVAILABLE)
            assertEquals(2, operations.checks)
            assertNull(model.state.value.message)
            model.close()
        }
    }

    // A281
    @Test fun retryWaitCountsDownAndBlocksSearchingUntilItExpires(): Unit = runBlocking {
        val failure = UpdateServiceFailure(429, "RATE_LIMITED", updateNow.plusSeconds(60), 60_000)
        val operations = operations().apply { error = failure }
        val model = model(this, operations).opened(this)
        model.checkRelease()
        model.awaitState { it.stage == UpdateStage.UNAVAILABLE && it.retryRemainingMillis > 0 }
        var state = model.state.value
        assertEquals("Hay demasiados intentos. Esperá a que termine la cuenta regresiva antes de buscar de nuevo.", state.message)
        assertEquals(failure.retryAt, state.retryAt)
        assertEquals(60_000L, state.retryRemainingMillis)
        assertFalse(state.busy)
        // While the wait lasts, neither repeated taps nor time passing start a request.
        operations.error = null
        model.checkRelease(); model.checkRelease(); model.download(); yield()
        assertEquals(UpdateStage.UNAVAILABLE, model.state.value.stage)
        operations.retryMillis = 30_000
        model.awaitState { it.retryRemainingMillis == 30_000L }
        model.checkRelease(); yield()
        operations.retryMillis = 1
        model.awaitState { it.retryRemainingMillis == 1L }
        model.checkRelease(); yield()
        assertEquals(1, operations.checks); assertEquals(0, operations.downloads)
        assertEquals(UpdateStage.UNAVAILABLE, model.state.value.stage)
        // Once it expires the countdown reaches zero and an explicit search works again.
        operations.retryMillis = 0
        model.awaitState { it.retryRemainingMillis == 0L }
        state = model.state.value
        assertEquals(UpdateStage.UNAVAILABLE, state.stage)
        assertEquals("Expiry alone must not search", 1, operations.checks)
        model.checkRelease(); model.await(UpdateStage.AVAILABLE)
        assertEquals(2, operations.checks)
        assertNull(model.state.value.message); assertNull(model.state.value.retryAt)
        assertNotNull(model.state.value.release)
        model.close()
    }
}
