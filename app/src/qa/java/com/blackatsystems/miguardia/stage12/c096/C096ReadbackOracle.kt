package com.blackatsystems.miguardia.stage12.c096

import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrence
import com.blackatsystems.miguardia.core.domain.model.RecurringPattern
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanAggregate
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevision
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.ShiftSituation
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import java.util.Base64
import java.util.UUID

internal data class C096TableSnapshot(val columns: List<String>, val rows: List<List<String>>)
internal data class C096StoreSnapshot(
    val tables: Map<String, C096TableSnapshot>,
    val preferences: Map<String, String>,
    val files: Map<String, C096FileSnapshot>,
)

/** Expected rows come from the frozen fixture/operation recipe, never from preview.mutation. */
internal object C096RecurringReadbackOracle {
    data class Row(val table: String, val values: Map<String, String>)
    private val keys = mapOf(
        "explicit_day_statuses" to listOf("localDate"),
        "shifts" to listOf("id"), "shift_work_snapshots" to listOf("shiftId"),
        "recurring_plans" to listOf("id"), "recurring_plan_revisions" to listOf("id"),
        "recurring_occurrences" to listOf("planId", "localDate"), "shift_notes" to listOf("id"),
        "medical_leaves" to listOf("id"), "vacations" to listOf("id"), "holidays" to listOf("id"),
        "shift_situations" to listOf("shiftId"), "shift_actual_records" to listOf("shiftId"),
        "shift_extra_intervals" to listOf("id"),
        "availability_windows" to listOf("id"), "objectives" to listOf("id"),
        "work_places" to listOf("id"), "work_types" to listOf("id"), "work_templates" to listOf("id"),
        "work_configuration_roots" to listOf("timelineId"), "work_configuration_revisions" to listOf("id"),
        "workplace_rule_revisions" to listOf("id"), "shift_note_attachments" to listOf("id"),
        "shift_notification_configs" to listOf("shiftId"), "shift_notification_reminders" to listOf("shiftId", "leadMinutes"),
    )

    private fun value(value: Any?): String = when (value) {
        null -> "N"
        is Boolean -> if (value) "I:1" else "I:0"
        is Int, is Long -> "I:$value"
        is Double -> "R:" + java.lang.Double.doubleToRawLongBits(value).toString(16)
        else -> "T:" + Base64.getEncoder().encodeToString(value.toString().toByteArray(Charsets.UTF_8))
    }
    private fun row(table: String, vararg values: Pair<String, Any?>) =
        Row(table, values.associate { it.first to value(it.second) }).also { check(it.values.size == values.size) }

    fun availability(v: AvailabilityWindowRecord) = row("availability_windows", "id" to v.id,
        "timelineId" to v.timelineId, "sector" to v.sector.name, "configurationRevisionId" to v.configurationRevisionId,
        "ownerLocalDate" to v.ownerLocalDate, "zoneId" to v.zoneId.id, "startEpochMillis" to v.start.toEpochMilli(),
        "endEpochMillis" to v.end.toEpochMilli(), "labelSnapshot" to v.labelSnapshot,
        "createdAtEpochMillis" to v.createdAt.toEpochMilli(), "updatedAtEpochMillis" to v.updatedAt.toEpochMilli())

    fun objective(v: Objective) = row("objectives", "id" to v.id, "fullName" to v.fullName,
        "abbreviation" to v.abbreviation, "address" to v.address, "note" to v.note, "isActive" to v.isActive,
        "createdAtEpochMillis" to v.createdAt.toEpochMilli(), "updatedAtEpochMillis" to v.updatedAt.toEpochMilli(),
        "weatherLatitude" to v.weatherLatitude, "weatherLongitude" to v.weatherLongitude)

    fun place(v: WorkPlace) = row("work_places", "id" to v.id, "timelineId" to v.timelineId, "sector" to v.sector.name,
        "objectiveId" to v.objectiveId, "isActive" to v.isActive,
        "createdAtEpochMillis" to v.createdAt.toEpochMilli(), "updatedAtEpochMillis" to v.updatedAt.toEpochMilli())

    fun type(v: WorkType) = row("work_types", "id" to v.id, "timelineId" to v.timelineId, "sector" to v.sector.name,
        "name" to v.name, "normalizedNameKey" to v.normalizedNameKey, "behavior" to v.behavior.name,
        "isActive" to v.isActive, "createdAtEpochMillis" to v.createdAt.toEpochMilli(), "updatedAtEpochMillis" to v.updatedAt.toEpochMilli())

    fun template(v: WorkTemplate) = row("work_templates", "id" to v.id, "timelineId" to v.timelineId, "sector" to v.sector.name,
        "workPlaceId" to v.workPlaceId, "objectiveId" to v.objectiveId, "workTypeId" to v.workTypeId,
        "startTime" to v.startTime, "endTime" to v.endTime, "colorArgb" to v.colorArgb, "isActive" to v.isActive,
        "createdAtEpochMillis" to v.createdAt.toEpochMilli(), "updatedAtEpochMillis" to v.updatedAt.toEpochMilli())

    /** This fixture admits a pending reference only; enabled reference kinds require their own complete mapper. */
    fun pendingConfiguration(timelineId: UUID, revision: EffectiveRevision<WorkConfiguration>): List<Row> {
        check(revision.value.hoursReference == HoursReference.PendingSetup && revision.value.hoursReferenceStartedOn == null)
        return listOf(row("work_configuration_roots", "timelineId" to timelineId, "singletonSlot" to 1),
            row("work_configuration_revisions", "id" to revision.id, "timelineId" to timelineId,
                "effectiveFrom" to revision.effectiveFrom, "sector" to revision.value.sector.name,
                "availabilityLabel" to revision.value.availabilityLabel?.name, "hoursReferenceKind" to "PENDING_SETUP",
                "periodKind" to null, "weeklyFirstDayIso" to null, "cycleAnchorDate" to null,
                "cycleLengthDays" to null, "requiredMinutes" to null, "perPeriodDefinitionId" to null, "hoursReferenceStartedOn" to null))
    }

    fun disabledWorkplaceRule(v: WorkplaceRuleRevision): Row {
        check(v.rules.nightHours == NightHoursRule.Disabled && v.rules.weekend == WeekendRule.None)
        return row("workplace_rule_revisions", "id" to v.id, "timelineId" to v.timelineId, "sector" to v.sector.name,
            "workPlaceId" to v.workPlaceId, "objectiveId" to v.objectiveId, "effectiveFrom" to v.effectiveFrom,
            "nightRuleCode" to "DISABLED", "nightStartTime" to null, "nightEndTime" to null,
            "nightDifferentTreatment" to null, "nightShowDedicatedSummary" to null, "weekendRuleCode" to "NONE",
            "weekendDifferentTreatment" to null, "weekendShowDedicatedSummary" to null,
            "holidayDifferentTreatment" to v.rules.holiday.differentTreatment,
            "holidayShowDedicatedSummary" to v.rules.holiday.showDedicatedSummary, "createdAtEpochMillis" to v.createdAt.toEpochMilli())
    }

    fun attachment(v: ShiftNoteAttachment) = row("shift_note_attachments", "id" to v.id, "noteId" to v.noteId,
        "storageKey" to v.storageKey, "mimeType" to v.mimeType, "byteSize" to v.byteSize,
        "pixelWidth" to v.pixelWidth, "pixelHeight" to v.pixelHeight, "position" to v.position,
        "createdAtEpochMillis" to v.createdAt.toEpochMilli(), "updatedAtEpochMillis" to v.updatedAt.toEpochMilli())

    fun notification(v: ShiftNotificationConfig): List<Row> = listOf(row("shift_notification_configs", "shiftId" to v.shiftId)) +
        v.reminderLeadMinutes.map { lead -> row("shift_notification_reminders", "shiftId" to v.shiftId, "leadMinutes" to lead) }

    fun write(write: V2ShiftWrite): List<Row> = listOf(
        write.shift.let { row("shifts", "id" to it.id, "startEpochMillis" to it.startAt.toEpochMilli(),
            "endEpochMillis" to it.endAt.toEpochMilli(), "zoneId" to it.zoneId.id, "localStartDate" to it.localStartDate,
            "objectiveNameSnapshot" to it.objectiveNameSnapshot, "objectiveAbbreviationSnapshot" to it.objectiveAbbreviationSnapshot,
            "objectiveAddressSnapshot" to it.objectiveAddressSnapshot, "startTimeSnapshot" to it.startTimeSnapshot,
            "endTimeSnapshot" to it.endTimeSnapshot, "colorArgbSnapshot" to it.colorArgbSnapshot, "position" to it.position,
            "status" to it.status.name, "sourceObjectiveId" to it.sourceObjectiveId,
            "createdAtEpochMillis" to it.createdAt.toEpochMilli(), "updatedAtEpochMillis" to it.updatedAt.toEpochMilli()) },
        write.snapshot.let { row("shift_work_snapshots", "shiftId" to it.shiftId, "timelineId" to it.timelineId,
            "sector" to it.sector.name, "configurationRevisionId" to it.configurationRevisionId, "workPlaceId" to it.workPlaceId,
            "objectiveId" to it.objectiveId, "templateId" to it.templateId, "workTypeId" to it.workTypeId,
            "workTypeNameSnapshot" to it.workTypeNameSnapshot, "workTypeBehaviorSnapshot" to it.workTypeBehaviorSnapshot.name) },
    )

    fun plan(aggregate: RecurringPlanAggregate): List<Row> = listOf(aggregate.plan.let {
        row("recurring_plans", "id" to it.id, "timelineId" to it.timelineId, "sector" to it.sector.name,
            "createdAtEpochMillis" to it.createdAt.toEpochMilli())
    }) + aggregate.revisions.map(::revision) + aggregate.occurrences.map(::occurrence)

    private fun revision(r: RecurringPlanRevision): Row {
        val p = r.pattern
        val kind = when (p) {
            is RecurringPattern.Weekdays -> "WEEKDAYS"
            is RecurringPattern.EveryNDays -> "EVERY_N_DAYS"
            is RecurringPattern.EveryNWeeks -> "EVERY_N_WEEKS"
            is RecurringPattern.Monthly -> "MONTHLY"
        }
        return row("recurring_plan_revisions", "id" to r.id, "planId" to r.planId, "revisionNumber" to r.revisionNumber,
            "effectiveFrom" to r.effectiveFrom, "kind" to r.kind.name, "endDateInclusive" to r.endDateInclusive, "patternKind" to kind,
            "weekdaysMask" to (p as? RecurringPattern.Weekdays)?.days?.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) },
            "intervalCount" to when (p) { is RecurringPattern.EveryNDays -> p.intervalCount; is RecurringPattern.EveryNWeeks -> p.intervalCount; else -> null },
            "monthlyOrdinal" to (p as? RecurringPattern.Monthly)?.ordinal?.name,
            "monthlyDayOfWeek" to (p as? RecurringPattern.Monthly)?.dayOfWeek?.value,
            "templateId" to r.templateId, "workPlaceId" to r.workPlaceId, "objectiveId" to r.objectiveId, "workTypeId" to r.workTypeId,
            "objectiveNameSnapshot" to r.objectiveNameSnapshot, "objectiveAbbreviationSnapshot" to r.objectiveAbbreviationSnapshot,
            "objectiveAddressSnapshot" to r.objectiveAddressSnapshot, "workTypeNameSnapshot" to r.workTypeNameSnapshot,
            "workTypeBehaviorSnapshot" to r.workTypeBehaviorSnapshot.name, "startTimeSnapshot" to r.startTimeSnapshot,
            "endTimeSnapshot" to r.endTimeSnapshot, "colorArgbSnapshot" to r.colorArgbSnapshot, "positionSnapshot" to r.positionSnapshot,
            "zoneId" to r.zoneId.id, "createdAtEpochMillis" to r.createdAt.toEpochMilli())
    }

    fun occurrence(o: RecurringOccurrence) = row("recurring_occurrences", "planId" to o.planId,
        "localDate" to o.localDate, "revisionId" to o.revisionId, "shiftId" to o.shiftId, "state" to o.state.name,
        "createdAtEpochMillis" to o.createdAt.toEpochMilli(), "updatedAtEpochMillis" to o.updatedAt.toEpochMilli())

    fun note(n: ShiftNote) = row("shift_notes", "id" to n.id, "shiftId" to n.shiftId, "body" to n.body,
        "createdAtEpochMillis" to n.createdAt.toEpochMilli(), "updatedAtEpochMillis" to n.updatedAt.toEpochMilli())

    fun medical(v: MedicalLeave) = row("medical_leaves", "id" to v.id, "startDate" to v.startDate,
        "endDateInclusive" to v.endDateInclusive, "privateNote" to v.privateNote,
        "createdAtEpochMillis" to v.createdAt.toEpochMilli(), "updatedAtEpochMillis" to v.updatedAt.toEpochMilli())

    fun vacation(v: Vacation) = row("vacations", "id" to v.id, "startDate" to v.startDate,
        "endDateInclusive" to v.endDateInclusive, "createdAtEpochMillis" to v.createdAt.toEpochMilli(),
        "updatedAtEpochMillis" to v.updatedAt.toEpochMilli())

    fun holiday(v: Holiday) = row("holidays", "id" to v.id, "localDate" to v.date, "name" to v.name,
        "createdAtEpochMillis" to v.createdAt.toEpochMilli(), "updatedAtEpochMillis" to v.updatedAt.toEpochMilli())

    fun situation(v: ShiftSituation) = row("shift_situations", "shiftId" to v.shiftId, "status" to v.status.name,
        "reason" to v.reason, "privateNote" to v.privateNote, "cancellationActor" to v.cancellationActor?.name,
        "notifiedAtEpochMillis" to v.notifiedAt?.toEpochMilli(), "evidenceStorageKey" to v.evidence?.storageKey,
        "evidenceMimeType" to v.evidence?.mimeType, "evidenceByteSize" to v.evidence?.byteSize,
        "evidencePixelWidth" to v.evidence?.pixelWidth, "evidencePixelHeight" to v.evidence?.pixelHeight,
        "evidenceSha256" to v.evidence?.sha256, "createdAtEpochMillis" to v.createdAt.toEpochMilli(),
        "updatedAtEpochMillis" to v.updatedAt.toEpochMilli())

    fun actual(v: ShiftActualAggregate): List<Row> = listOf(v.record.let {
        row("shift_actual_records", "shiftId" to it.shiftId, "timelineId" to it.timelineId,
            "sector" to it.sector.name, "actualStartEpochMillis" to it.actualStart.toEpochMilli(),
            "actualEndEpochMillis" to it.actualEnd.toEpochMilli(), "differenceReason" to it.differenceReason,
            "explanation" to it.explanation, "createdAtEpochMillis" to it.createdAt.toEpochMilli(),
            "updatedAtEpochMillis" to it.updatedAt.toEpochMilli())
    }) + v.extraIntervals.map {
        row("shift_extra_intervals", "id" to it.id, "shiftId" to it.shiftId, "timelineId" to it.timelineId,
            "sector" to it.sector.name, "extraWorkClassId" to it.extraWorkClassId,
            "startEpochMillis" to it.start.toEpochMilli(), "endEpochMillis" to it.end.toEpochMilli(),
            "classNameSnapshot" to it.classNameSnapshot,
            "helpsMeetHoursReferenceSnapshot" to it.helpsMeetHoursReferenceSnapshot,
            "showDedicatedSummarySnapshot" to it.showDedicatedSummarySnapshot,
            "createdAtEpochMillis" to it.createdAt.toEpochMilli(), "updatedAtEpochMillis" to it.updatedAt.toEpochMilli())
    }

    fun expected(
        before: C096StoreSnapshot,
        removed: List<Row> = emptyList(),
        inserted: List<Row> = emptyList(),
        files: Map<String, C096FileSnapshot> = before.files,
    ): C096StoreSnapshot {
        fun key(r: Row): Pair<String, List<String>> = r.table to checkNotNull(keys[r.table]).map { r.values.getValue(it) }
        check(removed.map(::key).distinct().size == removed.size && inserted.map(::key).distinct().size == inserted.size) {
            "RECURRING_EXPECTATION_DUPLICATE_KEY"
        }
        val rows = before.tables.mapValues { (_, table) -> table.rows.toMutableList() }.toMutableMap()
        fun encoded(r: Row): List<String> {
            val columns = before.tables.getValue(r.table).columns
            check(columns.toSet() == r.values.keys) { "RECURRING_EXPECTATION_COLUMNS" }
            return columns.map { r.values.getValue(it) }
        }
        removed.forEach { r ->
            val list = rows.getValue(r.table); val row = encoded(r)
            check(list.count { it == row } == 1 && list.remove(row)) { "RECURRING_EXPECTATION_BASELINE_DIFFERS" }
        }
        inserted.forEach { r ->
            val table = before.tables.getValue(r.table); val indices = keys.getValue(r.table).map(table.columns::indexOf)
            val row = encoded(r); val list = rows.getValue(r.table)
            check(list.none { old -> indices.all { old[it] == row[it] } }) { "RECURRING_EXPECTATION_INSERT_COLLISION" }
            list += row
        }
        return before.copy(tables = before.tables.mapValues { (name, table) ->
            table.copy(rows = rows.getValue(name).sortedBy { it.joinToString("\u0000") })
        }, files = files.toSortedMap())
    }

    fun requireExact(actual: C096StoreSnapshot, expected: C096StoreSnapshot) {
        check(actual == expected) { "RECURRING_FULL_STORE_TRANSITION_DIFFERS" }
    }
}
