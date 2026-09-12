package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.dao.WorkCatalogDao
import com.blackatsystems.miguardia.core.database.mapping.encodeSector
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toDomainOrNull
import com.blackatsystems.miguardia.core.database.mapping.toDomainRecentWorkTemplate
import com.blackatsystems.miguardia.core.database.mapping.toDomainRuleRevision
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkPlace
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkTemplate
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkType
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkSnapshot
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.database.validation.readCatalog
import com.blackatsystems.miguardia.core.database.validation.validated
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.repository.DuplicateObjectiveAbbreviationException
import com.blackatsystems.miguardia.core.domain.repository.DuplicateWorkPlaceException
import com.blackatsystems.miguardia.core.domain.repository.DuplicateWorkTemplateException
import com.blackatsystems.miguardia.core.domain.repository.DuplicateWorkTypeNameException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.WorkCatalogRepository
import com.blackatsystems.miguardia.core.domain.work.FirstWorkSet
import com.blackatsystems.miguardia.core.domain.work.NewV2Backfill
import com.blackatsystems.miguardia.core.domain.work.NewWorkPlace
import com.blackatsystems.miguardia.core.domain.work.RecentWorkTemplate
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkPlaceUpdate
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkTemplateUpdate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkTypeUpdate
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.validateWorkplaceRuleInsertion
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class RoomWorkCatalogRepository(
    private val database: MiGuardiaV2Database,
) : WorkCatalogRepository {
    private val dao: WorkCatalogDao = database.workCatalogDao()

    override fun observeCatalog(timelineId: UUID, sector: WorkSector): Flow<WorkCatalog> {
        val rowsChanged = combine(
            dao.observeWorkPlaces(timelineId.toString(), sector.encodeSector()),
            dao.observeWorkTypes(timelineId.toString(), sector.encodeSector()),
            dao.observeWorkTemplates(timelineId.toString(), sector.encodeSector()),
            dao.observeWorkplaceRuleRevisions(timelineId.toString(), sector.encodeSector()),
        ) { _, _, _, _ -> Unit }
        return combine(rowsChanged, dao.observeInvalidV2RowCount()) { _, _ -> Unit }
            .map {
                database.withTransaction {
                    database.requireValidV2LocalData()
                    database.readCatalog(timelineId, sector)
                }
            }
    }

    override fun observeRecentlyUsed(
        timelineId: UUID,
        sector: WorkSector,
        limit: Int,
    ): Flow<List<RecentWorkTemplate>> {
        requireRecentLimit(limit)
        return combine(
            dao.observeRecentlyUsed(timelineId.toString(), sector.encodeSector(), limit),
            dao.observeInvalidV2RowCount(),
            database.v2ShiftDao().observeSnapshotCount(),
        ) { rows, _, _ -> rows }
            .map { rows ->
                database.withTransaction {
                    database.requireValidV2LocalData()
                    rows.map { it.toDomainRecentWorkTemplate() }
                }
            }
    }

    override suspend fun getWorkPlace(id: UUID): WorkPlace? = database.withTransaction {
        database.requireValidV2LocalData()
        dao.getWorkPlaceById(id.toString())?.toDomainWorkPlace()
    }

    override suspend fun getWorkType(id: UUID): WorkType? = database.withTransaction {
        database.requireValidV2LocalData()
        dao.getWorkTypeById(id.toString())?.toDomainWorkType()
    }

    override suspend fun getWorkTemplate(id: UUID): WorkTemplate? = database.withTransaction {
        database.requireValidV2LocalData()
        dao.getWorkTemplateById(id.toString())?.toDomainWorkTemplate()
    }

    override suspend fun getRuleRevisionAt(
        workPlaceId: UUID,
        date: LocalDate,
    ): WorkplaceRuleRevision? = database.withTransaction {
        database.requireValidV2LocalData()
        dao.getRuleRevisionAt(workPlaceId.toString(), date.toString())?.toDomainRuleRevision()
    }

    override suspend fun getRuleRevisions(workPlaceId: UUID): List<WorkplaceRuleRevision> =
        database.withTransaction {
            database.requireValidV2LocalData()
            dao.getRuleRevisionsForWorkPlace(workPlaceId.toString()).map { it.toDomainRuleRevision() }
        }

    override suspend fun createFirstWorkSet(firstWorkSet: FirstWorkSet): Unit = writeCatalog(
        genericMessage = "No se pudo crear el primer lugar laboral.",
        objectiveAbbreviation = firstWorkSet.objective.abbreviation,
        place = firstWorkSet.workPlace,
        type = firstWorkSet.workType,
        template = firstWorkSet.workTemplate,
    ) {
        requireConfigurationContext(firstWorkSet.configurationContext)
        database.objectiveDao().insert(firstWorkSet.objective.validated().toEntity())
        dao.insertWorkPlace(firstWorkSet.workPlace.toEntity())
        dao.insertWorkplaceRuleRevision(firstWorkSet.firstRuleRevision.toEntity())
        dao.insertWorkType(firstWorkSet.workType.toEntity())
        dao.insertWorkTemplate(firstWorkSet.workTemplate.toEntity())
        database.requireValidV2LocalData()
    }

    override suspend fun createWorkPlace(newWorkPlace: NewWorkPlace): Unit = writeCatalog(
        genericMessage = "No se pudo crear el lugar laboral.",
        objectiveAbbreviation = newWorkPlace.objective.abbreviation,
        place = newWorkPlace.workPlace,
    ) {
        requireConfigurationContext(newWorkPlace.configurationContext)
        database.objectiveDao().insert(newWorkPlace.objective.validated().toEntity())
        dao.insertWorkPlace(newWorkPlace.workPlace.toEntity())
        dao.insertWorkplaceRuleRevision(newWorkPlace.firstRuleRevision.toEntity())
        database.requireValidV2LocalData()
    }

    override suspend fun updateWorkPlace(update: WorkPlaceUpdate): Unit = writeCatalog(
        genericMessage = "No se pudo actualizar el lugar laboral.",
        objectiveAbbreviation = update.updatedObjective.abbreviation,
        place = update.updatedWorkPlace,
    ) {
        database.requireValidV2LocalData()
        val storedPlace = dao.getWorkPlaceById(update.previousWorkPlace.id.toString())
            ?.toDomainWorkPlace() ?: missing("lugar", update.previousWorkPlace.id)
        val storedObjective = database.objectiveDao().getById(update.previousObjective.id.toString())
            ?.toDomain() ?: missing("objetivo", update.previousObjective.id)
        if (storedPlace != update.previousWorkPlace || storedObjective != update.previousObjective) {
            invalid("El lugar cambió desde que se abrió la edición. Volvé a intentarlo.")
        }
        if (dao.updateWorkPlace(update.updatedWorkPlace.toEntity()) != 1) {
            missing("lugar", update.updatedWorkPlace.id)
        }
        if (database.objectiveDao().update(update.updatedObjective.validated().toEntity()) != 1) {
            missing("objetivo", update.updatedObjective.id)
        }
        database.requireValidV2LocalData()
    }

    override suspend fun setWorkPlaceActive(id: UUID, isActive: Boolean, updatedAt: Instant): Unit =
        writeCatalog("No se pudo cambiar el estado del lugar.") {
            database.requireValidV2LocalData()
            val stored = dao.getWorkPlaceById(id.toString())?.toDomainWorkPlace()
                ?: missing("lugar", id)
            requireForwardTimestamp(stored.updatedAt, updatedAt)
            if (dao.setWorkPlaceActive(id.toString(), isActive, updatedAt.toEpochMilli()) != 1) {
                missing("lugar", id)
            }
            database.requireValidV2LocalData()
        }

    override suspend fun createWorkType(workType: WorkType): Unit = writeCatalog(
        genericMessage = "No se pudo crear el tipo de trabajo.",
        type = workType,
    ) {
        requireStoredContext(workType.timelineId, workType.sector)
        dao.insertWorkType(workType.toEntity())
        database.requireValidV2LocalData()
    }

    override suspend fun updateWorkType(update: WorkTypeUpdate): Unit = writeCatalog(
        genericMessage = "No se pudo actualizar el tipo de trabajo.",
        type = update.updated,
    ) {
        database.requireValidV2LocalData()
        val stored = dao.getWorkTypeById(update.previous.id.toString())?.toDomainWorkType()
            ?: missing("tipo de trabajo", update.previous.id)
        if (stored != update.previous) invalid("El tipo cambió desde que se abrió la edición.")
        if (dao.updateWorkType(update.updated.toEntity()) != 1) missing("tipo de trabajo", update.updated.id)
        database.requireValidV2LocalData()
    }

    override suspend fun setWorkTypeActive(id: UUID, isActive: Boolean, updatedAt: Instant): Unit =
        writeCatalog("No se pudo cambiar el estado del tipo de trabajo.") {
            database.requireValidV2LocalData()
            val stored = dao.getWorkTypeById(id.toString())?.toDomainWorkType()
                ?: missing("tipo de trabajo", id)
            requireForwardTimestamp(stored.updatedAt, updatedAt)
            if (dao.setWorkTypeActive(id.toString(), isActive, updatedAt.toEpochMilli()) != 1) {
                missing("tipo de trabajo", id)
            }
            database.requireValidV2LocalData()
        }

    override suspend fun createWorkTemplate(workTemplate: WorkTemplate): Unit = writeCatalog(
        genericMessage = "No se pudo crear la plantilla laboral.",
        template = workTemplate,
    ) {
        database.requireValidV2LocalData()
        requireSelectableParents(workTemplate)
        dao.insertWorkTemplate(workTemplate.toEntity())
        database.requireValidV2LocalData()
    }

    override suspend fun updateWorkTemplate(update: WorkTemplateUpdate): Unit = writeCatalog(
        genericMessage = "No se pudo actualizar la plantilla laboral.",
        template = update.updated,
    ) {
        database.requireValidV2LocalData()
        val stored = dao.getWorkTemplateById(update.previous.id.toString())?.toDomainWorkTemplate()
            ?: missing("plantilla", update.previous.id)
        if (stored != update.previous) invalid("La plantilla cambió desde que se abrió la edición.")
        if (dao.updateWorkTemplate(update.updated.toEntity()) != 1) missing("plantilla", update.updated.id)
        database.requireValidV2LocalData()
    }

    override suspend fun setWorkTemplateActive(id: UUID, isActive: Boolean, updatedAt: Instant): Unit =
        writeCatalog("No se pudo cambiar el estado de la plantilla.") {
            database.requireValidV2LocalData()
            val stored = dao.getWorkTemplateById(id.toString())?.toDomainWorkTemplate()
                ?: missing("plantilla", id)
            requireForwardTimestamp(stored.updatedAt, updatedAt)
            if (isActive) requireSelectableParents(stored)
            if (dao.setWorkTemplateActive(id.toString(), isActive, updatedAt.toEpochMilli()) != 1) {
                missing("plantilla", id)
            }
            database.requireValidV2LocalData()
        }

    override suspend fun addWorkplaceRuleRevision(
        revision: WorkplaceRuleRevision,
        confirmationNow: Instant,
    ): Unit = writeCatalog("No se pudo agregar la revisión de reglas del lugar.") {
        database.requireValidV2LocalData()
        val place = dao.getWorkPlaceById(revision.workPlaceId.toString())?.toDomainWorkPlace()
            ?: missing("lugar", revision.workPlaceId)
        if (
            place.timelineId != revision.timelineId ||
            place.sector != revision.sector ||
            place.objectiveId != revision.objectiveId
        ) {
            invalid("La revisión no pertenece al lugar indicado.")
        }
        requireStoredContextAt(revision.timelineId, revision.sector, revision.effectiveFrom)
        val existing = dao.getRuleRevisionsForWorkPlace(revision.workPlaceId.toString())
            .map { it.toDomainRuleRevision() }
        val writes = database.v2ShiftDao()
            .getShiftsWithSnapshotsForWorkPlace(revision.workPlaceId.toString())
            .map { row ->
                V2ShiftWrite(row.shift.toDomain(), row.snapshot.toDomainWorkSnapshot())
            }
        validateWorkplaceRuleInsertion(revision, existing, writes, confirmationNow)
        dao.insertWorkplaceRuleRevision(revision.toEntity())
        database.requireValidV2LocalData()
    }

    override suspend fun extendNewV2Backward(extension: NewV2Backfill): WorkConfigurationHistory =
        writeCatalog("No se pudo extender la configuración laboral hacia atrás.") {
            val stored = database.requireValidV2LocalData()
                ?: invalid("Todavía no existe una configuración laboral.")
            if (!sameHistory(stored, extension.currentHistory)) {
                invalid("La configuración laboral cambió antes de confirmar la retrocarga.")
            }
            val validated = try {
                NewV2Backfill(
                    currentHistory = stored,
                    configurationRevision = extension.configurationRevision,
                    workplaceRuleBackfills = extension.workplaceRuleBackfills,
                )
            } catch (error: IllegalArgumentException) {
                throw InvalidLocalDataException("La retrocarga solicitada no es válida.", error)
            }
            validated.workplaceRuleBackfills.forEach { backfill ->
                val storedRules = dao.getRuleRevisionsForWorkPlace(
                    backfill.sourceRevision.workPlaceId.toString(),
                ).map { it.toDomainRuleRevision() }
                val storedSource = storedRules.singleOrNull { it.id == backfill.sourceRevision.id }
                if (storedSource != backfill.sourceRevision) {
                    invalid("Las reglas del lugar cambiaron antes de confirmar la retrocarga.")
                }
                val earliestStored = storedRules.minWithOrNull(
                    compareBy<WorkplaceRuleRevision> { it.effectiveFrom }.thenBy { it.id },
                )
                if (earliestStored != storedSource) {
                    invalid("La retrocarga debe extender la primera regla conservada del lugar.")
                }
            }
            val encoded = validated.configurationRevision.toEntity(validated.timelineId)
            encoded.perPeriodDefinition?.let { definition ->
                val existing = database.workConfigurationDao().getDefinitionById(definition.id)
                when {
                    existing == null -> database.workConfigurationDao().insertDefinition(definition)
                    existing != definition -> invalid("La definición de horas cambió antes de confirmar.")
                }
            }
            database.workConfigurationDao().insertRevision(encoded.revision)
            validated.workplaceRuleRevisions.forEach { dao.insertWorkplaceRuleRevision(it.toEntity()) }
            val result = database.requireValidV2LocalData()
                ?: invalid("No se pudo recuperar la configuración extendida.")
            result
        }

    private suspend fun requireConfigurationContext(context: ResolvedWorkConfigurationRevision) {
        val history = database.requireValidV2LocalData()
            ?: invalid("Todavía no existe una configuración laboral.")
        if (history.timeline.id != context.timelineId) {
            invalid("La configuración laboral indicada no coincide con la almacenada.")
        }
        val applicable = history.timeline.revisionAt(context.referenceDate)
        if (applicable != context.revision) {
            invalid("La revisión laboral ya no es la vigente para la fecha elegida.")
        }
    }

    private suspend fun requireStoredContext(timelineId: UUID, sector: WorkSector) {
        val history = database.requireValidV2LocalData()
            ?: invalid("Todavía no existe una configuración laboral.")
        if (
            history.timeline.id != timelineId ||
            history.timeline.revisions.none { it.value.sector == sector }
        ) {
            invalid("El elemento no pertenece a una forma de trabajar configurada.")
        }
    }

    private suspend fun requireStoredContextAt(
        timelineId: UUID,
        sector: WorkSector,
        date: LocalDate,
    ) {
        val history = database.requireValidV2LocalData()
            ?: invalid("Todavía no existe una configuración laboral.")
        val revision = history.timeline.revisionAt(date)
        if (history.timeline.id != timelineId || revision?.value?.sector != sector) {
            invalid("Las reglas no pertenecen a la configuración vigente en esa fecha.")
        }
    }

    private suspend fun requireSelectableParents(template: WorkTemplate) {
        val place = dao.getWorkPlaceById(template.workPlaceId.toString())?.toDomainWorkPlace()
            ?: missing("lugar", template.workPlaceId)
        val type = dao.getWorkTypeById(template.workTypeId.toString())?.toDomainWorkType()
            ?: missing("tipo de trabajo", template.workTypeId)
        if (!place.isActive || !type.isActive) {
            invalid("El lugar y el tipo deben estar activos para ofrecer esta plantilla.")
        }
        if (
            place.timelineId != template.timelineId ||
            place.sector != template.sector ||
            place.objectiveId != template.objectiveId ||
            type.timelineId != template.timelineId ||
            type.sector != template.sector
        ) {
            invalid("La plantilla no pertenece al lugar y tipo indicados.")
        }
    }

    private fun sameHistory(first: WorkConfigurationHistory, second: WorkConfigurationHistory): Boolean =
        first.timeline.id == second.timeline.id &&
            first.timeline.revisions == second.timeline.revisions &&
            first.perPeriodHoursValues.entries == second.perPeriodHoursValues.entries

    private fun requireForwardTimestamp(previous: Instant, updated: Instant) {
        if (updated.isBefore(previous)) invalid("La actualización no puede retroceder en el tiempo.")
    }

    private suspend fun <T> writeCatalog(
        genericMessage: String,
        objectiveAbbreviation: String? = null,
        place: WorkPlace? = null,
        type: WorkType? = null,
        template: WorkTemplate? = null,
        block: suspend () -> T,
    ): T = try {
        database.withTransaction { block() }
    } catch (error: SQLiteConstraintException) {
        when {
            objectiveAbbreviation != null &&
                database.objectiveDao().findIdByAbbreviation(objectiveAbbreviation) != null ->
                throw DuplicateObjectiveAbbreviationException(objectiveAbbreviation, error)
            place != null && dao.getWorkPlaceByContext(
                place.timelineId.toString(),
                place.sector.encodeSector(),
                place.objectiveId.toString(),
            ) != null -> throw DuplicateWorkPlaceException(error)
            type != null && dao.findWorkTypeIdByNameKey(
                type.timelineId.toString(),
                type.sector.encodeSector(),
                type.normalizedNameKey,
            ) != null -> throw DuplicateWorkTypeNameException(type.name, error)
            template != null && dao.findExactWorkTemplateId(
                template.workPlaceId.toString(),
                template.workTypeId.toString(),
                template.startTime.toString(),
                template.endTime.toString(),
            ) != null -> throw DuplicateWorkTemplateException(error)
            else -> throw InvalidLocalDataException(genericMessage, error)
        }
    }

    private fun requireRecentLimit(limit: Int) {
        if (limit !in 1..5) invalid("La cantidad de plantillas recientes debe estar entre 1 y 5.")
    }

    private fun missing(kind: String, id: UUID): Nothing = invalid("No existe el $kind $id.")

    private fun invalid(message: String): Nothing = throw InvalidLocalDataException(message)
}
