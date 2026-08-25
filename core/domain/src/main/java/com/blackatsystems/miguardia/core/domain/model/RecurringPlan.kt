package com.blackatsystems.miguardia.core.domain.model

import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.core.domain.work.normalizeOptionalWorkText
import com.blackatsystems.miguardia.core.domain.work.normalizeRequiredWorkText
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Collections
import java.util.UUID

data class RecurringPlan(
    val id: UUID,
    val timelineId: UUID,
    val sector: WorkSector,
    val createdAt: Instant,
)

enum class RecurringPlanRevisionKind {
    ACTIVE,
    FINALIZED,
}

sealed interface RecurringPattern {
    @ConsistentCopyVisibility
    data class Weekdays private constructor(
        val days: Set<DayOfWeek>,
    ) : RecurringPattern {
        companion object {
            fun of(days: Iterable<DayOfWeek>): Weekdays {
                val copied = days.toSortedSet(compareBy(DayOfWeek::getValue))
                require(copied.isNotEmpty()) { "Elegí al menos un día de la semana" }
                return Weekdays(Collections.unmodifiableSet(copied))
            }
        }
    }

    data class EveryNDays(val intervalCount: Int) : RecurringPattern {
        init {
            require(intervalCount > 0) { "La cantidad de días debe ser positiva" }
        }
    }

    data class EveryNWeeks(val intervalCount: Int) : RecurringPattern {
        init {
            require(intervalCount > 0) { "La cantidad de semanas debe ser positiva" }
        }
    }

    data class Monthly(
        val ordinal: MonthlyOrdinal,
        val dayOfWeek: DayOfWeek,
    ) : RecurringPattern
}

enum class MonthlyOrdinal {
    FIRST,
    SECOND,
    THIRD,
    FOURTH,
    LAST,
}

data class RecurringPlanRevision(
    val id: UUID,
    val planId: UUID,
    val revisionNumber: Int,
    val effectiveFrom: LocalDate,
    val kind: RecurringPlanRevisionKind,
    val endDateInclusive: LocalDate,
    val pattern: RecurringPattern,
    val templateId: UUID,
    val workPlaceId: UUID,
    val objectiveId: UUID,
    val workTypeId: UUID,
    val objectiveNameSnapshot: String,
    val objectiveAbbreviationSnapshot: String,
    val objectiveAddressSnapshot: String?,
    val workTypeNameSnapshot: String,
    val workTypeBehaviorSnapshot: WorkTypeBehavior,
    val startTimeSnapshot: LocalTime,
    val endTimeSnapshot: LocalTime,
    val colorArgbSnapshot: Int,
    val positionSnapshot: String?,
    val zoneId: ZoneId,
    val createdAt: Instant,
) {
    init {
        require(revisionNumber > 0) { "El número de revisión debe ser positivo" }
        require(!endDateInclusive.isBefore(effectiveFrom)) {
            "La finalización del plan no puede ser anterior a su vigencia"
        }
        require(
            objectiveNameSnapshot == normalizeRequiredWorkText(
                objectiveNameSnapshot,
                "El nombre histórico del lugar",
            ),
        ) { "El nombre histórico del lugar debe estar normalizado" }
        require(objectiveAbbreviationSnapshot.isNotBlank()) {
            "La abreviatura histórica del lugar es obligatoria"
        }
        require(
            workTypeNameSnapshot == normalizeRequiredWorkText(
                workTypeNameSnapshot,
                "El nombre histórico del tipo de trabajo",
            ),
        ) { "El nombre histórico del tipo de trabajo debe estar normalizado" }
        require(positionSnapshot == normalizeOptionalWorkText(positionSnapshot)) {
            "El puesto histórico debe estar normalizado"
        }
        require(startTimeSnapshot.second == 0 && startTimeSnapshot.nano == 0) {
            "El inicio histórico debe expresarse en minutos enteros"
        }
        require(endTimeSnapshot.second == 0 && endTimeSnapshot.nano == 0) {
            "El final histórico debe expresarse en minutos enteros"
        }
    }
}

enum class RecurringOccurrenceState {
    AUTOMATIC,
    CUSTOMIZED,
    EXCLUDED,
    RETIRED,
}

data class RecurringOccurrence(
    val planId: UUID,
    val localDate: LocalDate,
    val revisionId: UUID,
    val shiftId: UUID?,
    val state: RecurringOccurrenceState,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(!updatedAt.isBefore(createdAt)) {
            "La actualización de una ocurrencia no puede ser anterior a su creación"
        }
        when (state) {
            RecurringOccurrenceState.AUTOMATIC,
            RecurringOccurrenceState.CUSTOMIZED,
            -> requireNotNull(shiftId) { "Una ocurrencia activa debe conservar su jornada" }

            RecurringOccurrenceState.EXCLUDED,
            RecurringOccurrenceState.RETIRED,
            -> require(shiftId == null) { "Una ocurrencia sin jornada no puede conservar shiftId" }
        }
    }
}

data class RecurringPlanAggregate(
    val plan: RecurringPlan,
    val revisions: List<RecurringPlanRevision>,
    val occurrences: List<RecurringOccurrence>,
) {
    init {
        require(revisions.isNotEmpty()) { "Un plan guardado debe tener al menos una revisión" }
        require(revisions.all { it.planId == plan.id }) {
            "Las revisiones no pertenecen al plan"
        }
        require(revisions.map { it.id }.distinct().size == revisions.size) {
            "Un plan no puede repetir identificadores de revisión"
        }
        require(revisions.map { it.revisionNumber }.distinct().size == revisions.size) {
            "Un plan no puede repetir números de revisión"
        }
        require(revisions.sortedBy { it.revisionNumber }.map { it.revisionNumber } == (1..revisions.size).toList()) {
            "Las revisiones de un plan deben avanzar sin huecos"
        }
        val finalized = revisions.filter { it.kind == RecurringPlanRevisionKind.FINALIZED }
        require(finalized.isEmpty() || (finalized.size == 1 && finalized.single().revisionNumber == revisions.size)) {
            "Una finalización debe ser la última revisión del plan"
        }
        val revisionIds = revisions.mapTo(hashSetOf()) { it.id }
        require(occurrences.all { it.planId == plan.id && it.revisionId in revisionIds }) {
            "Las ocurrencias no pertenecen a una revisión del plan"
        }
        require(occurrences.map { it.localDate }.distinct().size == occurrences.size) {
            "Un plan sólo puede tener una ocurrencia por fecha"
        }
        require(occurrences.mapNotNull { it.shiftId }.distinct().size == occurrences.mapNotNull { it.shiftId }.size) {
            "Una jornada no puede pertenecer a dos ocurrencias del mismo plan"
        }
    }

    val latestRevision: RecurringPlanRevision
        get() = revisions.maxBy(RecurringPlanRevision::revisionNumber)
}

@ConsistentCopyVisibility
data class RecurringPlanExpectation private constructor(
    val aggregatesById: Map<UUID, RecurringPlanAggregate?>,
) {
    companion object {
        fun capture(
            expectedByPlanId: Map<UUID, RecurringPlanAggregate?>,
        ): RecurringPlanExpectation = RecurringPlanExpectation(
            Collections.unmodifiableMap(
                expectedByPlanId
                    .mapValuesTo(linkedMapOf()) { (_, aggregate) -> aggregate?.defensiveCopy() },
            ),
        )

        fun capture(
            planId: UUID,
            aggregate: RecurringPlanAggregate?,
        ): RecurringPlanExpectation = capture(mapOf(planId to aggregate))
    }
}

data class RecurringNoteVersion(
    val id: UUID,
    val updatedAt: Instant,
)

data class RecurringMedicalLeaveVersion(
    val id: UUID,
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    val updatedAt: Instant,
) {
    init {
        require(!endDateInclusive.isBefore(startDate)) {
            "La señal de carpeta médica tiene un rango inválido"
        }
    }

    operator fun contains(date: LocalDate): Boolean = date in startDate..endDateInclusive
}

data class RecurringShiftProtectionVersion(
    val shiftId: UUID,
    val status: ShiftStatus,
    val notes: Set<RecurringNoteVersion>,
    val hasNotificationConfig: Boolean,
    val notificationLeadMinutes: List<Long>,
) {
    val isProtected: Boolean
        get() = status != ShiftStatus.PLANNED || notes.isNotEmpty() || hasNotificationConfig
}

@ConsistentCopyVisibility
data class RecurringProtectionExpectation private constructor(
    val versionsByShiftId: Map<UUID, RecurringShiftProtectionVersion>,
    val startDateInclusive: LocalDate?,
    val endDateInclusive: LocalDate?,
    val medicalLeaves: List<RecurringMedicalLeaveVersion>,
) {
    fun hasApplicableSituation(date: LocalDate): Boolean = medicalLeaves.any { date in it }

    companion object {
        fun capture(
            versions: Iterable<RecurringShiftProtectionVersion>,
            startDateInclusive: LocalDate? = null,
            endDateInclusive: LocalDate? = null,
            medicalLeaves: Iterable<RecurringMedicalLeaveVersion> = emptyList(),
        ): RecurringProtectionExpectation {
            require((startDateInclusive == null) == (endDateInclusive == null)) {
                "La expectativa de situaciones debe indicar el rango completo"
            }
            if (startDateInclusive != null && endDateInclusive != null) {
                require(!endDateInclusive.isBefore(startDateInclusive)) {
                    "La expectativa de situaciones tiene un rango inválido"
                }
            }
            val copied = versions.map { version ->
                version.copy(
                    notes = Collections.unmodifiableSet(LinkedHashSet(version.notes)),
                    notificationLeadMinutes = Collections.unmodifiableList(version.notificationLeadMinutes.toList()),
                )
            }
            require(copied.map { it.shiftId }.distinct().size == copied.size) {
                "Una expectativa de protección no puede repetir jornadas"
            }
            val copiedMedicalLeaves = medicalLeaves
                .map { it.copy() }
                .sortedWith(compareBy(RecurringMedicalLeaveVersion::startDate, RecurringMedicalLeaveVersion::endDateInclusive, RecurringMedicalLeaveVersion::id))
            require(copiedMedicalLeaves.map { it.id }.distinct().size == copiedMedicalLeaves.size) {
                "Una expectativa de protección no puede repetir carpetas médicas"
            }
            if (startDateInclusive == null) {
                require(copiedMedicalLeaves.isEmpty()) {
                    "Las carpetas médicas esperadas necesitan un rango observado"
                }
            } else {
                require(copiedMedicalLeaves.all { leave ->
                    leave.startDate <= requireNotNull(endDateInclusive) &&
                        leave.endDateInclusive >= startDateInclusive
                }) { "La expectativa contiene una carpeta médica fuera del rango observado" }
            }
            return RecurringProtectionExpectation(
                Collections.unmodifiableMap(copied.associateByTo(linkedMapOf()) { it.shiftId }),
                startDateInclusive,
                endDateInclusive,
                Collections.unmodifiableList(copiedMedicalLeaves),
            )
        }

        val EMPTY: RecurringProtectionExpectation = capture(emptyList())
    }
}

data class RecurringPlanMutation(
    val planToInsert: RecurringPlan? = null,
    val revisionToInsert: RecurringPlanRevision,
    val occurrencesToInsert: List<RecurringOccurrence> = emptyList(),
    val occurrencesToUpdate: List<RecurringOccurrence> = emptyList(),
    val shiftMutation: V2ShiftBatchMutation = V2ShiftBatchMutation(),
) {
    init {
        val planId = planToInsert?.id ?: revisionToInsert.planId
        require(revisionToInsert.planId == planId) { "La revisión no pertenece al plan mutado" }
        if (planToInsert != null) {
            require(revisionToInsert.revisionNumber == 1) {
                "Un plan nuevo debe comenzar en la revisión uno"
            }
        }
        val allOccurrences = occurrencesToInsert + occurrencesToUpdate
        require(occurrencesToInsert.all { it.planId == planId }) {
            "Las ocurrencias nuevas deben pertenecer al plan mutado"
        }
        val keys = allOccurrences.map { it.planId to it.localDate }
        require(keys.distinct().size == keys.size) {
            "Una mutación no puede escribir dos veces la misma ocurrencia"
        }
        require(
            occurrencesToInsert.mapNotNull { it.shiftId }.distinct().size ==
                occurrencesToInsert.mapNotNull { it.shiftId }.size,
        ) { "Una jornada no puede vincularse dos veces en una mutación" }
        require(
            shiftMutation.explicitDayStatusDatesToClear.all { date ->
                shiftMutation.shiftsToInsert.any { it.shift.localStartDate == date }
            },
        ) { "Sólo una jornada insertada puede limpiar F o ?" }
    }
}

private fun RecurringPlanAggregate.defensiveCopy(): RecurringPlanAggregate = copy(
    revisions = Collections.unmodifiableList(revisions.toList()),
    occurrences = Collections.unmodifiableList(occurrences.toList()),
)
