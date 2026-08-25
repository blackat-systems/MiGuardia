package com.blackatsystems.miguardia.core.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.RecurringPattern
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrence
import com.blackatsystems.miguardia.core.domain.model.RecurringPlan
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanExpectation
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanMutation
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevision
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevisionKind
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDifferenceChoice
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDraft
import com.blackatsystems.miguardia.core.domain.model.ShiftActualWriteResult
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWriteExpectation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.shift.RecurringConflictPolicy
import com.blackatsystems.miguardia.core.domain.shift.editV2ShiftPositionOnly
import com.blackatsystems.miguardia.core.domain.shift.planNewRecurringPlan
import com.blackatsystems.miguardia.core.domain.shift.planRecurringFinalization
import com.blackatsystems.miguardia.core.domain.shift.planRecurringRevision
import com.blackatsystems.miguardia.core.domain.model.buildShiftActualSaveMutation
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecurringPlanPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MiGuardiaV2Database
    private lateinit var store: LocalDataStore
    private lateinit var fixture: SeededV2Catalog

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB)
        openStore()
        fixture = store.seedV2Catalog()
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun planRevisionOccurrencesAndPairsAreAtomicAndSurviveReopen() = runBlocking {
        val dates = listOf(DATE, DATE.plusDays(2), DATE.plusDays(4))
        val writes = dates.mapIndexed { index, date ->
            store.buildTestV2Write(fixture, V2TestIds.uuid(201 + index), date)
        }
        createPlan(dates, writes)

        val stored = requireNotNull(store.recurringPlans.getPlan(PLAN_ID))
        assertEquals(1, stored.revisions.size)
        assertEquals(dates, stored.occurrences.map { it.localDate })
        assertTrue(stored.occurrences.all { it.state == RecurringOccurrenceState.AUTOMATIC })
        writes.forEach { write ->
            assertEquals(V2ShiftLookup.V2(write), store.v2Shifts.getShift(write.shift.id))
        }

        store.close()
        openStore()
        val reopened = requireNotNull(store.recurringPlans.getPlan(PLAN_ID))
        assertEquals(stored, reopened)
        assertEquals(
            PLAN_ID,
            store.recurringPlans.getOccurrenceForShift(writes.first().shift.id)?.planId,
        )
        assertDatabaseIntegrity()
    }

    @Test
    fun savingActualHoursKeepsTheRecurringOccurrenceAutomatic() = runBlocking {
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(209), DATE)
        createPlan(listOf(DATE), listOf(write))
        val planBefore = requireNotNull(store.recurringPlans.getPlan(PLAN_ID))
        val expectation = requireNotNull(store.shiftActuals.getExpectation(write.shift.id))
        assertEquals(
            RecurringOccurrenceState.AUTOMATIC,
            requireNotNull(expectation.recurringOccurrence).state,
        )
        val mutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation = expectation,
                draft = ShiftActualDraft(
                    actualStart = write.shift.startAt,
                    actualEnd = write.shift.endAt.plus(Duration.ofMinutes(30)),
                    differenceReason = "Salida posterior ficticia",
                    explanation = null,
                    differenceChoice = ShiftActualDifferenceChoice.ALL_REGULAR,
                    classSelection = null,
                    fragments = emptyList(),
                ),
                clock = Clock.fixed(write.shift.endAt.plus(Duration.ofHours(4)), ZoneOffset.UTC),
                timestamp = write.shift.endAt.plusSeconds(60),
            ),
        )

        assertTrue(store.shiftActuals.save(mutation) is ShiftActualWriteResult.Saved)

        assertEquals(planBefore, store.recurringPlans.getPlan(PLAN_ID))
        assertEquals(
            RecurringOccurrenceState.AUTOMATIC,
            store.recurringPlans.getOccurrenceForShift(write.shift.id)?.state,
        )
        assertEquals(
            RecurringOccurrenceState.AUTOMATIC,
            store.shiftActuals.getExpectation(write.shift.id)?.recurringOccurrence?.state,
        )
        assertEquals(V2ShiftLookup.V2(write), store.v2Shifts.getShift(write.shift.id))
    }

    @Test
    fun failureAfterRevisionInsertRollsBackPlanOccurrencesAndPairs() = runBlocking {
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(211), DATE)
        val prepared = prepareNewPlan(listOf(DATE), listOf(write))
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER force_recurring_rollback
                BEFORE INSERT ON shifts
                WHEN NEW.id = '${write.shift.id}'
                BEGIN
                    SELECT RAISE(ABORT, 'forced recurring failure');
                END""",
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.recurringShiftWriter.applyRecurringPlanMutation(
                mutation = requireNotNull(prepared.mutation),
                expectedPlan = RecurringPlanExpectation.capture(PLAN_ID, null),
                expectedOccupancy = ShiftOccupancyExpectation.capture(DATE, DATE, emptyList()),
                expectedPairs = V2ShiftWriteExpectation.EMPTY,
                expectedProtection = emptyProtection(DATE, DATE),
            )
        }

        assertNull(store.recurringPlans.getPlan(PLAN_ID))
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(write.shift.id))
        assertEquals(0, scalar("SELECT COUNT(*) FROM recurring_plan_revisions"))
        assertEquals(0, scalar("SELECT COUNT(*) FROM recurring_occurrences"))
    }

    @Test
    fun individualEditCustomizesAndIndividualDeleteKeepsExclusion() = runBlocking {
        val original = store.buildTestV2Write(fixture, V2TestIds.uuid(221), DATE)
        createPlan(listOf(DATE), listOf(original))
        val edited = editV2ShiftPositionOnly(
            original,
            "Puesto personalizado",
            original.shift.updatedAt.plusSeconds(10),
        )

        store.v2Shifts.applyV2Batch(
            mutation = V2ShiftBatchMutation(shiftsToUpdate = listOf(edited)),
            expectedOccupancy = ShiftOccupancyExpectation.capture(DATE, DATE, listOf(original.shift)),
            expectedUpdates = V2ShiftWriteExpectation.capture(listOf(original)),
        )
        assertEquals(
            RecurringOccurrenceState.CUSTOMIZED,
            store.recurringPlans.getOccurrenceForShift(original.shift.id)?.state,
        )

        store.v2Shifts.deleteShift(edited)
        val occurrence = requireNotNull(store.recurringPlans.getPlan(PLAN_ID))
            .occurrences.single()
        assertEquals(RecurringOccurrenceState.EXCLUDED, occurrence.state)
        assertNull(occurrence.shiftId)
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(original.shift.id))
        assertDatabaseIntegrity()
    }

    @Test
    fun manualReplacementLeavesOldOccurrenceExcludedAndNewShiftManual() = runBlocking {
        val recurring = store.buildTestV2Write(fixture, V2TestIds.uuid(231), DATE)
        val manual = store.buildTestV2Write(fixture, V2TestIds.uuid(232), DATE)
        createPlan(listOf(DATE), listOf(recurring))

        store.v2Shifts.applyV2Batch(
            mutation = V2ShiftBatchMutation(
                shiftIdsToDelete = setOf(recurring.shift.id),
                shiftsToInsert = listOf(manual),
            ),
            expectedOccupancy = ShiftOccupancyExpectation.capture(DATE, DATE, listOf(recurring.shift)),
        )

        val oldOccurrence = requireNotNull(store.recurringPlans.getPlan(PLAN_ID)).occurrences.single()
        assertEquals(RecurringOccurrenceState.EXCLUDED, oldOccurrence.state)
        assertNull(oldOccurrence.shiftId)
        assertNull(store.recurringPlans.getOccurrenceForShift(manual.shift.id))
        assertEquals(V2ShiftLookup.V2(manual), store.v2Shifts.getShift(manual.shift.id))
    }

    @Test
    fun changedProtectionRejectsFinalizationWithoutPartialMutation() = runBlocking {
        val original = store.buildTestV2Write(fixture, V2TestIds.uuid(241), DATE)
        createPlan(listOf(DATE), listOf(original))
        val aggregate = requireNotNull(store.recurringPlans.getPlan(PLAN_ID))
        val protection = store.recurringPlans.captureProtection(setOf(original.shift.id), DATE, DATE)
        val finalRevision = revision(
            id = FINAL_REVISION_ID,
            number = 2,
            kind = RecurringPlanRevisionKind.FINALIZED,
        )
        val preview = planRecurringFinalization(
            current = aggregate,
            finalRevision = finalRevision,
            existingShifts = listOf(original.shift),
            protection = protection,
        )
        val note = ShiftNote(
            id = V2TestIds.uuid(242),
            shiftId = original.shift.id,
            body = "Nota ficticia concurrente",
            createdAt = V2TestIds.NOW.plusSeconds(20),
            updatedAt = V2TestIds.NOW.plusSeconds(20),
        )
        store.shiftNotes.insert(note)
        val notification = ShiftNotificationConfig(
            shiftId = original.shift.id,
            reminderLeadMinutes = listOf(30L, 120L),
        )
        store.shiftNotificationConfigs.replace(notification)

        assertSuspendThrows<ConflictingLocalWriteException> {
            store.recurringShiftWriter.applyRecurringPlanMutation(
                mutation = requireNotNull(preview.mutation),
                expectedPlan = RecurringPlanExpectation.capture(PLAN_ID, aggregate),
                expectedOccupancy = ShiftOccupancyExpectation.capture(DATE, DATE, listOf(original.shift)),
                expectedPairs = V2ShiftWriteExpectation.capture(listOf(original)),
                expectedProtection = protection,
            )
        }

        assertEquals(1, requireNotNull(store.recurringPlans.getPlan(PLAN_ID)).revisions.size)
        assertEquals(V2ShiftLookup.V2(original), store.v2Shifts.getShift(original.shift.id))
        assertEquals(note, store.shiftNotes.getById(note.id))
        assertEquals(notification, store.shiftNotificationConfigs.getForShift(original.shift.id))

        val protectedNow = store.recurringPlans.captureProtection(setOf(original.shift.id), DATE, DATE)
        val protectedPreview = planRecurringFinalization(
            current = aggregate,
            finalRevision = finalRevision,
            existingShifts = listOf(original.shift),
            protection = protectedNow,
        )
        assertTrue(requireNotNull(protectedPreview.mutation).shiftMutation.shiftIdsToDelete.isEmpty())
        assertTrue(protectedPreview.results.single().action.name.contains("PROTECTED"))
    }

    @Test
    fun automaticReplacementAcrossPlansRetiresOldOwnerWithoutOrphans() = runBlocking {
        val oldWrite = store.buildTestV2Write(fixture, V2TestIds.uuid(251), DATE)
        createPlan(listOf(DATE), listOf(oldWrite))
        val oldPlan = requireNotNull(store.recurringPlans.getPlan(PLAN_ID))
        val newWrite = store.buildTestV2Write(fixture, V2TestIds.uuid(252), DATE)
        val protection = store.recurringPlans.captureProtection(setOf(oldWrite.shift.id), DATE, DATE)
        val preview = planNewRecurringPlan(
            plan = RecurringPlan(
                SECOND_PLAN_ID,
                V2TestIds.TIMELINE,
                fixture.revision.value.sector,
                V2TestIds.NOW.plusSeconds(30),
            ),
            revision = revision(
                id = SECOND_REVISION_ID,
                planId = SECOND_PLAN_ID,
                end = DATE,
                createdAt = V2TestIds.NOW.plusSeconds(31),
            ),
            dates = listOf(DATE),
            candidates = listOf(newWrite),
            existingShifts = listOf(oldWrite.shift),
            linkedOccurrences = oldPlan.occurrences,
            protection = protection,
            conflictPolicy = RecurringConflictPolicy.REPLACE_AUTOMATIC_INTACT,
            medicalLeaveDates = emptySet(),
        )

        store.recurringShiftWriter.applyRecurringPlanMutation(
            mutation = requireNotNull(preview.mutation),
            expectedPlan = RecurringPlanExpectation.capture(
                mapOf(PLAN_ID to oldPlan, SECOND_PLAN_ID to null),
            ),
            expectedOccupancy = ShiftOccupancyExpectation.capture(DATE, DATE, listOf(oldWrite.shift)),
            expectedPairs = V2ShiftWriteExpectation.capture(listOf(oldWrite)),
            expectedProtection = protection,
        )

        val retired = requireNotNull(store.recurringPlans.getPlan(PLAN_ID)).occurrences.single()
        val replacement = requireNotNull(store.recurringPlans.getPlan(SECOND_PLAN_ID)).occurrences.single()
        assertEquals(RecurringOccurrenceState.RETIRED, retired.state)
        assertNull(retired.shiftId)
        assertEquals(RecurringOccurrenceState.AUTOMATIC, replacement.state)
        assertEquals(newWrite.shift.id, replacement.shiftId)
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(oldWrite.shift.id))
        assertEquals(V2ShiftLookup.V2(newWrite), store.v2Shifts.getShift(newWrite.shift.id))
        assertDatabaseIntegrity()
    }

    @Test
    fun futureRevisionPreservesCustomizedAndExcludedAndRetiresOnlyAutomaticIntact() = runBlocking {
        val dates = listOf(DATE, DATE.plusDays(2), DATE.plusDays(4))
        val writes = dates.mapIndexed { index, date ->
            store.buildTestV2Write(fixture, V2TestIds.uuid(270 + index), date)
        }
        createPlan(dates, writes)
        val customized = editV2ShiftPositionOnly(
            writes[0],
            "Excepción personalizada",
            writes[0].shift.updatedAt.plusSeconds(1),
        )
        store.v2Shifts.applyV2Batch(
            mutation = V2ShiftBatchMutation(shiftsToUpdate = listOf(customized)),
            expectedOccupancy = ShiftOccupancyExpectation.capture(DATE, DATE, listOf(writes[0].shift)),
            expectedUpdates = V2ShiftWriteExpectation.capture(listOf(writes[0])),
        )
        store.v2Shifts.deleteShift(writes[1])

        val current = requireNotNull(store.recurringPlans.getPlan(PLAN_ID))
        val newDate = DATE.plusDays(6)
        val newWrite = store.buildTestV2Write(fixture, V2TestIds.uuid(273), newDate)
        val currentShifts = listOf(customized.shift, writes[2].shift)
        val protection = store.recurringPlans.captureProtection(
            currentShifts.mapTo(linkedSetOf()) { it.id },
            DATE,
            newDate.plusDays(2),
        )
        val preview = planRecurringRevision(
            current = current,
            revision = revision(
                id = THIRD_REVISION_ID,
                number = 2,
                end = newDate,
                pattern = RecurringPattern.Weekdays.of(setOf(newDate.dayOfWeek)),
            ),
            dates = listOf(newDate),
            candidates = listOf(newWrite),
            existingShifts = currentShifts,
            linkedOccurrences = current.occurrences,
            protection = protection,
            conflictPolicy = RecurringConflictPolicy.KEEP_EXISTING,
            medicalLeaveDates = emptySet(),
        )
        val mutation = requireNotNull(preview.mutation)

        store.recurringShiftWriter.applyRecurringPlanMutation(
            mutation = mutation,
            expectedPlan = RecurringPlanExpectation.capture(PLAN_ID, current),
            expectedOccupancy = ShiftOccupancyExpectation.capture(
                DATE,
                newDate.plusDays(2),
                currentShifts,
            ),
            expectedPairs = V2ShiftWriteExpectation.capture(listOf(writes[2])),
            expectedProtection = protection,
        )

        val stored = requireNotNull(store.recurringPlans.getPlan(PLAN_ID))
        assertEquals(RecurringOccurrenceState.CUSTOMIZED, stored.occurrences.single { it.localDate == dates[0] }.state)
        assertEquals(RecurringOccurrenceState.EXCLUDED, stored.occurrences.single { it.localDate == dates[1] }.state)
        assertEquals(RecurringOccurrenceState.RETIRED, stored.occurrences.single { it.localDate == dates[2] }.state)
        assertEquals(RecurringOccurrenceState.AUTOMATIC, stored.occurrences.single { it.localDate == newDate }.state)
        assertEquals(V2ShiftLookup.V2(customized), store.v2Shifts.getShift(customized.shift.id))
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(writes[1].shift.id))
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(writes[2].shift.id))
        assertEquals(V2ShiftLookup.V2(newWrite), store.v2Shifts.getShift(newWrite.shift.id))
        assertDatabaseIntegrity()
    }

    @Test
    fun occurrenceForeignKeysAndUniqueShiftOwnerRejectInvalidSecondPlanAtomically() = runBlocking {
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(261), DATE)
        createPlan(listOf(DATE), listOf(write))
        val secondPlan = RecurringPlan(
            SECOND_PLAN_ID,
            V2TestIds.TIMELINE,
            fixture.revision.value.sector,
            V2TestIds.NOW.plusSeconds(40),
        )
        val secondRevision = revision(
            id = SECOND_REVISION_ID,
            planId = SECOND_PLAN_ID,
            end = DATE,
        )

        assertSuspendThrows<SQLiteConstraintException> {
            database.withTransaction {
                database.recurringPlanDao().insertPlan(secondPlan.toEntity())
                database.recurringPlanDao().insertOccurrences(
                    listOf(
                        RecurringOccurrence(
                            planId = SECOND_PLAN_ID,
                            localDate = DATE,
                            revisionId = REVISION_ID,
                            shiftId = null,
                            state = RecurringOccurrenceState.EXCLUDED,
                            createdAt = V2TestIds.NOW.plusSeconds(41),
                            updatedAt = V2TestIds.NOW.plusSeconds(41),
                        ).toEntity(),
                    ),
                )
            }
        }
        assertNull(store.recurringPlans.getPlan(SECOND_PLAN_ID))

        assertSuspendThrows<SQLiteConstraintException> {
            database.withTransaction {
                database.recurringPlanDao().insertPlan(secondPlan.toEntity())
                database.recurringPlanDao().insertRevision(secondRevision.toEntity())
                database.recurringPlanDao().insertOccurrences(
                    listOf(
                        RecurringOccurrence(
                            planId = SECOND_PLAN_ID,
                            localDate = DATE,
                            revisionId = SECOND_REVISION_ID,
                            shiftId = write.shift.id,
                            state = RecurringOccurrenceState.AUTOMATIC,
                            createdAt = V2TestIds.NOW.plusSeconds(42),
                            updatedAt = V2TestIds.NOW.plusSeconds(42),
                        ).toEntity(),
                    ),
                )
            }
        }
        assertNull(store.recurringPlans.getPlan(SECOND_PLAN_ID))
        assertEquals(PLAN_ID, store.recurringPlans.getOccurrenceForShift(write.shift.id)?.planId)
        assertDatabaseIntegrity()
    }

    @Test
    fun writerRejectsEmptyPlanPastRevisionAndOutOfPatternOccurrence() = runBlocking {
        val plan = recurringPlan()
        val emptyRevision = revision(end = DATE.plusDays(2))

        assertSuspendThrows<InvalidLocalDataException> {
            store.recurringShiftWriter.applyRecurringPlanMutation(
                mutation = RecurringPlanMutation(
                    planToInsert = plan,
                    revisionToInsert = emptyRevision,
                ),
                expectedPlan = RecurringPlanExpectation.capture(PLAN_ID, null),
                expectedOccupancy = ShiftOccupancyExpectation.capture(
                    DATE,
                    DATE.plusDays(2),
                    emptyList(),
                ),
                expectedPairs = V2ShiftWriteExpectation.EMPTY,
                expectedProtection = emptyProtection(DATE, DATE.plusDays(2)),
            )
        }
        assertNull(store.recurringPlans.getPlan(PLAN_ID))

        val past = LocalDate.of(2025, 12, 31)
        assertSuspendThrows<InvalidLocalDataException> {
            store.recurringShiftWriter.applyRecurringPlanMutation(
                mutation = RecurringPlanMutation(
                    planToInsert = plan.copy(createdAt = V2TestIds.NOW.minusSeconds(60)),
                    revisionToInsert = revision(
                        start = past,
                        end = past,
                        pattern = RecurringPattern.EveryNDays(1),
                    ),
                ),
                expectedPlan = RecurringPlanExpectation.capture(PLAN_ID, null),
                expectedOccupancy = ShiftOccupancyExpectation.capture(past, past, emptyList()),
                expectedPairs = V2ShiftWriteExpectation.EMPTY,
                expectedProtection = emptyProtection(past, past),
            )
        }
        assertNull(store.recurringPlans.getPlan(PLAN_ID))

        val wrongDate = DATE.plusDays(1)
        val wrongWrite = store.buildTestV2Write(fixture, V2TestIds.uuid(601), wrongDate)
        val patternedRevision = revision(
            end = DATE.plusDays(2),
            pattern = RecurringPattern.EveryNDays(2),
        )
        val wrongOccurrence = RecurringOccurrence(
            planId = PLAN_ID,
            localDate = wrongDate,
            revisionId = patternedRevision.id,
            shiftId = wrongWrite.shift.id,
            state = RecurringOccurrenceState.AUTOMATIC,
            createdAt = patternedRevision.createdAt,
            updatedAt = patternedRevision.createdAt,
        )
        assertSuspendThrows<InvalidLocalDataException> {
            store.recurringShiftWriter.applyRecurringPlanMutation(
                mutation = RecurringPlanMutation(
                    planToInsert = plan,
                    revisionToInsert = patternedRevision,
                    occurrencesToInsert = listOf(wrongOccurrence),
                    shiftMutation = V2ShiftBatchMutation(shiftsToInsert = listOf(wrongWrite)),
                ),
                expectedPlan = RecurringPlanExpectation.capture(PLAN_ID, null),
                expectedOccupancy = ShiftOccupancyExpectation.capture(
                    DATE,
                    DATE.plusDays(2),
                    emptyList(),
                ),
                expectedPairs = V2ShiftWriteExpectation.EMPTY,
                expectedProtection = emptyProtection(DATE, DATE.plusDays(2)),
            )
        }
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(wrongWrite.shift.id))
        assertNull(store.recurringPlans.getPlan(PLAN_ID))
    }

    @Test
    fun writerRejectsManualShiftMutationAndRevisionPhotoMismatch() = runBlocking {
        val manual = store.buildTestV2Write(fixture, V2TestIds.uuid(611), DATE)
        store.v2Shifts.insert(manual)
        val manualEdit = editV2ShiftPositionOnly(
            manual,
            "Puesto recurrente",
            manual.shift.updatedAt.plusSeconds(1),
        )
        val manualRevision = revision().copy(positionSnapshot = "Puesto recurrente")
        val manualOccurrence = RecurringOccurrence(
            planId = PLAN_ID,
            localDate = DATE,
            revisionId = manualRevision.id,
            shiftId = manual.shift.id,
            state = RecurringOccurrenceState.AUTOMATIC,
            createdAt = manualRevision.createdAt,
            updatedAt = manualRevision.createdAt,
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.recurringShiftWriter.applyRecurringPlanMutation(
                mutation = RecurringPlanMutation(
                    planToInsert = recurringPlan(),
                    revisionToInsert = manualRevision,
                    occurrencesToInsert = listOf(manualOccurrence),
                    shiftMutation = V2ShiftBatchMutation(shiftsToUpdate = listOf(manualEdit)),
                ),
                expectedPlan = RecurringPlanExpectation.capture(PLAN_ID, null),
                expectedOccupancy = ShiftOccupancyExpectation.capture(DATE, DATE, listOf(manual.shift)),
                expectedPairs = V2ShiftWriteExpectation.capture(listOf(manual)),
                expectedProtection = emptyProtection(DATE, DATE),
            )
        }
        assertEquals(V2ShiftLookup.V2(manual), store.v2Shifts.getShift(manual.shift.id))
        assertNull(store.recurringPlans.getPlan(PLAN_ID))

        val replacement = store.buildTestV2Write(fixture, V2TestIds.uuid(613), DATE)
        val replacementRevision = revision()
        val replacementOccurrence = RecurringOccurrence(
            planId = PLAN_ID,
            localDate = DATE,
            revisionId = replacementRevision.id,
            shiftId = replacement.shift.id,
            state = RecurringOccurrenceState.AUTOMATIC,
            createdAt = replacementRevision.createdAt,
            updatedAt = replacementRevision.createdAt,
        )
        assertSuspendThrows<InvalidLocalDataException> {
            store.recurringShiftWriter.applyRecurringPlanMutation(
                mutation = RecurringPlanMutation(
                    planToInsert = recurringPlan(),
                    revisionToInsert = replacementRevision,
                    occurrencesToInsert = listOf(replacementOccurrence),
                    shiftMutation = V2ShiftBatchMutation(
                        shiftIdsToDelete = setOf(manual.shift.id),
                        shiftsToInsert = listOf(replacement),
                    ),
                ),
                expectedPlan = RecurringPlanExpectation.capture(PLAN_ID, null),
                expectedOccupancy = ShiftOccupancyExpectation.capture(DATE, DATE, listOf(manual.shift)),
                expectedPairs = V2ShiftWriteExpectation.capture(listOf(manual)),
                expectedProtection = emptyProtection(DATE, DATE),
            )
        }
        assertEquals(V2ShiftLookup.V2(manual), store.v2Shifts.getShift(manual.shift.id))
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(replacement.shift.id))
        assertNull(store.recurringPlans.getPlan(PLAN_ID))

        store.v2Shifts.deleteShift(manual)
        val recurringWrite = store.buildTestV2Write(fixture, V2TestIds.uuid(612), DATE)
        val prepared = prepareNewPlan(listOf(DATE), listOf(recurringWrite))
        val originalMutation = requireNotNull(prepared.mutation)
        val mismatched = originalMutation.copy(
            revisionToInsert = originalMutation.revisionToInsert.copy(
                positionSnapshot = "Puesto que la jornada no tiene",
            ),
        )
        assertSuspendThrows<InvalidLocalDataException> {
            store.recurringShiftWriter.applyRecurringPlanMutation(
                mutation = mismatched,
                expectedPlan = RecurringPlanExpectation.capture(PLAN_ID, null),
                expectedOccupancy = ShiftOccupancyExpectation.capture(DATE, DATE, emptyList()),
                expectedPairs = V2ShiftWriteExpectation.EMPTY,
                expectedProtection = emptyProtection(DATE, DATE),
            )
        }
        assertEquals(V2ShiftLookup.Missing, store.v2Shifts.getShift(recurringWrite.shift.id))
        assertNull(store.recurringPlans.getPlan(PLAN_ID))
    }

    @Test
    fun revisionCannotSilentlyLeaveAnIntactAutomaticOccurrenceUnchanged() = runBlocking {
        val write = store.buildTestV2Write(fixture, V2TestIds.uuid(621), DATE)
        createPlan(listOf(DATE), listOf(write))
        val current = requireNotNull(store.recurringPlans.getPlan(PLAN_ID))
        val protection = store.recurringPlans.captureProtection(setOf(write.shift.id), DATE, DATE)
        val malformedRevision = revision(
            id = THIRD_REVISION_ID,
            number = 2,
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.recurringShiftWriter.applyRecurringPlanMutation(
                mutation = RecurringPlanMutation(revisionToInsert = malformedRevision),
                expectedPlan = RecurringPlanExpectation.capture(PLAN_ID, current),
                expectedOccupancy = ShiftOccupancyExpectation.capture(DATE, DATE, listOf(write.shift)),
                expectedPairs = V2ShiftWriteExpectation.EMPTY,
                expectedProtection = protection,
            )
        }

        val unchanged = requireNotNull(store.recurringPlans.getPlan(PLAN_ID))
        assertEquals(1, unchanged.revisions.size)
        assertEquals(REVISION_ID, unchanged.occurrences.single().revisionId)
        assertEquals(V2ShiftLookup.V2(write), store.v2Shifts.getShift(write.shift.id))

        val malformedFinalization = revision(
            id = FINAL_REVISION_ID,
            number = 2,
            kind = RecurringPlanRevisionKind.FINALIZED,
        )
        assertSuspendThrows<InvalidLocalDataException> {
            store.recurringShiftWriter.applyRecurringPlanMutation(
                mutation = RecurringPlanMutation(revisionToInsert = malformedFinalization),
                expectedPlan = RecurringPlanExpectation.capture(PLAN_ID, unchanged),
                expectedOccupancy = ShiftOccupancyExpectation.capture(DATE, DATE, listOf(write.shift)),
                expectedPairs = V2ShiftWriteExpectation.EMPTY,
                expectedProtection = protection,
            )
        }
        assertEquals(1, requireNotNull(store.recurringPlans.getPlan(PLAN_ID)).revisions.size)
        assertEquals(V2ShiftLookup.V2(write), store.v2Shifts.getShift(write.shift.id))
    }

    @Test
    fun protectionAndDeletionAreChunkedBeyondSqliteBindLimit() = runBlocking {
        val dates = (0 until LARGE_RECURRING_BATCH_SIZE).map { offset ->
            DATE.plusDays(offset.toLong())
        }
        val writes = dates.mapIndexed { index, date ->
            store.buildTestV2Write(fixture, V2TestIds.uuid(10_000 + index), date)
        }
        val plan = recurringPlan()
        val activeRevision = revision(
            end = dates.last(),
            pattern = RecurringPattern.EveryNDays(1),
        )
        val preview = planNewRecurringPlan(
            plan = plan,
            revision = activeRevision,
            dates = dates,
            candidates = writes,
            existingShifts = emptyList(),
            linkedOccurrences = emptyList(),
            protection = emptyProtection(dates.first(), dates.last()),
            conflictPolicy = RecurringConflictPolicy.KEEP_EXISTING,
            medicalLeaveDates = emptySet(),
        )
        store.recurringShiftWriter.applyRecurringPlanMutation(
            mutation = requireNotNull(preview.mutation),
            expectedPlan = RecurringPlanExpectation.capture(PLAN_ID, null),
            expectedOccupancy = ShiftOccupancyExpectation.capture(
                dates.first(),
                dates.last(),
                emptyList(),
            ),
            expectedPairs = V2ShiftWriteExpectation.EMPTY,
            expectedProtection = emptyProtection(dates.first(), dates.last()),
        )

        val stored = requireNotNull(store.recurringPlans.getPlan(PLAN_ID))
        val shiftIds = writes.mapTo(linkedSetOf()) { it.shift.id }
        val protection = store.recurringPlans.captureProtection(
            shiftIds,
            dates.first(),
            dates.last(),
        )
        assertEquals(LARGE_RECURRING_BATCH_SIZE, protection.versionsByShiftId.size)

        val finalRevision = revision(
            id = FINAL_REVISION_ID,
            number = 2,
            end = dates.last(),
            kind = RecurringPlanRevisionKind.FINALIZED,
            pattern = RecurringPattern.EveryNDays(1),
        )
        val finalization = planRecurringFinalization(
            current = stored,
            finalRevision = finalRevision,
            existingShifts = writes.map { it.shift },
            protection = protection,
        )
        store.recurringShiftWriter.applyRecurringPlanMutation(
            mutation = requireNotNull(finalization.mutation),
            expectedPlan = RecurringPlanExpectation.capture(PLAN_ID, stored),
            expectedOccupancy = ShiftOccupancyExpectation.capture(
                dates.first(),
                dates.last(),
                writes.map { it.shift },
            ),
            expectedPairs = V2ShiftWriteExpectation.capture(writes),
            expectedProtection = protection,
        )

        assertEquals(0, scalar("SELECT COUNT(*) FROM shifts"))
        val finalized = requireNotNull(store.recurringPlans.getPlan(PLAN_ID))
        assertEquals(LARGE_RECURRING_BATCH_SIZE, finalized.occurrences.size)
        assertTrue(finalized.occurrences.all { it.state == RecurringOccurrenceState.RETIRED })
        assertDatabaseIntegrity()
    }

    private suspend fun createPlan(dates: List<LocalDate>, writes: List<V2ShiftWrite>) {
        val prepared = prepareNewPlan(dates, writes)
        store.recurringShiftWriter.applyRecurringPlanMutation(
            mutation = requireNotNull(prepared.mutation),
            expectedPlan = RecurringPlanExpectation.capture(PLAN_ID, null),
            expectedOccupancy = ShiftOccupancyExpectation.capture(dates.first(), dates.last(), emptyList()),
            expectedPairs = V2ShiftWriteExpectation.EMPTY,
            expectedProtection = emptyProtection(dates.first(), dates.last()),
        )
    }

    private fun prepareNewPlan(dates: List<LocalDate>, writes: List<V2ShiftWrite>) =
        planNewRecurringPlan(
            plan = recurringPlan(),
            revision = revision(end = dates.last()),
            dates = dates,
            candidates = writes,
            existingShifts = emptyList(),
            linkedOccurrences = emptyList(),
            protection = com.blackatsystems.miguardia.core.domain.model.RecurringProtectionExpectation.EMPTY,
            conflictPolicy = RecurringConflictPolicy.KEEP_EXISTING,
            medicalLeaveDates = emptySet(),
        )

    private fun recurringPlan() = RecurringPlan(
        PLAN_ID,
        V2TestIds.TIMELINE,
        fixture.revision.value.sector,
        V2TestIds.NOW.plusSeconds(5),
    )

    private fun revision(
        id: UUID = REVISION_ID,
        planId: UUID = PLAN_ID,
        number: Int = 1,
        start: LocalDate = DATE,
        end: LocalDate = DATE,
        kind: RecurringPlanRevisionKind = RecurringPlanRevisionKind.ACTIVE,
        pattern: RecurringPattern = RecurringPattern.EveryNDays(2),
        createdAt: java.time.Instant = V2TestIds.NOW.plusSeconds(5L + number),
    ) = RecurringPlanRevision(
        id = id,
        planId = planId,
        revisionNumber = number,
        effectiveFrom = start,
        kind = kind,
        endDateInclusive = end,
        pattern = pattern,
        templateId = fixture.template.id,
        workPlaceId = fixture.place.id,
        objectiveId = fixture.objective.id,
        workTypeId = fixture.type.id,
        objectiveNameSnapshot = fixture.objective.fullName,
        objectiveAbbreviationSnapshot = fixture.objective.abbreviation,
        objectiveAddressSnapshot = fixture.objective.address,
        workTypeNameSnapshot = fixture.type.name,
        workTypeBehaviorSnapshot = fixture.type.behavior,
        startTimeSnapshot = fixture.template.startTime,
        endTimeSnapshot = fixture.template.endTime,
        colorArgbSnapshot = fixture.template.colorArgb,
        positionSnapshot = null,
        zoneId = V2TestIds.ZONE,
        createdAt = createdAt,
    )

    private fun assertDatabaseIntegrity() {
        database.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }
        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertTrue(!cursor.moveToFirst())
        }
    }

    private fun emptyProtection(start: LocalDate, end: LocalDate) =
        com.blackatsystems.miguardia.core.domain.model.RecurringProtectionExpectation.capture(
            versions = emptyList(),
            startDateInclusive = start,
            endDateInclusive = end,
        )

    private fun scalar(sql: String): Int = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getInt(0)
    }

    private fun openStore() {
        database = MiGuardiaV2Database.build(context, DB)
        store = LocalDataStore(
            database = database,
            recurringClock = Clock.fixed(V2TestIds.NOW, V2TestIds.ZONE),
        )
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
        const val DB = "recurring-plan-persistence-test.db"
        val DATE: LocalDate = V2TestIds.SHIFT_DATE
        val PLAN_ID: UUID = V2TestIds.uuid(190)
        val REVISION_ID: UUID = V2TestIds.uuid(191)
        val FINAL_REVISION_ID: UUID = V2TestIds.uuid(192)
        val SECOND_PLAN_ID: UUID = V2TestIds.uuid(193)
        val SECOND_REVISION_ID: UUID = V2TestIds.uuid(194)
        val THIRD_REVISION_ID: UUID = V2TestIds.uuid(195)
        const val LARGE_RECURRING_BATCH_SIZE: Int = 1_001
    }
}
