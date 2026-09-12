package com.blackatsystems.miguardia.updates

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateSaveCancellationTest {
    @Test fun blockedPlatformCancellationRetainsLeaseAfterIoReturns() = runBlocking {
        val executor = UpdateSaveExecutor()
        val ioEntered = CountDownLatch(1)
        val ioRelease = CountDownLatch(1)
        val cancelEntered = CountDownLatch(1)
        val cancelRelease = CountDownLatch(1)
        val doc = object : UpdateDocument {
            override fun openEmptyOutput(): OutputStream {
                ioEntered.countDown(); check(ioRelease.await(10, TimeUnit.SECONDS))
                return ByteArrayOutputStream()
            }
            override fun openInput(): InputStream? = null
            override fun discardCreated() = error("No deletion")
            override fun cancelPendingIo() { cancelEntered.countDown(); check(cancelRelease.await(10, TimeUnit.SECONDS)) }
        }
        val apk = artifact()
        val job = launch { UpdateDocumentWriter(executor).save(apk, doc) { true }; fail("Cancelled save succeeded") }
        try {
            await(ioEntered)
            withTimeout(1_000) { job.cancelAndJoin() }
            await(cancelEntered)
            ioRelease.countDown()
            delay(50)
            repeat(100) { assertThrows(IOException::class.java) { runBlocking { UpdateDocumentWriter(executor).save(apk, BlockedDocument("none")) { true } } } }
        } finally { ioRelease.countDown(); cancelRelease.countDown(); job.cancelAndJoin() }
        withTimeout(3_000) {
            while (true) {
                try { UpdateDocumentWriter(executor).save(apk, BlockedDocument("none")) { true }; break }
                catch (_: IOException) { delay(10) }
            }
        }
    }
    @get:Rule val temporary = TemporaryFolder()
    private fun artifact(): VerifiedUpdate = VerifiedUpdate(
        UpdateSelection(updateRelease(), updateInstalled, Any(), 0), temporary.newFile().apply { writeBytes(updateBytes) })

    private class BlockedDocument(val phase: String) : UpdateDocument {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writes = AtomicInteger()
        val closes = AtomicInteger()
        val deletes = AtomicInteger()
        val cancels = AtomicInteger()
        val bytes = ByteArrayOutputStream()
        fun block(at: String) { if (phase == at) { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) } }
        override fun openEmptyOutput(): OutputStream {
            block("open-output")
            return object : OutputStream() {
                override fun write(value: Int) { block("write"); writes.incrementAndGet(); bytes.write(value) }
                override fun flush() { block("flush") }
                override fun close() { block("close-output"); closes.incrementAndGet() }
            }
        }
        override fun openInput(): InputStream {
            block("open-input")
            return object : ByteArrayInputStream(bytes.toByteArray()) {
                override fun read(b: ByteArray, off: Int, len: Int): Int { block("read"); return super.read(b, off, len) }
                override fun close() { block("close-input"); closes.incrementAndGet() }
            }
        }
        override fun cancelPendingIo() { cancels.incrementAndGet(); block("cancel") }
        override fun discardCreated() { deletes.incrementAndGet(); error("No late deletion is allowed") }
    }
    private suspend fun await(latch: CountDownLatch) = withContext(Dispatchers.IO) { assertTrue(latch.await(3, TimeUnit.SECONDS)) }

    @Test fun cancellationReturnsWithoutWaitingForAnyProviderPhaseAndRetriesCannotAccumulate() = runBlocking {
        for (phase in listOf("open-output", "write", "flush", "close-output", "open-input", "read", "close-input")) {
            val executor = UpdateSaveExecutor()
            val doc = BlockedDocument(phase)
            val apk = artifact()
            var success = false
            val job = launch { UpdateDocumentWriter(executor).save(apk, doc) { true }; success = true }
            try {
                await(doc.entered)
                withTimeout(1_000) { job.cancelAndJoin() }
                repeat(100) {
                    try { UpdateDocumentWriter(executor).save(apk, BlockedDocument("none")) { true }; fail("Admitted while provider is blocked: $phase") }
                    catch (_: IOException) { }
                }
                assertFalse(success)
                assertEquals(0, doc.deletes.get())
            } finally { doc.release.countDown(); job.cancelAndJoin() }
            // The lease is recovered only once every detached task really finishes.
            withTimeout(3_000) {
                while (true) {
                    try { UpdateDocumentWriter(executor).save(apk, BlockedDocument("none")) { true }; break }
                    catch (_: IOException) { delay(10) }
                }
            }
            assertFalse(success)
            assertEquals(0, doc.deletes.get())
            if (phase == "open-output") assertEquals(0, doc.writes.get())
        }
    }

    @Test fun timeoutReleasesCallerButNotProviderLeaseAndNeverDeletesLateDocument() = runBlocking {
        val executor = UpdateSaveExecutor()
        val doc = BlockedDocument("write")
        val apk = artifact()
        try {
            val result = async { runCatching { UpdateDocumentWriter(executor, 100).save(apk, doc) { true } } }
            await(doc.entered)
            assertTrue(withTimeout(1_000) { result.await() }.isFailure)
            assertThrows(IOException::class.java) { runBlocking { UpdateDocumentWriter(executor).save(apk, BlockedDocument("none")) { true } } }
            assertEquals(0, doc.deletes.get())
        } finally { doc.release.countDown() }
    }

    @Test fun monotonicDeadlineRejectsSuccessEvenBeforeTimerThreadRuns(): Unit = runBlocking {
        val now = AtomicLong()
        val executor = UpdateSaveExecutor(now::get)
        val doc = object : UpdateDocument {
            override fun openEmptyOutput(): OutputStream = object : ByteArrayOutputStream() {
                override fun flush() { now.set(TimeUnit.MINUTES.toNanos(3)) }
            }
            override fun openInput(): InputStream? = null
            override fun discardCreated() = error("Must not delete")
        }
        assertThrows(IOException::class.java) { runBlocking { UpdateDocumentWriter(executor).save(artifact(), doc) { true } } }
    }

    @Test fun failedCloseQuarantinesCapacityAcrossWriterInstances() = runBlocking {
        val executor = UpdateSaveExecutor()
        val doc = object : UpdateDocument {
            override fun openEmptyOutput(): OutputStream = object : ByteArrayOutputStream() {
                override fun close() { throw IOException("synthetic close failure") }
            }
            override fun openInput(): InputStream? = null
            override fun discardCreated() = error("Must not delete")
        }
        val apk = artifact()
        assertThrows(IOException::class.java) { runBlocking { UpdateDocumentWriter(executor).save(apk, doc) { true } } }
        repeat(100) { assertThrows(IOException::class.java) { runBlocking { UpdateDocumentWriter(executor).save(apk, BlockedDocument("none")) { true } } } }
    }
}
