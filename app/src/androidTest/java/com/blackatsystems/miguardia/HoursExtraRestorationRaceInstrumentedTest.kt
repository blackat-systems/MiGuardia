package com.blackatsystems.miguardia

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.repository.*
import com.blackatsystems.miguardia.core.domain.work.*
import com.blackatsystems.miguardia.ui.hours.*
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diagnostic only: real ViewModel, Main dispatcher, domain builder and SavedStateHandle.
 * Every repository is an in-memory test double. No Activity, Room, files, network or key access.
 * This models process-style ViewModel reconstruction, NOT ActivityScenario.recreate().
 */
@RunWith(AndroidJUnit4::class)
class HoursExtraRestorationRaceInstrumentedTest {
    @Before
    fun requireExplicitDisposableQa() {
        assertEquals("com.blackatsystems.miguardia.qa", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
        assertTrue("Disposable emulator only", Build.HARDWARE in setOf("ranchu", "goldfish"))
        assertEquals("ALLOW_ISOLATED_REPOSITORIES", InstrumentationRegistry.getArguments().getString("hoursReviewRace"))
    }

    @Test
    fun completedRestorationThenCorrectionPersistsLatestPosition() = exercise(lateRestoration = false)

    @Test
    fun lateRestorationCannotReplaceAReviewedCorrectionBeforeSave() = exercise(lateRestoration = true)

    private fun exercise(lateRestoration: Boolean) = runBlocking {
        withTimeout(15_000) {
            val fixture = Fixture()
            val models = mutableListOf<HoursAndExtrasViewModel>()
            try {
                // Produce the saved review through production actions, not a handwritten fingerprint.
                val originalState = SavedStateHandle()
                val original = withContext(Dispatchers.Main.immediate) {
                    fixture.model(originalState).also(models::add)
                }
                original.uiState.first { it.loadState == HoursAndExtrasLoadState.CONTENT }
                withContext(Dispatchers.Main.immediate) {
                    original.openCorrectExtra(fixture.repository.rows.value.single())
                    original.updateExtraDraft { it.copy(position = FIRST_REVIEW_POSITION) }
                    original.reviewExtra()
                }
                original.uiState.first { it.extraReview?.record?.snapshot?.position == FIRST_REVIEW_POSITION && !it.isSaving }
                val restoredState = withContext(Dispatchers.Main.immediate) {
                    val values = originalState.keys().associateWith { key ->
                        val value = originalState.get<Any?>(key)
                        if (value is ArrayList<*>) ArrayList(value) else value
                    }
                    original.viewModelScope.cancel()
                    SavedStateHandle(values)
                }
                assertEquals(0, fixture.repository.mutations.size)

                val hold = if (lateRestoration) Hold() else null
                fixture.repository.nextHold = hold
                val restored = withContext(Dispatchers.Main.immediate) {
                    fixture.model(restoredState).also(models::add)
                }
                if (hold != null) {
                    hold.entered.await()
                    // These are the actual public UI gates: Back/Edit is enabled when !isSaving.
                    withContext(Dispatchers.Main.immediate) {
                        assertEquals(HoursAndExtrasLoadState.CONTENT, restored.uiState.value.loadState)
                        assertEquals(HoursAndExtrasSurface.EXTRA_EDITOR, restored.uiState.value.surface)
                        assertEquals(EditorStage.REVIEW, restored.uiState.value.extraDraft?.stage)
                        assertFalse(restored.uiState.value.isSaving)
                        assertEquals(null, restored.uiState.value.extraReview)
                    }
                } else {
                    restored.uiState.first { it.extraReview?.record?.snapshot?.position == FIRST_REVIEW_POSITION }
                }

                val restorationFingerprint = withContext(Dispatchers.Main.immediate) {
                    requireNotNull(restored.uiState.value.extraDraft?.expectedFingerprint)
                }
                withContext(Dispatchers.Main.immediate) {
                    restored.backExtra()
                    assertEquals(EditorStage.EDIT, restored.uiState.value.extraDraft?.stage)
                    restored.updateExtraDraft { it.copy(position = LATEST_POSITION) }
                    assertEquals(LATEST_POSITION, restored.uiState.value.extraDraft?.position)
                    restored.reviewExtra()
                }
                restored.uiState.first { it.extraReview?.record?.snapshot?.position == LATEST_POSITION && !it.isSaving }
                val freshFingerprint = withContext(Dispatchers.Main.immediate) {
                    requireNotNull(restored.uiState.value.extraDraft?.expectedFingerprint)
                }

                if (hold != null) {
                    assertNotNull(hold.captured)
                    // Same persisted sources, different draft position. No CAS conflict was fabricated.
                    assertEquals(restorationFingerprint, freshFingerprint)
                    hold.release.complete(Unit)
                    hold.returned.await()
                }
                // Main queue barrier: the resumed restoration must finish publishing before save.
                val positionPresentedAfterLateResponse = withContext(Dispatchers.Main.immediate) {
                    assertFalse(restored.uiState.value.isSaving)
                    assertEquals(LATEST_POSITION, restored.uiState.value.extraDraft?.position)
                    restored.uiState.value.extraReview?.record?.snapshot?.position
                }
                withContext(Dispatchers.Main.immediate) { restored.saveExtra() }
                restored.uiState.first { it.successSequence > 0 }
                withContext(Dispatchers.Main.immediate) {
                    assertEquals(1, fixture.repository.mutations.size)
                    val actual = fixture.repository.rows.value.single().snapshot.position
                    assertEquals(
                        "Latest reviewed draft must be saved; late=$lateRestoration, " +
                            "presentedAfterResponse=$positionPresentedAfterLateResponse, persisted=$actual",
                        LATEST_POSITION,
                        actual,
                    )
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    models.forEach { it.viewModelScope.cancel() }
                }
            }
        }
    }

    private class Hold {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<Unit>()
        var captured: IndependentExtraWorkExpectation? = null
    }

    private class MemoryExtras(initial: IndependentExtraWorkRecord) : IndependentExtraWorkRepository {
        val rows = MutableStateFlow(listOf(initial))
        val mutations = mutableListOf<IndependentExtraWorkMutation>()
        var nextHold: Hold? = null
        override fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<IndependentExtraWorkRecord>> = rows
        override fun observeOn(timelineId: UUID, sector: WorkSector, ownerLocalDate: LocalDate) =
            rows.map { records -> records.filter { it.ownerLocalDate == ownerLocalDate } }
        override suspend fun get(id: UUID) = rows.value.singleOrNull { it.id == id }
        override suspend fun captureExpectation(
            id: UUID?, selection: IndependentExtraWorkSelection,
            windowStart: Instant, windowEnd: Instant,
            windowStartDate: LocalDate, windowEndDateInclusive: LocalDate,
        ): IndependentExtraWorkExpectation {
            val expectation = IndependentExtraWorkExpectation.capture(
                previous = id?.let { get(it) }, selection = selection,
                windowStart = windowStart, windowEnd = windowEnd,
                windowStartDate = windowStartDate, windowEndDateInclusive = windowEndDateInclusive,
                observedShifts = emptyList(), observedExtras = emptyList(),
                observedProtectedDateRanges = emptyList(), protectionFingerprint = "",
            )
            val hold = nextHold
            nextHold = null
            if (hold != null) {
                hold.captured = expectation
                hold.entered.complete(Unit)
                hold.release.await()
                hold.returned.complete(Unit)
            }
            return expectation
        }
        override suspend fun applyMutation(mutation: IndependentExtraWorkMutation): IndependentExtraWorkWriteResult {
            check(mutation.expectation.previous == rows.value.single()) { "Unexpected source change in diagnostic" }
            val replacement = requireNotNull(mutation.replacement)
            mutations += mutation
            rows.value = listOf(replacement)
            return IndependentExtraWorkWriteResult.Saved(replacement)
        }
    }

    private class Fixture {
        private val now = Instant.parse("2026-09-10T12:00:00Z")
        private val date = LocalDate.of(2026, 9, 10)
        private val timestamp = now.minusSeconds(86_400)
        private val clock = Clock.fixed(now, ZoneOffset.UTC)
        private val sector = WorkSector.NURSING
        private val history = WorkConfigurationHistory(
            EffectiveDateTimeline(id(1), listOf(EffectiveRevision(
                id(2), date.minusDays(1), WorkConfiguration(sector, HoursReference.PendingSetup, null),
            ))),
            PerPeriodHoursValues(emptyList()),
        )
        private val objective = Objective(id(3), "Lugar ficticio carrera", "LFC", null, null, true, timestamp, timestamp)
        private val place = WorkPlace(id(4), id(1), sector, id(3), true, timestamp, timestamp)
        private val type = WorkType.create(id(5), id(1), sector, "Trabajo ficticio", timestamp)
        private val extraClass = ExtraWorkClass.create(id(6), id(1), sector, "Extra ficticio", true, true, timestamp)
        private val rule = WorkplaceRuleRevision(
            id(9), id(1), sector, place.id, objective.id, date.minusDays(1),
            WorkplaceRules(NightHoursRule.Disabled, WeekendRule.None, HolidayRule(false, false)), timestamp,
        )
        private val catalog = WorkCatalog(id(1), sector, listOf(place), listOf(type), emptyList(), listOf(rule))
        private val selection = IndependentExtraWorkSelection(
            ResolvedWorkConfigurationRevision.resolve(history, date), place, objective, type, null, extraClass,
        )
        val repository = MemoryExtras(buildIndependentExtraWorkRecord(
            IndependentExtraWorkDraft(
                id = id(7), ownerLocalDate = date, zoneId = ZoneOffset.UTC,
                start = now.minusSeconds(7_200), end = now.minusSeconds(3_600),
                colorArgb = 0xFF334455.toInt(), position = "Puesto persistido inicial",
            ),
            selection = selection, clock = clock, timestamp = timestamp,
        ))

        fun model(saved: SavedStateHandle) = HoursAndExtrasViewModel(
            configurationRepository = readOnlyProxy(mapOf("observe" to flowOf(history))),
            catalogRepository = readOnlyProxy(mapOf("observeCatalog" to flowOf(catalog))),
            objectiveRepository = readOnlyProxy(mapOf("observeAll" to flowOf(listOf(objective)))),
            shiftRepository = readOnlyProxy(mapOf("observeAll" to flowOf(emptyList<V2ShiftWrite>()))),
            shiftActualRepository = readOnlyProxy(mapOf(
                "observeExtraWorkClasses" to flowOf(listOf(extraClass)),
                "observeAllActuals" to flowOf(emptyMap<UUID, ShiftActualAggregate>()),
            )),
            independentExtraRepository = repository,
            medicalLeaveRepository = readOnlyProxy(mapOf("observeIntersecting" to flowOf(emptyList<MedicalLeave>()))),
            vacationRepository = readOnlyProxy(mapOf("observeOverlapping" to flowOf(emptyList<Vacation>()))),
            clock = clock, zoneId = ZoneOffset.UTC, uuidProvider = { id(8) }, savedStateHandle = saved,
        )
    }

    companion object {
        private const val FIRST_REVIEW_POSITION = "Primera corrección revisada"
        private const val LATEST_POSITION = "Última corrección revisada"
        private fun id(value: Long) = UUID(0L, value)

        // Unexpected repository calls fail; the test cannot silently write outside MemoryExtras.
        private inline fun <reified T> readOnlyProxy(results: Map<String, Any>): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
                check(results.containsKey(method.name)) { "Unexpected repository call: ${T::class.java.simpleName}.${method.name}" }
                results.getValue(method.name)
            } as T
    }
}
