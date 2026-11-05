package com.blackatsystems.miguardia.stage12

import android.content.Context
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowMutation
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowWriteResult
import com.blackatsystems.miguardia.core.domain.model.ExtraWorkClassWriteResult
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkMutation
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSelection
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkWriteResult
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDraft
import com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftActualWriteResult
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.model.buildShiftActualSaveMutation
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Adds only this family's known sources to a prepared and reopened temporal scope. */
internal object Stage12AvailabilityFixtures {
    data class ReviewCase(
        val key: String,
        val window: AvailabilityWindowRecord,
        val active: List<Stage12AvailabilityOracle.Span>,
        val protected: Boolean,
    )

    data class Prepared(val expected: Stage12StoreSnapshot, val reviews: List<ReviewCase>)

    suspend fun prepare(case: Stage12TemporalCase, context: Context, clock: Clock): Prepared {
        check(context.packageName == "com.blackatsystems.miguardia.qa" && case.scope.wire == "a119-days")
        val fixture = case.fixture
        val store = case.opened.requireBinding(fixture.binding)
        val recipe = fixture.recipe
        val business = recipe.business
        val zone = recipe.zone
        val stamp = recipe.stamp
        val first = recipe.ownerDate
        val mixedDay = first.plusDays(1)
        val medicalDay = first.plusDays(2)
        val vacationDay = first.plusDays(3)
        val priorOwner = first.plusDays(4)
        val unprotectedDay = first.plusDays(5)
        val now = clock.instant()
        check(now.atZone(zone).toLocalDate() > unprotectedDay) { "AVAILABILITY_FIXTURE_PAST_REQUIRED" }
        val futureDay = now.atZone(zone).toLocalDate().plusDays(2)

        fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant =
            date.atTime(hour, minute).atZone(zone).toInstant()

        fun template(role: String, start: Int, end: Int): WorkTemplate =
            business.template(LocalTime.of(start, 0), LocalTime.of(end, 0))
                .copy(id = business.id("availability-template-$role"))

        fun planned(role: String, owner: LocalDate, template: WorkTemplate, start: Instant, end: Instant): V2ShiftWrite {
            val id = business.id("availability-plan-$role")
            return V2ShiftWrite(
                Shift(id, start, end, zone, owner, "Hospital Temporal", "HTP", "Calle de prueba 120",
                    template.startTime, template.endTime, 0xFF336699.toInt(), "Puesto temporal 7", ShiftStatus.PLANNED,
                    business.objectiveId, stamp, stamp),
                ShiftWorkSnapshot(id, business.timelineId, WorkSector.NURSING, business.revisionId,
                    business.placeId, business.objectiveId, template.id, business.typeId,
                    "Enfermería temporal", WorkTypeBehavior.ACTIVE_WORK),
            )
        }

        fun review(key: String, date: LocalDate, start: Int, end: Int, protected: Boolean,
            active: List<Stage12AvailabilityOracle.Span> = emptyList()): ReviewCase =
            ReviewCase(key, AvailabilityWindowRecord(business.id("availability-window-$key"), business.timelineId,
                WorkSector.NURSING, business.revisionId, date, zone, at(date, start), at(date, end),
                "Guardia pasiva", stamp, stamp), active, protected)

        val firstTemplate = template("first", 8, 12)
        val secondTemplate = template("second", 12, 14)
        val nightTemplate = template("prior-owner", 22, 2)
        val templates = listOf(firstTemplate, secondTemplate, nightTemplate)
        val firstPlan = planned("first", mixedDay, firstTemplate, at(mixedDay, 8), at(mixedDay, 12))
        val secondPlan = planned("second", mixedDay, secondTemplate, at(mixedDay, 12), at(mixedDay, 14))
        val priorPlan = planned("prior-owner", priorOwner, nightTemplate, at(priorOwner, 22), at(unprotectedDay, 2))
        val futurePlan = planned("future", futureDay, secondTemplate, at(futureDay, 12), at(futureDay, 14))
        val shifts = listOf(firstPlan, secondPlan, priorPlan, futurePlan)
        val actual = ShiftActualAggregate(ShiftActualRecord(firstPlan.shift.id, business.timelineId, WorkSector.NURSING,
            at(mixedDay, 9), at(mixedDay, 11, 30), "Diferencia sintética de disponibilidad", null, stamp, stamp), emptyList())
        val extraClass = ExtraWorkClass(business.id("availability-extra-class"), business.timelineId, WorkSector.NURSING,
            "Extra disponibilidad propia", "EXTRA DISPONIBILIDAD PROPIA", false, true, true, stamp, stamp)
        val extra = IndependentExtraWorkRecord(business.id("availability-extra"), business.timelineId, WorkSector.NURSING,
            business.revisionId, business.placeId, business.objectiveId, business.typeId, null, extraClass.id, mixedDay, zone,
            at(mixedDay, 11), at(mixedDay, 15), IndependentExtraWorkSnapshot("Hospital Temporal", "HTP",
                "Calle de prueba 120", "Enfermería temporal", WorkTypeBehavior.ACTIVE_WORK, 0xFF336699.toInt(),
                "Puesto temporal 7", "Extra disponibilidad propia", false, true), stamp, stamp)
        val medical = listOf(
            MedicalLeave(business.id("availability-medical"), medicalDay, medicalDay, null, stamp, stamp),
            MedicalLeave(business.id("availability-prior-medical"), priorOwner, priorOwner, null, stamp, stamp),
        )
        val vacation = Vacation(business.id("availability-vacation"), vacationDay, vacationDay, stamp, stamp)
        val reviews = listOf(
            review("mixed", mixedDay, 8, 16, false, listOf(
                Stage12AvailabilityOracle.Span(at(mixedDay, 9), at(mixedDay, 11, 30)),
                Stage12AvailabilityOracle.Span(at(mixedDay, 12), at(mixedDay, 14)),
                Stage12AvailabilityOracle.Span(at(mixedDay, 11), at(mixedDay, 15)),
            )),
            review("medical", medicalDay, 8, 16, true),
            review("vacation", vacationDay, 8, 16, true),
            review("prior-owner", unprotectedDay, 0, 4, false),
            review("future", futureDay, 8, 16, false,
                listOf(Stage12AvailabilityOracle.Span(at(futureDay, 12), at(futureDay, 14)))),
        )

        fun capture() = Stage12FullStoreReadback.captureTemporalOwned(context, case.run, case.scope, fixture.binding)

        Stage12RecurringReadbackOracle.requireExact(capture(), fixture.initialExpected)
        val oracle = Stage12RecurringReadbackOracle
        val mapped = templates.map(oracle::template) + shifts.flatMap(oracle::write) + oracle.actual(actual) +
            medical.map(oracle::medical) + oracle.vacation(vacation) + reviews.map { oracle.availability(it.window) }
        val expected = appendExtraRows(
            Stage12RecurringReadbackOracle.expected(fixture.initialExpected, inserted = mapped), extraClass, extra,
        )

        templates.forEach { store.workCatalog.createWorkTemplate(it) }
        shifts.forEach { store.v2Shifts.insert(it) }
        val actualExpectation = checkNotNull(store.shiftActuals.getExpectation(firstPlan.shift.id))
        check(actualExpectation.planned == firstPlan && actualExpectation.previousActual == null)
        val record = actual.record
        val actualMutation = checkNotNull(buildShiftActualSaveMutation(actualExpectation,
            ShiftActualDraft(record.actualStart, record.actualEnd, record.differenceReason, record.explanation,
                null, null, emptyList()), clock, stamp))
        check(actualMutation.replacement == actual)
        check(store.shiftActuals.save(actualMutation) == ShiftActualWriteResult.Saved(actual))

        check(store.shiftActuals.saveExtraWorkClass(null, extraClass) == ExtraWorkClassWriteResult.Saved(extraClass))
        val selection = IndependentExtraWorkSelection(business.configuration(mixedDay), business.place,
            business.objective, business.type, null, extraClass)
        val extraExpectation = store.independentExtraWork.captureExpectation(
            null, selection, extra.start, extra.end, mixedDay, mixedDay,
        )
        check(extraExpectation.previous == null && extraExpectation.selection == selection &&
            extraExpectation.observedExtras.isEmpty() && extraExpectation.observedProtectedDateRanges.isEmpty() &&
            extraExpectation.observedShifts.map { it.shiftId }.toSet() == setOf(firstPlan.shift.id, secondPlan.shift.id))
        check(store.independentExtraWork.applyMutation(
            IndependentExtraWorkMutation(extraExpectation, extra, overlappingWorkConfirmed = true, protectedDateConfirmed = false),
        ) == IndependentExtraWorkWriteResult.Saved(extra))

        medical.forEach { store.medicalLeaves.create(it) }
        store.vacations.insert(vacation)
        reviews.forEach { item ->
            val window = item.window
            val configuration = business.configuration(window.ownerLocalDate)
            val expectation = store.availabilityWindows.captureExpectation(null, configuration, window.start, window.end)
            val calculation = checkNotNull(expectation.calculation)
            check(expectation.previous == null &&
                expectation.configuration.timelineId == configuration.timelineId &&
                expectation.configuration.referenceDate == configuration.referenceDate &&
                expectation.configuration.revision == configuration.revision &&
                expectation.observedStart == window.start && expectation.observedEnd == window.end &&
                expectation.observedWindows.isEmpty() && calculation.ownerProtected == item.protected)
            check(expectation.protectionFingerprint.isNotEmpty() == (item.protected || item.key == "prior-owner"))
            if (item.key == "prior-owner") {
                check(expectation.observedActiveSources.single().key == "shift:${priorPlan.shift.id}")
            }
            check(calculation.activeWork.map { Stage12AvailabilityOracle.Span(it.start, it.end) }.toSet() ==
                item.active.toSet() && calculation.activeWork.size == item.active.size)
            check(store.availabilityWindows.applyMutation(AvailabilityWindowMutation(expectation, window)) ==
                AvailabilityWindowWriteResult.Saved(window))
        }
        Stage12RecurringReadbackOracle.requireExact(capture(), expected)
        return Prepared(expected, reviews)
    }

    /** Both tables are empty in the temporal baseline and have a single fixture-owned row. */
    private fun appendExtraRows(
        snapshot: Stage12StoreSnapshot,
        extraClass: ExtraWorkClass,
        extra: IndependentExtraWorkRecord,
    ): Stage12StoreSnapshot {
        fun text(value: Any?): String = if (value == null) "N" else Stage12FullStoreReadback.text(value.toString())
        fun integer(value: Long): String = Stage12FullStoreReadback.integer(value)
        fun flag(value: Boolean): String = integer(if (value) 1L else 0L)

        val classCells = mapOf(
            "id" to text(extraClass.id), "timelineId" to text(extraClass.timelineId), "sector" to text(extraClass.sector.name),
            "name" to text(extraClass.name), "normalizedNameKey" to text(extraClass.normalizedNameKey),
            "helpsMeetHoursReference" to flag(extraClass.helpsMeetHoursReference),
            "showDedicatedSummary" to flag(extraClass.showDedicatedSummary), "isActive" to flag(extraClass.isActive),
            "createdAtEpochMillis" to integer(extraClass.createdAt.toEpochMilli()),
            "updatedAtEpochMillis" to integer(extraClass.updatedAt.toEpochMilli()),
        )
        val work = extra.snapshot
        val extraCells = mapOf(
            "id" to text(extra.id), "timelineId" to text(extra.timelineId), "sector" to text(extra.sector.name),
            "configurationRevisionId" to text(extra.configurationRevisionId), "workPlaceId" to text(extra.workPlaceId),
            "objectiveId" to text(extra.objectiveId), "workTypeId" to text(extra.workTypeId),
            "templateId" to text(extra.templateId), "extraWorkClassId" to text(extra.extraWorkClassId),
            "ownerLocalDate" to text(extra.ownerLocalDate), "zoneId" to text(extra.zoneId.id),
            "startEpochMillis" to integer(extra.start.toEpochMilli()), "endEpochMillis" to integer(extra.end.toEpochMilli()),
            "workPlaceNameSnapshot" to text(work.workPlaceName), "workPlaceAbbreviationSnapshot" to text(work.workPlaceAbbreviation),
            "workPlaceAddressSnapshot" to text(work.workPlaceAddress), "workTypeNameSnapshot" to text(work.workTypeName),
            "workTypeBehaviorSnapshot" to text(work.workTypeBehavior.name), "colorArgbSnapshot" to integer(work.colorArgb.toLong()),
            "positionSnapshot" to text(work.position), "classNameSnapshot" to text(work.className),
            "helpsMeetHoursReferenceSnapshot" to flag(work.helpsMeetHoursReference),
            "showDedicatedSummarySnapshot" to flag(work.showDedicatedSummary),
            "createdAtEpochMillis" to integer(extra.createdAt.toEpochMilli()),
            "updatedAtEpochMillis" to integer(extra.updatedAt.toEpochMilli()),
        )
        val expected = mapOf("extra_work_classes" to classCells, "independent_extra_work_records" to extraCells)
        val added = expected.mapValues { (name, cells) ->
            val table = snapshot.tables.getValue(name)
            check(table.rows.isEmpty() && table.columns.toSet() == cells.keys)
            table.copy(rows = listOf(table.columns.map(cells::getValue)))
        }
        return snapshot.copy(tables = snapshot.tables + added)
    }
}
