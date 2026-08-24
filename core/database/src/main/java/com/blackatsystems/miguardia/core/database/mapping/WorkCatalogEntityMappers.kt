package com.blackatsystems.miguardia.core.database.mapping

import com.blackatsystems.miguardia.core.database.dao.RecentWorkTemplateRow
import com.blackatsystems.miguardia.core.database.entity.ShiftWorkSnapshotEntity
import com.blackatsystems.miguardia.core.database.entity.WorkPlaceEntity
import com.blackatsystems.miguardia.core.database.entity.WorkTemplateEntity
import com.blackatsystems.miguardia.core.database.entity.WorkTypeEntity
import com.blackatsystems.miguardia.core.database.entity.WorkplaceRuleRevisionEntity
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.RecentWorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WeekendDays
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRules
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

internal fun WorkPlace.toEntity() = WorkPlaceEntity(
    id = id.toString(),
    timelineId = timelineId.toString(),
    sector = sector.encodeSector(),
    objectiveId = objectiveId.toString(),
    isActive = isActive,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun WorkType.toEntity() = WorkTypeEntity(
    id = id.toString(),
    timelineId = timelineId.toString(),
    sector = sector.encodeSector(),
    name = name,
    normalizedNameKey = normalizedNameKey,
    behavior = behavior.encodeBehavior(),
    isActive = isActive,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun WorkTemplate.toEntity() = WorkTemplateEntity(
    id = id.toString(),
    timelineId = timelineId.toString(),
    sector = sector.encodeSector(),
    workPlaceId = workPlaceId.toString(),
    objectiveId = objectiveId.toString(),
    workTypeId = workTypeId.toString(),
    startTime = startTime.toString(),
    endTime = endTime.toString(),
    colorArgb = colorArgb,
    isActive = isActive,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun WorkplaceRuleRevision.toEntity(): WorkplaceRuleRevisionEntity {
    val night = rules.nightHours
    val weekend = rules.weekend
    return WorkplaceRuleRevisionEntity(
        id = id.toString(),
        timelineId = timelineId.toString(),
        sector = sector.encodeSector(),
        workPlaceId = workPlaceId.toString(),
        objectiveId = objectiveId.toString(),
        effectiveFrom = effectiveFrom.toString(),
        nightRuleCode = when (night) {
            NightHoursRule.Disabled -> NIGHT_DISABLED
            is NightHoursRule.Defined -> NIGHT_DEFINED
        },
        nightStartTime = (night as? NightHoursRule.Defined)?.startInclusive?.toString(),
        nightEndTime = (night as? NightHoursRule.Defined)?.endExclusive?.toString(),
        nightDifferentTreatment = (night as? NightHoursRule.Defined)?.differentTreatment,
        nightShowDedicatedSummary = (night as? NightHoursRule.Defined)?.showDedicatedSummary,
        weekendRuleCode = when (weekend) {
            WeekendRule.None -> WEEKEND_NONE
            is WeekendRule.Defined -> weekend.days.encodeWeekendDays()
        },
        weekendDifferentTreatment = (weekend as? WeekendRule.Defined)?.differentTreatment,
        weekendShowDedicatedSummary = (weekend as? WeekendRule.Defined)?.showDedicatedSummary,
        holidayDifferentTreatment = rules.holiday.differentTreatment,
        holidayShowDedicatedSummary = rules.holiday.showDedicatedSummary,
        createdAtEpochMillis = createdAt.toEpochMilli(),
    )
}

internal fun ShiftWorkSnapshot.toEntity() = ShiftWorkSnapshotEntity(
    shiftId = shiftId.toString(),
    timelineId = timelineId.toString(),
    sector = sector.encodeSector(),
    configurationRevisionId = configurationRevisionId.toString(),
    workPlaceId = workPlaceId.toString(),
    objectiveId = objectiveId.toString(),
    templateId = templateId.toString(),
    workTypeId = workTypeId.toString(),
    workTypeNameSnapshot = workTypeNameSnapshot,
    workTypeBehaviorSnapshot = workTypeBehaviorSnapshot.encodeBehavior(),
)

internal fun WorkPlaceEntity.toDomainWorkPlace(): WorkPlace = decodeCatalogRow("lugar", id) {
    WorkPlace(
        id = UUID.fromString(id),
        timelineId = UUID.fromString(timelineId),
        sector = sector.decodeSector(),
        objectiveId = UUID.fromString(objectiveId),
        isActive = isActive,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun WorkTypeEntity.toDomainWorkType(): WorkType = decodeCatalogRow("tipo de trabajo", id) {
    WorkType(
        id = UUID.fromString(id),
        timelineId = UUID.fromString(timelineId),
        sector = sector.decodeSector(),
        name = name,
        normalizedNameKey = normalizedNameKey,
        behavior = behavior.decodeBehavior(),
        isActive = isActive,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun WorkTemplateEntity.toDomainWorkTemplate(): WorkTemplate = decodeCatalogRow("plantilla", id) {
    WorkTemplate(
        id = UUID.fromString(id),
        timelineId = UUID.fromString(timelineId),
        sector = sector.decodeSector(),
        workPlaceId = UUID.fromString(workPlaceId),
        objectiveId = UUID.fromString(objectiveId),
        workTypeId = UUID.fromString(workTypeId),
        startTime = LocalTime.parse(startTime),
        endTime = LocalTime.parse(endTime),
        colorArgb = colorArgb,
        isActive = isActive,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun WorkplaceRuleRevisionEntity.toDomainRuleRevision(): WorkplaceRuleRevision =
    decodeCatalogRow("revisión de reglas", id) {
        WorkplaceRuleRevision(
            id = UUID.fromString(id),
            timelineId = UUID.fromString(timelineId),
            sector = sector.decodeSector(),
            workPlaceId = UUID.fromString(workPlaceId),
            objectiveId = UUID.fromString(objectiveId),
            effectiveFrom = LocalDate.parse(effectiveFrom),
            rules = WorkplaceRules(
                nightHours = decodeNightRule(),
                weekend = decodeWeekendRule(),
                holiday = HolidayRule(
                    differentTreatment = holidayDifferentTreatment,
                    showDedicatedSummary = holidayShowDedicatedSummary,
                ),
            ),
            createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        )
    }

internal fun ShiftWorkSnapshotEntity.toDomainWorkSnapshot(): ShiftWorkSnapshot =
    decodeCatalogRow("fotografía laboral", shiftId) {
        ShiftWorkSnapshot(
            shiftId = UUID.fromString(shiftId),
            timelineId = UUID.fromString(timelineId),
            sector = sector.decodeSector(),
            configurationRevisionId = UUID.fromString(configurationRevisionId),
            workPlaceId = UUID.fromString(workPlaceId),
            objectiveId = UUID.fromString(objectiveId),
            templateId = UUID.fromString(templateId),
            workTypeId = UUID.fromString(workTypeId),
            workTypeNameSnapshot = workTypeNameSnapshot,
            workTypeBehaviorSnapshot = workTypeBehaviorSnapshot.decodeBehavior(),
        )
    }

internal fun decodeWorkCatalog(
    timelineId: UUID,
    sector: WorkSector,
    places: List<WorkPlaceEntity>,
    types: List<WorkTypeEntity>,
    templates: List<WorkTemplateEntity>,
    revisions: List<WorkplaceRuleRevisionEntity>,
    invalidRowCount: Int,
): WorkCatalog = decodeCatalogRows {
    require(invalidRowCount == 0) { "El catálogo laboral contiene filas huérfanas o incoherentes" }
    WorkCatalog(
        timelineId = timelineId,
        sector = sector,
        workPlaces = places.map(WorkPlaceEntity::toDomainWorkPlace),
        workTypes = types.map(WorkTypeEntity::toDomainWorkType),
        workTemplates = templates.map(WorkTemplateEntity::toDomainWorkTemplate),
        workplaceRuleRevisions = revisions.map(WorkplaceRuleRevisionEntity::toDomainRuleRevision),
    )
}

internal fun RecentWorkTemplateRow.toDomainRecentWorkTemplate(): RecentWorkTemplate =
    decodeCatalogRows {
        RecentWorkTemplate(
            objective = objective.toDomain(),
            workPlace = workPlace.toDomainWorkPlace(),
            workType = workType.toDomainWorkType(),
            template = template.toDomainWorkTemplate(),
            lastUsedAt = Instant.ofEpochMilli(lastUsedAtEpochMillis),
        )
    }

private fun WorkplaceRuleRevisionEntity.decodeNightRule(): NightHoursRule = when (nightRuleCode) {
    NIGHT_DISABLED -> {
        require(
            nightStartTime == null &&
                nightEndTime == null &&
                nightDifferentTreatment == null &&
                nightShowDedicatedSummary == null,
        ) { "Una regla nocturna desactivada no admite detalles" }
        NightHoursRule.Disabled
    }
    NIGHT_DEFINED -> NightHoursRule.Defined(
        startInclusive = LocalTime.parse(requireNotNull(nightStartTime)),
        endExclusive = LocalTime.parse(requireNotNull(nightEndTime)),
        differentTreatment = requireNotNull(nightDifferentTreatment),
        showDedicatedSummary = requireNotNull(nightShowDedicatedSummary),
    )
    else -> error("Código de regla nocturna desconocido: $nightRuleCode")
}

private fun WorkplaceRuleRevisionEntity.decodeWeekendRule(): WeekendRule = when (weekendRuleCode) {
    WEEKEND_NONE -> {
        require(weekendDifferentTreatment == null && weekendShowDedicatedSummary == null) {
            "Una regla de fin de semana desactivada no admite detalles"
        }
        WeekendRule.None
    }
    else -> WeekendRule.Defined(
        days = weekendRuleCode.decodeWeekendDays(),
        differentTreatment = requireNotNull(weekendDifferentTreatment),
        showDedicatedSummary = requireNotNull(weekendShowDedicatedSummary),
    )
}

private fun WeekendDays.encodeWeekendDays(): String = when (this) {
    WeekendDays.SATURDAY -> WEEKEND_SATURDAY
    WeekendDays.SUNDAY -> WEEKEND_SUNDAY
    WeekendDays.SATURDAY_AND_SUNDAY -> WEEKEND_SATURDAY_AND_SUNDAY
}

private fun String.decodeWeekendDays(): WeekendDays = when (this) {
    WEEKEND_SATURDAY -> WeekendDays.SATURDAY
    WEEKEND_SUNDAY -> WeekendDays.SUNDAY
    WEEKEND_SATURDAY_AND_SUNDAY -> WeekendDays.SATURDAY_AND_SUNDAY
    else -> error("Código de fin de semana desconocido: $this")
}

private fun WorkTypeBehavior.encodeBehavior(): String = when (this) {
    WorkTypeBehavior.ACTIVE_WORK -> BEHAVIOR_ACTIVE_WORK
}

private fun String.decodeBehavior(): WorkTypeBehavior = when (this) {
    BEHAVIOR_ACTIVE_WORK -> WorkTypeBehavior.ACTIVE_WORK
    else -> error("Código de comportamiento laboral desconocido: $this")
}

private inline fun <T> decodeCatalogRow(kind: String, id: String, block: () -> T): T = try {
    block()
} catch (error: RuntimeException) {
    throw InvalidLocalDataException("La fila de $kind $id contiene datos inválidos.", error)
}

private inline fun <T> decodeCatalogRows(block: () -> T): T = try {
    block()
} catch (error: InvalidLocalDataException) {
    throw error
} catch (error: RuntimeException) {
    throw InvalidLocalDataException("El catálogo laboral almacenado contiene datos inválidos.", error)
}

private const val BEHAVIOR_ACTIVE_WORK = "ACTIVE_WORK"
private const val NIGHT_DISABLED = "DISABLED"
private const val NIGHT_DEFINED = "DEFINED"
private const val WEEKEND_NONE = "NONE"
private const val WEEKEND_SATURDAY = "SATURDAY"
private const val WEEKEND_SUNDAY = "SUNDAY"
private const val WEEKEND_SATURDAY_AND_SUNDAY = "SATURDAY_AND_SUNDAY"
