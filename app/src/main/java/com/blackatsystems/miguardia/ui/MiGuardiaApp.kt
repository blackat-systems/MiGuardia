package com.blackatsystems.miguardia.ui

import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import com.blackatsystems.miguardia.core.domain.calendar.CalendarShift
import com.blackatsystems.miguardia.core.domain.calendar.ShiftTemporalStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.nextevent.isEligibleUpcomingWork
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import com.blackatsystems.miguardia.core.domain.weather.roundedTemperature
import com.blackatsystems.miguardia.core.domain.weather.spanishLabel
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import com.blackatsystems.miguardia.ui.management.CalendarManagementInlineContent
import com.blackatsystems.miguardia.ui.management.InitialDataPreparationContent
import com.blackatsystems.miguardia.ui.management.ManagementActions
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
import com.blackatsystems.miguardia.ui.profile.ProfileActions
import com.blackatsystems.miguardia.ui.profile.ProfileSurface
import com.blackatsystems.miguardia.ui.profile.ProfileSurfaceHost
import com.blackatsystems.miguardia.ui.profile.ProfileUiState
import com.blackatsystems.miguardia.ui.profile.ProfileViewModel
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
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import com.blackatsystems.miguardia.ui.weather.WeatherActions
import com.blackatsystems.miguardia.ui.weather.ShiftWeatherBrief
import com.blackatsystems.miguardia.ui.weather.WeatherSurface
import com.blackatsystems.miguardia.ui.weather.WeatherSurfaceHost
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import com.blackatsystems.miguardia.ui.weather.WeatherViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val SpanishArgentina = Locale.forLanguageTag("es-AR")
private val FullDateFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", SpanishArgentina)
private val ShiftTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", SpanishArgentina)
private enum class MainDestination(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
    val glyph: String,
) {
    CALENDAR(R.string.calendar, R.string.drawer_calendar_description, "▦"),
    SUMMARY(R.string.summary, R.string.drawer_summary_description, "≡"),
    APPEARANCE(R.string.appearance, R.string.drawer_appearance_description, "◐"),
}

private enum class DrawerAction(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
    val glyph: String,
    val testTag: String,
) {
    PROFILE(R.string.profile, R.string.drawer_profile_description, "◎", "drawer-action-profile"),
    OBJECTIVES(R.string.objectives_and_schedules, R.string.drawer_objectives_description, "⌖", "drawer-action-objectives"),
    HOLIDAYS(R.string.holidays, R.string.drawer_holidays_description, "✦", "drawer-action-holidays"),
    VACATIONS(R.string.vacations, R.string.drawer_vacations_description, "∿", "drawer-action-vacations"),
    NOTIFICATIONS(R.string.notifications, R.string.drawer_notifications_description, "◌", "drawer-action-notifications"),
    WEATHER(R.string.weather, R.string.drawer_weather_description, "☁", "drawer-action-weather"),
}

private val WorkDrawerActions = listOf(
    DrawerAction.PROFILE,
    DrawerAction.OBJECTIVES,
    DrawerAction.HOLIDAYS,
    DrawerAction.VACATIONS,
)
private val ContextDrawerActions = listOf(
    DrawerAction.NOTIFICATIONS,
    DrawerAction.WEATHER,
)

@Composable
private fun DrawerHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.vigiliaColors.active.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "M",
                color = MaterialTheme.vigiliaColors.active,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.drawer_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.vigiliaColors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun DrawerSectionTitle(@StringRes labelRes: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = stringResource(labelRes).uppercase(SpanishArgentina),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.vigiliaColors.active,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DrawerDestinationItem(
    item: MainDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { DrawerItemLabel(item.labelRes, item.descriptionRes, selected) },
        selected = selected,
        onClick = onClick,
        icon = { DrawerGlyph(item.glyph) },
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .testTag("main-destination-${item.name.lowercase()}")
            .semantics { this.selected = selected },
        shape = MaterialTheme.shapes.medium,
        colors = NavigationDrawerItemDefaults.colors(
            selectedIconColor = MaterialTheme.vigiliaColors.active,
            selectedTextColor = MaterialTheme.vigiliaColors.active,
            selectedContainerColor = MaterialTheme.vigiliaColors.active.copy(alpha = 0.18f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun DrawerActionItem(
    action: DrawerAction,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { DrawerItemLabel(action.labelRes, action.descriptionRes, selected = false) },
        selected = false,
        onClick = onClick,
        icon = { DrawerGlyph(action.glyph) },
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .testTag(action.testTag),
        shape = MaterialTheme.shapes.medium,
        colors = NavigationDrawerItemDefaults.colors(
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun DrawerItemLabel(
    @StringRes labelRes: Int,
    @StringRes descriptionRes: Int,
    selected: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = stringResource(labelRes),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        Text(
            text = stringResource(descriptionRes),
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) {
                MaterialTheme.vigiliaColors.active
            } else {
                MaterialTheme.vigiliaColors.onSurfaceMuted
            },
        )
    }
}

@Composable
private fun DrawerGlyph(glyph: String) {
    Text(
        text = glyph,
        modifier = Modifier.clearAndSetSemantics {},
        fontWeight = FontWeight.Bold,
    )
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
    weatherViewModel: WeatherViewModel,
    profileViewModel: ProfileViewModel,
    modifier: Modifier = Modifier,
    calendarNavigationRequest: Int = 0,
    appZoom: AppZoom = AppZoom.STANDARD,
    onAppZoomChange: (AppZoom) -> Unit = {},
    appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onAppThemeModeChange: (AppThemeMode) -> Unit = {},
) {
    val calendarState by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val nextEventState by nextEventViewModel.uiState.collectAsStateWithLifecycle()
    val managementState by managementViewModel.uiState.collectAsStateWithLifecycle()
    val summaryState by summaryViewModel.uiState.collectAsStateWithLifecycle()
    val exceptionsState by exceptionsViewModel.uiState.collectAsStateWithLifecycle()
    val vacationState by vacationViewModel.uiState.collectAsStateWithLifecycle()
    val photosState by photosViewModel.uiState.collectAsStateWithLifecycle()
    val notificationState by notificationViewModel.uiState.collectAsStateWithLifecycle()
    val weatherState by weatherViewModel.uiState.collectAsStateWithLifecycle()
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    MiGuardiaApp(
        calendarState = calendarState,
        nextEventState = nextEventState,
        onNextEventRetry = nextEventViewModel::retry,
        onPreviousMonth = calendarViewModel::showPreviousMonth,
        onNextMonth = calendarViewModel::showNextMonth,
        onToday = calendarViewModel::showCurrentMonth,
        onSelectDate = calendarViewModel::selectDate,
        onDismissDate = calendarViewModel::clearSelectedDate,
        onEnterCalendarEditMode = calendarViewModel::enterEditMode,
        onEditSelectionChange = calendarViewModel::setEditSelectedDates,
        onConfirmEditSelection = calendarViewModel::confirmEditSelection,
        onResumeEditSelection = calendarViewModel::resumeEditSelection,
        onFinishCalendarEditMode = calendarViewModel::finishEditMode,
        onRetry = calendarViewModel::retry,
        summaryState = summaryState,
        onSummaryPreviousMonth = summaryViewModel::showPreviousMonth,
        onSummaryNextMonth = summaryViewModel::showNextMonth,
        onSummaryToday = summaryViewModel::showCurrentMonth,
        onSummaryRetry = summaryViewModel::retry,
        onSeniorityYearsChange = summaryViewModel::setSeniorityYears,
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
        weatherState = weatherState,
        weatherActions = WeatherActions.from(weatherViewModel),
        profileState = profileState,
        profileActions = ProfileActions.from(profileViewModel),
        calendarNavigationRequest = calendarNavigationRequest,
        appZoom = appZoom,
        onAppZoomChange = onAppZoomChange,
        appThemeMode = appThemeMode,
        onAppThemeModeChange = onAppThemeModeChange,
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
    onEnterCalendarEditMode: (LocalDate?) -> Unit = {},
    onEditSelectionChange: (Set<LocalDate>) -> Unit = {},
    onConfirmEditSelection: () -> Unit = {},
    onResumeEditSelection: () -> Unit = {},
    onFinishCalendarEditMode: () -> Unit = {},
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
    onSeniorityYearsChange: (Int) -> Unit = {},
    exceptionsState: ExceptionsUiState = ExceptionsUiState(holidayMonth = calendarState.visibleMonth),
    exceptionsActions: ExceptionsActions = ExceptionsActions(),
    vacationState: VacationUiState = VacationUiState(visibleMonth = calendarState.visibleMonth),
    vacationActions: VacationActions = VacationActions(),
    photosState: PhotosUiState = PhotosUiState(month = calendarState.visibleMonth),
    photosActions: PhotosActions = PhotosActions(),
    photosViewModel: PhotosViewModel? = null,
    notificationState: NotificationUiState = NotificationUiState(),
    notificationActions: NotificationActions = NotificationActions(),
    weatherState: WeatherUiState = WeatherUiState(),
    weatherActions: WeatherActions = WeatherActions(),
    profileState: ProfileUiState = ProfileUiState(),
    profileActions: ProfileActions = ProfileActions(),
    calendarNavigationRequest: Int = 0,
    appZoom: AppZoom = AppZoom.STANDARD,
    onAppZoomChange: (AppZoom) -> Unit = {},
    appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onAppThemeModeChange: (AppThemeMode) -> Unit = {},
) {
    var destination by rememberSaveable { androidx.compose.runtime.mutableStateOf(MainDestination.CALENDAR) }
    val drawerState = remember { DrawerState(initialValue = DrawerValue.Closed) }
    val coroutineScope = rememberCoroutineScope()
    val selectedDay = if (calendarState.interactionMode == CalendarInteractionMode.VIEW) {
        calendarState.detailDate?.let { selectedDate ->
        calendarState.days.firstOrNull { it.date == selectedDate }
        }
    } else {
        null
    }
    val inlineCalendarManagement = destination == MainDestination.CALENDAR &&
        calendarState.interactionMode == CalendarInteractionMode.EDIT &&
        when (managementState.surface) {
            ManagementSurface.INITIAL_DATA_PREPARATION -> true
            ManagementSurface.DAY_OFF_FORM -> managementState.dayOffDraft != null
            ManagementSurface.SHIFT_FORM -> managementState.shiftDraft?.let { it.editingShift == null } == true
            else -> false
        }
    val hasBlockingSurface = managementState.surface != ManagementSurface.NONE ||
        exceptionsState.surface != ExceptionsSurface.NONE ||
        vacationState.surface != VacationSurface.NONE ||
        photosState.surface != PhotosSurface.NONE ||
        notificationState.surface != NotificationSurface.NONE ||
        weatherState.surface != WeatherSurface.NONE ||
        profileState.surface != ProfileSurface.NONE
    val canOpenDrawer = !hasBlockingSurface && selectedDay == null
    LaunchedEffect(calendarNavigationRequest) {
        if (calendarNavigationRequest > 0) {
            if (managementState.surface == ManagementSurface.INITIAL_DATA_PREPARATION) {
                managementActions.close()
            }
            drawerState.snapTo(DrawerValue.Closed)
            destination = MainDestination.CALENDAR
        }
    }
    LaunchedEffect(hasBlockingSurface) {
        if (hasBlockingSurface) drawerState.snapTo(DrawerValue.Closed)
    }
    val selectDestination: (MainDestination) -> Unit = { selectedDestination ->
        coroutineScope.launch {
            drawerState.close()
            destination = selectedDestination
        }
    }
    val openDrawerAction: (DrawerAction) -> Unit = { action ->
        coroutineScope.launch {
            drawerState.close()
            when (action) {
                DrawerAction.PROFILE -> profileActions.open()
                DrawerAction.OBJECTIVES -> managementActions.openSettings()
                DrawerAction.HOLIDAYS -> exceptionsActions.openHolidays(calendarState.visibleMonth)
                DrawerAction.VACATIONS -> vacationActions.openList(calendarState.visibleMonth)
                DrawerAction.NOTIFICATIONS -> notificationActions.openGlobal()
                DrawerAction.WEATHER -> weatherActions.openGlobal()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.testTag("main-navigation-drawer"),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DrawerHeader()
                    DrawerDestinationItem(
                        item = MainDestination.CALENDAR,
                        selected = destination == MainDestination.CALENDAR,
                        onClick = { selectDestination(MainDestination.CALENDAR) },
                    )
                    DrawerDestinationItem(
                        item = MainDestination.SUMMARY,
                        selected = destination == MainDestination.SUMMARY,
                        onClick = { selectDestination(MainDestination.SUMMARY) },
                    )
                    DrawerSectionTitle(R.string.drawer_section_work)
                    WorkDrawerActions.forEach { action ->
                        DrawerActionItem(action = action, onClick = { openDrawerAction(action) })
                    }
                    DrawerSectionTitle(R.string.drawer_section_context)
                    ContextDrawerActions.forEach { action ->
                        DrawerActionItem(action = action, onClick = { openDrawerAction(action) })
                    }
                    DrawerSectionTitle(R.string.drawer_section_application)
                    DrawerDestinationItem(
                        item = MainDestination.APPEARANCE,
                        selected = destination == MainDestination.APPEARANCE,
                        onClick = { selectDestination(MainDestination.APPEARANCE) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                val openMenuDescription = stringResource(R.string.open_menu)
                CenterAlignedTopAppBar(
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    navigationIcon = {
                        IconButton(
                            enabled = canOpenDrawer,
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier
                                .testTag("main-menu-button")
                                .semantics { contentDescription = openMenuDescription },
                        ) {
                            Text(
                                text = "☰",
                                modifier = Modifier.clearAndSetSemantics {},
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                    title = {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                )
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
                    onEditSelectionChange = { selectedDates ->
                        onEditSelectionChange(selectedDates)
                        if (inlineCalendarManagement) {
                            managementActions.updateCalendarSelection(selectedDates)
                        }
                    },
                    onConfirmEditSelection = onConfirmEditSelection,
                    onResumeEditSelection = onResumeEditSelection,
                    onRetry = onRetry,
                    onNextEventRetry = onNextEventRetry,
                    onEnterEditMode = { onEnterCalendarEditMode(null) },
                    onFinishEditMode = {
                        if (managementState.surface == ManagementSurface.INITIAL_DATA_PREPARATION) {
                            managementActions.close()
                        }
                        onFinishCalendarEditMode()
                    },
                    onLoadInitialData = {
                        onEnterCalendarEditMode(null)
                        managementActions.openInitialDataPreparation()
                    },
                    managementState = managementState,
                    managementActions = managementActions,
                    onOpenNotifications = notificationActions.openShift,
                    onOpenExceptions = exceptionsActions.openShift,
                    onOpenWeather = weatherActions.openShift,
                    weatherState = weatherState,
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
                    onSeniorityYearsChange = onSeniorityYearsChange,
                )

                MainDestination.APPEARANCE -> AppearanceScreen(
                    contentPadding = innerPadding,
                    appZoom = appZoom,
                    onAppZoomChange = onAppZoomChange,
                    appThemeMode = appThemeMode,
                    onAppThemeModeChange = onAppThemeModeChange,
                )
            }
        }
    }

    BackHandler(
        enabled = destination != MainDestination.CALENDAR && !hasBlockingSurface,
        onBack = { destination = MainDestination.CALENDAR },
    )
    BackHandler(
        enabled = destination == MainDestination.CALENDAR &&
            calendarState.interactionMode == CalendarInteractionMode.EDIT &&
            selectedDay == null &&
            !hasBlockingSurface,
        onBack = {
            if (calendarState.editSelectionConfirmed) {
                onResumeEditSelection()
            } else {
                onFinishCalendarEditMode()
            }
        },
    )
    BackHandler(
        enabled = destination == MainDestination.CALENDAR &&
            calendarState.interactionMode == CalendarInteractionMode.EDIT &&
            managementState.surface == ManagementSurface.INITIAL_DATA_PREPARATION,
        onBack = {
            managementActions.close()
            onFinishCalendarEditMode()
        },
    )
    BackHandler(
        enabled = drawerState.isOpen && !hasBlockingSurface,
        onBack = { coroutineScope.launch { drawerState.close() } },
    )
    val weatherBriefIds = selectedDay
        ?.shifts
        ?.map(CalendarShift::shift)
        ?.filter { it.isEligibleUpcomingWork(calendarState.referenceInstant, listOfNotNull(selectedDay.vacation)) }
        ?.mapTo(linkedSetOf()) { it.id }
        .orEmpty()
    LaunchedEffect(weatherBriefIds, weatherState.preferences.enabled) {
        if (weatherBriefIds.isEmpty() || !weatherState.preferences.enabled) {
            weatherActions.clearBriefs()
        } else {
            weatherActions.loadBriefs(weatherBriefIds)
        }
    }
    val navigationBarBottomPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    if (selectedDay != null && managementState.surface == ManagementSurface.NONE) {
        ModalBottomSheet(
            onDismissRequest = onDismissDate,
            modifier = Modifier.padding(bottom = navigationBarBottomPadding + 16.dp),
        ) {
            DayDetailSheet(
                day = selectedDay,
                referenceInstant = calendarState.referenceInstant,
                editEnabled = calendarState.hasAnyShiftsLoaded,
                onEditDay = {
                    onDismissDate()
                    val hasUsableSchedule = managementState.scheduleOptions.any {
                        it.objective.isActive && it.combination.isActive
                    }
                    if (!calendarState.hasAnyShifts && !hasUsableSchedule) {
                        onEnterCalendarEditMode(null)
                        managementActions.openInitialDataPreparation()
                    } else {
                        onEnterCalendarEditMode(selectedDay.date)
                    }
                },
                onOpenWeather = weatherActions.openShift,
                weatherState = weatherState,
            )
        }
    }

    if (profileState.surface != ProfileSurface.NONE) {
        ProfileSurfaceHost(
            state = profileState,
            actions = profileActions.copy(openObjectives = managementActions.openSettings),
        )
    }
    if (managementState.surface != ManagementSurface.NONE && !inlineCalendarManagement) {
        ManagementSurfaceHost(
            state = managementState,
            actions = managementActions,
            onOpenNotifications = notificationActions.openShift,
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
    if (weatherState.surface != WeatherSurface.NONE) {
        WeatherSurfaceHost(weatherState, weatherActions)
    }
}

internal data class VerticalScrollbarMetrics(
    val thumbHeightPx: Float,
    val thumbOffsetPx: Float,
)

internal fun calculateVerticalScrollbarMetrics(
    viewportHeightPx: Float,
    maxScrollPx: Int,
    scrollValuePx: Int,
    minimumThumbHeightPx: Float,
): VerticalScrollbarMetrics? {
    if (viewportHeightPx <= 0f || maxScrollPx <= 0) return null
    val contentHeightPx = viewportHeightPx + maxScrollPx
    val boundedMinimumHeight = minimumThumbHeightPx.coerceIn(0f, viewportHeightPx)
    val thumbHeightPx = (viewportHeightPx * viewportHeightPx / contentHeightPx)
        .coerceIn(boundedMinimumHeight, viewportHeightPx)
    val availableTravelPx = viewportHeightPx - thumbHeightPx
    val scrollFraction = scrollValuePx.coerceIn(0, maxScrollPx).toFloat() / maxScrollPx.toFloat()
    return VerticalScrollbarMetrics(
        thumbHeightPx = thumbHeightPx,
        thumbOffsetPx = availableTravelPx * scrollFraction,
    )
}

@Composable
private fun CalendarScrollContainer(
    scrollState: ScrollState,
    verticalSpacing: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("calendar-scroll-viewport"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .testTag("calendar-scroll-container"),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
        if (scrollState.maxValue > 0) {
            CalendarVerticalScrollIndicator(
                scrollState = scrollState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun CalendarVerticalScrollIndicator(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    var trackHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val metrics = calculateVerticalScrollbarMetrics(
        viewportHeightPx = trackHeightPx.toFloat(),
        maxScrollPx = scrollState.maxValue,
        scrollValuePx = 0,
        minimumThumbHeightPx = with(density) { 40.dp.toPx() },
    )
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)
    val thumbColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .width(10.dp)
            .fillMaxHeight()
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .onSizeChanged { trackHeightPx = it.height }
            .testTag("calendar-scrollbar-track"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(trackColor, CircleShape),
        )
        metrics?.let { currentMetrics ->
            Box(
                modifier = Modifier
                    .offset {
                        val currentOffsetPx = calculateVerticalScrollbarMetrics(
                            viewportHeightPx = trackHeightPx.toFloat(),
                            maxScrollPx = scrollState.maxValue,
                            scrollValuePx = scrollState.value,
                            minimumThumbHeightPx = with(density) { 40.dp.toPx() },
                        )?.thumbOffsetPx ?: 0f
                        IntOffset(0, currentOffsetPx.roundToInt())
                    }
                    .fillMaxWidth()
                    .height(with(density) { currentMetrics.thumbHeightPx.toDp() })
                    .background(thumbColor, CircleShape)
                    .testTag("calendar-scrollbar-thumb"),
            )
        }
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
    onEditSelectionChange: (Set<LocalDate>) -> Unit,
    onConfirmEditSelection: () -> Unit,
    onResumeEditSelection: () -> Unit,
    onRetry: () -> Unit,
    onNextEventRetry: () -> Unit,
    onEnterEditMode: () -> Unit,
    onFinishEditMode: () -> Unit,
    onLoadInitialData: () -> Unit,
    onOpenPhotos: () -> Unit,
    managementState: ManagementUiState,
    managementActions: ManagementActions,
    onOpenNotifications: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onOpenExceptions: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onOpenWeather: (java.util.UUID) -> Unit,
    weatherState: WeatherUiState,
    appZoom: AppZoom,
) {
    val today = state.referenceInstant.atZone(AppDefaults.zoneId()).toLocalDate()
    val verticalScrollState = rememberScrollState()
    var pendingMonthChange by rememberSaveable { mutableStateOf<String?>(null) }
    val formOpen = when (managementState.surface) {
        ManagementSurface.SHIFT_FORM -> managementState.shiftDraft?.let { it.editingShift == null } == true
        ManagementSurface.DAY_OFF_FORM -> managementState.dayOffDraft != null
        else -> false
    }
    val initialDataPreparationOpen = managementState.surface == ManagementSurface.INITIAL_DATA_PREPARATION
    val requestMonthChange: (String, () -> Unit) -> Unit = { key, action ->
        if (state.interactionMode == CalendarInteractionMode.EDIT && state.editSelectedDates.isNotEmpty()) {
            pendingMonthChange = key
        } else {
            action()
        }
    }
    val previousMonth = { requestMonthChange("previous", onPreviousMonth) }
    val nextMonth = { requestMonthChange("next", onNextMonth) }
    val currentMonth = {
        if (state.visibleMonth == YearMonth.from(today)) onToday() else requestMonthChange("today", onToday)
    }
    TransientConfirmation(
        message = managementState.infoMessage.takeIf {
            managementState.surface in setOf(ManagementSurface.NONE, ManagementSurface.INITIAL_DATA_PREPARATION)
        },
        onDismiss = managementActions.clearMessage,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            val verticalSpacing = if (maxHeight < 1_200.dp) 4.dp else 8.dp
            CalendarScrollContainer(
                scrollState = verticalScrollState,
                verticalSpacing = verticalSpacing,
            ) {
                NextEventCard(state = nextEventState, onRetry = onNextEventRetry)
                if (state.interactionMode == CalendarInteractionMode.EDIT) {
                    Text(
                        text = "Editando calendario",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.vigiliaColors.active,
                        modifier = Modifier.semantics {
                            contentDescription = "Editando calendario. Las acciones para modificar están habilitadas."
                        },
                    )
                }
                MonthControls(
                    visibleMonth = state.visibleMonth,
                    onPrevious = previousMonth,
                    onNext = nextMonth,
                    onToday = currentMonth,
                    onPhotos = onOpenPhotos,
                    showPhotos = state.interactionMode == CalendarInteractionMode.EDIT,
                    appZoom = appZoom,
                    navigationEnabled = !formOpen,
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
                        onPreviousMonth = previousMonth,
                        onNextMonth = nextMonth,
                        onSelectDate = onSelectDate,
                        interactionMode = state.interactionMode,
                        selectedDates = state.editSelectedDates,
                        onEditSelectionChange = onEditSelectionChange,
                        selectionEnabled = !formOpen && !initialDataPreparationOpen,
                        selectionConfirmed = state.editSelectionConfirmed,
                        monthSwipeEnabled = !formOpen,
                        appZoom = appZoom,
                    )
                }

                if (state.interactionMode == CalendarInteractionMode.EDIT) {
                    if (initialDataPreparationOpen) {
                        InitialDataPreparationContent(
                            state = managementState,
                            actions = managementActions,
                        )
                    } else if (!formOpen) {
                        CalendarEditTools(
                            state = state,
                            managementActions = managementActions,
                            onConfirmSelection = onConfirmEditSelection,
                            onResumeSelection = onResumeEditSelection,
                            onOpenExceptions = onOpenExceptions,
                            onOpenWeather = onOpenWeather,
                            weatherState = weatherState,
                        )
                    }
                    if (formOpen) {
                        CalendarManagementInlineContent(
                            state = managementState,
                            actions = managementActions,
                            onOpenNotifications = onOpenNotifications,
                            onReturnToDateSelection = onResumeEditSelection,
                        )
                    }
                }

                if (!formOpen) {
                    if (state.interactionMode == CalendarInteractionMode.EDIT) {
                        OutlinedButton(
                            onClick = onFinishEditMode,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (initialDataPreparationOpen) "Salir por ahora" else "Salir de edición")
                        }
                    } else {
                        if (!state.hasAnyShiftsLoaded && state.shiftPresenceError) {
                            OutlinedButton(
                                onClick = onRetry,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Reintentar carga")
                            }
                        } else if (!state.hasAnyShiftsLoaded) {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("calendar-shift-presence-loading"),
                            ) {
                                Text("Cargando datos…")
                            }
                        } else {
                            Button(
                                onClick = if (state.hasAnyShifts) onEnterEditMode else onLoadInitialData,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("calendar-bottom-action"),
                            ) {
                                Text(if (state.hasAnyShifts) "Editar calendario" else "Cargar datos")
                            }
                        }
                    }
                }
            }
        }
    }
    pendingMonthChange?.let { requested ->
        AlertDialog(
            onDismissRequest = { pendingMonthChange = null },
            title = { Text("Cambiar de mes") },
            text = { Text("Al cambiar de mes se limpiará la selección actual. ¿Querés continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingMonthChange = null
                    onEditSelectionChange(emptySet())
                    when (requested) {
                        "previous" -> onPreviousMonth()
                        "next" -> onNextMonth()
                        else -> onToday()
                    }
                }) { Text("Cambiar de mes") }
            },
            dismissButton = { TextButton(onClick = { pendingMonthChange = null }) { Text("Conservar selección") } },
        )
    }
}

@Composable
private fun CalendarEditTools(
    state: CalendarUiState,
    managementActions: ManagementActions,
    onConfirmSelection: () -> Unit,
    onResumeSelection: () -> Unit,
    onOpenExceptions: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onOpenWeather: (java.util.UUID) -> Unit,
    weatherState: WeatherUiState,
) {
    val selectedDays = state.days.filter { it.date in state.editSelectedDates }
    val selectionReady = state.loadState == CalendarLoadState.CONTENT &&
        selectedDays.size == state.editSelectedDates.size
    val selectionStatusMessage = if (state.loadState == CalendarLoadState.ERROR) {
        "No pudimos preparar los días elegidos. Reintentá arriba."
    } else {
        "Cargando los días elegidos…"
    }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar-edit-tools"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.editSelectedDates.isEmpty()) {
            Text(
                text = "Elegí uno o varios días",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Tocá las fechas que querés modificar",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("calendar-edit-empty-instruction"),
            )
        } else if (!state.editSelectionConfirmed) {
            Text(
                text = "Elegí uno o varios días",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (state.editSelectedDates.size == 1) {
                    "1 día seleccionado"
                } else {
                    "${state.editSelectedDates.size} días seleccionados"
                },
                modifier = Modifier.testTag("calendar-edit-selection-count"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Button(
                onClick = onConfirmSelection,
                enabled = selectionReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("calendar-confirm-date-selection"),
            ) {
                Text("Terminar de elegir días")
            }
            if (!selectionReady) {
                Text(
                    text = selectionStatusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                text = "¿Qué querés cargar?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (state.editSelectedDates.size == 1) {
                    "1 día elegido"
                } else {
                    "${state.editSelectedDates.size} días elegidos"
                },
                modifier = Modifier.testTag("calendar-edit-selection-count"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (selectionReady) {
                Button(
                    onClick = {
                        managementActions.openAddShiftForDates(state.visibleMonth, state.editSelectedDates)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("calendar-add-shift"),
                ) { Text("Agregar guardia") }
                if (selectedDays.none { it.shifts.isNotEmpty() }) {
                    OutlinedButton(
                        onClick = {
                            managementActions.openDayOffsForDates(state.visibleMonth, state.editSelectedDates)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .testTag("calendar-add-day-offs"),
                    ) { Text("Agregar francos") }
                }
            } else {
                Text(
                    text = selectionStatusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onResumeSelection,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("calendar-change-date-selection"),
            ) {
                Text("Modificar días elegidos")
            }
        }
    }

    if (state.editSelectionConfirmed && selectedDays.size == 1 && selectedDays.single().shifts.isNotEmpty()) {
        val day = selectedDays.single()
        SectionCard(
            title = "Acciones del ${day.date.dayOfMonth}",
            supportingText = "Cada guardia conserva sus acciones individuales.",
        ) {
            day.shifts.forEachIndexed { index, calendarShift ->
                if (index > 0) HorizontalDivider()
                ShiftDetail(
                    calendarShift = calendarShift,
                    excludedByVacation = day.vacation != null && calendarShift.shift.status == ShiftStatus.PLANNED,
                    onEdit = managementActions.openEditShift,
                    onAddAnotherShift = if (index == 0) {
                        {
                            managementActions.openAddShiftForDates(state.visibleMonth, setOf(day.date))
                        }
                    } else {
                        null
                    },
                    addAnotherShiftLabel = if (day.shifts.size == 1) {
                        "Agregar una segunda guardia"
                    } else {
                        "Agregar otra guardia"
                    },
                    onDelete = { id -> pendingDeleteId = id.toString() },
                    onOpenExceptions = onOpenExceptions,
                    onOpenWeather = onOpenWeather,
                    weatherEnabled = weatherState.preferences.enabled,
                    weatherUnit = weatherState.preferences.unitSystem,
                    weatherBrief = weatherState.shiftBriefs[calendarShift.shift.id],
                    weatherLoading = calendarShift.shift.id in weatherState.loadingBriefIds,
                )
            }
        }
    }
    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Eliminar guardia") },
            text = { Text("Se eliminará solamente esta guardia. ¿Querés continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    managementActions.deleteShift(java.util.UUID.fromString(id))
                    pendingDeleteId = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Cancelar") } },
        )
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
    interactionMode: CalendarInteractionMode,
    selectedDates: Set<LocalDate>,
    onEditSelectionChange: (Set<LocalDate>) -> Unit,
    selectionEnabled: Boolean,
    selectionConfirmed: Boolean,
    monthSwipeEnabled: Boolean,
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
                    interactionMode = interactionMode,
                    selectedDates = selectedDates,
                    onEditSelectionChange = onEditSelectionChange,
                    selectionEnabled = selectionEnabled,
                    selectionConfirmed = selectionConfirmed,
                    enableMonthSwipe = !isEnlarged && monthSwipeEnabled,
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
    showPhotos: Boolean,
    appZoom: AppZoom,
    navigationEnabled: Boolean,
) {
    val previousDescription = stringResource(R.string.previous_month)
    val nextDescription = stringResource(R.string.next_month)
    val todayInline = appZoom == AppZoom.STANDARD && !showPhotos
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = if (todayInline) 0.dp else 10.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onPrevious,
                    enabled = navigationEnabled,
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
                    enabled = navigationEnabled,
                    modifier = Modifier.semantics { contentDescription = nextDescription },
                ) {
                    Text("›", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.headlineMedium)
                }
                if (todayInline) {
                    TextButton(
                        onClick = onToday,
                        enabled = navigationEnabled,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text("Ir a hoy")
                    }
                }
            }
            if (!todayInline) {
                if (appZoom == AppZoom.STANDARD) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onToday,
                            enabled = navigationEnabled,
                            modifier = if (showPhotos) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                        ) {
                            Text("Ir a hoy")
                        }
                        if (showPhotos) {
                            OutlinedButton(
                                onClick = onPhotos,
                                enabled = navigationEnabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = "Fotos del cronograma del mes" },
                            ) { Text("Fotos del mes") }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onToday,
                            enabled = navigationEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Ir a hoy")
                        }
                        if (showPhotos) {
                            OutlinedButton(
                                onClick = onPhotos,
                                enabled = navigationEnabled,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Fotos del cronograma del mes" },
                            ) { Text("Fotos del mes") }
                        }
                    }
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
    interactionMode: CalendarInteractionMode,
    selectedDates: Set<LocalDate>,
    onEditSelectionChange: (Set<LocalDate>) -> Unit,
    selectionEnabled: Boolean,
    selectionConfirmed: Boolean,
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
        Modifier.pointerInput(month, selectedDates) {
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
                            isSelected = day.date in selectedDates,
                            enabled = interactionMode == CalendarInteractionMode.VIEW ||
                                (selectionEnabled && !selectionConfirmed),
                            onClick = {
                                if (interactionMode == CalendarInteractionMode.VIEW) {
                                    onSelectDate(day.date)
                                } else if (selectionEnabled && !selectionConfirmed) {
                                    onEditSelectionChange(
                                        if (day.date in selectedDates) selectedDates - day.date else selectedDates + day.date,
                                    )
                                }
                            },
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
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = day.accessibilityDescription(isToday)
    val isCompletedDay = day.vacation == null &&
        day.shifts.isNotEmpty() &&
        day.shifts.all { it.temporalStatus == ShiftTemporalStatus.COMPLETED }
    val vigilia = MaterialTheme.vigiliaColors
    val background = when {
        isSelected -> vigilia.active.copy(alpha = if (vigilia.isDark) 0.30f else 0.18f)
        day.vacation != null -> vigilia.vacation.copy(alpha = if (vigilia.isDark) 0.20f else 0.12f)
        isCompletedDay -> vigilia.success.copy(alpha = if (vigilia.isDark) 0.22f else 0.13f)
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Column(
        modifier = modifier
            .padding(horizontal = 1.dp, vertical = 2.dp)
            .heightIn(min = 100.dp)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .then(
                if (isSelected || isToday) {
                    Modifier.border(
                        2.dp,
                        if (isSelected) vigilia.active else MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.small,
                    )
                } else {
                    Modifier
                },
            )
            .testTag(if (isCompletedDay) "completed-day-${day.date}" else "day-${day.date}")
            .clearAndSetSemantics {
                contentDescription = if (isSelected) "$description, seleccionado para editar" else description
                role = Role.Button
                selected = isSelected
                if (enabled) {
                    onClick(action = {
                        onClick()
                        true
                    })
                } else {
                    disabled()
                }
            }
            .clickable(enabled = enabled, onClick = onClick)
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
                color = vigilia.vacation,
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
    referenceInstant: java.time.Instant,
    editEnabled: Boolean,
    onEditDay: () -> Unit,
    onOpenWeather: (java.util.UUID) -> Unit,
    weatherState: WeatherUiState,
) {
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
                onOpenWeather = if (
                    calendarShift.shift.isEligibleUpcomingWork(referenceInstant, listOfNotNull(day.vacation))
                ) {
                    onOpenWeather
                } else {
                    null
                },
                weatherEnabled = weatherState.preferences.enabled,
                weatherUnit = weatherState.preferences.unitSystem,
                weatherBrief = weatherState.shiftBriefs[calendarShift.shift.id],
                weatherLoading = calendarShift.shift.id in weatherState.loadingBriefIds,
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
        Button(
            onClick = onEditDay,
            enabled = editEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("edit-day-action"),
        ) {
            Text("Editar día")
        }
    }
}

@Composable
private fun ShiftDetail(
    calendarShift: CalendarShift,
    excludedByVacation: Boolean = false,
    onEdit: ((com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit)? = null,
    onAddAnotherShift: (() -> Unit)? = null,
    addAnotherShiftLabel: String = "Agregar otra guardia",
    onDelete: ((java.util.UUID) -> Unit)? = null,
    onOpenExceptions: ((com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit)? = null,
    onOpenWeather: ((java.util.UUID) -> Unit)? = null,
    weatherEnabled: Boolean = false,
    weatherUnit: WeatherUnitSystem = WeatherUnitSystem.CELSIUS,
    weatherBrief: ShiftWeatherBrief? = null,
    weatherLoading: Boolean = false,
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
                OutlinedButton(onClick = { onOpenExceptions(shift) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Informar novedad / notas")
                }
            }
            if (onOpenWeather != null) {
                ShiftWeatherBriefCard(
                    enabled = weatherEnabled,
                    loading = weatherLoading,
                    brief = weatherBrief,
                    unit = weatherUnit,
                    onOpen = { onOpenWeather(shift.id) },
                )
            }
            if (onEdit != null && onDelete != null) {
                OutlinedButton(onClick = { onEdit(shift) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Editar")
                }
                if (onAddAnotherShift != null) {
                    OutlinedButton(onClick = onAddAnotherShift, modifier = Modifier.fillMaxWidth()) {
                        Text(addAnotherShiftLabel)
                    }
                }
                OutlinedButton(onClick = { onDelete(shift.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
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
private fun ShiftWeatherBriefCard(
    enabled: Boolean,
    loading: Boolean,
    brief: ShiftWeatherBrief?,
    unit: WeatherUnitSystem,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        enabled = enabled && brief != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Clima durante la guardia", fontWeight = FontWeight.Bold)
            when {
                !enabled -> Text("Clima está desactivado. Podés activarlo desde el menú Clima.")
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
                    if (loading) {
                        Text(
                            "Actualizando sin ocultar el último pronóstico…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                loading -> Text("Consultando el pronóstico de Córdoba…")
                else -> Text("No hay cobertura meteorológica para todo o parte de este horario.")
            }
        }
    }
}

@Composable
private fun AppearanceScreen(
    contentPadding: PaddingValues,
    appZoom: AppZoom,
    onAppZoomChange: (AppZoom) -> Unit,
    appThemeMode: AppThemeMode,
    onAppThemeModeChange: (AppThemeMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeading("Apariencia", supportingText = stringResource(R.string.appearance_intro))
        SectionCard(
            title = "Tema de MiGuardia",
            supportingText = "Alterná entre claro y oscuro, o dejá que Android elija.",
        ) {
            val alternateMode = if (appThemeMode == AppThemeMode.LIGHT) {
                AppThemeMode.DARK
            } else {
                AppThemeMode.LIGHT
            }
            Text(
                "Tema actual: ${appThemeMode.label}",
                color = MaterialTheme.vigiliaColors.onSurfaceMuted,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAppThemeModeChange(alternateMode) },
                    modifier = Modifier.testTag("theme-mode-toggle"),
                ) {
                    Text(if (alternateMode == AppThemeMode.LIGHT) "Usar modo claro" else "Usar modo oscuro")
                }
                OutlinedButton(
                    onClick = { onAppThemeModeChange(AppThemeMode.SYSTEM) },
                    enabled = appThemeMode != AppThemeMode.SYSTEM,
                    modifier = Modifier
                        .testTag("theme-mode-system")
                        .semantics { selected = appThemeMode == AppThemeMode.SYSTEM },
                ) {
                    Text("Seguir el sistema")
                }
            }
        }
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
