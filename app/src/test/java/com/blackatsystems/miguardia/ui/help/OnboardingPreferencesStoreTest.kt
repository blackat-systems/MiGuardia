package com.blackatsystems.miguardia.ui.help

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnboardingPreferencesStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `empty file is pending and version one survives reopening`() = runBlocking {
        val target = file()
        var store = OnboardingPreferencesStore(target, scope)

        assertEquals(OnboardingStoreState.Ready(0), store.state.first())
        assertEquals(1, store.completeAtLeast(1))

        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = OnboardingPreferencesStore(target, scope)
        assertEquals(OnboardingStoreState.Ready(1), store.state.first())
    }

    @Test
    fun `future version is never downgraded`() = runBlocking {
        val store = OnboardingPreferencesStore(file(), scope)
        store.completeAtLeast(7)

        assertEquals(7, store.completeAtLeast(1))
        assertEquals(OnboardingStoreState.Ready(7), store.state.first())
    }

    @Test
    fun `inapplicable completion leaves the real store pending`() = runBlocking {
        val store = OnboardingPreferencesStore(file(), scope)

        assertEquals(0, store.completeAtLeast(1) { false })
        assertEquals(OnboardingStoreState.Ready(0), store.state.first())
    }

    @Test
    fun `unknown extra and negative values are errors instead of completion`() {
        assertEquals(
            OnboardingStoreState.Error,
            OnboardingPreferencesStore.decode(
                mutablePreferencesOf(OnboardingPreferencesStore.CompletedVersion to -1),
            ),
        )
        assertEquals(
            OnboardingStoreState.Error,
            OnboardingPreferencesStore.decode(
                mutablePreferencesOf(
                    OnboardingPreferencesStore.CompletedVersion to 1,
                    intPreferencesKey("unexpected") to 2,
                ),
            ),
        )
    }

    @Test
    fun `corrupt bytes recover as a pending guide without touching work data`() = runBlocking {
        val target = file().apply { writeBytes(byteArrayOf(1, 7, 21, 42)) }
        val store = OnboardingPreferencesStore(target, scope)

        assertEquals(OnboardingStoreState.Ready(0), store.state.first())
        assertEquals(1, store.completeAtLeast(1))
    }

    private fun file(): File = File(temporaryFolder.root, "isolated-onboarding.preferences_pb")
}
