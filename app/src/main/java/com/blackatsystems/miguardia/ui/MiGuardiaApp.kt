package com.blackatsystems.miguardia.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import com.blackatsystems.miguardia.core.domain.calendar.CalendarShift
import com.blackatsystems.miguardia.core.domain.calendar.ShiftTemporalStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.components.DestructiveAction
import com.blackatsystems.miguardia.ui.components.NavigationCard
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.management.ManagementActions
import com.blackatsystems.miguardia.ui.management.ShiftEntryMode
import com.blackatsystems.miguardia.ui.management.ManagementSurface
import com.blackatsystems.miguardia.ui.management.ManagementSurfaceHost
import com.blackatsystems.miguardia.ui.management.ManagementUiState
import com.blackatsystems.miguardia.ui.management.ManagementViewModel
import com.blackatsystems.miguardia.ui.nextevent.NextEventCard
import com.blackatsystems.miguardia.ui.nextevent.NextEventUiState
import com.blackatsystems.miguardia.ui.nextevent.NextEventViewModel
import com.blackatsystems.miguardia.ui.notifications.NotificationActions
import com.blackatsystems.miguardia.ui.notifications.NotificationSurface
import com.blackatsystems.miguardia.ui.notifications.NotificationSurfaceHost
import com.blackatsystems.miguardia.ui.notifications.NotificationUiState
import com.blackatsystems.miguardia.ui.notifications.NotificationViewModel
import com.blackatsystems.miguardia.ui.photos.PhotosActions
import com.blackatsystems.miguardia.ui.photos.PhotosSurface
import com.blackatsystems.miguardia.ui.photos.PhotosSurfaceHost
import com.blackatsystems.miguardia.ui.photos.PhotosUiState
import com.blackatsystems.miguardia.ui.photos.PhotosViewModel
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsActions
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsSurface
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsSurfaceHost
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsUiState
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsViewModel
import com.blackatsystems.miguardia.ui.summary.SummaryScreen
import com.blackatsystems.miguardia.ui.summary.SummaryUiState
import com.blackatsystems.miguardia.ui.summary.SummaryViewModel
import com.blackatsystems.miguardia.ui.vacation.VacationActions
import com.blackatsystems.miguardia.ui.vacation.VacationSurface
import com.blackatsystems.miguardia.ui.vacation.VacationSurfaceHost
import com.blackatsystems.miguardia.ui.vacation.VacationUiState
import com.blackatsystems.miguardia.ui.vacation.VacationViewModel
import com.blackatsystems.miguardia.ui.theme.AppZoom
import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val SpanishArgentina = Locale.forLanguageTag("es-AR")
private val FullDateFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", SpanishArgentina)
private val ShiftTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", SpanishArgentina)
private val CompletedDayLight = Color(0xFFC8E6C9)
private val CompletedDayDark = Color(0xFF1B5E20)

private enum class MainDestination(
    @param:StringRes val labelRes: Int,
    val glyph: String,
) {
    CALENDAR(R.string.calendar, "▦"),
    SUMMARY(R.string.summary, "≡"),
    SETTINGS(R.string.settings, "⚙"),
}

@Composable
fun MiGuardiaApp(
    calendarViewModel: CalendarViewModel,
    nextEventViewModel: NextEventViewModel,
    managementViewModel: ManagementViewModel,
    summaryViewModel: SummaryViewModel,
    exceptionsViewModel: ExceptionsViewModel,
    vacationViewModel: VacationViewModel,
    photosViewModel: PhotosViewModel,
    notificationViewModel: NotificationViewModel,
    modifier: Modifier = Modifier,
    calendarNavigationRequest: Int = 0,
    appZoom: AppZoom = AppZoom.STANDARD,
    onAppZoomChange: (AppZoom) -> Unit = {},
) {
    val calendarState by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val nextEventState by nextEventViewModel.uiState.collectAsStateWithLifecycle()
    val managementState by managementViewModel.uiState.collectAsStateWithLifecycle()
    val summaryState by summaryViewModel.uiState.collectAsStateWithLifecycle()
    val exceptionsState by exceptionsViewModel.uiState.collectAsStateWithLifecycle()
    val vacationState by vacationViewModel.uiState.collectAsStateWithLifecycle()
    val photosState by photosViewModel.uiState.collectAsStateWithLifecycle()
    val notificationState by notificationViewModel.uiState.collectAsStateWithLifecycle()
    MiGuardiaApp(
        calendarState = calendarState,
        nextEventState = nextEventState,
        onNextEventRetry = nextEventViewModel::retry,
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
        vacationState = vacationState,
        vacationActions = VacationActions.from(vacationViewModel),
        photosState = photosState,
        photosActions = PhotosActions.from(photosViewModel),
        photosViewModel = photosViewModel,
        notificationState = notificationState,
        notificationActions = NotificationActions.from(notificationViewModel),
        calendarNavigationRequest = calendarNavigationRequest,
        appZoom = appZoom,
        onAppZoomChange = onAppZoomChange,
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
    nextEventState: NextEventUiState = NextEventUiState(),
    onNextEventRetry: () -> Unit = {},
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
    vacationState: VacationUiState = VacationUiState(visibleMonth = calendarState.visibleMonth),
    vacationActions: VacationActions = VacationActions(),
    photosState: PhotosUiState = PhotosUiState(month = calendarState.visibleMonth),
    photosActions: PhotosActions = PhotosActions(),
    photosViewModel: PhotosViewModel? = null,
    notificationState: NotificationUiState = NotificationUiState(),
    notificationActions: NotificationActions = NotificationActions(),
    calendarNavigationRequest: Int = 0,
    appZoom: AppZoom = AppZoom.STANDARD,
    onAppZoomChange: (AppZoom) -> Unit = {},
) {
    var destination by rememberSaveable { androidx.compose.runtime.mutableStateOf(MainDestination.CALENDAR) }
    var showAddChoice by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(calendarNavigationRequest) {
        if (calendarNavigationRequest > 0) destination = MainDestination.CALENDAR
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                MainDestination.entries.forEach { item ->
                    val label = stringResource(item.labelRes)
                    val showLabel = appZoom == AppZoom.STANDARD
                    NavigationBarItem(
                        modifier = if (showLabel) {
                            Modifier
                        } else {
                            Modifier.semantics { contentDescription = label }
                        },
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = {
                            Text(
                                text = item.glyph,
                                modifier = Modifier.clearAndSetSemantics {},
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        label = if (showLabel) {
                            {
                                Text(
                                    text = label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            null
                        },
                        alwaysShowLabel = showLabel,
                    )
                }
            }
        },
    ) { innerPadding ->
        when (destination) {
            MainDestination.CALENDAR -> CalendarScreen(
                state = calendarState,
                nextEventState = nextEventState,
                contentPadding = innerPadding,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onToday = onToday,
                onSelectDate = onSelectDate,
                onRetry = onRetry,
                onNextEventRetry = onNextEventRetry,
                onAddShift = { showAddChoice = true },
                onOpenPhotos = { photosActions.open(calendarState.visibleMonth) },
                appZoom = appZoom,
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
                onOpenVacations = { vacationActions.openList(calendarState.visibleMonth) },
                onOpenNotifications = notificationActions.openGlobal,
                appZoom = appZoom,
                onAppZoomChange = onAppZoomChange,
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
                onAddRange = {
                    onDismissDate()
                    managementActions.openAddShift(calendarState.visibleMonth, selectedDay.date)
                    managementActions.updateShiftMode(ShiftEntryMode.MULTIPLE)
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
                onOpenNotifications = notificationActions.openShift,
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
    if (vacationState.surface != VacationSurface.NONE) {
        VacationSurfaceHost(vacationState, vacationActions)
    }
    if (photosState.surface != PhotosSurface.NONE && photosViewModel != null) {
        PhotosSurfaceHost(photosState, photosActions, photosViewModel.fileStore)
    }
    if (notificationState.surface != NotificationSurface.NONE) {
        NotificationSurfaceHost(notificationState, notificationActions)
    }
    if (showAddChoice) {
        AlertDialog(
            onDismissRequest = { showAddChoice = false },
            title = { Text("Agregar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Elegí si querés cargar una guardia o un rango de fechas.")
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showAddChoice = false
                            managementActions.openAddShift(calendarState.visibleMonth, null)
                        },
                    ) { Text("Agregar guardia") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showAddChoice = false
                            managementActions.openAddShift(calendarState.visibleMonth, null)
                            managementActions.updateShiftMode(ShiftEntryMode.MULTIPLE)
                        },
                    ) { Text("Agregar rangos") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddChoice = false
                    },
                ) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun CalendarScreen(
    state: CalendarUiState,
    nextEventState: NextEventUiState,
    contentPadding: PaddingValues,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onNextEventRetry: () -> Unit,
    onAddShift: () -> Unit,
    onOpenPhotos: () -> Unit,
    appZoom: AppZoom,
) {
    val today = state.referenceInstant.atZone(AppDefaults.zoneId()).toLocalDate()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NextEventCard(state = nextEventState, onRetry = onNextEventRetry)
        MonthControls(
            visibleMonth = state.visibleMonth,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
            onToday = onToday,
            onPhotos = onOpenPhotos,
            appZoom = appZoom,
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
            CalendarGridViewport(
                month = state.visibleMonth,
                days = state.days,
                today = today,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onSelectDate = onSelectDate,
                appZoom = appZoom,
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
private fun CalendarGridViewport(
    month: YearMonth,
    days: List<CalendarDay>,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    appZoom: AppZoom,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isEnlarged = appZoom != AppZoom.STANDARD
        val gridWidth = if (isEnlarged) maxOf(maxWidth, 378.dp) else maxWidth
        val scrolling = if (isEnlarged) {
            Modifier.horizontalScroll(rememberScrollState())
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(scrolling),
        ) {
            Column(
                modifier = Modifier.width(gridWidth),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WeekdayHeader()
                MonthGrid(
                    month = month,
                    days = days,
                    today = today,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onSelectDate = onSelectDate,
                    enableMonthSwipe = !isEnlarged,
                )
            }
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
    PersistentMessage(message = message, onRetry = onRetry)
}

@Composable
private fun MonthControls(
    visibleMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onPhotos: () -> Unit,
    appZoom: AppZoom,
) {
    val previousDescription = stringResource(R.string.previous_month)
    val nextDescription = stringResource(R.string.next_month)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.semantics { contentDescription = previousDescription },
                ) {
                    Text("‹", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                    text = visibleMonth.displayName(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.semantics { contentDescription = nextDescription },
                ) {
                    Text("›", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.headlineMedium)
                }
            }
            if (appZoom == AppZoom.STANDARD) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onToday, modifier = Modifier.weight(1f)) {
                        Text("Ir a hoy")
                    }
                    Button(
                        onClick = onPhotos,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Fotos del cronograma del mes" },
                    ) { Text("Fotos del mes") }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onToday, modifier = Modifier.fillMaxWidth()) {
                        Text("Ir a hoy")
                    }
                    Button(
                        onClick = onPhotos,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Fotos del cronograma del mes" },
                    ) { Text("Fotos del mes") }
                }
            }
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
    enableMonthSwipe: Boolean,
) {
    val dayByDate = remember(days) { days.associateBy { it.date } }
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cells = List<CalendarDay?>(42) { index ->
        val dayNumber = index - offset + 1
        dayNumber.takeIf { it in 1..month.lengthOfMonth() }
            ?.let { dayByDate[month.atDay(it)] }
    }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }

    val swipeModifier = if (enableMonthSwipe) {
        Modifier.pointerInput(month) {
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
            }
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("month-grid")
            .then(swipeModifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 100.dp),
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
    val isCompletedDay = day.vacation == null &&
        day.shifts.isNotEmpty() &&
        day.shifts.all { it.temporalStatus == ShiftTemporalStatus.COMPLETED }
    val background = when {
        isCompletedDay && MaterialTheme.colorScheme.background.luminance() < 0.5f -> CompletedDayDark
        isCompletedDay -> CompletedDayLight
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Column(
        modifier = modifier
            .padding(horizontal = 1.dp, vertical = 2.dp)
            .heightIn(min = 100.dp)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .then(
                if (isToday) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                } else {
                    Modifier
                },
            )
            .testTag(if (isCompletedDay) "completed-day-${day.date}" else "day-${day.date}")
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(action = {
                    onClick()
                    true
                })
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${day.date.dayOfMonth}${day.date.dayOfWeek.calendarInitial()}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
        )
        val firstShift = day.shifts.firstOrNull()
        if (day.vacation != null) {
            Text(
                text = "V",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        } else if (firstShift != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(Color(firstShift.shift.colorArgbSnapshot)),
            )
            AutoSizeSingleLineText(
                text = firstShift.shift.objectiveAbbreviationSnapshot,
                maximum = 12.sp,
                minimum = 8.sp,
                fontWeight = FontWeight.Bold,
            )
            AutoSizeSingleLineText(
                text = firstShift.shift.timeRange(),
                maximum = 10.sp,
                minimum = 6.sp,
                letterSpacing = (-0.4).sp,
            )
            AutoSizeSingleLineText(
                text = firstShift.temporalStatus.shortLabel(),
                maximum = 9.sp,
                minimum = 6.sp,
                fontWeight = FontWeight.SemiBold,
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
        if (day.vacation == null && markers.isNotEmpty()) {
            AutoSizeSingleLineText(
                text = markers.joinToString(" · "),
                maximum = 12.sp,
                minimum = 7.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AutoSizeSingleLineText(
    text: String,
    maximum: TextUnit,
    minimum: TextUnit,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = 0.sp,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        var currentSize by remember(text, maxWidth, maximum, minimum) {
            mutableStateOf(maximum)
        }
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            fontSize = currentSize,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            textAlign = TextAlign.Center,
            onTextLayout = { result ->
                if (result.didOverflowWidth && currentSize.value > minimum.value) {
                    currentSize = (currentSize.value - 0.5f).coerceAtLeast(minimum.value).sp
                }
            },
        )
    }
}

@Composable
private fun DayDetailSheet(
    day: CalendarDay,
    onAddShift: () -> Unit,
    onAddRange: () -> Unit,
    onEditShift: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onDuplicateShift: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onDeleteShift: (java.util.UUID) -> Unit,
    onOpenExceptions: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onOpenNotifications: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
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
                excludedByVacation = day.vacation != null && calendarShift.shift.status == ShiftStatus.PLANNED,
                onEdit = onEditShift,
                onDuplicate = onDuplicateShift,
                onDelete = { pendingDeleteId = it.toString() },
                onOpenExceptions = onOpenExceptions,
                onOpenNotifications = onOpenNotifications,
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
        day.vacation?.let { vacation ->
            Text(
                "Vacaciones: ${vacation.startDate.fullDisplayName()} – ${vacation.endDateInclusive.fullDisplayName()}",
                fontWeight = FontWeight.SemiBold,
            )
        }
        Button(onClick = onAddShift, modifier = Modifier.fillMaxWidth()) {
            Text("Agregar guardia")
        }
        OutlinedButton(onClick = onAddRange, modifier = Modifier.fillMaxWidth()) {
            Text("Agregar rangos")
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
    excludedByVacation: Boolean = false,
    onEdit: ((com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit)? = null,
    onDuplicate: ((com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit)? = null,
    onDelete: ((java.util.UUID) -> Unit)? = null,
    onOpenExceptions: ((com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit)? = null,
    onOpenNotifications: ((com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit)? = null,
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
            if (onOpenExceptions != null) {
                Button(onClick = { onOpenExceptions(shift) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Informar novedad / notas")
                }
            }
            if (onOpenNotifications != null) {
                OutlinedButton(
                    onClick = { onOpenNotifications(shift) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Avisos") }
            }
            if (onEdit != null && onDuplicate != null && onDelete != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedButton(onClick = { onEdit(shift) }, modifier = Modifier.weight(1f)) {
                        Text("Editar")
                    }
                    OutlinedButton(onClick = { onDuplicate(shift) }, modifier = Modifier.weight(1f)) {
                        Text("Duplicar")
                    }
                }
                DestructiveAction(label = "Eliminar", onClick = { onDelete(shift.id) })
            }
            if (excludedByVacation) {
                Text(
                    "Esta guardia se conserva, pero no computa horas porque su fecha inicial está en vacaciones.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenObjectives: () -> Unit,
    onOpenHolidays: () -> Unit,
    onOpenVacations: () -> Unit,
    onOpenNotifications: () -> Unit,
    appZoom: AppZoom,
    onAppZoomChange: (AppZoom) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeading("Configuración", supportingText = stringResource(R.string.settings_intro))
        NavigationCard(
            title = "Objetivos y horarios",
            description = "Plantillas, colores y horarios para nuevas guardias.",
            onClick = onOpenObjectives,
        )
        NavigationCard(
            title = "Feriados",
            description = "Elegí fechas en un calendario y agregá un nombre opcional.",
            onClick = onOpenHolidays,
        )
        NavigationCard(
            title = "Vacaciones",
            description = "Períodos inclusivos y su efecto en el calendario.",
            onClick = onOpenVacations,
        )
        NavigationCard(
            title = "Notificaciones",
            description = "Recordatorios, permisos, privacidad y sonido.",
            onClick = onOpenNotifications,
        )
        SectionCard(
            title = "Zoom de MiGuardia",
            supportingText = "Aumenta toda la aplicación sin modificar ajustes de Android.",
        ) {
            AppZoom.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onAppZoomChange(option) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = appZoom == option,
                        onClick = { onAppZoomChange(option) },
                    )
                    Text(option.label, fontWeight = if (appZoom == option) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

private fun DayOfWeek.calendarInitial(): String = when (this) {
    DayOfWeek.MONDAY -> "L"
    DayOfWeek.TUESDAY -> "M"
    DayOfWeek.WEDNESDAY -> "X"
    DayOfWeek.THURSDAY -> "J"
    DayOfWeek.FRIDAY -> "V"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "D"
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
    vacation?.let {
        parts += "vacaciones desde ${it.startDate.fullDisplayName()} hasta ${it.endDateInclusive.fullDisplayName()} inclusive"
    }
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
