package com.blackatsystems.miguardia

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.widget.AndroidWidgetBoundaryScheduler
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetBoundarySchedulerInstrumentedTest {
    @Test
    fun oneShotBoundaryUsesAnImmutableExplicitBroadcastAndCanBeFullyCancelled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduler = AndroidWidgetBoundaryScheduler(context)
        try {
            scheduler.schedule(Instant.now().plusSeconds(3_600))
            val pendingIntent = scheduler.existingPendingIntent()

            assertNotNull(pendingIntent)
            assertEquals(context.packageName, requireNotNull(pendingIntent).creatorPackage)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) assertTrue(pendingIntent.isImmutable)
        } finally {
            scheduler.cancel()
        }
        assertNull(scheduler.existingPendingIntent())
    }
}
