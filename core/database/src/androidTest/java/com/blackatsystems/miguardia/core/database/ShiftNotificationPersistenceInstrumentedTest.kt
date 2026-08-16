package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
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
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "notification-${UUID.randomUUID()}.db"
    private lateinit var store: LocalDataStore

    @Before fun setUp() { store = LocalDataStore.create(context, databaseName) }
    @After fun tearDown() { store.close(); context.deleteDatabase(databaseName) }

    @Test
    fun emptyOverrideAndFiveRemindersRoundTrip() = runBlocking {
        store.shifts.insert(shift())
        store.shiftNotificationConfigs.replace(ShiftNotificationConfig(SHIFT_ID, emptyList()))
        assertEquals(emptyList<Long>(), store.shiftNotificationConfigs.getForShift(SHIFT_ID)?.reminderLeadMinutes)

        val five = listOf(1L, 360L, 480L, 720L, 1440L)
        store.shiftNotificationConfigs.replace(ShiftNotificationConfig(SHIFT_ID, five))
        assertEquals(five, store.shiftNotificationConfigs.observeForShift(SHIFT_ID).first()?.reminderLeadMinutes)
    }

    @Test
    fun replacementIsAtomicAndClearRestoresGlobalMeaning() = runBlocking {
        store.shifts.insert(shift())
        store.shiftNotificationConfigs.replace(ShiftNotificationConfig(SHIFT_ID, listOf(360L, 720L)))
        store.shiftNotificationConfigs.replace(ShiftNotificationConfig(SHIFT_ID, listOf(480L)))
        assertEquals(listOf(480L), store.shiftNotificationConfigs.getForShift(SHIFT_ID)?.reminderLeadMinutes)
        store.shiftNotificationConfigs.clear(SHIFT_ID)
        assertNull(store.shiftNotificationConfigs.getForShift(SHIFT_ID))
    }

    @Test
    fun deletingShiftCascadesConfigAndReminders() = runBlocking {
        store.shifts.insert(shift())
        store.shiftNotificationConfigs.replace(ShiftNotificationConfig(SHIFT_ID, listOf(360L, 720L)))
        store.shifts.delete(SHIFT_ID)
        assertNull(store.shiftNotificationConfigs.getForShift(SHIFT_ID))
    }

    @Test
    fun configurationSurvivesReopen() = runBlocking {
        store.shifts.insert(shift())
        store.shiftNotificationConfigs.replace(ShiftNotificationConfig(SHIFT_ID, listOf(720L)))
        store.close()
        store = LocalDataStore.create(context, databaseName)
        assertEquals(listOf(720L), store.shiftNotificationConfigs.getForShift(SHIFT_ID)?.reminderLeadMinutes)
    }

    private fun shift(): Shift {
        val start = Instant.parse("2026-09-01T22:00:00Z")
        val end = Instant.parse("2026-09-02T10:00:00Z")
        return Shift(
            SHIFT_ID,
            start,
            end,
            ZoneId.of("America/Argentina/Cordoba"),
            LocalDate.of(2026, 9, 1),
            "Objetivo ficticio",
            "QA",
            null,
            LocalTime.of(19, 0),
            LocalTime.of(7, 0),
            0xff336699.toInt(),
            null,
            ShiftStatus.PLANNED,
            null,
            null,
            start,
            start,
        )
    }

    private companion object {
        val SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000501")
    }
}
