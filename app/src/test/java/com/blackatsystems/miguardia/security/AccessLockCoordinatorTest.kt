package com.blackatsystems.miguardia.security

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessLockCoordinatorTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @org.junit.After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun `activation writes enabled and draft timeout only after success`() = runBlocking {
        val (coordinator, store) = coordinator()
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), deviceLocked = false)
        val token = coordinator.beginAuthentication(
            AccessLockOperation.Activate(AccessLockTimeout.FIVE_MINUTES),
        )

        assertNotNull(token)
        assertFalse(coordinator.state.value.configuration!!.enabled)
        assertEquals(AccessLockConfiguration(), ready(store))

        coordinator.completeAuthentication(
            requireNotNull(token),
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.STRONG_BIOMETRIC),
        )

        assertEquals(
            AccessLockConfiguration(true, AccessLockTimeout.FIVE_MINUTES),
            coordinator.state.value.configuration,
        )
        assertTrue(coordinator.state.value.allowsSensitiveContent)
        assertEquals(coordinator.state.value.configuration, ready(store))
    }

    @Test
    fun `cancel and every authentication error keep the exact previous configuration`() = runBlocking {
        val failures = listOf(
            DeviceAuthenticationResult.UserCancelled,
            DeviceAuthenticationResult.SystemCancelled,
            DeviceAuthenticationResult.Lockout,
            DeviceAuthenticationResult.HardwareUnavailable,
            DeviceAuthenticationResult.BiometricNotEnrolled,
            DeviceAuthenticationResult.NoSecureCredential,
            DeviceAuthenticationResult.Unsupported,
            DeviceAuthenticationResult.RecoverableError,
            DeviceAuthenticationResult.FinalError,
        )
        failures.forEach { failure ->
            val initial = AccessLockConfiguration(true, AccessLockTimeout.ONE_MINUTE)
            val (coordinator, store) = coordinator(initial)
            coordinator.initializeAfterRecovery()
            coordinator.activityStarted(Any(), deviceLocked = false)
            val unlock = requireNotNull(coordinator.beginAuthentication(AccessLockOperation.Unlock))
            coordinator.completeAuthentication(
                unlock,
                DeviceAuthenticationResult.Success(DeviceAuthenticationSource.DEVICE_CREDENTIAL),
            )
            val token = requireNotNull(
                coordinator.beginAuthentication(
                    AccessLockOperation.ChangeTimeout(AccessLockTimeout.FIFTEEN_MINUTES),
                ),
            )

            coordinator.completeAuthentication(token, failure)

            assertEquals(initial, coordinator.state.value.configuration)
            assertEquals(initial, ready(store))
            assertFalse(coordinator.state.value.authenticationInProgress)
        }
    }

    @Test
    fun `double touch and late callback are ignored`() = runBlocking {
        val (coordinator, _) = coordinator()
        coordinator.initializeAfterRecovery()
        val first = requireNotNull(
            coordinator.beginAuthentication(AccessLockOperation.Activate(AccessLockTimeout.IMMEDIATE)),
        )

        assertNull(coordinator.beginAuthentication(AccessLockOperation.Activate(AccessLockTimeout.ONE_MINUTE)))
        coordinator.completeAuthentication(first, DeviceAuthenticationResult.UserCancelled)
        val second = requireNotNull(
            coordinator.beginAuthentication(AccessLockOperation.Activate(AccessLockTimeout.ONE_MINUTE)),
        )
        coordinator.completeAuthentication(
            first,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.STRONG_BIOMETRIC),
        )

        assertFalse(coordinator.state.value.configuration!!.enabled)
        assertTrue(coordinator.state.value.authenticationInProgress)

        coordinator.completeAuthentication(
            second,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.DEVICE_CREDENTIAL),
        )
        assertEquals(AccessLockTimeout.ONE_MINUTE, coordinator.state.value.configuration!!.timeout)
    }

    @Test
    fun `finishing the host abandons its dialog and ignores a later success`() = runBlocking {
        val (coordinator, _) = coordinator()
        coordinator.initializeAfterRecovery()
        val token = requireNotNull(
            coordinator.beginAuthentication(AccessLockOperation.Activate(AccessLockTimeout.IMMEDIATE)),
        )

        coordinator.abandonAuthentication(token)
        coordinator.completeAuthentication(
            token,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.STRONG_BIOMETRIC),
        )

        assertFalse(coordinator.state.value.authenticationInProgress)
        assertFalse(coordinator.state.value.configuration!!.enabled)
        assertEquals(AccessLockMessage.SYSTEM_CANCELLED, coordinator.state.value.message)
    }

    @Test
    fun `configuration recreation and overlapping internal activity preserve immediate session`() = runBlocking {
        val initial = AccessLockConfiguration(true, AccessLockTimeout.IMMEDIATE)
        val (coordinator, _) = coordinator(initial)
        coordinator.initializeAfterRecovery()
        val firstActivity = Any()
        coordinator.activityStarted(firstActivity, deviceLocked = false)
        unlock(coordinator)

        coordinator.activityPausedForPrivacy()
        coordinator.activityStopped(firstActivity, changingConfigurations = true)
        val recreatedActivity = Any()
        coordinator.activityStarted(recreatedActivity, deviceLocked = false)
        coordinator.activityResumed(deviceLocked = false)

        assertTrue(coordinator.state.value.allowsSensitiveContent)

        val widgetActivity = Any()
        coordinator.activityStarted(widgetActivity, deviceLocked = false)
        coordinator.activityStopped(recreatedActivity, changingConfigurations = false)
        coordinator.activityResumed(deviceLocked = false)

        assertTrue(coordinator.state.value.allowsSensitiveContent)
    }

    @Test
    fun `device lock during authentication cannot be erased by cancellation on return`() = runBlocking {
        val initial = AccessLockConfiguration(true, AccessLockTimeout.FIFTEEN_MINUTES)
        val (coordinator, _) = coordinator(initial)
        coordinator.initializeAfterRecovery()
        val activity = Any()
        coordinator.activityStarted(activity, deviceLocked = false)
        unlock(coordinator)
        val token = requireNotNull(
            coordinator.beginAuthentication(
                AccessLockOperation.ChangeTimeout(AccessLockTimeout.ONE_MINUTE),
                "main-host",
            ),
        )

        coordinator.activityPausedForPrivacy()
        coordinator.activityStopped(activity, changingConfigurations = false)
        coordinator.deviceLocked()
        coordinator.activityStarted(Any(), deviceLocked = false)
        coordinator.completeAuthentication(token, DeviceAuthenticationResult.UserCancelled)
        coordinator.activityResumed(deviceLocked = false)

        assertTrue(coordinator.state.value.locked)
        assertFalse(coordinator.state.value.allowsSensitiveContent)
        assertEquals(initial, coordinator.state.value.configuration)
    }

    @Test
    fun `device lock while an authorized write waits aborts data and keeps the session closed`() = runBlocking {
        val initial = AccessLockConfiguration(true, AccessLockTimeout.ONE_MINUTE)
        val dataStore = ControllablePreferencesDataStore(
            mutablePreferencesOf(
                AccessLockPreferencesStore.ContractVersion to 1,
                AccessLockPreferencesStore.Enabled to true,
                AccessLockPreferencesStore.Timeout to initial.timeout.name,
            ),
        )
        val coordinator = coordinator(dataStore)
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), deviceLocked = false)
        unlock(coordinator)
        val writeStarted = CompletableDeferred<Unit>()
        val allowWrite = CompletableDeferred<Unit>()
        dataStore.writeStarted = writeStarted
        dataStore.allowWrite = allowWrite
        val token = requireNotNull(
            coordinator.beginAuthentication(
                AccessLockOperation.ChangeTimeout(AccessLockTimeout.FIVE_MINUTES),
                "main-host",
            ),
        )

        coordinator.completeAuthentication(
            token,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.DEVICE_CREDENTIAL),
        )
        writeStarted.await()
        coordinator.deviceLocked()
        allowWrite.complete(Unit)
        while (coordinator.state.value.persistenceInProgress) kotlinx.coroutines.yield()

        assertEquals(
            initial,
            coordinator.state.value.configuration,
        )
        assertEquals(initial, ready(AccessLockPreferencesStore(dataStore)))
        assertTrue(coordinator.state.value.locked)
        assertFalse(coordinator.state.value.allowsSensitiveContent)
    }

    @Test
    fun `authentication token belongs only to its hosting activity`() = runBlocking {
        val initial = AccessLockConfiguration(true, AccessLockTimeout.IMMEDIATE)
        val (coordinator, _) = coordinator(initial)
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), deviceLocked = false)
        val mainToken = requireNotNull(
            coordinator.beginAuthentication(AccessLockOperation.Unlock, "main-host"),
        )

        assertEquals(mainToken, coordinator.activeAuthenticationToken("main-host"))
        assertNull(coordinator.activeAuthenticationToken("widget-host"))
        coordinator.abandonAuthentication(mainToken, "widget-host")
        assertEquals(mainToken, coordinator.activeAuthenticationToken("main-host"))

        coordinator.completeAuthentication(
            mainToken,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.STRONG_BIOMETRIC),
        )
        coordinator.lockNow()
        assertNotNull(
            coordinator.beginAuthentication(AccessLockOperation.Unlock, "widget-host"),
        )
    }

    @Test
    fun `a second coordinator over the same store is a locked new process`() = runBlocking {
        val dataStore = ControllablePreferencesDataStore(
            mutablePreferencesOf(
                AccessLockPreferencesStore.ContractVersion to 1,
                AccessLockPreferencesStore.Enabled to true,
                AccessLockPreferencesStore.Timeout to AccessLockTimeout.FIFTEEN_MINUTES.name,
            ),
        )
        val first = coordinator(dataStore)
        first.initializeAfterRecovery()
        first.activityStarted(Any(), false)
        unlock(first)
        assertTrue(first.state.value.allowsSensitiveContent)

        val restarted = coordinator(dataStore)
        restarted.initializeAfterRecovery()
        restarted.activityStarted(Any(), false)

        assertTrue(restarted.state.value.locked)
        assertFalse(restarted.state.value.allowsSensitiveContent)
    }

    @Test
    fun `store error stays closed and authenticated repair resets only this contract`() = runBlocking {
        val dataStore = ControllablePreferencesDataStore(
            mutablePreferencesOf(
                AccessLockPreferencesStore.ContractVersion to 77,
                AccessLockPreferencesStore.Enabled to false,
                AccessLockPreferencesStore.Timeout to "UNKNOWN",
                androidx.datastore.preferences.core.stringPreferencesKey("forbidden") to "value",
            ),
        )
        val coordinator = coordinator(dataStore)
        coordinator.initializeAfterRecovery()

        assertEquals(AccessLockPhase.STORE_ERROR, coordinator.state.value.phase)
        assertFalse(coordinator.state.value.allowsSensitiveContent)
        val token = requireNotNull(coordinator.beginAuthentication(AccessLockOperation.RepairStore))
        coordinator.completeAuthentication(
            token,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.DEVICE_CREDENTIAL),
        )

        assertEquals(AccessLockPhase.READY, coordinator.state.value.phase)
        assertEquals(AccessLockConfiguration(), coordinator.state.value.configuration)
        assertEquals(
            setOf("contract_version", "enabled", "timeout"),
            dataStore.current().asMap().keys.map { it.name }.toSet(),
        )
    }

    @Test
    fun `lock now needs no authentication and keeps persisted configuration`() = runBlocking {
        val initial = AccessLockConfiguration(true, AccessLockTimeout.FIVE_MINUTES)
        val (coordinator, store) = coordinator(initial)
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), false)
        unlock(coordinator)

        coordinator.lockNow()

        assertTrue(coordinator.state.value.locked)
        assertNull(coordinator.activeAuthenticationToken)
        assertEquals(initial, ready(store))
    }

    @Test
    fun `removing the secure phone credential closes an enabled session`() = runBlocking {
        val initial = AccessLockConfiguration(true, AccessLockTimeout.FIFTEEN_MINUTES)
        val (coordinator, _) = coordinator(initial)
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), false)
        unlock(coordinator)

        coordinator.secureCredentialUnavailable()

        assertTrue(coordinator.state.value.locked)
        assertFalse(coordinator.state.value.allowsSensitiveContent)
        assertEquals(AccessLockMessage.NO_SECURE_CREDENTIAL, coordinator.state.value.message)
    }

    @Test
    fun `late success cannot reopen after the secure credential disappears`() = runBlocking {
        val initial = AccessLockConfiguration(true, AccessLockTimeout.FIFTEEN_MINUTES)
        val (coordinator, store) = coordinator(initial)
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), false)
        val token = requireNotNull(
            coordinator.beginAuthentication(AccessLockOperation.Unlock, "main-host"),
        )

        coordinator.secureCredentialUnavailable()
        coordinator.completeAuthentication(
            token,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.STRONG_BIOMETRIC),
        )

        assertTrue(coordinator.state.value.locked)
        assertFalse(coordinator.state.value.allowsSensitiveContent)
        assertNull(coordinator.activeAuthenticationToken)
        assertEquals(initial, ready(store))
    }

    @Test
    fun `lock now invalidates an authenticated settings dialog before it can persist`() = runBlocking {
        val initial = AccessLockConfiguration(true, AccessLockTimeout.ONE_MINUTE)
        val (coordinator, store) = coordinator(initial)
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), false)
        unlock(coordinator)
        val token = requireNotNull(
            coordinator.beginAuthentication(
                AccessLockOperation.ChangeTimeout(AccessLockTimeout.FIFTEEN_MINUTES),
                "main-host",
            ),
        )

        coordinator.lockNow()
        coordinator.completeAuthentication(
            token,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.DEVICE_CREDENTIAL),
        )

        assertTrue(coordinator.state.value.locked)
        assertEquals(initial, coordinator.state.value.configuration)
        assertEquals(initial, ready(store))
    }

    @Test
    fun `device lock aborts a disable write that has not entered its atomic transform`() = runBlocking {
        val initial = AccessLockConfiguration(true, AccessLockTimeout.ONE_MINUTE)
        val dataStore = ControllablePreferencesDataStore(
            mutablePreferencesOf(
                AccessLockPreferencesStore.ContractVersion to 1,
                AccessLockPreferencesStore.Enabled to true,
                AccessLockPreferencesStore.Timeout to initial.timeout.name,
            ),
        )
        val coordinator = coordinator(dataStore)
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), false)
        unlock(coordinator)
        val writeStarted = CompletableDeferred<Unit>()
        val allowWrite = CompletableDeferred<Unit>()
        dataStore.writeStarted = writeStarted
        dataStore.allowWrite = allowWrite
        val token = requireNotNull(coordinator.beginAuthentication(AccessLockOperation.Disable))

        coordinator.completeAuthentication(
            token,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.DEVICE_CREDENTIAL),
        )
        writeStarted.await()
        coordinator.deviceLocked()
        allowWrite.complete(Unit)
        while (coordinator.state.value.persistenceInProgress) kotlinx.coroutines.yield()

        assertTrue(coordinator.state.value.locked)
        assertEquals(initial, coordinator.state.value.configuration)
        assertEquals(initial, ready(AccessLockPreferencesStore(dataStore)))
    }

    @Test
    fun `disable whose atomic transform began before device lock remains committed`() = runBlocking {
        val initial = AccessLockConfiguration(true, AccessLockTimeout.ONE_MINUTE)
        val dataStore = ControllablePreferencesDataStore(
            mutablePreferencesOf(
                AccessLockPreferencesStore.ContractVersion to 1,
                AccessLockPreferencesStore.Enabled to true,
                AccessLockPreferencesStore.Timeout to initial.timeout.name,
            ),
        )
        val coordinator = coordinator(dataStore)
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), false)
        unlock(coordinator)
        val transformCompleted = CompletableDeferred<Unit>()
        val allowCommit = CompletableDeferred<Unit>()
        dataStore.transformCompleted = transformCompleted
        dataStore.allowCommit = allowCommit
        val token = requireNotNull(coordinator.beginAuthentication(AccessLockOperation.Disable))

        coordinator.completeAuthentication(
            token,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.DEVICE_CREDENTIAL),
        )
        transformCompleted.await()
        coordinator.deviceLocked()
        allowCommit.complete(Unit)
        while (coordinator.state.value.persistenceInProgress) kotlinx.coroutines.yield()

        assertEquals(AccessLockConfiguration(), coordinator.state.value.configuration)
        assertEquals(AccessLockConfiguration(), ready(AccessLockPreferencesStore(dataStore)))
        assertFalse(coordinator.state.value.locked)
        assertTrue(coordinator.state.value.allowsSensitiveContent)
        coordinator.activityStarted(Any(), false)
        coordinator.activityResumed(false)
        assertTrue(coordinator.state.value.allowsSensitiveContent)
    }

    @Test
    fun `device lock aborts repair before corrupt preferences are replaced`() = runBlocking {
        val dataStore = ControllablePreferencesDataStore(
            mutablePreferencesOf(
                AccessLockPreferencesStore.ContractVersion to 77,
                AccessLockPreferencesStore.Enabled to false,
                AccessLockPreferencesStore.Timeout to "UNKNOWN",
            ),
        )
        val coordinator = coordinator(dataStore)
        coordinator.initializeAfterRecovery()
        val writeStarted = CompletableDeferred<Unit>()
        val allowWrite = CompletableDeferred<Unit>()
        dataStore.writeStarted = writeStarted
        dataStore.allowWrite = allowWrite
        val token = requireNotNull(coordinator.beginAuthentication(AccessLockOperation.RepairStore))

        coordinator.completeAuthentication(
            token,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.DEVICE_CREDENTIAL),
        )
        writeStarted.await()
        coordinator.deviceLocked()
        allowWrite.complete(Unit)
        while (coordinator.state.value.persistenceInProgress) kotlinx.coroutines.yield()

        assertEquals(AccessLockPhase.STORE_ERROR, coordinator.state.value.phase)
        assertTrue(coordinator.state.value.locked)
        assertEquals(77, dataStore.current()[AccessLockPreferencesStore.ContractVersion])
    }

    @Test
    fun `settings write completed in background preserves the start of the new timeout`() = runBlocking {
        val initial = AccessLockConfiguration(true, AccessLockTimeout.FIFTEEN_MINUTES)
        val dataStore = ControllablePreferencesDataStore(
            mutablePreferencesOf(
                AccessLockPreferencesStore.ContractVersion to 1,
                AccessLockPreferencesStore.Enabled to true,
                AccessLockPreferencesStore.Timeout to initial.timeout.name,
            ),
        )
        val clock = FakeElapsedClock()
        val coordinator = coordinator(dataStore, clock)
        coordinator.initializeAfterRecovery()
        val activity = Any()
        coordinator.activityStarted(activity, false)
        unlock(coordinator)
        val writeStarted = CompletableDeferred<Unit>()
        val allowWrite = CompletableDeferred<Unit>()
        dataStore.writeStarted = writeStarted
        dataStore.allowWrite = allowWrite
        val token = requireNotNull(
            coordinator.beginAuthentication(
                AccessLockOperation.ChangeTimeout(AccessLockTimeout.ONE_MINUTE),
            ),
        )

        coordinator.completeAuthentication(
            token,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.STRONG_BIOMETRIC),
        )
        writeStarted.await()
        coordinator.activityPausedForPrivacy()
        coordinator.activityStopped(activity, changingConfigurations = false)
        clock.now = AccessLockTimeout.ONE_MINUTE.durationMillis
        allowWrite.complete(Unit)
        while (coordinator.state.value.persistenceInProgress) kotlinx.coroutines.yield()
        coordinator.activityStarted(Any(), false)
        coordinator.activityResumed(false)

        assertEquals(AccessLockTimeout.ONE_MINUTE, coordinator.state.value.configuration?.timeout)
        assertTrue(coordinator.state.value.locked)
        assertFalse(coordinator.state.value.allowsSensitiveContent)
    }

    @Test
    fun `activation performed in background starts its timeout when the atomic write commits`() = runBlocking {
        val dataStore = ControllablePreferencesDataStore(mutablePreferencesOf())
        val clock = FakeElapsedClock()
        val coordinator = coordinator(dataStore, clock)
        coordinator.initializeAfterRecovery()
        val firstActivity = Any()
        coordinator.activityStarted(firstActivity, false)
        val writeStarted = CompletableDeferred<Unit>()
        val allowWrite = CompletableDeferred<Unit>()
        dataStore.writeStarted = writeStarted
        dataStore.allowWrite = allowWrite
        val token = requireNotNull(
            coordinator.beginAuthentication(
                AccessLockOperation.Activate(AccessLockTimeout.ONE_MINUTE),
            ),
        )

        coordinator.completeAuthentication(
            token,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.STRONG_BIOMETRIC),
        )
        writeStarted.await()
        coordinator.activityPausedForPrivacy()
        coordinator.activityStopped(firstActivity, changingConfigurations = false)
        clock.now = AccessLockTimeout.ONE_MINUTE.durationMillis
        allowWrite.complete(Unit)
        while (coordinator.state.value.persistenceInProgress) kotlinx.coroutines.yield()

        val secondActivity = Any()
        coordinator.activityStarted(secondActivity, false)
        coordinator.activityResumed(false)
        assertTrue(coordinator.state.value.allowsSensitiveContent)

        coordinator.activityPausedForPrivacy()
        coordinator.activityStopped(secondActivity, changingConfigurations = false)
        clock.now += AccessLockTimeout.ONE_MINUTE.durationMillis
        coordinator.activityStarted(Any(), false)
        coordinator.activityResumed(false)
        assertTrue(coordinator.state.value.locked)
    }

    @Test
    fun `atomic write failure preserves the previous enabled configuration`() = runBlocking {
        val initial = AccessLockConfiguration(true, AccessLockTimeout.ONE_MINUTE)
        val dataStore = ControllablePreferencesDataStore(
            mutablePreferencesOf(
                AccessLockPreferencesStore.ContractVersion to 1,
                AccessLockPreferencesStore.Enabled to true,
                AccessLockPreferencesStore.Timeout to initial.timeout.name,
            ),
        )
        val coordinator = coordinator(dataStore)
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), false)
        unlock(coordinator)
        dataStore.failWrites = true
        val token = requireNotNull(
            coordinator.beginAuthentication(
                AccessLockOperation.ChangeTimeout(AccessLockTimeout.FIFTEEN_MINUTES),
            ),
        )

        coordinator.completeAuthentication(
            token,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.DEVICE_CREDENTIAL),
        )

        assertEquals(initial, coordinator.state.value.configuration)
        assertEquals(AccessLockMessage.STORE_WRITE_ERROR, coordinator.state.value.message)
        dataStore.failWrites = false
        assertEquals(initial, ready(AccessLockPreferencesStore(dataStore)))
    }

    private suspend fun unlock(coordinator: AccessLockCoordinator) {
        val token = requireNotNull(coordinator.beginAuthentication(AccessLockOperation.Unlock))
        coordinator.completeAuthentication(
            token,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.STRONG_BIOMETRIC),
        )
    }

    private fun coordinator(
        initial: AccessLockConfiguration = AccessLockConfiguration(),
    ): Pair<AccessLockCoordinator, AccessLockPreferencesStore> {
        val dataStore = ControllablePreferencesDataStore(
            if (initial == AccessLockConfiguration()) mutablePreferencesOf() else mutablePreferencesOf(
                AccessLockPreferencesStore.ContractVersion to 1,
                AccessLockPreferencesStore.Enabled to initial.enabled,
                AccessLockPreferencesStore.Timeout to initial.timeout.name,
            ),
        )
        val store = AccessLockPreferencesStore(dataStore)
        return coordinator(dataStore) to store
    }

    private fun coordinator(
        dataStore: ControllablePreferencesDataStore,
        clock: FakeElapsedClock = FakeElapsedClock(),
    ): AccessLockCoordinator {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also(scopes::add)
        return AccessLockCoordinator(
            AccessLockPreferencesStore(dataStore),
            scope,
            clock,
        )
    }

    private suspend fun ready(store: AccessLockPreferencesStore): AccessLockConfiguration =
        (store.read() as AccessLockStoreRead.Ready).configuration
}
