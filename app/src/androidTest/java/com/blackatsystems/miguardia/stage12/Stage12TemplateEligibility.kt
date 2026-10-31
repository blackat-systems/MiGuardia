package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import java.time.LocalDate

/** Reads received catalogue references; never edits them to make a fixture admissible. */
internal suspend fun stage12TemplateEligible(store: LocalDataStore, write: V2ShiftWrite, date: LocalDate): Boolean {
    val snapshot = write.snapshot
    val place = store.workCatalog.getWorkPlace(snapshot.workPlaceId) ?: return false
    val type = store.workCatalog.getWorkType(snapshot.workTypeId) ?: return false
    val template = store.workCatalog.getWorkTemplate(snapshot.templateId) ?: return false
    val objective = store.objectives.getById(snapshot.objectiveId) ?: return false
    return place.isActive && type.isActive && template.isActive &&
        place.timelineId == snapshot.timelineId && place.sector == snapshot.sector &&
        place.objectiveId == snapshot.objectiveId && type.timelineId == snapshot.timelineId &&
        type.sector == snapshot.sector && template.timelineId == snapshot.timelineId &&
        template.sector == snapshot.sector && template.workPlaceId == place.id &&
        template.objectiveId == objective.id && template.workTypeId == type.id &&
        write.shift.objectiveNameSnapshot == objective.fullName &&
        write.shift.objectiveAbbreviationSnapshot == objective.abbreviation &&
        write.shift.objectiveAddressSnapshot == objective.address &&
        write.shift.startTimeSnapshot == template.startTime && write.shift.endTimeSnapshot == template.endTime &&
        write.shift.colorArgbSnapshot == template.colorArgb &&
        snapshot.workTypeNameSnapshot == type.name && snapshot.workTypeBehaviorSnapshot == type.behavior &&
        store.workCatalog.getRuleRevisionAt(place.id, date) != null
}
