package com.blackatsystems.miguardia

import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.FirstWorkSet
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.NewWorkPlace
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRules
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first

internal object V2AppTestFixture {
    val TIMELINE_ID: UUID = UUID.fromString("f2000000-0000-0000-0000-000000000001")
    val REVISION_ID: UUID = UUID.fromString("f2000000-0000-0000-0000-000000000002")
    val PLACEHOLDER_OBJECTIVE_ID: UUID = UUID.fromString("f2000000-0000-0000-0000-000000000003")
    val TYPE_ID: UUID = UUID.fromString("f2000000-0000-0000-0000-000000000005")

    suspend fun writeFor(
        store: LocalDataStore,
        shift: Shift,
        effectiveFrom: LocalDate,
    ): V2ShiftWrite {
        require(!shift.localStartDate.isBefore(effectiveFrom))
        val objectiveId = stableId(
            "objective|${shift.objectiveNameSnapshot}|${shift.objectiveAbbreviationSnapshot}|" +
                shift.objectiveAddressSnapshot.orEmpty(),
        )
        val placeId = stableId("place|$objectiveId")
        val ruleId = stableId("rule|$placeId")
        val requestedTemplateId = templateId(placeId, shift)
        var history = store.workConfiguration.get()
        if (history == null) {
            val revision = EffectiveRevision(
                id = REVISION_ID,
                effectiveFrom = FIXTURE_EFFECTIVE_FROM,
                value = WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, null),
            )
            store.workConfiguration.createInitial(TIMELINE_ID, revision)
            history = requireNotNull(store.workConfiguration.get())
            val configuration = ResolvedWorkConfigurationRevision.resolve(
                history = history,
                date = FIXTURE_EFFECTIVE_FROM,
            )
            store.workCatalog.createFirstWorkSet(
                FirstWorkSet(
                    objective = Objective(
                        id = objectiveId,
                        fullName = shift.objectiveNameSnapshot,
                        abbreviation = shift.objectiveAbbreviationSnapshot,
                        address = shift.objectiveAddressSnapshot,
                        note = null,
                        isActive = true,
                        createdAt = shift.createdAt,
                        updatedAt = shift.createdAt,
                    ),
                    workPlace = WorkPlace(
                        id = placeId,
                        timelineId = TIMELINE_ID,
                        sector = WorkSector.NURSING,
                        objectiveId = objectiveId,
                        isActive = true,
                        createdAt = shift.createdAt,
                        updatedAt = shift.createdAt,
                    ),
                    firstRuleRevision = WorkplaceRuleRevision(
                        id = ruleId,
                        timelineId = TIMELINE_ID,
                        sector = WorkSector.NURSING,
                        workPlaceId = placeId,
                        objectiveId = objectiveId,
                        effectiveFrom = FIXTURE_EFFECTIVE_FROM,
                        rules = WorkplaceRules(
                            nightHours = NightHoursRule.Disabled,
                            weekend = WeekendRule.None,
                            holiday = HolidayRule(false, false),
                        ),
                        createdAt = shift.createdAt,
                    ),
                    configurationContext = configuration,
                    workType = WorkType.create(
                        id = TYPE_ID,
                        timelineId = TIMELINE_ID,
                        sector = WorkSector.NURSING,
                        rawName = TYPE_NAME,
                        timestamp = shift.createdAt,
                    ),
                    workTemplate = template(requestedTemplateId, placeId, objectiveId, shift),
                ),
            )
        } else {
            require(history.timeline.id == TIMELINE_ID) {
                "La prueba V2 requiere una instalación QA limpia o su propio fixture V2."
            }
            require(history.timeline.revisionAt(shift.localStartDate) != null) {
                "El fixture QA compartido debe comenzar antes que todas sus jornadas."
            }
            val firstConfigurationDate = history.timeline.revisions.first().effectiveFrom
            var catalog = store.workCatalog.observeCatalog(TIMELINE_ID, WorkSector.NURSING).first()
            if (catalog.workPlaces.none { it.id == placeId }) {
                val configuration = ResolvedWorkConfigurationRevision.resolve(
                    history = history,
                    date = firstConfigurationDate,
                )
                store.workCatalog.createWorkPlace(
                    NewWorkPlace(
                        objective = Objective(
                            id = objectiveId,
                            fullName = shift.objectiveNameSnapshot,
                            abbreviation = shift.objectiveAbbreviationSnapshot,
                            address = shift.objectiveAddressSnapshot,
                            note = null,
                            isActive = true,
                            createdAt = shift.createdAt,
                            updatedAt = shift.createdAt,
                        ),
                        workPlace = WorkPlace(
                            id = placeId,
                            timelineId = TIMELINE_ID,
                            sector = WorkSector.NURSING,
                            objectiveId = objectiveId,
                            isActive = true,
                            createdAt = shift.createdAt,
                            updatedAt = shift.createdAt,
                        ),
                        firstRuleRevision = WorkplaceRuleRevision(
                            id = ruleId,
                            timelineId = TIMELINE_ID,
                            sector = WorkSector.NURSING,
                            workPlaceId = placeId,
                            objectiveId = objectiveId,
                            effectiveFrom = firstConfigurationDate,
                            rules = fixtureRules(),
                            createdAt = shift.createdAt,
                        ),
                        configurationContext = configuration,
                    ),
                )
                catalog = store.workCatalog.observeCatalog(TIMELINE_ID, WorkSector.NURSING).first()
            }
            if (catalog.workTypes.none { it.id == TYPE_ID }) {
                store.workCatalog.createWorkType(
                    WorkType.create(
                        TYPE_ID,
                        TIMELINE_ID,
                        WorkSector.NURSING,
                        TYPE_NAME,
                        shift.createdAt,
                    ),
                )
                catalog = store.workCatalog.observeCatalog(TIMELINE_ID, WorkSector.NURSING).first()
            }
            val existing = catalog.workTemplates.firstOrNull {
                it.workPlaceId == placeId &&
                    it.startTime == shift.startTimeSnapshot &&
                    it.endTime == shift.endTimeSnapshot &&
                    it.colorArgb == shift.colorArgbSnapshot
            }
            if (existing == null) {
                store.workCatalog.createWorkTemplate(
                    template(requestedTemplateId, placeId, objectiveId, shift),
                )
            }
        }

        val catalog = store.workCatalog.observeCatalog(TIMELINE_ID, WorkSector.NURSING).first()
        val selectedTemplate = requireNotNull(
            catalog.workTemplates.firstOrNull {
                it.workPlaceId == placeId &&
                    it.startTime == shift.startTimeSnapshot &&
                    it.endTime == shift.endTimeSnapshot &&
                    it.colorArgb == shift.colorArgbSnapshot
            },
        )
        val revision = requireNotNull(
            requireNotNull(store.workConfiguration.get())
                .timeline
                .revisionAt(shift.localStartDate),
        )
        return V2ShiftWrite(
            shift = shift.copy(sourceObjectiveId = objectiveId),
            snapshot = ShiftWorkSnapshot(
                shiftId = shift.id,
                timelineId = TIMELINE_ID,
                sector = WorkSector.NURSING,
                configurationRevisionId = revision.id,
                workPlaceId = placeId,
                objectiveId = objectiveId,
                templateId = selectedTemplate.id,
                workTypeId = TYPE_ID,
                workTypeNameSnapshot = TYPE_NAME,
                workTypeBehaviorSnapshot = WorkType.create(
                    TYPE_ID,
                    TIMELINE_ID,
                    WorkSector.NURSING,
                    TYPE_NAME,
                    shift.createdAt,
                ).behavior,
            ),
        )
    }

    private fun template(
        id: UUID,
        placeId: UUID,
        objectiveId: UUID,
        shift: Shift,
    ) = WorkTemplate(
        id = id,
        timelineId = TIMELINE_ID,
        sector = WorkSector.NURSING,
        workPlaceId = placeId,
        objectiveId = objectiveId,
        workTypeId = TYPE_ID,
        startTime = shift.startTimeSnapshot,
        endTime = shift.endTimeSnapshot,
        colorArgb = shift.colorArgbSnapshot,
        isActive = true,
        createdAt = shift.createdAt,
        updatedAt = shift.createdAt,
    )

    private fun templateId(placeId: UUID, shift: Shift): UUID = stableId(
        "template|$placeId|${shift.startTimeSnapshot}|${shift.endTimeSnapshot}|${shift.colorArgbSnapshot}",
    )

    private fun stableId(value: String): UUID =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))

    private fun fixtureRules() = WorkplaceRules(
        nightHours = NightHoursRule.Disabled,
        weekend = WeekendRule.None,
        holiday = HolidayRule(false, false),
    )

    private const val TYPE_NAME: String = "Trabajo de prueba"
    private val FIXTURE_EFFECTIVE_FROM: LocalDate = LocalDate.of(2000, 1, 1)
}
