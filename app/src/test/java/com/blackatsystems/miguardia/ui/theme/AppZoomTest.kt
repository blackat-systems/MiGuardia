package com.blackatsystems.miguardia.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppZoomTest {
    @Test
    fun supportedPercentagesRoundTrip() {
        AppZoom.entries.forEach { zoom ->
            assertEquals(zoom, AppZoom.fromPercent(zoom.percent))
        }
    }

    @Test
    fun unknownStoredValueFallsBackToStandard() {
        assertEquals(AppZoom.STANDARD, AppZoom.fromPercent(175))
    }
}
