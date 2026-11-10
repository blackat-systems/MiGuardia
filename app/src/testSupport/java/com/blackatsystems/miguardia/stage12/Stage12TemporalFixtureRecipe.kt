package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.work.*
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

/** Complete immutable inputs, computed before any repository or filesystem delegate. */
internal class Stage12TemporalFixtureRecipe(val run: String, val scope: Stage12TemporalScope, val stamp: Instant) {
    val namespace = UUID.nameUUIDFromBytes("$run/temporal/${scope.wire}".toByteArray(Charsets.UTF_8))
    val business = Stage12TemporalRecipe(namespace, stamp)
    val literal = scope.literal
    val zone = Stage12TemporalLiteralOracle.zone
    val ownerDate = literal.ownerDate
    val revision = business.history.timeline.revisions.single()
    val firstSetDate: LocalDate = LocalDate.of(2026, 1, 1)
    val rule = WorkplaceRuleRevision(business.id("workplace-rule"), business.timelineId, WorkSector.NURSING,
        business.placeId, business.objectiveId, firstSetDate,
        WorkplaceRules(NightHoursRule.Disabled, WeekendRule.None, HolidayRule(false, false)), stamp)
    val neighbourTemplate = business.template(LocalTime.of(5, 0), LocalTime.of(5, 30)).copy(id = business.id("neighbour-template"))
    val activeTemplate = business.template(LocalTime.of(20, 0), LocalTime.of(8, 0)).copy(id = business.id("active-template"))
    val isShift: Boolean = when (scope.variant) {
        Stage12TemporalVariant.SAME_DAY, Stage12TemporalVariant.OVERNIGHT, Stage12TemporalVariant.TWENTY_FOUR_HOURS,
        Stage12TemporalVariant.YEAR_CROSSING_SHIFT, Stage12TemporalVariant.HISTORICAL -> true
        Stage12TemporalVariant.TWENTY_FIVE_HOURS, Stage12TemporalVariant.SEVERAL_DAYS, Stage12TemporalVariant.MONTH_CROSSING,
        Stage12TemporalVariant.YEAR_CROSSING_WINDOW, Stage12TemporalVariant.RESUMPTION -> false
    }
    val initialTemplate = if (isShift) business.template(LocalDateTime.parse(literal.localStart).toLocalTime(),
        LocalDateTime.parse(literal.localEnd).toLocalTime()) else neighbourTemplate
    val additionalTemplates: List<WorkTemplate> = when (scope.variant) {
        Stage12TemporalVariant.SAME_DAY, Stage12TemporalVariant.OVERNIGHT, Stage12TemporalVariant.TWENTY_FOUR_HOURS,
        Stage12TemporalVariant.YEAR_CROSSING_SHIFT, Stage12TemporalVariant.HISTORICAL -> listOf(neighbourTemplate)
        Stage12TemporalVariant.RESUMPTION -> listOf(activeTemplate)
        Stage12TemporalVariant.TWENTY_FIVE_HOURS, Stage12TemporalVariant.SEVERAL_DAYS,
        Stage12TemporalVariant.MONTH_CROSSING, Stage12TemporalVariant.YEAR_CROSSING_WINDOW -> emptyList()
    }
    val templates: List<WorkTemplate> = listOf(initialTemplate) + additionalTemplates
    val firstSet = FirstWorkSet(business.objective, business.place, rule, business.configuration(firstSetDate), business.type, initialTemplate)

    private fun seedShift(role: String, template: WorkTemplate, start: Instant, end: Instant): V2ShiftWrite {
        val id = business.id(role)
        return V2ShiftWrite(Shift(id, start, end, zone, ownerDate, "Hospital Temporal", "HTP", "Calle de prueba 120",
            template.startTime, template.endTime, 0xFF336699.toInt(), "Puesto temporal 7", ShiftStatus.PLANNED,
            business.objectiveId, stamp, stamp),
            ShiftWorkSnapshot(id, business.timelineId, WorkSector.NURSING, business.revisionId, business.placeId,
                business.objectiveId, template.id, business.typeId, "Enfermería temporal", WorkTypeBehavior.ACTIVE_WORK))
    }
    // Explicit UTC clock endpoints, independent of a production duration/zone resolver.
    val neighbourShift = seedShift("neighbour-shift", neighbourTemplate,
        ownerDate.atTime(8, 0).toInstant(ZoneOffset.UTC), ownerDate.atTime(8, 30).toInstant(ZoneOffset.UTC))
    val activeShift = if (scope.variant == Stage12TemporalVariant.RESUMPTION)
        seedShift("active-shift", activeTemplate, Instant.parse("2026-10-23T23:00:00Z"), Instant.parse("2026-10-24T11:00:00Z")) else null
    val shifts: List<V2ShiftWrite> = listOf(neighbourShift) + listOfNotNull(activeShift)
    val neighbourWindow = AvailabilityWindowRecord(business.id("neighbour-window"), business.timelineId, WorkSector.NURSING,
        business.revisionId, ownerDate, zone, ownerDate.atTime(3, 30).toInstant(ZoneOffset.UTC),
        ownerDate.atTime(4, 30).toInstant(ZoneOffset.UTC), "Guardia pasiva", stamp, stamp)
    val note = ShiftNote(business.id("neighbour-note"), neighbourShift.shift.id, "Nota vecina temporal propia", stamp, stamp)
    val pngKey = "note_${business.id("neighbour-image")}.png"
    val png: ByteArray get() = Base64.getDecoder().decode(PNG)
    val attachment = ShiftNoteAttachment(business.id("neighbour-attachment"), note.id, pngKey,
        "image/png", png.size.toLong(), 1, 1, 0, stamp, stamp)
    val notice = ShiftNotificationConfig(neighbourShift.shift.id, listOf(11L, 23L))
    val businessRowCount: Int = when (scope.variant) {
        Stage12TemporalVariant.SAME_DAY, Stage12TemporalVariant.OVERNIGHT, Stage12TemporalVariant.TWENTY_FOUR_HOURS,
        Stage12TemporalVariant.YEAR_CROSSING_SHIFT, Stage12TemporalVariant.HISTORICAL -> 16
        Stage12TemporalVariant.RESUMPTION -> 18
        Stage12TemporalVariant.TWENTY_FIVE_HOURS, Stage12TemporalVariant.SEVERAL_DAYS,
        Stage12TemporalVariant.MONTH_CROSSING, Stage12TemporalVariant.YEAR_CROSSING_WINDOW -> 15
    }

    fun expectedRows(): List<Stage12RecurringReadbackOracle.Row> = with(Stage12RecurringReadbackOracle) {
        pendingConfiguration(business.timelineId, revision) + listOf(objective(business.objective), place(business.place),
            type(business.type), disabledWorkplaceRule(rule)) + templates.map(::template) + shifts.flatMap(::write) +
            listOf(availability(neighbourWindow), note(this@Stage12TemporalFixtureRecipe.note), attachment(this@Stage12TemporalFixtureRecipe.attachment)) +
            notification(notice)
    }

    fun requireSafeBeforeDelegates() {
        scope.databaseName(run)
        check(stamp.nano % 1_000_000 == 0)
        Stage12TemporalLiteralOracle.requireConsistent(literal)
        check(revision.effectiveFrom == firstSetDate && revision.value.sector == WorkSector.NURSING &&
            revision.value.hoursReference == HoursReference.PendingSetup && revision.value.availabilityLabel == AvailabilityLabel.PASSIVE_GUARD)
        check(firstSet.configurationContext.timelineId == business.timelineId &&
            firstSet.configurationContext.referenceDate == firstSetDate && firstSet.configurationContext.revision == revision)
        check(templates.map { it.id }.distinct().size == templates.size)
        shifts.forEach { pair ->
            val template = templates.single { it.id == pair.snapshot.templateId }
            check(pair.shift.localStartDate == ownerDate && pair.shift.zoneId == zone && pair.shift.status == ShiftStatus.PLANNED)
            check(pair.shift.startAt.atZone(zone).toLocalTime() == template.startTime && pair.shift.endAt.atZone(zone).toLocalTime() == template.endTime)
            check(pair.shift.startAt < pair.shift.endAt && pair.shift.createdAt == stamp && pair.shift.updatedAt == stamp)
            check(pair.snapshot.timelineId == business.timelineId && pair.snapshot.configurationRevisionId == business.revisionId &&
                pair.snapshot.workPlaceId == business.placeId && pair.snapshot.objectiveId == business.objectiveId && pair.snapshot.workTypeId == business.typeId)
            check(pair.shift.colorArgbSnapshot == template.colorArgb && pair.snapshot.sector == WorkSector.NURSING)
        }
        check(neighbourShift.shift.startAt.atZone(zone).toLocalDate() == ownerDate && neighbourShift.shift.endAt <= literal.start)
        check(neighbourWindow.ownerLocalDate == ownerDate && neighbourWindow.end <= neighbourShift.shift.startAt && neighbourWindow.end <= literal.start)
        check(neighbourWindow.labelSnapshot == "Guardia pasiva" && neighbourWindow.createdAt == stamp && neighbourWindow.updatedAt == stamp)
        check(note.shiftId == neighbourShift.shift.id && attachment.noteId == note.id && notice.shiftId == neighbourShift.shift.id)
        check(expectedRows().size == businessRowCount && expectedRows().all { it.table in businessTables })
    }

    fun requireSeedArgumentBeforeDelegate(value: V2ShiftWrite) {
        val expected = shifts.singleOrNull { it.shift.id == value.shift.id }
        check(expected != null && value == expected) { "TEMPORAL_SEED_ARGUMENT_DIFFERS" }
    }

    /** Independent empty preference protobufs are valid fixture inputs, not copies of received preferences. */
    fun expectedPrivateFiles(includePng: Boolean): Map<String, Stage12FileSnapshot> {
        val empty = digest(byteArrayOf())
        val directories = listOf("files", "files/schedule_photos", "files/datastore", "cache", "no_backup")
            .associateWith { Stage12FileSnapshot(0, empty, Stage12PrivateNodeKind.DIRECTORY) }
        val preferences = preferenceNames.associate { "files/datastore/$it" to Stage12FileSnapshot(0, empty) }
        return directories + preferences + if (includePng) mapOf("files/schedule_photos/$pngKey" to Stage12FileSnapshot(png.size.toLong(), digest(png))) else emptyMap()
    }
    fun expectedPrivatePreferences(): Map<String, String> = preferenceNames.associate {
        "files/datastore/$it#@file" to "BYTES:0:${digest(byteArrayOf())}"
    }

    companion object {
        val preferenceNames = listOf("notification.preferences_pb", "weather.preferences_pb", "summary.preferences_pb", "profile.preferences_pb")
        val businessTables = setOf("objectives", "shifts", "shift_work_snapshots", "explicit_day_statuses", "medical_leaves", "holidays",
            "shift_notes", "shift_note_attachments", "vacations", "schedule_photos", "shift_notification_configs", "shift_notification_reminders",
            "work_configuration_roots", "per_period_hours_definitions", "work_configuration_revisions", "per_period_hours_values", "work_places",
            "work_types", "work_templates", "workplace_rule_revisions", "recurring_plans", "recurring_plan_revisions", "recurring_occurrences",
            "extra_work_classes", "shift_actual_records", "shift_extra_intervals", "independent_extra_work_records", "availability_windows", "shift_situations")
        private const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJZkAAAAASUVORK5CYII="
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
