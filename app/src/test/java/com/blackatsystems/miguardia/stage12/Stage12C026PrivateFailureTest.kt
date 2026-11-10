package com.blackatsystems.miguardia.stage12

import java.io.PrintWriter
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.notification.Failure

class Stage12C026PrivateFailureTest {
    private val id = Stage12C026PrivateFailure.SELECTOR
    private val context = Stage12C026PrivateFailure.Context(id, "a7b0329c-a845-4d6a-b1f7-7a12c6962c54", "a".repeat(64), "b".repeat(64))
    private val sentinel = "SYNTHETIC_PRIVATE_C026 C:/synthetic/person/value=93851"
    private fun original() = IllegalStateException(sentinel, IllegalArgumentException("cause $sentinel")).apply {
        addSuppressed(IllegalStateException("suppressed $sentinel"))
    }
    private fun take(c: Stage12C026PrivateFailure, current: Stage12C026PrivateFailure.Context = context,
        active: String? = id, method: String = id, number: Int = 1, code: Int = -2,
        stack: Boolean = true, exists: Boolean = false) = c.consume(current, active, method, number, code, stack, exists)
    private fun text(wire: String) = String(Base64.getDecoder().decode(wire.split('|')[7]), Charsets.UTF_8)

    @Test fun originalOnlyInPrivateWireAndPublicJUnitFailureHasNoCauseOrSuppressed() {
        val collector = Stage12C026PrivateFailure(context)
        val first = original()
        assertTrue(collector.capture(context, id, first))
        val wire = requireNotNull(take(collector))
        assertEquals(listOf("v1", id, context.run, context.qa, context.runner, "COMPLETE"), wire.split('|').take(6))
        assertTrue(text(wire).contains(sentinel))
        assertTrue(text(wire).contains("Caused by:"))
        assertTrue(text(wire).contains("Suppressed:"))
        assertEquals(text(wire).toByteArray(Charsets.UTF_8).size.toString(), wire.split('|')[6])
        val public = Stage12C026PrivateFailure.publicFailure()
        assertNull(public.cause)
        assertTrue(public.suppressed.isEmpty())
        val failure = Failure(Description.createTestDescription("C026", "method"), public)
        assertSame(public, failure.exception)
        assertFalse(failure.trace.contains(sentinel))
        assertFalse(failure.exception.toString().contains(sentinel))
        assertEquals("cause $sentinel", first.cause?.message)
        assertEquals("suppressed $sentinel", first.suppressed.single().message)
        assertNull(take(collector))
    }

    @Test fun onlyFirstCaptureAndOneConsumption() {
        val collector = Stage12C026PrivateFailure(context)
        assertTrue(collector.capture(context, id, IllegalStateException("first")))
        assertFalse(collector.capture(context, id, IllegalStateException("second")))
        assertNull(take(collector, code = 1))
        val wire = requireNotNull(take(collector))
        assertTrue(text(wire).contains("first")); assertFalse(text(wire).contains("second"))
        assertNull(take(collector)); assertFalse(collector.capture(context, id, original()))
    }

    @Test fun foreignContextCannotCaptureOrConsume() {
        for (foreign in listOf(context.copy(run = "a7b0329c-a845-4d6a-b1f7-7a12c6962c55"),
            context.copy(qa = "c".repeat(64)), context.copy(runner = "d".repeat(64)))) {
            val collector = Stage12C026PrivateFailure(context)
            assertFalse(collector.capture(foreign, id, original()))
            assertFalse(collector.capture(context, "other", original()))
            assertTrue(collector.capture(context, id, original()))
            assertNull(take(collector, current = foreign)); assertNull(take(collector))
        }
    }

    @Test fun terminalIdentityStackCodeCountAndDuplicateKeyAreClosed() {
        val invalid: List<(Stage12C026PrivateFailure) -> String?> = listOf(
            { take(it, active = null) }, { take(it, active = "other") }, { take(it, method = "other") },
            { take(it, number = 2) }, { take(it, code = 0) }, { take(it, code = -3) },
            { take(it, code = -4) }, { take(it, code = 7) }, { take(it, stack = false) }, { take(it, exists = true) })
        invalid.forEach { terminal ->
            val collector = Stage12C026PrivateFailure(context)
            assertTrue(collector.capture(context, id, original()))
            assertNull(terminal(collector)); assertNull(take(collector))
        }
    }

    @Test fun utf8BoundDoesNotSplitCodePointAndRecordsTruncation() {
        val collector = Stage12C026PrivateFailure(context)
        val first = IllegalStateException("á🙂".repeat(20000))
        assertTrue(collector.capture(context, id, first))
        val fields = requireNotNull(take(collector)).split('|')
        val bytes = Base64.getDecoder().decode(fields[7])
        assertEquals("TRUNCATED", fields[5])
        assertTrue(bytes.size <= Stage12C026PrivateFailure.MAX_BYTES)
        assertEquals(bytes.size.toString(), fields[6])
        assertArrayEquals(bytes, String(bytes, Charsets.UTF_8).toByteArray(Charsets.UTF_8))
    }

    @Test fun writerExceptionRetainsPartialPrivateTextAndDoesNotEscape() {
        val original = object : Throwable(sentinel) {
            override fun printStackTrace(s: PrintWriter) {
                s.write("partial $sentinel")
                throw IllegalStateException("writer failure $sentinel")
            }
        }
        val collector = Stage12C026PrivateFailure(context)
        assertTrue(collector.capture(context, id, original))
        val wire = requireNotNull(take(collector))
        assertEquals("WRITER_ERROR", wire.split('|')[5]); assertTrue(text(wire).contains(sentinel))
        assertFalse(text(wire).contains("writer failure"))
        assertNull(Stage12C026PrivateFailure.publicFailure().cause)
    }

    @Test fun splitSurrogateWritesRemainValidUtf8() {
        val collector = Stage12C026PrivateFailure(context)
        val original = object : Throwable() {
            override fun printStackTrace(s: PrintWriter) { s.write("\uD83D"); s.write("\uDE42"); s.write("\uD83D") }
        }
        collector.capture(context, id, original)
        assertEquals("🙂�", text(requireNotNull(take(collector))))
    }

    @Test fun sameTwoStatusesPreserveRealFailedCounts() {
        val counts = Stage12NormalQaCounts(setOf(id))
        val collector = Stage12C026PrivateFailure(context)
        counts.observe(id, 1, 1, false)
        collector.capture(context, id, original())
        counts.observe(id, 1, -2, true)
        assertNotNull(take(collector))
        val snapshot = counts.finish(true)
        assertEquals(1, snapshot.expected); assertEquals(1, snapshot.started)
        assertEquals(0, snapshot.passed); assertEquals(1, snapshot.failed); assertEquals(0, snapshot.skipped)
        assertTrue(snapshot.malformed); assertFalse(snapshot.complete)
    }
}
