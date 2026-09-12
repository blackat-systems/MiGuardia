package com.blackatsystems.miguardia

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.ui.calendar.CalendarMonthObserver
import com.blackatsystems.miguardia.ui.management.UuidProvider
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadActions
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadContent
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadCoordinator
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Only the unique named database below is created/deleted. Never uses the QA application's store. */
class FrancoFlowInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var store: LocalDataStore
    private lateinit var databaseName: String
    private lateinit var scope: CoroutineScope
    private lateinit var coordinator: V2ManualShiftLoadCoordinator
    private val date = LocalDate.of(2026, 9, 10)
    private val now = Instant.parse("2026-09-10T12:00:00Z")
    private val zone = ZoneId.of("America/Argentina/Cordoba")

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "franco-flow-${UUID.randomUUID()}.db"
        store = LocalDataStore.create(context, databaseName)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        coordinator = V2ManualShiftLoadCoordinator(
            configurationRepository = store.workConfiguration,
            catalogRepository = store.workCatalog,
            objectiveRepository = store.objectives,
            shiftRepository = store.shifts,
            medicalLeaveRepository = store.medicalLeaves,
            v2ShiftRepository = store.v2Shifts,
            shiftActualRepository = store.shiftActuals,
            explicitDayStatusRepository = store.explicitDayStatuses,
            clock = Clock.fixed(now, zone), zoneId = zone,
            uuidProvider = UuidProvider(UUID::randomUUID), scope = scope,
        )
    }

    @After fun tearDown() {
        scope.cancel()
        store.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun realLoadConfirmationPersistsObservedFrancosThenRemovesWithoutCatalog() {
        val revision = EffectiveRevision(
            UUID.randomUUID(), date.minusDays(1),
            WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, null),
        )
        runBlocking { store.workConfiguration.createInitial(V2AppTestFixture.TIMELINE_ID, revision) }
        val root = WorkSetupState.V2NeedsFirstSet(
            V2AppTestFixture.TIMELINE_ID, revision,
            setOf(com.blackatsystems.miguardia.core.domain.work.MissingWorkSetupRequirement.ACTIVE_WORK_TEMPLATE),
        )
        val dates = setOf(date, date.plusDays(2))
        showLoad(root, dates)
        compose.onNodeWithText("Cargar francos").performClick()
        compose.onNodeWithTag("v2-mark-day-off").performScrollTo().performClick()
        assertTrue(observed().explicitStatuses.isEmpty())
        compose.onNodeWithTag("v2-confirm-day-off").performClick()
        compose.waitUntil(10_000) { coordinator.uiState.value.successSequence > 0 }
        assertEquals(dates, observed().explicitStatuses.map { it.date }.toSet())
        assertTrue(observed().explicitStatuses.all { it.type == ExplicitDayStatusType.DAY_OFF })
        assertTrue(observed().shifts.isEmpty())

        compose.onNodeWithText("Cargar francos").performClick()
        compose.onNodeWithTag("v2-remove-day-off").performScrollTo().performClick()
        assertEquals(2, observed().explicitStatuses.size)
        val previousSuccess = coordinator.uiState.value.successSequence
        compose.onNodeWithTag("v2-confirm-day-off").performClick()
        compose.waitUntil(10_000) { coordinator.uiState.value.successSequence > previousSuccess }
        assertTrue(observed().explicitStatuses.isEmpty())
        assertTrue(observed().shifts.isEmpty())
    }

    @Test fun realOccupiedBatchRejectsEveryMarkAndKeepsTheStoredShift() {
        val shift = Shift(
            UUID.randomUUID(), now, now.plusSeconds(3_600), zone, date,
            "Lugar de prueba", "PRU", null, LocalTime.of(9, 0), LocalTime.of(10, 0),
            0xFF336699.toInt(), null, ShiftStatus.PLANNED, UUID.randomUUID(), now, now,
        )
        val storedShift = runBlocking {
            val write = V2AppTestFixture.writeFor(store, shift, date.minusDays(1))
            store.v2Shifts.insert(write)
            write.shift
        }
        val history = runBlocking { requireNotNull(store.workConfiguration.get()) }
        val root = WorkSetupState.V2Ready(history.timeline.id, requireNotNull(history.timeline.revisionAt(date)))
        showLoad(root, setOf(date, date.plusDays(1)))
        compose.onNodeWithText("Cargar francos").performClick()
        compose.onNodeWithTag("v2-mark-day-off").performScrollTo().performClick()
        compose.onNodeWithTag("v2-confirm-day-off").performClick()
        compose.waitUntil(10_000) { coordinator.uiState.value.errorMessage != null && !coordinator.uiState.value.isSaving }
        compose.onNodeWithTag("v2-confirm-day-off").assertIsDisplayed()
        assertEquals(0, coordinator.uiState.value.successSequence)
        assertTrue(observed().explicitStatuses.isEmpty())
        assertEquals(listOf(storedShift), observed().shifts)
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(setOf(date, date.plusDays(1)), coordinator.uiState.value.selectedDates)
    }

    private fun showLoad(root: WorkSetupState, dates: Set<LocalDate>) {
        compose.setContent {
            val state by coordinator.uiState.collectAsState()
            MiGuardiaTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (!state.isActive) Button(onClick = { coordinator.start(root) }) { Text("Cargar francos") }
                    V2ManualShiftLoadContent(
                        state, dates, false, true,
                        V2ManualShiftLoadActions(
                            requestDayOff = coordinator::requestDayOff,
                            confirmDayOff = coordinator::confirmDayOff,
                            cancelDayOff = coordinator::cancelDayOff,
                            retry = coordinator::retry,
                        ), {}, {},
                    )
                }
            }
        }
    }

    private fun observed() = runBlocking {
        withTimeout(5_000) {
            CalendarMonthObserver(store.shifts, store.explicitDayStatuses, store.medicalLeaves,
                store.holidays, store.vacations).observe(YearMonth.from(date)).first()
        }
    }
}
