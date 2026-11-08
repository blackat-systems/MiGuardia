package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftExtraInterval
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.report.MonthlyReportSnapshotRequest
import com.blackatsystems.miguardia.core.domain.report.MonthlyReportSourceSnapshot
import com.blackatsystems.miguardia.core.domain.report.ReportPrivacySelection
import com.blackatsystems.miguardia.core.domain.report.resolveReportCaptureRange
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryInput
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HolidayRule
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.NightHoursRule
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.WeekendRule
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRuleRevision
import com.blackatsystems.miguardia.core.domain.work.WorkplaceRules
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Fixture única de la etapa 12.2 (Cálculos y consulta): la misma fuente alimenta Horas, Resumen e
 * Informes en la PC y, sembrada en Room, en el teléfono. Mes cerrado para que el resultado no dependa
 * de la hora de la corrida. Los valores esperados están escritos a mano, no derivados del código.
 */
object Stage12CalculationFixture {
    val MONTH: YearMonth = YearMonth.of(2026, 9)
    val ZONE: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    val NOW: Instant = Instant.parse("2026-10-05T15:00:00Z")
    val CLOCK: Clock = Clock.fixed(NOW, ZONE)
    val CREATED_AT: Instant = Instant.parse("2026-08-20T12:00:00Z")
    val SECTOR: WorkSector = WorkSector.NURSING

    const val TARGET_MINUTES: Long = 1_200L

    /** Día 7: planificada 08:00–16:00, real 08:00–17:00 con 16:00–17:00 extra que cuenta. */
    val DAY_SHIFT_DATE: LocalDate = LocalDate.of(2026, 9, 7)

    /** Día 14: nocturna 22:00–06:00 sin horario real; cruza la medianoche. */
    val NIGHT_SHIFT_DATE: LocalDate = LocalDate.of(2026, 9, 14)

    /** Día 20: extra independiente 10:00–13:30 que no cuenta para la meta. */
    val INDEPENDENT_DATE: LocalDate = LocalDate.of(2026, 9, 20)

    /** Día 22: disponibilidad 08:00–20:00, separada del trabajo. */
    val AVAILABILITY_DATE: LocalDate = LocalDate.of(2026, 9, 22)

    /** Día 25: licencia médica con nota privada; no tiene jornadas. */
    val MEDICAL_DATE: LocalDate = LocalDate.of(2026, 9, 25)

    object Expected {
        const val REGULAR: Long = 960L
        const val SHIFT_EXTRA_HELPING: Long = 60L
        const val INDEPENDENT_NOT_HELPING: Long = 210L
        const val EXTRAS: Long = SHIFT_EXTRA_HELPING + INDEPENDENT_NOT_HELPING
        const val TOTAL: Long = REGULAR + EXTRAS
        const val HELPS_REFERENCE: Long = REGULAR + SHIFT_EXTRA_HELPING
        const val DOES_NOT_HELP: Long = INDEPENDENT_NOT_HELPING
        const val TARGET: Long = TARGET_MINUTES
        const val MISSING: Long = TARGET - HELPS_REFERENCE
        const val PENDING: Long = 0L
        const val AVAILABILITY_PROGRAMMED: Long = 720L
        const val WORK_ROWS: Int = 3
    }

    /** Textos que nunca deben salir en un informe, o sólo con su opción expresa. */
    object Canary {
        const val ADDRESS: String = "Calle Canario Direccion 123"
        const val DIFFERENCE_REASON: String = "Motivo canario del horario real"
        const val POSITION: String = "Puesto canario"
        const val SHIFT_NOTE: String = "Nota canaria de jornada"
        const val MEDICAL_NOTE: String = "Nota medica canaria"
    }

    const val PLACE_NAME: String = "Hospital ficticio 12.2"
    const val PLACE_ABBREVIATION: String = "HF2"
    const val WORK_TYPE_NAME: String = "Turno ficticio"
    const val HELPING_CLASS_NAME: String = "Refuerzo que cuenta"
    const val NOT_HELPING_CLASS_NAME: String = "Extra aparte"
    const val COLOR: Int = 0xFF2F6B4F.toInt()

    val TIMELINE_ID: UUID = uuid(1)
    val CONFIGURATION_REVISION_ID: UUID = uuid(2)
    val WORK_PLACE_ID: UUID = uuid(3)
    val OBJECTIVE_ID: UUID = uuid(4)
    val WORK_TYPE_ID: UUID = uuid(5)
    val TEMPLATE_ID: UUID = uuid(6)
    val WORKPLACE_RULE_ID: UUID = uuid(7)
    val DAY_SHIFT_ID: UUID = uuid(10)
    val NIGHT_SHIFT_ID: UUID = uuid(11)
    val HELPING_CLASS_ID: UUID = uuid(12)
    val NOT_HELPING_CLASS_ID: UUID = uuid(13)
    val SHIFT_EXTRA_ID: UUID = uuid(14)
    val INDEPENDENT_EXTRA_ID: UUID = uuid(15)
    val AVAILABILITY_ID: UUID = uuid(16)
    val MEDICAL_LEAVE_ID: UUID = uuid(17)
    val SHIFT_NOTE_ID: UUID = uuid(18)
    val NIGHT_TEMPLATE_ID: UUID = uuid(19)

    val configuration: WorkConfigurationHistory = WorkConfigurationHistory(
        timeline = EffectiveDateTimeline(
            TIMELINE_ID,
            listOf(
                EffectiveRevision(
                    id = CONFIGURATION_REVISION_ID,
                    effectiveFrom = MONTH.atDay(1),
                    value = WorkConfiguration(
                        sector = SECTOR,
                        hoursReference = HoursReference.Fixed(
                            period = HoursPeriod.Monthly,
                            requiredMinutes = PositiveMinutes(TARGET_MINUTES),
                        ),
                        availabilityLabel = AvailabilityLabel.ON_CALL_RETAINER,
                        hoursReferenceStartedOn = MONTH.atDay(1),
                    ),
                ),
            ),
        ),
        perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
    )

    val dayShift: V2ShiftWrite = shiftWrite(
        id = DAY_SHIFT_ID,
        date = DAY_SHIFT_DATE,
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(16, 0),
        position = Canary.POSITION,
        templateId = TEMPLATE_ID,
    )

    val nightShift: V2ShiftWrite = shiftWrite(
        id = NIGHT_SHIFT_ID,
        date = NIGHT_SHIFT_DATE,
        startTime = LocalTime.of(22, 0),
        endTime = LocalTime.of(6, 0),
        position = null,
        templateId = NIGHT_TEMPLATE_ID,
    )

    val dayActual: ShiftActualAggregate = ShiftActualAggregate(
        record = ShiftActualRecord(
            shiftId = DAY_SHIFT_ID,
            timelineId = TIMELINE_ID,
            sector = SECTOR,
            actualStart = local(DAY_SHIFT_DATE, LocalTime.of(8, 0)),
            actualEnd = local(DAY_SHIFT_DATE, LocalTime.of(17, 0)),
            differenceReason = Canary.DIFFERENCE_REASON,
            explanation = null,
            createdAt = CREATED_AT,
            updatedAt = CREATED_AT,
        ),
        extraIntervals = listOf(
            ShiftExtraInterval(
                id = SHIFT_EXTRA_ID,
                shiftId = DAY_SHIFT_ID,
                timelineId = TIMELINE_ID,
                sector = SECTOR,
                extraWorkClassId = HELPING_CLASS_ID,
                start = local(DAY_SHIFT_DATE, LocalTime.of(16, 0)),
                end = local(DAY_SHIFT_DATE, LocalTime.of(17, 0)),
                classNameSnapshot = HELPING_CLASS_NAME,
                helpsMeetHoursReferenceSnapshot = true,
                showDedicatedSummarySnapshot = true,
                createdAt = CREATED_AT,
                updatedAt = CREATED_AT,
            ),
        ),
    )

    val independentExtra: IndependentExtraWorkRecord = IndependentExtraWorkRecord(
        id = INDEPENDENT_EXTRA_ID,
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        configurationRevisionId = CONFIGURATION_REVISION_ID,
        workPlaceId = WORK_PLACE_ID,
        objectiveId = OBJECTIVE_ID,
        workTypeId = WORK_TYPE_ID,
        templateId = null,
        extraWorkClassId = NOT_HELPING_CLASS_ID,
        ownerLocalDate = INDEPENDENT_DATE,
        zoneId = ZONE,
        start = local(INDEPENDENT_DATE, LocalTime.of(10, 0)),
        end = local(INDEPENDENT_DATE, LocalTime.of(13, 30)),
        snapshot = IndependentExtraWorkSnapshot(
            workPlaceName = PLACE_NAME,
            workPlaceAbbreviation = PLACE_ABBREVIATION,
            workPlaceAddress = Canary.ADDRESS,
            workTypeName = WORK_TYPE_NAME,
            workTypeBehavior = WorkTypeBehavior.ACTIVE_WORK,
            colorArgb = COLOR,
            position = null,
            className = NOT_HELPING_CLASS_NAME,
            helpsMeetHoursReference = false,
            showDedicatedSummary = true,
        ),
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
    )

    val availability: AvailabilityWindowRecord = AvailabilityWindowRecord(
        id = AVAILABILITY_ID,
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        configurationRevisionId = CONFIGURATION_REVISION_ID,
        ownerLocalDate = AVAILABILITY_DATE,
        zoneId = ZONE,
        start = local(AVAILABILITY_DATE, LocalTime.of(8, 0)),
        end = local(AVAILABILITY_DATE, LocalTime.of(20, 0)),
        labelSnapshot = AvailabilityLabel.ON_CALL_RETAINER.displayName,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
    )

    val medicalLeave: MedicalLeave = MedicalLeave(
        id = MEDICAL_LEAVE_ID,
        startDate = MEDICAL_DATE,
        endDateInclusive = MEDICAL_DATE,
        privateNote = Canary.MEDICAL_NOTE,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
    )

    val shiftNote: ShiftNote = ShiftNote(
        id = SHIFT_NOTE_ID,
        shiftId = DAY_SHIFT_ID,
        body = Canary.SHIFT_NOTE,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
    )

    val catalog: WorkCatalog = WorkCatalog(
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        workPlaces = listOf(
            WorkPlace(
                id = WORK_PLACE_ID,
                timelineId = TIMELINE_ID,
                sector = SECTOR,
                objectiveId = OBJECTIVE_ID,
                isActive = true,
                createdAt = CREATED_AT,
                updatedAt = CREATED_AT,
            ),
        ),
        workTypes = listOf(
            WorkType.create(
                id = WORK_TYPE_ID,
                timelineId = TIMELINE_ID,
                sector = SECTOR,
                rawName = WORK_TYPE_NAME,
                timestamp = CREATED_AT,
            ),
        ),
        workTemplates = listOf(
            WorkTemplate(
                id = TEMPLATE_ID,
                timelineId = TIMELINE_ID,
                sector = SECTOR,
                workPlaceId = WORK_PLACE_ID,
                objectiveId = OBJECTIVE_ID,
                workTypeId = WORK_TYPE_ID,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(16, 0),
                colorArgb = COLOR,
                isActive = true,
                createdAt = CREATED_AT,
                updatedAt = CREATED_AT,
            ),
            WorkTemplate(
                id = NIGHT_TEMPLATE_ID,
                timelineId = TIMELINE_ID,
                sector = SECTOR,
                workPlaceId = WORK_PLACE_ID,
                objectiveId = OBJECTIVE_ID,
                workTypeId = WORK_TYPE_ID,
                startTime = LocalTime.of(22, 0),
                endTime = LocalTime.of(6, 0),
                colorArgb = COLOR,
                isActive = true,
                createdAt = CREATED_AT,
                updatedAt = CREATED_AT,
            ),
        ),
        workplaceRuleRevisions = listOf(
            WorkplaceRuleRevision(
                id = WORKPLACE_RULE_ID,
                timelineId = TIMELINE_ID,
                sector = SECTOR,
                workPlaceId = WORK_PLACE_ID,
                objectiveId = OBJECTIVE_ID,
                effectiveFrom = MONTH.atDay(1),
                rules = WorkplaceRules(
                    nightHours = NightHoursRule.Disabled,
                    weekend = WeekendRule.None,
                    holiday = HolidayRule(false, false),
                ),
                createdAt = CREATED_AT,
            ),
        ),
    )

    val shifts: List<V2ShiftWrite> = listOf(dayShift, nightShift)

    val summaryInput: MonthlySummaryInput = MonthlySummaryInput(
        month = MONTH,
        configuration = configuration,
        shifts = shifts,
        actuals = listOf(dayActual),
        independentExtras = listOf(independentExtra),
        availabilityWindows = listOf(availability),
        catalogs = listOf(catalog),
        holidays = emptyList(),
        medicalLeaves = listOf(medicalLeave),
        vacations = emptyList(),
        explicitDayStatuses = emptyList(),
    )

    /** Captura equivalente a la que haría Room para la selección de privacidad dada (sin fotos). */
    fun reportSnapshot(privacy: ReportPrivacySelection): MonthlyReportSourceSnapshot {
        require(privacy.selectedPhotoIds.isEmpty()) { "La fixture 12.2 no incluye fotos" }
        val request = MonthlyReportSnapshotRequest(
            month = MONTH,
            includeShiftNotes = privacy.includeShiftNotes,
            includeMedicalNotes = privacy.includeMedicalNotes,
            selectedPhotoIds = emptySet(),
        )
        val leaves = if (privacy.includeMedicalNotes) {
            listOf(medicalLeave)
        } else {
            listOf(medicalLeave.copy(privateNote = null))
        }
        return MonthlyReportSourceSnapshot(
            request = request,
            captureRange = resolveReportCaptureRange(MONTH, configuration),
            summaryInput = summaryInput.copy(medicalLeaves = leaves),
            shiftNotes = if (privacy.includeShiftNotes) listOf(shiftNote) else emptyList(),
            selectedPhotos = emptyList(),
        )
    }

    fun local(date: LocalDate, time: LocalTime): Instant = ZonedDateTime.of(date, time, ZONE).toInstant()

    private fun shiftWrite(
        id: UUID,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        position: String?,
        templateId: UUID,
    ): V2ShiftWrite {
        val endDate = if (endTime <= startTime) date.plusDays(1) else date
        return V2ShiftWrite(
            shift = Shift(
                id = id,
                startAt = local(date, startTime),
                endAt = local(endDate, endTime),
                zoneId = ZONE,
                localStartDate = date,
                objectiveNameSnapshot = PLACE_NAME,
                objectiveAbbreviationSnapshot = PLACE_ABBREVIATION,
                objectiveAddressSnapshot = Canary.ADDRESS,
                startTimeSnapshot = startTime,
                endTimeSnapshot = endTime,
                colorArgbSnapshot = COLOR,
                position = position,
                status = ShiftStatus.PLANNED,
                sourceObjectiveId = OBJECTIVE_ID,
                createdAt = CREATED_AT,
                updatedAt = CREATED_AT,
            ),
            snapshot = ShiftWorkSnapshot(
                shiftId = id,
                timelineId = TIMELINE_ID,
                sector = SECTOR,
                configurationRevisionId = CONFIGURATION_REVISION_ID,
                workPlaceId = WORK_PLACE_ID,
                objectiveId = OBJECTIVE_ID,
                templateId = templateId,
                workTypeId = WORK_TYPE_ID,
                workTypeNameSnapshot = WORK_TYPE_NAME,
                workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
            ),
        )
    }

    private fun uuid(number: Int): UUID = UUID.fromString(
        "12200000-0000-0000-0000-${number.toString().padStart(12, '0')}",
    )
}
