package com.blackatsystems.miguardia.core.database.validation

import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.mapping.decodeWorkCatalog
import com.blackatsystems.miguardia.core.database.mapping.decodeRecurringPlanAggregate
import com.blackatsystems.miguardia.core.database.mapping.encodeSector
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toDomainOrNull
import com.blackatsystems.miguardia.core.database.mapping.toDomainActualRecord
import com.blackatsystems.miguardia.core.database.mapping.toDomainExtraInterval
import com.blackatsystems.miguardia.core.database.mapping.toDomainExtraWorkClass
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkPlace
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkSnapshot
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkTemplate
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkType
import com.blackatsystems.miguardia.core.database.mapping.toDomainRuleRevision
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevisionKind
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.requireValidStoredShiftActual
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.normalizedForNewV2WorkPlace
import com.blackatsystems.miguardia.core.domain.work.resolveWorkplaceRuleSegments
import java.util.UUID

/**
 * Audits all V2 rows, including data that Room relations could hide after an
 * external restore with foreign keys disabled. Call inside the surrounding
 * repository transaction so validation and writing see the same state.
 */
internal suspend fun MiGuardiaV2Database.requireValidV2LocalData(): WorkConfigurationHistory? = try {
    auditValidV2LocalData()
} catch (error: InvalidLocalDataException) {
    throw error
} catch (error: RuntimeException) {
    throw InvalidLocalDataException("Los datos laborales 2.0 almacenados son inválidos.", error)
}

private suspend fun MiGuardiaV2Database.auditValidV2LocalData(): WorkConfigurationHistory? {
    val catalogDao = workCatalogDao()
    if (catalogDao.getInvalidV2RowCount() != 0) {
        invalidV2Data("La base contiene filas laborales huérfanas o incoherentes.")
    }
    val objectiveDao = objectiveDao()
    if (objectiveDao.getInvalidBooleanCount() != 0) {
        invalidV2Data("La base contiene un objetivo con un estado inválido.")
    }
    objectiveDao.getAll().forEach { row ->
        val objective = row.toDomain()
        if (objective.normalizedForNewV2WorkPlace() != objective) {
            invalidV2Data("El objetivo ${objective.id} contiene datos sin normalizar.")
        }
    }

    val history = workConfigurationDao().getRoots().toDomainOrNull(
        workConfigurationDao().getOrphanRowCount(),
    )
    val places = catalogDao.getAllWorkPlaces().map { it.toDomainWorkPlace() }
    val types = catalogDao.getAllWorkTypes().map { it.toDomainWorkType() }
    val templates = catalogDao.getAllWorkTemplates().map { it.toDomainWorkTemplate() }
    val rules = catalogDao.getAllWorkplaceRuleRevisions().map { it.toDomainRuleRevision() }

    val contexts = buildSet {
        places.forEach { add(it.timelineId to it.sector) }
        types.forEach { add(it.timelineId to it.sector) }
        templates.forEach { add(it.timelineId to it.sector) }
        rules.forEach { add(it.timelineId to it.sector) }
    }
    val catalogs = contexts.associateWith { (timelineId, sector) ->
        WorkCatalog(
            timelineId = timelineId,
            sector = sector,
            workPlaces = places.filter { it.timelineId == timelineId && it.sector == sector },
            workTypes = types.filter { it.timelineId == timelineId && it.sector == sector },
            workTemplates = templates.filter { it.timelineId == timelineId && it.sector == sector },
            workplaceRuleRevisions = rules.filter { it.timelineId == timelineId && it.sector == sector },
        )
    }

    if (contexts.isNotEmpty() && history == null) {
        invalidV2Data("Hay un catálogo laboral sin una configuración 2.0.")
    }
    contexts.forEach { (timelineId, sector) ->
        val storedHistory = requireNotNull(history)
        if (
            storedHistory.timeline.id != timelineId ||
            storedHistory.timeline.revisions.none { revision -> revision.value.sector == sector }
        ) {
            invalidV2Data("El catálogo laboral usa un sector que nunca estuvo configurado.")
        }
    }
    rules.groupBy { rule -> rule.workPlaceId }.values.forEach { placeRules ->
        val rule = requireNotNull(
            placeRules.minWithOrNull(
                compareBy<WorkplaceRuleRevision> { it.effectiveFrom }.thenBy { it.id },
            ),
        )
        val applicable = requireNotNull(history).timeline.revisionAt(rule.effectiveFrom)
        if (
            applicable?.effectiveFrom != rule.effectiveFrom ||
            applicable.value.sector != rule.sector
        ) {
            invalidV2Data("La primera regla de un lugar no coincide con su activación laboral.")
        }
    }

    val writes = v2ShiftDao().getAllShiftsWithSnapshots().map { row ->
        V2ShiftWrite(
            shift = row.shift.toDomain(),
            snapshot = row.snapshot.toDomainWorkSnapshot(),
        )
    }
    if (writes.isNotEmpty() && history == null) {
        invalidV2Data("Hay jornadas 2.0 sin una configuración laboral.")
    }
    writes.forEach { write ->
        validateStoredV2Write(
            write = write,
            history = requireNotNull(history),
            catalogs = catalogs,
        )
    }
    validateRecurringPlans(
        history = history,
        catalogs = catalogs,
        writes = writes,
    )
    validateShiftActuals(history = history, writes = writes)
    return history
}

private suspend fun MiGuardiaV2Database.validateShiftActuals(
    history: WorkConfigurationHistory?,
    writes: List<V2ShiftWrite>,
) {
    val actualDao = shiftActualDao()
    if (actualDao.getInvalidReferenceOrBooleanCount() != 0) {
        invalidV2Data("La base contiene horarios reales o clases extra huérfanos o incoherentes.")
    }
    val classes = actualDao.getAllClasses().map { it.toDomainExtraWorkClass() }
    val storedHistory = history
    if (classes.isNotEmpty() && storedHistory == null) {
        invalidV2Data("Hay clases extra sin una configuración laboral.")
    }
    classes.forEach { extraClass ->
        if (
            storedHistory?.timeline?.id != extraClass.timelineId ||
            storedHistory.timeline.revisions.none { it.value.sector == extraClass.sector }
        ) {
            invalidV2Data("La clase extra ${extraClass.id} pertenece a otra forma de trabajar.")
        }
    }
    val classesById = classes.associateBy { it.id }
    val records = actualDao.getAllRecords().associateBy { it.shiftId }
    val intervals = actualDao.getAllIntervals().groupBy { it.shiftId }
    if (intervals.keys.any { it !in records.keys }) {
        invalidV2Data("Hay fragmentos extra sin su horario real.")
    }
    val writesById = writes.associateBy { it.shift.id.toString() }
    records.forEach { (shiftId, recordEntity) ->
        val planned = writesById[shiftId]
            ?: invalidV2Data("El horario real $shiftId no conserva su jornada planificada.")
        val aggregate = ShiftActualAggregate(
            record = recordEntity.toDomainActualRecord(),
            extraIntervals = intervals[shiftId].orEmpty().map { it.toDomainExtraInterval() },
        )
        val classIds = aggregate.extraIntervals.map { it.extraWorkClassId }.distinct()
        if (classIds.size > 1) invalidV2Data("El horario real $shiftId mezcla clases extra.")
        val selectedClass = classIds.singleOrNull()?.let { classId ->
            classesById[classId] ?: invalidV2Data("El horario real $shiftId referencia una clase inexistente.")
        }
        try {
            requireValidStoredShiftActual(planned, aggregate, selectedClass)
        } catch (error: IllegalArgumentException) {
            throw InvalidLocalDataException("El horario real $shiftId contiene datos inválidos.", error)
        }
    }
}

private suspend fun MiGuardiaV2Database.validateRecurringPlans(
    history: WorkConfigurationHistory?,
    catalogs: Map<Pair<UUID, WorkSector>, WorkCatalog>,
    writes: List<V2ShiftWrite>,
) {
    val planRows = recurringPlanDao().getAllPlans()
    val revisionRows = recurringPlanDao().getAllRevisions()
    val occurrenceRows = recurringPlanDao().getAllOccurrences()
    val planIds = planRows.mapTo(hashSetOf()) { it.id }
    if (
        revisionRows.any { it.planId !in planIds } ||
        occurrenceRows.any { it.planId !in planIds }
    ) {
        invalidV2Data("La base contiene revisiones u ocurrencias sin su plan recurrente.")
    }
    val writesById = writes.associateBy { it.shift.id }
    planRows.forEach { planRow ->
        val aggregate = decodeRecurringPlanAggregate(
            plan = planRow,
            revisions = revisionRows.filter { it.planId == planRow.id },
            occurrences = occurrenceRows.filter { it.planId == planRow.id },
        )
        val storedHistory = history
            ?: invalidV2Data("Hay planes recurrentes sin una configuración laboral.")
        if (
            aggregate.plan.timelineId != storedHistory.timeline.id ||
            storedHistory.timeline.revisions.none { it.value.sector == aggregate.plan.sector }
        ) {
            invalidV2Data("El plan ${aggregate.plan.id} pertenece a otra forma de trabajar.")
        }
        val catalog = catalogs[aggregate.plan.timelineId to aggregate.plan.sector]
            ?: invalidV2Data("El plan ${aggregate.plan.id} no conserva su catálogo laboral.")
        aggregate.revisions.forEach { revision ->
            val place = catalog.workPlaces.singleOrNull { it.id == revision.workPlaceId }
                ?: invalidV2Data("Una revisión recurrente referencia un lugar inexistente.")
            val type = catalog.workTypes.singleOrNull { it.id == revision.workTypeId }
                ?: invalidV2Data("Una revisión recurrente referencia un tipo inexistente.")
            val template = catalog.workTemplates.singleOrNull { it.id == revision.templateId }
                ?: invalidV2Data("Una revisión recurrente referencia una plantilla inexistente.")
            if (
                place.objectiveId != revision.objectiveId ||
                template.workPlaceId != place.id ||
                template.objectiveId != revision.objectiveId ||
                template.workTypeId != type.id
            ) {
                invalidV2Data("Una revisión recurrente mezcla fuentes laborales incompatibles.")
            }
        }
        val finalizedRevisions = aggregate.revisions.filter { revision ->
            revision.kind == RecurringPlanRevisionKind.FINALIZED
        }
        if (
            finalizedRevisions.size > 1 ||
            finalizedRevisions.singleOrNull()?.let { it != aggregate.latestRevision } == true
        ) {
            invalidV2Data("Una finalización recurrente debe ser la última revisión del plan.")
        }
        aggregate.occurrences.forEach { occurrence ->
            occurrence.shiftId?.let { shiftId ->
                val write = writesById[shiftId]
                    ?: invalidV2Data("Una ocurrencia recurrente no conserva su par de jornada.")
                if (
                    write.snapshot.timelineId != aggregate.plan.timelineId ||
                    write.snapshot.sector != aggregate.plan.sector ||
                    write.shift.localStartDate != occurrence.localDate
                ) {
                    invalidV2Data("Una ocurrencia recurrente no coincide con la fecha o forma de trabajar de su jornada.")
                }
            }
        }
    }
}

internal suspend fun MiGuardiaV2Database.readCatalog(
    timelineId: UUID,
    sector: WorkSector,
): WorkCatalog = decodeWorkCatalog(
    timelineId = timelineId,
    sector = sector,
    places = workCatalogDao().getWorkPlaces(timelineId.toString(), sector.encodeSector()),
    types = workCatalogDao().getWorkTypes(timelineId.toString(), sector.encodeSector()),
    templates = workCatalogDao().getWorkTemplates(timelineId.toString(), sector.encodeSector()),
    revisions = workCatalogDao().getWorkplaceRuleRevisions(timelineId.toString(), sector.encodeSector()),
    invalidRowCount = workCatalogDao().getInvalidV2RowCount(),
)

private fun validateStoredV2Write(
    write: V2ShiftWrite,
    history: WorkConfigurationHistory,
    catalogs: Map<Pair<UUID, WorkSector>, WorkCatalog>,
) {
    val shift = write.shift.validated()
    if (shift != write.shift) {
        invalidV2Data("La jornada ${shift.id} contiene fotografías comunes sin normalizar.")
    }
    requireExactShiftSnapshotInstants(shift)
    val snapshot = write.snapshot
    if (history.timeline.id != snapshot.timelineId) {
        invalidV2Data("La jornada ${shift.id} pertenece a otra línea temporal.")
    }
    val preservedRevision = history.timeline.revisions.singleOrNull { revision ->
        revision.id == snapshot.configurationRevisionId
    } ?: invalidV2Data("La jornada ${shift.id} no conserva una revisión de configuración existente.")
    if (
        preservedRevision.effectiveFrom.isAfter(shift.localStartDate) ||
        preservedRevision.value.sector != snapshot.sector
    ) {
        invalidV2Data("La revisión histórica de la jornada no corresponde a su fecha y sector.")
    }
    val catalog = catalogs[snapshot.timelineId to snapshot.sector]
        ?: invalidV2Data("La jornada ${shift.id} no tiene catálogo laboral.")
    val place = catalog.workPlaces.singleOrNull { it.id == snapshot.workPlaceId }
        ?: invalidV2Data("La jornada ${shift.id} referencia un lugar inexistente.")
    val type = catalog.workTypes.singleOrNull { it.id == snapshot.workTypeId }
        ?: invalidV2Data("La jornada ${shift.id} referencia un tipo inexistente.")
    val template = catalog.workTemplates.singleOrNull { it.id == snapshot.templateId }
        ?: invalidV2Data("La jornada ${shift.id} referencia una plantilla inexistente.")
    if (
        place.objectiveId != snapshot.objectiveId ||
        template.workPlaceId != place.id ||
        template.objectiveId != place.objectiveId ||
        template.workTypeId != type.id ||
        shift.sourceObjectiveId != snapshot.objectiveId
    ) {
        invalidV2Data("La jornada ${shift.id} mezcla datos de catálogo incompatibles.")
    }
    resolveWorkplaceRuleSegments(
        shift = shift,
        snapshot = snapshot,
        revisions = catalog.workplaceRuleRevisions.filter { it.workPlaceId == place.id },
    )
}

private fun invalidV2Data(message: String): Nothing = throw InvalidLocalDataException(message)
