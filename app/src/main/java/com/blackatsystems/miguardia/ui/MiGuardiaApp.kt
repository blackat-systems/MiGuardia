package com.blackatsystems.miguardia.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import com.blackatsystems.miguardia.core.domain.calendar.CalendarShift
import com.blackatsystems.miguardia.core.domain.calendar.ShiftTemporalStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.management.ManagementActions
import com.blackatsystems.miguardia.ui.management.ManagementSurface
import com.blackatsystems.miguardia.ui.management.ManagementSurfaceHost
import com.blackatsystems.miguardia.ui.management.ManagementUiState
import com.blackatsystems.miguardia.ui.management.ManagementViewModel
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsActions
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsSurface
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsSurfaceHost
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsUiState
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsViewModel
import com.blackatsystems.miguardia.ui.summary.SummaryScreen
import com.blackatsystems.miguardia.ui.summary.SummaryUiState
import com.blackatsystems.miguardia.ui.summary.SummaryViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val SpanishArgentina = Locale.forLanguageTag("es-AR")
private val FullDateFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", SpanishArgentina)
private val ShiftTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", SpanishArgentina)

private enum class MainDestination(
    @param:StringRes val labelRes: Int,
    val glyph: String,
) {
    CALENDAR(R.string.calendar, "C"),
    SUMMARY(R.string.summary, "R"),
    SETTINGS(R.string.settings, "A"),
}

@Composable
fun MiGuardiaApp(
    calendarViewModel: CalendarViewModel,
    managementViewModel: ManagementViewModel,
    summaryViewModel: SummaryViewModel,
    exceptionsViewModel: ExceptionsViewModel,
    modifier: Modifier = Modifier,
) {
    val calendarState by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val managementState by managementViewModel.uiState.collectAsStateWithLifecycle()
    val summaryState by summaryViewModel.uiState.collectAsStateWithLifecycle()
    val exceptionsState by exceptionsViewModel.uiState.collectAsStateWithLifecycle()
    MiGuardiaApp(
        calendarState = calendarState,
        onPreviousMonth = calendarViewModel::showPreviousMonth,
        onNextMonth = calendarViewModel::showNextMonth,
        onToday = calendarViewModel::showCurrentMonth,
        onSelectDate = calendarViewModel::selectDate,
        onDismissDate = calendarViewModel::clearSelectedDate,
        onRetry = calendarViewModel::retry,
        summaryState = summaryState,
        onSummaryPreviousMonth = summaryViewModel::showPreviousMonth,
        onSummaryNextMonth = summaryViewModel::showNextMonth,
        onSummaryToday = summaryViewModel::showCurrentMonth,
        onSummaryRetry = summaryViewModel::retry,
        managementState = managementState,
        managementActions = ManagementActions.from(managementViewModel),
        exceptionsState = exceptionsState,
        exceptionsActions = ExceptionsActions.from(exceptionsViewModel),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiGuardiaApp(
    calendarState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onDismissDate: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    managementState: ManagementUiState = ManagementUiState(),
    managementActions: ManagementActions = ManagementActions(),
    summaryState: SummaryUiState = SummaryUiState(
        visibleMonth = calendarState.visibleMonth,
        referenceInstant = calendarState.referenceInstant,
    ),
    onSummaryPreviousMonth: () -> Unit = {},
    onSummaryNextMonth: () -> Unit = {},
    onSummaryToday: () -> Unit = {},
    onSummaryRetry: () -> Unit = {},
    exceptionsState: ExceptionsUiState = ExceptionsUiState(holidayMonth = calendarState.visibleMonth),
    exceptionsActions: ExceptionsActions = ExceptionsActions(),
) {
    var destination by rememberSaveable { androidx.compose.runtime.mutableStateOf(MainDestination.CALENDAR) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        bottomBar = {
            NavigationBar {
                MainDestination.entries.forEach { item ->
                    val label = stringResource(item.labelRes)
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = {
                            Text(
                                text = item.glyph,
                                modifier = Modifier.clearAndSetSemantics {},
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (destination) {
            MainDestination.CALENDAR -> CalendarScreen(
                state = calendarState,
                contentPadding = innerPadding,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onToday = onToday,
                onSelectDate = onSelectDate,
                onRetry = onRetry,
                onAddShift = { managementActions.openAddShift(calendarState.visibleMonth, null) },
            )

            MainDestination.SUMMARY -> SummaryScreen(
                state = summaryState,
                contentPadding = innerPadding,
                onPreviousMonth = onSummaryPreviousMonth,
                onNextMonth = onSummaryNextMonth,
                onToday = onSummaryToday,
                onRetry = onSummaryRetry,
            )

            MainDestination.SETTINGS -> SettingsScreen(
                contentPadding = innerPadding,
                onOpenObjectives = managementActions.openSettings,
                onOpenHolidays = { exceptionsActions.openHolidays(calendarState.visibleMonth) },
            )
        }
    }

    val selectedDay = calendarState.selectedDate?.let { selectedDate ->
        calendarState.days.firstOrNull { it.date == selectedDate }
    }
    if (selectedDay != null) {
        ModalBottomSheet(onDismissRequest = onDismissDate) {
            DayDetailSheet(
                day = selectedDay,
                onAddShift = {
                    onDismissDate()
                    managementActions.openAddShift(calendarState.visibleMonth, selectedDay.date)
                },
                onEditShift = {
                    onDismissDate()
                    managementActions.openEditShift(it)
                },
                onDuplicateShift = {
                    onDismissDate()
                    managementActions.openDuplicateShift(it)
                },
                onDeleteShift = managementActions.deleteShift,
                onOpenExceptions = {
                    onDismissDate()
                    exceptionsActions.openShift(it)
                },
            )
        }
    }

    if (managementState.surface != ManagementSurface.NONE) {
        ManagementSurfaceHost(
            state = managementState,
            actions = managementActions,
        )
    }
    if (exceptionsState.surface != ExceptionsSurface.NONE) {
        ExceptionsSurfaceHost(exceptionsState, exceptionsActions)
    }
}

@Composable
private fun CalendarScreen(
    state: CalendarUiState,
    contentPadding: PaddingValues,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onAddShift: () -> Unit,
) {
    val today = state.referenceInstant.atZone(AppDefaults.zoneId()).toLocalDate()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NextGuardCard()
        MonthControls(
            visibleMonth = state.visibleMonth,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
            onToday = onToday,
        )

        when (state.loadState) {
            CalendarLoadState.LOADING -> LoadingCalendar()
            CalendarLoadState.ERROR -> ErrorCalendar(
                message = state.errorMessage ?: stringResource(R.string.calendar_error),
                onRetry = onRetry,
            )
            CalendarLoadState.CONTENT -> Unit
        }

        if (state.days.isNotEmpty()) {
            WeekdayHeader()
            MonthGrid(
                month = state.visibleMonth,
                days = state.days,
                today = today,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onSelectDate = onSelectDate,
            )
        }

        Button(
            onClick = onAddShift,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add))
        }
    }
}

@Composable
private fun NextGuardCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.next_guard),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.next_guard_pending),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LoadingCalendar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.width(28.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(stringResource(R.string.calendar_loading))
    }
}

@Composable
private fun ErrorCalendar(message: String, onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun MonthControls(
    visibleMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val previousDescription = stringResource(R.string.previous_month)
    val nextDescription = stringResource(R.string.next_month)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.semantics { contentDescription = previousDescription },
        ) {
            Text(
                text = "‹",
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = visibleMonth.displayName(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onToday) {
                Text(stringResource(R.string.today))
            }
        }
        IconButton(
            onClick = onNext,
            modifier = Modifier.semantics { contentDescription = nextDescription },
        ) {
            Text(
                text = "›",
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val labels = listOf(
        R.string.monday_short to R.string.monday,
        R.string.tuesday_short to R.string.tuesday,
        R.string.wednesday_short to R.string.wednesday,
        R.string.thursday_short to R.string.thursday,
        R.string.friday_short to R.string.friday,
        R.string.saturday_short to R.string.saturday,
        R.string.sunday_short to R.string.sunday,
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach { (shortLabel, fullLabel) ->
            val fullDayName = stringResource(fullLabel)
            Text(
                text = stringResource(shortLabel),
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = fullDayName },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    days: List<CalendarDay>,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val dayByDate = remember(days) { days.associateBy { it.date } }
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cells = List<CalendarDay?>(42) { index ->
        val dayNumber = index - offset + 1
        dayNumber.takeIf { it in 1..month.lengthOfMonth() }
            ?.let { dayByDate[month.atDay(it)] }
    }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("month-grid")
            .pointerInput(month) {
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        horizontalDrag += dragAmount
                        change.consume()
                    },
                    onDragCancel = { horizontalDrag = 0f },
                    onDragEnd = {
                        val threshold = 64.dp.toPx()
                        when {
                            horizontalDrag <= -threshold -> onNextMonth()
                            horizontalDrag >= threshold -> onPreviousMonth()
                        }
                        horizontalDrag = 0f
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 88.dp),
                        )
                    } else {
                        DayCell(
                            day = day,
                            isToday = day.date == today,
                            onClick = { onSelectDate(day.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = day.accessibilityDescription(isToday)
    val background = if (isToday) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Column(
        modifier = modifier
            .padding(2.dp)
            .heightIn(min = 88.dp)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(action = {
                    onClick()
                    true
                })
            }
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
        )
        val firstShift = day.shifts.firstOrNull()
        if (firstShift != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(Color(firstShift.shift.colorArgbSnapshot)),
            )
            Text(
                text = "${firstShift.shift.objectiveAbbreviationSnapshot} · ${firstShift.temporalStatus.shortLabel()}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = firstShift.shift.timeRange(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
            )
            if (day.shifts.size > 1) {
                Text(
                    text = "+${day.shifts.size - 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        val markers = buildList {
            when (day.explicitStatus) {
                ExplicitDayStatusType.DAY_OFF -> add("F")
                ExplicitDayStatusType.UNDEFINED -> add("?")
                null -> Unit
            }
            if (day.hasMedicalLeave) add("CM")
            if (day.holiday != null) add("Fer.")
            if (day.isImplicitlyUndefined) add("?")
        }
        if (markers.isNotEmpty()) {
            Text(
                text = markers.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DayDetailSheet(
    day: CalendarDay,
    onAddShift: () -> Unit,
    onEditShift: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onDuplicateShift: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onDeleteShift: (java.util.UUID) -> Unit,
    onOpenExceptions: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
) {
    var pendingDeleteId by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = day.date.fullDisplayName(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (day.shifts.isEmpty() && day.explicitStatus == null && !day.hasMedicalLeave) {
            Text(stringResource(R.string.undefined_implicit_detail))
        }
        day.shifts.forEachIndexed { index, calendarShift ->
            if (index > 0) HorizontalDivider()
            ShiftDetail(
                calendarShift = calendarShift,
                onEdit = onEditShift,
                onDuplicate = onDuplicateShift,
                onDelete = { pendingDeleteId = it.toString() },
                onOpenExceptions = onOpenExceptions,
            )
        }
        when (day.explicitStatus) {
            ExplicitDayStatusType.DAY_OFF -> Text(stringResource(R.string.day_off_explicit_detail))
            ExplicitDayStatusType.UNDEFINED -> Text(stringResource(R.string.undefined_explicit_detail))
            null -> Unit
        }
        if (day.hasMedicalLeave) {
            Text(stringResource(R.string.medical_leave_detail))
        }
        day.holiday?.let { holiday ->
            Text("Feriado: ${holiday.name ?: "sin nombre"}", fontWeight = FontWeight.SemiBold)
        }
        Button(onClick = onAddShift, modifier = Modifier.fillMaxWidth()) {
            Text("Agregar guardia")
        }
    }
    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Eliminar guardia") },
            text = { Text("Se eliminará solamente esta guardia. ¿Querés continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteShift(java.util.UUID.fromString(id))
                    pendingDeleteId = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun ShiftDetail(
    calendarShift: CalendarShift,
    onEdit: ((com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit)? = null,
    onDuplicate: ((com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit)? = null,
    onDelete: ((java.util.UUID) -> Unit)? = null,
    onOpenExceptions: ((com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit)? = null,
) {
    val shift = calendarShift.shift
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(48.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(Color(shift.colorArgbSnapshot)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = "${shift.objectiveNameSnapshot} (${shift.objectiveAbbreviationSnapshot})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(shift.timeRange())
            Text(calendarShift.temporalStatus.displayLabel(), fontWeight = FontWeight.Medium)
            shift.position?.takeIf { it.isNotBlank() }?.let { position ->
                Text(stringResource(R.string.position_value, position))
            }
            shift.objectiveAddressSnapshot?.takeIf { it.isNotBlank() }?.let { address ->
                Text(address)
            }
            if (onEdit != null && onDuplicate != null && onDelete != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onEdit(shift) }) { Text("Editar") }
                    TextButton(onClick = { onDuplicate(shift) }) { Text("Duplicar") }
                    TextButton(onClick = { onDelete(shift.id) }) { Text("Eliminar") }
                }
            }
            if (onOpenExceptions != null) {
                Button(onClick = { onOpenExceptions(shift) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Informar novedad / notas")
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenObjectives: () -> Unit,
    onOpenHolidays: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Configuración", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.settings_intro))
        Button(onClick = onOpenObjectives, modifier = Modifier.fillMaxWidth()) {
            Text("Objetivos y horarios")
        }
        Button(onClick = onOpenHolidays, modifier = Modifier.fillMaxWidth()) {
            Text("Feriados")
        }
    }
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

private fun ShiftTemporalStatus.shortLabel(): String = when (this) {
    ShiftTemporalStatus.UPCOMING -> "Próx."
    ShiftTemporalStatus.IN_PROGRESS -> "Ahora"
    ShiftTemporalStatus.COMPLETED -> "Hecha"
    ShiftTemporalStatus.CANCELLED -> "Cancel."
    ShiftTemporalStatus.ABSENT -> "Aus."
}

private fun CalendarDay.accessibilityDescription(isToday: Boolean): String {
    val parts = mutableListOf(date.fullDisplayName())
    if (isToday) parts += "hoy"
    shifts.forEach { calendarShift ->
        parts += buildString {
            append("guardia ")
            append(calendarShift.shift.objectiveAbbreviationSnapshot)
            append(" de ")
            append(calendarShift.shift.startTimeSnapshot.format(ShiftTimeFormatter))
            append(" a ")
            append(calendarShift.shift.endTimeSnapshot.format(ShiftTimeFormatter))
            append(", ")
            append(calendarShift.temporalStatus.accessibilityLabel())
        }
    }
    when (explicitStatus) {
        ExplicitDayStatusType.DAY_OFF -> parts += "franco marcado explícitamente"
        ExplicitDayStatusType.UNDEFINED -> parts += "día sin definir marcado explícitamente"
        null -> Unit
    }
    if (hasMedicalLeave) parts += "carpeta médica"
    holiday?.let { parts += "feriado ${it.name ?: "sin nombre"}" }
    if (isImplicitlyUndefined) parts += "sin definir"
    return parts.joinToString(", ")
}

private fun ShiftTemporalStatus.accessibilityLabel(): String = when (this) {
    ShiftTemporalStatus.UPCOMING -> "próxima"
    ShiftTemporalStatus.IN_PROGRESS -> "en curso"
    ShiftTemporalStatus.COMPLETED -> "completada"
    ShiftTemporalStatus.CANCELLED -> "cancelada"
    ShiftTemporalStatus.ABSENT -> "ausencia"
}

private fun com.blackatsystems.miguardia.core.domain.model.Shift.timeRange(): String =
    "${startTimeSnapshot.format(ShiftTimeFormatter)}–${endTimeSnapshot.format(ShiftTimeFormatter)}"

private fun LocalDate.fullDisplayName(): String = format(FullDateFormatter)
    .replaceFirstChar { it.titlecase(SpanishArgentina) }

private fun YearMonth.displayName(): String {
    val monthName = month.getDisplayName(TextStyle.FULL, SpanishArgentina)
        .replaceFirstChar { it.titlecase(SpanishArgentina) }
    return "$monthName de $year"
}
