package com.blackatsystems.miguardia.stage12.c096

import android.content.Context
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.work.*
import java.time.*
import java.util.UUID

internal class C096Fixture(val variant:C096Variant) {
    private fun id(role:String)=C096Recipe.id(variant,role)
    val timeline=id("timeline");val revisionId=id("revision");val placeId=id("place");val typeId=id("type");val templateId=id("template")
    val objective=Objective(id("objective"),"Stage12-C096","C096","Calle de prueba 96",null,true,C096Recipe.stamp,C096Recipe.stamp)
    val place=WorkPlace(placeId,timeline,WorkSector.NURSING,objective.id,true,C096Recipe.stamp,C096Recipe.stamp)
    val type=WorkType.create(typeId,timeline,WorkSector.NURSING,"Enfermería C096",C096Recipe.stamp)
    val revision=EffectiveRevision(revisionId,LocalDate.of(2026,1,1),WorkConfiguration(WorkSector.NURSING,HoursReference.PendingSetup,AvailabilityLabel.PASSIVE_GUARD))
    val history=WorkConfigurationHistory(EffectiveDateTimeline(timeline,listOf(revision)),PerPeriodHoursValues(emptyList()))
    val template=WorkTemplate(templateId,timeline,WorkSector.NURSING,placeId,objective.id,typeId,LocalTime.of(8,15),LocalTime.of(16,45),0xFF336699.toInt(),true,C096Recipe.stamp,C096Recipe.stamp)
    val rule=WorkplaceRuleRevision(id("rule"),timeline,WorkSector.NURSING,placeId,objective.id,LocalDate.of(2026,1,1),WorkplaceRules(NightHoursRule.Disabled,WeekendRule.None,HolidayRule(false,false)),C096Recipe.stamp)
    val first=FirstWorkSet(objective,place,rule,ResolvedWorkConfigurationRevision.resolve(history,LocalDate.of(2026,1,1)),type,template)
    private fun shift(role:String,day:LocalDate,start:Instant,end:Instant)=V2ShiftWrite(
        Shift(id(role),start,end,C096Recipe.zone,day,objective.fullName,objective.abbreviation,objective.address,template.startTime,template.endTime,template.colorArgb,"C096 vecino",ShiftStatus.PLANNED,objective.id,C096Recipe.stamp,C096Recipe.stamp),
        ShiftWorkSnapshot(id(role),timeline,WorkSector.NURSING,revisionId,placeId,objective.id,templateId,typeId,"Enfermería C096",WorkTypeBehavior.ACTIVE_WORK))
    val detail=shift("detail",C096Recipe.detailDate,Instant.parse("2026-10-23T11:15:00Z"),Instant.parse("2026-10-23T19:45:00Z"))
    val neighbour=shift("neighbour",LocalDate.of(2026,10,21),Instant.parse("2026-10-21T11:15:00Z"),Instant.parse("2026-10-21T19:45:00Z"))
    val note=ShiftNote(id("neighbour-note"),neighbour.shift.id,"C096 nota vecina",C096Recipe.stamp,C096Recipe.stamp)
    val neighbourImage=ShiftNoteAttachment(id("neighbour-attachment"),note.id,"note_"+id("neighbour-image")+".png","image/png",C096Recipe.png.size.toLong(),1,1,0,C096Recipe.stamp,C096Recipe.stamp)
    val notice=ShiftNotificationConfig(neighbour.shift.id,listOf(11L,23L))
    val operation=id("draft-operation")
    val businessTables=setOf("objectives","shifts","shift_work_snapshots","explicit_day_statuses","medical_leaves","holidays","shift_notes","shift_note_attachments","vacations","schedule_photos","shift_notification_configs","shift_notification_reminders","work_configuration_roots","per_period_hours_definitions","work_configuration_revisions","per_period_hours_values","work_places","work_types","work_templates","workplace_rule_revisions","recurring_plans","recurring_plan_revisions","recurring_occurrences","extra_work_classes","shift_actual_records","shift_extra_intervals","independent_extra_work_records","availability_windows","shift_situations")
    private fun rows()=with(C096RecurringReadbackOracle) { pendingConfiguration(timeline,revision)+listOf(objective(this@C096Fixture.objective),place(this@C096Fixture.place),type(this@C096Fixture.type),template(this@C096Fixture.template),disabledWorkplaceRule(rule))+write(detail)+write(neighbour)+listOf(note(this@C096Fixture.note),attachment(neighbourImage),C096RecurringReadbackOracle.Row("explicit_day_statuses",mapOf("localDate" to C096FullStoreReadback.text("2026-10-22"),"type" to C096FullStoreReadback.text("DAY_OFF"))))+notification(notice) }
    suspend fun create(store:LocalDataStore,paths:C096Paths) {
        check(store.workConfiguration.get()==null)
        val empty=C096FullStoreReadback.capture(paths)
        check(businessTables.all { empty.tables.getValue(it).rows.isEmpty() })
        store.workConfiguration.createInitial(timeline,revision)
        store.workCatalog.createFirstWorkSet(first)
        store.v2Shifts.insert(detail);store.v2Shifts.insert(neighbour)
        store.explicitDayStatuses.set(LocalDate.of(2026,10,22),ExplicitDayStatusType.DAY_OFF)
        java.nio.file.Files.write(paths.file("files/schedule_photos/"+neighbourImage.storageKey).toPath(),C096Recipe.png,java.nio.file.StandardOpenOption.CREATE_NEW)
        store.shiftNotes.saveWithAttachments(note,listOf(neighbourImage));store.shiftNotificationConfigs.replace(notice)
        requireDatabase(C096FullStoreReadback.capture(paths))
    }
    fun requireDatabase(actual:C096StoreSnapshot) {
        check(actual.tables.size==32 && actual.tables.keys.containsAll(businessTables)) { "C096_READBACK_PROFILE" }
        val independentEmpty=actual.copy(tables=actual.tables.mapValues { (name,table)->if(name in businessTables)table.copy(rows=emptyList()) else table })
        val expected=C096RecurringReadbackOracle.expected(independentEmpty,inserted=rows())
        check(actual.tables==expected.tables) { "C096_DATABASE_CHANGED_OR_SAVED" }
    }
}
