package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.stage12.Stage12ActivityAdmission.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class Stage12AospFixtureFinalizationTest {
    private fun ready() = Stage12ActivityAdmission(false).apply {
        phase(Phase.READINESS)
        observe(Snapshot(true, Foreground.OWN, true, false, Life.RESUMED, true, true, Startup.READY, true, true), 0)
        phase(Phase.BODY)
    }
    private fun exercise(name: String, body: Throwable? = null, closeError: Throwable? = null,
        readError: Throwable? = null, publicationError: Boolean = false): Pair<Throwable?, Stage12ActivityAdmission> {
        val admission = ready();body?.let(admission::failed)
        val calls = mutableListOf<String>()
        val dir = Files.createTempDirectory("stage12-fixture-finalization").toFile()
        val output = File(dir, "receipt.json")
        if (publicationError) output.writeText("PRESERVE_EXISTING")
        val primary = Stage12AospFixturePlan.finishPreparation(admission, body,
            close = { calls += "close";closeError?.let { throw it } },
            readback = { calls += "readback";readError?.let { throw it } },
            publish = { failure -> calls += "publish";Stage12NativeReceiptFiles.publish(output, "{}", failure) })
        assertEquals(if (body != null || closeError != null) listOf("close", "publish") else listOf("close", "readback", "publish"), calls)
        if (publicationError) assertEquals("PRESERVE_EXISTING", output.readText())
        if (body != null) assertSame(body, primary)
        else if (closeError != null) assertSame(closeError, primary)
        else if (readError != null) assertSame(readError, primary)
        // The runner's generic later classification cannot overwrite the recorded first failure.
        if (primary != null) admission.failed(Failure.OTHER)
        val selector = Stage12RuntimeTarget.WORK_FIXTURE
        val counts = Stage12NormalQaCounts(setOf(selector));counts.observe(selector, 1, 1, false)
        val code = if (primary == null) 0 else -2
        counts.observe(selector, 1, code, primary != null)
        val count = counts.finish(primary == null);assertEquals(primary == null, count.complete)
        val unsafe = mapOf("numtests" to 1, "stack" to primary?.stackTraceToString().orEmpty(),
            "stream" to "PRIVATE_STREAM", "extra" to "PRIVATE_EXTRA")
        val terminal = Stage12AdmissionProjection.terminal(admission)
        val raw = buildString {
            for (status in listOf(1, code)) {
                val projected = Stage12AdmissionProjection.status(selector, if (status == 1) mapOf("numtests" to 1) else unsafe, status, admission)
                projected.forEach { (k,v) -> append("INSTRUMENTATION_STATUS: $k=$v\n") }
                append("INSTRUMENTATION_STATUS_CODE: $status\n")
            }
            terminal.forEach { (k,v) -> append("INSTRUMENTATION_RESULT: $k=$v\n") }
            append("INSTRUMENTATION_RESULT: stage12NormalStatus=${if (count.complete) "COMPLETE" else "INCOMPLETE"}\n")
            append("INSTRUMENTATION_RESULT: stage12RunId=65d22b73-2b96-4a6a-866d-e6f3eceecdd1\n")
            append("INSTRUMENTATION_RESULT: stage12QaSha256=${"1".repeat(64)}\n")
            append("INSTRUMENTATION_RESULT: stage12RunnerSha256=${"2".repeat(64)}\n")
            append("INSTRUMENTATION_RESULT: stage12CalendarClock=REAL\n")
            append("INSTRUMENTATION_RESULT: stage12MethodCounts={\"expected\":${count.expected},\"started\":${count.started},\"passed\":${count.passed},\"failed\":${count.failed},\"skipped\":${count.skipped},\"malformed\":${count.malformed}}\n")
            append("INSTRUMENTATION_CODE: -1\n")
        }
        assertFalse(raw.contains("PRIVATE_"));assertFalse(raw.contains(dir.path))
        val goldenDir = File("build/stage12-finalization-wire");check(goldenDir.isDirectory || goldenDir.mkdirs())
        File(goldenDir, "$name.raw").writeText(raw)
        output.delete();dir.delete()
        return primary to admission
    }
    @Test fun closeFailurePreservesItsPhaseAndSkipsUnreachedReadback() {
        val (error,a) = exercise("close", closeError = IllegalStateException("PRIVATE_CLOSE", IOException("PRIVATE_CAUSE")))
        assertNotNull(error);assertTrue(a.wire().contains("\"failure_phase\":\"CLOSE\""))
        assertTrue(a.wire().contains("\"close_failed\":true"));assertTrue(a.wire().contains("\"publication_failed\":false"))
    }
    @Test fun readbackFailureIsPostconditionNotCloseOrPublication() {
        val (_,a) = exercise("readback", readError = IOException("PRIVATE_READBACK"))
        assertTrue(a.wire().contains("\"failure_phase\":\"POSTCONDITION\""));assertTrue(a.wire().contains("\"failure_kind\":\"IO\""))
        assertTrue(a.wire().contains("\"close_failed\":false"));assertTrue(a.wire().contains("\"publication_failed\":false"))
    }
    @Test fun actualExclusivePublicationFailureIsAttributedToPublish() {
        val (_,a) = exercise("publication", publicationError = true)
        assertTrue(a.wire().contains("\"failure_phase\":\"PUBLISH\""));assertTrue(a.wire().contains("\"publication_failed\":true"))
    }
    @Test fun bodyCloseAndPublicationKeepFirstThrowableAndAllUniqueSecondaryFailures() {
        val body = IllegalArgumentException("PRIVATE_BODY");val close = IOException("PRIVATE_CLOSE")
        val (error,a) = exercise("combined", body = body, closeError = close, publicationError = true)
        assertSame(body,error);assertEquals(2,body.suppressed.size);assertSame(close,body.suppressed[0])
        assertTrue(a.wire().contains("\"failure_phase\":\"BODY\""));assertTrue(a.wire().contains("\"failure_kind\":\"OTHER\""))
        assertTrue(a.wire().contains("\"close_failed\":true"));assertTrue(a.wire().contains("\"publication_failed\":true"))
    }
    @Test fun priorBodyFailureDoesNotInventSecondaryFailureOrReadback() {
        val body = IllegalStateException("PRIVATE_BODY")
        val (error,a) = exercise("body", body = body)
        assertSame(body,error);assertTrue(body.suppressed.isEmpty())
        assertTrue(a.wire().contains("\"failure_phase\":\"BODY\""));assertTrue(a.wire().contains("\"close_failed\":false"))
    }
    @Test fun successfulFinalizationHasNoInventedFailure() {
        val (error,a) = exercise("success")
        assertNull(error);assertTrue(a.wire().contains("\"phase\":\"DONE\""));assertTrue(a.wire().contains("\"failure_phase\":\"NONE\""))
    }
}
