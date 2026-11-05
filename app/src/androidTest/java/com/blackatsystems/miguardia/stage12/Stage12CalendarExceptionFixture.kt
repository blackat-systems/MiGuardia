package com.blackatsystems.miguardia.stage12

import android.content.Context
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.first

/** Literal V5 dates remain absolute. The existing recurring fixture supplies rich unrelated data. */
internal data class Stage12CalendarExceptionFixture(
    val base: Stage12RecurringFixture,
    val template: WorkTemplate,
    val writes: List<V2ShiftWrite>,
    val plan: RecurringPlanAggregate,
    val notes: List<ShiftNoteWithAttachments>,
    val actuals: List<ShiftActualAggregate>,
) {
    fun id(role: String): UUID = base.id("calendar-exceptions/$role")
    val target: V2ShiftWrite get() = writes.first()
    val neighbour: V2ShiftWrite get() = writes.last()

    companion object {
        val FIRST: LocalDate = LocalDate.of(2026, 10, 23)
        val DATES = listOf(FIRST, FIRST.plusDays(1), FIRST.plusDays(2), FIRST.plusDays(3),
            LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31),
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2))
        const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJZkAAAAASUVORK5CYII="
        fun profile(): List<Stage12PreparationJournal.Operation> = buildList {
            fun op(s: String) { add(Stage12PreparationJournal.Operation(s)) }
            op("BINDING"); op("RECIPE"); op("TEMPLATE_CREATE")
            repeat(9) { op("SHIFT/$it/BUILD"); op("SHIFT/$it/INSERT") }
            op("PLAN_BUILD"); op("PLAN_INSERT")
            repeat(2) { i ->
                op("ASSOCIATES/$i/BUILD"); op("ASSOCIATES/$i/FILE"); op("ASSOCIATES/$i/NOTE_SAVE")
                op("ASSOCIATES/$i/NOTICE_SAVE"); op("ACTUAL/$i/BUILD"); op("ACTUAL/$i/EXPECTATION")
                op("ACTUAL/$i/MUTATION"); op("ACTUAL/$i/SAVE")
            }
            op("RESULT")
            repeat(9) { op("VALIDATE_SHIFT/$it/READ"); op("VALIDATE_SHIFT/$it/COMPARE") }
            op("VALIDATE_PLAN/READ"); op("VALIDATE_PLAN/COMPARE")
            repeat(2) { i ->
                for (kind in listOf("NOTE", "ACTUAL", "NOTICE", "IMAGE")) {
                    op("VALIDATE_$kind/$i/READ"); op("VALIDATE_$kind/$i/COMPARE")
                }
            }
            op("VALIDATE_ALL/READ"); op("VALIDATE_ALL/COMPARE")
        }

        suspend fun create(context: Context, store: LocalDataStore, base: Stage12RecurringFixture,
            binding: Stage12RecurringPaths, photoStore: SchedulePhotoFileStore, clock: Clock,
            journal: Stage12PreparationJournal,
        ): Stage12CalendarExceptionFixture {
            fun <T> step(name: String, body: () -> T): T = journal.call(Stage12PreparationJournal.Operation(name), body)
            suspend fun <T> call(name: String, body: suspend () -> T): T = journal.callSuspend(Stage12PreparationJournal.Operation(name), body)
            step("BINDING") { binding.requireStored(stage12RecurringPathAccess(context)); check(binding.run == base.run) }
            val template = step("RECIPE") {
                base.primary.copy(id = base.id("calendar-exceptions/literal-template"),
                    startTime = LocalTime.of(8, 15), endTime = LocalTime.of(16, 45))
            }
            call("TEMPLATE_CREATE") { store.workCatalog.createWorkTemplate(template) }
            val writes = mutableListOf<V2ShiftWrite>()
            (DATES + FIRST).forEachIndexed { index, date ->
                val write = step("SHIFT/$index/BUILD") {
                    base.expectedWrite(base.id("calendar-exceptions/shift-$index"), date, template,
                        "Puesto sintético de excepciones", base.stamp)
                }
                call("SHIFT/$index/INSERT") { store.v2Shifts.insert(write) }
                writes += write
            }
            val plan = step("PLAN_BUILD") {
                val id = base.id("calendar-exceptions/plan")
                val revision = base.expectedRevision(base.id("calendar-exceptions/revision"), id, 1,
                    FIRST, FIRST.plusDays(3), 1, template, "Puesto sintético de excepciones", base.stamp)
                RecurringPlanAggregate(RecurringPlan(id, base.place.timelineId, base.place.sector, base.stamp),
                    listOf(revision), writes.take(4).map { write ->
                        RecurringOccurrence(id, write.shift.localStartDate, revision.id, write.shift.id,
                            RecurringOccurrenceState.AUTOMATIC, base.stamp, base.stamp)
                    })
            }
            step("PLAN_INSERT") { Stage12RecurringHistoryProducer.insert(context, binding, listOf(plan)) }
            val notes = mutableListOf<ShiftNoteWithAttachments>()
            val actuals = mutableListOf<ShiftActualAggregate>()
            listOf(writes.first(), writes.last()).forEachIndexed { index, write ->
                val note = step("ASSOCIATES/$index/BUILD") {
                    val n = ShiftNote(base.id("calendar-exceptions/note-$index"), write.shift.id,
                        "Nota privada sintética de excepciones", base.stamp, base.stamp)
                    val bytes = Base64.getDecoder().decode(PNG)
                    ShiftNoteWithAttachments(n, listOf(ShiftNoteAttachment(base.id("calendar-exceptions/attachment-$index"),
                        n.id, "note_${base.id("calendar-exceptions/image-$index")}.png", "image/png", bytes.size.toLong(),
                        1, 1, 0, base.stamp, base.stamp)))
                }
                step("ASSOCIATES/$index/FILE") {
                    val file = photoStore.file(note.attachments.single().storageKey)
                    check(file.parentFile.isDirectory && file.createNewFile())
                    file.writeBytes(Base64.getDecoder().decode(PNG))
                }
                call("ASSOCIATES/$index/NOTE_SAVE") { store.shiftNotes.saveWithAttachments(note.note, note.attachments) }
                call("ASSOCIATES/$index/NOTICE_SAVE") {
                    store.shiftNotificationConfigs.replace(ShiftNotificationConfig(write.shift.id, listOf(17, 29)))
                }
                notes += note
                val actual = step("ACTUAL/$index/BUILD") {
                    val date = write.shift.localStartDate
                    fun at(h: Int, m: Int) = date.atTime(h, m).atZone(base.zone).toInstant()
                    val stamp = base.stamp.plusSeconds(15)
                    ShiftActualAggregate(ShiftActualRecord(write.shift.id, base.place.timelineId, base.place.sector,
                        at(8, 0), at(17, 0), "Diferencia sintética expresa", "Explicación sintética", stamp, stamp),
                        listOf(at(8, 0) to at(8, 15), at(16, 45) to at(17, 0)).mapIndexed { n, range ->
                            ShiftExtraInterval(base.id("calendar-exceptions/extra-$index-$n"), write.shift.id,
                                base.place.timelineId, base.place.sector, base.extraClass.id, range.first, range.second,
                                base.extraClass.name, base.extraClass.helpsMeetHoursReference,
                                base.extraClass.showDedicatedSummary, stamp, stamp)
                        })
                }
                val expected = call("ACTUAL/$index/EXPECTATION") { checkNotNull(store.shiftActuals.getExpectation(write.shift.id)) }
                val mutation = step("ACTUAL/$index/MUTATION") {
                    val draft = ShiftActualDraft(actual.record.actualStart, actual.record.actualEnd,
                        actual.record.differenceReason, actual.record.explanation, ShiftActualDifferenceChoice.EXTRA_CLASS,
                        ShiftActualClassSelection.Existing(base.extraClass), actual.extraIntervals.map {
                            ShiftActualFragmentDraft(it.id, it.start, it.end)
                        })
                    checkNotNull(buildShiftActualSaveMutation(expected, draft, clock, actual.record.createdAt)).also {
                        check(it.replacement == actual)
                    }
                }
                call("ACTUAL/$index/SAVE") { check(store.shiftActuals.save(mutation) is ShiftActualWriteResult.Saved) }
                actuals += actual
            }
            val result = step("RESULT") { Stage12CalendarExceptionFixture(base, template, writes.toList(), plan, notes.toList(), actuals.toList()) }
            writes.forEachIndexed { i, write ->
                val found = call("VALIDATE_SHIFT/$i/READ") { store.v2Shifts.getShift(write.shift.id) }
                step("VALIDATE_SHIFT/$i/COMPARE") { check(found == V2ShiftLookup.V2(write)) }
            }
            val foundPlan = call("VALIDATE_PLAN/READ") { store.recurringPlans.getPlan(plan.plan.id) }
            step("VALIDATE_PLAN/COMPARE") { check(foundPlan == plan) }
            notes.forEachIndexed { i, note ->
                val foundNote = call("VALIDATE_NOTE/$i/READ") { store.shiftNotes.getWithAttachments(note.note.id) }
                step("VALIDATE_NOTE/$i/COMPARE") { check(foundNote == note) }
                val foundActual = call("VALIDATE_ACTUAL/$i/READ") { store.shiftActuals.getExpectation(actuals[i].record.shiftId) }
                step("VALIDATE_ACTUAL/$i/COMPARE") { check(foundActual?.previousActual == actuals[i]) }
                val foundNotice = call("VALIDATE_NOTICE/$i/READ") { store.shiftNotificationConfigs.getForShift(note.note.shiftId) }
                step("VALIDATE_NOTICE/$i/COMPARE") { check(foundNotice == ShiftNotificationConfig(note.note.shiftId, listOf(17, 29))) }
                val foundImage = step("VALIDATE_IMAGE/$i/READ") { photoStore.file(note.attachments.single().storageKey).readBytes() }
                step("VALIDATE_IMAGE/$i/COMPARE") { check(foundImage.contentEquals(Base64.getDecoder().decode(PNG))) }
            }
            val allWrites = call("VALIDATE_ALL/READ") { store.v2Shifts.observeAll(base.place.timelineId, base.place.sector).first() }
            step("VALIDATE_ALL/COMPARE") { check(allWrites.associateBy { it.shift.id } == base.writes + writes.associateBy { it.shift.id }) }
            return result
        }
    }
}
