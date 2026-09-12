package com.blackatsystems.miguardia.core.domain.model

import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftActualTest {
    @Test
    fun noCorrectionUsesPlanningAndEqualRealityDoesNotCreateRecord() {
        assertEquals(PLANNED_START to PLANNED_END, effectiveWorkedInterval(WRITE.shift, null))

        val mutation = buildShiftActualSaveMutation(
            expectation = expectation(),
            draft = draft(PLANNED_START, PLANNED_END),
            clock = CLOCK,
            timestamp = NOW,
        )

        assertNull(mutation)
    }

    @Test
    fun shorterRealityIsEntirelyRegularAndRequiresAReason() {
        val mutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation(),
                draft(
                    start = instant("2026-08-25T22:00:00Z"),
                    end = instant("2026-08-26T05:00:00Z"),
                    reason = "  Salida anticipada ",
                ),
                CLOCK,
                NOW,
            ),
        )

        assertEquals(420L, mutation.replacement.totalMinutes)
        assertEquals(420L, mutation.replacement.regularMinutes)
        assertEquals(0L, mutation.replacement.extraMinutes)
        assertEquals("Salida anticipada", mutation.replacement.record.differenceReason)
        assertThrows(IllegalArgumentException::class.java) {
            buildShiftActualSaveMutation(
                expectation(),
                draft(PLANNED_START, instant("2026-08-26T05:00:00Z"), reason = "  "),
                CLOCK,
                NOW,
            )
        }
    }

    @Test
    fun longerRealityCanRemainEntirelyRegularWithoutAutomaticExtras() {
        val mutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation(),
                draft(
                    start = instant("2026-08-25T20:00:00Z"),
                    end = instant("2026-08-26T07:00:00Z"),
                    choice = ShiftActualDifferenceChoice.ALL_REGULAR,
                ),
                CLOCK,
                NOW,
            ),
        )

        assertEquals(660L, mutation.replacement.totalMinutes)
        assertEquals(660L, mutation.replacement.regularMinutes)
        assertTrue(mutation.replacement.extraIntervals.isEmpty())
    }

    @Test
    fun oneLateFragmentClassifiesTheExactDifferenceWithoutDoubleCounting() {
        val actualEnd = instant("2026-08-26T07:30:00Z")
        val mutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation(),
                draft(
                    start = PLANNED_START,
                    end = actualEnd,
                    choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    selection = ShiftActualClassSelection.Existing(EXTRA_CLASS),
                    fragments = listOf(fragment(PLANNED_END, actualEnd)),
                ),
                CLOCK,
                NOW,
            ),
        )

        assertEquals(630L, mutation.replacement.totalMinutes)
        assertEquals(30L, mutation.replacement.extraMinutes)
        assertEquals(600L, mutation.replacement.regularMinutes)
        assertEquals(mutation.replacement.totalMinutes, mutation.replacement.regularMinutes + mutation.replacement.extraMinutes)
    }

    @Test
    fun earlyEntryAndLateExitCanBeTwoExactDisjointFragments() {
        val actualStart = instant("2026-08-25T20:30:00Z")
        val actualEnd = instant("2026-08-26T07:45:00Z")
        val mutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation(),
                draft(
                    actualStart,
                    actualEnd,
                    choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    selection = ShiftActualClassSelection.Existing(EXTRA_CLASS),
                    fragments = listOf(
                        fragment(PLANNED_END, actualEnd, FRAGMENT_TWO_ID),
                        fragment(actualStart, PLANNED_START, FRAGMENT_ONE_ID),
                    ),
                ),
                CLOCK,
                NOW,
            ),
        )

        assertEquals(listOf(FRAGMENT_ONE_ID, FRAGMENT_TWO_ID), mutation.replacement.extraIntervals.map { it.id })
        assertEquals(75L, mutation.replacement.extraMinutes)
        assertEquals(600L, mutation.replacement.regularMinutes)
    }

    @Test
    fun shiftedEqualDurationHasNoExtraAndShiftedLongerOnlyAcceptsOutsidePlan() {
        val shifted = requireNotNull(
            buildShiftActualSaveMutation(
                expectation(),
                draft(
                    instant("2026-08-25T22:00:00Z"),
                    instant("2026-08-26T08:00:00Z"),
                ),
                CLOCK,
                NOW,
            ),
        )
        assertEquals(600L, shifted.replacement.regularMinutes)

        assertThrows(IllegalArgumentException::class.java) {
            buildShiftActualSaveMutation(
                expectation(),
                draft(
                    instant("2026-08-25T22:00:00Z"),
                    instant("2026-08-26T09:00:00Z"),
                    choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    selection = ShiftActualClassSelection.Existing(EXTRA_CLASS),
                    fragments = listOf(fragment(instant("2026-08-26T06:00:00Z"), instant("2026-08-26T07:00:00Z"))),
                ),
                CLOCK,
                NOW,
            )
        }
        val valid = requireNotNull(
            buildShiftActualSaveMutation(
                expectation(),
                draft(
                    instant("2026-08-25T22:00:00Z"),
                    instant("2026-08-26T09:00:00Z"),
                    choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    selection = ShiftActualClassSelection.Existing(EXTRA_CLASS),
                    fragments = listOf(fragment(instant("2026-08-26T08:00:00Z"), instant("2026-08-26T09:00:00Z"))),
                ),
                CLOCK,
                NOW,
            ),
        )
        assertEquals(60L, valid.replacement.extraMinutes)
    }

    @Test
    fun invalidFragmentsAreRejected() {
        val actualStart = instant("2026-08-25T20:30:00Z")
        val actualEnd = instant("2026-08-26T07:30:00Z")
        val invalidSets = listOf(
            listOf(fragment(instant("2026-08-25T20:00:00Z"), actualStart)),
            listOf(fragment(actualStart, actualStart)),
            listOf(fragment(actualStart, PLANNED_START), fragment(instant("2026-08-25T20:45:00Z"), PLANNED_START, FRAGMENT_TWO_ID)),
            listOf(fragment(actualStart, instant("2026-08-25T20:45:00Z"))),
        )

        invalidSets.forEach { fragments ->
            assertThrows(IllegalArgumentException::class.java) {
                buildShiftActualSaveMutation(
                    expectation(),
                    draft(
                        actualStart,
                        actualEnd,
                        choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                        selection = ShiftActualClassSelection.Existing(EXTRA_CLASS),
                        fragments = fragments,
                    ),
                    CLOCK,
                    NOW,
                )
            }
        }
    }

    @Test
    fun oneCorrectionCannotMixClassesAndSnapshotsBelongToTheObservedClass() {
        val record = record(instant("2026-08-25T20:30:00Z"), instant("2026-08-26T07:30:00Z"))
        val first = interval(record, EXTRA_CLASS, FRAGMENT_ONE_ID, record.actualStart, PLANNED_START)
        val otherClass = EXTRA_CLASS.copy(id = OTHER_CLASS_ID, name = "Servicio extra", normalizedNameKey = "SERVICIO EXTRA")
        val second = interval(record, otherClass, FRAGMENT_TWO_ID, PLANNED_END, record.actualEnd)

        assertThrows(IllegalArgumentException::class.java) {
            ShiftActualAggregate(record, listOf(first, second))
        }
    }

    @Test
    fun aggregateDefensivelyCopiesAndExposesAnUnmodifiableIntervalList() {
        val record = record(instant("2026-08-25T20:30:00Z"), instant("2026-08-26T07:30:00Z"))
        val interval = interval(record, EXTRA_CLASS, FRAGMENT_ONE_ID, record.actualStart, PLANNED_START)
        val source = mutableListOf(interval)

        val aggregate = ShiftActualAggregate(record, source)
        source.clear()

        assertEquals(listOf(interval), aggregate.extraIntervals)
        assertThrows(UnsupportedOperationException::class.java) {
            (aggregate.extraIntervals as MutableList<ShiftExtraInterval>).clear()
        }
        assertEquals(listOf(interval), aggregate.extraIntervals)
    }

    @Test
    fun transitionRejectsInventedSnapshotsAndPreservesAnUnchangedHistoricalSnapshot() {
        val actualEnd = instant("2026-08-26T07:30:00Z")
        val initialExpectation = expectation()
        val initial = requireNotNull(
            buildShiftActualSaveMutation(
                initialExpectation,
                draft(
                    PLANNED_START,
                    actualEnd,
                    choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    selection = ShiftActualClassSelection.Existing(EXTRA_CLASS),
                    fragments = listOf(fragment(PLANNED_END, actualEnd)),
                ),
                CLOCK,
                NOW,
            ),
        )
        requireValidShiftActualTransition(
            initialExpectation,
            initial.replacement,
            initial.selectedClass,
        )
        val forgedCreation = initial.replacement.copy(
            extraIntervals = initial.replacement.extraIntervals.map { interval ->
                interval.copy(classNameSnapshot = "Clase inventada")
            },
        )
        assertThrows(IllegalArgumentException::class.java) {
            requireValidShiftActualTransition(initialExpectation, forgedCreation, EXTRA_CLASS)
        }

        val renamed = EXTRA_CLASS.updated(
            name = "Horas extraordinarias",
            timestamp = NOW.plusMillis(1),
        )
        val correctionExpectation = expectation(
            previousActual = initial.replacement,
            observedClass = renamed,
        )
        val correction = requireNotNull(
            buildShiftActualSaveMutation(
                correctionExpectation,
                draft(
                    PLANNED_START,
                    actualEnd,
                    reason = "Motivo corregido",
                    choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    selection = ShiftActualClassSelection.Existing(renamed),
                    fragments = initial.replacement.extraIntervals.map { interval ->
                        ShiftActualFragmentDraft(interval.id, interval.start, interval.end)
                    },
                ),
                CLOCK,
                NOW.plusMillis(2),
            ),
        )
        requireValidShiftActualTransition(
            correctionExpectation,
            correction.replacement,
            correction.selectedClass,
        )
        assertEquals(
            "Horas extras",
            correction.replacement.extraIntervals.single().classNameSnapshot,
        )
        val forgedCorrection = correction.replacement.copy(
            extraIntervals = correction.replacement.extraIntervals.map { interval ->
                interval.copy(classNameSnapshot = renamed.name)
            },
        )
        assertThrows(IllegalArgumentException::class.java) {
            requireValidShiftActualTransition(correctionExpectation, forgedCorrection, renamed)
        }
    }

    @Test
    fun transitionRequiresTheObservedClassSnapshotWhenReclassifying() {
        val actualEnd = instant("2026-08-26T07:30:00Z")
        val initial = requireNotNull(
            buildShiftActualSaveMutation(
                expectation(),
                draft(
                    PLANNED_START,
                    actualEnd,
                    choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    selection = ShiftActualClassSelection.Existing(EXTRA_CLASS),
                    fragments = listOf(fragment(PLANNED_END, actualEnd)),
                ),
                CLOCK,
                NOW,
            ),
        )
        val reclassified = ExtraWorkClass.create(
            id = OTHER_CLASS_ID,
            timelineId = TIMELINE_ID,
            sector = WorkSector.PRIVATE_SECURITY,
            name = "Servicio extraordinario",
            helpsMeetHoursReference = false,
            showDedicatedSummary = false,
            timestamp = NOW.plusMillis(1),
        )
        val correctionExpectation = expectation(
            previousActual = initial.replacement,
            observedClass = EXTRA_CLASS,
        )
        val correction = requireNotNull(
            buildShiftActualSaveMutation(
                correctionExpectation,
                draft(
                    PLANNED_START,
                    actualEnd,
                    reason = "Reclasificación consciente",
                    choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    selection = ShiftActualClassSelection.Existing(reclassified),
                    fragments = initial.replacement.extraIntervals.map { interval ->
                        ShiftActualFragmentDraft(interval.id, interval.start, interval.end)
                    },
                ),
                CLOCK,
                NOW.plusMillis(2),
            ),
        )
        requireValidShiftActualTransition(
            correctionExpectation,
            correction.replacement,
            reclassified,
        )
        assertEquals(
            reclassified.name,
            correction.replacement.extraIntervals.single().classNameSnapshot,
        )

        val forged = correction.replacement.copy(
            extraIntervals = correction.replacement.extraIntervals.map { interval ->
                interval.copy(
                    classNameSnapshot = EXTRA_CLASS.name,
                    helpsMeetHoursReferenceSnapshot = EXTRA_CLASS.helpsMeetHoursReference,
                    showDedicatedSummarySnapshot = EXTRA_CLASS.showDedicatedSummary,
                )
            },
        )
        assertThrows(IllegalArgumentException::class.java) {
            requireValidShiftActualTransition(correctionExpectation, forged, reclassified)
        }
    }

    @Test
    fun transitionRejectsForgedCreationAndCorrectionTimestamps() {
        val initialExpectation = expectation()
        val initial = requireNotNull(
            buildShiftActualSaveMutation(
                initialExpectation,
                draft(PLANNED_START.plusSeconds(60), PLANNED_END),
                CLOCK,
                NOW,
            ),
        )
        val forgedCreation = initial.replacement.copy(
            record = initial.replacement.record.copy(createdAt = NOW.minusMillis(1)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            requireValidShiftActualTransition(initialExpectation, forgedCreation, null)
        }

        val correctionExpectation = expectation(previousActual = initial.replacement)
        val correction = requireNotNull(
            buildShiftActualSaveMutation(
                correctionExpectation,
                draft(PLANNED_START.plusSeconds(120), PLANNED_END),
                CLOCK,
                NOW.plusMillis(1),
            ),
        )
        requireValidShiftActualTransition(correctionExpectation, correction.replacement, null)
        val forgedCreatedAt = correction.replacement.copy(
            record = correction.replacement.record.copy(createdAt = NOW.minusMillis(1)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            requireValidShiftActualTransition(correctionExpectation, forgedCreatedAt, null)
        }
        val forgedUpdatedAt = correction.replacement.copy(
            record = correction.replacement.record.copy(updatedAt = NOW),
        )
        assertThrows(IllegalArgumentException::class.java) {
            requireValidShiftActualTransition(correctionExpectation, forgedUpdatedAt, null)
        }
    }

    @Test
    fun intervalCanCrossPreviousAndNextDayAndExceedTwentyFourHours() {
        val start = instant("2026-08-24T20:00:00Z")
        val end = instant("2026-08-26T08:00:00Z")
        val mutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation(),
                draft(start, end, choice = ShiftActualDifferenceChoice.ALL_REGULAR),
                CLOCK,
                NOW,
            ),
        )

        assertEquals(36L * 60L, mutation.replacement.totalMinutes)
    }

    @Test
    fun localResolutionHandlesLeapDatesMonthYearBoundariesAndDst() {
        val utc = ZoneOffset.UTC
        assertEquals(
            instant("2028-02-29T23:59:00Z"),
            resolveActualLocalDateTime(LocalDateTime.of(2028, 2, 29, 23, 59), utc),
        )
        assertEquals(
            instant("2027-01-01T00:00:00Z"),
            resolveActualLocalDateTime(LocalDateTime.of(2027, 1, 1, 0, 0), utc),
        )
        val newYork = ZoneId.of("America/New_York")
        assertThrows(IllegalArgumentException::class.java) {
            resolveActualLocalDateTime(LocalDateTime.of(2026, 3, 8, 2, 30), newYork)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveActualLocalDateTime(LocalDateTime.of(2026, 11, 1, 1, 30), newYork)
        }
        val earlier = resolveActualLocalDateTime(
            LocalDateTime.of(2026, 11, 1, 1, 30),
            newYork,
            ZoneOffset.ofHours(-4),
        )
        val later = resolveActualLocalDateTime(
            LocalDateTime.of(2026, 11, 1, 1, 30),
            newYork,
            ZoneOffset.ofHours(-5),
        )
        assertEquals(60L, java.time.Duration.between(earlier, later).toMinutes())
    }

    @Test
    fun archivedClassIsOnlyPreservedForAnUnchangedHistoricalClassification() {
        val activeMutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation(),
                draft(
                    PLANNED_START,
                    instant("2026-08-26T07:30:00Z"),
                    choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    selection = ShiftActualClassSelection.Existing(EXTRA_CLASS),
                    fragments = listOf(fragment(PLANNED_END, instant("2026-08-26T07:30:00Z"))),
                ),
                CLOCK,
                NOW,
            ),
        )
        val archived = EXTRA_CLASS.updated(isActive = false, timestamp = NOW.plusSeconds(1))
        val previous = activeMutation.replacement
        val preserved = buildShiftActualSaveMutation(
            expectation(previousActual = previous, observedClass = archived),
            draft(
                PLANNED_START,
                instant("2026-08-26T07:30:00Z"),
                choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                selection = ShiftActualClassSelection.Existing(archived),
                fragments = previous.extraIntervals.map { ShiftActualFragmentDraft(it.id, it.start, it.end) },
            ),
            CLOCK,
            NOW.plusSeconds(2),
        )
        assertEquals("Horas extras", preserved?.replacement?.extraIntervals?.single()?.classNameSnapshot)

        assertThrows(IllegalArgumentException::class.java) {
            buildShiftActualSaveMutation(
                expectation(previousActual = previous, observedClass = archived),
                draft(
                    PLANNED_START,
                    instant("2026-08-26T08:00:00Z"),
                    choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    selection = ShiftActualClassSelection.Existing(archived),
                    fragments = listOf(fragment(PLANNED_END, instant("2026-08-26T08:00:00Z"))),
                ),
                CLOCK,
                NOW.plusSeconds(2),
            )
        }
    }

    @Test
    fun inlineClassRequiresBothExplicitAnswersAndIsReturnedInsideTheAtomicMutation() {
        val incomplete = ShiftActualClassSelection.NewDraft(OTHER_CLASS_ID, "Servicio extra", true, null)
        assertThrows(IllegalArgumentException::class.java) {
            buildShiftActualSaveMutation(
                expectation(),
                draft(
                    PLANNED_START,
                    instant("2026-08-26T07:30:00Z"),
                    choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    selection = incomplete,
                    fragments = listOf(fragment(PLANNED_END, instant("2026-08-26T07:30:00Z"))),
                ),
                CLOCK,
                NOW,
            )
        }
        val complete = incomplete.copy(showDedicatedSummary = false)
        val mutation = buildShiftActualSaveMutation(
            expectation(),
            draft(
                PLANNED_START,
                instant("2026-08-26T07:30:00Z"),
                choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                selection = complete,
                fragments = listOf(fragment(PLANNED_END, instant("2026-08-26T07:30:00Z"))),
            ),
            CLOCK,
            NOW,
        )
        assertEquals("Servicio extra", mutation?.classToCreate?.name)
        assertFalse(requireNotNull(mutation?.classToCreate).showDedicatedSummary)
    }

    @Test
    fun unfinishedRealityAndSubMinuteValuesAreRejectedByInjectedClock() {
        assertThrows(IllegalArgumentException::class.java) {
            buildShiftActualSaveMutation(
                expectation(),
                draft(PLANNED_START, NOW.plusSeconds(60)),
                CLOCK,
                NOW,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildShiftActualSaveMutation(
                expectation(),
                draft(PLANNED_START.plusSeconds(1), PLANNED_END),
                CLOCK,
                NOW,
            )
        }
    }

    @Test
    fun correctionsPreserveCreationAdvanceMillisecondsAndRejectTemporalOverflow() {
        val first = requireNotNull(
            buildShiftActualSaveMutation(
                expectation(),
                draft(PLANNED_START.plusSeconds(60), PLANNED_END),
                CLOCK,
                NOW,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            buildShiftActualSaveMutation(
                expectation(previousActual = first.replacement),
                draft(PLANNED_START.plusSeconds(120), PLANNED_END),
                CLOCK,
                NOW.plusNanos(999_999),
            )
        }
        val corrected = requireNotNull(
            buildShiftActualSaveMutation(
                expectation(previousActual = first.replacement),
                draft(PLANNED_START.plusSeconds(120), PLANNED_END),
                CLOCK,
                NOW.plusMillis(1),
            ),
        )
        assertEquals(first.replacement.record.createdAt, corrected.replacement.record.createdAt)
        assertEquals(NOW.plusMillis(1), corrected.replacement.record.updatedAt)
        assertThrows(IllegalArgumentException::class.java) {
            exactDurationMinutes(Instant.MIN, Instant.MAX)
        }
    }

    private fun expectation(
        previousActual: ShiftActualAggregate? = null,
        observedClass: ExtraWorkClass? = null,
    ) = ShiftActualExpectation(
        planned = WRITE,
        previousActual = previousActual,
        observedClass = observedClass,
        recurringOccurrence = null,
        protectionFingerprint = "context-v1",
    )

    private fun draft(
        start: Instant,
        end: Instant,
        reason: String = "Horario informado",
        choice: ShiftActualDifferenceChoice? = null,
        selection: ShiftActualClassSelection? = null,
        fragments: List<ShiftActualFragmentDraft> = emptyList(),
    ) = ShiftActualDraft(start, end, reason, null, choice, selection, fragments)

    private fun fragment(start: Instant, end: Instant, id: UUID = FRAGMENT_ONE_ID) =
        ShiftActualFragmentDraft(id, start, end)

    private fun record(start: Instant, end: Instant) = ShiftActualRecord(
        SHIFT_ID,
        TIMELINE_ID,
        WorkSector.PRIVATE_SECURITY,
        start,
        end,
        "Horario informado",
        null,
        NOW,
        NOW,
    )

    private fun interval(
        record: ShiftActualRecord,
        extraClass: ExtraWorkClass,
        id: UUID,
        start: Instant,
        end: Instant,
    ) = ShiftExtraInterval(
        id,
        record.shiftId,
        record.timelineId,
        record.sector,
        extraClass.id,
        start,
        end,
        extraClass.name,
        extraClass.helpsMeetHoursReference,
        extraClass.showDedicatedSummary,
        NOW,
        NOW,
    )

    private companion object {
        val SHIFT_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000001")
        val TIMELINE_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000002")
        val CONFIGURATION_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000003")
        val PLACE_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000004")
        val OBJECTIVE_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000005")
        val TEMPLATE_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000006")
        val TYPE_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000007")
        val CLASS_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000008")
        val OTHER_CLASS_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000009")
        val FRAGMENT_ONE_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000010")
        val FRAGMENT_TWO_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000011")
        val PLANNED_START: Instant = instant("2026-08-25T21:00:00Z")
        val PLANNED_END: Instant = instant("2026-08-26T07:00:00Z")
        val NOW: Instant = instant("2026-08-27T12:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val WRITE = V2ShiftWrite(
            shift = Shift(
                id = SHIFT_ID,
                startAt = PLANNED_START,
                endAt = PLANNED_END,
                zoneId = ZoneOffset.UTC,
                localStartDate = LocalDate.of(2026, 8, 25),
                objectiveNameSnapshot = "Central",
                objectiveAbbreviationSnapshot = "CTR",
                objectiveAddressSnapshot = null,
                startTimeSnapshot = LocalTime.of(21, 0),
                endTimeSnapshot = LocalTime.of(7, 0),
                colorArgbSnapshot = 0xff112233.toInt(),
                position = null,
                status = ShiftStatus.PLANNED,
                sourceObjectiveId = OBJECTIVE_ID,
                createdAt = instant("2026-08-20T12:00:00Z"),
                updatedAt = instant("2026-08-20T12:00:00Z"),
            ),
            snapshot = ShiftWorkSnapshot(
                shiftId = SHIFT_ID,
                timelineId = TIMELINE_ID,
                sector = WorkSector.PRIVATE_SECURITY,
                configurationRevisionId = CONFIGURATION_ID,
                workPlaceId = PLACE_ID,
                objectiveId = OBJECTIVE_ID,
                templateId = TEMPLATE_ID,
                workTypeId = TYPE_ID,
                workTypeNameSnapshot = "Guardia habitual",
                workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
            ),
        )
        val EXTRA_CLASS = ExtraWorkClass.create(
            id = CLASS_ID,
            timelineId = TIMELINE_ID,
            sector = WorkSector.PRIVATE_SECURITY,
            name = "Horas extras",
            helpsMeetHoursReference = true,
            showDedicatedSummary = true,
            timestamp = instant("2026-08-20T12:00:00Z"),
        )

        fun instant(value: String): Instant = Instant.parse(value)
    }
}
