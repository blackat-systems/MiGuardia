package com.blackatsystems.miguardia.stage12

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.blackatsystems.miguardia.core.domain.model.AvailabilityActiveWorkInterval
import com.blackatsystems.miguardia.core.domain.model.AvailabilityActiveWorkKind
import com.blackatsystems.miguardia.core.domain.model.AvailabilityCalculationSnapshot
import com.blackatsystems.miguardia.core.domain.model.AvailabilitySourceVersion
import com.blackatsystems.miguardia.core.domain.model.AvailabilityTemporalState
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowExpectation
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowMutation
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowWriteResult
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.repository.AvailabilityWindowRepository
import com.blackatsystems.miguardia.core.domain.repository.IndependentExtraWorkRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.ui.availability.AvailabilityLoadState
import com.blackatsystems.miguardia.ui.availability.AvailabilitySurface
import com.blackatsystems.miguardia.ui.availability.AvailabilityViewModel
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real coordinator, with observation and transaction responses controlled independently of each other. */
@OptIn(ExperimentalCoroutinesApi::class)
class Stage12AvailabilityReviewRegressionTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun before() = Dispatchers.setMain(dispatcher)

    @After fun after() = Dispatchers.resetMain()

    @Test fun correctionShowsCandidateMinutesForTheSameUuid() = runAvailabilityTest {
        val original = storedWindow()
        val repository = ControlledWindows(original)
        val viewModel = model(repository)
        val source = requireNotNull(viewModel.uiState.value.source)
        assertEquals(60L, source.breakdowns.getValue(original.id).programmedMinutes)

        editEnd(viewModel, original, "11:00")
        viewModel.reviewWindow()

        val review = requireNotNull(viewModel.uiState.value.windowReview)
        assertEquals(original.id, review.record.id)
        assertEquals(Instant.parse("2026-10-24T11:00:00Z"), review.record.end)
        assertEquals(180L, review.record.durationMinutes)
        Stage12AvailabilityOracle.requireExact(review.breakdown, completed(programmed = 180))
        assertEquals(original, repository.stored)
        assertEquals(0, repository.attempts)
    }

    @Test fun protectionOnOverlappingShiftOwnerDoesNotProtectCandidateWindow() = runAvailabilityTest {
        val shift = priorNightShift()
        val prior = OWNER.minusDays(1)
        val leave = MedicalLeave(UUID(1, 5), prior, prior, null, STAMP, STAMP)
        val repository = ControlledWindows(null, shift, leave)
        val viewModel = model(repository, listOf(shift), listOf(leave))
        assertEquals(emptyList<Any>(), requireNotNull(viewModel.uiState.value.source).activeWork)

        viewModel.openCreate(OWNER)
        val draft = requireNotNull(viewModel.uiState.value.windowDraft)
        viewModel.updateWindowDraft(draft.copy(startTime = "00:00", endTime = "04:00"))
        viewModel.reviewWindow()

        val review = requireNotNull(viewModel.uiState.value.windowReview)
        assertTrue(review.expectation.protectionFingerprint.isNotEmpty())
        assertEquals(OWNER, review.record.ownerLocalDate)
        assertFalse("La licencia corresponde a la fecha dueña de la jornada anterior", review.isProtected)
        Stage12AvailabilityOracle.requireExact(review.breakdown, completed(programmed = 240))
        assertEquals(0, repository.attempts)
    }

    @Test fun reviewUsesTransactionalWorkWhenTheObservationStillHasNoWork() = runAvailabilityTest {
        val occupied = AvailabilityActiveWorkInterval(
            key = "shift:${UUID(3, 1)}",
            kind = AvailabilityActiveWorkKind.SHIFT_PLANNED,
            start = Instant.parse("2026-10-24T09:00:00Z"),
            end = Instant.parse("2026-10-24T11:00:00Z"),
        )
        val repository = ControlledWindows(null, capturedWork = listOf(occupied))
        val viewModel = model(repository)
        viewModel.openCreate(OWNER)

        viewModel.reviewWindow()

        val state = viewModel.uiState.value
        assertTrue(requireNotNull(state.source?.activeWork).isEmpty())
        val review = requireNotNull(state.windowReview)
        assertEquals(listOf(occupied), requireNotNull(review.expectation.calculation).activeWork)
        assertEquals(Instant.parse("2026-10-24T08:00:00Z"), review.record.start)
        assertEquals(Instant.parse("2026-10-24T12:00:00Z"), review.record.end)
        Stage12AvailabilityOracle.requireExact(
            review.breakdown,
            Stage12AvailabilityOracle.Expected(
                state = AvailabilityTemporalState.COMPLETED,
                programmed = 240,
                effective = 120,
                replaced = 120,
                pending = 0,
                projected = 120,
                futureReplaced = 0,
            ),
        )
        assertEquals(1, repository.captures)
        assertEquals(0, repository.attempts)
        assertNull(repository.stored)
    }

    @Test fun competitorBeforeReviewMustNotBeAdoptedFromANewerTransaction() = runAvailabilityTest {
        val original = storedWindow()
        val repository = ControlledWindows(original)
        val viewModel = model(repository)
        editEnd(viewModel, original, "12:00")
        val draft = viewModel.uiState.value.windowDraft
        val winner = original.copy(end = Instant.parse("2026-10-24T10:00:00Z"), updatedAt = STAMP.plusMillis(1))
        repository.stored = winner
        assertEquals(original, requireNotNull(viewModel.uiState.value.source).windows.single())

        viewModel.reviewWindow()
        viewModel.saveWindow()

        assertEquals(1, repository.captures)
        assertEquals("Una revisión fallida no debe autorizar un intento de guardado", 0, repository.attempts)
        assertEquals(winner, repository.stored)
        assertEquals(AvailabilitySurface.WINDOW_EDITOR, viewModel.uiState.value.surface)
        assertEquals(draft, viewModel.uiState.value.windowDraft)
        assertNull(viewModel.uiState.value.windowReview)
        assertNotNull(viewModel.uiState.value.message)
    }

    @Test fun competitorAfterReviewLeavesTheDraftAndDoesNotRetryTheConflict() = runAvailabilityTest {
        val original = storedWindow()
        val repository = ControlledWindows(original)
        val viewModel = model(repository)
        editEnd(viewModel, original, "12:00")
        val draft = viewModel.uiState.value.windowDraft
        viewModel.reviewWindow()
        val review = requireNotNull(viewModel.uiState.value.windowReview)
        assertEquals(original, review.expectation.previous)
        val winner = original.copy(end = Instant.parse("2026-10-24T10:00:00Z"), updatedAt = STAMP.plusMillis(1))
        repository.stored = winner

        viewModel.saveWindow()

        assertEquals(1, repository.captures)
        assertEquals(1, repository.attempts)
        assertEquals(winner, repository.stored)
        assertEquals(draft, viewModel.uiState.value.windowDraft)
        assertEquals(AvailabilitySurface.WINDOW_EDITOR, viewModel.uiState.value.surface)
        assertNull(viewModel.uiState.value.windowReview)
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.message.orEmpty().contains("Los datos cambiaron"))
    }

    private fun runAvailabilityTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            // Stop the ViewModel's minute pulse before runTest drains its scheduler.
            stores.forEach(ViewModelStore::clear)
        }
    }

    private fun editEnd(viewModel: AvailabilityViewModel, original: AvailabilityWindowRecord, endTime: String) {
        viewModel.openCorrect(original)
        val draft = requireNotNull(viewModel.uiState.value.windowDraft)
        viewModel.updateWindowDraft(draft.copy(endTime = endTime))
    }

    private suspend fun model(
        windows: ControlledWindows,
        shifts: List<V2ShiftWrite> = emptyList(),
        medical: List<MedicalLeave> = emptyList(),
    ): AvailabilityViewModel = AvailabilityViewModel(
        configurationRepository = observation<WorkConfigurationRepository>("observe", flowOf(HISTORY)),
        repository = windows,
        shiftRepository = observation<V2ShiftRepository>("observeAll", flowOf(shifts)),
        shiftActualRepository = observation<ShiftActualRepository>("observeAllActuals", flowOf(emptyMap<UUID, Nothing>())),
        independentExtraRepository = observation<IndependentExtraWorkRepository>("observeAll", flowOf(emptyList<Nothing>())),
        medicalLeaveRepository = observation<MedicalLeaveRepository>("observeIntersecting", flowOf(medical)),
        vacationRepository = observation<VacationRepository>("observeOverlapping", flowOf(emptyList<Nothing>())),
        clock = Clock.fixed(Instant.parse("2026-10-25T16:00:00Z"), ZoneOffset.UTC),
        zoneId = ZoneOffset.UTC,
        uuidProvider = { WINDOW },
        savedStateHandle = SavedStateHandle(),
    ).also { viewModel ->
        stores += ViewModelStore().also { it.put("availability", viewModel) }
        val firstLoad = withTimeout(5_000) {
            viewModel.uiState.first { it.loadState != AvailabilityLoadState.LOADING }
        }
        assertEquals(AvailabilityLoadState.CONTENT, firstLoad.loadState)
    }

    private inline fun <reified T : Any> observation(name: String, flow: Flow<*>): T {
        val type = T::class.java
        val proxy = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            check(method.name == name) { "Llamada no prevista: ${type.simpleName}.${method.name}" }
            flow
        }
        return requireNotNull(type.cast(proxy))
    }

    private class ControlledWindows(
        initial: AvailabilityWindowRecord?,
        private val shift: V2ShiftWrite? = null,
        private val leave: MedicalLeave? = null,
        private val capturedWork: List<AvailabilityActiveWorkInterval> = emptyList(),
    ) : AvailabilityWindowRepository {
        private val observed = MutableStateFlow(listOfNotNull(initial))
        var stored = initial
        var captures = 0
            private set
        var attempts = 0
            private set

        override fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<AvailabilityWindowRecord>> = observed

        override fun observeOn(
            timelineId: UUID,
            sector: WorkSector,
            ownerLocalDate: LocalDate,
        ): Flow<List<AvailabilityWindowRecord>> = error("Este recorrido observa la línea completa")

        override suspend fun get(id: UUID): AvailabilityWindowRecord? = stored?.takeIf { it.id == id }

        override suspend fun captureExpectation(
            id: UUID?,
            configuration: ResolvedWorkConfigurationRevision,
            windowStart: Instant,
            windowEnd: Instant,
        ): AvailabilityWindowExpectation {
            captures++
            val active = shift?.shift?.let { planned ->
                AvailabilitySourceVersion("shift:${planned.id}", planned.startAt, planned.endAt, "planned-v1")
            }
            val capturedVersions = capturedWork.map { work ->
                AvailabilitySourceVersion(work.key, work.start, work.end, "current-work-v1")
            }
            val protection = leave?.let { "medical:${it.id}:${it.startDate}:${it.endDateInclusive}:${it.updatedAt.toEpochMilli()}" }.orEmpty()
            return AvailabilityWindowExpectation.capture(
                previous = stored?.takeIf { it.id == id },
                configuration = configuration,
                observedStart = windowStart,
                observedEnd = windowEnd,
                observedWindows = emptyList(),
                observedActiveSources = listOfNotNull(active) + capturedVersions,
                protectionFingerprint = protection,
                calculation = AvailabilityCalculationSnapshot.capture(capturedWork, ownerProtected = false),
            )
        }

        override suspend fun applyMutation(mutation: AvailabilityWindowMutation): AvailabilityWindowWriteResult {
            attempts++
            if (mutation.expectation.previous != stored) return AvailabilityWindowWriteResult.Conflict
            val replacement = requireNotNull(mutation.replacement)
            stored = replacement
            return AvailabilityWindowWriteResult.Saved(replacement)
        }
    }

    private fun completed(programmed: Long) = Stage12AvailabilityOracle.Expected(
        state = AvailabilityTemporalState.COMPLETED,
        programmed = programmed,
        effective = programmed,
        replaced = 0,
        pending = 0,
        projected = programmed,
        futureReplaced = 0,
    )

    private fun storedWindow() = AvailabilityWindowRecord(
        id = WINDOW,
        timelineId = TIMELINE,
        sector = WorkSector.NURSING,
        configurationRevisionId = REVISION,
        ownerLocalDate = OWNER,
        zoneId = ZoneOffset.UTC,
        start = Instant.parse("2026-10-24T08:00:00Z"),
        end = Instant.parse("2026-10-24T09:00:00Z"),
        labelSnapshot = "Guardia pasiva",
        createdAt = STAMP,
        updatedAt = STAMP,
    )

    private fun priorNightShift(): V2ShiftWrite {
        val id = UUID(2, 1)
        val objective = UUID(2, 2)
        return V2ShiftWrite(
            shift = Shift(
                id = id,
                startAt = Instant.parse("2026-10-23T22:00:00Z"),
                endAt = Instant.parse("2026-10-24T02:00:00Z"),
                zoneId = ZoneOffset.UTC,
                localStartDate = OWNER.minusDays(1),
                objectiveNameSnapshot = "Objetivo",
                objectiveAbbreviationSnapshot = "OBJ",
                objectiveAddressSnapshot = null,
                startTimeSnapshot = LocalTime.of(22, 0),
                endTimeSnapshot = LocalTime.of(2, 0),
                colorArgbSnapshot = 0,
                position = null,
                status = ShiftStatus.PLANNED,
                sourceObjectiveId = objective,
                createdAt = STAMP,
                updatedAt = STAMP,
            ),
            snapshot = ShiftWorkSnapshot(
                shiftId = id,
                timelineId = TIMELINE,
                sector = WorkSector.NURSING,
                configurationRevisionId = REVISION,
                workPlaceId = UUID(2, 3),
                objectiveId = objective,
                templateId = UUID(2, 4),
                workTypeId = UUID(2, 5),
                workTypeNameSnapshot = "Trabajo",
                workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
            ),
        )
    }

    private companion object {
        val OWNER: LocalDate = LocalDate.of(2026, 10, 24)
        val STAMP: Instant = Instant.parse("2026-10-20T12:00:00Z")
        val WINDOW = UUID(1, 1)
        val TIMELINE = UUID(1, 2)
        val REVISION = UUID(1, 3)
        val HISTORY = WorkConfigurationHistory(
            EffectiveDateTimeline(
                TIMELINE,
                listOf(EffectiveRevision(
                    REVISION,
                    LocalDate.of(2026, 1, 1),
                    WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, AvailabilityLabel.PASSIVE_GUARD),
                )),
            ),
            PerPeriodHoursValues(emptyList()),
        )
    }
}
