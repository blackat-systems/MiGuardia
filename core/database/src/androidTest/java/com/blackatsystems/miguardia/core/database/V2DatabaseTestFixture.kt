package com.blackatsystems.miguardia.core.database

import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.shift.buildV2ShiftWrite
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.FirstWorkSet
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRules
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

internal data class SeededV2Catalog(
    val revision: EffectiveRevision<WorkConfiguration>,
    val objective: Objective,
    val place: WorkPlace,
    val type: WorkType,
    val template: WorkTemplate,
    val rule: WorkplaceRuleRevision,
)

internal fun buildV2CatalogFixture(): SeededV2Catalog {
    val revision = EffectiveRevision(
        id = V2TestIds.CONFIGURATION_REVISION,
        effectiveFrom = V2TestIds.CONFIGURATION_DATE,
        value = WorkConfiguration(
            sector = WorkSector.PRIVATE_SECURITY,
            hoursReference = HoursReference.PendingSetup,
            availabilityLabel = null,
        ),
    )
    val objective = Objective(
        id = V2TestIds.OBJECTIVE,
        fullName = "Hospital de prueba",
        abbreviation = "HPR",
        address = "Dirección ficticia 123",
        note = null,
        isActive = true,
        createdAt = V2TestIds.NOW,
        updatedAt = V2TestIds.NOW,
        weatherLatitude = -31.4201,
        weatherLongitude = -64.1888,
    )
    val place = WorkPlace(
        id = V2TestIds.PLACE,
        timelineId = V2TestIds.TIMELINE,
        sector = WorkSector.PRIVATE_SECURITY,
        objectiveId = objective.id,
        isActive = true,
        createdAt = V2TestIds.NOW,
        updatedAt = V2TestIds.NOW,
    )
    val type = WorkType.create(
        id = V2TestIds.TYPE,
        timelineId = V2TestIds.TIMELINE,
        sector = WorkSector.PRIVATE_SECURITY,
        rawName = "Jornada habitual",
        timestamp = V2TestIds.NOW,
    )
    val template = WorkTemplate(
        id = V2TestIds.TEMPLATE,
        timelineId = V2TestIds.TIMELINE,
        sector = WorkSector.PRIVATE_SECURITY,
        workPlaceId = place.id,
        objectiveId = objective.id,
        workTypeId = type.id,
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(16, 0),
        colorArgb = 0xFF336699.toInt(),
        isActive = true,
        createdAt = V2TestIds.NOW,
        updatedAt = V2TestIds.NOW,
    )
    val rule = WorkplaceRuleRevision(
        id = V2TestIds.RULE,
        timelineId = V2TestIds.TIMELINE,
        sector = WorkSector.PRIVATE_SECURITY,
        workPlaceId = place.id,
        objectiveId = objective.id,
        effectiveFrom = revision.effectiveFrom,
        rules = WorkplaceRules(
            nightHours = NightHoursRule.Disabled,
            weekend = WeekendRule.None,
            holiday = HolidayRule(false, false),
        ),
        createdAt = V2TestIds.NOW,
    )
    return SeededV2Catalog(revision, objective, place, type, template, rule)
}

internal fun SeededV2Catalog.toFirstWorkSet(): FirstWorkSet = FirstWorkSet(
    objective = objective,
    workPlace = place,
    firstRuleRevision = rule,
    configurationContext = ResolvedWorkConfigurationRevision.resolve(
        history = WorkConfigurationHistory(
            timeline = EffectiveDateTimeline(place.timelineId, listOf(revision)),
            perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
        ),
        date = revision.effectiveFrom,
    ),
    workType = type,
    workTemplate = template,
)

internal suspend fun LocalDataStore.seedV2Catalog(): SeededV2Catalog {
    val fixture = buildV2CatalogFixture()
    workConfiguration.createInitial(V2TestIds.TIMELINE, fixture.revision)
    workCatalog.createFirstWorkSet(fixture.toFirstWorkSet())
    return fixture
}

internal suspend fun LocalDataStore.buildTestV2Write(
    fixture: SeededV2Catalog,
    id: UUID,
    date: LocalDate,
    timestamp: Instant = V2TestIds.NOW.plusSeconds(60),
    position: String? = null,
): V2ShiftWrite = buildV2ShiftWrite(
    id = id,
    date = date,
    objective = fixture.objective,
    workPlace = fixture.place,
    workType = fixture.type,
    template = fixture.template,
    configurationContext = ResolvedWorkConfigurationRevision.resolve(
        history = requireNotNull(workConfiguration.get()),
        date = date,
    ),
    position = position,
    timestamp = timestamp,
    zoneId = V2TestIds.ZONE,
)

internal object V2TestIds {
    val CONFIGURATION_DATE: LocalDate = LocalDate.of(2026, 1, 1)
    val SHIFT_DATE: LocalDate = LocalDate.of(2026, 8, 23)
    val NOW: Instant = Instant.parse("2026-01-01T12:00:00Z")
    val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
    val TIMELINE: UUID = uuid(1)
    val CONFIGURATION_REVISION: UUID = uuid(2)
    val OBJECTIVE: UUID = uuid(3)
    val PLACE: UUID = uuid(4)
    val TYPE: UUID = uuid(5)
    val TEMPLATE: UUID = uuid(6)
    val RULE: UUID = uuid(7)

    fun uuid(number: Int): UUID = UUID.fromString(
        "97000000-0000-0000-0000-${number.toString().padStart(12, '0')}",
    )
}
