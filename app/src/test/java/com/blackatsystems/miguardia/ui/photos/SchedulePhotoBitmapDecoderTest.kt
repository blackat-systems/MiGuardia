package com.blackatsystems.miguardia.ui.photos

import org.junit.Assert.assertEquals
import org.junit.Test

class SchedulePhotoBitmapDecoderTest {
    @Test
    fun sampleSizeUsesTheLargestVisualSideAndPowersOfTwo() {
        assertEquals(1, SchedulePhotoBitmapDecoder.sampleSizeFor(1_200, 800, 2_048))
        assertEquals(2, SchedulePhotoBitmapDecoder.sampleSizeFor(3_000, 4_000, 2_048))
        assertEquals(4, SchedulePhotoBitmapDecoder.sampleSizeFor(8_000, 6_000, 2_048))
        assertEquals(8, SchedulePhotoBitmapDecoder.sampleSizeFor(256, 1_024, 200))
    }
}
