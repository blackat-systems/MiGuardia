package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.entity.PerPeriodHoursDefinitionEntity
import com.blackatsystems.miguardia.core.database.mapping.newWorkConfigurationRoot
import com.blackatsystems.miguardia.core.database.mapping.toDomainOrNull
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursEntry
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValueMutation
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValueWriteResult
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationAvailabilityMutation
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationAvailabilityWriteResult
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationReferenceMutation
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationReferenceWriteResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class RoomWorkConfigurationRepository(
    private val database: MiGuardiaV2Database,
) : WorkConfigurationRepository {
    private val dao = database.workConfigurationDao()

    override fun observe(): Flow<WorkConfigurationHistory?> = combine(
        dao.observeRoots(),
        dao.observeOrphanRowCount(),
    ) { rows, orphanRowCount ->
        rows.toDomainOrNull(orphanRowCount)
    }

    override suspend fun get(): WorkConfigurationHistory? = database.withTransaction {
        dao.getRoots().toDomainOrNull(dao.getOrphanRowCount())
    }

    override suspend fun createInitial(
        timelineId: UUID,
        firstRevision: EffectiveRevision<WorkConfiguration>,
    ) {
        validateHistory("La configuración laboral inicial no es válida.") {
            WorkConfigurationHistory(
                timeline = EffectiveDateTimeline(timelineId, listOf(firstRevision)),
                perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
            )
        }
        val encodedRevision = firstRevision.toEntity(timelineId)

        mapConstraint("No se pudo crear la configuración laboral inicial.") {
            database.withTransaction {
                database.requireValidV2LocalData()
                if (dao.getOrphanRowCount() != 0) {
                    invalid("La configuración laboral contiene filas sin su registro principal.")
                }
                if (dao.getRoots().isNotEmpty()) {
                    invalid("Ya existe una configuración laboral.")
                }
                dao.insertRoot(newWorkConfigurationRoot(timelineId))
                encodedRevision.perPeriodDefinition?.let { definition ->
                    dao.insertDefinition(definition)
                }
                dao.insertRevision(encodedRevision.revision)
                database.requireValidV2LocalData()
            }
        }
    }

    override suspend fun addRevision(
        timelineId: UUID,
        revision: EffectiveRevision<WorkConfiguration>,
    ) {
        mapConstraint("No se pudo agregar la revisión de configuración laboral.") {
            database.withTransaction {
                database.requireValidV2LocalData()
                val current = requireHistory(timelineId)
                validateHistoryWithRevision(current, revision)

                val encodedRevision = revision.toEntity(timelineId)
                encodedRevision.perPeriodDefinition?.let { expected ->
                    insertDefinitionIfMissing(expected)
                }
                dao.insertRevision(encodedRevision.revision)
                database.requireValidV2LocalData()
            }
        }
    }

    override suspend fun applyReferenceMutation(
        mutation: WorkConfigurationReferenceMutation,
    ): WorkConfigurationReferenceWriteResult = try {
        database.withTransaction {
            database.requireValidV2LocalData()
            val current = requireHistory(mutation.expectedHistory.timeline.id)
            if (!current.structurallyEquals(mutation.expectedHistory)) {
                return@withTransaction WorkConfigurationReferenceWriteResult.Conflict
            }
            val existingSameDate = current.timeline.revisions.singleOrNull {
                it.effectiveFrom == mutation.revision.effectiveFrom
            }
            if (existingSameDate != null && existingSameDate.id != mutation.revision.id) {
                return@withTransaction WorkConfigurationReferenceWriteResult.Conflict
            }
            val previousAtStart = current.timeline.revisionAt(mutation.revision.effectiveFrom)
                ?: invalid("La referencia no puede comenzar antes de la configuración laboral.")
            var propagateReference = true
            val revisedTimeline = current.timeline.revisions.map { revision ->
                when {
                    revision.effectiveFrom == mutation.revision.effectiveFrom -> mutation.revision
                    revision.effectiveFrom.isBefore(mutation.revision.effectiveFrom) -> revision
                    !propagateReference -> revision
                    revision.value.hasSameReferenceIdentity(previousAtStart.value) -> revision.copy(
                        value = revision.value.copy(
                            hoursReference = mutation.revision.value.hoursReference,
                            hoursReferenceStartedOn = mutation.revision.value.hoursReferenceStartedOn,
                        ),
                    )
                    else -> {
                        propagateReference = false
                        revision
                    }
                }
            }.let { revisions ->
                if (existingSameDate == null) revisions + mutation.revision else revisions
            }
            val referencedDefinitions = revisedTimeline.mapNotNull { revision ->
                (revision.value.hoursReference as? HoursReference.PerPeriod)
                    ?.definitionId
            }.toSet()
            val retainedValues = current.perPeriodHoursValues.entries
                .filter { it.key.definitionId in referencedDefinitions }
                .toMutableList()
            val previousInitialValue = mutation.initialPerPeriodValue?.let { replacement ->
                retainedValues.singleOrNull { it.key == replacement.key }
                    ?.also { previous ->
                        if (previous.id != replacement.id) {
                            return@withTransaction WorkConfigurationReferenceWriteResult.Conflict
                        }
                        retainedValues.remove(previous)
                        retainedValues += replacement
                    }
            }
            val updated = validateHistory("La nueva referencia de horas no es válida.") {
                WorkConfigurationHistory(
                    timeline = EffectiveDateTimeline(
                        current.timeline.id,
                        revisedTimeline,
                    ),
                    perPeriodHoursValues = PerPeriodHoursValues(
                        retainedValues + listOfNotNull(
                            mutation.initialPerPeriodValue.takeIf { previousInitialValue == null },
                        ),
                    ),
                )
            }
            val encoded = mutation.revision.toEntity(current.timeline.id)
            encoded.perPeriodDefinition?.let { insertDefinitionIfMissing(it) }
            if (existingSameDate == null) {
                dao.insertRevision(encoded.revision)
            } else if (dao.updateRevision(encoded.revision) != 1) {
                throw ReferenceWriteConflictException()
            }
            revisedTimeline
                .filter { revision ->
                    revision.id != mutation.revision.id &&
                        current.timeline.revisions.single { it.id == revision.id } != revision
                }
                .forEach { revision ->
                    val propagated = revision.toEntity(current.timeline.id)
                    propagated.perPeriodDefinition?.let { insertDefinitionIfMissing(it) }
                    if (dao.updateRevision(propagated.revision) != 1) {
                        throw ReferenceWriteConflictException()
                    }
                }
            mutation.initialPerPeriodValue?.let { value ->
                if (previousInitialValue == null) {
                    dao.insertValue(value.toEntity())
                } else {
                    val entity = value.toEntity()
                    if (dao.updateValueMinutes(
                            id = entity.id,
                            definitionId = entity.definitionId,
                            windowStartInclusive = entity.windowStartInclusive,
                            windowEndExclusive = entity.windowEndExclusive,
                            requiredMinutes = entity.requiredMinutes,
                        ) != 1
                    ) {
                        throw ReferenceWriteConflictException()
                    }
                }
            }
            val previousDefinitions = current.timeline.revisions.mapNotNull { revision ->
                (revision.value.hoursReference as? HoursReference.PerPeriod)?.definitionId
            }.toSet()
            (previousDefinitions - referencedDefinitions).forEach { definitionId ->
                dao.deleteValuesForDefinition(definitionId.toString())
                dao.deleteDefinitionIfUnused(definitionId.toString())
            }
            database.requireValidV2LocalData()
            WorkConfigurationReferenceWriteResult.Saved(updated)
        }
    } catch (_: SQLiteConstraintException) {
        WorkConfigurationReferenceWriteResult.Conflict
    } catch (_: ReferenceWriteConflictException) {
        WorkConfigurationReferenceWriteResult.Conflict
    }

    override suspend fun applyPerPeriodHoursValueMutation(
        mutation: PerPeriodHoursValueMutation,
    ): PerPeriodHoursValueWriteResult = try {
        database.withTransaction {
            database.requireValidV2LocalData()
            val timelineId = mutation.expectedHistory.timeline.id
            val current = requireHistory(timelineId)
            if (!current.structurallyEquals(mutation.expectedHistory)) {
                return@withTransaction PerPeriodHoursValueWriteResult.Conflict
            }
            val replacement = mutation.replacement
            val existing = current.perPeriodHoursValues.entries.singleOrNull { stored ->
                stored.key == replacement.key
            }
            if (existing != null && existing.id != replacement.id) {
                return@withTransaction PerPeriodHoursValueWriteResult.Conflict
            }
            val values = current.perPeriodHoursValues.entries
                .filterNot { stored -> stored.key == replacement.key } + replacement
            val updated = validateHistory("El valor del período no es válido.") {
                WorkConfigurationHistory(
                    timeline = current.timeline,
                    perPeriodHoursValues = PerPeriodHoursValues(values),
                )
            }
            if (existing == null) {
                dao.insertValue(replacement.toEntity())
            } else {
                val entity = replacement.toEntity()
                if (
                    dao.updateValueMinutes(
                        id = entity.id,
                        definitionId = entity.definitionId,
                        windowStartInclusive = entity.windowStartInclusive,
                        windowEndExclusive = entity.windowEndExclusive,
                        requiredMinutes = entity.requiredMinutes,
                    ) != 1
                ) {
                    throw ReferenceWriteConflictException()
                }
            }
            database.requireValidV2LocalData()
            PerPeriodHoursValueWriteResult.Saved(updated)
        }
    } catch (_: SQLiteConstraintException) {
        PerPeriodHoursValueWriteResult.Conflict
    } catch (_: ReferenceWriteConflictException) {
        PerPeriodHoursValueWriteResult.Conflict
    }

    override suspend fun applyAvailabilityMutation(
        mutation: WorkConfigurationAvailabilityMutation,
    ): WorkConfigurationAvailabilityWriteResult = try {
        database.withTransaction {
            database.requireValidV2LocalData()
            val current = requireHistory(mutation.expectedHistory.timeline.id)
            if (!current.structurallyEquals(mutation.expectedHistory)) {
                return@withTransaction WorkConfigurationAvailabilityWriteResult.Conflict
            }
            val existingSameDate = current.timeline.revisions.singleOrNull {
                it.effectiveFrom == mutation.revision.effectiveFrom
            }
            if (existingSameDate != null && existingSameDate.id != mutation.revision.id) {
                return@withTransaction WorkConfigurationAvailabilityWriteResult.Conflict
            }
            val previousAtStart = current.timeline.revisionAt(mutation.revision.effectiveFrom)
                ?: invalid("La disponibilidad no puede comenzar antes de la configuración laboral.")
            var propagateAvailability = true
            val revisedTimeline = current.timeline.revisions.map { revision ->
                when {
                    revision.effectiveFrom == mutation.revision.effectiveFrom -> mutation.revision
                    revision.effectiveFrom.isBefore(mutation.revision.effectiveFrom) -> revision
                    !propagateAvailability -> revision
                    revision.value.availabilityLabel == previousAtStart.value.availabilityLabel -> revision.copy(
                        value = revision.value.copy(
                            availabilityLabel = mutation.revision.value.availabilityLabel,
                        ),
                    )
                    else -> {
                        propagateAvailability = false
                        revision
                    }
                }
            }.let { revisions ->
                if (existingSameDate == null) revisions + mutation.revision else revisions
            }
            val updated = validateHistory("La nueva disponibilidad no es válida.") {
                WorkConfigurationHistory(
                    timeline = EffectiveDateTimeline(current.timeline.id, revisedTimeline),
                    perPeriodHoursValues = current.perPeriodHoursValues,
                )
            }
            val encoded = mutation.revision.toEntity(current.timeline.id)
            if (existingSameDate == null) {
                dao.insertRevision(encoded.revision)
            } else if (dao.updateRevision(encoded.revision) != 1) {
                throw ReferenceWriteConflictException()
            }
            revisedTimeline
                .filter { revision ->
                    revision.id != mutation.revision.id &&
                        current.timeline.revisions.single { it.id == revision.id } != revision
                }
                .forEach { revision ->
                    if (dao.updateRevision(revision.toEntity(current.timeline.id).revision) != 1) {
                        throw ReferenceWriteConflictException()
                    }
                }
            database.requireValidV2LocalData()
            WorkConfigurationAvailabilityWriteResult.Saved(updated)
        }
    } catch (_: SQLiteConstraintException) {
        WorkConfigurationAvailabilityWriteResult.Conflict
    } catch (_: ReferenceWriteConflictException) {
        WorkConfigurationAvailabilityWriteResult.Conflict
    }

    private suspend fun requireHistory(timelineId: UUID): WorkConfigurationHistory {
        val history = dao.getRoots().toDomainOrNull(dao.getOrphanRowCount())
            ?: invalid("Todavía no existe una configuración laboral.")
        if (history.timeline.id != timelineId) {
            invalid("La configuración laboral indicada no coincide con la almacenada.")
        }
        return history
    }

    private suspend fun insertDefinitionIfMissing(expected: PerPeriodHoursDefinitionEntity) {
        val existing = dao.getDefinitionById(expected.id)
        when {
            existing == null -> dao.insertDefinition(expected)
            existing != expected -> invalid(
                "Una definición por período existente no puede cambiar su patrón.",
            )
        }
    }

    private fun validateHistoryWithRevision(
        current: WorkConfigurationHistory,
        revision: EffectiveRevision<WorkConfiguration>,
    ) {
        validateHistory("La revisión de configuración laboral no es válida.") {
            WorkConfigurationHistory(
                timeline = EffectiveDateTimeline(
                    id = current.timeline.id,
                    revisions = current.timeline.revisions + revision,
                ),
                perPeriodHoursValues = current.perPeriodHoursValues,
            )
        }
    }

    private fun validateHistoryWithValues(
        current: WorkConfigurationHistory,
        values: List<PerPeriodHoursEntry>,
    ) {
        validateHistory("El valor de horas del período no es válido.") {
            WorkConfigurationHistory(
                timeline = current.timeline,
                perPeriodHoursValues = PerPeriodHoursValues(values),
            )
        }
    }

    private inline fun validateHistory(
        message: String,
        block: () -> WorkConfigurationHistory,
    ): WorkConfigurationHistory = try {
        block()
    } catch (error: IllegalArgumentException) {
        throw InvalidLocalDataException(message, error)
    }

    private suspend fun <T> mapConstraint(
        message: String,
        block: suspend () -> T,
    ): T = try {
        block()
    } catch (error: SQLiteConstraintException) {
        throw InvalidLocalDataException(message, error)
    }

    private fun invalid(message: String): Nothing = throw InvalidLocalDataException(message)

    private fun WorkConfigurationHistory.structurallyEquals(other: WorkConfigurationHistory): Boolean =
        timeline.id == other.timeline.id &&
            timeline.revisions == other.timeline.revisions &&
            perPeriodHoursValues.entries == other.perPeriodHoursValues.entries

    private fun WorkConfiguration.hasSameReferenceIdentity(other: WorkConfiguration): Boolean =
        hoursReference == other.hoursReference &&
            hoursReferenceStartedOn == other.hoursReferenceStartedOn

    private class ReferenceWriteConflictException : RuntimeException()
}
