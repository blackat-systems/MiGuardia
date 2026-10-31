package com.blackatsystems.miguardia.stage12

import java.time.LocalDate
import java.time.YearMonth
import com.blackatsystems.miguardia.core.domain.work.normalizeNewWorkPlaceAbbreviation
import org.junit.Assert.*
import org.junit.Test

class Stage12AospFixturePlanTest {
    @Test fun syntheticPlaceAbbreviationPassesTheRealNewPlaceValidator() {
        assertEquals(Stage12AospFixturePlan.ABBREVIATION,
            normalizeNewWorkPlaceAbbreviation(Stage12AospFixturePlan.ABBREVIATION))
    }
    @Test fun sourcesLeaveThePastOracleDateEmptyAndKeepFutureSourceLater() {
        val plan = Stage12AospFixturePlan(LocalDate.of(2026, 10, 27))
        assertEquals(LocalDate.of(2026, 10, 24), plan.pastSource)
        assertEquals(LocalDate.of(2026, 10, 25), plan.c090EmptyDate)
        assertEquals(LocalDate.of(2026, 10, 29), plan.futureSource)
        assertFalse(plan.c090EmptyDate in plan.sourceDates)
        assertEquals(1, plan.batches.size)
    }
    @Test fun monthYearAndLeapBoundariesUseNormalSeparateMonthlyBatches() {
        for (day in listOf("2026-01-01", "2026-12-31", "2028-03-01", "2026-02-28")) {
            val plan = Stage12AospFixturePlan(LocalDate.parse(day))
            assertEquals(2, plan.batches.size)
            assertEquals(plan.sourceDates, plan.batches.flatten().toSet())
            assertTrue(plan.batches.all { it.map(YearMonth::from).distinct().size == 1 })
            assertEquals(plan.pastSource, plan.batches.first().min())
        }
    }
    @Test fun receivedBusinessRowsOrConfigurationNeverAuthorizePreparation() {
        val empty = mapOf("shifts" to 0, "shift_work_snapshots" to 0, "room_master_table" to 1, "sqlite_master" to 100)
        Stage12AospFixturePlan.requireEmptyWork(false, empty)
        assertThrows(IllegalStateException::class.java) { Stage12AospFixturePlan.requireEmptyWork(true, empty) }
        assertThrows(IllegalStateException::class.java) { Stage12AospFixturePlan.requireEmptyWork(false, empty + ("shifts" to 1)) }
        assertThrows(IllegalStateException::class.java) { Stage12AospFixturePlan.requireEmptyWork(false, empty + ("unknown_business" to 1)) }
        assertThrows(IllegalStateException::class.java) { Stage12AospFixturePlan.requireEmptyWork(false, emptyMap()) }
    }
    @Test fun midnightMissingDuplicateAndExtraRowsCannotProduceACompleteFixture() {
        val plan = Stage12AospFixturePlan(LocalDate.of(2026, 10, 27))
        plan.requireSameDay(plan.anchor);plan.requireExactSources(plan.sourceDates.toList())
        assertThrows(IllegalStateException::class.java) { plan.requireSameDay(plan.anchor.plusDays(1)) }
        for (dates in listOf(emptyList(), listOf(plan.pastSource), listOf(plan.pastSource, plan.pastSource),
            plan.sourceDates.toList() + plan.anchor)) {
            assertThrows(IllegalStateException::class.java) { plan.requireExactSources(dates) }
        }
    }
    @Test fun fixtureAndX015StatusAreSanitizedBeforeAndAfterReadiness() {
        for (selector in listOf(Stage12RuntimeTarget.WORK_FIXTURE, Stage12AdmissionProjection.X015_SELECTOR)) {
            assertTrue(Stage12AdmissionProjection.needsSafeStatus(false, selector, null))
            assertTrue(Stage12AdmissionProjection.needsSafeStatus(false, "other", null, setOf(selector)))
            assertTrue(Stage12AdmissionProjection.needsSafeTerminal(false, false, setOf(selector)))
            val projected = Stage12AdmissionProjection.status(selector, mapOf("numtests" to 1, "stack" to "PRIVATE_CANARY"), -2, Stage12ActivityAdmission(false))
            assertFalse(projected.toString().contains("PRIVATE_CANARY"))
        }
    }
}
