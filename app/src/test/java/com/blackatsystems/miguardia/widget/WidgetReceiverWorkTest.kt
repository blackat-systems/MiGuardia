package com.blackatsystems.miguardia.widget

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetReceiverWorkTest {
    @Test
    fun `receiver work always finishes on success error and timeout`() = runBlocking {
        var successFinished = false
        var errorFinished = false
        var timeoutFinished = false

        assertTrue(runWidgetReceiverWork(finish = { successFinished = true }) {})
        assertFalse(
            runWidgetReceiverWork(finish = { errorFinished = true }) {
                error("fallo ficticio")
            },
        )
        assertFalse(
            runWidgetReceiverWork(timeoutMillis = 1, finish = { timeoutFinished = true }) {
                delay(100)
            },
        )

        assertTrue(successFinished)
        assertTrue(errorFinished)
        assertTrue(timeoutFinished)
    }
}
