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
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursEntry
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
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

    override suspend fun createPerPeriodValue(
        timelineId: UUID,
        entry: PerPeriodHoursEntry,
    ) {
        mapConstraint("No se pudo guardar el valor de horas del período.") {
            database.withTransaction {
                val current = requireHistory(timelineId)
                validateHistoryWithValues(
                    current = current,
                    values = current.perPeriodHoursValues.entries + entry,
                )
                dao.insertValue(entry.toEntity())
            }
        }
    }

    override suspend fun updatePerPeriodValue(
        timelineId: UUID,
        entry: PerPeriodHoursEntry,
    ) {
        mapConstraint("No se pudo corregir el valor de horas del período.") {
            database.withTransaction {
                val current = requireHistory(timelineId)
                val existing = current.perPeriodHoursValues.entries
                    .singleOrNull { stored -> stored.id == entry.id }
                    ?: invalid("No existe el valor de horas que se quiere corregir.")
                if (existing.key != entry.key) {
                    invalid("Una corrección no puede cambiar la definición ni la ventana del período.")
                }

                val updatedValues = current.perPeriodHoursValues.entries.map { stored ->
                    if (stored.id == entry.id) entry else stored
                }
                validateHistoryWithValues(current, updatedValues)

                val entity = entry.toEntity()
                val updatedRows = dao.updateValueMinutes(
                    id = entity.id,
                    definitionId = entity.definitionId,
                    windowStartInclusive = entity.windowStartInclusive,
                    windowEndExclusive = entity.windowEndExclusive,
                    requiredMinutes = entity.requiredMinutes,
                )
                if (updatedRows != 1) {
                    invalid("No existe el valor de horas que se quiere corregir.")
                }
            }
        }
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
}
