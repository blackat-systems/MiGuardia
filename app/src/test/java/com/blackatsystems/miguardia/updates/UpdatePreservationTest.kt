package com.blackatsystems.miguardia.updates
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
class UpdatePreservationTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun publicRuntimeViewModelServiceWriterReturnAndLateCallbacksPreserveInertRecords(): Unit = runBlocking {
        val issuer = com.blackatsystems.miguardia.beta.BetaTestIssuer()
        val active = com.blackatsystems.miguardia.beta.BetaRecord(installationHash = issuer.fingerprint, activation = issuer.sign(issuer.payload()))
        val opinion = com.blackatsystems.miguardia.beta.BetaOpinion.create(com.blackatsystems.miguardia.beta.BetaCategory.OTHER,
            com.blackatsystems.miguardia.beta.BetaArea.OTHER, "Fictitious pending opinion", true)
        val pending = com.blackatsystems.miguardia.beta.BetaPending("pending_test_123", opinion,
            com.blackatsystems.miguardia.beta.BetaCrypto.encode(issuer.identity().sign(byteArrayOf(1))))
        val records = listOf(null, active, "corrupt bytes", active.copy(activation = issuer.sign(issuer.payload().replace("com.blackatsystems.miguardia", "other.package"))),
            active.copy(dueObserved = true), active.copy(dueObserved = true, pending = pending))
        for (record in records) {
            val root = temporary.newFolder()
            val residue = File(root, "beta-record")
            if (record != null) residue.writeText(if (record is String) record else com.blackatsystems.miguardia.beta.BetaRecordCodec.encode(record as com.blackatsystems.miguardia.beta.BetaRecord))
            val preferences = File(root, "preferences").apply { writeText("fictitious theme=dark; zoom=200; lock=enabled; notifications=off") }
            fun preserved() = listOf(residue,preferences).filter {it.exists()}.associate {it.name to updateHash(it.readBytes())}
            val before = preserved()
            var recovery = false; var unlocked = false; var started = 0
            val runtime = com.blackatsystems.miguardia.LocalRuntimeActivation({recovery}) { started++ }
            assertFalse(runtime.ensureStarted()); recovery = true
            assertTrue(runtime.ensureStarted()); assertTrue(runtime.ensureStarted()); assertEquals(1,started)
            val transport = FixtureUpdateTransport()
            val service = UpdateService(transport, FixtureUpdateInspector(), UpdateFiles(File(root,"updates")))
            val model = UpdatesViewModel(service, UpdateDocumentWriter(), androidx.lifecycle.SavedStateHandle(), testScope = this)
            suspend fun await(stage: UpdateStage) = withTimeout(5000) { while(model.state.value.stage != stage) yield() }
            model.attachHost(this) { recovery && unlocked }
            model.open(); assertFalse(model.state.value.open); assertTrue(transport.paths.isEmpty())
            unlocked = true; model.open(); assertTrue(model.state.value.open); assertTrue(transport.paths.isEmpty())
            model.checkRelease(); await(UpdateStage.AVAILABLE)
            model.download(); await(UpdateStage.READY)
            var launches = 0
            model.prepareSave { launches++ }; await(UpdateStage.PICKER)
            val output = ByteArrayOutputStream(); var writes = 0
            val document = object : UpdateDocument {
                override fun openEmptyOutput(): java.io.OutputStream { writes++; return output }
                override fun openInput() = output.toByteArray().inputStream()
                override fun discardCreated() = error("Successful document must stay")
            }
            model.pauseHost(); model.documentReturned(document)
            assertEquals(0,writes)
            model.attachHost(this) { recovery && unlocked }; await(UpdateStage.SAVED)
            model.documentReturned(document); yield()
            assertEquals(1,launches); assertEquals(1,writes); assertArrayEquals(updateBytes,output.toByteArray())
            model.close(); assertFalse(model.state.value.open)
            assertEquals(before,preserved())
            assertEquals(listOf(UpdateEndpoint.RELEASE,UpdateEndpoint.DOWNLOAD),transport.paths)
            // Late callback after close is inert; malformed response does not reopen beta or touch input data.
            model.documentReturned(document); assertEquals(1,writes)
            transport.transform = {_,_ -> UpdateResponse(200,"broken".byteInputStream(),"application/json",6) }
            model.open(); model.checkRelease(); await(UpdateStage.INVALID_RESPONSE); model.close()
            assertEquals(before,preserved())
            assertTrue(root.list()!!.none { it.contains("identity") || it.contains("keystore") })
        }
    }
    @Test fun publicCoordinatorIgnoresAllResidualBetaStatesAcrossFullTransferSaveAndFailures(): Unit = runBlocking {
        for (state in listOf("absent", "valid", "corrupt", "incompatible", "expired", "queued")) {
            val root = temporary.newFolder()
            val residual = File(root, "beta-residual")
            if (state != "absent") residual.writeBytes(state.toByteArray())
            val before = if (residual.exists()) residual.readBytes() else null
            val work = File(root, "work").apply { writeText("synthetic labor history") }
            val transport = FixtureUpdateTransport()
            val service = UpdateService(transport, FixtureUpdateInspector(), UpdateFiles(File(root,"updates")))
            val release = service.release()!!
            val selected = service.select(release, service.installed())
            val artifact = service.download(selected, {true}, {}, {})
            val output = ByteArrayOutputStream()
            service.validate(artifact) {true}
            UpdateDocumentWriter().save(artifact, object : UpdateDocument {
                override fun openEmptyOutput() = output
                override fun openInput() = output.toByteArray().inputStream()
                override fun discardCreated() = error("Must preserve success")
            }) {true}
            assertArrayEquals(updateBytes, output.toByteArray())
            assertEquals(listOf(UpdateEndpoint.RELEASE, UpdateEndpoint.DOWNLOAD), transport.paths)
            service.discard(artifact)
            assertThrows(Exception::class.java) { runBlocking { service.download(selected,{false},{},{}) } }
            transport.binary = byteArrayOf(1)
            assertThrows(Exception::class.java) { runBlocking { service.download(selected,{true},{},{}) } }
            assertEquals(before != null, residual.exists())
            if (before != null) assertArrayEquals(before, residual.readBytes())
            assertEquals("synthetic labor history", work.readText())
            assertEquals(setOfNotNull("work", "updates", "beta-residual".takeIf {before != null}), root.list()!!.toSet())
        }
    }
}
