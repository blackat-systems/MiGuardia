package com.blackatsystems.miguardia.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import com.blackatsystems.miguardia.ui.components.StatusBadge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import com.blackatsystems.miguardia.core.domain.calendar.CalendarShift
import com.blackatsystems.miguardia.core.domain.calendar.ShiftTemporalStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.CancellationActor
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftSituationExpectation
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import com.blackatsystems.miguardia.core.domain.weather.roundedTemperature
import com.blackatsystems.miguardia.core.domain.weather.spanishLabel
import com.blackatsystems.miguardia.ui.availability.AvailabilityActions
import com.blackatsystems.miguardia.ui.availability.AvailabilityDaySection
import com.blackatsystems.miguardia.ui.availability.AvailabilityUiState
import com.blackatsystems.miguardia.ui.hours.IndependentExtraDetailCard
import com.blackatsystems.miguardia.ui.management.V2DayEditEntry
import com.blackatsystems.miguardia.ui.management.V2ShiftEditUiState
import com.blackatsystems.miguardia.ui.management.V2ShiftActualActions
import com.blackatsystems.miguardia.ui.management.V2ShiftActualDetailCard
import com.blackatsystems.miguardia.ui.management.V2ShiftActualUiState
import com.blackatsystems.miguardia.ui.weather.ShiftWeatherBrief
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import com.blackatsystems.miguardia.weather.WeatherCredits
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class Stage2DayActions(
    val clearMessage: () -> Unit = {},
    val toggleUndefined: (LocalDate, ExplicitDayStatusType?) -> Unit = { _, _ -> },
    val saveMedicalLeave: (MedicalLeave?, LocalDate, LocalDate, String?, (Boolean) -> Unit) -> Unit =
        { _, _, _, _, completed -> completed(false) },
    val deleteMedicalLeave: (MedicalLeave) -> Unit = {},
    val changeSituation: (
        ShiftSituationExpectation,
        ShiftStatus,
        String?,
        String?,
        CancellationActor?,
        Instant?,
        Boolean,
        Uri?,
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
)

@Composable
internal fun DayDetailSheet(
    day: CalendarDay,
    referenceInstant: java.time.Instant,
    onEditDay: (() -> Unit)?,
    onOpenNotes: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onOpenNotifications: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onOpenWeather: (java.util.UUID) -> Unit,
    weatherState: WeatherUiState,
    v2ShiftEditState: V2ShiftEditUiState? = null,
    onRetryV2Inspection: () -> Unit = {},
    v2ShiftActualState: V2ShiftActualUiState = V2ShiftActualUiState(),
    v2ShiftActualActions: V2ShiftActualActions = V2ShiftActualActions(),
    independentExtras: List<IndependentExtraWorkRecord> = emptyList(),
    canRegisterIndependentExtra: Boolean = false,
    onRegisterIndependentExtra: () -> Unit = {},
    onCorrectIndependentExtra: (IndependentExtraWorkRecord) -> Unit = {},
    onDeleteIndependentExtra: (IndependentExtraWorkRecord) -> Unit = {},
    availabilityState: AvailabilityUiState = AvailabilityUiState(),
    availabilityActions: AvailabilityActions = AvailabilityActions(),
    onManageDayOff: (() -> Unit)? = null,
    stage2Actions: Stage2DayActions = Stage2DayActions(),
    stage2Busy: Boolean = false,
    stage2Expectations: Map<java.util.UUID, ShiftSituationExpectation> = emptyMap(),
) {
    var editingDay by remember(day.date) { mutableStateOf(false) }
    var situationTarget by remember { mutableStateOf<Pair<Shift, ShiftStatus>?>(null) }
    var medicalTarget by remember { mutableStateOf<MedicalLeave?>(null) }
    var showMedicalEditor by remember { mutableStateOf(false) }
    var medicalSaveRequest by remember { mutableStateOf<Any?>(null) }
    var medicalSaveFailed by remember { mutableStateOf(false) }
    var pendingMedicalDelete by remember { mutableStateOf<MedicalLeave?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .testTag("day-detail-scroll")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (editingDay) {
            Text(
                text = day.date.dayEditorDisplayName(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            DayEditingMenu(
                day = day,
                v2ShiftEditState = v2ShiftEditState,
                onEditDay = onEditDay,
                onRetryV2Inspection = onRetryV2Inspection,
                onManageDayOff = onManageDayOff,
                stage2Busy = stage2Busy,
                stage2Expectations = stage2Expectations,
                onSelectSituation = { shift, target -> situationTarget = shift to target },
                onToggleUndefined = { stage2Actions.toggleUndefined(day.date, day.explicitStatus) },
                onAddMedicalLeave = { medicalTarget = null; medicalSaveFailed = false; showMedicalEditor = true },
                onEditMedicalLeave = { leave -> medicalTarget = leave; medicalSaveFailed = false; showMedicalEditor = true },
                onDeleteMedicalLeave = { pendingMedicalDelete = it },
                independentExtras = independentExtras,
                canRegisterIndependentExtra = canRegisterIndependentExtra,
                onRegisterIndependentExtra = onRegisterIndependentExtra,
                onCorrectIndependentExtra = onCorrectIndependentExtra,
                onDeleteIndependentExtra = onDeleteIndependentExtra,
                availabilityState = availabilityState,
                availabilityActions = availabilityActions,
            )
        } else {
            Text(
                text = day.date.fullDisplayName(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Button(
                onClick = { if (onManageDayOff != null) onManageDayOff() else editingDay = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("day-edit-menu"),
            ) { Text("Editar día") }
            TextButton(onClick = { editingDay = true }, modifier = Modifier.testTag("day-other-options")) {
                Text("Otras opciones del día")
            }
            val orderedShifts = day.shifts.sortedBy { it.shift.startAt }
            if (orderedShifts.isNotEmpty()) {
                Text("Jornadas del día", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            orderedShifts.forEachIndexed { index, calendarShift ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shift-detail-card-${calendarShift.shift.id}"),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ShiftDetail(
                            calendarShift = calendarShift,
                            excludedByVacation = day.vacation != null && calendarShift.shift.status == ShiftStatus.PLANNED,
                            weatherUnavailableReason = weatherUnavailableReason(
                                calendarShift.shift,
                                referenceInstant,
                                day,
                            ),
                            onOpenNotes = onOpenNotes,
                            onOpenNotifications = onOpenNotifications,
                            onOpenWeather = onOpenWeather,
                            weatherEnabled = weatherState.preferences.enabled,
                            weatherUnit = weatherState.preferences.unitSystem,
                            weatherBrief = weatherState.shiftBriefs[calendarShift.shift.id],
                            weatherLoading = calendarShift.shift.id in weatherState.loadingBriefIds,
                        )
                        V2ShiftActualDetailCard(
                            shift = calendarShift.shift,
                            ordinal = index + 1,
                            count = orderedShifts.size,
                            ownerDate = day.date,
                            rowState = v2ShiftActualState.rows[calendarShift.shift.id],
                            actions = v2ShiftActualActions,
                            embedded = true,
                        )
                    }
                }
            }
            DayConditionCard(day, independentExtras, availabilityState)
            independentExtras.forEach { record ->
                IndependentExtraDetailCard(
                    record = record,
                    onCorrect = {},
                    onDelete = {},
                    showActions = false,
                )
            }
        }
    }
    situationTarget?.let { (shift, target) ->
        val expectation = stage2Expectations[shift.id]
        if (expectation != null) {
            ShiftSituationDialog(
                shift = shift,
                target = target,
                expectation = expectation,
                onDismiss = { situationTarget = null },
                onConfirm = { reason, note, actor, notifiedAt, evidenceUri ->
                    stage2Actions.changeSituation(expectation, target, reason, note, actor, notifiedAt, true, evidenceUri)
                    situationTarget = null
                },
            )
        }
    }
    if (showMedicalEditor) {
        DisposableEffect(Unit) {
            onDispose { medicalSaveRequest = null }
        }
        MedicalLeaveDialog(
            ownerDate = day.date,
            previous = medicalTarget,
            saving = medicalSaveRequest != null,
            saveFailed = medicalSaveFailed,
            onDismiss = { if (medicalSaveRequest == null) showMedicalEditor = false },
            onConfirm = { start, end, note ->
                if (medicalSaveRequest == null) {
                    val request = Any()
                    medicalSaveFailed = false
                    medicalSaveRequest = request
                    stage2Actions.saveMedicalLeave(medicalTarget, start, end, note) { saved ->
                        // A completed or abandoned editor cannot dismiss a later opening.
                        if (medicalSaveRequest === request) {
                            medicalSaveRequest = null
                            medicalSaveFailed = !saved
                            if (saved) showMedicalEditor = false
                        }
                    }
                }
            },
        )
    }
    pendingMedicalDelete?.let { leave ->
        AlertDialog(
            onDismissRequest = { pendingMedicalDelete = null },
            title = { Text("Eliminar carpeta médica") },
            text = {
                Text(
                    "Se quitará sólo el rango ${leave.startDate.shortDisplayDate()} – " +
                        "${leave.endDateInclusive.shortDisplayDate()}. Las jornadas se conservarán.",
                )
            },
            dismissButton = { TextButton(onClick = { pendingMedicalDelete = null }) { Text("Cancelar") } },
            confirmButton = { TextButton(onClick = {
                stage2Actions.deleteMedicalLeave(leave)
                pendingMedicalDelete = null
            }) { Text("Eliminar") } },
        )
    }
}

@Composable
private fun DayEditingMenu(
    day: CalendarDay,
    v2ShiftEditState: V2ShiftEditUiState?,
    onEditDay: (() -> Unit)?,
    onRetryV2Inspection: () -> Unit,
    onManageDayOff: (() -> Unit)?,
    stage2Busy: Boolean,
    stage2Expectations: Map<java.util.UUID, ShiftSituationExpectation>,
    onSelectSituation: (Shift, ShiftStatus) -> Unit,
    onToggleUndefined: () -> Unit,
    onAddMedicalLeave: () -> Unit,
    onEditMedicalLeave: (MedicalLeave) -> Unit,
    onDeleteMedicalLeave: (MedicalLeave) -> Unit,
    independentExtras: List<IndependentExtraWorkRecord>,
    canRegisterIndependentExtra: Boolean,
    onRegisterIndependentExtra: () -> Unit,
    onCorrectIndependentExtra: (IndependentExtraWorkRecord) -> Unit,
    onDeleteIndependentExtra: (IndependentExtraWorkRecord) -> Unit,
    availabilityState: AvailabilityUiState,
    availabilityActions: AvailabilityActions,
) {
    if (day.shifts.isEmpty() && onManageDayOff != null) {
        OutlinedButton(
            onClick = onManageDayOff,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("day-detail-manage-day-off"),
        ) { Text("Agregar jornada o franco") }
    } else if (v2ShiftEditState != null && onEditDay != null) {
        V2DayEditEntry(
            state = v2ShiftEditState,
            date = day.date,
            onBegin = onEditDay,
            onRetry = onRetryV2Inspection,
            label = "Agregar jornada o franco",
        )
    } else if (onManageDayOff != null) {
        OutlinedButton(
            onClick = onManageDayOff,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("day-detail-manage-day-off"),
        ) { Text("Agregar jornada o franco") }
    }
    OutlinedButton(
        onClick = { availabilityActions.openRetainer(day.date) },
        enabled = !stage2Busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("day-add-retainer"),
    ) { Text("Agregar disponibilidad de retén") }
    OutlinedButton(
        onClick = onAddMedicalLeave,
        enabled = !stage2Busy && day.vacation == null,
        modifier = Modifier.fillMaxWidth().testTag("day-add-medical-leave"),
    ) { Text("Agregar carpeta médica") }
    if (day.explicitStatus == ExplicitDayStatusType.UNDEFINED) {
        OutlinedButton(
            onClick = onToggleUndefined,
            enabled = !stage2Busy,
            modifier = Modifier.fillMaxWidth().testTag("day-toggle-undefined"),
        ) { Text("Quitar marca Sin definir") }
    }
    day.shifts.forEach { calendarShift ->
        val shift = calendarShift.shift
        if (stage2Expectations[shift.id] == null) return@forEach
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Situación de ${shift.objectiveNameSnapshot} (${shift.objectiveAbbreviationSnapshot})",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when (shift.status) {
                        ShiftStatus.PLANNED -> "Estado actual: Planificada"
                        ShiftStatus.ABSENT -> "Estado actual: Ausencia"
                        ShiftStatus.CANCELLED -> "Estado actual: Cancelada"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shift.status != ShiftStatus.ABSENT) {
                        OutlinedButton(
                            onClick = { onSelectSituation(shift, ShiftStatus.ABSENT) },
                            enabled = !stage2Busy,
                            modifier = Modifier.weight(1f).testTag("shift-absence-${shift.id}"),
                        ) { Text("Ausencia") }
                    }
                    if (shift.status != ShiftStatus.CANCELLED) {
                        OutlinedButton(
                            onClick = { onSelectSituation(shift, ShiftStatus.CANCELLED) },
                            enabled = !stage2Busy,
                            modifier = Modifier.weight(1f).testTag("shift-cancel-${shift.id}"),
                        ) { Text("Cancelación") }
                    }
                }
                if (shift.status != ShiftStatus.PLANNED) {
                    OutlinedButton(
                        onClick = { onSelectSituation(shift, shift.status) },
                        enabled = !stage2Busy,
                        modifier = Modifier.fillMaxWidth().testTag("shift-edit-situation-${shift.id}"),
                    ) { Text(if (shift.status == ShiftStatus.ABSENT) "Editar ausencia" else "Editar cancelación") }
                    OutlinedButton(
                        onClick = { onSelectSituation(shift, ShiftStatus.PLANNED) },
                        enabled = !stage2Busy,
                        modifier = Modifier.fillMaxWidth().testTag("shift-restore-${shift.id}"),
                    ) { Text("Volver a Planificada") }
                }
            }
        }
    }
    day.medicalLeaves.forEach { leave ->
        Card(modifier = Modifier.fillMaxWidth().testTag("medical-leave-card-${leave.id}")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Carpeta médica", fontWeight = FontWeight.SemiBold)
                Text("${leave.startDate.shortDisplayDate()} – ${leave.endDateInclusive.shortDisplayDate()}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onEditMedicalLeave(leave) }, modifier = Modifier.weight(1f)) {
                        Text("Editar")
                    }
                    TextButton(onClick = { onDeleteMedicalLeave(leave) }, modifier = Modifier.weight(1f)) {
                        Text("Eliminar")
                    }
                }
            }
        }
    }
    independentExtras.forEach { record ->
        IndependentExtraDetailCard(
            record = record,
            onCorrect = { onCorrectIndependentExtra(record) },
            onDelete = { onDeleteIndependentExtra(record) },
        )
    }
    if (canRegisterIndependentExtra) {
        OutlinedButton(
            onClick = onRegisterIndependentExtra,
            modifier = Modifier.fillMaxWidth().testTag("register-independent-extra-${day.date}"),
        ) { Text("Registrar trabajo extra") }
    }
    if (availabilityState.windowsOn(day.date).isNotEmpty()) {
        AvailabilityDaySection(
            date = day.date,
            state = availabilityState,
            actions = availabilityActions,
            showActions = true,
        )
    }
}

@Composable
private fun DayConditionCard(
    day: CalendarDay,
    independentExtras: List<IndependentExtraWorkRecord>,
    availabilityState: AvailabilityUiState,
) {
    val availability = availabilityState.windowsOn(day.date)
    val hasCondition = day.explicitStatus != null ||
        day.medicalLeaves.isNotEmpty() ||
        day.vacation != null ||
        day.holiday != null ||
        independentExtras.isNotEmpty() ||
        availability.isNotEmpty()
    if (!hasCondition) return
    Card(modifier = Modifier.fillMaxWidth().testTag("day-condition-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Condición del día", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (day.explicitStatus) {
                ExplicitDayStatusType.DAY_OFF -> Text("Franco")
                ExplicitDayStatusType.UNDEFINED -> Text("Sin definir")
                null -> Unit
            }
            availability.forEach { record ->
                Text(
                    AnnotatedString("${record.labelSnapshot} · " + formatEventTimeRange(
                        record.start, record.end, record.zoneId, EventTimeRangeStyle.DETAIL,
                    )),
                    modifier = Modifier.testTag("availability-readonly-${record.id}"),
                )
            }
            day.medicalLeaves.forEach { leave ->
                Text(
                    "Carpeta médica · ${leave.startDate.shortDisplayDate()} – " +
                        leave.endDateInclusive.shortDisplayDate(),
                )
            }
            day.vacation?.let { vacation ->
                Text(
                    "Vacaciones · ${vacation.startDate.shortDisplayDate()} – " +
                        vacation.endDateInclusive.shortDisplayDate(),
                )
            }
            day.holiday?.let { holiday -> Text("Feriado · ${holiday.name ?: "Sin nombre"}") }
            independentExtras.forEach { record -> Text("Trabajo extra · ${record.snapshot.className}") }
        }
    }
}

@Composable
private fun ShiftSituationDialog(
    shift: Shift,
    target: ShiftStatus,
    expectation: ShiftSituationExpectation,
    onDismiss: () -> Unit,
    onConfirm: (String?, String?, CancellationActor?, Instant?, Uri?) -> Unit,
) {
    val previous = expectation.previous
    var reason by remember(shift.id, target) { mutableStateOf(previous?.reason.orEmpty()) }
    var note by remember(shift.id, target) { mutableStateOf(previous?.privateNote.orEmpty()) }
    var actor by remember(shift.id, target) {
        mutableStateOf(previous?.cancellationActor ?: CancellationActor.NOT_REPORTED)
    }
    var noticeText by remember(shift.id, target) {
        mutableStateOf(
            (previous?.notifiedAt ?: Instant.now()).atZone(com.blackatsystems.miguardia.core.domain.AppDefaults.zoneId())
                .toLocalDateTime().withSecond(0).withNano(0).format(DAY_DATE_TIME_FORMATTER),
        )
    }
    var evidenceUri by remember(shift.id, target) { mutableStateOf<Uri?>(null) }
    val evidencePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> if (uri != null) evidenceUri = uri }
    val parsedNotice = runCatching {
        java.time.LocalDateTime.parse(noticeText, DAY_DATE_TIME_FORMATTER)
            .atZone(com.blackatsystems.miguardia.core.domain.AppDefaults.zoneId()).toInstant()
    }.getOrNull()
    val restoring = target == ShiftStatus.PLANNED
    AlertDialog(
        modifier = Modifier.testTag("shift-situation-dialog"),
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    restoring -> "Volver a Planificada"
                    target == shift.status && target == ShiftStatus.ABSENT -> "Editar ausencia"
                    target == shift.status && target == ShiftStatus.CANCELLED -> "Editar cancelación"
                    target == ShiftStatus.ABSENT -> "Registrar ausencia"
                    else -> "Registrar cancelación"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${shift.objectiveAbbreviationSnapshot} · " +
                        formatEventTimeRange(shift.startAt, shift.endAt, shift.zoneId, EventTimeRangeStyle.DETAIL),
                )
                if (restoring) {
                    val removed = buildList {
                        if (!previous?.reason.isNullOrBlank()) add("motivo")
                        if (!previous?.privateNote.isNullOrBlank()) add("nota privada")
                        if (previous?.cancellationActor != null) add("quién canceló")
                        if (previous?.notifiedAt != null) add("fecha y hora del aviso")
                        if (previous?.evidence != null) add("evidencia privada")
                    }
                    Text(if (removed.isEmpty()) "No hay metadatos adicionales guardados." else "Se retirarán: ${removed.joinToString()}.")
                    Text("La jornada y su plan original se conservan.")
                } else {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(if (target == ShiftStatus.CANCELLED) "Motivo (obligatorio)" else "Motivo (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota privada (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (target == ShiftStatus.CANCELLED) {
                        Text("Quién canceló: ${actor.displayName()}")
                        TextButton(onClick = { actor = actor.next() }) { Text("Cambiar") }
                        OutlinedTextField(
                            value = noticeText,
                            onValueChange = { noticeText = it },
                            label = { Text("Fecha y hora del aviso") },
                            supportingText = { Text("Formato: DD/MM/AAAA HH:MM") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (previous?.evidence != null) {
                            Text("Hay una evidencia privada guardada. Sólo se reemplaza si elegís otra imagen.")
                        }
                        OutlinedButton(onClick = {
                            evidencePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        }) {
                            Text(
                                when {
                                    evidenceUri != null -> "Reemplazar evidencia elegida"
                                    previous?.evidence != null -> "Reemplazar evidencia guardada"
                                    else -> "Elegir evidencia opcional"
                                },
                            )
                        }
                        if (evidenceUri != null) Text("Imagen elegida. Se copiará al almacenamiento privado al confirmar.")
                    }
                    Text("La jornada se conserva.")
                    val actual = expectation.actual?.previousActual
                    if (actual != null) {
                        Text("Esta jornada tiene horario real${if (actual.extraIntervals.isNotEmpty()) " y extras" else ""}. Esta confirmación autoriza retirarlos dentro del mismo cambio.")
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason, note, actor.takeIf { target == ShiftStatus.CANCELLED }, parsedNotice, evidenceUri) },
                enabled = restoring || (
                    (target != ShiftStatus.CANCELLED || reason.isNotBlank()) &&
                        (target != ShiftStatus.CANCELLED || parsedNotice != null)
                    ),
            ) { Text("Confirmar") }
        },
    )
}

@Composable
private fun MedicalLeaveDialog(
    ownerDate: LocalDate,
    previous: MedicalLeave?,
    saving: Boolean,
    saveFailed: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate, String?) -> Unit,
) {
    var startText by remember(previous?.id, ownerDate) {
        mutableStateOf((previous?.startDate ?: ownerDate).shortDisplayDate())
    }
    var endText by remember(previous?.id, ownerDate) {
        mutableStateOf((previous?.endDateInclusive ?: ownerDate).shortDisplayDate())
    }
    var note by remember(previous?.id) { mutableStateOf(previous?.privateNote.orEmpty()) }
    val start = runCatching { LocalDate.parse(startText, DAY_DATE_FORMATTER) }.getOrNull()
    val end = runCatching { LocalDate.parse(endText, DAY_DATE_FORMATTER) }.getOrNull()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.testTag("medical-leave-dialog"),
        title = { Text(if (previous == null) "Agregar carpeta médica" else "Editar carpeta médica") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Registrá un rango inclusivo. Informa días, no horas, y nunca guarda fotos de certificados.")
                OutlinedTextField(startText, { startText = it }, label = { Text("Desde (DD/MM/AAAA)") },
                    enabled = !saving, modifier = Modifier.testTag("medical-leave-start"))
                OutlinedTextField(endText, { endText = it }, label = { Text("Hasta (DD/MM/AAAA)") },
                    enabled = !saving, modifier = Modifier.testTag("medical-leave-end"))
                OutlinedTextField(note, { note = it }, label = { Text("Nota privada (opcional)") },
                    enabled = !saving, modifier = Modifier.testTag("medical-leave-note"))
                if (saveFailed) {
                    Text("No se pudo guardar. Revisá los datos e intentá nuevamente.",
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("medical-leave-error"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving,
                modifier = Modifier.testTag("medical-leave-cancel")) { Text("Cancelar") }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(requireNotNull(start), requireNotNull(end), note) },
                enabled = !saving && start != null && end != null && !end.isBefore(start),
                modifier = Modifier.testTag("medical-leave-save"),
            ) { Text("Guardar") }
        },
    )
}

private fun CancellationActor.displayName(): String = when (this) {
    CancellationActor.NOT_REPORTED -> "No informado"
    CancellationActor.EMPLOYER -> "Empleador / organización"
    CancellationActor.ME -> "Yo"
    CancellationActor.OTHER -> "Otra persona"
}

private fun CancellationActor.next(): CancellationActor =
    CancellationActor.entries[(ordinal + 1) % CancellationActor.entries.size]

private val DAY_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
private val DAY_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT)

@Composable
private fun ShiftDetail(
    calendarShift: CalendarShift,
    excludedByVacation: Boolean = false,
    onOpenNotes: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onOpenNotifications: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    weatherUnavailableReason: String? = null,
    onOpenWeather: (java.util.UUID) -> Unit,
    weatherEnabled: Boolean = false,
    weatherUnit: WeatherUnitSystem = WeatherUnitSystem.CELSIUS,
    weatherBrief: ShiftWeatherBrief? = null,
    weatherLoading: Boolean = false,
) {
    val shift = calendarShift.shift
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val indicator: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(shift.colorArgbSnapshot))
                    .semantics { contentDescription = "Color de la jornada ${shift.objectiveAbbreviationSnapshot}" },
            )
        }
        val details: @Composable (Modifier) -> Unit = { detailsModifier ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = detailsModifier) {
                Text(
                    text = AnnotatedString("${shift.objectiveNameSnapshot} (${shift.objectiveAbbreviationSnapshot})"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                shift.objectiveAddressSnapshot?.takeIf { it.isNotBlank() }?.let { address ->
                    Text(
                        text = AnnotatedString(address),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { context.openAddressInMaps(address) }
                            .semantics { contentDescription = "Abrir en Google Maps: $address" }
                            .padding(vertical = 12.dp),
                    )
                }
                Text(AnnotatedString(formatEventTimeRange(shift.startAt, shift.endAt, shift.zoneId, EventTimeRangeStyle.DETAIL)))
                val roles = MaterialTheme.vigiliaColors
                val completed = calendarShift.temporalStatus == ShiftTemporalStatus.COMPLETED
                val adverse = calendarShift.temporalStatus == ShiftTemporalStatus.CANCELLED ||
                    calendarShift.temporalStatus == ShiftTemporalStatus.ABSENT
                StatusBadge(
                    label = calendarShift.temporalStatus.displayLabel(),
                    containerColor = when {
                        completed -> roles.completedContainer
                        adverse -> MaterialTheme.colorScheme.errorContainer
                        else -> roles.plannedContainer
                    },
                    contentColor = when {
                        completed -> roles.onCompletedContainer
                        adverse -> MaterialTheme.colorScheme.onErrorContainer
                        else -> roles.onPlannedContainer
                    },
                    symbol = if (completed) "✓" else null,
                )
                shift.position?.takeIf { it.isNotBlank() }?.let { position ->
                    Text(AnnotatedString(stringResource(R.string.position_value, position)))
                }
                CompactShiftAction(
                    symbol = "✎",
                    label = "Notas",
                    description = "Abrir notas de ${shift.objectiveAbbreviationSnapshot}",
                    onClick = { onOpenNotes(shift) },
                    modifier = Modifier.testTag("shift-notes-${shift.id}"),
                )
                CompactShiftAction(
                    symbol = "◷",
                    label = "Avisos de esta jornada",
                    description = "Abrir avisos de ${shift.objectiveAbbreviationSnapshot}",
                    onClick = { onOpenNotifications(shift) },
                    modifier = Modifier.testTag("shift-notifications-${shift.id}"),
                )
                ShiftWeatherBriefCard(
                    enabled = weatherEnabled,
                    loading = weatherLoading,
                    brief = weatherBrief,
                    unit = weatherUnit,
                    unavailableReason = weatherUnavailableReason,
                    onOpen = { onOpenWeather(shift.id) },
                    modifier = Modifier.testTag("shift-weather-${shift.id}"),
                )
                if (excludedByVacation) {
                    Text(
                        "Esta jornada se conserva, pero no computa horas porque su fecha inicial está en vacaciones.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (maxWidth < 240.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                indicator()
                details(Modifier.fillMaxWidth())
            }
        } else {
            Row(verticalAlignment = Alignment.Top) {
                indicator()
                Spacer(modifier = Modifier.width(12.dp))
                details(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CompactShiftAction(
    symbol: String,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(symbol, fontWeight = FontWeight.SemiBold)
            Text(label, fontWeight = FontWeight.Medium)
        }
    }
}

internal fun Context.openAddressInMaps(address: String): Boolean {
    val encoded = Uri.encode(address)
    val candidates = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).setPackage("com.google.android.apps.maps"),
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")),
    )
    candidates.forEach { intent ->
        if (runCatching { startActivity(intent) }.isSuccess) return true
    }
    Toast.makeText(this, "No hay una aplicación disponible para abrir la dirección.", Toast.LENGTH_LONG).show()
    return false
}

@Composable
internal fun ShiftWeatherBriefCard(
    enabled: Boolean,
    loading: Boolean,
    brief: ShiftWeatherBrief?,
    unit: WeatherUnitSystem,
    unavailableReason: String? = null,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().testTag("shift-weather-card"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Clima durante la jornada", fontWeight = FontWeight.SemiBold)
            when {
                !enabled -> Text("Clima está desactivado. Tocá para revisar la explicación, habilitación y ubicación.")
                unavailableReason != null -> Text(unavailableReason)
                brief != null -> {
                    val summary = brief.summary
                    val minimumTemperature = summary.minimumTemperatureCelsius
                    val maximumTemperature = summary.maximumTemperatureCelsius
                    val temperatures = if (minimumTemperature != null && maximumTemperature != null) {
                        val suffix = if (unit == WeatherUnitSystem.CELSIUS) "°C" else "°F"
                        "${roundedTemperature(minimumTemperature, unit)}–" +
                            "${roundedTemperature(maximumTemperature, unit)} $suffix"
                    } else {
                        null
                    }
                    Text(
                        listOfNotNull(summary.condition?.spanishLabel(), temperatures).joinToString(" · ")
                            .ifBlank { "Pronóstico disponible" },
                    )
                    summary.maximumPrecipitationProbabilityPercent?.let { probability ->
                        Text("Probabilidad máxima de lluvia: $probability %")
                    }
                    Text(
                        when (summary.coverage) {
                            WeatherCoverage.COMPLETE -> "Cobertura completa del horario"
                            WeatherCoverage.PARTIAL -> "Cobertura parcial; no se inventan las horas faltantes"
                            WeatherCoverage.NONE -> "Sin cobertura"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        when (brief.freshness) {
                            WeatherFreshness.FRESH -> "Actualizado · tocá para ver el detalle horario"
                            WeatherFreshness.STALE -> "Pronóstico antiguo · tocá para actualizar o ver el detalle"
                            WeatherFreshness.EXPIRED -> "Pronóstico vencido · tocá para intentar actualizar"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (summary.coverage != WeatherCoverage.NONE) {
                        Text(WeatherCredits.SUMMARY, style = MaterialTheme.typography.bodySmall)
                        Text(WeatherCredits.OPEN, style = MaterialTheme.typography.bodySmall)
                    }
                    if (loading) {
                        Text(
                            "Actualizando sin ocultar el último pronóstico…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                loading -> Text("Consultando el clima del objetivo…")
                else -> Text("Todavía no hay un pronóstico utilizable. Tocá para revisar ubicación, consentimiento o cobertura.")
            }
        }
    }
}

private fun weatherUnavailableReason(
    shift: Shift,
    referenceInstant: Instant,
    day: CalendarDay,
): String? = when {
    shift.status != ShiftStatus.PLANNED ->
        "El clima no aplica por la situación laboral de esta jornada. Tocá para ver el motivo."
    day.vacation != null || day.medicalLeaves.isNotEmpty() ->
        "El clima no aplica mientras el día está cubierto por vacaciones o carpeta médica."
    !shift.endAt.isAfter(referenceInstant) ->
        "La jornada quedó fuera de la ventana futura del pronóstico."
    else -> null
}

@Composable
private fun ShiftTemporalStatus.displayLabel(): String = stringResource(
    when (this) {
        ShiftTemporalStatus.UPCOMING -> R.string.shift_upcoming
        ShiftTemporalStatus.IN_PROGRESS -> R.string.shift_in_progress
        ShiftTemporalStatus.COMPLETED -> R.string.shift_completed
        ShiftTemporalStatus.CANCELLED -> R.string.shift_cancelled
        ShiftTemporalStatus.ABSENT -> R.string.shift_absent
    },
)
