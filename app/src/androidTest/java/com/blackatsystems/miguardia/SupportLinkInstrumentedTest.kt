package com.blackatsystems.miguardia

import android.content.ActivityNotFoundException
import android.content.Intent
import com.blackatsystems.miguardia.support.openSupportLink
import org.junit.Assert.*
import org.junit.Test

/** Pure Android intent boundary: no browser, storage, permissions or installed-app queries. */
class SupportLinkInstrumentedTest {
    @Test fun exactFreshIntentHasNoPrivatePayloadOrBrowserBinding() {
        val intents = mutableListOf<Intent>()
        repeat(2) { assertTrue(openSupportLink { intents += it }) }
        assertNotSame(intents[0], intents[1])
        intents.forEach { intent ->
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("https://blackatsystems.com/miguardia/apoyar", intent.dataString)
            assertNull(intent.data!!.query)
            assertNull(intent.data!!.fragment)
            assertNull(intent.extras)
            assertNull(intent.clipData)
            assertNull(intent.`package`)
            assertNull(intent.component)
            assertNull(intent.selector)
            assertNull(intent.categories)
            assertNull(intent.type)
            assertEquals(0, intent.flags)
        }
    }

    @Test fun missingHandlerIsRecoverableAndNeverRetriesAutomatically() {
        var attempts = 0
        assertFalse(openSupportLink { attempts++; throw ActivityNotFoundException() })
        assertEquals(1, attempts)
        assertTrue(openSupportLink { attempts++ })
        assertEquals(2, attempts)
    }

    @Test fun securityRejectionIsRecoverableAndNeverRetriesAutomatically() {
        var attempts = 0
        assertFalse(openSupportLink { attempts++; throw SecurityException() })
        assertEquals(1, attempts)
        assertTrue(openSupportLink { attempts++ })
        assertEquals(2, attempts)
    }
}
