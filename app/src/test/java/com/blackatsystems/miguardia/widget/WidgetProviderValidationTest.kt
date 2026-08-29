package com.blackatsystems.miguardia.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetProviderValidationTest {
    @Test
    fun `only a positive ID owned by this provider is accepted`() {
        val installed = intArrayOf(11, 22)

        assertTrue(belongsToWidgetProvider(11, installed))
        assertFalse(belongsToWidgetProvider(33, installed))
        assertFalse(belongsToWidgetProvider(0, installed))
        assertFalse(belongsToWidgetProvider(-1, installed))
    }
}
