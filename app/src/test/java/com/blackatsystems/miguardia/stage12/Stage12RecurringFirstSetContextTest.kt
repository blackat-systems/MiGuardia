package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.work.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** The helper called by the Android fixture, then the actual domain constructor and its guards. */
class Stage12RecurringFirstSetContextTest {
    private class Input {
        val today: LocalDate = LocalDate.of(2026, 11, 1)
        val effective: LocalDate = today.minusYears(1)
        val stamp: Instant = Instant.parse("2026-11-01T18:00:00Z")
        fun id(role: String): UUID = UUID.nameUUIDFromBytes("STAGE12-FIRST-SET-CONTROL/$role".toByteArray(Charsets.UTF_8))
        val timeline = id("timeline")
        val sector = WorkSector.NURSING
        val revision = EffectiveRevision(id("configuration-revision"), effective,
            WorkConfiguration(sector, HoursReference.PendingSetup, AvailabilityLabel.ON_CALL_RETAINER))
        val history = WorkConfigurationHistory(EffectiveDateTimeline(timeline, listOf(revision)), PerPeriodHoursValues(emptyList()))
        val objective = Objective(id("objective"), "Own recurring institution public control", "OWN", "Own synthetic address", null, true, stamp, stamp)
        val place = WorkPlace(id("place"), timeline, sector, objective.id, true, stamp, stamp)
        val type = WorkType.create(id("type"), timeline, sector, "Own recurring type", stamp)
        val template = WorkTemplate(id("primary"), timeline, sector, place.id, objective.id, type.id,
            LocalTime.MIDNIGHT, LocalTime.of(0, 1), 0xFF1E88E5.toInt(), true, stamp, stamp)
        val rule = WorkplaceRuleRevision(id("rule"), timeline, sector, place.id, objective.id, effective,
            WorkplaceRules(NightHoursRule.Disabled, WeekendRule.None, HolidayRule(false, false)), stamp)
        fun build(context: ResolvedWorkConfigurationRevision, firstRule: WorkplaceRuleRevision = rule) =
            FirstWorkSet(objective, place, firstRule, context, type, template)
    }
    private fun rejected(message: String, body: () -> Unit) {
        val error = assertThrows(IllegalArgumentException::class.java) { body() }
        assertEquals(message, error.message)
        assertTrue(error.stackTrace.any { it.className.endsWith(".WorkCatalogKt") && it.methodName == "requireFirstRuleMatchesConfiguration" })
    }
    @Test fun todayReferenceStillFailsTheRealConstructor() {
        val input = Input()
        val wrong = ResolvedWorkConfigurationRevision.resolve(input.history, input.today)
        assertSame(input.revision, wrong.revision)
        rejected("La primera regla debe comenzar junto con la revision de configuracion") { input.build(wrong) }
    }
    @Test fun actualFixtureResolverAlignsTheDateAndKeepsAllOtherArgumentIdentities() {
        val input = Input(); val context = Stage12RecurringFirstSetContext.resolve(input.history, input.rule)
        val set = input.build(context)
        assertEquals(input.effective, context.referenceDate); assertSame(input.revision, context.revision)
        assertSame(input.objective, set.objective); assertSame(input.place, set.workPlace)
        assertSame(input.rule, set.firstRuleRevision); assertSame(context, set.configurationContext)
        assertSame(input.type, set.workType); assertSame(input.template, set.workTemplate)
        assertEquals(input.effective, input.revision.effectiveFrom); assertEquals(input.effective, input.rule.effectiveFrom)
    }
    @Test fun movingOnlyTheRuleDoesNotEvadeTheRevisionGuard() {
        val input = Input(); val changedRule = input.rule.copy(effectiveFrom = input.today)
        val context = Stage12RecurringFirstSetContext.resolve(input.history, changedRule)
        assertEquals(input.today, context.referenceDate); assertSame(input.revision, context.revision)
        rejected("La primera regla debe comenzar junto con la revision de configuracion") { input.build(context, changedRule) }
    }
    @Test fun theCorrectDateDoesNotPermitAnotherTimeline() {
        val input = Input()
        val other = WorkConfigurationHistory(EffectiveDateTimeline(input.id("other-timeline"), listOf(input.revision)), PerPeriodHoursValues(emptyList()))
        val context = Stage12RecurringFirstSetContext.resolve(other, input.rule)
        rejected("La configuracion no pertenece al contexto del lugar") { input.build(context) }
    }
}
