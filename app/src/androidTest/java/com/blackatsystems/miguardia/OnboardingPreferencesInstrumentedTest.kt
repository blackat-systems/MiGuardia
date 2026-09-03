package com.blackatsystems.miguardia

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.ui.help.OnboardingPreferencesStore
import com.blackatsystems.miguardia.ui.help.OnboardingStoreState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingPreferencesInstrumentedTest {
    private lateinit var target: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        target = File(context.cacheDir, "onboarding-test-${System.nanoTime()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        target.delete()
    }

    @Test
    fun isolatedFilePersistsCurrentAndKeepsFutureVersion() = runBlocking {
        var store = OnboardingPreferencesStore(target, scope)
        assertEquals(OnboardingStoreState.Ready(0), store.state.first())
        assertEquals(1, store.completeAtLeast(1))

        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = OnboardingPreferencesStore(target, scope)
        assertEquals(OnboardingStoreState.Ready(1), store.state.first())
        store.completeAtLeast(4)
        assertEquals(4, store.completeAtLeast(1))
        assertEquals(OnboardingStoreState.Ready(4), store.state.first())
    }

    @Test
    fun corruptIsolatedFileIsNotTreatedAsCompleted() = runBlocking {
        target.writeBytes(byteArrayOf(21, 2, 0))
        var store = OnboardingPreferencesStore(target, scope)

        assertEquals(OnboardingStoreState.Ready(0), store.state.first())
        assertEquals(1, store.completeAtLeast(1))

        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = OnboardingPreferencesStore(target, scope)
        assertEquals(OnboardingStoreState.Ready(1), store.state.first())
    }
}
