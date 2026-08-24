package com.blackatsystems.miguardia.core.domain.work

import com.blackatsystems.miguardia.core.domain.model.Objective
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class WorkTypeBehavior {
    ACTIVE_WORK,
}

data class WorkPlace(
    val id: UUID,
    val timelineId: UUID,
    val sector: WorkSector,
    val objectiveId: UUID,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(!updatedAt.isBefore(createdAt)) {
            "La actualizacion del lugar no puede ser anterior a su creacion"
        }
    }
}

data class WorkType(
    val id: UUID,
    val timelineId: UUID,
    val sector: WorkSector,
    val name: String,
    val normalizedNameKey: String,
    val behavior: WorkTypeBehavior,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(name == normalizeRequiredWorkText(name, "El nombre del tipo de trabajo")) {
            "El nombre visible del tipo de trabajo debe estar normalizado"
        }
        require(normalizedNameKey == canonicalWorkTypeNameKey(name)) {
            "La clave del tipo de trabajo no coincide con su nombre"
        }
        require(!updatedAt.isBefore(createdAt)) {
            "La actualizacion del tipo de trabajo no puede ser anterior a su creacion"
        }
    }

    companion object {
        fun create(
            id: UUID,
            timelineId: UUID,
            sector: WorkSector,
            rawName: String,
            timestamp: Instant,
            behavior: WorkTypeBehavior = WorkTypeBehavior.ACTIVE_WORK,
        ): WorkType {
            val normalizedName = normalizeRequiredWorkText(rawName, "El nombre del tipo de trabajo")
            return WorkType(
                id = id,
                timelineId = timelineId,
                sector = sector,
                name = normalizedName,
                normalizedNameKey = canonicalWorkTypeNameKey(normalizedName),
                behavior = behavior,
                isActive = true,
                createdAt = timestamp,
                updatedAt = timestamp,
            )
        }
    }
}

data class WorkTemplate(
    val id: UUID,
    val timelineId: UUID,
    val sector: WorkSector,
    val workPlaceId: UUID,
    val objectiveId: UUID,
    val workTypeId: UUID,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val colorArgb: Int,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        requireWholeMinute(startTime, "El inicio de la plantilla")
        requireWholeMinute(endTime, "El final de la plantilla")
        require(!updatedAt.isBefore(createdAt)) {
            "La actualizacion de la plantilla no puede ser anterior a su creacion"
        }
    }

    val plannedDurationMinutes: Int
        get() {
            val startMinutes = startTime.hour * MINUTES_PER_HOUR + startTime.minute
            val endMinutes = endTime.hour * MINUTES_PER_HOUR + endTime.minute
            val difference = (endMinutes - startMinutes + MINUTES_PER_DAY) % MINUTES_PER_DAY
            return if (difference == 0) MINUTES_PER_DAY else difference
        }

    val plannedDuration: Duration
        get() = Duration.ofMinutes(plannedDurationMinutes.toLong())

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    }
}

data class WorkplaceRuleRevision(
    val id: UUID,
    val timelineId: UUID,
    val sector: WorkSector,
    val workPlaceId: UUID,
    val objectiveId: UUID,
    val effectiveFrom: LocalDate,
    val rules: WorkplaceRules,
    val createdAt: Instant,
)

data class WorkPlaceUpdate(
    val previousWorkPlace: WorkPlace,
    val updatedWorkPlace: WorkPlace,
    val previousObjective: Objective,
    val updatedObjective: Objective,
) {
    init {
        require(
            previousWorkPlace.id == updatedWorkPlace.id &&
                previousWorkPlace.timelineId == updatedWorkPlace.timelineId &&
                previousWorkPlace.sector == updatedWorkPlace.sector &&
                previousWorkPlace.objectiveId == updatedWorkPlace.objectiveId &&
                previousWorkPlace.createdAt == updatedWorkPlace.createdAt,
        ) { "Editar un lugar no puede cambiar su identidad" }
        require(previousWorkPlace.isActive == updatedWorkPlace.isActive) {
            "Archivar o reactivar un lugar requiere una accion explicita"
        }
        require(!updatedWorkPlace.updatedAt.isBefore(previousWorkPlace.updatedAt)) {
            "La actualizacion del lugar no puede retroceder en el tiempo"
        }
        require(updatedWorkPlace.objectiveId == updatedObjective.id) {
            "El objetivo actualizado no corresponde al lugar"
        }
        require(
            updatedObjective == updatedObjective.normalizedForV2Update(
                previous = previousObjective,
                timestamp = updatedWorkPlace.updatedAt,
            ),
        ) { "Los datos actualizados del lugar no son validos" }
    }
}

data class WorkTypeUpdate(
    val previous: WorkType,
    val updated: WorkType,
) {
    init {
        require(
            previous.id == updated.id &&
                previous.timelineId == updated.timelineId &&
                previous.sector == updated.sector &&
                previous.behavior == updated.behavior &&
                previous.createdAt == updated.createdAt,
        ) { "Editar un tipo de trabajo no puede cambiar su identidad ni comportamiento" }
        require(previous.isActive == updated.isActive) {
            "Archivar o reactivar un tipo requiere una accion explicita"
        }
        require(!updated.updatedAt.isBefore(previous.updatedAt)) {
            "La actualizacion del tipo de trabajo no puede retroceder en el tiempo"
        }
    }
}

data class WorkTemplateUpdate(
    val previous: WorkTemplate,
    val updated: WorkTemplate,
) {
    init {
        require(
            previous.id == updated.id &&
                previous.timelineId == updated.timelineId &&
                previous.sector == updated.sector &&
                previous.workPlaceId == updated.workPlaceId &&
                previous.objectiveId == updated.objectiveId &&
                previous.workTypeId == updated.workTypeId &&
                previous.createdAt == updated.createdAt,
        ) { "Editar una plantilla no puede cambiar su identidad" }
        require(previous.isActive == updated.isActive) {
            "Archivar o reactivar una plantilla requiere una accion explicita"
        }
        require(!updated.updatedAt.isBefore(previous.updatedAt)) {
            "La actualizacion de la plantilla no puede retroceder en el tiempo"
        }
    }
}

data class RecentWorkTemplate(
    val objective: Objective,
    val workPlace: WorkPlace,
    val workType: WorkType,
    val template: WorkTemplate,
    val lastUsedAt: Instant,
) {
    init {
        require(objective.id == workPlace.objectiveId) {
            "El objetivo reciente no corresponde al lugar"
        }
        require(template.workPlaceId == workPlace.id && template.objectiveId == objective.id) {
            "La plantilla reciente no corresponde al lugar"
        }
        require(template.workTypeId == workType.id) {
            "La plantilla reciente no corresponde al tipo de trabajo"
        }
        requireSameContext(workPlace, workType, template)
    }
}

data class FirstWorkSet(
    val objective: Objective,
    val workPlace: WorkPlace,
    val firstRuleRevision: WorkplaceRuleRevision,
    val configurationContext: ResolvedWorkConfigurationRevision,
    val workType: WorkType,
    val workTemplate: WorkTemplate,
) {
    init {
        require(objective == objective.normalizedForNewV2WorkPlace()) {
            "Los datos del nuevo lugar deben estar normalizados"
        }
        require(workPlace.objectiveId == objective.id) {
            "El lugar no corresponde al objetivo nuevo"
        }
        require(workPlace.isActive && workType.isActive && workTemplate.isActive) {
            "El primer conjunto debe crearse activo"
        }
        requireRuleBelongsToPlace(firstRuleRevision, workPlace)
        requireFirstRuleMatchesConfiguration(firstRuleRevision, workPlace, configurationContext)
        requireTemplateBelongsTo(workTemplate, workPlace, workType)
    }
}

data class NewWorkPlace(
    val objective: Objective,
    val workPlace: WorkPlace,
    val firstRuleRevision: WorkplaceRuleRevision,
    val configurationContext: ResolvedWorkConfigurationRevision,
) {
    init {
        require(objective == objective.normalizedForNewV2WorkPlace()) {
            "Los datos del nuevo lugar deben estar normalizados"
        }
        require(workPlace.objectiveId == objective.id) {
            "El lugar no corresponde al objetivo nuevo"
        }
        require(workPlace.isActive) { "El lugar debe crearse activo" }
        requireRuleBelongsToPlace(firstRuleRevision, workPlace)
        requireFirstRuleMatchesConfiguration(firstRuleRevision, workPlace, configurationContext)
    }
}

data class WorkplaceRuleBackfill(
    val sourceRevision: WorkplaceRuleRevision,
    val earlierRevision: WorkplaceRuleRevision,
) {
    init {
        requireMatchingRuleContext(sourceRevision, earlierRevision)
        require(earlierRevision.effectiveFrom.isBefore(sourceRevision.effectiveFrom)) {
            "La regla extendida debe comenzar antes que su revision de origen"
        }
        require(earlierRevision.rules == sourceRevision.rules) {
            "La retrocarga debe conservar exactamente las reglas ya elegidas"
        }
        require(earlierRevision.id != sourceRevision.id) {
            "La regla extendida necesita un identificador nuevo"
        }
    }
}

data class NewV2Backfill(
    val currentHistory: WorkConfigurationHistory,
    val configurationRevision: EffectiveRevision<WorkConfiguration>,
    val workplaceRuleBackfills: List<WorkplaceRuleBackfill>,
) {
    val timelineId: UUID
        get() = currentHistory.timeline.id

    val sector: WorkSector
        get() = configurationRevision.value.sector

    val workplaceRuleRevisions: List<WorkplaceRuleRevision>
        get() = workplaceRuleBackfills.map(WorkplaceRuleBackfill::earlierRevision)

    init {
        val firstCurrentRevision = currentHistory.timeline.revisions.first()
        require(configurationRevision.effectiveFrom.isBefore(firstCurrentRevision.effectiveFrom)) {
            "La nueva revision debe extender realmente la configuracion hacia atras"
        }
        require(configurationRevision.value == firstCurrentRevision.value) {
            "La retrocarga debe conservar exactamente la primera configuracion elegida"
        }
        require(currentHistory.timeline.revisions.none { it.id == configurationRevision.id }) {
            "La revision anterior necesita un identificador nuevo"
        }
        require(workplaceRuleBackfills.isNotEmpty()) {
            "La retrocarga debe extender las reglas de al menos un lugar"
        }
        workplaceRuleRevisions.forEach { rule ->
            require(rule.timelineId == timelineId && rule.sector == sector) {
                "Las reglas anteriores deben pertenecer al mismo contexto laboral"
            }
            require(rule.effectiveFrom == configurationRevision.effectiveFrom) {
                "La configuracion y las reglas anteriores deben comenzar juntas"
            }
        }
        require(
            workplaceRuleBackfills.all { backfill ->
                !backfill.sourceRevision.effectiveFrom.isBefore(firstCurrentRevision.effectiveFrom)
            },
        ) { "La regla de origen debe pertenecer a la configuracion vigente" }
        require(workplaceRuleRevisions.map { it.id }.distinct().size == workplaceRuleRevisions.size) {
            "Cada regla anterior necesita un identificador unico"
        }
        require(workplaceRuleRevisions.map { it.workPlaceId }.distinct().size == workplaceRuleRevisions.size) {
            "La retrocarga admite una sola regla anterior por lugar"
        }
    }
}

data class WorkCatalog(
    val timelineId: UUID,
    val sector: WorkSector,
    val workPlaces: List<WorkPlace>,
    val workTypes: List<WorkType>,
    val workTemplates: List<WorkTemplate>,
    val workplaceRuleRevisions: List<WorkplaceRuleRevision>,
) {
    init {
        requireUniqueIds(workPlaces.map { it.id }, "lugar")
        requireUniqueIds(workTypes.map { it.id }, "tipo de trabajo")
        requireUniqueIds(workTemplates.map { it.id }, "plantilla")
        requireUniqueIds(workplaceRuleRevisions.map { it.id }, "revision de reglas")

        require(workPlaces.all { it.timelineId == timelineId && it.sector == sector }) {
            "Los lugares no pertenecen al catalogo solicitado"
        }
        require(workTypes.all { it.timelineId == timelineId && it.sector == sector }) {
            "Los tipos no pertenecen al catalogo solicitado"
        }
        require(workTemplates.all { it.timelineId == timelineId && it.sector == sector }) {
            "Las plantillas no pertenecen al catalogo solicitado"
        }
        require(workplaceRuleRevisions.all { it.timelineId == timelineId && it.sector == sector }) {
            "Las reglas no pertenecen al catalogo solicitado"
        }
        require(workPlaces.map { it.objectiveId }.distinct().size == workPlaces.size) {
            "Un objetivo solo puede vincularse una vez dentro del mismo catalogo"
        }
        require(workTypes.map { it.normalizedNameKey }.distinct().size == workTypes.size) {
            "Los nombres de tipos de trabajo deben ser unicos dentro del catalogo"
        }

        val placesById = workPlaces.associateBy { it.id }
        val typesById = workTypes.associateBy { it.id }
        workTemplates.forEach { template ->
            val place = requireNotNull(placesById[template.workPlaceId]) {
                "La plantilla referencia un lugar inexistente"
            }
            val type = requireNotNull(typesById[template.workTypeId]) {
                "La plantilla referencia un tipo inexistente"
            }
            requireTemplateBelongsTo(template, place, type)
        }
        require(
            workTemplates
                .map { listOf(it.workPlaceId, it.workTypeId, it.startTime, it.endTime) }
                .distinct()
                .size == workTemplates.size,
        ) { "No puede haber dos plantillas exactas en el mismo catalogo" }

        workplaceRuleRevisions.forEach { revision ->
            val place = requireNotNull(placesById[revision.workPlaceId]) {
                "La revision de reglas referencia un lugar inexistente"
            }
            requireRuleBelongsToPlace(revision, place)
        }
        require(
            workplaceRuleRevisions
                .map { it.workPlaceId to it.effectiveFrom }
                .distinct()
                .size == workplaceRuleRevisions.size,
        ) { "Un lugar no puede tener dos reglas desde la misma fecha" }
        require(workPlaces.all { place -> workplaceRuleRevisions.any { it.workPlaceId == place.id } }) {
            "Todo lugar debe conservar al menos una revision de reglas"
        }
    }

    fun ruleRevisionAt(workPlaceId: UUID, date: LocalDate): WorkplaceRuleRevision? =
        workplaceRuleRevisions
            .asSequence()
            .filter { it.workPlaceId == workPlaceId && !it.effectiveFrom.isAfter(date) }
            .maxWithOrNull(compareBy<WorkplaceRuleRevision> { it.effectiveFrom }.thenBy { it.id })
}

internal fun requireTemplateBelongsTo(
    template: WorkTemplate,
    workPlace: WorkPlace,
    workType: WorkType,
) {
    requireSameContext(workPlace, workType, template)
    require(template.workPlaceId == workPlace.id && template.objectiveId == workPlace.objectiveId) {
        "La plantilla no pertenece al lugar indicado"
    }
    require(template.workTypeId == workType.id) {
        "La plantilla no pertenece al tipo indicado"
    }
}

internal fun requireRuleBelongsToPlace(
    revision: WorkplaceRuleRevision,
    workPlace: WorkPlace,
) {
    require(
        revision.timelineId == workPlace.timelineId &&
            revision.sector == workPlace.sector &&
            revision.workPlaceId == workPlace.id &&
            revision.objectiveId == workPlace.objectiveId,
    ) { "La revision de reglas no pertenece al lugar indicado" }
}

private fun requireFirstRuleMatchesConfiguration(
    revision: WorkplaceRuleRevision,
    workPlace: WorkPlace,
    configurationContext: ResolvedWorkConfigurationRevision,
) {
    require(
        configurationContext.timelineId == workPlace.timelineId &&
            configurationContext.revision.value.sector == workPlace.sector,
    ) { "La configuracion no pertenece al contexto del lugar" }
    require(
        configurationContext.referenceDate == revision.effectiveFrom &&
            configurationContext.revision.effectiveFrom == revision.effectiveFrom,
    ) { "La primera regla debe comenzar junto con la revision de configuracion" }
}

private fun requireMatchingRuleContext(
    first: WorkplaceRuleRevision,
    second: WorkplaceRuleRevision,
) {
    require(
        first.timelineId == second.timelineId &&
            first.sector == second.sector &&
            first.workPlaceId == second.workPlaceId &&
            first.objectiveId == second.objectiveId,
    ) { "Las reglas extendidas no pertenecen al mismo lugar" }
}

private fun requireSameContext(
    workPlace: WorkPlace,
    workType: WorkType,
    template: WorkTemplate,
) {
    require(
        workPlace.timelineId == workType.timelineId &&
            workPlace.timelineId == template.timelineId &&
            workPlace.sector == workType.sector &&
            workPlace.sector == template.sector,
    ) { "El lugar, el tipo y la plantilla no pertenecen al mismo contexto laboral" }
}

private fun requireUniqueIds(ids: List<UUID>, label: String) {
    require(ids.distinct().size == ids.size) { "Hay mas de un $label con el mismo identificador" }
}

private fun requireWholeMinute(time: LocalTime, label: String) {
    require(time.second == 0 && time.nano == 0) {
        "$label debe expresarse sin segundos ni fracciones"
    }
}
