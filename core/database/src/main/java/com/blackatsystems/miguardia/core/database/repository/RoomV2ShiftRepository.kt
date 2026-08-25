package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.dao.ShiftWithWorkSnapshotRow
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.decodeRecurringPlanAggregate
import com.blackatsystems.miguardia.core.database.mapping.encodeSector
import com.blackatsystems.miguardia.core.database.mapping.toDomainOccurrence
import com.blackatsystems.miguardia.core.database.mapping.toDomainRuleRevision
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkPlace
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkSnapshot
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkTemplate
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkType
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.database.validation.requireExactShiftSnapshotInstants
import com.blackatsystems.miguardia.core.database.validation.validated
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.RecurringNoteVersion
import com.blackatsystems.miguardia.core.domain.model.RecurringMedicalLeaveVersion
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrence
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanAggregate
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanExpectation
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanMutation
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevisionKind
import com.blackatsystems.miguardia.core.domain.model.RecurringProtectionExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation
import com.blackatsystems.miguardia.core.domain.model.RecurringShiftProtectionVersion
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWriteExpectation
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.RecurringPlanRepository
import com.blackatsystems.miguardia.core.domain.repository.V2RecurringShiftRepository
import com.blackatsystems.miguardia.core.domain.shift.expandRecurringDates
import com.blackatsystems.miguardia.core.domain.shift.isExactV2PositionOnlyEdit
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.resolveWorkplaceRuleSegments
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class RoomV2ShiftRepository(
    private val database: MiGuardiaV2Database,
    private val clock: Clock,
) : V2RecurringShiftRepository, RecurringPlanRepository {
    private val dao = database.v2ShiftDao()
    private val recurringDao = database.recurringPlanDao()
    private val actualReader = RoomShiftActualRepository(database)
    private val actualDao = database.shiftActualDao()

    override fun observePlans(
        timelineId: UUID,
        sector: com.blackatsystems.miguardia.core.domain.work.WorkSector,
    ): Flow<List<RecurringPlanAggregate>> = combine(
        recurringDao.observePlans(timelineId.toString(), sector.encodeSector()),
        recurringDao.observeRevisionCount(),
        recurringDao.observeOccurrenceCount(),
    ) { plans, _, _ -> plans }
        .map { plans ->
            database.withTransaction {
                database.requireValidV2LocalData()
                plans.map { plan -> requireNotNull(readPlan(plan.id)) }
            }
        }

    override suspend fun getPlan(planId: UUID): RecurringPlanAggregate? = database.withTransaction {
        database.requireValidV2LocalData()
        readPlan(planId.toString())
    }

    override suspend fun getOccurrenceForShift(shiftId: UUID): RecurringOccurrence? =
        database.withTransaction {
            database.requireValidV2LocalData()
            recurringDao.getOccurrenceForShift(shiftId.toString())?.toDomainOccurrence()
        }

    override suspend fun captureProtection(
        shiftIds: Set<UUID>,
        startDateInclusive: LocalDate?,
        endDateInclusive: LocalDate?,
    ): RecurringProtectionExpectation = database.withTransaction {
        database.requireValidV2LocalData()
        captureProtectionInside(shiftIds, startDateInclusive, endDateInclusive)
    }

    override fun observeWorkSnapshot(shiftId: UUID): Flow<ShiftWorkSnapshot?> = combine(
        dao.observeSnapshot(shiftId.toString()),
        database.workCatalogDao().observeInvalidV2RowCount(),
        database.workConfigurationDao().observeRoots(),
    ) { snapshot, _, _ -> snapshot }
        .map {
            database.withTransaction {
                database.requireValidV2LocalData()
                dao.getSnapshot(shiftId.toString())?.toDomainWorkSnapshot()
            }
        }

    override suspend fun getWorkSnapshot(shiftId: UUID): ShiftWorkSnapshot? =
        database.withTransaction {
            database.requireValidV2LocalData()
            dao.getSnapshot(shiftId.toString())?.toDomainWorkSnapshot()
        }

    override suspend fun getShift(shiftId: UUID): V2ShiftLookup = database.withTransaction {
        database.requireValidV2LocalData()
        val pair = dao.getShiftWithSnapshot(shiftId.toString())
            ?: return@withTransaction V2ShiftLookup.Missing
        V2ShiftLookup.V2(pair.toDomainWrite())
    }

    override suspend fun insert(write: V2ShiftWrite): Unit = writeShiftData(
        "No se pudo guardar la jornada 2.0.",
    ) {
        val history = database.requireValidV2LocalData()
        if (database.shiftDao().getById(write.shift.id.toString()) != null) {
            invalid("Ya existe una jornada con ese identificador.")
        }
        validateIncomingWrite(write, history, IncomingWriteValidationCache())
        dao.insertPair(write.shift.validated().toEntity(), write.snapshot.toEntity())
        database.requireValidV2LocalData()
    }

    override suspend fun deleteShift(
        expected: V2ShiftWrite,
        expectedActual: com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation?,
    ): Unit = writeShiftData(
        "No se pudo eliminar la jornada 2.0.",
    ) {
        database.requireValidV2LocalData()
        val current = dao.getShiftWithSnapshot(expected.shift.id.toString())?.toDomainWrite()
            ?: throw ConflictingLocalWriteException(
                "La jornada ya no existe o dejó de ser V2. Revisá el día nuevamente.",
            )
        if (current != expected) {
            throw ConflictingLocalWriteException(
                "La jornada cambió mientras confirmabas la eliminación. Revisala nuevamente.",
            )
        }
        val currentActual = actualReader.readExpectationInside(expected.shift.id.toString())
        if (currentActual?.previousActual != null && currentActual != expectedActual) {
            throw ConflictingLocalWriteException(
                "La jornada tiene horario real y extras. Revisá la eliminación y confirmá que también se quitarán.",
            )
        }
        if (expectedActual != null && currentActual != expectedActual) {
            throw ConflictingLocalWriteException(
                "El horario real cambió mientras confirmabas la eliminación. Revisalo nuevamente.",
            )
        }
        recurringDao.getOccurrenceForShift(expected.shift.id.toString())?.let { entity ->
            val occurrence = entity.toDomainOccurrence()
            val excluded = occurrence.copy(
                shiftId = null,
                state = RecurringOccurrenceState.EXCLUDED,
                updatedAt = nextOccurrenceUpdate(occurrence.updatedAt, expected.shift.updatedAt),
            )
            if (recurringDao.updateOccurrence(excluded.toEntity()) != 1) {
                invalid("La ocurrencia cambió mientras se eliminaba la jornada.")
            }
        }
        if (currentActual?.previousActual != null) {
            actualDao.deleteIntervals(expected.shift.id.toString())
            if (actualDao.deleteRecord(expected.shift.id.toString()) != 1) {
                invalid("El horario real cambió mientras se eliminaba la jornada.")
            }
        }
        if (dao.deleteShiftAndOwnedSnapshot(expected.shift.id.toString()) != 1) {
            missingShift(expected.shift.id)
        }
        database.requireValidV2LocalData()
    }

    override suspend fun applyV2Batch(
        mutation: V2ShiftBatchMutation,
        expectedOccupancy: ShiftOccupancyExpectation,
        expectedUpdates: V2ShiftWriteExpectation,
    ): Unit = writeShiftData(
        "No se pudo guardar el lote de jornadas 2.0.",
    ) {
        val history = database.requireValidV2LocalData()
        val validationCache = IncomingWriteValidationCache()

        val writeDates = (mutation.shiftsToInsert + mutation.shiftsToUpdate)
            .mapTo(hashSetOf()) { write -> write.shift.localStartDate }
        val expectedIds = expectedOccupancy.observedShifts
            .mapTo(hashSetOf()) { version -> version.shiftId }
        val updatedIds = mutation.shiftsToUpdate.mapTo(linkedSetOf()) { it.shift.id }
        if (
            writeDates.any { it !in expectedOccupancy.startDateInclusive..expectedOccupancy.endDateInclusive } ||
            !expectedIds.containsAll(mutation.shiftIdsToDelete) ||
            !expectedIds.containsAll(updatedIds) ||
            expectedUpdates.writesById.keys != updatedIds
        ) {
            invalid("La ocupacion revisada no cubre todas las jornadas del lote.")
        }
        val currentOccupancy = ShiftOccupancyExpectation.capture(
            startDateInclusive = expectedOccupancy.startDateInclusive,
            endDateInclusive = expectedOccupancy.endDateInclusive,
            shifts = database.shiftDao().getStartingBetween(
                expectedOccupancy.startDateInclusive.toString(),
                expectedOccupancy.endDateInclusive.toString(),
            ).map { entity -> entity.toDomain() },
        )
        if (currentOccupancy != expectedOccupancy) {
            throw ConflictingLocalWriteException(
                "Las jornadas cambiaron mientras revisabas la carga. Revisalas nuevamente antes de guardar.",
            )
        }

        if (!writeDates.containsAll(mutation.explicitDayStatusDatesToClear)) {
            invalid("Una carga V2 sólo puede limpiar estados de las fechas que guarda.")
        }
        mutation.shiftIdsToDelete.forEach { id ->
            val stored = database.shiftDao().getById(id.toString())?.toDomain()
                ?: missingShift(id)
            if (stored.localStartDate !in writeDates) {
                invalid("Una carga V2 sólo puede reemplazar jornadas de las fechas que guarda.")
            }
        }

        mutation.shiftsToInsert.forEach { write ->
            if (database.shiftDao().getById(write.shift.id.toString()) != null) {
                invalid("Ya existe la jornada ${write.shift.id}.")
            }
            validateIncomingWrite(write, history, validationCache)
        }
        mutation.shiftsToUpdate.forEach { write ->
            val existing = requireExistingV2Write(write.shift.id)
            if (existing != expectedUpdates.writesById[write.shift.id]) {
                throw ConflictingLocalWriteException(
                    "La jornada cambió mientras revisabas la edición. Revisala nuevamente antes de guardar.",
                )
            }
            validateUpdateIdentity(existing, write)
            val currentActual = actualReader.readExpectationInside(write.shift.id.toString())
            val expectedActual = mutation.actualExpectations[write.shift.id]
            if (currentActual?.previousActual != null) {
                if (expectedActual == null || currentActual != expectedActual) {
                    throw ConflictingLocalWriteException(
                        "La jornada tiene horario real. Revisalo antes de modificar su planificación.",
                    )
                }
                if (
                    existing.shift.startAt != write.shift.startAt ||
                    existing.shift.endAt != write.shift.endAt
                ) {
                    invalid(
                        "Volvé al horario planificado antes de cambiar el inicio o final de esta jornada.",
                    )
                }
            } else if (expectedActual != null && currentActual != expectedActual) {
                throw ConflictingLocalWriteException(
                    "El horario real cambió mientras revisabas la edición.",
                )
            }
            if (isExactV2PositionOnlyEdit(existing, write)) {
                write.shift.validated()
                requireExactShiftSnapshotInstants(write.shift)
            } else {
                validateIncomingWrite(write, history, validationCache)
            }
        }

        val writeTimestampsByDate = (mutation.shiftsToInsert + mutation.shiftsToUpdate)
            .groupBy { it.shift.localStartDate }
            .mapValues { (_, writes) -> writes.maxOf { it.shift.updatedAt } }
        mutation.shiftIdsToDelete.forEach { shiftId ->
            val currentActual = actualReader.readExpectationInside(shiftId.toString())
            val expectedActual = mutation.actualExpectations[shiftId]
            if (currentActual?.previousActual != null && currentActual != expectedActual) {
                throw ConflictingLocalWriteException(
                    "La jornada tiene horario real y extras. Confirmá específicamente su reemplazo.",
                )
            }
            if (expectedActual != null && currentActual != expectedActual) {
                throw ConflictingLocalWriteException(
                    "El horario real cambió mientras confirmabas el reemplazo.",
                )
            }
            recurringDao.getOccurrenceForShift(shiftId.toString())?.let { entity ->
                val occurrence = entity.toDomainOccurrence()
                val replacementTimestamp = writeTimestampsByDate[occurrence.localDate]
                    ?: invalid("Una jornada recurrente sólo puede reemplazarse por otra de la misma fecha.")
                val excluded = occurrence.copy(
                    shiftId = null,
                    state = RecurringOccurrenceState.EXCLUDED,
                    updatedAt = nextOccurrenceUpdate(occurrence.updatedAt, replacementTimestamp),
                )
                if (recurringDao.updateOccurrence(excluded.toEntity()) != 1) {
                    invalid("La ocurrencia cambió mientras se reemplazaba la jornada.")
                }
            }
        }
        mutation.shiftsToUpdate.forEach { write ->
            recurringDao.getOccurrenceForShift(write.shift.id.toString())?.let { entity ->
                val occurrence = entity.toDomainOccurrence()
                val customized = occurrence.copy(
                    state = RecurringOccurrenceState.CUSTOMIZED,
                    updatedAt = nextOccurrenceUpdate(occurrence.updatedAt, write.shift.updatedAt),
                )
                if (recurringDao.updateOccurrence(customized.toEntity()) != 1) {
                    invalid("La ocurrencia cambió mientras se personalizaba la jornada.")
                }
            }
        }

        if (mutation.shiftIdsToDelete.isNotEmpty()) {
            mutation.shiftIdsToDelete.forEach { shiftId ->
                val currentActual = actualReader.readExpectationInside(shiftId.toString())
                if (currentActual?.previousActual != null) {
                    actualDao.deleteIntervals(shiftId.toString())
                    if (actualDao.deleteRecord(shiftId.toString()) != 1) {
                        invalid("El horario real cambió mientras se reemplazaba la jornada.")
                    }
                }
            }
            val deletedRows = deleteShiftsInSafeBatches(mutation.shiftIdsToDelete)
            if (deletedRows != mutation.shiftIdsToDelete.size) {
                invalid("Una jornada cambió mientras se confirmaba el reemplazo.")
            }
        }
        mutation.shiftsToInsert.forEach { write ->
            dao.insertPair(write.shift.validated().toEntity(), write.snapshot.toEntity())
        }
        mutation.shiftsToUpdate.forEach { write ->
            val (shiftRows, snapshotRows) = dao.updatePair(
                write.shift.validated().toEntity(),
                write.snapshot.toEntity(),
            )
            if (shiftRows != 1 || snapshotRows != 1) {
                invalid("No existe la jornada ${write.shift.id} que se quiere actualizar.")
            }
        }
        mutation.explicitDayStatusDatesToClear.forEach { date ->
            database.explicitDayStatusDao().clear(date.toString())
        }
        database.requireValidV2LocalData()
    }

    override suspend fun applyRecurringPlanMutation(
        mutation: RecurringPlanMutation,
        expectedPlan: RecurringPlanExpectation,
        expectedOccupancy: ShiftOccupancyExpectation,
        expectedPairs: V2ShiftWriteExpectation,
        expectedProtection: RecurringProtectionExpectation,
    ): Unit = writeShiftData(
        "No se pudo guardar el plan recurrente.",
    ) {
        val history = database.requireValidV2LocalData()
        val validationCache = IncomingWriteValidationCache()
        val targetPlanId = mutation.revisionToInsert.planId
        val touchedPlanIds = buildSet {
            add(targetPlanId)
            mutation.occurrencesToInsert.forEach { add(it.planId) }
            mutation.occurrencesToUpdate.forEach { add(it.planId) }
        }
        if (!expectedPlan.aggregatesById.keys.containsAll(touchedPlanIds)) {
            invalid("La revisión no conserva todos los planes recurrentes alcanzados.")
        }
        val currentPlans = expectedPlan.aggregatesById.keys.associateWith { planId ->
            readPlan(planId.toString())
        }
        if (currentPlans != expectedPlan.aggregatesById) {
            throw ConflictingLocalWriteException(
                "El plan cambió mientras confirmabas. Revisá nuevamente antes de guardar.",
            )
        }
        val expectedTarget = expectedPlan.aggregatesById[targetPlanId]
        if (
            (mutation.planToInsert == null && expectedTarget == null) ||
            (mutation.planToInsert != null && expectedTarget != null) ||
            mutation.planToInsert?.id?.let { it != targetPlanId } == true
        ) {
            invalid("La mutación no coincide con la existencia revisada del plan.")
        }

        val shiftMutation = mutation.shiftMutation
        val writeDates = (shiftMutation.shiftsToInsert + shiftMutation.shiftsToUpdate)
            .mapTo(hashSetOf()) { it.shift.localStartDate }
        val expectedIds = expectedOccupancy.observedShifts.mapTo(hashSetOf()) { it.shiftId }
        val updatedIds = shiftMutation.shiftsToUpdate.mapTo(linkedSetOf()) { it.shift.id }
        val comparedPairIds = shiftMutation.shiftIdsToDelete + updatedIds
        if (
            writeDates.any { it !in expectedOccupancy.startDateInclusive..expectedOccupancy.endDateInclusive } ||
            !expectedIds.containsAll(shiftMutation.shiftIdsToDelete) ||
            !expectedIds.containsAll(updatedIds) ||
            expectedPairs.writesById.keys != comparedPairIds ||
            !writeDates.containsAll(shiftMutation.explicitDayStatusDatesToClear)
        ) {
            invalid("La revisión no cubre toda la ocupación y los pares alcanzados.")
        }
        val currentOccupancy = ShiftOccupancyExpectation.capture(
            startDateInclusive = expectedOccupancy.startDateInclusive,
            endDateInclusive = expectedOccupancy.endDateInclusive,
            shifts = database.shiftDao().getStartingBetween(
                expectedOccupancy.startDateInclusive.toString(),
                expectedOccupancy.endDateInclusive.toString(),
            ).map { it.toDomain() },
        )
        if (currentOccupancy != expectedOccupancy) {
            throw ConflictingLocalWriteException(
                "Las jornadas cambiaron mientras confirmabas el plan. Revisá nuevamente.",
            )
        }
        comparedPairIds.forEach { shiftId ->
            if (requireExistingV2Write(shiftId) != expectedPairs.writesById[shiftId]) {
                throw ConflictingLocalWriteException(
                    "Una jornada cambió mientras confirmabas el plan. Revisá nuevamente.",
                )
            }
            if (actualReader.readExpectationInside(shiftId.toString())?.previousActual != null) {
                invalid(
                    "Una revisión recurrente no puede retirar ni reemplazar una jornada con horario real.",
                )
            }
        }

        val protectionIdsRequired = expectedPlan.aggregatesById.values
            .filterNotNull()
            .flatMap(RecurringPlanAggregate::occurrences)
            .filter { occurrence ->
                occurrence.shiftId != null &&
                    occurrence.localDate in expectedOccupancy.startDateInclusive..expectedOccupancy.endDateInclusive
            }
            .mapNotNullTo(linkedSetOf()) { it.shiftId }
        if (!expectedProtection.versionsByShiftId.keys.containsAll(protectionIdsRequired)) {
            invalid("La revisión no conserva todas las señales de protección consultadas.")
        }
        if (
            expectedProtection.startDateInclusive != expectedOccupancy.startDateInclusive ||
            expectedProtection.endDateInclusive != expectedOccupancy.endDateInclusive
        ) {
            invalid("La revisión no conserva el rango de situaciones aplicables consultado.")
        }
        val currentProtection = captureProtectionInside(
            expectedProtection.versionsByShiftId.keys,
            expectedProtection.startDateInclusive,
            expectedProtection.endDateInclusive,
            requireEveryShift = false,
        )
        if (currentProtection != expectedProtection) {
            throw ConflictingLocalWriteException(
                "Las notas, avisos o estados cambiaron. Revisá el plan nuevamente.",
            )
        }

        validateRecurringRevision(mutation, expectedTarget)
        validateRecurringOccurrenceChanges(mutation, expectedPlan, expectedProtection)
        validateRecurringPatternCoverage(mutation, expectedTarget, expectedProtection)
        validateRecurringPairLinks(mutation, expectedPlan)
        validateRecurringWritesMatchRevision(mutation, expectedTarget)

        shiftMutation.shiftsToInsert.forEach { write ->
            if (database.shiftDao().getById(write.shift.id.toString()) != null) {
                invalid("Ya existe la jornada ${write.shift.id}.")
            }
            validateIncomingWrite(write, history, validationCache)
        }
        shiftMutation.shiftsToUpdate.forEach { write ->
            val existing = expectedPairs.writesById.getValue(write.shift.id)
            validateUpdateIdentity(existing, write)
            validateIncomingWrite(write, history, validationCache)
        }

        mutation.planToInsert?.let { recurringDao.insertPlan(it.toEntity()) }
        recurringDao.insertRevision(mutation.revisionToInsert.toEntity())

        val clearingUpdates = mutation.occurrencesToUpdate.filter { it.shiftId == null }
        val linkingUpdates = mutation.occurrencesToUpdate.filter { it.shiftId != null }
        clearingUpdates.forEach { occurrence ->
            if (recurringDao.updateOccurrence(occurrence.toEntity()) != 1) {
                invalid("Una ocurrencia cambió antes de retirar su jornada.")
            }
        }
        if (shiftMutation.shiftIdsToDelete.isNotEmpty()) {
            val deletedRows = deleteShiftsInSafeBatches(shiftMutation.shiftIdsToDelete)
            if (deletedRows != shiftMutation.shiftIdsToDelete.size) {
                invalid("Una jornada cambió mientras se aplicaba el plan.")
            }
        }
        shiftMutation.shiftsToInsert.forEach { write ->
            dao.insertPair(write.shift.validated().toEntity(), write.snapshot.toEntity())
        }
        shiftMutation.shiftsToUpdate.forEach { write ->
            val (shiftRows, snapshotRows) = dao.updatePair(
                write.shift.validated().toEntity(),
                write.snapshot.toEntity(),
            )
            if (shiftRows != 1 || snapshotRows != 1) {
                invalid("No existe la jornada ${write.shift.id} que se quiere versionar.")
            }
        }
        linkingUpdates.forEach { occurrence ->
            if (recurringDao.updateOccurrence(occurrence.toEntity()) != 1) {
                invalid("Una ocurrencia cambió antes de vincular su jornada.")
            }
        }
        if (mutation.occurrencesToInsert.isNotEmpty()) {
            recurringDao.insertOccurrences(mutation.occurrencesToInsert.map { it.toEntity() })
        }
        shiftMutation.explicitDayStatusDatesToClear.forEach { date ->
            database.explicitDayStatusDao().clear(date.toString())
        }
        database.requireValidV2LocalData()
    }

    private suspend fun readPlan(planId: String): RecurringPlanAggregate? {
        val plan = recurringDao.getPlan(planId) ?: return null
        return decodeRecurringPlanAggregate(
            plan = plan,
            revisions = recurringDao.getRevisions(planId),
            occurrences = recurringDao.getOccurrences(planId),
        )
    }

    private suspend fun captureProtectionInside(
        shiftIds: Set<UUID>,
        startDateInclusive: LocalDate? = null,
        endDateInclusive: LocalDate? = null,
        requireEveryShift: Boolean = true,
    ): RecurringProtectionExpectation {
        if ((startDateInclusive == null) != (endDateInclusive == null)) {
            invalid("La expectativa de situaciones debe indicar el rango completo.")
        }
        val encodedIds = shiftIds.map(UUID::toString)
        val shifts = queryShiftIdBatches(encodedIds, recurringDao::getShifts).map { it.toDomain() }
        if (requireEveryShift && shifts.mapTo(hashSetOf()) { it.id } != shiftIds) {
            throw ConflictingLocalWriteException(
                "Una jornada cambió antes de revisar sus protecciones. Revisá nuevamente.",
            )
        }
        val notesByShift = queryShiftIdBatches(encodedIds, recurringDao::getNotes)
            .groupBy { it.shiftId }
        val configIds = queryShiftIdBatches(encodedIds, recurringDao::getNotificationConfigs)
            .mapTo(hashSetOf()) { it.shiftId }
        val remindersByShift = queryShiftIdBatches(encodedIds, recurringDao::getNotificationReminders)
            .groupBy { it.shiftId }
        val actualRecordIds = queryShiftIdBatches(encodedIds, database.shiftActualDao()::getRecords)
            .mapTo(hashSetOf()) { it.shiftId }
        val actualFingerprintsByShift = actualRecordIds.associateWith { shiftId ->
            actualFingerprint(
                requireNotNull(actualReader.readExpectationInside(shiftId)) {
                    "El horario real protegido dejó de conservar su jornada."
                },
            )
        }
        val medicalLeaves = if (startDateInclusive == null) {
            emptyList()
        } else {
            database.medicalLeaveDao().getIntersecting(
                startDateInclusive.toString(),
                requireNotNull(endDateInclusive).toString(),
            ).map { leave ->
                RecurringMedicalLeaveVersion(
                    id = UUID.fromString(leave.id),
                    startDate = LocalDate.parse(leave.startDate),
                    endDateInclusive = LocalDate.parse(leave.endDateInclusive),
                    updatedAt = Instant.ofEpochMilli(leave.updatedAtEpochMillis),
                )
            }
        }
        return RecurringProtectionExpectation.capture(
            shifts.map { shift ->
                val encodedShiftId = shift.id.toString()
                RecurringShiftProtectionVersion(
                    shiftId = shift.id,
                    status = shift.status,
                    notes = notesByShift[encodedShiftId].orEmpty().mapTo(linkedSetOf()) { note ->
                        RecurringNoteVersion(
                            id = UUID.fromString(note.id),
                            updatedAt = Instant.ofEpochMilli(note.updatedAtEpochMillis),
                        )
                    },
                    hasNotificationConfig = encodedShiftId in configIds,
                    notificationLeadMinutes = remindersByShift[encodedShiftId]
                        .orEmpty()
                        .map { it.leadMinutes },
                    actualFingerprint = actualFingerprintsByShift[encodedShiftId],
                )
            },
            startDateInclusive = startDateInclusive,
            endDateInclusive = endDateInclusive,
            medicalLeaves = medicalLeaves,
        )
    }

    private fun actualFingerprint(expectation: ShiftActualExpectation): String {
        val canonical = buildString {
            append("aggregate|").append(requireNotNull(expectation.previousActual)).append('\n')
            append("class|").append(expectation.observedClass).append('\n')
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private suspend fun validateRecurringRevision(
        mutation: RecurringPlanMutation,
        current: RecurringPlanAggregate?,
    ) {
        val revision = mutation.revisionToInsert
        val plan = mutation.planToInsert ?: requireNotNull(current).plan
        val today = LocalDate.now(clock.withZone(revision.zoneId))
        if (
            plan.id != revision.planId ||
            revision.createdAt.isBefore(plan.createdAt) ||
            revision.effectiveFrom.isBefore(today)
        ) {
            invalid("La revisión no pertenece a la raíz recurrente confirmada o intenta cambiar el pasado.")
        }
        if (current == null) {
            if (
                mutation.planToInsert == null ||
                revision.revisionNumber != 1 ||
                revision.kind != RecurringPlanRevisionKind.ACTIVE
            ) {
                invalid("Un plan nuevo debe comenzar con una revisión activa número uno.")
            }
        } else {
            if (current.latestRevision.kind == RecurringPlanRevisionKind.FINALIZED) {
                invalid("Un plan finalizado no admite revisiones posteriores.")
            }
            if (revision.revisionNumber != current.latestRevision.revisionNumber + 1) {
                invalid("El número de revisión recurrente no avanza exactamente uno.")
            }
        }

        if (revision.kind == RecurringPlanRevisionKind.FINALIZED) {
            val latest = current?.latestRevision
                ?: invalid("No se puede finalizar un plan que todavía no existe.")
            val expectedEnd = maxOf(latest.endDateInclusive, revision.effectiveFrom)
            if (
                revision.pattern != latest.pattern ||
                revision.endDateInclusive != expectedEnd ||
                revision.templateId != latest.templateId ||
                revision.workPlaceId != latest.workPlaceId ||
                revision.objectiveId != latest.objectiveId ||
                revision.workTypeId != latest.workTypeId ||
                revision.objectiveNameSnapshot != latest.objectiveNameSnapshot ||
                revision.objectiveAbbreviationSnapshot != latest.objectiveAbbreviationSnapshot ||
                revision.objectiveAddressSnapshot != latest.objectiveAddressSnapshot ||
                revision.workTypeNameSnapshot != latest.workTypeNameSnapshot ||
                revision.workTypeBehaviorSnapshot != latest.workTypeBehaviorSnapshot ||
                revision.startTimeSnapshot != latest.startTimeSnapshot ||
                revision.endTimeSnapshot != latest.endTimeSnapshot ||
                revision.colorArgbSnapshot != latest.colorArgbSnapshot ||
                revision.positionSnapshot != latest.positionSnapshot ||
                revision.zoneId != latest.zoneId
            ) {
                invalid("La finalización debe copiar exactamente la última definición del plan.")
            }
            return
        }

        val place = database.workCatalogDao().getWorkPlaceById(revision.workPlaceId.toString())
            ?.toDomainWorkPlace() ?: invalid("No existe el lugar elegido para el plan.")
        val type = database.workCatalogDao().getWorkTypeById(revision.workTypeId.toString())
            ?.toDomainWorkType() ?: invalid("No existe el tipo elegido para el plan.")
        val template = database.workCatalogDao().getWorkTemplateById(revision.templateId.toString())
            ?.toDomainWorkTemplate() ?: invalid("No existe la plantilla elegida para el plan.")
        val objective = database.objectiveDao().getById(revision.objectiveId.toString())
            ?.toDomain() ?: invalid("No existe el lugar físico elegido para el plan.")
        if (!place.isActive || !type.isActive || !template.isActive) {
            invalid("El lugar, el tipo y la plantilla deben seguir activos para versionar el plan.")
        }
        if (
            plan.timelineId != place.timelineId ||
            plan.sector != place.sector ||
            place.timelineId != type.timelineId ||
            place.sector != type.sector ||
            template.timelineId != place.timelineId ||
            template.sector != place.sector ||
            template.workPlaceId != place.id ||
            template.objectiveId != objective.id ||
            template.workTypeId != type.id ||
            place.objectiveId != objective.id ||
            revision.objectiveNameSnapshot != objective.fullName ||
            revision.objectiveAbbreviationSnapshot != objective.abbreviation ||
            revision.objectiveAddressSnapshot != objective.address ||
            revision.workTypeNameSnapshot != type.name ||
            revision.workTypeBehaviorSnapshot != type.behavior ||
            revision.startTimeSnapshot != template.startTime ||
            revision.endTimeSnapshot != template.endTime ||
            revision.colorArgbSnapshot != template.colorArgb
        ) {
            invalid("La fotografía del plan no coincide con sus fuentes laborales activas.")
        }
    }

    private suspend fun validateRecurringOccurrenceChanges(
        mutation: RecurringPlanMutation,
        expectedPlans: RecurringPlanExpectation,
        protection: RecurringProtectionExpectation,
    ) {
        val targetPlanId = mutation.revisionToInsert.planId
        mutation.occurrencesToInsert.forEach { occurrence ->
            if (
                occurrence.planId != targetPlanId ||
                occurrence.revisionId != mutation.revisionToInsert.id ||
                occurrence.localDate.isBefore(mutation.revisionToInsert.effectiveFrom) ||
                occurrence.createdAt != mutation.revisionToInsert.createdAt ||
                occurrence.updatedAt != mutation.revisionToInsert.createdAt
            ) {
                invalid("Una ocurrencia nueva no pertenece a la revisión confirmada.")
            }
        }
        mutation.occurrencesToUpdate.forEach { updated ->
            val current = expectedPlans.aggregatesById[updated.planId]
                ?.occurrences
                ?.singleOrNull { it.localDate == updated.localDate }
                ?: invalid("La ocurrencia que se quiere cambiar no fue revisada.")
            if (
                current.planId != updated.planId ||
                current.localDate != updated.localDate ||
                current.createdAt != updated.createdAt ||
                !updated.updatedAt.isAfter(current.updatedAt) ||
                updated.updatedAt != mutation.revisionToInsert.createdAt
            ) {
                invalid("Cambiar una ocurrencia debe conservar su identidad y avanzar su versión.")
            }
            if (updated.localDate.isBefore(mutation.revisionToInsert.effectiveFrom)) {
                invalid("Una revisión futura no puede modificar una ocurrencia anterior al corte.")
            }
            if (
                current.state == RecurringOccurrenceState.CUSTOMIZED ||
                current.state == RecurringOccurrenceState.EXCLUDED
            ) {
                invalid("Una revisión futura no puede absorber una excepción manual.")
            }
            if (current.state == RecurringOccurrenceState.AUTOMATIC) {
                val shiftId = requireNotNull(current.shiftId)
                val version = protection.versionsByShiftId[shiftId]
                    ?: invalid("Falta la protección revisada de una ocurrencia automática.")
                if (version.isProtected || protection.hasApplicableSituation(current.localDate)) {
                    invalid("Una revisión futura no puede modificar una jornada protegida.")
                }
            }
            if (updated.planId != targetPlanId) {
                if (
                    current.state != RecurringOccurrenceState.AUTOMATIC ||
                    updated.state != RecurringOccurrenceState.RETIRED ||
                    updated.revisionId != current.revisionId
                ) {
                    invalid("Otro plan sólo puede ceder una ocurrencia automática intacta.")
                }
            } else if (
                updated.state in setOf(
                    RecurringOccurrenceState.AUTOMATIC,
                    RecurringOccurrenceState.EXCLUDED,
                ) && updated.revisionId != mutation.revisionToInsert.id
            ) {
                invalid("La ocurrencia cambiada no quedó vinculada a la revisión nueva.")
            }
        }
    }

    private fun validateRecurringPatternCoverage(
        mutation: RecurringPlanMutation,
        current: RecurringPlanAggregate?,
        protection: RecurringProtectionExpectation,
    ) {
        val revision = mutation.revisionToInsert
        if (revision.kind == RecurringPlanRevisionKind.FINALIZED) {
            val stored = current ?: invalid("No se puede finalizar un plan que todavía no existe.")
            if (
                mutation.planToInsert != null ||
                mutation.occurrencesToInsert.isNotEmpty() ||
                mutation.shiftMutation.shiftsToInsert.isNotEmpty() ||
                mutation.shiftMutation.shiftsToUpdate.isNotEmpty()
            ) {
                invalid("Finalizar un plan no puede crear ni reescribir jornadas recurrentes.")
            }
            val requiredEnd = maxOf(
                revision.endDateInclusive,
                stored.occurrences
                    .asSequence()
                    .map(RecurringOccurrence::localDate)
                    .filter { date -> !date.isBefore(revision.effectiveFrom) }
                    .maxOrNull()
                    ?: revision.endDateInclusive,
            )
            requireProtectionRange(protection, revision.effectiveFrom, requiredEnd)
            val resultingOccurrences = stored.occurrences.associateByTo(
                linkedMapOf<LocalDate, RecurringOccurrence>(),
                RecurringOccurrence::localDate,
            )
            mutation.occurrencesToUpdate
                .filter { it.planId == revision.planId }
                .forEach { resultingOccurrences[it.localDate] = it }
            stored.occurrences
                .filter { occurrence -> !occurrence.localDate.isBefore(revision.effectiveFrom) }
                .forEach { occurrence ->
                    if (occurrence.state == RecurringOccurrenceState.AUTOMATIC) {
                        val shiftId = requireNotNull(occurrence.shiftId)
                        val version = protection.versionsByShiftId[shiftId]
                            ?: invalid("Falta la protección revisada de una ocurrencia automática.")
                        val isProtected = version.isProtected ||
                            protection.hasApplicableSituation(occurrence.localDate)
                        val resulting = resultingOccurrences.getValue(occurrence.localDate)
                        if (
                            !isProtected &&
                            (
                                resulting.state != RecurringOccurrenceState.RETIRED ||
                                    resulting.shiftId != null
                                )
                        ) {
                            invalid("Finalizar debe retirar cada jornada automática intacta del tramo.")
                        }
                    }
                }
            return
        }

        val fixedClock = Clock.fixed(
            revision.effectiveFrom.atStartOfDay(revision.zoneId).toInstant(),
            revision.zoneId,
        )
        val generatedDates = expandRecurringDates(
            pattern = revision.pattern,
            startDateInclusive = revision.effectiveFrom,
            endDateInclusive = revision.endDateInclusive,
            clock = fixedClock,
            zoneId = revision.zoneId,
        ).toSet()
        val requiredEnd = maxOf(
            revision.endDateInclusive,
            current?.occurrences
                ?.asSequence()
                ?.map(RecurringOccurrence::localDate)
                ?.filter { date -> !date.isBefore(revision.effectiveFrom) }
                ?.maxOrNull()
                ?: revision.endDateInclusive,
        )
        requireProtectionRange(protection, revision.effectiveFrom, requiredEnd)
        val resultingOccurrences = current?.occurrences
            .orEmpty()
            .associateByTo(
                linkedMapOf<LocalDate, RecurringOccurrence>(),
                RecurringOccurrence::localDate,
            )
        mutation.occurrencesToUpdate
            .filter { it.planId == revision.planId }
            .forEach { resultingOccurrences[it.localDate] = it }
        mutation.occurrencesToInsert.forEach { resultingOccurrences[it.localDate] = it }

        if (!resultingOccurrences.keys.containsAll(generatedDates)) {
            invalid("La revisión no representa todas las fechas generadas por su patrón.")
        }
        current?.occurrences
            .orEmpty()
            .filter { occurrence -> !occurrence.localDate.isBefore(revision.effectiveFrom) }
            .forEach { occurrence ->
                val resulting = resultingOccurrences.getValue(occurrence.localDate)
                when {
                    occurrence.state == RecurringOccurrenceState.AUTOMATIC -> {
                        val shiftId = requireNotNull(occurrence.shiftId)
                        val version = protection.versionsByShiftId[shiftId]
                            ?: invalid("Falta la protección revisada de una ocurrencia automática.")
                        val isProtected = version.isProtected ||
                            protection.hasApplicableSituation(occurrence.localDate)
                        if (!isProtected && occurrence.localDate in generatedDates) {
                            if (
                                resulting.revisionId != revision.id ||
                                resulting.state !in setOf(
                                    RecurringOccurrenceState.AUTOMATIC,
                                    RecurringOccurrenceState.EXCLUDED,
                                )
                            ) {
                                invalid("Una ocurrencia automática intacta quedó fuera de la revisión nueva.")
                            }
                        } else if (
                            !isProtected &&
                            occurrence.localDate !in generatedDates &&
                            resulting.state != RecurringOccurrenceState.RETIRED
                        ) {
                            invalid("Una ocurrencia automática fuera del patrón no quedó retirada.")
                        }
                    }

                    occurrence.state == RecurringOccurrenceState.RETIRED &&
                        occurrence.localDate in generatedDates &&
                        (
                            resulting.revisionId != revision.id ||
                                resulting.state !in setOf(
                                    RecurringOccurrenceState.AUTOMATIC,
                                    RecurringOccurrenceState.EXCLUDED,
                                )
                            ) -> invalid("Una fecha retirada sólo puede reaparecer mediante la revisión confirmada.")
                }
            }
        val linkedToNewRevision = (mutation.occurrencesToInsert + mutation.occurrencesToUpdate)
            .filter { occurrence ->
                occurrence.planId == revision.planId && occurrence.revisionId == revision.id
            }
        if (
            linkedToNewRevision.any { occurrence ->
                occurrence.localDate !in generatedDates ||
                    occurrence.state !in setOf(
                        RecurringOccurrenceState.AUTOMATIC,
                        RecurringOccurrenceState.EXCLUDED,
                    )
            }
        ) {
            invalid("Una ocurrencia no coincide con el rango o el patrón de la revisión.")
        }
        if (
            current == null &&
            generatedDates.none { date ->
                resultingOccurrences[date]?.let { occurrence ->
                    occurrence.state == RecurringOccurrenceState.AUTOMATIC && occurrence.shiftId != null
                } == true
            }
        ) {
            invalid("Un plan nuevo debe materializar al menos una jornada concreta.")
        }
    }

    private fun requireProtectionRange(
        protection: RecurringProtectionExpectation,
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
    ) {
        val observedStart = protection.startDateInclusive
            ?: invalid("La revisión no cubre todas las protecciones de su tramo futuro.")
        val observedEnd = protection.endDateInclusive
            ?: invalid("La revisión no cubre todas las protecciones de su tramo futuro.")
        if (
            observedStart > startDateInclusive ||
            observedEnd < endDateInclusive
        ) {
            invalid("La revisión no cubre todas las protecciones de su tramo futuro.")
        }
    }

    private fun validateRecurringPairLinks(
        mutation: RecurringPlanMutation,
        expectedPlans: RecurringPlanExpectation,
    ) {
        val occurrenceChanges = mutation.occurrencesToInsert + mutation.occurrencesToUpdate
        val linkedChanges = occurrenceChanges.mapNotNull { occurrence ->
            occurrence.shiftId?.let { shiftId -> shiftId to occurrence }
        }
        if (linkedChanges.map { it.first }.distinct().size != linkedChanges.size) {
            invalid("Una jornada no puede quedar vinculada a dos ocurrencias.")
        }
        val changesByShiftId = linkedChanges.toMap()
        val insertedIds = mutation.shiftMutation.shiftsToInsert.mapTo(linkedSetOf()) { it.shift.id }
        val updatedIds = mutation.shiftMutation.shiftsToUpdate.mapTo(linkedSetOf()) { it.shift.id }
        val writtenIds = insertedIds + updatedIds
        if (changesByShiftId.keys != writtenIds) {
            invalid("Cada jornada escrita por un plan debe corresponder exactamente a una ocurrencia recurrente.")
        }

        val currentOccurrences = expectedPlans.aggregatesById.values
            .filterNotNull()
            .flatMap(RecurringPlanAggregate::occurrences)
        val currentOccurrencesByShiftId = currentOccurrences
            .mapNotNull { occurrence -> occurrence.shiftId?.let { it to occurrence } }
            .also { linked ->
                if (linked.map { it.first }.distinct().size != linked.size) {
                    invalid("La expectativa vincula una jornada a más de una ocurrencia.")
                }
            }
            .toMap()
        val currentOccurrencesByKey = currentOccurrences.associateBy { occurrence ->
            occurrence.planId to occurrence.localDate
        }

        mutation.occurrencesToInsert.forEach { occurrence ->
            occurrence.shiftId?.let { shiftId ->
                if (shiftId !in insertedIds) {
                    invalid("Una ocurrencia nueva debe materializar una jornada nueva.")
                }
            }
        }
        mutation.occurrencesToUpdate.forEach { occurrence ->
            val currentOccurrence = currentOccurrencesByKey[occurrence.planId to occurrence.localDate]
                ?: invalid("La ocurrencia actualizada no formaba parte del estado revisado.")
            when {
                currentOccurrence.shiftId == null && occurrence.shiftId != null -> {
                    if (occurrence.shiftId !in insertedIds) {
                        invalid("Reactivar una ocurrencia debe crear una jornada nueva.")
                    }
                }

                currentOccurrence.shiftId != null && occurrence.shiftId == currentOccurrence.shiftId -> {
                    if (occurrence.shiftId !in updatedIds) {
                        invalid("Versionar una ocurrencia debe actualizar su mismo par de jornada.")
                    }
                }

                currentOccurrence.shiftId != null && occurrence.shiftId == null -> {
                    if (currentOccurrence.shiftId !in mutation.shiftMutation.shiftIdsToDelete) {
                        invalid("Retirar una ocurrencia debe eliminar su par de jornada.")
                    }
                }

                currentOccurrence.shiftId != occurrence.shiftId ->
                    invalid("Una ocurrencia no puede cambiar una jornada por otra silenciosamente.")
            }
        }

        mutation.shiftMutation.shiftsToUpdate.forEach { write ->
            val currentOccurrence = currentOccurrencesByShiftId[write.shift.id]
                ?: invalid("Un plan recurrente no puede apropiarse de una jornada manual.")
            val updatedOccurrence = changesByShiftId[write.shift.id]
                ?: invalid("Una jornada recurrente versionada debe actualizar también su ocurrencia.")
            if (
                updatedOccurrence.planId != currentOccurrence.planId ||
                updatedOccurrence.localDate != currentOccurrence.localDate
            ) {
                invalid("Una jornada recurrente no puede cambiar de ocurrencia o de plan.")
            }
        }
        mutation.shiftMutation.shiftIdsToDelete.forEach { shiftId ->
            val currentOccurrence = currentOccurrencesByShiftId[shiftId]
                ?: invalid("Un plan recurrente no puede eliminar una jornada manual.")
            val clearing = mutation.occurrencesToUpdate.singleOrNull { occurrence ->
                occurrence.planId == currentOccurrence.planId &&
                    occurrence.localDate == currentOccurrence.localDate &&
                    occurrence.shiftId == null &&
                    occurrence.state in setOf(
                        RecurringOccurrenceState.EXCLUDED,
                        RecurringOccurrenceState.RETIRED,
                    )
            }
            if (clearing == null) {
                invalid("Retirar una jornada recurrente debe conservar su tumba durable.")
            }
        }
    }

    private fun validateRecurringWritesMatchRevision(
        mutation: RecurringPlanMutation,
        current: RecurringPlanAggregate?,
    ) {
        val revision = mutation.revisionToInsert
        val plan = mutation.planToInsert ?: requireNotNull(current).plan
        val occurrencesByShiftId = (mutation.occurrencesToInsert + mutation.occurrencesToUpdate)
            .mapNotNull { occurrence -> occurrence.shiftId?.let { it to occurrence } }
            .toMap()
        (mutation.shiftMutation.shiftsToInsert + mutation.shiftMutation.shiftsToUpdate)
            .forEach { write ->
                val occurrence = occurrencesByShiftId[write.shift.id]
                    ?: invalid("La jornada recurrente no conserva su ocurrencia confirmada.")
                if (
                    occurrence.planId != revision.planId ||
                    occurrence.revisionId != revision.id ||
                    occurrence.localDate != write.shift.localStartDate ||
                    occurrence.state != RecurringOccurrenceState.AUTOMATIC ||
                    plan.timelineId != write.snapshot.timelineId ||
                    plan.sector != write.snapshot.sector ||
                    write.shift.status != ShiftStatus.PLANNED ||
                    write.shift.sourceObjectiveId != revision.objectiveId ||
                    write.shift.objectiveNameSnapshot != revision.objectiveNameSnapshot ||
                    write.shift.objectiveAbbreviationSnapshot != revision.objectiveAbbreviationSnapshot ||
                    write.shift.objectiveAddressSnapshot != revision.objectiveAddressSnapshot ||
                    write.shift.startTimeSnapshot != revision.startTimeSnapshot ||
                    write.shift.endTimeSnapshot != revision.endTimeSnapshot ||
                    write.shift.colorArgbSnapshot != revision.colorArgbSnapshot ||
                    write.shift.position != revision.positionSnapshot ||
                    write.shift.zoneId != revision.zoneId ||
                    write.snapshot.workPlaceId != revision.workPlaceId ||
                    write.snapshot.objectiveId != revision.objectiveId ||
                    write.snapshot.templateId != revision.templateId ||
                    write.snapshot.workTypeId != revision.workTypeId ||
                    write.snapshot.workTypeNameSnapshot != revision.workTypeNameSnapshot ||
                    write.snapshot.workTypeBehaviorSnapshot != revision.workTypeBehaviorSnapshot
                ) {
                    invalid("La jornada recurrente no coincide exactamente con la fotografía de su revisión.")
                }
            }
    }

    private suspend fun validateIncomingWrite(
        write: V2ShiftWrite,
        history: WorkConfigurationHistory?,
        cache: IncomingWriteValidationCache,
    ) {
        val shift = write.shift.validated()
        requireExactShiftSnapshotInstants(shift)
        val snapshot = write.snapshot
        val storedHistory = history
            ?: invalid("Todavía no existe una configuración laboral.")
        val applicable = storedHistory.timeline.revisionAt(shift.localStartDate)
            ?: invalid("MiGuardia 2.0 todavía no está configurada para ${shift.localStartDate}.")
        if (
            storedHistory.timeline.id != snapshot.timelineId ||
            applicable.id != snapshot.configurationRevisionId ||
            applicable.value.sector != snapshot.sector
        ) {
            invalid("La jornada no usa la revisión laboral exacta de su fecha.")
        }

        val place = cache.workPlaces[snapshot.workPlaceId] ?: database.workCatalogDao()
            .getWorkPlaceById(snapshot.workPlaceId.toString())
            ?.toDomainWorkPlace()
            ?.also { cache.workPlaces[snapshot.workPlaceId] = it }
            ?: invalid("No existe el lugar elegido.")
        val type = cache.workTypes[snapshot.workTypeId] ?: database.workCatalogDao()
            .getWorkTypeById(snapshot.workTypeId.toString())
            ?.toDomainWorkType()
            ?.also { cache.workTypes[snapshot.workTypeId] = it }
            ?: invalid("No existe el tipo de trabajo elegido.")
        val template = cache.workTemplates[snapshot.templateId] ?: database.workCatalogDao()
            .getWorkTemplateById(snapshot.templateId.toString())
            ?.toDomainWorkTemplate()
            ?.also { cache.workTemplates[snapshot.templateId] = it }
            ?: invalid("No existe la plantilla elegida.")
        val objective = cache.objectives[snapshot.objectiveId] ?: database.objectiveDao()
            .getById(snapshot.objectiveId.toString())
            ?.toDomain()
            ?.also { cache.objectives[snapshot.objectiveId] = it }
            ?: invalid("No existe el lugar físico elegido.")

        if (!place.isActive || !type.isActive || !template.isActive) {
            invalid("El lugar, el tipo y el horario deben estar activos para guardar la jornada.")
        }
        if (
            place.timelineId != snapshot.timelineId ||
            place.sector != snapshot.sector ||
            place.objectiveId != snapshot.objectiveId ||
            type.timelineId != snapshot.timelineId ||
            type.sector != snapshot.sector ||
            template.timelineId != snapshot.timelineId ||
            template.sector != snapshot.sector ||
            template.workPlaceId != place.id ||
            template.objectiveId != objective.id ||
            template.workTypeId != type.id ||
            shift.sourceObjectiveId != objective.id
        ) {
            invalid("La jornada mezcla un lugar, tipo o plantilla de otra forma de trabajar.")
        }
        if (
            shift.objectiveNameSnapshot != objective.fullName ||
            shift.objectiveAbbreviationSnapshot != objective.abbreviation ||
            shift.objectiveAddressSnapshot != objective.address ||
            shift.startTimeSnapshot != template.startTime ||
            shift.endTimeSnapshot != template.endTime ||
            shift.colorArgbSnapshot != template.colorArgb ||
            snapshot.workTypeNameSnapshot != type.name ||
            snapshot.workTypeBehaviorSnapshot != type.behavior
        ) {
            invalid("Las fotografías de la jornada no coinciden con la selección confirmada.")
        }
        val rules = cache.rulesByWorkPlace[place.id] ?: database.workCatalogDao()
            .getRuleRevisionsForWorkPlace(place.id.toString())
            .map { it.toDomainRuleRevision() }
            .also { cache.rulesByWorkPlace[place.id] = it }
        resolveWorkplaceRuleSegments(shift, snapshot, rules)
    }

    private suspend fun requireExistingV2Write(id: UUID): V2ShiftWrite {
        val pair = dao.getShiftWithSnapshot(id.toString())
        if (pair == null) {
            missingShift(id)
        }
        return pair.toDomainWrite()
    }

    private fun validateUpdateIdentity(existing: V2ShiftWrite, updated: V2ShiftWrite) {
        if (
            existing.shift.id != updated.shift.id ||
            existing.shift.createdAt != updated.shift.createdAt ||
            existing.shift.status != updated.shift.status ||
            existing.shift.localStartDate != updated.shift.localStartDate ||
            existing.shift.zoneId != updated.shift.zoneId ||
            !updated.shift.updatedAt.isAfter(existing.shift.updatedAt)
        ) {
            invalid("Editar una jornada debe conservar UUID, fecha, zona, creación y estado.")
        }
    }

    private fun ShiftWithWorkSnapshotRow.toDomainWrite(): V2ShiftWrite = V2ShiftWrite(
        shift = shift.toDomain(),
        snapshot = snapshot.toDomainWorkSnapshot(),
    )

    private fun nextOccurrenceUpdate(
        current: Instant,
        candidate: Instant,
    ): Instant = if (candidate.isAfter(current)) candidate else current.plusMillis(1)

    private suspend fun <T> queryShiftIdBatches(
        encodedIds: List<String>,
        query: suspend (List<String>) -> List<T>,
    ): List<T> {
        if (encodedIds.isEmpty()) return emptyList()
        val rows = mutableListOf<T>()
        for (batch in encodedIds.chunked(SQLITE_SAFE_BIND_BATCH_SIZE)) {
            rows += query(batch)
        }
        return rows
    }

    private suspend fun deleteShiftsInSafeBatches(shiftIds: Set<UUID>): Int {
        var deletedRows = 0
        for (batch in shiftIds.map(UUID::toString).chunked(SQLITE_SAFE_BIND_BATCH_SIZE)) {
            deletedRows += dao.deleteShiftsAndOwnedSnapshots(batch)
        }
        return deletedRows
    }

    private class IncomingWriteValidationCache {
        val objectives: MutableMap<UUID, Objective> = hashMapOf()
        val workPlaces: MutableMap<UUID, WorkPlace> = hashMapOf()
        val workTypes: MutableMap<UUID, WorkType> = hashMapOf()
        val workTemplates: MutableMap<UUID, WorkTemplate> = hashMapOf()
        val rulesByWorkPlace: MutableMap<UUID, List<WorkplaceRuleRevision>> = hashMapOf()
    }

    private suspend fun <T> writeShiftData(
        message: String,
        block: suspend () -> T,
    ): T = try {
        database.withTransaction { block() }
    } catch (error: SQLiteConstraintException) {
        throw InvalidLocalDataException(message, error)
    } catch (error: IllegalArgumentException) {
        throw InvalidLocalDataException(message, error)
    }

    private fun missingShift(id: UUID): Nothing = invalid("No existe la jornada $id.")

    private fun invalid(message: String): Nothing = throw InvalidLocalDataException(message)

    private companion object {
        const val SQLITE_SAFE_BIND_BATCH_SIZE: Int = 900
    }
}
