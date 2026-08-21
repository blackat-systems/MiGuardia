package com.blackatsystems.miguardia.core.database.mapping

import com.blackatsystems.miguardia.core.database.dao.WorkConfigurationRootWithRelations
import com.blackatsystems.miguardia.core.database.entity.PerPeriodHoursDefinitionEntity
import com.blackatsystems.miguardia.core.database.entity.PerPeriodHoursValueEntity
import com.blackatsystems.miguardia.core.database.entity.WorkConfigurationRevisionEntity
import com.blackatsystems.miguardia.core.database.entity.WorkConfigurationRootEntity
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.DateWindow
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursEntry
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursKey
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationOrigin
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

internal data class EncodedWorkConfigurationRevision(
    val revision: WorkConfigurationRevisionEntity,
    val perPeriodDefinition: PerPeriodHoursDefinitionEntity?,
)

internal fun newWorkConfigurationRoot(timelineId: UUID) = WorkConfigurationRootEntity(
    timelineId = timelineId.toString(),
    singletonSlot = WORK_CONFIGURATION_SINGLETON_SLOT,
    origin = ORIGIN_NEW_V2,
)

internal fun EffectiveRevision<WorkConfiguration>.toEntity(
    timelineId: UUID,
): EncodedWorkConfigurationRevision {
    val encodedReference = value.hoursReference.encode(timelineId)
    return EncodedWorkConfigurationRevision(
        revision = WorkConfigurationRevisionEntity(
            id = id.toString(),
            timelineId = timelineId.toString(),
            effectiveFrom = effectiveFrom.toString(),
            sector = value.sector.encode(),
            availabilityLabel = value.availabilityLabel?.encode(),
            hoursReferenceKind = encodedReference.kind,
            periodKind = encodedReference.inlinePeriod?.kind,
            weeklyFirstDayIso = encodedReference.inlinePeriod?.weeklyFirstDayIso,
            cycleAnchorDate = encodedReference.inlinePeriod?.cycleAnchorDate,
            cycleLengthDays = encodedReference.inlinePeriod?.cycleLengthDays,
            requiredMinutes = encodedReference.requiredMinutes,
            perPeriodDefinitionId = encodedReference.perPeriodDefinition?.id,
        ),
        perPeriodDefinition = encodedReference.perPeriodDefinition,
    )
}

internal fun PerPeriodHoursEntry.toEntity() = PerPeriodHoursValueEntity(
    id = id.toString(),
    definitionId = key.definitionId.toString(),
    windowStartInclusive = key.window.startInclusive.toString(),
    windowEndExclusive = key.window.endExclusive.toString(),
    requiredMinutes = requiredMinutes.value,
)

internal fun List<WorkConfigurationRootWithRelations>.toDomainOrNull(
    orphanRowCount: Int,
): WorkConfigurationHistory? =
    decodeWorkConfigurationRows {
        require(orphanRowCount == 0) {
            "La configuración laboral contiene filas sin su registro principal"
        }
        if (isEmpty()) return@decodeWorkConfigurationRows null
        require(size == 1) { "Debe existir como máximo una raíz de configuración laboral" }

        val aggregate = single()
        require(aggregate.root.singletonSlot == WORK_CONFIGURATION_SINGLETON_SLOT) {
            "La raíz de configuración laboral debe ocupar el slot 1"
        }

        val timelineId = UUID.fromString(aggregate.root.timelineId)
        val definitions = aggregate.perPeriodDefinitions.associate { relation ->
            require(relation.definition.timelineId == aggregate.root.timelineId) {
                "La definición por período pertenece a otra configuración"
            }
            val definitionId = UUID.fromString(relation.definition.id)
            definitionId to relation.definition.decodePeriod()
        }
        require(definitions.size == aggregate.perPeriodDefinitions.size) {
            "No puede repetirse una definición por período"
        }

        val revisions = aggregate.revisions.map { entity ->
            require(entity.timelineId == aggregate.root.timelineId) {
                "La revisión pertenece a otra configuración"
            }
            entity.toDomain(definitions)
        }
        val referencedDefinitionIds = revisions.mapNotNull { revision ->
            (revision.value.hoursReference as? HoursReference.PerPeriod)?.definitionId
        }.toSet()
        require(definitions.keys.all { it in referencedDefinitionIds }) {
            "Existe una definición por período sin una revisión que la utilice"
        }

        val values = aggregate.perPeriodDefinitions.flatMap { relation ->
            val definitionId = UUID.fromString(relation.definition.id)
            val period = definitions.getValue(definitionId)
            relation.values.map { entity -> entity.toDomain(definitionId, period) }
        }

        WorkConfigurationHistory(
            origin = aggregate.root.origin.decodeOrigin(),
            timeline = EffectiveDateTimeline(timelineId, revisions),
            perPeriodHoursValues = PerPeriodHoursValues(values),
        )
    }

private data class EncodedHoursReference(
    val kind: String,
    val inlinePeriod: EncodedPeriod? = null,
    val requiredMinutes: Long? = null,
    val perPeriodDefinition: PerPeriodHoursDefinitionEntity? = null,
)

private data class EncodedPeriod(
    val kind: String,
    val weeklyFirstDayIso: Int? = null,
    val cycleAnchorDate: String? = null,
    val cycleLengthDays: Int? = null,
)

private fun HoursReference.encode(timelineId: UUID): EncodedHoursReference = when (this) {
    HoursReference.PendingSetup -> EncodedHoursReference(kind = REFERENCE_PENDING_SETUP)
    HoursReference.NotUsed -> EncodedHoursReference(kind = REFERENCE_NOT_USED)
    is HoursReference.Unknown -> EncodedHoursReference(
        kind = REFERENCE_UNKNOWN,
        inlinePeriod = period?.encode(),
    )
    is HoursReference.Fixed -> EncodedHoursReference(
        kind = REFERENCE_FIXED,
        inlinePeriod = period.encode(),
        requiredMinutes = requiredMinutes.value,
    )
    is HoursReference.PerPeriod -> EncodedHoursReference(
        kind = REFERENCE_PER_PERIOD,
        perPeriodDefinition = period.toDefinitionEntity(
            timelineId = timelineId,
            definitionId = definitionId,
        ),
    )
}

private fun HoursPeriod.encode(): EncodedPeriod = when (this) {
    HoursPeriod.Monthly -> EncodedPeriod(kind = PERIOD_MONTHLY)
    is HoursPeriod.Weekly -> EncodedPeriod(
        kind = PERIOD_WEEKLY,
        weeklyFirstDayIso = firstDay.value,
    )
    is HoursPeriod.Cycle -> EncodedPeriod(
        kind = PERIOD_CYCLE,
        cycleAnchorDate = anchorDate.toString(),
        cycleLengthDays = lengthDays,
    )
}

private fun HoursPeriod.toDefinitionEntity(
    timelineId: UUID,
    definitionId: UUID,
): PerPeriodHoursDefinitionEntity {
    val encoded = encode()
    return PerPeriodHoursDefinitionEntity(
        id = definitionId.toString(),
        timelineId = timelineId.toString(),
        periodKind = encoded.kind,
        weeklyFirstDayIso = encoded.weeklyFirstDayIso,
        cycleAnchorDate = encoded.cycleAnchorDate,
        cycleLengthDays = encoded.cycleLengthDays,
    )
}

private fun WorkConfigurationRevisionEntity.toDomain(
    definitions: Map<UUID, HoursPeriod>,
): EffectiveRevision<WorkConfiguration> = EffectiveRevision(
    id = UUID.fromString(id),
    effectiveFrom = LocalDate.parse(effectiveFrom),
    value = WorkConfiguration(
        sector = sector.decodeSector(),
        hoursReference = decodeHoursReference(definitions),
        availabilityLabel = availabilityLabel?.decodeAvailabilityLabel(),
    ),
)

private fun WorkConfigurationRevisionEntity.decodeHoursReference(
    definitions: Map<UUID, HoursPeriod>,
): HoursReference = when (hoursReferenceKind) {
    REFERENCE_PENDING_SETUP -> {
        requireEmptyReferenceDetails()
        HoursReference.PendingSetup
    }
    REFERENCE_NOT_USED -> {
        requireEmptyReferenceDetails()
        HoursReference.NotUsed
    }
    REFERENCE_UNKNOWN -> {
        require(requiredMinutes == null && perPeriodDefinitionId == null) {
            "Una referencia desconocida no puede tener minutos ni definición por período"
        }
        HoursReference.Unknown(decodeInlinePeriod(required = false))
    }
    REFERENCE_FIXED -> {
        require(perPeriodDefinitionId == null) {
            "Una referencia fija no puede usar una definición por período"
        }
        val minutes = requireNotNull(requiredMinutes) {
            "Una referencia fija debe tener minutos"
        }
        HoursReference.Fixed(
            period = requireNotNull(decodeInlinePeriod(required = true)),
            requiredMinutes = PositiveMinutes(minutes),
        )
    }
    REFERENCE_PER_PERIOD -> {
        requireNoInlinePeriod()
        require(requiredMinutes == null) {
            "Una referencia por período no puede tener minutos fijos"
        }
        val definitionId = UUID.fromString(requireNotNull(perPeriodDefinitionId) {
            "Una referencia por período debe indicar su definición"
        })
        HoursReference.PerPeriod(
            definitionId = definitionId,
            period = requireNotNull(definitions[definitionId]) {
                "No existe la definición por período $definitionId"
            },
        )
    }
    else -> error("Código de referencia de horas desconocido: $hoursReferenceKind")
}

private fun WorkConfigurationRevisionEntity.requireEmptyReferenceDetails() {
    requireNoInlinePeriod()
    require(requiredMinutes == null && perPeriodDefinitionId == null) {
        "La referencia $hoursReferenceKind no admite minutos ni definición"
    }
}

private fun WorkConfigurationRevisionEntity.requireNoInlinePeriod() {
    require(
        periodKind == null &&
            weeklyFirstDayIso == null &&
            cycleAnchorDate == null &&
            cycleLengthDays == null,
    ) { "La referencia $hoursReferenceKind no admite un período inline" }
}

private fun WorkConfigurationRevisionEntity.decodeInlinePeriod(required: Boolean): HoursPeriod? {
    if (periodKind == null) {
        require(!required) { "La referencia $hoursReferenceKind debe indicar un período" }
        require(weeklyFirstDayIso == null && cycleAnchorDate == null && cycleLengthDays == null) {
            "No puede haber detalles de período sin un tipo de período"
        }
        return null
    }
    return decodePeriod(periodKind, weeklyFirstDayIso, cycleAnchorDate, cycleLengthDays)
}

private fun PerPeriodHoursDefinitionEntity.decodePeriod(): HoursPeriod = decodePeriod(
    kind = periodKind,
    weeklyFirstDayIso = weeklyFirstDayIso,
    cycleAnchorDate = cycleAnchorDate,
    cycleLengthDays = cycleLengthDays,
)

private fun decodePeriod(
    kind: String,
    weeklyFirstDayIso: Int?,
    cycleAnchorDate: String?,
    cycleLengthDays: Int?,
): HoursPeriod = when (kind) {
    PERIOD_MONTHLY -> {
        require(weeklyFirstDayIso == null && cycleAnchorDate == null && cycleLengthDays == null) {
            "Un período mensual no admite detalles semanales ni cíclicos"
        }
        HoursPeriod.Monthly
    }
    PERIOD_WEEKLY -> {
        require(cycleAnchorDate == null && cycleLengthDays == null) {
            "Un período semanal no admite detalles cíclicos"
        }
        HoursPeriod.Weekly(
            DayOfWeek.of(requireNotNull(weeklyFirstDayIso) {
                "Un período semanal debe indicar su primer día"
            }),
        )
    }
    PERIOD_CYCLE -> {
        require(weeklyFirstDayIso == null) {
            "Un ciclo no admite un primer día semanal"
        }
        HoursPeriod.Cycle(
            anchorDate = LocalDate.parse(requireNotNull(cycleAnchorDate) {
                "Un ciclo debe indicar su fecha de anclaje"
            }),
            lengthDays = requireNotNull(cycleLengthDays) {
                "Un ciclo debe indicar su cantidad de días"
            },
        )
    }
    else -> error("Código de período desconocido: $kind")
}

private fun PerPeriodHoursValueEntity.toDomain(
    canonicalDefinitionId: UUID,
    period: HoursPeriod,
): PerPeriodHoursEntry {
    val storedDefinitionId = UUID.fromString(definitionId)
    require(storedDefinitionId == canonicalDefinitionId) {
        "El valor por período pertenece a otra definición"
    }
    return PerPeriodHoursEntry(
        id = UUID.fromString(id),
        key = PerPeriodHoursKey(
            definitionId = storedDefinitionId,
            period = period,
            window = DateWindow(
                startInclusive = LocalDate.parse(windowStartInclusive),
                endExclusive = LocalDate.parse(windowEndExclusive),
            ),
        ),
        requiredMinutes = PositiveMinutes(requiredMinutes),
    )
}

private fun WorkSector.encode(): String = when (this) {
    WorkSector.PRIVATE_SECURITY -> SECTOR_PRIVATE_SECURITY
    WorkSector.POLICE -> SECTOR_POLICE
    WorkSector.NURSING -> SECTOR_NURSING
    WorkSector.MEDICINE -> SECTOR_MEDICINE
}

private fun String.decodeSector(): WorkSector = when (this) {
    SECTOR_PRIVATE_SECURITY -> WorkSector.PRIVATE_SECURITY
    SECTOR_POLICE -> WorkSector.POLICE
    SECTOR_NURSING -> WorkSector.NURSING
    SECTOR_MEDICINE -> WorkSector.MEDICINE
    else -> error("Código de sector desconocido: $this")
}

private fun AvailabilityLabel.encode(): String = when (this) {
    AvailabilityLabel.PASSIVE_GUARD -> AVAILABILITY_PASSIVE_GUARD
    AvailabilityLabel.AVAILABLE_FOR_CALL -> AVAILABILITY_AVAILABLE_FOR_CALL
    AvailabilityLabel.ON_CALL_RETAINER -> AVAILABILITY_ON_CALL_RETAINER
}

private fun String.decodeAvailabilityLabel(): AvailabilityLabel = when (this) {
    AVAILABILITY_PASSIVE_GUARD -> AvailabilityLabel.PASSIVE_GUARD
    AVAILABILITY_AVAILABLE_FOR_CALL -> AvailabilityLabel.AVAILABLE_FOR_CALL
    AVAILABILITY_ON_CALL_RETAINER -> AvailabilityLabel.ON_CALL_RETAINER
    else -> error("Código de disponibilidad desconocido: $this")
}

private fun String.decodeOrigin(): WorkConfigurationOrigin = when (this) {
    ORIGIN_MIGRATED_V1 -> WorkConfigurationOrigin.MIGRATED_V1
    ORIGIN_NEW_V2 -> WorkConfigurationOrigin.NEW_V2
    else -> error("Código de origen de configuración desconocido: $this")
}

private inline fun <T> decodeWorkConfigurationRows(block: () -> T): T = try {
    block()
} catch (error: RuntimeException) {
    throw InvalidLocalDataException("La configuración laboral almacenada contiene datos inválidos.", error)
}

internal const val WORK_CONFIGURATION_SINGLETON_SLOT: Int = 1
internal const val ORIGIN_MIGRATED_V1: String = "MIGRATED_V1"
internal const val ORIGIN_NEW_V2: String = "NEW_V2"

private const val SECTOR_PRIVATE_SECURITY: String = "PRIVATE_SECURITY"
private const val SECTOR_POLICE: String = "POLICE"
private const val SECTOR_NURSING: String = "NURSING"
private const val SECTOR_MEDICINE: String = "MEDICINE"

private const val AVAILABILITY_PASSIVE_GUARD: String = "PASSIVE_GUARD"
private const val AVAILABILITY_AVAILABLE_FOR_CALL: String = "AVAILABLE_FOR_CALL"
private const val AVAILABILITY_ON_CALL_RETAINER: String = "ON_CALL_RETAINER"

private const val REFERENCE_PENDING_SETUP: String = "PENDING_SETUP"
private const val REFERENCE_NOT_USED: String = "NOT_USED"
private const val REFERENCE_UNKNOWN: String = "UNKNOWN"
private const val REFERENCE_FIXED: String = "FIXED"
private const val REFERENCE_PER_PERIOD: String = "PER_PERIOD"

private const val PERIOD_MONTHLY: String = "MONTHLY"
private const val PERIOD_WEEKLY: String = "WEEKLY"
private const val PERIOD_CYCLE: String = "CYCLE"
