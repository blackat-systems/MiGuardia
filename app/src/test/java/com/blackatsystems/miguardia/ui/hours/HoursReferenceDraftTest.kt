package com.blackatsystems.miguardia.ui.hours

import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursEntry
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HoursReferenceDraftTest {
    @Test
    fun visibleChoicesDoNotInventAPeriodOrAmount() {
        val pending = review(HoursReferenceDraft(choice = HoursReferenceChoice.PENDING))
        val notUsed = review(HoursReferenceDraft(choice = HoursReferenceChoice.NOT_USED))
        val unknown = review(
            HoursReferenceDraft(
                choice = HoursReferenceChoice.UNKNOWN,
                periodChoice = HoursPeriodChoice.NONE,
            ),
        )

        assertEquals(HoursReference.PendingSetup, pending.reference)
        assertEquals(HoursReference.NotUsed, notUsed.reference)
        assertEquals(HoursReference.Unknown(), unknown.reference)
        assertNull(pending.initialValue)
        assertNull(notUsed.initialValue)
        assertNull(unknown.initialValue)
    }

    @Test
    fun fixedReferenceKeepsExactMinutesAndWarnsAboutShortFirstMonth() {
        val result = review(
            HoursReferenceDraft(
                choice = HoursReferenceChoice.FIXED,
                periodChoice = HoursPeriodChoice.MONTHLY,
                requiredMinutes = "7650",
            ),
        )

        assertEquals(
            HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(7_650)),
            result.reference,
        )
        assertEquals(TODAY, result.startedOn)
        assertEquals(LocalDate.of(2026, 9, 1), result.naturalWindowEndExclusive)
        assertTrue(result.isShortFirstSegment)
        assertFalse(result.isPast)
    }

    @Test
    fun nextWeeklyAndCycleBoundariesUseTheExplicitPattern() {
        val weekly = review(
            HoursReferenceDraft(
                choice = HoursReferenceChoice.UNKNOWN,
                periodChoice = HoursPeriodChoice.WEEKLY,
                weeklyFirstDay = DayOfWeek.THURSDAY,
                startChoice = ReferenceStartChoice.NEXT_PERIOD,
            ),
        )
        val cycle = review(
            HoursReferenceDraft(
                choice = HoursReferenceChoice.UNKNOWN,
                periodChoice = HoursPeriodChoice.CYCLE,
                cycleAnchorDate = "2026-08-01",
                cycleLengthDays = "21",
                startChoice = ReferenceStartChoice.NEXT_PERIOD,
            ),
        )

        assertEquals(LocalDate.of(2026, 8, 27), weekly.startedOn)
        assertEquals(LocalDate.of(2026, 9, 12), cycle.startedOn)
        assertFalse(weekly.isShortFirstSegment)
        assertFalse(cycle.isShortFirstSegment)
    }

    @Test
    fun nextPeriodCannotSilentlyFallBackToTodayWhenNoPeriodExists() {
        assertThrows(IllegalArgumentException::class.java) {
            review(
                HoursReferenceDraft(
                    choice = HoursReferenceChoice.UNKNOWN,
                    periodChoice = HoursPeriodChoice.NONE,
                    startChoice = ReferenceStartChoice.NEXT_PERIOD,
                ),
            )
        }
    }

    @Test
    fun perPeriodKeepsMissingDistinctFromAnExplicitInitialValue() {
        val missing = review(
            HoursReferenceDraft(
                choice = HoursReferenceChoice.PER_PERIOD,
                periodChoice = HoursPeriodChoice.MONTHLY,
                initialPerPeriodMinutes = "",
                definitionId = DEFINITION_ID,
                valueId = VALUE_ID,
            ),
        )
        val defined = review(
            HoursReferenceDraft(
                choice = HoursReferenceChoice.PER_PERIOD,
                periodChoice = HoursPeriodChoice.MONTHLY,
                initialPerPeriodMinutes = "8400",
                definitionId = DEFINITION_ID,
                valueId = VALUE_ID,
            ),
        )

        assertNull(missing.initialValue)
        assertEquals(PositiveMinutes(8_400), defined.initialValue?.requiredMinutes)
        assertEquals(DEFINITION_ID, defined.initialValue?.key?.definitionId)
    }

    @Test
    fun customPastAndFutureDatesArePreservedButPreTimelineDateIsRejected() {
        val past = review(
            HoursReferenceDraft(
                choice = HoursReferenceChoice.UNKNOWN,
                periodChoice = HoursPeriodChoice.MONTHLY,
                startChoice = ReferenceStartChoice.CUSTOM,
                customStartDate = "2026-08-10",
            ),
        )
        val future = review(
            HoursReferenceDraft(
                choice = HoursReferenceChoice.UNKNOWN,
                periodChoice = HoursPeriodChoice.MONTHLY,
                startChoice = ReferenceStartChoice.CUSTOM,
                customStartDate = "2026-09-10",
            ),
        )

        assertTrue(past.isPast)
        assertEquals(LocalDate.of(2026, 9, 10), future.startedOn)
        assertThrows(IllegalArgumentException::class.java) {
            review(
                HoursReferenceDraft(
                    choice = HoursReferenceChoice.UNKNOWN,
                    periodChoice = HoursPeriodChoice.MONTHLY,
                    startChoice = ReferenceStartChoice.CUSTOM,
                    customStartDate = "2026-07-31",
                ),
            )
        }
    }

    @Test
    fun invalidAmountsAndCyclesFailBeforeAnyWriteCanBeRequested() {
        assertThrows(IllegalStateException::class.java) {
            review(
                HoursReferenceDraft(
                    choice = HoursReferenceChoice.FIXED,
                    periodChoice = HoursPeriodChoice.MONTHLY,
                    requiredMinutes = "0",
                ),
            )
        }
        assertThrows(IllegalStateException::class.java) {
            review(
                HoursReferenceDraft(
                    choice = HoursReferenceChoice.UNKNOWN,
                    periodChoice = HoursPeriodChoice.CYCLE,
                    cycleAnchorDate = "2026-08-01",
                    cycleLengthDays = "-1",
                ),
            )
        }
    }

    @Test
    fun consciousRestartOfTheSamePerPeriodReferenceKeepsItsDefinitionIdentity() {
        val reference = HoursReference.PerPeriod(DEFINITION_ID, HoursPeriod.Monthly)
        val value = PerPeriodHoursEntry(
            VALUE_ID,
            reference.keyContaining(TODAY),
            PositiveMinutes(8_400),
        )
        val history = WorkConfigurationHistory(
            EffectiveDateTimeline(
                TIMELINE_ID,
                listOf(
                    EffectiveRevision(
                        REVISION_ID,
                        LocalDate.of(2026, 8, 1),
                        WorkConfiguration(
                            WorkSector.NURSING,
                            reference,
                            null,
                            hoursReferenceStartedOn = LocalDate.of(2026, 8, 1),
                        ),
                    ),
                ),
            ),
            PerPeriodHoursValues(listOf(value)),
        )
        val draft = HoursReferenceDraft(
            choice = HoursReferenceChoice.PER_PERIOD,
            periodChoice = HoursPeriodChoice.MONTHLY,
            definitionId = uuid(99),
            initialPerPeriodMinutes = "8400",
        )

        val result = buildHoursReferenceReview(history, TODAY, draft)

        assertEquals(reference, result.reference)
        assertEquals(TODAY, result.startedOn)
        assertNull(result.initialValue)
        assertThrows(IllegalArgumentException::class.java) {
            buildHoursReferenceReview(
                history,
                TODAY,
                draft.copy(initialPerPeriodMinutes = "9000"),
            )
        }
    }

    private fun review(draft: HoursReferenceDraft): HoursReferenceReview =
        buildHoursReferenceReview(HISTORY, TODAY, draft)

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 8, 25)
        val TIMELINE_ID: UUID = uuid(1)
        val REVISION_ID: UUID = uuid(2)
        val DEFINITION_ID: UUID = uuid(3)
        val VALUE_ID: UUID = uuid(4)
        val HISTORY = WorkConfigurationHistory(
            timeline = EffectiveDateTimeline(
                TIMELINE_ID,
                listOf(
                    EffectiveRevision(
                        REVISION_ID,
                        LocalDate.of(2026, 8, 1),
                        WorkConfiguration(
                            WorkSector.NURSING,
                            HoursReference.PendingSetup,
                            availabilityLabel = null,
                        ),
                    ),
                ),
            ),
            perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
        )

        fun uuid(value: Int): UUID = UUID.fromString(
            "93000000-0000-0000-0000-${value.toString().padStart(12, '0')}",
        )
    }
}
