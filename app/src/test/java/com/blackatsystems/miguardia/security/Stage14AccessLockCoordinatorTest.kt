package com.blackatsystems.miguardia.security

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Etapa 14 · Bloqueo: cancelar al desbloquear, reintento de lectura y teléfono sin capacidad. */
class Stage14AccessLockCoordinatorTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    // A256
    @Test
    fun `cancelling the unlock keeps the session closed with its message and a later success opens`() =
        runBlocking {
            val initial = AccessLockConfiguration(true, AccessLockTimeout.FIVE_MINUTES)
            val dataStore = enabledStore(initial)
            val coordinator = coordinator(dataStore)
            coordinator.initializeAfterRecovery()
            coordinator.activityStarted(Any(), deviceLocked = false)
            coordinator.activityResumed(deviceLocked = false)
            assertTrue(coordinator.state.value.locked)

            val cancelled = requireNotNull(coordinator.beginAuthentication(AccessLockOperation.Unlock))
            assertTrue(coordinator.state.value.authenticationInProgress)
            assertFalse(coordinator.state.value.allowsSensitiveContent)
            coordinator.completeAuthentication(cancelled, DeviceAuthenticationResult.UserCancelled)

            val closed = coordinator.state.value
            assertEquals(AccessLockPhase.READY, closed.phase)
            assertTrue(closed.locked)
            assertFalse(closed.allowsSensitiveContent)
            assertFalse(closed.authenticationInProgress)
            assertEquals(AccessLockMessage.USER_CANCELLED, closed.message)
            assertNull(coordinator.activeAuthenticationToken)
            assertEquals(initial, closed.configuration)
            assertEquals(initial, ready(dataStore))

            val retry = requireNotNull(coordinator.beginAuthentication(AccessLockOperation.Unlock))
            assertTrue(retry != cancelled)
            assertNull(coordinator.state.value.message)
            coordinator.completeAuthentication(
                retry,
                DeviceAuthenticationResult.Success(DeviceAuthenticationSource.DEVICE_CREDENTIAL),
            )

            val open = coordinator.state.value
            assertFalse(open.locked)
            assertTrue(open.allowsSensitiveContent)
            assertNull(open.message)
            assertEquals(initial, open.configuration)
        }

    // A256
    @Test
    fun `a late success of the cancelled unlock dialog cannot open the session`() = runBlocking {
        val coordinator = coordinator(enabledStore())
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), deviceLocked = false)
        val cancelled = requireNotNull(coordinator.beginAuthentication(AccessLockOperation.Unlock))
        coordinator.completeAuthentication(cancelled, DeviceAuthenticationResult.UserCancelled)

        coordinator.completeAuthentication(
            cancelled,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.STRONG_BIOMETRIC),
        )

        assertTrue(coordinator.state.value.locked)
        assertFalse(coordinator.state.value.allowsSensitiveContent)
        assertEquals(AccessLockMessage.USER_CANCELLED, coordinator.state.value.message)
    }

    // A256
    @Test
    fun `abandoning the unlock dialog keeps the session closed and allows a new attempt`() = runBlocking {
        val coordinator = coordinator(enabledStore())
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), deviceLocked = false)
        val token = requireNotNull(coordinator.beginAuthentication(AccessLockOperation.Unlock, "main-host"))

        coordinator.abandonAuthentication(token, "main-host")

        assertTrue(coordinator.state.value.locked)
        assertFalse(coordinator.state.value.allowsSensitiveContent)
        assertFalse(coordinator.state.value.authenticationInProgress)
        assertEquals(AccessLockMessage.SYSTEM_CANCELLED, coordinator.state.value.message)
        assertNotNull(coordinator.beginAuthentication(AccessLockOperation.Unlock, "main-host"))
    }

    // A256, A257
    @Test
    fun `every failed unlock stays closed and reports the message of its own error`() = runBlocking {
        val expected = mapOf(
            DeviceAuthenticationResult.UserCancelled to AccessLockMessage.USER_CANCELLED,
            DeviceAuthenticationResult.SystemCancelled to AccessLockMessage.SYSTEM_CANCELLED,
            DeviceAuthenticationResult.Lockout to AccessLockMessage.LOCKOUT,
            DeviceAuthenticationResult.HardwareUnavailable to AccessLockMessage.HARDWARE_UNAVAILABLE,
            DeviceAuthenticationResult.BiometricNotEnrolled to AccessLockMessage.BIOMETRIC_NOT_ENROLLED,
            DeviceAuthenticationResult.NoSecureCredential to AccessLockMessage.NO_SECURE_CREDENTIAL,
            DeviceAuthenticationResult.Unsupported to AccessLockMessage.UNSUPPORTED,
            DeviceAuthenticationResult.RecoverableError to AccessLockMessage.RECOVERABLE_ERROR,
            DeviceAuthenticationResult.FinalError to AccessLockMessage.FINAL_ERROR,
        )
        expected.forEach { (failure, message) ->
            val initial = AccessLockConfiguration(true, AccessLockTimeout.ONE_MINUTE)
            val dataStore = enabledStore(initial)
            val coordinator = coordinator(dataStore)
            coordinator.initializeAfterRecovery()
            coordinator.activityStarted(Any(), deviceLocked = false)
            val token = requireNotNull(coordinator.beginAuthentication(AccessLockOperation.Unlock))

            coordinator.completeAuthentication(token, failure)

            val state = coordinator.state.value
            assertEquals("$failure", message, state.message)
            assertTrue("$failure", state.locked)
            assertFalse("$failure", state.allowsSensitiveContent)
            assertFalse("$failure", state.authenticationInProgress)
            assertEquals("$failure", initial, ready(dataStore))
            // The error is not terminal: the person can ask again.
            assertNotNull("$failure", coordinator.beginAuthentication(AccessLockOperation.Unlock))
        }
    }

    // A258
    @Test
    fun `retrying a failed read with a healthy store restores the saved lock still closed`() = runBlocking {
        val initial = AccessLockConfiguration(true, AccessLockTimeout.FIFTEEN_MINUTES)
        val dataStore = enabledStore(initial)
        dataStore.failReads = true
        val coordinator = coordinator(dataStore)
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), deviceLocked = false)
        assertEquals(AccessLockPhase.STORE_ERROR, coordinator.state.value.phase)
        assertNull(coordinator.state.value.configuration)
        assertFalse(coordinator.state.value.allowsSensitiveContent)

        dataStore.failReads = false
        coordinator.retryStoreRead()
        awaitLoaded(coordinator)

        val state = coordinator.state.value
        assertEquals(AccessLockPhase.READY, state.phase)
        assertEquals(initial, state.configuration)
        assertTrue(state.locked)
        assertFalse(state.allowsSensitiveContent)
        assertNull(state.message)

        val token = requireNotNull(coordinator.beginAuthentication(AccessLockOperation.Unlock))
        coordinator.completeAuthentication(
            token,
            DeviceAuthenticationResult.Success(DeviceAuthenticationSource.STRONG_BIOMETRIC),
        )
        assertTrue(coordinator.state.value.allowsSensitiveContent)
        assertEquals(initial, ready(dataStore))
    }

    // A258
    @Test
    fun `retrying a failed read with a healthy disabled store opens without authentication`() = runBlocking {
        val dataStore = ControllablePreferencesDataStore(mutablePreferencesOf())
        dataStore.failReads = true
        val coordinator = coordinator(dataStore)
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), deviceLocked = false)
        assertEquals(AccessLockPhase.STORE_ERROR, coordinator.state.value.phase)
        assertTrue(coordinator.state.value.locked)

        dataStore.failReads = false
        coordinator.retryStoreRead()
        awaitLoaded(coordinator)

        val state = coordinator.state.value
        assertEquals(AccessLockPhase.READY, state.phase)
        assertEquals(AccessLockConfiguration(), state.configuration)
        assertFalse(state.locked)
        assertTrue(state.allowsSensitiveContent)
        assertNull(coordinator.activeAuthenticationToken)
        assertTrue(dataStore.current().asMap().isEmpty())
    }

    // A258
    @Test
    fun `retrying while the store is still broken stays closed and keeps retry and repair available`() =
        runBlocking {
            val dataStore = enabledStore()
            dataStore.failReads = true
            val coordinator = coordinator(dataStore)
            coordinator.initializeAfterRecovery()
            coordinator.activityStarted(Any(), deviceLocked = false)

            repeat(2) {
                coordinator.retryStoreRead()
                awaitLoaded(coordinator)

                val state = coordinator.state.value
                assertEquals(AccessLockPhase.STORE_ERROR, state.phase)
                assertTrue(state.locked)
                assertFalse(state.allowsSensitiveContent)
                assertTrue(state.protectionRequired)
                assertFalse(state.persistenceInProgress)
            }
            // A closed error door never offers the ordinary unlock, only the authenticated repair.
            assertNull(coordinator.beginAuthentication(AccessLockOperation.Unlock))
            assertNotNull(coordinator.beginAuthentication(AccessLockOperation.RepairStore))
        }

    // A258
    @Test
    fun `retrying a store with an unknown contract stays closed and does not rewrite it`() = runBlocking {
        val dataStore = ControllablePreferencesDataStore(
            mutablePreferencesOf(
                AccessLockPreferencesStore.ContractVersion to 77,
                AccessLockPreferencesStore.Enabled to false,
                AccessLockPreferencesStore.Timeout to "UNKNOWN",
            ),
        )
        val coordinator = coordinator(dataStore)
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), deviceLocked = false)

        coordinator.retryStoreRead()
        awaitLoaded(coordinator)

        assertEquals(AccessLockPhase.STORE_ERROR, coordinator.state.value.phase)
        assertFalse(coordinator.state.value.allowsSensitiveContent)
        assertEquals(77, dataStore.current()[AccessLockPreferencesStore.ContractVersion])
        assertEquals("UNKNOWN", dataStore.current()[AccessLockPreferencesStore.Timeout])
    }

    // A258
    @Test
    fun `retry does nothing without a read error and a cancelled repair keeps what the retry read`() =
        runBlocking {
            val initial = AccessLockConfiguration(true, AccessLockTimeout.ONE_MINUTE)
            val healthy = enabledStore(initial)
            val healthyCoordinator = coordinator(healthy)
            healthyCoordinator.initializeAfterRecovery()
            healthyCoordinator.activityStarted(Any(), deviceLocked = false)
            val before = healthyCoordinator.state.value

            healthy.failReads = true
            healthyCoordinator.retryStoreRead()

            // Not in error: the store is not read again, so the broken read is never observed.
            assertEquals(before, healthyCoordinator.state.value)
            assertEquals(AccessLockPhase.READY, healthyCoordinator.state.value.phase)

            val broken = enabledStore(initial)
            broken.failReads = true
            val failedCoordinator = coordinator(broken)
            failedCoordinator.initializeAfterRecovery()
            val repair = requireNotNull(
                failedCoordinator.beginAuthentication(AccessLockOperation.RepairStore),
            )
            broken.failReads = false
            failedCoordinator.retryStoreRead()
            awaitLoaded(failedCoordinator)
            assertEquals(AccessLockPhase.READY, failedCoordinator.state.value.phase)

            failedCoordinator.completeAuthentication(repair, DeviceAuthenticationResult.UserCancelled)

            assertEquals(initial, failedCoordinator.state.value.configuration)
            assertEquals(initial, ready(broken))
            assertTrue(failedCoordinator.state.value.locked)
            assertFalse(failedCoordinator.state.value.allowsSensitiveContent)
        }

    // A246, A257
    @Test
    fun `phone without secure credential reports it and an activation attempt changes nothing`() =
        runBlocking {
            val dataStore = ControllablePreferencesDataStore(mutablePreferencesOf())
            val coordinator = coordinator(dataStore)
            coordinator.initializeAfterRecovery()
            coordinator.activityStarted(Any(), deviceLocked = false)

            // What the host does when the phone answers «sin credencial segura» before any dialog.
            coordinator.reportUnavailable(DeviceAuthenticationCapability.NO_SECURE_CREDENTIAL)

            var state = coordinator.state.value
            assertEquals(AccessLockMessage.NO_SECURE_CREDENTIAL, state.message)
            assertEquals(AccessLockConfiguration(), state.configuration)
            assertFalse(state.authenticationInProgress)
            assertNull(coordinator.activeAuthenticationToken)
            // The lock is off: the app stays usable.
            assertTrue(state.allowsSensitiveContent)
            assertTrue(dataStore.current().asMap().isEmpty())

            // The system dialog itself answering «sin credencial» to an activation.
            val token = requireNotNull(
                coordinator.beginAuthentication(AccessLockOperation.Activate(AccessLockTimeout.ONE_MINUTE)),
            )
            coordinator.completeAuthentication(token, DeviceAuthenticationResult.NoSecureCredential)

            state = coordinator.state.value
            assertEquals(AccessLockMessage.NO_SECURE_CREDENTIAL, state.message)
            assertEquals(AccessLockConfiguration(), state.configuration)
            assertFalse(state.locked)
            assertFalse(state.persistenceInProgress)
            assertTrue(state.allowsSensitiveContent)
            assertTrue(dataStore.current().asMap().isEmpty())
        }

    // A257
    @Test
    fun `losing the secure credential during activation never enables an unrecoverable lock`() =
        runBlocking {
            val dataStore = ControllablePreferencesDataStore(mutablePreferencesOf())
            val coordinator = coordinator(dataStore)
            coordinator.initializeAfterRecovery()
            coordinator.activityStarted(Any(), deviceLocked = false)
            val token = requireNotNull(
                coordinator.beginAuthentication(AccessLockOperation.Activate(AccessLockTimeout.IMMEDIATE)),
            )

            coordinator.secureCredentialUnavailable()
            coordinator.completeAuthentication(
                token,
                DeviceAuthenticationResult.Success(DeviceAuthenticationSource.STRONG_BIOMETRIC),
            )

            val state = coordinator.state.value
            assertEquals(AccessLockConfiguration(), state.configuration)
            assertEquals(AccessLockMessage.NO_SECURE_CREDENTIAL, state.message)
            assertFalse(state.authenticationInProgress)
            assertFalse(state.persistenceInProgress)
            assertFalse(state.locked)
            assertTrue(state.allowsSensitiveContent)
            assertTrue(dataStore.current().asMap().isEmpty())
        }

    // A246, A257
    @Test
    fun `temporarily unavailable capability reports hardware and keeps a disabled lock disabled`() =
        runBlocking {
            val dataStore = ControllablePreferencesDataStore(mutablePreferencesOf())
            val coordinator = coordinator(dataStore)
            coordinator.initializeAfterRecovery()
            coordinator.activityStarted(Any(), deviceLocked = false)

            coordinator.reportUnavailable(DeviceAuthenticationCapability.TEMPORARILY_UNAVAILABLE)

            var state = coordinator.state.value
            assertEquals(AccessLockMessage.HARDWARE_UNAVAILABLE, state.message)
            assertEquals(AccessLockConfiguration(), state.configuration)
            assertFalse(state.authenticationInProgress)
            assertNull(coordinator.activeAuthenticationToken)
            assertTrue(state.allowsSensitiveContent)

            val token = requireNotNull(
                coordinator.beginAuthentication(AccessLockOperation.Activate(AccessLockTimeout.FIVE_MINUTES)),
            )
            coordinator.completeAuthentication(token, DeviceAuthenticationResult.HardwareUnavailable)

            state = coordinator.state.value
            assertEquals(AccessLockMessage.HARDWARE_UNAVAILABLE, state.message)
            assertEquals(AccessLockConfiguration(), state.configuration)
            assertTrue(dataStore.current().asMap().isEmpty())

            // Once the phone answers again, the same activation goes through.
            val retry = requireNotNull(
                coordinator.beginAuthentication(AccessLockOperation.Activate(AccessLockTimeout.FIVE_MINUTES)),
            )
            assertNull(coordinator.state.value.message)
            coordinator.completeAuthentication(
                retry,
                DeviceAuthenticationResult.Success(DeviceAuthenticationSource.DEVICE_CREDENTIAL),
            )
            assertEquals(
                AccessLockConfiguration(true, AccessLockTimeout.FIVE_MINUTES),
                coordinator.state.value.configuration,
            )
        }

    // A246, A257
    @Test
    fun `unavailable capability on a closed session keeps it closed and leaves lock now and retry usable`() =
        runBlocking {
            listOf(
                DeviceAuthenticationCapability.TEMPORARILY_UNAVAILABLE to AccessLockMessage.HARDWARE_UNAVAILABLE,
                DeviceAuthenticationCapability.NO_SECURE_CREDENTIAL to AccessLockMessage.NO_SECURE_CREDENTIAL,
            ).forEach { (capability, message) ->
                val initial = AccessLockConfiguration(true, AccessLockTimeout.FIVE_MINUTES)
                val dataStore = enabledStore(initial)
                val coordinator = coordinator(dataStore)
                coordinator.initializeAfterRecovery()
                coordinator.activityStarted(Any(), deviceLocked = false)

                coordinator.reportUnavailable(capability)

                var state = coordinator.state.value
                assertEquals("$capability", message, state.message)
                assertTrue("$capability", state.locked)
                assertFalse("$capability", state.allowsSensitiveContent)
                assertFalse("$capability", state.authenticationInProgress)
                assertEquals("$capability", initial, state.configuration)
                assertEquals("$capability", initial, ready(dataStore))

                // Locking never needs the phone credential.
                coordinator.lockNow()
                state = coordinator.state.value
                assertTrue("$capability", state.locked)
                assertNull("$capability", state.message)

                // A new attempt is accepted when the capability comes back.
                val token = requireNotNull(coordinator.beginAuthentication(AccessLockOperation.Unlock))
                coordinator.completeAuthentication(
                    token,
                    DeviceAuthenticationResult.Success(DeviceAuthenticationSource.DEVICE_CREDENTIAL),
                )
                assertTrue("$capability", coordinator.state.value.allowsSensitiveContent)
            }
        }

    // A246
    @Test
    fun `capability report cannot overwrite a dialog in flight and its message can be cleared`() =
        runBlocking {
            val coordinator = coordinator(enabledStore())
            coordinator.initializeAfterRecovery()
            coordinator.activityStarted(Any(), deviceLocked = false)
            val token = requireNotNull(coordinator.beginAuthentication(AccessLockOperation.Unlock))

            coordinator.reportUnavailable(DeviceAuthenticationCapability.TEMPORARILY_UNAVAILABLE)

            assertNull(coordinator.state.value.message)
            assertTrue(coordinator.state.value.authenticationInProgress)
            assertEquals(token, coordinator.activeAuthenticationToken)

            coordinator.completeAuthentication(token, DeviceAuthenticationResult.UserCancelled)
            coordinator.reportUnavailable(DeviceAuthenticationCapability.NO_SECURE_CREDENTIAL)
            assertEquals(AccessLockMessage.NO_SECURE_CREDENTIAL, coordinator.state.value.message)

            coordinator.clearMessage()

            assertNull(coordinator.state.value.message)
            assertTrue(coordinator.state.value.locked)
            assertFalse(coordinator.state.value.allowsSensitiveContent)
        }

    private fun enabledStore(
        configuration: AccessLockConfiguration = AccessLockConfiguration(true, AccessLockTimeout.FIVE_MINUTES),
    ) = ControllablePreferencesDataStore(
        mutablePreferencesOf(
            AccessLockPreferencesStore.ContractVersion to 1,
            AccessLockPreferencesStore.Enabled to configuration.enabled,
            AccessLockPreferencesStore.Timeout to configuration.timeout.name,
        ),
    )

    private fun coordinator(dataStore: ControllablePreferencesDataStore): AccessLockCoordinator {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also(scopes::add)
        return AccessLockCoordinator(AccessLockPreferencesStore(dataStore), scope, FakeElapsedClock())
    }

    private suspend fun awaitLoaded(coordinator: AccessLockCoordinator) {
        withTimeout(5_000L) {
            while (coordinator.state.value.phase == AccessLockPhase.LOADING) yield()
        }
    }

    private suspend fun ready(dataStore: ControllablePreferencesDataStore): AccessLockConfiguration =
        (AccessLockPreferencesStore(dataStore).read() as AccessLockStoreRead.Ready).configuration
}
