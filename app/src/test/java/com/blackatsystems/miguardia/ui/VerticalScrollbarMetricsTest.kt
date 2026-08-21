package com.blackatsystems.miguardia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class VerticalScrollbarMetricsTest {
    @Test
    fun noOverflowProducesNoScrollbarMetrics() {
        assertNull(
            calculateVerticalScrollbarMetrics(
                viewportHeightPx = 600f,
                maxScrollPx = 0,
                scrollValuePx = 0,
                minimumThumbHeightPx = 40f,
            ),
        )
    }

    @Test
    fun thumbRepresentsViewportAndTracksStartMiddleAndEnd() {
        val start = metrics(scrollValuePx = 0)
        val middle = metrics(scrollValuePx = 300)
        val end = metrics(scrollValuePx = 600)

        assertEquals(300f, start.thumbHeightPx, 0.001f)
        assertEquals(0f, start.thumbOffsetPx, 0.001f)
        assertEquals(150f, middle.thumbOffsetPx, 0.001f)
        assertEquals(300f, end.thumbOffsetPx, 0.001f)
    }

    @Test
    fun minimumThumbRemainsVisibleAndScrollValuesStayInsideTrack() {
        val beforeStart = minimumMetrics(scrollValuePx = -200)
        val middle = minimumMetrics(scrollValuePx = 1_800)
        val afterEnd = minimumMetrics(scrollValuePx = 4_000)

        assertEquals(60f, middle.thumbHeightPx, 0.001f)
        assertEquals(0f, beforeStart.thumbOffsetPx, 0.001f)
        assertEquals(170f, middle.thumbOffsetPx, 0.001f)
        assertEquals(340f, afterEnd.thumbOffsetPx, 0.001f)
    }

    private fun metrics(scrollValuePx: Int): VerticalScrollbarMetrics {
        val result = calculateVerticalScrollbarMetrics(
            viewportHeightPx = 600f,
            maxScrollPx = 600,
            scrollValuePx = scrollValuePx,
            minimumThumbHeightPx = 40f,
        )
        assertNotNull(result)
        return requireNotNull(result)
    }

    private fun minimumMetrics(scrollValuePx: Int): VerticalScrollbarMetrics {
        val result = calculateVerticalScrollbarMetrics(
            viewportHeightPx = 400f,
            maxScrollPx = 3_600,
            scrollValuePx = scrollValuePx,
            minimumThumbHeightPx = 60f,
        )
        assertNotNull(result)
        return requireNotNull(result)
    }
}
