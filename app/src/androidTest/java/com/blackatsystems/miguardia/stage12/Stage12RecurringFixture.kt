package com.blackatsystems.miguardia.stage12

import android.content.Context
import android.content.ContextWrapper
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.work.*
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.first

/** Immutable expected input produced before the tested UI flow. No planner result is used as its oracle. */
internal data class Stage12RecurringFixture(
    val run: String,
    val ownCase: Stage12RecurringCase,
    val today: LocalDate,
    val zone: ZoneId,
    val stamp: Instant,
    val configurationRevisionId: UUID,
    val objective: Objective,
    val place: WorkPlace,
    val type: WorkType,
    val individualType: WorkType,
    val primary: WorkTemplate,
    val individualAlternative: WorkTemplate,
    val changeTemplate: WorkTemplate,
    val main: RecurringPlanAggregate,
    val otherPlans: List<RecurringPlanAggregate>,
    val writes: Map<UUID, V2ShiftWrite>,
    val notes: Map<UUID, ShiftNoteWithAttachments>,
    val notifications: Map<UUID, ShiftNotificationConfig>,
    val actual: ShiftActualAggregate,
    val extraClass: ExtraWorkClass,
    val situation: ShiftSituation,
    val medical: MedicalLeave,
    val originalFiles: Map<String, Pair<Long, String>>,
) {
    fun id(role: String): UUID = UUID.nameUUIDFromBytes("$run/${ownCase.wire}/fixture/$role".toByteArray(Charsets.UTF_8))
    fun date(offset: Int): LocalDate = today.plusDays(offset.toLong())
    fun mainWrite(offset: Int): V2ShiftWrite = writes.getValue(id("main-$offset"))
    val plans: List<RecurringPlanAggregate> get() = listOf(main) + otherPlans

    fun expectedWrite(id: UUID, date: LocalDate, template: WorkTemplate, position: String?, timestamp: Instant): V2ShiftWrite {
        val selectedType = listOf(type, individualType).single { it.id == template.workTypeId }
        val start = date.atTime(template.startTime).atZone(zone).toInstant()
        val endDate = if (template.endTime > template.startTime) date else date.plusDays(1)
        return V2ShiftWrite(Shift(id, start, endDate.atTime(template.endTime).atZone(zone).toInstant(), zone, date,
            objective.fullName, objective.abbreviation, objective.address, template.startTime, template.endTime,
            template.colorArgb, position, ShiftStatus.PLANNED, objective.id, timestamp, timestamp),
            ShiftWorkSnapshot(id, place.timelineId, place.sector, configurationRevisionId, place.id, objective.id,
                template.id, selectedType.id, selectedType.name, selectedType.behavior))
    }

    fun expectedRevision(id: UUID, planId: UUID, number: Int, from: LocalDate, end: LocalDate, interval: Int,
        template: WorkTemplate, position: String?, timestamp: Instant): RecurringPlanRevision {
        val selectedType = listOf(type, individualType).single { it.id == template.workTypeId }
        return RecurringPlanRevision(id, planId, number,
        from, RecurringPlanRevisionKind.ACTIVE, end, RecurringPattern.EveryNDays(interval), template.id, place.id,
        objective.id, selectedType.id, objective.fullName, objective.abbreviation, objective.address, selectedType.name, selectedType.behavior,
        template.startTime, template.endTime, template.colorArgb, position, zone, timestamp)
    }

    suspend fun validate(store: LocalDataStore, photoStore: SchedulePhotoFileStore, prefix: Stage12RecurringFixturePrefix? = null) {
        fun <T> step(name: String, body: () -> T): T = if (prefix == null) body() else prefix.call(name, body)
        suspend fun <T> action(name: String, body: suspend () -> T): T = if (prefix == null) body() else prefix.callSuspend(name, body)
        step("VALIDATE_ENTRY") { Unit }
        plans.forEachIndexed { planIndex, expected ->
            val plan = action("VALIDATE_PLAN/$planIndex/READ") { store.recurringPlans.getPlan(expected.plan.id) }
            step("VALIDATE_PLAN/$planIndex/COMPARE") { check(plan == expected) { "RECURRING_FIXTURE_AGGREGATE_DIFFERS" } }
            expected.occurrences.filter { it.shiftId != null }.forEachIndexed { index, occurrence ->
                val shiftId = step("VALIDATE_LINK/$planIndex/$index/ID") { checkNotNull(occurrence.shiftId) }
                val found = action("VALIDATE_LINK/$planIndex/$index/READ") { store.recurringPlans.getOccurrenceForShift(shiftId) }
                step("VALIDATE_LINK/$planIndex/$index/COMPARE") { check(found == occurrence) { "RECURRING_FIXTURE_LINK_DIFFERS" } }
            }
        }
        writes.values.forEachIndexed { index, expected ->
            val found = action("VALIDATE_SHIFT/$index/READ") { store.v2Shifts.getShift(expected.shift.id) }
            step("VALIDATE_SHIFT/$index/COMPARE") { check(found == V2ShiftLookup.V2(expected)) { "RECURRING_FIXTURE_PAIR_DIFFERS" } }
        }
        val allFlow = action("VALIDATE_ALL_FLOW") { store.v2Shifts.observeAll(place.timelineId, place.sector) }
        val all = action("VALIDATE_ALL_READ") { allFlow.first() }
        step("VALIDATE_ALL_COMPARE") { check(all.associateBy { it.shift.id } == writes) { "RECURRING_FIXTURE_UNEXPECTED_SHIFT" } }
        notes.values.forEachIndexed { index, expected ->
            val found = action("VALIDATE_NOTE/$index/READ") { store.shiftNotes.getWithAttachments(expected.note.id) }
            step("VALIDATE_NOTE/$index/COMPARE") { check(found == expected) { "RECURRING_FIXTURE_NOTE_DIFFERS" } }
        }
        notifications.entries.forEachIndexed { index, (id, expected) ->
            val found = action("VALIDATE_NOTICE/$index/READ") { store.shiftNotificationConfigs.getForShift(id) }
            step("VALIDATE_NOTICE/$index/COMPARE") { check(found == expected) }
        }
        val actualId = step("VALIDATE_ACTUAL_ID") { id("main-0") }
        val actualRead = action("VALIDATE_ACTUAL_READ") { store.shiftActuals.getExpectation(actualId) }
        val actualExpectation = step("VALIDATE_ACTUAL_NONNULL") { checkNotNull(actualRead) }
        step("VALIDATE_ACTUAL_COMPARE") { check(actualExpectation.planned == mainWrite(0) && actualExpectation.previousActual == actual && actualExpectation.observedClass == extraClass) }
        val situationRead = action("VALIDATE_SITUATION_READ") { store.shiftSituations.inspect(situation.shiftId) }
        step("VALIDATE_SITUATION_COMPARE") { check(situationRead?.previous == situation) }
        val medicalFlow = action("VALIDATE_MEDICAL_FLOW") { store.medicalLeaves.observeIntersecting(medical.startDate, medical.endDateInclusive) }
        val medicalRead = action("VALIDATE_MEDICAL_READ") { medicalFlow.first() }
        step("VALIDATE_MEDICAL_COMPARE") { check(medicalRead == listOf(medical)) }
        originalFiles.entries.forEachIndexed { index, (key, expected) ->
            val file = step("VALIDATE_FILE/$index/FILE") { photoStore.file(key) }
            val length = step("VALIDATE_FILE/$index/LENGTH") { file.length() }
            step("VALIDATE_FILE/$index/LENGTH_CHECK") { check(length == expected.first) { "RECURRING_FIXTURE_FILE_DIFFERS" } }
            val bytes = step("VALIDATE_FILE/$index/BYTES") { file.readBytes() }
            val hash = step("VALIDATE_FILE/$index/DIGEST") { digest(bytes) }
            step("VALIDATE_FILE/$index/DIGEST_CHECK") { check(hash == expected.second) { "RECURRING_FIXTURE_FILE_DIFFERS" } }
        }
        step("VALIDATE_RETURN") { Unit }
    }

    companion object {
        private const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJZkAAAAASUVORK5CYII="
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

        suspend fun create(context: Context, run: String, ownCase: Stage12RecurringCase, store: LocalDataStore,
            clock: Clock, zone: ZoneId, dataRoot: File, binding: Stage12RecurringPaths, prefix: Stage12RecurringFixturePrefix): Stage12RecurringFixture {
            prefix.call("CREATE_ENTRY") { Unit }
            fun <T> step(name: String, body: () -> T): T = prefix.call(name, body)
            suspend fun <T> action(name: String, body: suspend () -> T): T = prefix.callSuspend(name, body)
            step("PACKAGE_CHECK") { check(context.packageName == "com.blackatsystems.miguardia.qa") }
            step("BINDING_CHECK") { check(binding.run == run && binding.ownCase == ownCase) }
            val access = step("PATH_ACCESS") { stage12RecurringPathAccess(context) }
            val expectedDataRoot = step("REVALIDATE") { binding.revalidate(access).dataRoot }
            step("DATA_ROOT_CHECK") { check(dataRoot.isDirectory && !Files.isSymbolicLink(dataRoot.toPath()) && dataRoot.absoluteFile == expectedDataRoot) {
                "RECURRING_FIXTURE_FILES_FENCE"
            } }
            val today = step("DATE") { LocalDate.now(clock.withZone(zone)) }
            val stamp = step("STAMP") { clock.instant().truncatedTo(ChronoUnit.MILLIS).minusSeconds(120) }
            fun id(role: String) = UUID.nameUUIDFromBytes("$run/${ownCase.wire}/fixture/$role".toByteArray(Charsets.UTF_8))
            fun date(n: Int) = today.plusDays(n.toLong())
            val actualStart = step("ACTUAL_START") { today.atStartOfDay(zone).toInstant() }
            val actualEnd = step("ACTUAL_END") { actualStart.plusSeconds(180) }
            step("ACTUAL_PAST") { check(actualEnd <= clock.instant()) { "RECURRING_FIXTURE_ACTUAL_END_NOT_PAST" } }
            val sector = WorkSector.NURSING; val timeline = step("TIMELINE") { id("timeline") }
            val revision = step("CONFIG_REVISION") { EffectiveRevision(id("configuration-revision"), today.minusYears(1),
                WorkConfiguration(sector, HoursReference.PendingSetup, AvailabilityLabel.ON_CALL_RETAINER)) }
            action("CONFIG_CREATE") { store.workConfiguration.createInitial(timeline, revision) }
            val objective = step("OBJECTIVE_BUILD") { Objective(id("objective"), "Own recurring institution $run", "OWN", "Own synthetic address", null, true, stamp, stamp) }
            val place = step("PLACE_BUILD") { WorkPlace(id("place"), timeline, sector, objective.id, true, stamp, stamp) }
            val type = step("TYPE_BUILD") { WorkType.create(id("type"), timeline, sector, "Own recurring type", stamp) }
            val individualType = step("INDIVIDUAL_TYPE_BUILD") { WorkType.create(id("individual-type"), timeline, sector, "Own individual type", stamp) }
            val primary = step("PRIMARY_BUILD") { WorkTemplate(id("primary"), timeline, sector, place.id, objective.id, type.id,
                LocalTime.MIDNIGHT, LocalTime.of(0, 1), 0xFF1E88E5.toInt(), true, stamp, stamp) }
            val alternative = step("ALTERNATIVE_BUILD") { primary.copy(id = id("individual-alternative"), workTypeId = individualType.id, colorArgb = 0xFF009688.toInt()) }
            val changeTemplate = step("CHANGE_BUILD") { primary.copy(id = id("change-template"), startTime = LocalTime.of(0, 10), endTime = LocalTime.of(0, 11), colorArgb = 0xFFD84315.toInt()) }
            val rule = step("RULE_BUILD") { WorkplaceRuleRevision(id("rule"), timeline, sector, place.id, objective.id, today.minusYears(1),
                WorkplaceRules(NightHoursRule.Disabled, WeekendRule.None, HolidayRule(false, false)), stamp) }
            val configurationRead = action("CONFIG_READ") { store.workConfiguration.get() }
            val configuration = step("CONFIG_NONNULL") { checkNotNull(configurationRead) }
            val resolved = step("CONFIG_RESOLVE") { Stage12RecurringFirstSetContext.resolve(configuration, rule) }
            val firstSet = step("FIRST_SET_BUILD") { FirstWorkSet(objective, place, rule, resolved, type, primary) }
            action("FIRST_SET_CREATE") { store.workCatalog.createFirstWorkSet(firstSet) }
            action("INDIVIDUAL_TYPE_CREATE") { store.workCatalog.createWorkType(individualType) }
            action("ALTERNATIVE_CREATE") { store.workCatalog.createWorkTemplate(alternative) }
            action("CHANGE_CREATE") { store.workCatalog.createWorkTemplate(changeTemplate) }
            fun write(role: String, offset: Int, position: String? = "Own automatic position"): V2ShiftWrite {
                val d = date(offset)
                return V2ShiftWrite(Shift(id(role), d.atStartOfDay(zone).toInstant(), d.atStartOfDay(zone).plusMinutes(1).toInstant(),
                    zone, d, objective.fullName, objective.abbreviation, objective.address, primary.startTime, primary.endTime,
                    primary.colorArgb, position, ShiftStatus.PLANNED, objective.id, stamp, stamp),
                    ShiftWorkSnapshot(id(role), timeline, sector, revision.id, place.id, objective.id, primary.id, type.id, type.name, type.behavior))
            }
            val setup = step("WRITES_PLAN") {
                val lifecycle = ownCase in setOf(Stage12RecurringCase.LIFECYCLE, Stage12RecurringCase.SINGLE_OCCURRENCE)
                val single = ownCase == Stage12RecurringCase.SINGLE_OCCURRENCE
                val customized = if (lifecycle) 2 else 4
                val excluded = if (lifecycle) if (single) emptySet() else setOf(3) else setOf(1, 2)
                val retired = if (lifecycle) setOf(9) else emptySet()
                val statusOffset = if (lifecycle) 7 else 5
                Triple(Triple(lifecycle, single, customized), excluded to retired, statusOffset)
            }
            val (lifecycle, single, customized) = setup.first
            val (excluded, retired) = setup.second; val statusOffset = setup.third
            val writes = linkedMapOf<UUID, V2ShiftWrite>()
            (-2..12).filter { it !in excluded && it !in retired }.forEach { offset ->
                step("MAIN_BUILD/${offset + 2}") {
                var w = write("main-$offset", offset, if (offset == customized) "Own customized position" else "Own automatic position")
                if (offset == statusOffset) w = w.copy(shift = w.shift.copy(status = ShiftStatus.ABSENT))
                writes[w.shift.id] = w
                }
            }
            if (!lifecycle) listOf(2, 9).forEachIndexed { index, offset -> step("MANUAL_BUILD/$index") { val w = write("manual-$offset", offset, "Own manual position"); writes[w.shift.id] = w } }
            val second = step("SECOND_BUILD") { (if (!lifecycle) write("replaceable-other", 3) else null).also { value -> value?.let { writes[it.shift.id] = it } } }
            val alien = step("ALIEN_BUILD") { write("alien-shift", 20, "Own unrelated position").also { writes[it.shift.id] = it } }
            writes.values.forEachIndexed { index, value -> action("SHIFT_INSERT/$index") { store.v2Shifts.insert(value) } }
            val fileContext = step("FILE_CONTEXT") { object : ContextWrapper(context) { override fun getFilesDir() = dataRoot } }
            val photoStore = step("PHOTO_STORE") { SchedulePhotoFileStore(fileContext) }
            val bytes = step("PNG") { Base64.getDecoder().decode(PNG) }
            val originalFiles = step("FILE_EXPECTATIONS") { linkedMapOf<String, Pair<Long, String>>() }
            fun image(filePrefix: String, role: String, measuredRole: String): String {
                val key = step("IMAGE_$measuredRole/KEY") { "${filePrefix}_${id(role)}.png" }
                val file = step("IMAGE_$measuredRole/FILE") { photoStore.file(key) }
                step("IMAGE_$measuredRole/PARENT_READY") { check(file.parentFile.isDirectory || file.parentFile.mkdirs()) }
                step("IMAGE_$measuredRole/CREATE") { check(file.createNewFile()) }
                step("IMAGE_$measuredRole/WRITE") { file.writeBytes(bytes) }
                step("IMAGE_$measuredRole/EXPECTATION") { originalFiles[key] = bytes.size.toLong() to digest(bytes) }
                return key
            }
            val notes = step("NOTE_EXPECTATIONS") { linkedMapOf<UUID, ShiftNoteWithAttachments>() }
            suspend fun note(offset: Int, role: String, measuredRole: String) {
                val n = step("NOTE_$measuredRole/BUILD") { ShiftNote(id("$role-note"), id("main-$offset"), "Own note $role", stamp.plusSeconds(5), stamp.plusSeconds(5)) }
                val key = image("note", "$role-image", measuredRole)
                val a = step("NOTE_$measuredRole/ATTACHMENT") { ShiftNoteAttachment(id("$role-attachment"), n.id, key, "image/png", bytes.size.toLong(), 1, 1, 0, n.createdAt, n.updatedAt) }
                val saveAttachments = step("NOTE_$measuredRole/SAVE_ARGUMENT") { listOf(a) }
                action("NOTE_$measuredRole/SAVE") { store.shiftNotes.saveWithAttachments(n, saveAttachments) }
                step("NOTE_$measuredRole/EXPECTATION") { notes[n.shiftId] = ShiftNoteWithAttachments(n, listOf(a)) }
            }
            note(if (lifecycle) 4 else 6, "protection", "PROTECTION")
            if (single) note(0, "edited-target", "EDITED_TARGET")
            val notifications = step("NOTICE_EXPECTATIONS") { linkedMapOf<UUID, ShiftNotificationConfig>() }
            for ((index, offset) in (if (single) listOf(0, 5) else listOf(if (lifecycle) 5 else 7)).withIndex()) {
                val config = step("NOTICE/$index/BUILD") { ShiftNotificationConfig(id("main-$offset"), listOf(11, 23)) }
                action("NOTICE/$index/REPLACE") { store.shiftNotificationConfigs.replace(config) }
                step("NOTICE/$index/EXPECTATION") { notifications[config.shiftId] = config }
            }
            val extraClass = step("EXTRA_CLASS_BUILD") { ExtraWorkClass.create(id("extra-class"), timeline, sector, "Own explicit extra class", false, true, stamp.plusSeconds(10)) }
            val extraSaved = action("EXTRA_CLASS_SAVE") { store.shiftActuals.saveExtraWorkClass(null, extraClass) }
            step("EXTRA_CLASS_SAVED") { check(extraSaved is ExtraWorkClassWriteResult.Saved) }
            val actualStamp = step("ACTUAL_STAMP") { stamp.plusSeconds(15) }
            val actualRecord = step("ACTUAL_RECORD") { ShiftActualRecord(id("main-0"), timeline, sector, actualStart, actualEnd,
                "Own actual difference", "Own explanation", actualStamp, actualStamp) }
            val actualIntervals = listOf(1, 2).map { minute ->
                step("ACTUAL_INTERVAL/$minute") { ShiftExtraInterval(id("actual-fragment-$minute"), id("main-0"), timeline, sector, extraClass.id,
                    actualStart.plusSeconds(minute * 60L), actualStart.plusSeconds((minute + 1) * 60L), extraClass.name,
                    extraClass.helpsMeetHoursReference, extraClass.showDedicatedSummary, actualStamp, actualStamp) }
            }
            val expectedActual = step("ACTUAL_AGGREGATE") { ShiftActualAggregate(actualRecord, actualIntervals) }
            val actualId = step("ACTUAL_EXPECTATION_ID") { id("main-0") }
            val actualRead = action("ACTUAL_EXPECTATION_READ") { store.shiftActuals.getExpectation(actualId) }
            val expected = step("ACTUAL_EXPECTATION_NONNULL") { checkNotNull(actualRead) }
            val draftSelection = step("ACTUAL_DRAFT_SELECTION") { ShiftActualClassSelection.Existing(extraClass) }
            val draftFragments = expectedActual.extraIntervals.mapIndexed { index, fragment ->
                step("ACTUAL_FRAGMENT/${index + 1}") { ShiftActualFragmentDraft(fragment.id, fragment.start, fragment.end) }
            }
            val draft = step("ACTUAL_DRAFT") { ShiftActualDraft(actualStart, actualEnd,
                expectedActual.record.differenceReason, expectedActual.record.explanation, ShiftActualDifferenceChoice.EXTRA_CLASS,
                draftSelection, draftFragments) }
            val mutationRead = step("ACTUAL_MUTATION_BUILD") { buildShiftActualSaveMutation(expected, draft, clock, actualStamp) }
            val mutation = step("ACTUAL_MUTATION_NONNULL") { checkNotNull(mutationRead) }
            step("ACTUAL_COMPARE") { check(mutation.replacement == expectedActual) }
            val actualSaved = action("ACTUAL_SAVE") { store.shiftActuals.save(mutation) }
            step("ACTUAL_SAVED") { check(actualSaved is ShiftActualWriteResult.Saved) }
            val evidenceKey = image("cancel", "alien-evidence", "ALIEN")
            val situationStamp = step("SITUATION_STAMP") { stamp.plusSeconds(20) }
            val situation = step("SITUATION_BUILD") { ShiftSituation(alien.shift.id, ShiftStatus.CANCELLED, "Own unrelated cancellation", "Own private unrelated note",
                CancellationActor.ME, situationStamp, CancellationEvidence(evidenceKey, "image/png", bytes.size.toLong(), 1, 1, digest(bytes)), situationStamp, situationStamp) }
            val situationRead = action("SITUATION_READ") { store.shiftSituations.inspect(alien.shift.id) }
            val situationExpectation = step("SITUATION_NONNULL") { checkNotNull(situationRead) }
            val situationMutation = step("SITUATION_MUTATION") { ShiftSituationMutation(situationExpectation, situation, false) }
            action("SITUATION_APPLY") { store.shiftSituations.apply(situationMutation) }
            step("ALIEN_EXPECTATION") { writes[alien.shift.id] = alien.copy(shift = alien.shift.copy(status = situation.status, updatedAt = situationStamp)) }
            val medical = step("MEDICAL_BUILD") { MedicalLeave(id("medical"), date(8), date(8), "Own synthetic protection only", stamp, stamp) }
            action("MEDICAL_CREATE") { store.medicalLeaves.create(medical) }
            fun revisionFor(plan: UUID, from: LocalDate, end: LocalDate) = RecurringPlanRevision(id("revision-$plan"), plan, 1, from,
                RecurringPlanRevisionKind.ACTIVE, end, RecurringPattern.EveryNDays(1), primary.id, place.id, objective.id, type.id,
                objective.fullName, objective.abbreviation, objective.address, type.name, type.behavior, primary.startTime,
                primary.endTime, primary.colorArgb, "Own automatic position", zone, stamp)
            val mainId = step("MAIN_PLAN_ID") { id("main-plan") }
            val mainRevision = step("MAIN_REVISION") { revisionFor(mainId, date(-2), date(12)) }
            val mainPlan = step("MAIN_PLAN") { RecurringPlan(mainId, timeline, sector, stamp) }
            val mainOccurrences = (-2..12).map { offset -> step("MAIN_OCCURRENCE/${offset + 2}") {
                val state = when (offset) { in excluded -> RecurringOccurrenceState.EXCLUDED; in retired -> RecurringOccurrenceState.RETIRED; customized -> RecurringOccurrenceState.CUSTOMIZED; else -> RecurringOccurrenceState.AUTOMATIC }
                RecurringOccurrence(mainId, date(offset), mainRevision.id, if (state in setOf(RecurringOccurrenceState.EXCLUDED, RecurringOccurrenceState.RETIRED)) null else id("main-$offset"), state, stamp, stamp)
            } }
            val main = step("MAIN_AGGREGATE") { RecurringPlanAggregate(mainPlan, listOf(mainRevision), mainOccurrences) }
            fun other(role: String, write: V2ShiftWrite): RecurringPlanAggregate {
                val planId = id("$role-plan"); val r = revisionFor(planId, write.shift.localStartDate, write.shift.localStartDate)
                return RecurringPlanAggregate(RecurringPlan(planId, timeline, sector, stamp), listOf(r),
                    listOf(RecurringOccurrence(planId, write.shift.localStartDate, r.id, write.shift.id, RecurringOccurrenceState.AUTOMATIC, stamp, stamp)))
            }
            val replaceablePlan = second?.let { step("OTHER_PLAN_REPLACEABLE") { other("replaceable", it) } }
            val alienWrite = step("OTHER_ALIEN_LOOKUP") { checkNotNull(writes[alien.shift.id]) }
            val alienPlan = step("OTHER_PLAN_ALIEN") { other("alien", alienWrite) }
            val others = step("OTHER_PLANS_LIST") { listOfNotNull(replaceablePlan, alienPlan) }
            val historyReceiver = step("HISTORY_RECEIVER") { Stage12RecurringHistoryProducer }
            val historicalPlans = step("HISTORY_ARGS") { listOf(main) + others }
            step("HISTORY_INSERT") { historyReceiver.insert(context, binding, historicalPlans) }
            val maps = step("RESULT_MAPS") { Pair(Triple(writes.toMap(), notes.toMap(), notifications.toMap()), originalFiles.toMap()) }
            val result = step("RESULT_BUILD") { Stage12RecurringFixture(run, ownCase, today, zone, stamp, revision.id, objective, place, type, individualType, primary,
                alternative, changeTemplate, main, others, maps.first.first, maps.first.second, maps.first.third, expectedActual,
                extraClass, situation, medical, maps.second) }
            result.validate(store, photoStore, prefix)
            return result
        }
    }
}
