package com.blackatsystems.miguardia.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AccessLockPreferencesStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `empty store is the only implicit disabled default`() = runBlocking {
        val store = AccessLockPreferencesStore(file(), scope)

        assertEquals(
            AccessLockStoreRead.Ready(AccessLockConfiguration()),
            store.read(),
        )
    }

    @Test
    fun `all timeouts persist atomically and reopen`() = runBlocking {
        val target = file()
        var store = AccessLockPreferencesStore(target, scope)
        AccessLockTimeout.entries.forEach { timeout ->
            store.replace(AccessLockConfiguration(enabled = true, timeout = timeout))
            assertEquals(
                AccessLockStoreRead.Ready(AccessLockConfiguration(true, timeout)),
                store.read(),
            )
        }

        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = AccessLockPreferencesStore(target, scope)

        assertEquals(
            AccessLockStoreRead.Ready(
                AccessLockConfiguration(true, AccessLockTimeout.FIFTEEN_MINUTES),
            ),
            store.read(),
        )
    }

    @Test
    fun `unknown incomplete or extra values fail closed`() {
        assertEquals(
            AccessLockStoreRead.Error,
            AccessLockPreferencesStore.decode(
                mutablePreferencesOf(
                    AccessLockPreferencesStore.ContractVersion to 99,
                    AccessLockPreferencesStore.Enabled to false,
                    AccessLockPreferencesStore.Timeout to AccessLockTimeout.IMMEDIATE.name,
                ),
            ),
        )
        assertEquals(
            AccessLockStoreRead.Error,
            AccessLockPreferencesStore.decode(
                mutablePreferencesOf(
                    AccessLockPreferencesStore.ContractVersion to 1,
                    AccessLockPreferencesStore.Enabled to false,
                ),
            ),
        )
        assertEquals(
            AccessLockStoreRead.Error,
            AccessLockPreferencesStore.decode(
                mutablePreferencesOf(
                    AccessLockPreferencesStore.ContractVersion to 1,
                    AccessLockPreferencesStore.Enabled to false,
                    AccessLockPreferencesStore.Timeout to "NEVER",
                ),
            ),
        )
        assertEquals(
            AccessLockStoreRead.Error,
            AccessLockPreferencesStore.decode(
                mutablePreferencesOf(
                    AccessLockPreferencesStore.ContractVersion to 1,
                    AccessLockPreferencesStore.Enabled to false,
                    AccessLockPreferencesStore.Timeout to AccessLockTimeout.IMMEDIATE.name,
                    stringPreferencesKey("destination") to "forbidden",
                ),
            ),
        )
    }

    @Test
    fun `corrupt bytes remain a closed error until explicit repair`() = runBlocking {
        val corruptFile = file().apply { writeBytes(byteArrayOf(0x7F, 0x00, 0x55, 0x33)) }
        val store = AccessLockPreferencesStore(corruptFile, scope)

        assertEquals(AccessLockStoreRead.Error, store.read())
        store.repair()

        assertEquals(
            AccessLockStoreRead.Ready(AccessLockConfiguration()),
            store.read(),
        )
    }

    @Test
    fun `io failure is reported and a later retry reads the unchanged value`() = runBlocking {
        val dataStore = ControllablePreferencesDataStore(
            mutablePreferencesOf(
                AccessLockPreferencesStore.ContractVersion to 1,
                AccessLockPreferencesStore.Enabled to true,
                AccessLockPreferencesStore.Timeout to AccessLockTimeout.FIVE_MINUTES.name,
            ),
        )
        val store = AccessLockPreferencesStore(dataStore)
        dataStore.failReads = true

        assertEquals(AccessLockStoreRead.Error, store.read())
        dataStore.failReads = false

        assertEquals(
            AccessLockStoreRead.Ready(
                AccessLockConfiguration(true, AccessLockTimeout.FIVE_MINUTES),
            ),
            store.read(),
        )
    }

    @Test
    fun `contract persists exactly version enabled and timeout`() = runBlocking {
        val dataStore = ControllablePreferencesDataStore(mutablePreferencesOf())
        val store = AccessLockPreferencesStore(dataStore)

        store.replace(AccessLockConfiguration(true, AccessLockTimeout.ONE_MINUTE))

        val keys = dataStore.current().asMap().keys.map { it.name }.toSet()
        assertEquals(setOf("contract_version", "enabled", "timeout"), keys)
        assertTrue(keys.none { it.contains("session") || it.contains("destination") })
    }

    private fun file(): File = File(temporaryFolder.root, AccessLockPreferencesStore.DEFAULT_FILE_NAME)
}

internal class ControllablePreferencesDataStore(
    initial: Preferences,
) : DataStore<Preferences> {
    private val values = MutableStateFlow(initial)
    var failReads: Boolean = false
    var failWrites: Boolean = false
    var writeStarted: CompletableDeferred<Unit>? = null
    var allowWrite: CompletableDeferred<Unit>? = null
    var transformCompleted: CompletableDeferred<Unit>? = null
    var allowCommit: CompletableDeferred<Unit>? = null

    override val data: Flow<Preferences>
        get() = if (failReads) flow { throw IOException("synthetic read failure") } else values

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        if (failWrites) throw IOException("synthetic write failure")
        writeStarted?.complete(Unit)
        allowWrite?.await()
        val updated = transform(values.value)
        transformCompleted?.complete(Unit)
        allowCommit?.await()
        values.value = updated
        return updated
    }

    fun current(): Preferences = values.value
}
