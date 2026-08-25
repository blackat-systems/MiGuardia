package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.mapping.encodeSector
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toDomainActualRecord
import com.blackatsystems.miguardia.core.database.mapping.toDomainExtraInterval
import com.blackatsystems.miguardia.core.database.mapping.toDomainExtraWorkClass
import com.blackatsystems.miguardia.core.database.mapping.toDomainOccurrence
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkSnapshot
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.domain.model.ExtraWorkClassWriteResult
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftActualSaveMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftActualWriteResult
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.requireValidShiftActualTransition
import com.blackatsystems.miguardia.core.domain.model.requireValidStoredShiftActual
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomShiftActualRepository(
    private val database: MiGuardiaV2Database,
) : ShiftActualRepository {
    private val dao = database.shiftActualDao()
    private val shiftDao = database.v2ShiftDao()

    override fun observeExpectation(shiftId: UUID): Flow<ShiftActualExpectation?> =
        dao.observeExpectationToken(shiftId.toString()).map {
            database.withTransaction {
                database.requireValidV2LocalData()
                readExpectationInside(shiftId.toString())
            }
        }

    override suspend fun getExpectation(shiftId: UUID): ShiftActualExpectation? =
        database.withTransaction {
            database.requireValidV2LocalData()
            readExpectationInside(shiftId.toString())
        }

    override fun observeExtraWorkClasses(
        timelineId: UUID,
        sector: WorkSector,
    ): Flow<List<ExtraWorkClass>> = dao.observeClasses(
        timelineId.toString(),
        sector.encodeSector(),
    ).map { entities ->
        database.withTransaction {
            database.requireValidV2LocalData()
            entities.map { it.toDomainExtraWorkClass() }
        }
    }

    override suspend fun save(mutation: ShiftActualSaveMutation): ShiftActualWriteResult =
        try {
            database.withTransaction {
                database.requireValidV2LocalData()
                val shiftId = mutation.expectation.planned.shift.id.toString()
                val current = readExpectationInside(shiftId)
                if (current != mutation.expectation) return@withTransaction ShiftActualWriteResult.Conflict
                val replacement = mutation.replacement
                val selectedClass = mutation.selectedClass
                require(mutation.classToCreate == null || mutation.classToCreate == selectedClass) {
                    "La clase creada debe ser la clase elegida por la clasificación"
                }
                if (selectedClass != null && mutation.classToCreate == null) {
                    val currentlyStored = dao.getClass(selectedClass.id.toString())?.toDomainExtraWorkClass()
                    if (currentlyStored != selectedClass) return@withTransaction ShiftActualWriteResult.Conflict
                }
                requireValidShiftActualTransition(current, replacement, selectedClass)
                mutation.classToCreate?.let { newClass ->
                    require(newClass.timelineId == current.planned.snapshot.timelineId)
                    require(newClass.sector == current.planned.snapshot.sector)
                    val duplicate = dao.getClasses(
                        newClass.timelineId.toString(),
                        newClass.sector.encodeSector(),
                    ).any { it.id != newClass.id.toString() && it.normalizedNameKey == newClass.normalizedNameKey }
                    if (duplicate) return@withTransaction ShiftActualWriteResult.DuplicateClassName
                    dao.insertClass(newClass.toEntity())
                }
                dao.deleteIntervals(shiftId)
                if (current.previousActual == null) {
                    dao.insertRecord(replacement.record.toEntity())
                } else if (dao.updateRecord(replacement.record.toEntity()) != 1) {
                    throw ActualWriteConflictException()
                }
                if (replacement.extraIntervals.isNotEmpty()) {
                    dao.insertIntervals(replacement.extraIntervals.map { it.toEntity() })
                }
                val saved = requireNotNull(readExpectationInside(shiftId)?.previousActual)
                ShiftActualWriteResult.Saved(saved)
            }
        } catch (_: ActualWriteConflictException) {
            ShiftActualWriteResult.Conflict
        } catch (_: SQLiteConstraintException) {
            ShiftActualWriteResult.Conflict
        }

    override suspend fun returnToPlanned(
        expectation: ShiftActualExpectation,
    ): ShiftActualWriteResult = try {
        database.withTransaction {
            database.requireValidV2LocalData()
            val shiftId = expectation.planned.shift.id.toString()
            val current = readExpectationInside(shiftId)
            if (current != expectation || current.previousActual == null) {
                return@withTransaction ShiftActualWriteResult.Conflict
            }
            dao.deleteIntervals(shiftId)
            if (dao.deleteRecord(shiftId) != 1) throw ActualWriteConflictException()
            ShiftActualWriteResult.ReturnedToPlanned
        }
    } catch (_: ActualWriteConflictException) {
        ShiftActualWriteResult.Conflict
    }

    override suspend fun saveExtraWorkClass(
        expected: ExtraWorkClass?,
        replacement: ExtraWorkClass,
    ): ExtraWorkClassWriteResult = try {
        database.withTransaction {
            val history = requireNotNull(database.requireValidV2LocalData()) {
                "Una clase extra requiere una configuración laboral"
            }
            val current = dao.getClass(replacement.id.toString())?.toDomainExtraWorkClass()
            if (current != expected) return@withTransaction ExtraWorkClassWriteResult.Conflict
            require(
                history.timeline.id == replacement.timelineId &&
                    history.timeline.revisions.any { it.value.sector == replacement.sector },
            ) { "La clase extra no pertenece a una forma de trabajar configurada" }
            require(expected == null || (
                expected.id == replacement.id &&
                    expected.timelineId == replacement.timelineId &&
                    expected.sector == replacement.sector &&
                    expected.createdAt == replacement.createdAt
                )) { "Editar una clase no puede cambiar su identidad" }
            val duplicate = dao.getClasses(
                replacement.timelineId.toString(),
                replacement.sector.encodeSector(),
            ).any { entity ->
                entity.id != replacement.id.toString() &&
                    entity.normalizedNameKey == replacement.normalizedNameKey
            }
            if (duplicate) return@withTransaction ExtraWorkClassWriteResult.DuplicateName
            if (current == null) {
                dao.insertClass(replacement.toEntity())
            } else if (dao.updateClass(replacement.toEntity()) != 1) {
                return@withTransaction ExtraWorkClassWriteResult.Conflict
            }
            ExtraWorkClassWriteResult.Saved(replacement)
        }
    } catch (_: SQLiteConstraintException) {
        ExtraWorkClassWriteResult.DuplicateName
    }

    internal suspend fun readExpectationInside(shiftId: String): ShiftActualExpectation? {
        val pair = shiftDao.getShiftWithSnapshot(shiftId) ?: return null
        val planned = V2ShiftWrite(
            shift = pair.shift.toDomain(),
            snapshot = pair.snapshot.toDomainWorkSnapshot(),
        )
        val record = dao.getRecord(shiftId)?.toDomainActualRecord()
        val intervals = dao.getIntervals(shiftId).map { it.toDomainExtraInterval() }
        if (record == null && intervals.isNotEmpty()) {
            throw InvalidLocalDataException("La jornada $shiftId contiene fragmentos sin horario real.")
        }
        val aggregate = record?.let { ShiftActualAggregate(it, intervals) }
        val classIds = intervals.map { it.extraWorkClassId }.distinct()
        if (classIds.size > 1) {
            throw InvalidLocalDataException("La jornada $shiftId mezcla clases extra.")
        }
        val selectedClass = classIds.singleOrNull()?.let { classId ->
            dao.getClass(classId.toString())?.toDomainExtraWorkClass()
                ?: throw InvalidLocalDataException("La jornada $shiftId referencia una clase extra inexistente.")
        }
        aggregate?.let { requireValidStoredShiftActual(planned, it, selectedClass) }
        return ShiftActualExpectation(
            planned = planned,
            previousActual = aggregate,
            observedClass = selectedClass,
            recurringOccurrence = dao.getOccurrence(shiftId)?.toDomainOccurrence(),
            protectionFingerprint = protectionFingerprint(shiftId, planned.shift.localStartDate.toString()),
        )
    }

    private suspend fun protectionFingerprint(shiftId: String, localDate: String): String {
        val canonical = buildString {
            dao.getNotes(shiftId).forEach { append("note|").append(it).append('\n') }
            append("config|").append(dao.getNotificationConfig(shiftId)).append('\n')
            dao.getNotificationReminders(shiftId).forEach { append("reminder|").append(it).append('\n') }
            append("day|").append(dao.getExplicitDayStatus(localDate)).append('\n')
            dao.getMedicalLeaves(localDate).forEach { append("medical|").append(it).append('\n') }
            dao.getVacations(localDate).forEach { append("vacation|").append(it).append('\n') }
            dao.getHolidays(localDate).forEach { append("holiday|").append(it).append('\n') }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

private class ActualWriteConflictException : RuntimeException()
