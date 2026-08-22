package com.blackatsystems.miguardia.core.database.validation

import com.blackatsystems.miguardia.core.database.MiGuardiaDatabase
import com.blackatsystems.miguardia.core.database.mapping.decodeWorkCatalog
import com.blackatsystems.miguardia.core.database.mapping.encodeSector
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toDomainOrNull
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkPlace
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkSnapshot
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkTemplate
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkType
import com.blackatsystems.miguardia.core.database.mapping.toDomainRuleRevision
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.resolveWorkplaceRuleSegments
import java.util.UUID

/**
 * Audits all V2 rows, including data that Room relations could hide after an
 * external restore with foreign keys disabled. Call inside the surrounding
 * repository transaction so validation and writing see the same state.
 */
internal suspend fun MiGuardiaDatabase.requireValidV2LocalData(): WorkConfigurationHistory? = try {
    auditValidV2LocalData()
} catch (error: InvalidLocalDataException) {
    throw error
} catch (error: RuntimeException) {
    throw InvalidLocalDataException("Los datos laborales 2.0 almacenados son inválidos.", error)
}

private suspend fun MiGuardiaDatabase.auditValidV2LocalData(): WorkConfigurationHistory? {
    val catalogDao = workCatalogDao()
    if (catalogDao.getInvalidV2RowCount() != 0) {
        invalidV2Data("La base contiene filas laborales huérfanas o incoherentes.")
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
    return history
}

internal suspend fun MiGuardiaDatabase.readCatalog(
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
