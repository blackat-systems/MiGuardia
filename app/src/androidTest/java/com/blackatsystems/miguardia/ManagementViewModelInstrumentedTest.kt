package com.blackatsystems.miguardia

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.ScheduleCombinationRepository
import com.blackatsystems.miguardia.ui.management.ManagementSurface
import com.blackatsystems.miguardia.ui.management.ManagementViewModel
import com.blackatsystems.miguardia.ui.management.UuidProvider
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagementViewModelInstrumentedTest {
    @Test
    fun initialPreparationKeepsMultipleTemplatesAndNeverCreatesAShiftDraft() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val databaseName = "management-initial-${UUID.randomUUID()}.db"
        val localDataStore = LocalDataStore.create(context, databaseName)
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val generatedIds = ArrayDeque(
            listOf(
                FIRST_OBJECTIVE_ID,
                FIRST_SCHEDULE_ID,
                FIRST_SECOND_SCHEDULE_ID,
                SECOND_OBJECTIVE_ID,
                SECOND_SCHEDULE_ID,
            ),
        )
        lateinit var viewModel: ManagementViewModel

        try {
            instrumentation.runOnMainSync {
                viewModel = ViewModelProvider(
                    owner,
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = ManagementViewModel(
                            objectiveRepository = localDataStore.objectives,
                            scheduleRepository = localDataStore.scheduleCombinations,
                            shiftRepository = localDataStore.shifts,
                            explicitDayStatusRepository = localDataStore.explicitDayStatuses,
                            medicalLeaveRepository = localDataStore.medicalLeaves,
                            clock = Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), AppDefaults.zoneId()),
                            uuidProvider = UuidProvider { generatedIds.removeFirst() },
                            savedStateHandle = SavedStateHandle(),
                        ) as T
                    },
                )[ManagementViewModel::class.java]
                viewModel.openInitialDataPreparation()
            }
            runBlocking { withTimeout(5_000L) { viewModel.uiState.first { it.catalogLoaded } } }
            assertEquals(ManagementSurface.INITIAL_DATA_PREPARATION, viewModel.uiState.value.surface)
            assertNull(viewModel.uiState.value.shiftDraft)

            instrumentation.runOnMainSync {
                viewModel.openObjectiveForm(null)
                viewModel.updateObjectiveDraft { it.copy(fullName = "Objetivo inicial uno", abbreviation = "UNO") }
                viewModel.saveObjective()
            }
            runBlocking {
                withTimeout(5_000L) {
                    viewModel.uiState.first {
                        it.surface == ManagementSurface.INITIAL_DATA_PREPARATION &&
                            it.objectives.any { objective -> objective.id == FIRST_OBJECTIVE_ID }
                    }
                }
            }
            instrumentation.runOnMainSync {
                viewModel.openScheduleForm(FIRST_OBJECTIVE_ID, null)
                viewModel.updateScheduleDraft { it.copy(startTime = "07:00", endTime = "15:00") }
                viewModel.saveSchedule()
            }
            runBlocking {
                withTimeout(5_000L) {
                    viewModel.uiState.first {
                        it.surface == ManagementSurface.INITIAL_DATA_PREPARATION &&
                            it.scheduleOptions.any { option -> option.combination.id == FIRST_SCHEDULE_ID }
                    }
                }
            }
            assertNull(viewModel.uiState.value.shiftDraft)

            instrumentation.runOnMainSync {
                viewModel.openScheduleForm(FIRST_OBJECTIVE_ID, null)
                viewModel.updateScheduleDraft { it.copy(startTime = "12:00", endTime = "20:00") }
                viewModel.saveSchedule()
            }
            runBlocking {
                withTimeout(5_000L) {
                    viewModel.uiState.first {
                        it.surface == ManagementSurface.INITIAL_DATA_PREPARATION &&
                            it.scheduleOptions.any { option -> option.combination.id == FIRST_SECOND_SCHEDULE_ID }
                    }
                }
            }
            assertNull(viewModel.uiState.value.shiftDraft)

            instrumentation.runOnMainSync {
                viewModel.openObjectiveForm(null)
                viewModel.updateObjectiveDraft { it.copy(fullName = "Objetivo inicial dos", abbreviation = "DOS") }
                viewModel.saveObjective()
            }
            runBlocking {
                withTimeout(5_000L) {
                    viewModel.uiState.first {
                        it.surface == ManagementSurface.INITIAL_DATA_PREPARATION &&
                            it.objectives.any { objective -> objective.id == SECOND_OBJECTIVE_ID }
                    }
                }
            }
            instrumentation.runOnMainSync {
                viewModel.openScheduleForm(SECOND_OBJECTIVE_ID, null)
                viewModel.updateScheduleDraft {
                    it.copy(startTime = "15:00", endTime = "23:00", colorArgb = 0xFF8A3FFC.toInt())
                }
                viewModel.saveSchedule()
            }
            val completedPreparation = runBlocking {
                withTimeout(5_000L) {
                    viewModel.uiState.first {
                        it.surface == ManagementSurface.INITIAL_DATA_PREPARATION &&
                            it.scheduleOptions.any { option -> option.combination.id == SECOND_SCHEDULE_ID }
                    }
                }
            }
            assertEquals(2, completedPreparation.objectives.count { it.isActive })
            assertEquals(3, completedPreparation.scheduleOptions.count { it.combination.isActive })
            assertEquals(
                2,
                completedPreparation.scheduleOptions.count {
                    it.objective.id == FIRST_OBJECTIVE_ID && it.combination.isActive
                },
            )
            assertNull(completedPreparation.shiftDraft)
            assertTrue(completedPreparation.scheduleOptions.all { it.objective.isActive })

            instrumentation.runOnMainSync { viewModel.closeSurface() }
            assertEquals(ManagementSurface.NONE, viewModel.uiState.value.surface)
            assertNull(viewModel.uiState.value.shiftDraft)
        } finally {
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
            localDataStore.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun creatingObjectiveAndScheduleReturnsToTheSameShiftDraftAndSelectsTheNewSchedule() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val databaseName = "management-return-${UUID.randomUUID()}.db"
        val localDataStore = LocalDataStore.create(context, databaseName)
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val generatedIds = ArrayDeque(listOf(OBJECTIVE_ID, SCHEDULE_ID))
        lateinit var viewModel: ManagementViewModel

        try {
            instrumentation.runOnMainSync {
                viewModel = ViewModelProvider(
                    owner,
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = ManagementViewModel(
                            objectiveRepository = localDataStore.objectives,
                            scheduleRepository = localDataStore.scheduleCombinations,
                            shiftRepository = localDataStore.shifts,
                            explicitDayStatusRepository = localDataStore.explicitDayStatuses,
                            medicalLeaveRepository = localDataStore.medicalLeaves,
                            clock = Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), AppDefaults.zoneId()),
                            uuidProvider = UuidProvider { generatedIds.removeFirst() },
                            savedStateHandle = SavedStateHandle(),
                        ) as T
                    },
                )[ManagementViewModel::class.java]
            }

            val month = YearMonth.of(2026, 8)
            val selectedDates = linkedSetOf(month.atDay(4), month.atDay(11))
            instrumentation.runOnMainSync {
                viewModel.openAddShift(month, selectedDates)
                viewModel.updateShiftPosition("Borrador ficticio")
                viewModel.openObjectiveForm(null)
            }
            assertEquals(ManagementSurface.OBJECTIVE_FORM, viewModel.uiState.value.surface)
            assertEquals(ManagementSurface.SHIFT_FORM, viewModel.uiState.value.formReturnSurface)
            assertEquals(selectedDates, viewModel.uiState.value.shiftDraft?.selectedDates)
            assertEquals("Borrador ficticio", viewModel.uiState.value.shiftDraft?.position)

            instrumentation.runOnMainSync {
                viewModel.updateObjectiveDraft {
                    it.copy(fullName = "Objetivo de prueba", abbreviation = "PRB")
                }
                viewModel.saveObjective()
            }
            val afterObjective = runBlocking {
                withTimeout(5_000L) {
                    viewModel.uiState.first { state ->
                        state.surface == ManagementSurface.SHIFT_FORM &&
                            state.objectives.any { it.id == OBJECTIVE_ID }
                    }
                }
            }
            assertEquals(selectedDates, afterObjective.shiftDraft?.selectedDates)
            assertEquals("Borrador ficticio", afterObjective.shiftDraft?.position)
            assertNull(afterObjective.shiftDraft?.combinationId)

            instrumentation.runOnMainSync {
                viewModel.openScheduleForm(OBJECTIVE_ID, null)
            }
            assertEquals(ManagementSurface.SCHEDULE_FORM, viewModel.uiState.value.surface)
            assertEquals(ManagementSurface.SHIFT_FORM, viewModel.uiState.value.formReturnSurface)
            assertEquals(OBJECTIVE_ID, viewModel.uiState.value.scheduleDraft.objectiveId)
            assertEquals(selectedDates, viewModel.uiState.value.shiftDraft?.selectedDates)

            instrumentation.runOnMainSync {
                viewModel.updateScheduleDraft {
                    it.copy(startTime = "18:30", endTime = "06:30", colorArgb = 0xFF315DA8.toInt())
                }
                viewModel.saveSchedule()
            }
            val afterSchedule = runBlocking {
                withTimeout(5_000L) {
                    viewModel.uiState.first { state ->
                        state.surface == ManagementSurface.SHIFT_FORM &&
                            state.shiftDraft?.combinationId == SCHEDULE_ID &&
                            state.scheduleOptions.any { it.combination.id == SCHEDULE_ID }
                    }
                }
            }
            assertEquals(selectedDates, afterSchedule.shiftDraft?.selectedDates)
            assertEquals("Borrador ficticio", afterSchedule.shiftDraft?.position)
            assertEquals(SCHEDULE_ID, afterSchedule.shiftDraft?.combinationId)
            assertNotNull(afterSchedule.scheduleOptions.singleOrNull { it.combination.id == SCHEDULE_ID })
        } finally {
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
            localDataStore.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun restoredNestedSetupFormsDiscardBackToInitialPreparation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val databaseName = "management-restored-return-${UUID.randomUUID()}.db"
        val localDataStore = LocalDataStore.create(context, databaseName)

        try {
            listOf(ManagementSurface.OBJECTIVE_FORM, ManagementSurface.SCHEDULE_FORM).forEach { restoredSurface ->
                val owner = object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
                val savedStateHandle = SavedStateHandle(
                    mapOf(
                        "management.surface" to restoredSurface.name,
                        "management.formReturnSurface" to ManagementSurface.INITIAL_DATA_PREPARATION.name,
                    ),
                )
                lateinit var viewModel: ManagementViewModel

                instrumentation.runOnMainSync {
                    viewModel = ViewModelProvider(
                        owner,
                        object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T = ManagementViewModel(
                                objectiveRepository = localDataStore.objectives,
                                scheduleRepository = localDataStore.scheduleCombinations,
                                shiftRepository = localDataStore.shifts,
                                explicitDayStatusRepository = localDataStore.explicitDayStatuses,
                                medicalLeaveRepository = localDataStore.medicalLeaves,
                                clock = Clock.fixed(
                                    Instant.parse("2026-08-18T12:00:00Z"),
                                    AppDefaults.zoneId(),
                                ),
                                uuidProvider = UuidProvider(UUID::randomUUID),
                                savedStateHandle = savedStateHandle,
                            ) as T
                        },
                    )[ManagementViewModel::class.java]
                }

                assertEquals(restoredSurface, viewModel.uiState.value.surface)
                assertEquals(
                    ManagementSurface.INITIAL_DATA_PREPARATION,
                    viewModel.uiState.value.formReturnSurface,
                )
                instrumentation.runOnMainSync { viewModel.discardCurrentForm() }
                assertEquals(ManagementSurface.INITIAL_DATA_PREPARATION, viewModel.uiState.value.surface)
                assertEquals(ManagementSurface.NONE, viewModel.uiState.value.formReturnSurface)
                assertEquals(
                    ManagementSurface.INITIAL_DATA_PREPARATION.name,
                    savedStateHandle.get<String>("management.surface"),
                )
                assertNull(savedStateHandle.get<String>("management.formReturnSurface"))
                instrumentation.runOnMainSync { owner.viewModelStore.clear() }
            }
        } finally {
            localDataStore.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun restoredInlineFormsWithoutDraftFallBackToCalendarState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val databaseName = "management-restored-inline-${UUID.randomUUID()}.db"
        val localDataStore = LocalDataStore.create(context, databaseName)

        try {
            listOf(ManagementSurface.SHIFT_FORM, ManagementSurface.DAY_OFF_FORM).forEach { restoredSurface ->
                val owner = object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
                val savedStateHandle = SavedStateHandle(
                    mapOf(
                        "management.surface" to restoredSurface.name,
                        "management.formReturnSurface" to ManagementSurface.INITIAL_DATA_PREPARATION.name,
                    ),
                )
                lateinit var viewModel: ManagementViewModel

                instrumentation.runOnMainSync {
                    viewModel = ViewModelProvider(
                        owner,
                        object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T = ManagementViewModel(
                                objectiveRepository = localDataStore.objectives,
                                scheduleRepository = localDataStore.scheduleCombinations,
                                shiftRepository = localDataStore.shifts,
                                explicitDayStatusRepository = localDataStore.explicitDayStatuses,
                                medicalLeaveRepository = localDataStore.medicalLeaves,
                                clock = Clock.fixed(
                                    Instant.parse("2026-08-18T12:00:00Z"),
                                    AppDefaults.zoneId(),
                                ),
                                uuidProvider = UuidProvider(UUID::randomUUID),
                                savedStateHandle = savedStateHandle,
                            ) as T
                        },
                    )[ManagementViewModel::class.java]
                }

                assertEquals(ManagementSurface.NONE, viewModel.uiState.value.surface)
                assertEquals(ManagementSurface.NONE, viewModel.uiState.value.formReturnSurface)
                assertNull(viewModel.uiState.value.shiftDraft)
                assertNull(viewModel.uiState.value.dayOffDraft)
                assertEquals(
                    ManagementSurface.NONE.name,
                    savedStateHandle.get<String>("management.surface"),
                )
                assertNull(savedStateHandle.get<String>("management.formReturnSurface"))
                instrumentation.runOnMainSync { owner.viewModelStore.clear() }
            }
        } finally {
            localDataStore.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun setupSavesUseTheReturnTargetCapturedBeforeRepositorySuspension() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val databaseName = "management-return-race-${UUID.randomUUID()}.db"
        val localDataStore = LocalDataStore.create(context, databaseName)
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val generatedIds = ArrayDeque(listOf(RACE_OBJECTIVE_ID, RACE_SCHEDULE_ID))
        val objectiveCreateStarted = CompletableDeferred<Unit>()
        val continueObjectiveCreate = CompletableDeferred<Unit>()
        val scheduleCreateStarted = CompletableDeferred<Unit>()
        val continueScheduleCreate = CompletableDeferred<Unit>()
        val objectiveRepository = object : ObjectiveRepository by localDataStore.objectives {
            override suspend fun create(objective: Objective) {
                objectiveCreateStarted.complete(Unit)
                continueObjectiveCreate.await()
                localDataStore.objectives.create(objective)
            }
        }
        val scheduleRepository = object : ScheduleCombinationRepository by localDataStore.scheduleCombinations {
            override suspend fun create(combination: ScheduleCombination) {
                scheduleCreateStarted.complete(Unit)
                continueScheduleCreate.await()
                localDataStore.scheduleCombinations.create(combination)
            }
        }
        lateinit var viewModel: ManagementViewModel

        try {
            instrumentation.runOnMainSync {
                viewModel = ViewModelProvider(
                    owner,
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = ManagementViewModel(
                            objectiveRepository = objectiveRepository,
                            scheduleRepository = scheduleRepository,
                            shiftRepository = localDataStore.shifts,
                            explicitDayStatusRepository = localDataStore.explicitDayStatuses,
                            medicalLeaveRepository = localDataStore.medicalLeaves,
                            clock = Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), AppDefaults.zoneId()),
                            uuidProvider = UuidProvider { generatedIds.removeFirst() },
                            savedStateHandle = SavedStateHandle(),
                        ) as T
                    },
                )[ManagementViewModel::class.java]
                viewModel.openInitialDataPreparation()
                viewModel.openObjectiveForm(null)
                viewModel.updateObjectiveDraft {
                    it.copy(fullName = "Objetivo de carrera", abbreviation = "CAR")
                }
                viewModel.saveObjective()
                viewModel.closeSurface()
                assertEquals(ManagementSurface.NONE, viewModel.uiState.value.surface)
            }
            runBlocking {
                withTimeout(5_000L) { objectiveCreateStarted.await() }
                continueObjectiveCreate.complete(Unit)
            }

            runBlocking {
                withTimeout(5_000L) {
                    viewModel.uiState.first { state ->
                        state.surface == ManagementSurface.INITIAL_DATA_PREPARATION &&
                            state.objectives.any { it.id == RACE_OBJECTIVE_ID }
                    }
                }
            }

            instrumentation.runOnMainSync {
                viewModel.openScheduleForm(RACE_OBJECTIVE_ID, null)
                viewModel.updateScheduleDraft { it.copy(startTime = "06:00", endTime = "14:00") }
                viewModel.saveSchedule()
                viewModel.closeSurface()
                assertEquals(ManagementSurface.NONE, viewModel.uiState.value.surface)
            }
            runBlocking {
                withTimeout(5_000L) { scheduleCreateStarted.await() }
                continueScheduleCreate.complete(Unit)
            }

            val completed = runBlocking {
                withTimeout(5_000L) {
                    viewModel.uiState.first { state ->
                        state.surface == ManagementSurface.INITIAL_DATA_PREPARATION &&
                            state.scheduleOptions.any { it.combination.id == RACE_SCHEDULE_ID }
                    }
                }
            }
            assertNull(completed.shiftDraft)
            assertNull(completed.dayOffDraft)
        } finally {
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
            localDataStore.close()
            context.deleteDatabase(databaseName)
        }
    }

    private companion object {
        val OBJECTIVE_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000001")
        val SCHEDULE_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000002")
        val FIRST_OBJECTIVE_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000011")
        val FIRST_SCHEDULE_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000012")
        val FIRST_SECOND_SCHEDULE_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000015")
        val SECOND_OBJECTIVE_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000013")
        val SECOND_SCHEDULE_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000014")
        val RACE_OBJECTIVE_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000021")
        val RACE_SCHEDULE_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000022")
    }
}
