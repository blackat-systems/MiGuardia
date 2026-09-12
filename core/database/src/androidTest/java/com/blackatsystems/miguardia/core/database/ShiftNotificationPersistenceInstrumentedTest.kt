package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShiftNotificationPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: LocalDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB)
        store = LocalDataStore.create(context, DB)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun overrideRoundTripsReplacesClearsAndSurvivesReopen() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(501), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(write)
        store.shiftNotificationConfigs.replace(ShiftNotificationConfig(write.shift.id, emptyList()))
        assertEquals(emptyList<Long>(), store.shiftNotificationConfigs.getForShift(write.shift.id)?.reminderLeadMinutes)

        store.shiftNotificationConfigs.replace(ShiftNotificationConfig(write.shift.id, listOf(720L, 60L)))
        assertEquals(listOf(60L, 720L), store.shiftNotificationConfigs.observeForShift(write.shift.id).first()?.reminderLeadMinutes)
        store.close()
        store = LocalDataStore.create(context, DB)
        assertEquals(listOf(60L, 720L), store.shiftNotificationConfigs.getForShift(write.shift.id)?.reminderLeadMinutes)

        store.shiftNotificationConfigs.clear(write.shift.id)
        assertNull(store.shiftNotificationConfigs.getForShift(write.shift.id))
    }

    @Test
    fun deletingTheV2PairCascadesConfigAndReminders() = runBlocking {
        val fixture = store.seedV2Catalog()
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(502), V2TestIds.SHIFT_DATE)
        store.v2Shifts.insert(write)
        store.shiftNotificationConfigs.replace(ShiftNotificationConfig(write.shift.id, listOf(360L, 720L)))

        store.v2Shifts.deleteShift(write)

        assertNull(store.shiftNotificationConfigs.getForShift(write.shift.id))
    }

    @Test
    fun anOverrideCannotReferenceAMissingShift() = runBlocking {
        assertSuspendThrows<InvalidLocalDataException> {
            store.shiftNotificationConfigs.replace(
                ShiftNotificationConfig(V2TestIds.uuid(503), listOf(60L)),
            )
        }
    }

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(
        crossinline block: suspend () -> Unit,
    ) {
        try {
            block()
            throw AssertionError("Se esperaba ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
        }
    }

    private companion object {
        const val DB = "notification-v2-test.db"
    }
}
