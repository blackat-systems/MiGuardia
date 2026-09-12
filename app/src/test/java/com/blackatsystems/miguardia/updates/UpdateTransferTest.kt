package com.blackatsystems.miguardia.updates

import com.blackatsystems.miguardia.beta.BetaCrypto
import java.io.*
import java.nio.file.Files
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateTransferTest {
    @get:Rule val temporary = TemporaryFolder()
    private val transport = FixtureUpdateTransport()
    private val inspector = FixtureUpdateInspector()
    private fun service(folder: File) = UpdateService(transport, inspector, UpdateFiles(folder), Clock.fixed(updateNow, ZoneOffset.UTC))

    @Test fun completeAuthenticatedTransferAndRevalidationUseOnlyFourEndpoints(): Unit = runBlocking {
        val root = temporary.newFolder()
        val service = service(root)
        val release = service.release()!!
        val selected = service.select(release, updateInstalled)
        var verifying = false
        val file = service.download(selected, { true }, {}, { verifying = true })
        assertTrue(verifying)
        assertArrayEquals(updateBytes, file.file.readBytes())
        service.validate(file) { true }
        assertEquals(UpdateEndpoint.entries, transport.paths)
        assertEquals(release.downloadPath, transport.requests.last().path)
        service.discard(file)
        assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun truncationOverflowCorruptionWrongMimePartialResponseAndLengthAreRejectedAndCleaned(): Unit = runBlocking {
        val scenarios: List<() -> Unit> = listOf(
            { transport.binary = updateBytes.dropLast(1).toByteArray() },
            { transport.binary = updateBytes + 0 },
            { transport.binary = ByteArray(updateBytes.size) { 5 } },
            { transport.mime = "text/html" }, { transport.mime = "application/json" },
            { transport.status = 206 }, { transport.status = 304 },
            { transport.declaredSize = updateBytes.size.toLong() + 1 },
        )
        for (scenario in scenarios) {
            transport.binary = updateBytes; transport.mime = APK_MIME; transport.status = 200; transport.declaredSize = updateBytes.size.toLong()
            scenario()
            val root = temporary.newFolder()
            val service = service(root)
            assertThrows(Exception::class.java) { runBlocking { service.download(service.select(updateRelease(), updateInstalled), { true }, {}, {}) } }
            assertTrue(root.listFiles()!!.isEmpty())
        }
    }
    @Test fun parsedApkMustMatchEveryMetadataAndInstalledBinding() {
        val valid = inspector.apk
        val bad = listOf(valid.copy(packageName = "other.app"), valid.copy(versionCode = 2), valid.copy(versionCode = 4),
            valid.copy(versionName = "2.0.0"), valid.copy(minAndroid = 27), valid.copy(certificates = emptyList()),
            valid.copy(certificates = listOf("a".repeat(64))), valid.copy(certificates = listOf(PUBLIC_CERTIFICATE, "c".repeat(64))), valid.copy(split = true))
        bad.forEach { assertThrows(Exception::class.java) { validateUpdateApk(it, updateRelease(), updateInstalled) } }
    }
    @Test fun rejectedApkAndChangedSessionOrInstalledVersionCannotBeSaved(): Unit = runBlocking {
        val root = temporary.newFolder()
        val service = service(root)
        val selection = service.select(updateRelease(), updateInstalled)
        inspector.apk = inspector.apk.copy(certificates = listOf("c".repeat(64)))
        assertThrows(Exception::class.java) { runBlocking { service.download(selection, { true }, {}, {}) } }
        assertTrue(root.listFiles()!!.isEmpty())
        inspector.apk = inspector.apk.copy(certificates = listOf(PUBLIC_CERTIFICATE))
        val file = service.download(selection, { true }, {}, {})
        service.select(updateRelease(), updateInstalled)
        assertThrows(Exception::class.java) { runBlocking { service.validate(file) { true } } }
        inspector.current = updateInstalled.copy(identity = updateInstalled.identity.copy(versionCode = 3))
        assertThrows(Exception::class.java) { runBlocking { service.validate(file) { true } } }
        service.discard(file)
    }
    @Test fun cancellationDuringTransferOnlyRemovesItsOwnedPartial(): Unit = runBlocking {
        val root = temporary.newFolder()
        val unrelated = File(root, "keep.txt").apply { writeText("keep") }
        var allowed = true
        val service = service(root)
        assertThrows(Exception::class.java) { runBlocking { service.download(service.select(updateRelease(), updateInstalled), { allowed }, { allowed = false }, {}) } }
        assertEquals(listOf("keep.txt"), root.listFiles()!!.map { it.name })
        assertEquals("keep", unrelated.readText())
    }
    @Test fun cancellationAtReturnFromIoRemovesCompletedPrivateApk(): Unit = runBlocking {
        val root = temporary.newFolder()
        val entered = CompletableDeferred<Unit>()
        val releaseReturn = java.util.concurrent.CountDownLatch(1)
        val dispatcher = object : CoroutineDispatcher() {
            var dispatches = 0
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                Dispatchers.Default.dispatch(context) {
                    dispatches++
                    if (dispatches > 1) { entered.complete(Unit); releaseReturn.await(3, java.util.concurrent.TimeUnit.SECONDS) }
                    block.run()
                }
            }
        }
        val service = service(root)
        val selected = service.select(updateRelease(), updateInstalled)
        val job = launch(dispatcher) { service.download(selected, { true }, {}, {}) }
        withTimeout(5000) { entered.await() }
        job.cancel()
        releaseReturn.countDown()
        job.join()
        assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun streamingHasBoundedReadsAndDetectsDiskFullWithoutLoadingWholeApk(): Unit = runBlocking {
        var remaining = 4L * 1024 * 1024
        var maximumRead = 0
        val input = object : InputStream() {
            override fun read(): Int = error("Use bounded buffer")
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                maximumRead = maxOf(maximumRead, len)
                if (remaining == 0L) return -1
                val count = minOf(len.toLong(), remaining).toInt()
                bytes.fill(3, off, off + count); remaining -= count
                return count
            }
        }
        val digest = copyUpdate(input, null, remaining)
        assertEquals(4L * 1024 * 1024, digest.bytes)
        assertTrue(maximumRead <= 32 * 1024)
        val full = object : OutputStream() { override fun write(value: Int) { throw IOException("Synthetic disk full") } }
        assertThrows(IOException::class.java) { runBlocking { copyUpdate(updateBytes.inputStream(), full, updateBytes.size.toLong()) } }
        Unit
    }
    @Test fun processCleanupLeavesUnrelatedFilesAndDirectoriesAndRefusesForeignPaths() {
        val root = temporary.newFolder()
        val files = UpdateFiles(root)
        val part = File(root, "12345678-1234-1234-1234-123456789abc.part").apply { writeText("old") }
        File(root, "foreign.txt").writeText("keep")
        File(root, "12345678-1234-1234-1234-123456789abc.apk").mkdir()
        files.cleanupAfterProcessStart()
        assertFalse(part.exists())
        assertTrue(File(root, "foreign.txt").exists())
        assertTrue(File(root, "12345678-1234-1234-1234-123456789abc.apk").isDirectory)
        assertThrows(Exception::class.java) { files.discard(temporary.newFile()) }
    }
    private fun artifact(file: File): VerifiedUpdate {
        file.writeBytes(updateBytes)
        return VerifiedUpdate(UpdateSelection(updateRelease(), updateInstalled, Any(), 0), file)
    }
    private class Document : UpdateDocument {
        val bytes = ByteArrayOutputStream()
        var deleted = false
        var readAvailable = true
        var corrupt = false
        var failAt: String? = null
        override fun openEmptyOutput(): OutputStream {
            if (failAt == "open") throw IOException()
            return object : OutputStream() {
                override fun write(value: Int) { if (failAt == "write") throw IOException(); bytes.write(value) }
                override fun flush() { if (failAt == "flush") throw IOException() }
                override fun close() { if (failAt == "close") throw IOException() }
            }
        }
        override fun openInput(): InputStream? = if (!readAvailable) null else
            (if (corrupt) byteArrayOf(7) else bytes.toByteArray()).inputStream()
        override fun discardCreated() { deleted = true }
    }
    @Test fun documentWriteReadBackAndWriteOnlyHaveDifferentEvidence(): Unit = runBlocking {
        val file = artifact(temporary.newFile())
        val readBack = Document()
        assertEquals(UpdateSaveVerification.READ_BACK, UpdateDocumentWriter().save(file, readBack) { true })
        assertArrayEquals(updateBytes, readBack.bytes.toByteArray())
        assertFalse(readBack.deleted)
        val writeOnly = Document().apply { readAvailable = false }
        assertEquals(UpdateSaveVerification.WRITE_ONLY, UpdateDocumentWriter().save(file, writeOnly) { true })
        assertFalse(writeOnly.deleted)
    }
    @Test fun openWriteFlushCloseAndReadBackFailuresNeverReportSavedOrDeleteUnopenedDocument(): Unit = runBlocking {
        val file = artifact(temporary.newFile())
        for (failure in listOf("open", "write", "flush", "close", "read")) {
            val document = Document().apply { failAt = failure; corrupt = failure == "read" }
            assertThrows(Exception::class.java) { runBlocking { UpdateDocumentWriter(UpdateSaveExecutor()).save(file, document) { true } } }
            assertFalse(document.deleted)
        }
        val blocked = Document()
        assertThrows(Exception::class.java) { runBlocking { UpdateDocumentWriter().save(file, blocked) { false } } }
        assertEquals(0, blocked.bytes.size()); assertFalse(blocked.deleted)
    }
}
