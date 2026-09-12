package com.blackatsystems.miguardia.ui.help

import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.MissingWorkSetupRequirement
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpCoordinatorTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach(CoroutineScope::cancel)
    }

    @Test
    fun `only pending V2Ready with no setup surface starts automatically`() {
        val (coordinator, _) = coordinator(version = 0)

        coordinator.synchronizeWorkSetup(WorkSetupState.FreshInstall, WorkSetupSurface.NONE)
        assertNull(coordinator.uiState.value.session)
        coordinator.synchronizeWorkSetup(WorkSetupState.LoadError, WorkSetupSurface.NONE)
        assertNull(coordinator.uiState.value.session)
        coordinator.synchronizeWorkSetup(needsFirstSet(), WorkSetupSurface.NONE)
        assertNull(coordinator.uiState.value.session)
        coordinator.synchronizeWorkSetup(ready(), WorkSetupSurface.COMPLETION)
        assertNull(coordinator.uiState.value.session)

        coordinator.synchronizeWorkSetup(ready(), WorkSetupSurface.NONE)
        assertEquals(HelpSessionMode.AUTOMATIC, coordinator.uiState.value.session?.mode)
    }

    @Test
    fun `current and future versions do not start automatically`() {
        listOf(1, 9).forEach { version ->
            val (coordinator, _) = coordinator(version)
            coordinator.synchronizeWorkSetup(ready(), WorkSetupSurface.NONE)
            assertNull(coordinator.uiState.value.session)
            assertTrue(coordinator.uiState.value.canConsumePendingDestination)
        }
    }

    @Test
    fun `omit and double confirmation write and navigate once`() = runBlocking {
        val (coordinator, store) = coordinator(version = 0)
        coordinator.synchronizeWorkSetup(ready(), WorkSetupSurface.NONE)
        coordinator.requestExit()

        coordinator.confirmExit()
        coordinator.confirmExit()
        yield()

        assertEquals(1, store.writeCount)
        assertEquals(HelpNavigationTarget.CALENDAR, coordinator.uiState.value.navigationEvent?.target)
        assertNull(coordinator.uiState.value.session)
    }

    @Test
    fun `finishing full tour writes once`() = runBlocking {
        val (coordinator, store) = coordinator(version = 0)
        coordinator.synchronizeWorkSetup(ready(), WorkSetupSurface.NONE)
        repeat(3 + HelpTourStep.entries.lastIndex) { coordinator.next() }
        assertEquals(HelpTourStep.HELP, coordinator.uiState.value.session?.tourStep)

        coordinator.finish()
        coordinator.finish()
        yield()

        assertEquals(1, store.writeCount)
        assertEquals(HelpNavigationTarget.CALENDAR, coordinator.uiState.value.navigationEvent?.target)
    }

    @Test
    fun `replay neither writes nor offers skip and returns to Help`() {
        val (coordinator, store) = coordinator(version = 1)
        coordinator.synchronizeWorkSetup(ready(), WorkSetupSurface.NONE)
        coordinator.startReplay()

        assertEquals(HelpSessionMode.REPLAY, coordinator.uiState.value.session?.mode)
        coordinator.requestExit()

        assertEquals(0, store.writeCount)
        assertEquals(HelpNavigationTarget.HELP, coordinator.uiState.value.navigationEvent?.target)
    }

    @Test
    fun `write error preserves step and retry succeeds`() = runBlocking {
        val store = FakeStore(0).apply { writeFailure = IllegalStateException("disk") }
        val coordinator = coordinator(store)
        coordinator.synchronizeWorkSetup(ready(), WorkSetupSurface.NONE)
        coordinator.next()
        val stepBefore = coordinator.uiState.value.session?.stepIndex
        coordinator.requestExit()
        coordinator.confirmExit()
        yield()

        assertEquals(stepBefore, coordinator.uiState.value.session?.stepIndex)
        assertNotNull(coordinator.uiState.value.errorMessage)

        store.writeFailure = null
        coordinator.retryCompletion()
        yield()
        assertNull(coordinator.uiState.value.session)
        assertEquals(2, store.writeCount)
    }

    @Test
    fun `read error is safe and retryable`() = runBlocking {
        val store = FakeStore(error = true)
        val coordinator = coordinator(store)
        coordinator.synchronizeWorkSetup(ready(), WorkSetupSurface.NONE)
        assertEquals(HelpReadState.Error, coordinator.uiState.value.readState)
        assertNull(coordinator.uiState.value.session)

        store.emitVersion(0)
        coordinator.retryRead()
        yield()
        assertEquals(HelpSessionMode.AUTOMATIC, coordinator.uiState.value.session?.mode)
    }

    @Test
    fun `cancellation is not translated to an error`() = runBlocking {
        val store = FakeStore(0).apply { writeFailure = CancellationException("closed") }
        val coordinator = coordinator(store)
        coordinator.synchronizeWorkSetup(ready(), WorkSetupSurface.NONE)
        coordinator.requestExit()
        coordinator.confirmExit()
        yield()

        assertNull(coordinator.uiState.value.errorMessage)
        assertFalse(coordinator.uiState.value.isSaving)
    }

    @Test
    fun `leaving V2Ready closes without marking and ignores late result`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val store = FakeStore(0).apply { delayedWrite = gate }
        val coordinator = coordinator(store)
        coordinator.synchronizeWorkSetup(ready(), WorkSetupSurface.NONE)
        coordinator.requestExit()
        coordinator.confirmExit()
        coordinator.synchronizeWorkSetup(needsFirstSet(), WorkSetupSurface.NONE)
        gate.complete(Unit)
        yield()

        assertNull(coordinator.uiState.value.session)
        assertNull(coordinator.uiState.value.navigationEvent)
        assertEquals(0, store.currentVersion)
    }

    @Test
    fun `opening work setup closes without marking and ignores late result`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val store = FakeStore(0).apply { delayedWrite = gate }
        val coordinator = coordinator(store)
        coordinator.synchronizeWorkSetup(ready(), WorkSetupSurface.NONE)
        coordinator.requestExit()
        coordinator.confirmExit()
        coordinator.synchronizeWorkSetup(ready(), WorkSetupSurface.COMPLETION)
        gate.complete(Unit)
        yield()

        assertNull(coordinator.uiState.value.session)
        assertNull(coordinator.uiState.value.navigationEvent)
        assertEquals(0, store.currentVersion)
    }

    @Test
    fun `saved replay restores mode stage and step without a write`() {
        val store = FakeStore(1)
        val restored = RestoredHelpSession(HelpSessionMode.REPLAY, HelpSessionStage.TOUR, 5)
        val scope = scope()
        val coordinator = HelpCoordinator(store, scope, restoredSession = restored)

        coordinator.synchronizeWorkSetup(ready(), WorkSetupSurface.NONE)

        assertEquals(HelpSessionMode.REPLAY, coordinator.uiState.value.session?.mode)
        assertEquals(HelpTourStep.LOAD_AND_REPEAT, coordinator.uiState.value.session?.tourStep)
        assertEquals(0, store.writeCount)
    }

    @Test
    fun `pending destination gate opens only after current version is saved`() = runBlocking {
        val (coordinator, _) = coordinator(version = 0)
        coordinator.synchronizeWorkSetup(ready(), WorkSetupSurface.NONE)
        assertFalse(coordinator.uiState.value.canConsumePendingDestination)
        coordinator.requestExit()
        coordinator.confirmExit()
        yield()
        assertTrue(coordinator.uiState.value.canConsumePendingDestination)
    }

    private fun coordinator(version: Int): Pair<HelpCoordinator, FakeStore> {
        val store = FakeStore(version)
        return coordinator(store) to store
    }

    private fun coordinator(store: FakeStore): HelpCoordinator = HelpCoordinator(store, scope())

    private fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        .also(scopes::add)

    private class FakeStore(
        version: Int = 0,
        error: Boolean = false,
    ) : OnboardingVersionStore {
        private val mutable = MutableStateFlow<OnboardingStoreState>(
            if (error) OnboardingStoreState.Error else OnboardingStoreState.Ready(version),
        )
        override val state: Flow<OnboardingStoreState> = mutable
        var writeCount = 0
        var writeFailure: Throwable? = null
        var delayedWrite: CompletableDeferred<Unit>? = null
        val currentVersion: Int
            get() = (mutable.value as? OnboardingStoreState.Ready)?.completedVersion ?: 0

        override suspend fun completeAtLeast(version: Int, stillApplicable: () -> Boolean): Int {
            writeCount++
            delayedWrite?.let { deferred ->
                withContext(NonCancellable) { deferred.await() }
            }
            writeFailure?.let { throw it }
            val current = (mutable.value as? OnboardingStoreState.Ready)?.completedVersion ?: 0
            if (!stillApplicable()) return current
            val completed = maxOf(current, version)
            mutable.value = OnboardingStoreState.Ready(completed)
            return completed
        }

        fun emitVersion(version: Int) {
            mutable.value = OnboardingStoreState.Ready(version)
        }
    }

    private fun ready(): WorkSetupState.V2Ready = WorkSetupState.V2Ready(TIMELINE_ID, REVISION)

    private fun needsFirstSet(): WorkSetupState.V2NeedsFirstSet = WorkSetupState.V2NeedsFirstSet(
        TIMELINE_ID,
        REVISION,
        setOf(MissingWorkSetupRequirement.ACTIVE_WORK_PLACE),
    )

    private companion object {
        val TIMELINE_ID: UUID = UUID(21L, 1L)
        val REVISION = EffectiveRevision(
            id = UUID(21L, 2L),
            effectiveFrom = LocalDate.of(2026, 9, 2),
            value = WorkConfiguration(WorkSector.PRIVATE_SECURITY, HoursReference.PendingSetup, null),
        )
    }
}
