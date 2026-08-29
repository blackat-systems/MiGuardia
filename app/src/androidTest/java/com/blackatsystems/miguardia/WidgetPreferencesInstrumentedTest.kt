package com.blackatsystems.miguardia

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.widget.WidgetInstancePreferences
import com.blackatsystems.miguardia.widget.WidgetPreferencesStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPreferencesInstrumentedTest {
    @Test
    fun independentInstancesPersistRemapAndDeleteWithoutTouchingTheirNeighbor() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.cacheDir, "widget-preferences-${UUID.randomUUID()}")
        check(directory.mkdirs())
        val file = File(directory, "widget.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = WidgetPreferencesStore(file, firstScope)
        val shift = WidgetInstancePreferences(
            WidgetMode.NEXT_SHIFT,
            WidgetPrivacy.COMPLETE,
            includeWeather = true,
            configured = true,
        )
        val dayOff = WidgetInstancePreferences(
            WidgetMode.NEXT_DAY_OFF,
            WidgetPrivacy.REDUCED,
            includeWeather = false,
            configured = true,
        )
        first.save(10, shift)
        first.save(20, dayOff)
        firstScope.cancel()
        delay(100)

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val restored = WidgetPreferencesStore(file, secondScope)
        assertEquals(shift, restored.current(10))
        assertEquals(dayOff, restored.current(20))
        restored.remap(intArrayOf(10), intArrayOf(101))
        assertEquals(shift, restored.current(101))
        assertEquals(WidgetPreferencesStore.SafeDefault, restored.current(10))
        restored.delete(listOf(20))
        assertFalse(20 in restored.all())
        assertTrue(101 in restored.all())
        secondScope.cancel()
        directory.deleteRecursively()
        Unit
    }
}
