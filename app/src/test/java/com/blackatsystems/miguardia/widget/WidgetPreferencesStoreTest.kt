package com.blackatsystems.miguardia.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WidgetPreferencesStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: WidgetPreferencesStore

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = WidgetPreferencesStore(File(temporaryFolder.root, "widget.preferences_pb"), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `two IDs remain independent and an initial cancel writes nothing`() = runBlocking {
        assertTrue(store.all().isEmpty())
        store.save(
            11,
            WidgetInstancePreferences(
                mode = WidgetMode.NEXT_SHIFT,
                privacy = WidgetPrivacy.COMPLETE,
                includeWeather = true,
                configured = true,
            ),
        )
        store.save(
            22,
            WidgetInstancePreferences(
                mode = WidgetMode.NEXT_DAY_OFF,
                privacy = WidgetPrivacy.REDUCED,
                configured = true,
            ),
        )

        assertEquals(WidgetMode.NEXT_SHIFT, store.current(11).mode)
        assertTrue(store.current(11).includeWeather)
        assertEquals(WidgetMode.NEXT_DAY_OFF, store.current(22).mode)
        assertFalse(store.current(22).includeWeather)
        assertEquals(WidgetPreferencesStore.SafeDefault, store.current(33))
    }

    @Test
    fun `cancelled reconfiguration preserves the previous committed state`() = runBlocking {
        val original = WidgetInstancePreferences(
            mode = WidgetMode.AUTOMATIC,
            privacy = WidgetPrivacy.REDUCED,
            configured = true,
        )
        store.save(7, original)

        // A cancelled editor never calls save.
        assertEquals(original, store.current(7))
    }

    @Test
    fun `selective cleanup removes only deleted instances`() = runBlocking {
        store.save(1, configured(WidgetMode.NEXT_SHIFT))
        store.save(2, configured(WidgetMode.AUTOMATIC))
        store.delete(listOf(1, -1))

        assertEquals(WidgetPreferencesStore.SafeDefault, store.current(1))
        assertEquals(configured(WidgetMode.AUTOMATIC), store.current(2))
        assertEquals(setOf(2), store.all().keys)
    }

    @Test
    fun `restoration transfers exact pairs atomically and removes old IDs`() = runBlocking {
        val first = configured(WidgetMode.NEXT_SHIFT, WidgetPrivacy.COMPLETE)
        val second = configured(WidgetMode.NEXT_DAY_OFF, WidgetPrivacy.REDUCED)
        store.save(10, first)
        store.save(20, second)
        store.save(30, configured(WidgetMode.AUTOMATIC))

        store.remap(intArrayOf(10, 20), intArrayOf(101, 202))

        assertEquals(first, store.current(101))
        assertEquals(second, store.current(202))
        assertEquals(WidgetPreferencesStore.SafeDefault, store.current(10))
        assertEquals(WidgetPreferencesStore.SafeDefault, store.current(20))
        assertTrue(30 in store.all())
    }

    @Test
    fun `replaying the same restoration keeps the already transferred configuration`() = runBlocking {
        val original = configured(WidgetMode.NEXT_SHIFT, WidgetPrivacy.COMPLETE)
        store.save(10, original)

        store.remap(intArrayOf(10), intArrayOf(101))
        store.remap(intArrayOf(10), intArrayOf(101))

        assertEquals(original, store.current(101))
        assertEquals(setOf(101), store.all().keys)
    }

    @Test
    fun `restoration without an old preference creates only a hidden incomplete target`() = runBlocking {
        store.save(44, configured(WidgetMode.AUTOMATIC))

        store.remap(intArrayOf(999), intArrayOf(55))

        assertEquals(WidgetPreferencesStore.SafeDefault, store.current(55))
        assertEquals(configured(WidgetMode.AUTOMATIC), store.current(44))
        assertEquals(setOf(44, 55), store.all().keys)
    }

    @Test
    fun `corrupt mode or privacy never produces a configured instance`() {
        val corruptMode = mutablePreferencesOf(
            WidgetPreferencesStore.configuredKey(1) to true,
            WidgetPreferencesStore.modeKey(1) to "UNKNOWN",
            WidgetPreferencesStore.privacyKey(1) to WidgetPrivacy.COMPLETE.name,
        )
        val corruptPrivacy = mutablePreferencesOf(
            WidgetPreferencesStore.configuredKey(2) to true,
            WidgetPreferencesStore.modeKey(2) to WidgetMode.AUTOMATIC.name,
            WidgetPreferencesStore.privacyKey(2) to "PUBLIC",
        )

        assertEquals(WidgetPreferencesStore.SafeDefault, decodeWidgetPreferences(corruptMode, 1))
        assertEquals(WidgetPreferencesStore.SafeDefault, decodeWidgetPreferences(corruptPrivacy, 2))
    }

    @Test
    fun `corrupt datastore bytes are replaced by the safe empty state`() = runBlocking {
        scope.cancel()
        val corruptFile = File(temporaryFolder.root, "corrupt.preferences_pb").apply {
            writeBytes(byteArrayOf(0x7F, 0x00, 0x55, 0x33))
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = WidgetPreferencesStore(corruptFile, scope)

        assertTrue(store.all().isEmpty())
        assertEquals(WidgetPreferencesStore.SafeDefault, store.current(17))
    }

    private fun configured(
        mode: WidgetMode,
        privacy: WidgetPrivacy = WidgetPrivacy.HIDDEN,
    ) = WidgetInstancePreferences(mode, privacy, includeWeather = false, configured = true)
}
