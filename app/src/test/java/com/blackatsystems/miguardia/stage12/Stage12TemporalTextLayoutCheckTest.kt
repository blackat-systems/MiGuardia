package com.blackatsystems.miguardia.stage12

import org.junit.Assert.*
import org.junit.Test

class Stage12TemporalTextLayoutCheckTest {
    private data class Layout(val text: String, val overflow: Boolean)

    @Test fun checksStopInOriginalOrderAndMetricsCannotReplaceOverflow() {
        val exact = Layout("expected", false)
        val wrong = Layout("different", true)
        val overflow = Layout("expected", true)
        val events = mutableListOf<String>()
        var metricsFail = false
        fun check(layouts: List<Layout>) = requireTemporalTextLayout(
            layouts, exact.text,
            text = { events += "text"; it.text },
            hasOverflow = { events += "overflow"; it.overflow },
            overflowMetrics = {
                events += "metrics"
                check(!metricsFail)
                "TEMPORAL_LAYOUT_METRICS size_w=10"
            },
        ).also { events += "dependent" }

        for (badCount in listOf(emptyList(), listOf(exact, exact))) {
            val error = runCatching { check(badCount) }.exceptionOrNull()
            assertEquals("TEMPORAL_TEXT_LAYOUT_COUNT_MISMATCH", error?.message)
            assertTrue(events.isEmpty())
        }
        val mismatch = runCatching { check(listOf(wrong)) }.exceptionOrNull()
        assertEquals("TEMPORAL_TEXT_LAYOUT_VALUE_MISMATCH", mismatch?.message)
        assertEquals(listOf("text"), events)

        events.clear()
        val clipped = runCatching { check(listOf(overflow)) }.exceptionOrNull()
        assertEquals("TEMPORAL_TEXT_LAYOUT_OVERFLOW", clipped?.message)
        assertEquals("TEMPORAL_LAYOUT_METRICS size_w=10", clipped?.suppressed?.single()?.message)
        assertEquals(listOf("text", "overflow", "metrics"), events)

        events.clear()
        metricsFail = true
        val withoutMetrics = runCatching { check(listOf(overflow)) }.exceptionOrNull()
        assertEquals("TEMPORAL_TEXT_LAYOUT_OVERFLOW", withoutMetrics?.message)
        assertEquals(listOf("text", "overflow", "metrics"), events)

        events.clear()
        assertSame(exact, check(listOf(exact)))
        assertEquals(listOf("text", "overflow", "dependent"), events)
    }
}
