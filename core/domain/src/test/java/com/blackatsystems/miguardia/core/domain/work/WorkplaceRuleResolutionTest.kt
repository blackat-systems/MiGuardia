package com.blackatsystems.miguardia.core.domain.work

import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.RetroactiveWorkplaceRuleException
import com.blackatsystems.miguardia.core.domain.shift.buildV2ShiftWrite
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkplaceRuleResolutionTest {
    @Test
    fun shiftFromDayThirtyOneToDayOneUsesOneRulePerCivilSegment() {
        val write = write(
            date = LocalDate.of(2026, 8, 31),
            start = LocalTime.of(22, 0),
            end = LocalTime.of(6, 0),
        )
        val augustRule = rule(RULE_ID, LocalDate.of(2026, 8, 1), showHoliday = false)
        val septemberRule = rule(OTHER_RULE_ID, LocalDate.of(2026, 9, 1), showHoliday = true)

        val segments = resolveWorkplaceRuleSegments(
            write.shift,
            write.snapshot,
            listOf(septemberRule, augustRule),
        )

        assertEquals(
            listOf(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1)),
            segments.map { it.localDate },
        )
        assertEquals(augustRule, segments[0].ruleRevision)
        assertEquals(septemberRule, segments[1].ruleRevision)
    }

    @Test
    fun shiftEndingExactlyAtMidnightDoesNotOccupyNextCivilDay() {
        val write = write(
            date = LocalDate.of(2026, 8, 31),
            start = LocalTime.of(20, 0),
            end = LocalTime.MIDNIGHT,
        )

        val segments = resolveWorkplaceRuleSegments(
            write.shift,
            write.snapshot,
            listOf(rule(RULE_ID, LocalDate.of(2026, 8, 1))),
        )

        assertEquals(listOf(LocalDate.of(2026, 8, 31)), segments.map { it.localDate })
    }

    @Test
    fun missingRuleForAnyTouchedDateFailsInsteadOfPartiallyResolving() {
        val write = write(
            date = LocalDate.of(2026, 8, 31),
            start = LocalTime.of(22, 0),
            end = LocalTime.of(6, 0),
        )

        assertThrows(InvalidLocalDataException::class.java) {
            resolveWorkplaceRuleSegments(
                write.shift,
                write.snapshot,
                listOf(rule(RULE_ID, LocalDate.of(2026, 9, 1))),
            )
        }
    }

    @Test
    fun retroactiveRuleCannotReachAnyShiftWhoseStartAlreadyArrived() {
        val started = write(
            date = LocalDate.of(2026, 9, 5),
            start = LocalTime.of(8, 0),
            end = LocalTime.of(16, 0),
        ).let { it.copy(shift = it.shift.copy(status = ShiftStatus.CANCELLED)) }
        val candidate = rule(OTHER_RULE_ID, LocalDate.of(2026, 9, 1))

        assertThrows(RetroactiveWorkplaceRuleException::class.java) {
            validateWorkplaceRuleInsertion(
                candidate = candidate,
                existingRevisions = listOf(rule(RULE_ID, LocalDate.of(2026, 8, 1))),
                existingV2Shifts = listOf(started),
                confirmationNow = started.shift.startAt,
            )
        }
    }

    @Test
    fun futureShiftMayReceiveRuleAndLaterRevisionLimitsAffectedInterval() {
        val afterLaterRevision = write(
            date = LocalDate.of(2026, 9, 11),
            start = LocalTime.of(8, 0),
            end = LocalTime.of(16, 0),
        )
        val candidate = rule(OTHER_RULE_ID, LocalDate.of(2026, 9, 1))

        validateWorkplaceRuleInsertion(
            candidate = candidate,
            existingRevisions = listOf(
                rule(RULE_ID, LocalDate.of(2026, 8, 1)),
                rule(THIRD_RULE_ID, LocalDate.of(2026, 9, 10)),
            ),
            existingV2Shifts = listOf(afterLaterRevision),
            confirmationNow = afterLaterRevision.shift.startAt.plusSeconds(1),
        )
    }

    @Test
    fun futureRuleMayAffectFutureShiftWithoutRewritingSnapshot() {
        val future = write(
            date = LocalDate.of(2026, 9, 5),
            start = LocalTime.of(8, 0),
            end = LocalTime.of(16, 0),
        )
        val originalSnapshot = future.snapshot

        validateWorkplaceRuleInsertion(
            candidate = rule(OTHER_RULE_ID, LocalDate.of(2026, 9, 1)),
            existingRevisions = listOf(rule(RULE_ID, LocalDate.of(2026, 8, 1))),
            existingV2Shifts = listOf(future),
            confirmationNow = future.shift.startAt.minusSeconds(1),
        )

        assertEquals(originalSnapshot, future.snapshot)
    }

    private fun write(
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
    ) = buildV2ShiftWrite(
        id = SHIFT_ID,
        date = date,
        objective = Objective(
            OBJECTIVE_ID,
            "Hospital Norte",
            "HNO",
            null,
            null,
            true,
            CREATED_AT,
            CREATED_AT,
        ),
        workPlace = WorkPlace(
            PLACE_ID,
            TIMELINE_ID,
            WorkSector.MEDICINE,
            OBJECTIVE_ID,
            true,
            CREATED_AT,
            CREATED_AT,
        ),
        workType = WorkType.create(
            TYPE_ID,
            TIMELINE_ID,
            WorkSector.MEDICINE,
            "Jornada habitual",
            CREATED_AT,
        ),
        template = WorkTemplate(
            TEMPLATE_ID,
            TIMELINE_ID,
            WorkSector.MEDICINE,
            PLACE_ID,
            OBJECTIVE_ID,
            TYPE_ID,
            start,
            end,
            0xFF336699.toInt(),
            true,
            null,
            CREATED_AT,
            CREATED_AT,
        ),
        configurationContext = ResolvedWorkConfigurationRevision.resolve(
            history = WorkConfigurationHistory(
                origin = WorkConfigurationOrigin.NEW_V2,
                timeline = EffectiveDateTimeline(
                    TIMELINE_ID,
                    listOf(
                        EffectiveRevision(
                            CONFIGURATION_REVISION_ID,
                            LocalDate.of(2026, 8, 1),
                            WorkConfiguration(WorkSector.MEDICINE, HoursReference.PendingSetup, null),
                        ),
                    ),
                ),
                perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
            ),
            date = date,
        ),
        position = null,
        timestamp = CREATED_AT,
        zoneId = ZONE,
    )

    private fun rule(
        id: UUID,
        date: LocalDate,
        showHoliday: Boolean = false,
    ) = WorkplaceRuleRevision(
        id,
        TIMELINE_ID,
        WorkSector.MEDICINE,
        PLACE_ID,
        OBJECTIVE_ID,
        date,
        WorkplaceRules(
            NightHoursRule.Disabled,
            WeekendRule.None,
            HolidayRule(differentTreatment = showHoliday, showDedicatedSummary = showHoliday),
        ),
        CREATED_AT,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-01T12:00:00Z")
        val ZONE: ZoneId = ZoneId.of("UTC")
        val TIMELINE_ID: UUID = UUID.fromString("85000000-0000-0000-0000-000000000001")
        val OBJECTIVE_ID: UUID = UUID.fromString("85000000-0000-0000-0000-000000000002")
        val PLACE_ID: UUID = UUID.fromString("85000000-0000-0000-0000-000000000003")
        val TYPE_ID: UUID = UUID.fromString("85000000-0000-0000-0000-000000000004")
        val TEMPLATE_ID: UUID = UUID.fromString("85000000-0000-0000-0000-000000000005")
        val CONFIGURATION_REVISION_ID: UUID = UUID.fromString("85000000-0000-0000-0000-000000000006")
        val SHIFT_ID: UUID = UUID.fromString("85000000-0000-0000-0000-000000000007")
        val RULE_ID: UUID = UUID.fromString("85000000-0000-0000-0000-000000000008")
        val OTHER_RULE_ID: UUID = UUID.fromString("85000000-0000-0000-0000-000000000009")
        val THIRD_RULE_ID: UUID = UUID.fromString("85000000-0000-0000-0000-000000000010")
    }
}
