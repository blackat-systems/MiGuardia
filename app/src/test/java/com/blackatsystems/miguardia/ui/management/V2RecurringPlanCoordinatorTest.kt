package com.blackatsystems.miguardia.ui.management

import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.RecurringMedicalLeaveVersion
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrence
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanAggregate
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanExpectation
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanMutation
import com.blackatsystems.miguardia.core.domain.model.RecurringProtectionExpectation
import com.blackatsystems.miguardia.core.domain.model.RecurringShiftProtectionVersion
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWriteExpectation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.RecurringPlanRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.V2RecurringShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkCatalogRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.shift.RecurringConflictPolicy
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.FirstWorkSet
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.NewV2Backfill
import com.blackatsystems.miguardia.core.domain.work.NewWorkPlace
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.RecentWorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkPlaceUpdate
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkTemplateUpdate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkTypeUpdate
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRules
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V2RecurringPlanCoordinatorTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach(CoroutineScope::cancel)
    }

    @Test
    fun everyPatternRecalculatesAnExactFinitePreviewWithoutWriting() {
        val harness = harness()
        harness.coordinator.openCreate(FIXTURE.ready)
        harness.coordinator.updateEndDate(TODAY.plusMonths(2).toString())

        val cases = listOf(
            V2RecurringPatternKind.WEEKDAYS to 9,
            V2RecurringPatternKind.EVERY_N_DAYS to 21,
            V2RecurringPatternKind.EVERY_N_WEEKS to 5,
            V2RecurringPatternKind.MONTHLY to 2,
        )
        cases.forEach { (kind, minimumDates) ->
            harness.coordinator.selectPattern(kind)
            when (kind) {
                V2RecurringPatternKind.WEEKDAYS -> Unit
                V2RecurringPatternKind.EVERY_N_DAYS -> harness.coordinator.updateInterval("3")
                V2RecurringPatternKind.EVERY_N_WEEKS -> harness.coordinator.updateInterval("2")
                V2RecurringPatternKind.MONTHLY -> {
                    harness.coordinator.selectMonthlyOrdinal(com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal.FIRST)
                    harness.coordinator.selectMonthlyDay(DayOfWeek.SUNDAY)
                }
            }
            harness.coordinator.review()
            val preview = requireNotNull(harness.coordinator.uiState.value.preview)
            assertTrue(preview.dates.size >= minimumDates)
            assertEquals(preview.dates.distinct().sorted(), preview.dates)
        }

        assertTrue(harness.store.mutations.isEmpty())
    }

    @Test
    fun invalidDatesAndMissingActiveTemplateKeepTheDraftAndNeverWrite() {
        val invalid = harness()
        invalid.coordinator.openCreate(FIXTURE.ready)
        invalid.coordinator.updateStartDate("fecha inválida")
        invalid.coordinator.review()
        assertEquals(V2RecurringStage.FORM, invalid.coordinator.uiState.value.stage)
        assertTrue(invalid.coordinator.uiState.value.errorMessage.orEmpty().contains("AAAA-MM-DD"))
        assertTrue(invalid.store.mutations.isEmpty())

        val archivedFixture = FIXTURE.copy(
            catalog = FIXTURE.catalog.copy(
                workTemplates = listOf(FIXTURE.template.copy(isActive = false)),
            ),
        )
        val archived = harness(archivedFixture)
        archived.coordinator.openCreate(archivedFixture.ready)
        assertTrue(archived.coordinator.uiState.value.templateOptions.isEmpty())
        assertNotNull(archived.coordinator.uiState.value.errorMessage)
        archived.coordinator.review()
        assertTrue(archived.store.mutations.isEmpty())
    }

    @Test
    fun listAndEditorSourceFailuresExposeARealRetryWithoutInventingEmptyData() {
        val plansHarness = harness()
        plansHarness.store.observePlansFailure = IllegalStateException("Lectura de planes interrumpida")
        plansHarness.coordinator.openPlans(FIXTURE.ready)

        assertEquals(V2RecurringStage.PLANS, plansHarness.coordinator.uiState.value.stage)
        assertFalse(plansHarness.coordinator.uiState.value.plansReadSuccessfully)
        assertTrue(plansHarness.coordinator.uiState.value.canRetry)
        assertEquals("Lectura de planes interrumpida", plansHarness.coordinator.uiState.value.errorMessage)

        plansHarness.store.observePlansFailure = null
        plansHarness.coordinator.retry()
        assertTrue(plansHarness.coordinator.uiState.value.plansReadSuccessfully)
        assertFalse(plansHarness.coordinator.uiState.value.canRetry)
        assertNull(plansHarness.coordinator.uiState.value.errorMessage)

        val sourcesHarness = harness()
        sourcesHarness.catalog.observeFailure = IllegalStateException("Catálogo temporalmente inaccesible")
        sourcesHarness.coordinator.openCreate(FIXTURE.ready)

        assertEquals(V2RecurringStage.FORM, sourcesHarness.coordinator.uiState.value.stage)
        assertTrue(sourcesHarness.coordinator.uiState.value.canRetry)
        assertTrue(sourcesHarness.coordinator.uiState.value.templateOptions.isEmpty())

        sourcesHarness.catalog.observeFailure = null
        sourcesHarness.coordinator.retry()
        assertFalse(sourcesHarness.coordinator.uiState.value.canRetry)
        assertNull(sourcesHarness.coordinator.uiState.value.errorMessage)
        assertEquals(FIXTURE.template.id, sourcesHarness.coordinator.uiState.value.selectedTemplateId)
    }

    @Test
    fun finalizationLoadFailureCanRetryAndReachAFreshPreview() {
        val harness = harness()
        harness.coordinator.openCreate(FIXTURE.ready)
        harness.coordinator.review()
        harness.coordinator.save()
        val planId = harness.store.plans.value.single().plan.id
        harness.store.getPlanFailure = IllegalStateException("Plan temporalmente inaccesible")

        harness.coordinator.finalizeFrom(planId, TODAY.plusDays(1))

        assertEquals(V2RecurringStage.FORM, harness.coordinator.uiState.value.stage)
        assertTrue(harness.coordinator.uiState.value.canRetry)
        assertEquals("Plan temporalmente inaccesible", harness.coordinator.uiState.value.errorMessage)

        harness.store.getPlanFailure = null
        harness.coordinator.retry()
        assertEquals(V2RecurringStage.PREVIEW, harness.coordinator.uiState.value.stage)
        assertFalse(harness.coordinator.uiState.value.canRetry)
        assertNotNull(harness.coordinator.uiState.value.preview)
    }

    @Test
    fun intervalInputIsNeverSilentlyTruncated() {
        val harness = harness()
        val unsupportedInteger = "2147483648"
        harness.coordinator.openCreate(FIXTURE.ready)
        harness.coordinator.selectPattern(V2RecurringPatternKind.EVERY_N_DAYS)
        harness.coordinator.updateInterval(unsupportedInteger)

        assertEquals(unsupportedInteger, harness.coordinator.uiState.value.intervalText)
        harness.coordinator.review()

        assertEquals(V2RecurringStage.FORM, harness.coordinator.uiState.value.stage)
        assertTrue(harness.coordinator.uiState.value.errorMessage.orEmpty().contains("entero positivo"))
        assertTrue(harness.store.mutations.isEmpty())
    }

    @Test
    fun createAcrossMonthsWritesOnlyAfterPreviewAndDoubleTapIsOneAtomicCall() {
        val harness = harness()
        harness.coordinator.openCreate(FIXTURE.ready)
        harness.coordinator.selectPattern(V2RecurringPatternKind.EVERY_N_WEEKS)
        harness.coordinator.updateInterval("1")
        harness.coordinator.updateEndDate(TODAY.plusMonths(1).toString())
        harness.coordinator.review()
        val exactDates = requireNotNull(harness.coordinator.uiState.value.preview).dates
        assertTrue(exactDates.map { it.month }.distinct().size > 1)
        assertTrue(harness.store.mutations.isEmpty())
        harness.store.gate = CompletableDeferred()

        harness.coordinator.save()
        harness.coordinator.save()
        assertEquals(1, harness.store.calls)
        harness.store.gate?.complete(Unit)

        assertEquals(1, harness.store.mutations.size)
        assertEquals(V2RecurringStage.IDLE, harness.coordinator.uiState.value.stage)
        assertEquals(exactDates.size, harness.shifts.values.value.size)
        assertEquals(exactDates, harness.shifts.values.value.map(Shift::localStartDate))
    }

    @Test
    fun everyGeneratedDateUsesItsExactApplicableConfigurationRevision() {
        val secondRevision = EffectiveRevision(
            id = UUID(0L, 900L),
            effectiveFrom = TODAY.plusDays(5),
            value = WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, null),
        )
        val history = FIXTURE.history.copy(
            timeline = EffectiveDateTimeline(
                TIMELINE_ID,
                FIXTURE.history.timeline.revisions + secondRevision,
            ),
        )
        val harness = harness(FIXTURE.copy(history = history))
        harness.coordinator.openCreate(FIXTURE.ready)
        harness.coordinator.selectPattern(V2RecurringPatternKind.EVERY_N_DAYS)
        harness.coordinator.updateInterval("5")
        harness.coordinator.updateEndDate(TODAY.plusDays(10).toString())
        harness.coordinator.review()
        harness.coordinator.save()

        val occurrences = harness.store.plans.value.single().occurrences.sortedBy(RecurringOccurrence::localDate)
        val revisionIds = occurrences.map { occurrence ->
            harness.store.requireWrite(requireNotNull(occurrence.shiftId)).snapshot.configurationRevisionId
        }
        assertEquals(
            listOf(FIXTURE.ready.configurationRevision.id, secondRevision.id, secondRevision.id),
            revisionIds,
        )
    }

    @Test
    fun concurrentConflictKeepsDraftRequiresFreshReviewAndThenRetries() {
        val harness = harness()
        harness.coordinator.openCreate(FIXTURE.ready)
        harness.coordinator.updatePosition("Puesto recuperable")
        harness.coordinator.review()
        harness.store.failure = ConflictingLocalWriteException("Cambio concurrente ficticio")

        harness.coordinator.save()

        val conflicted = harness.coordinator.uiState.value
        assertEquals(V2RecurringStage.FORM, conflicted.stage)
        assertNull(conflicted.preview)
        assertTrue(conflicted.canRetry)
        assertEquals("Puesto recuperable", conflicted.position)
        assertEquals("Cambio concurrente ficticio", conflicted.errorMessage)
        assertEquals(1, harness.store.calls)
        assertTrue(harness.store.mutations.isEmpty())

        harness.store.failure = null
        harness.coordinator.save()
        assertEquals(1, harness.store.calls)
        harness.coordinator.retry()
        assertNotNull(harness.coordinator.uiState.value.preview)
        harness.coordinator.save()
        assertEquals(2, harness.store.calls)
        assertEquals(1, harness.store.mutations.size)
    }

    @Test
    fun midnightAfterPreviewRequiresFreshReviewAndNeverWritesTheNowPastStart() {
        val clock = MutableRecurringClock(NOW, ZoneOffset.UTC)
        val harness = harness(clock = clock)
        harness.coordinator.openCreate(FIXTURE.ready)
        harness.coordinator.review()
        assertEquals(V2RecurringStage.PREVIEW, harness.coordinator.uiState.value.stage)

        clock.current = NOW.plusSeconds(86_400)
        harness.coordinator.save()

        assertEquals(0, harness.store.calls)
        assertTrue(harness.store.mutations.isEmpty())
        assertEquals(V2RecurringStage.FORM, harness.coordinator.uiState.value.stage)
        assertNull(harness.coordinator.uiState.value.preview)
        assertTrue(harness.coordinator.uiState.value.canRetry)
        assertTrue(harness.coordinator.uiState.value.errorMessage.orEmpty().contains("pasado"))
        harness.coordinator.save()
        assertEquals(0, harness.store.calls)
    }

    @Test
    fun recreationRestoresDraftButRebuildsPreviewWithoutPersistingOldCas() {
        var persisted = V2RecurringPersistedState()
        val first = harness(persist = { persisted = it })
        first.coordinator.openCreate(FIXTURE.ready)
        first.coordinator.updatePosition("Borrador recreado")
        first.coordinator.selectPattern(V2RecurringPatternKind.EVERY_N_DAYS)
        first.coordinator.updateInterval("4")
        first.coordinator.review()
        assertEquals(V2RecurringStage.PREVIEW, persisted.stage)

        val restored = harness(initial = persisted)
        restored.coordinator.resume(FIXTURE.ready)

        val state = restored.coordinator.uiState.value
        assertEquals(V2RecurringStage.PREVIEW, state.stage)
        assertEquals("Borrador recreado", state.position)
        assertEquals("4", state.intervalText)
        assertNotNull(state.preview)
        assertTrue(restored.store.mutations.isEmpty())
    }

    @Test
    fun recreationKeepsDiscardConfirmationAndNeverWritesWhileRehydrating() {
        var persisted = V2RecurringPersistedState()
        val first = harness(persist = { persisted = it })
        first.coordinator.openCreate(FIXTURE.ready)
        first.coordinator.updatePosition("Borrador por descartar")
        first.coordinator.back()
        assertEquals(V2RecurringStage.CONFIRM_DISCARD, persisted.stage)

        val restored = harness(initial = persisted)
        restored.coordinator.resume(FIXTURE.ready)

        assertEquals(V2RecurringStage.CONFIRM_DISCARD, restored.coordinator.uiState.value.stage)
        assertEquals("Borrador por descartar", restored.coordinator.uiState.value.position)
        assertTrue(restored.store.mutations.isEmpty())
        restored.coordinator.cancelDiscard()
        assertEquals(V2RecurringStage.FORM, restored.coordinator.uiState.value.stage)
    }

    @Test
    fun changeAndFinalizeAppendRevisionsWithFixedCutAndKeepConcreteObserversReactive() {
        val harness = harness()
        harness.coordinator.openCreate(FIXTURE.ready)
        harness.coordinator.selectPattern(V2RecurringPatternKind.EVERY_N_DAYS)
        harness.coordinator.updateInterval("2")
        harness.coordinator.updateEndDate(TODAY.plusDays(10).toString())
        harness.coordinator.review()
        harness.coordinator.save()
        val planId = harness.store.plans.value.single().plan.id
        val cut = TODAY.plusDays(4)

        harness.coordinator.changeFrom(planId, cut)
        assertEquals(cut, harness.coordinator.uiState.value.cutDate)
        harness.coordinator.updateStartDate(TODAY.plusDays(5).toString())
        assertEquals(cut.toString(), harness.coordinator.uiState.value.startDateText)
        harness.coordinator.updateInterval("3")
        harness.coordinator.review()
        assertEquals(
            harness.coordinator.uiState.value.errorMessage,
            V2RecurringStage.PREVIEW,
            harness.coordinator.uiState.value.stage,
        )
        assertTrue(
            harness.coordinator.uiState.value.errorMessage,
            harness.coordinator.uiState.value.preview?.canConfirm == true,
        )
        harness.coordinator.save()
        assertEquals(
            harness.coordinator.uiState.value.errorMessage,
            V2RecurringStage.IDLE,
            harness.coordinator.uiState.value.stage,
        )
        assertEquals(2, harness.store.plans.value.single().revisions.size)
        assertTrue(harness.store.plans.value.single().occurrences.none {
            it.localDate.isBefore(cut) && it.revisionId == harness.store.plans.value.single().latestRevision.id
        })

        harness.coordinator.finalizeFrom(planId, cut.plusDays(1))
        assertEquals(V2RecurringStage.PREVIEW, harness.coordinator.uiState.value.stage)
        harness.coordinator.save()
        val final = harness.store.plans.value.single()
        assertEquals(3, final.revisions.size)
        assertEquals(
            com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevisionKind.FINALIZED,
            final.latestRevision.kind,
        )
        assertTrue(harness.shifts.values.value.all { shift ->
            shift.localStartDate < cut.plusDays(1) ||
                final.occurrences.single { it.shiftId == shift.id }.state !=
                com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState.AUTOMATIC
        })
    }

    @Test
    fun backAndConfirmedDiscardNeverWrite() {
        val harness = harness()
        harness.coordinator.openCreate(FIXTURE.ready)
        harness.coordinator.review()
        harness.coordinator.back()
        assertEquals(V2RecurringStage.CONFIRM_DISCARD, harness.coordinator.uiState.value.stage)
        harness.coordinator.confirmDiscard()
        assertEquals(V2RecurringStage.IDLE, harness.coordinator.uiState.value.stage)
        assertTrue(harness.store.mutations.isEmpty())
    }

    private fun harness(
        fixture: Fixture = FIXTURE,
        initial: V2RecurringPersistedState = V2RecurringPersistedState(),
        persist: (V2RecurringPersistedState) -> Unit = {},
        medicalLeaves: List<MedicalLeave> = emptyList(),
        clock: Clock = CLOCK,
    ): Harness {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also(scopes::add)
        val configurations = FakeRecurringConfigurations(fixture.history)
        val catalog = FakeRecurringCatalog(fixture.catalog)
        val objectives = FakeRecurringObjectives(listOf(fixture.objective))
        val shifts = FakeRecurringShifts()
        val medical = FakeRecurringMedicalLeaves(medicalLeaves)
        val store = FakeRecurringStore(shifts, medical)
        var uuid = 1_000L
        val coordinator = V2RecurringPlanCoordinator(
            configurationRepository = configurations,
            catalogRepository = catalog,
            objectiveRepository = objectives,
            shiftRepository = shifts,
            medicalLeaveRepository = medical,
            recurringPlanRepository = store,
            recurringShiftRepository = store,
            clock = clock,
            zoneId = ZONE,
            uuidProvider = UuidProvider { UUID(0L, uuid++) },
            scope = scope,
            initialState = initial,
            persist = persist,
        )
        return Harness(coordinator, catalog, shifts, store)
    }

    private data class Harness(
        val coordinator: V2RecurringPlanCoordinator,
        val catalog: FakeRecurringCatalog,
        val shifts: FakeRecurringShifts,
        val store: FakeRecurringStore,
    )

    private data class Fixture(
        val history: WorkConfigurationHistory,
        val objective: Objective,
        val place: WorkPlace,
        val type: WorkType,
        val template: WorkTemplate,
        val catalog: WorkCatalog,
        val ready: WorkSetupState.V2Ready,
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 8, 23)
        val NOW: Instant = Instant.parse("2026-08-23T12:00:00Z")
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val TIMELINE_ID: UUID = UUID(0L, 100L)

        val FIXTURE: Fixture = run {
            val revision = EffectiveRevision(
                id = UUID(0L, 1L),
                effectiveFrom = LocalDate.of(2026, 8, 1),
                value = WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, null),
            )
            val history = WorkConfigurationHistory(
                EffectiveDateTimeline(TIMELINE_ID, listOf(revision)),
                PerPeriodHoursValues(emptyList()),
            )
            val objective = Objective(UUID(0L, 2L), "Lugar ficticio", "FIC", null, null, true, NOW, NOW)
            val place = WorkPlace(UUID(0L, 3L), TIMELINE_ID, WorkSector.NURSING, objective.id, true, NOW, NOW)
            val type = WorkType.create(UUID(0L, 4L), TIMELINE_ID, WorkSector.NURSING, "Trabajo habitual", NOW)
            val template = WorkTemplate(
                UUID(0L, 5L),
                TIMELINE_ID,
                WorkSector.NURSING,
                place.id,
                objective.id,
                type.id,
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                0xFF336699.toInt(),
                true,
                NOW,
                NOW,
            )
            val rule = WorkplaceRuleRevision(
                UUID(0L, 6L),
                TIMELINE_ID,
                WorkSector.NURSING,
                place.id,
                objective.id,
                LocalDate.of(2026, 8, 1),
                WorkplaceRules(NightHoursRule.Disabled, WeekendRule.None, HolidayRule(false, false)),
                NOW,
            )
            Fixture(
                history,
                objective,
                place,
                type,
                template,
                WorkCatalog(TIMELINE_ID, WorkSector.NURSING, listOf(place), listOf(type), listOf(template), listOf(rule)),
                WorkSetupState.V2Ready(TIMELINE_ID, revision),
            )
        }
    }
}

private class MutableRecurringClock(
    var current: Instant,
    private val clockZone: ZoneId,
) : Clock() {
    override fun instant(): Instant = current
    override fun getZone(): ZoneId = clockZone
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
}

private class FakeRecurringConfigurations(
    private val history: WorkConfigurationHistory,
) : WorkConfigurationRepository {
    override fun observe(): Flow<WorkConfigurationHistory?> = MutableStateFlow(history)
    override suspend fun get(): WorkConfigurationHistory = history
    override suspend fun createInitial(timelineId: UUID, firstRevision: EffectiveRevision<WorkConfiguration>) = error("No se usa")
    override suspend fun addRevision(timelineId: UUID, revision: EffectiveRevision<WorkConfiguration>) = error("No se usa")
}

private class FakeRecurringCatalog(
    private val catalog: WorkCatalog,
) : WorkCatalogRepository {
    var observeFailure: Throwable? = null

    override fun observeCatalog(timelineId: UUID, sector: WorkSector): Flow<WorkCatalog> =
        observeFailure?.let { error -> kotlinx.coroutines.flow.flow { throw error } }
            ?: MutableStateFlow(catalog)
    override fun observeRecentlyUsed(timelineId: UUID, sector: WorkSector, limit: Int): Flow<List<RecentWorkTemplate>> = MutableStateFlow(emptyList())
    override suspend fun getWorkPlace(id: UUID): WorkPlace? = catalog.workPlaces.firstOrNull { it.id == id }
    override suspend fun getWorkType(id: UUID): WorkType? = catalog.workTypes.firstOrNull { it.id == id }
    override suspend fun getWorkTemplate(id: UUID): WorkTemplate? = catalog.workTemplates.firstOrNull { it.id == id }
    override suspend fun getRuleRevisionAt(workPlaceId: UUID, date: LocalDate): WorkplaceRuleRevision? = catalog.ruleRevisionAt(workPlaceId, date)
    override suspend fun getRuleRevisions(workPlaceId: UUID): List<WorkplaceRuleRevision> = catalog.workplaceRuleRevisions.filter { it.workPlaceId == workPlaceId }
    override suspend fun createFirstWorkSet(firstWorkSet: FirstWorkSet) = error("No se usa")
    override suspend fun createWorkPlace(newWorkPlace: NewWorkPlace) = error("No se usa")
    override suspend fun updateWorkPlace(update: WorkPlaceUpdate) = error("No se usa")
    override suspend fun setWorkPlaceActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("No se usa")
    override suspend fun createWorkType(workType: WorkType) = error("No se usa")
    override suspend fun updateWorkType(update: WorkTypeUpdate) = error("No se usa")
    override suspend fun setWorkTypeActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("No se usa")
    override suspend fun createWorkTemplate(workTemplate: WorkTemplate) = error("No se usa")
    override suspend fun updateWorkTemplate(update: WorkTemplateUpdate) = error("No se usa")
    override suspend fun setWorkTemplateActive(id: UUID, isActive: Boolean, updatedAt: Instant) = error("No se usa")
    override suspend fun addWorkplaceRuleRevision(revision: WorkplaceRuleRevision, confirmationNow: Instant) = error("No se usa")
    override suspend fun extendNewV2Backward(extension: NewV2Backfill): WorkConfigurationHistory = error("No se usa")
}

private class FakeRecurringObjectives(
    private val values: List<Objective>,
) : ObjectiveRepository {
    override fun observeActive(): Flow<List<Objective>> = MutableStateFlow(values.filter(Objective::isActive))
    override fun observeAll(): Flow<List<Objective>> = MutableStateFlow(values)
    override suspend fun getById(id: UUID): Objective? = values.firstOrNull { it.id == id }
}

private class FakeRecurringShifts : ShiftRepository {
    val values = MutableStateFlow<List<Shift>>(emptyList())

    override fun observeHasAny(): Flow<Boolean> = values.map(List<Shift>::isNotEmpty)
    override fun observeStartingBetween(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<Shift>> =
        values.map { shifts -> shifts.filter { it.localStartDate in startDateInclusive..endDateInclusive } }
    override fun observeEndingAfter(instantExclusive: Instant): Flow<List<Shift>> =
        values.map { shifts -> shifts.filter { it.endAt > instantExclusive } }
    override suspend fun getById(id: UUID): Shift? = values.value.firstOrNull { it.id == id }

    fun apply(mutation: V2ShiftBatchMutation) {
        val updated = values.value.associateByTo(linkedMapOf(), Shift::id)
        mutation.shiftIdsToDelete.forEach(updated::remove)
        mutation.shiftsToInsert.forEach { updated[it.shift.id] = it.shift }
        mutation.shiftsToUpdate.forEach { updated[it.shift.id] = it.shift }
        values.value = updated.values.sortedWith(compareBy(Shift::startAt, Shift::id))
    }
}

private class FakeRecurringMedicalLeaves(
    val values: List<MedicalLeave>,
) : MedicalLeaveRepository {
    override fun observeIntersecting(startDateInclusive: LocalDate, endDateInclusive: LocalDate): Flow<List<MedicalLeave>> =
        MutableStateFlow(values.filter { it.startDate <= endDateInclusive && it.endDateInclusive >= startDateInclusive })
    override suspend fun create(medicalLeave: MedicalLeave) = error("No se usa")
    override suspend fun update(medicalLeave: MedicalLeave) = error("No se usa")
    override suspend fun delete(id: UUID) = error("No se usa")
}

private class FakeRecurringStore(
    private val shifts: FakeRecurringShifts,
    private val medical: FakeRecurringMedicalLeaves,
) : RecurringPlanRepository, V2RecurringShiftRepository {
    val plans = MutableStateFlow<List<RecurringPlanAggregate>>(emptyList())
    private val writes = linkedMapOf<UUID, V2ShiftWrite>()
    val mutations = mutableListOf<RecurringPlanMutation>()
    var calls = 0
    var gate: CompletableDeferred<Unit>? = null
    var failure: Throwable? = null
    var observePlansFailure: Throwable? = null
    var getPlanFailure: Throwable? = null

    override fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<V2ShiftWrite>> =
        MutableStateFlow(
            writes.values.filter { write ->
                write.snapshot.timelineId == timelineId && write.snapshot.sector == sector
            },
        )

    fun requireWrite(shiftId: UUID): V2ShiftWrite = requireNotNull(writes[shiftId])

    override fun observePlans(timelineId: UUID, sector: WorkSector): Flow<List<RecurringPlanAggregate>> =
        observePlansFailure?.let { error -> kotlinx.coroutines.flow.flow { throw error } }
            ?: plans.map { values -> values.filter { it.plan.timelineId == timelineId && it.plan.sector == sector } }

    override suspend fun getPlan(planId: UUID): RecurringPlanAggregate? {
        getPlanFailure?.let { throw it }
        return plans.value.firstOrNull { it.plan.id == planId }
    }

    override suspend fun getOccurrenceForShift(shiftId: UUID): RecurringOccurrence? = plans.value
        .asSequence()
        .flatMap { it.occurrences.asSequence() }
        .firstOrNull { it.shiftId == shiftId }

    override suspend fun captureProtection(
        shiftIds: Set<UUID>,
        startDateInclusive: LocalDate?,
        endDateInclusive: LocalDate?,
    ): RecurringProtectionExpectation = RecurringProtectionExpectation.capture(
        versions = shiftIds.map { id ->
            val shift = requireNotNull(writes[id]).shift
            RecurringShiftProtectionVersion(id, shift.status, emptySet(), false, emptyList())
        },
        startDateInclusive = startDateInclusive,
        endDateInclusive = endDateInclusive,
        medicalLeaves = medical.values
            .filter { leave ->
                startDateInclusive != null && endDateInclusive != null &&
                    leave.startDate <= endDateInclusive && leave.endDateInclusive >= startDateInclusive
            }
            .map { leave ->
                RecurringMedicalLeaveVersion(leave.id, leave.startDate, leave.endDateInclusive, leave.updatedAt)
            },
    )

    override fun observeWorkSnapshot(shiftId: UUID): Flow<ShiftWorkSnapshot?> =
        MutableStateFlow(writes[shiftId]?.snapshot)
    override suspend fun getWorkSnapshot(shiftId: UUID): ShiftWorkSnapshot? = writes[shiftId]?.snapshot
    override suspend fun getShift(shiftId: UUID): V2ShiftLookup = writes[shiftId]?.let(V2ShiftLookup::V2)
        ?: V2ShiftLookup.Missing
    override suspend fun insert(write: V2ShiftWrite) = error("No se usa")
    override suspend fun deleteShift(
        expected: V2ShiftWrite,
        expectedActual: com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation?,
    ) = error("No se usa")
    override suspend fun applyV2Batch(
        mutation: V2ShiftBatchMutation,
        expectedOccupancy: ShiftOccupancyExpectation,
        expectedUpdates: V2ShiftWriteExpectation,
    ) = error("No se usa")

    override suspend fun applyRecurringPlanMutation(
        mutation: RecurringPlanMutation,
        expectedPlan: RecurringPlanExpectation,
        expectedOccupancy: ShiftOccupancyExpectation,
        expectedPairs: V2ShiftWriteExpectation,
        expectedProtection: RecurringProtectionExpectation,
    ) {
        calls++
        gate?.await()
        failure?.let { throw it }
        mutations += mutation
        mutation.shiftMutation.shiftIdsToDelete.forEach(writes::remove)
        mutation.shiftMutation.shiftsToInsert.forEach { writes[it.shift.id] = it }
        mutation.shiftMutation.shiftsToUpdate.forEach { writes[it.shift.id] = it }
        shifts.apply(mutation.shiftMutation)

        val byId = plans.value.associateByTo(linkedMapOf()) { it.plan.id }
        val targetId = mutation.revisionToInsert.planId
        mutation.occurrencesToUpdate
            .filterNot { it.planId == targetId }
            .groupBy(RecurringOccurrence::planId)
            .forEach { (planId, updates) ->
            val current = requireNotNull(byId[planId])
            val occurrences = current.occurrences.associateByTo(linkedMapOf()) { it.localDate }
            updates.forEach { occurrences[it.localDate] = it }
            byId[planId] = current.copy(occurrences = occurrences.values.sortedBy(RecurringOccurrence::localDate))
        }
        val currentTarget = byId[targetId]
        val targetOccurrences = currentTarget?.occurrences.orEmpty().associateByTo(linkedMapOf()) { it.localDate }
        mutation.occurrencesToInsert.forEach { targetOccurrences[it.localDate] = it }
        mutation.occurrencesToUpdate.filter { it.planId == targetId }.forEach { targetOccurrences[it.localDate] = it }
        byId[targetId] = if (currentTarget == null) {
            RecurringPlanAggregate(
                plan = requireNotNull(mutation.planToInsert),
                revisions = listOf(mutation.revisionToInsert),
                occurrences = targetOccurrences.values.sortedBy(RecurringOccurrence::localDate),
            )
        } else {
            currentTarget.copy(
                revisions = currentTarget.revisions + mutation.revisionToInsert,
                occurrences = targetOccurrences.values.sortedBy(RecurringOccurrence::localDate),
            )
        }
        plans.value = byId.values.sortedBy { it.plan.createdAt }
    }
}
