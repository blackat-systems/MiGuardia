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
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
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
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasActions
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasSurfaceHost
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasUiState
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasViewModel
import com.blackatsystems.miguardia.ui.hours.IndependentExtraDetailCard
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadActions
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadContent
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadStage
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadUiState
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadViewModel
import com.blackatsystems.miguardia.ui.management.V2RecurringActions
import com.blackatsystems.miguardia.ui.management.V2RecurringPlanSurfaceHost
import com.blackatsystems.miguardia.ui.management.V2RecurringPlanViewModel
import com.blackatsystems.miguardia.ui.management.V2RecurringUiState
import com.blackatsystems.miguardia.ui.management.V2DayEditEntry
import com.blackatsystems.miguardia.ui.management.V2ShiftEditActions
import com.blackatsystems.miguardia.ui.management.V2ShiftEditSurfaceHost
import com.blackatsystems.miguardia.ui.management.V2ShiftEditUiState
import com.blackatsystems.miguardia.ui.management.V2ShiftEditViewModel
import com.blackatsystems.miguardia.ui.management.V2ShiftActualActions
import com.blackatsystems.miguardia.ui.management.V2ShiftActualDetailCard
import com.blackatsystems.miguardia.ui.management.V2ShiftActualSurfaceHost
import com.blackatsystems.miguardia.ui.management.V2ShiftActualUiState
import com.blackatsystems.miguardia.ui.management.V2ShiftActualViewModel
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
import com.blackatsystems.miguardia.ui.worksetup.V2FirstWorkSetGuide
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupActions
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurfaceHost
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupUiState
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupViewModel
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupStartupScreen
import com.blackatsystems.miguardia.ui.worksetup.previewV2WorkSetupUiState
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
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
    APPEARANCE(R.string.appearance, R.string.drawer_appearance_description, "◐"),
}

private enum class DrawerAction(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
    val glyph: String,
    val testTag: String,
) {
    WORK_SETUP(R.string.work_setup, R.string.drawer_work_setup_description, "◇", "drawer-action-work-setup"),
    HOLIDAYS(R.string.holidays, R.string.drawer_holidays_description, "✦", "drawer-action-holidays"),
    VACATIONS(R.string.vacations, R.string.drawer_vacations_description, "∿", "drawer-action-vacations"),
    NOTIFICATIONS(R.string.notifications, R.string.drawer_notifications_description, "◌", "drawer-action-notifications"),
    WEATHER(R.string.weather, R.string.drawer_weather_description, "☁", "drawer-action-weather"),
}

private val WorkDrawerActions = listOf(
    DrawerAction.WORK_SETUP,
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
    v2ManualShiftLoadViewModel: V2ManualShiftLoadViewModel,
    v2ShiftEditViewModel: V2ShiftEditViewModel,
    v2ShiftActualViewModel: V2ShiftActualViewModel,
    v2RecurringPlanViewModel: V2RecurringPlanViewModel,
    exceptionsViewModel: ExceptionsViewModel,
    vacationViewModel: VacationViewModel,
    photosViewModel: PhotosViewModel,
    notificationViewModel: NotificationViewModel,
    weatherViewModel: WeatherViewModel,
    workSetupViewModel: WorkSetupViewModel,
    hoursAndExtrasViewModel: HoursAndExtrasViewModel,
    modifier: Modifier = Modifier,
    calendarNavigationRequest: Int = 0,
    appZoom: AppZoom = AppZoom.STANDARD,
    onAppZoomChange: (AppZoom) -> Unit = {},
    appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onAppThemeModeChange: (AppThemeMode) -> Unit = {},
) {
    val calendarState by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val nextEventState by nextEventViewModel.uiState.collectAsStateWithLifecycle()
    val v2ManualShiftLoadState by v2ManualShiftLoadViewModel.uiState.collectAsStateWithLifecycle()
    val v2ShiftEditState by v2ShiftEditViewModel.uiState.collectAsStateWithLifecycle()
    val v2ShiftActualState by v2ShiftActualViewModel.uiState.collectAsStateWithLifecycle()
    val v2RecurringState by v2RecurringPlanViewModel.uiState.collectAsStateWithLifecycle()
    val exceptionsState by exceptionsViewModel.uiState.collectAsStateWithLifecycle()
    val vacationState by vacationViewModel.uiState.collectAsStateWithLifecycle()
    val photosState by photosViewModel.uiState.collectAsStateWithLifecycle()
    val notificationState by notificationViewModel.uiState.collectAsStateWithLifecycle()
    val weatherState by weatherViewModel.uiState.collectAsStateWithLifecycle()
    val workSetupState by workSetupViewModel.uiState.collectAsStateWithLifecycle()
    val hoursAndExtrasState by hoursAndExtrasViewModel.uiState.collectAsStateWithLifecycle()
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
        v2ManualShiftLoadState = v2ManualShiftLoadState,
        v2ManualShiftLoadActions = V2ManualShiftLoadActions.from(v2ManualShiftLoadViewModel),
        v2ShiftEditState = v2ShiftEditState,
        v2ShiftEditActions = V2ShiftEditActions.from(v2ShiftEditViewModel),
        v2ShiftActualState = v2ShiftActualState,
        v2ShiftActualActions = V2ShiftActualActions.from(v2ShiftActualViewModel),
        v2RecurringState = v2RecurringState,
        v2RecurringActions = V2RecurringActions.from(v2RecurringPlanViewModel),
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
        workSetupState = workSetupState,
        workSetupActions = WorkSetupActions.from(workSetupViewModel),
        hoursAndExtrasState = hoursAndExtrasState,
        hoursAndExtrasActions = HoursAndExtrasActions.from(hoursAndExtrasViewModel),
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
    v2ManualShiftLoadState: V2ManualShiftLoadUiState = V2ManualShiftLoadUiState(),
    v2ManualShiftLoadActions: V2ManualShiftLoadActions = V2ManualShiftLoadActions(),
    v2ShiftEditState: V2ShiftEditUiState = V2ShiftEditUiState(),
    v2ShiftEditActions: V2ShiftEditActions = V2ShiftEditActions(),
    v2ShiftActualState: V2ShiftActualUiState = V2ShiftActualUiState(),
    v2ShiftActualActions: V2ShiftActualActions = V2ShiftActualActions(),
    v2RecurringState: V2RecurringUiState = V2RecurringUiState(),
    v2RecurringActions: V2RecurringActions = V2RecurringActions(),
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
    workSetupState: WorkSetupUiState = previewV2WorkSetupUiState(),
    workSetupActions: WorkSetupActions = WorkSetupActions(),
    hoursAndExtrasState: HoursAndExtrasUiState = HoursAndExtrasUiState(),
    hoursAndExtrasActions: HoursAndExtrasActions = HoursAndExtrasActions(),
    calendarNavigationRequest: Int = 0,
    appZoom: AppZoom = AppZoom.STANDARD,
    onAppZoomChange: (AppZoom) -> Unit = {},
    appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onAppThemeModeChange: (AppThemeMode) -> Unit = {},
) {
    val preserveV2WriteSurface =
        (v2ShiftEditState.isBlocking && v2ShiftEditState.isSaving) ||
            (v2RecurringState.isBlocking && v2RecurringState.isSaving) ||
            (v2ShiftActualState.isBlocking && v2ShiftActualState.isSaving) ||
            (hoursAndExtrasState.isBlocking && hoursAndExtrasState.isSaving)
    if (
        !preserveV2WriteSurface &&
        (
            workSetupState.rootState == WorkSetupState.Loading ||
                workSetupState.rootState == WorkSetupState.LoadError ||
                workSetupState.rootState == WorkSetupState.FreshInstall
            )
    ) {
        WorkSetupStartupScreen(workSetupState, workSetupActions, modifier)
        return
    }
    val needsFirstWorkSet = workSetupState.rootState is WorkSetupState.V2NeedsFirstSet
    val v2ReadyState = workSetupState.rootState as? WorkSetupState.V2Ready
    val v2ManualLoadActive = v2ReadyState != null &&
        v2ManualShiftLoadState.isActive &&
        v2ReadyState.timelineId == v2ManualShiftLoadState.timelineId
    val v2ShiftEditActive = (v2ShiftEditState.isBlocking && v2ShiftEditState.isSaving) || (
        v2ReadyState != null &&
            v2ShiftEditState.isBlocking &&
            v2ReadyState.timelineId == v2ShiftEditState.timelineId
        )
    val v2RecurringActive = (v2RecurringState.isBlocking && v2RecurringState.isSaving) || (
        v2ReadyState != null &&
            v2RecurringState.isBlocking &&
            v2ReadyState.timelineId == v2RecurringState.timelineId
        )
    val v2ShiftActualActive = v2ShiftActualState.isBlocking
    val hoursAndExtrasActive = hoursAndExtrasState.isBlocking
    var destination by rememberSaveable { androidx.compose.runtime.mutableStateOf(MainDestination.CALENDAR) }
    val displayedCalendarState = if (
        !v2ManualLoadActive && calendarState.interactionMode == CalendarInteractionMode.EDIT
    ) {
        calendarState.copy(
            interactionMode = CalendarInteractionMode.VIEW,
            editSelectedDates = emptySet(),
            editSelectionConfirmed = false,
        )
    } else {
        calendarState
    }
    val drawerState = remember { DrawerState(initialValue = DrawerValue.Closed) }
    val coroutineScope = rememberCoroutineScope()
    val selectedDay = if (displayedCalendarState.interactionMode == CalendarInteractionMode.VIEW) {
        displayedCalendarState.detailDate?.let { selectedDate ->
        displayedCalendarState.days.firstOrNull { it.date == selectedDate }
        }
    } else {
        null
    }
    val hasBlockingSurface = exceptionsState.surface != ExceptionsSurface.NONE ||
        vacationState.surface != VacationSurface.NONE ||
        photosState.surface != PhotosSurface.NONE ||
        notificationState.surface != NotificationSurface.NONE ||
        weatherState.surface != WeatherSurface.NONE ||
        workSetupState.surface != WorkSetupSurface.NONE ||
        v2ManualLoadActive ||
        v2ShiftEditActive ||
        v2RecurringActive ||
        v2ShiftActualActive ||
        hoursAndExtrasActive
    val canOpenDrawer = !hasBlockingSurface && selectedDay == null
    LaunchedEffect(calendarNavigationRequest) {
        if (calendarNavigationRequest > 0) {
            drawerState.snapTo(DrawerValue.Closed)
            destination = MainDestination.CALENDAR
        }
    }
    LaunchedEffect(workSetupState.rootState) {
        v2ShiftEditActions.resume(workSetupState.rootState)
        v2RecurringActions.resume(workSetupState.rootState)
        v2ShiftActualActions.resume(workSetupState.rootState)
    }
    LaunchedEffect(
        v2ManualShiftLoadState.isActive,
        v2ManualShiftLoadState.timelineId,
        v2ManualShiftLoadState.isLoading,
        v2ManualShiftLoadState.isSaving,
        v2ReadyState?.timelineId,
    ) {
        if (
            v2ManualShiftLoadState.isActive &&
            !v2ManualLoadActive &&
            !v2ManualShiftLoadState.isLoading &&
            !v2ManualShiftLoadState.isSaving
        ) {
            v2ManualShiftLoadActions.discardIncompatible()
        }
    }
    LaunchedEffect(v2ManualLoadActive, calendarState.interactionMode) {
        if (v2ManualLoadActive && calendarState.interactionMode == CalendarInteractionMode.VIEW) {
            onEnterCalendarEditMode(null)
        } else if (
            !v2ManualLoadActive &&
            calendarState.interactionMode == CalendarInteractionMode.EDIT
        ) {
            onFinishCalendarEditMode()
        }
    }
    LaunchedEffect(v2ManualShiftLoadState.successSequence) {
        val successSequence = v2ManualShiftLoadState.successSequence
        if (successSequence > 0) {
            v2ManualShiftLoadActions.consumeSuccess(successSequence)
            destination = MainDestination.CALENDAR
        }
    }
    LaunchedEffect(v2ShiftEditState.successSequence) {
        val successSequence = v2ShiftEditState.successSequence
        if (successSequence > 0) {
            v2ShiftEditActions.consumeSuccess(successSequence)
            onDismissDate()
            destination = MainDestination.CALENDAR
        }
    }
    LaunchedEffect(v2RecurringState.successSequence) {
        val successSequence = v2RecurringState.successSequence
        if (successSequence > 0) {
            v2RecurringActions.consumeSuccess(successSequence)
            onDismissDate()
            destination = MainDestination.CALENDAR
        }
    }
    LaunchedEffect(v2ShiftActualState.successSequence) {
        val successSequence = v2ShiftActualState.successSequence
        if (successSequence > 0) {
            v2ShiftActualActions.consumeSuccess(successSequence)
            destination = MainDestination.CALENDAR
        }
    }
    LaunchedEffect(hoursAndExtrasState.successSequence) {
        val successSequence = hoursAndExtrasState.successSequence
        if (successSequence > 0) {
            hoursAndExtrasActions.consumeSuccess(successSequence)
            destination = MainDestination.CALENDAR
        }
    }
    LaunchedEffect(v2ReadyState?.timelineId, selectedDay?.date, v2ShiftEditActive) {
        val ready = v2ReadyState
        val date = selectedDay?.date
        if (ready != null && date != null && !v2ShiftEditActive) {
            v2ShiftEditActions.inspectDay(ready, date)
        } else if (!v2ShiftEditActive) {
            v2ShiftEditActions.clearInspection()
        }
    }
    LaunchedEffect(
        workSetupState.rootState,
        selectedDay?.date,
        selectedDay?.shifts?.map { it.shift.id },
        calendarState.referenceInstant,
        v2ShiftActualActive,
    ) {
        val date = selectedDay?.date
        if (date != null && !v2ShiftActualActive) {
            v2ShiftActualActions.inspectDay(
                workSetupState.rootState,
                date,
                selectedDay.shifts.map { it.shift },
                calendarState.referenceInstant,
            )
        } else if (!v2ShiftActualActive) {
            v2ShiftActualActions.clearInspection()
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
                DrawerAction.WORK_SETUP -> {
                    destination = MainDestination.CALENDAR
                    workSetupActions.openOverview()
                }
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
                    state = displayedCalendarState,
                    nextEventState = nextEventState,
                    contentPadding = innerPadding,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onToday = onToday,
                    onSelectDate = onSelectDate,
                    onEditSelectionChange = onEditSelectionChange,
                    onConfirmEditSelection = onConfirmEditSelection,
                    onResumeEditSelection = onResumeEditSelection,
                    onRetry = onRetry,
                    onNextEventRetry = onNextEventRetry,
                    onFinishEditMode = onFinishCalendarEditMode,
                    v2ManualShiftLoadState = v2ManualShiftLoadState,
                    v2ManualShiftLoadActions = v2ManualShiftLoadActions,
                    v2ShiftEditState = v2ShiftEditState,
                    v2ShiftEditActions = v2ShiftEditActions,
                    v2ShiftActualState = v2ShiftActualState,
                    v2ShiftActualActions = v2ShiftActualActions,
                    v2RecurringState = v2RecurringState,
                    v2RecurringActions = v2RecurringActions,
                    hoursAndExtrasState = hoursAndExtrasState,
                    hoursAndExtrasActions = hoursAndExtrasActions,
                    onOpenPhotos = { photosActions.open(calendarState.visibleMonth) },
                    appZoom = appZoom,
                    needsFirstWorkSet = needsFirstWorkSet,
                    onOpenWorkSetup = workSetupActions.openOverview,
                    onCreateFirstWorkSet = workSetupActions.openFirstWorkSet,
                    onStartV2ManualLoad = {
                        v2ManualShiftLoadActions.start(workSetupState.rootState)
                        onEnterCalendarEditMode(null)
                    },
                    onStartV2Recurring = {
                        v2RecurringActions.openCreate(workSetupState.rootState)
                    },
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
        enabled = destination == MainDestination.CALENDAR &&
            v2ManualLoadActive,
        onBack = {
            if (v2ManualShiftLoadState.isLoading || v2ManualShiftLoadState.isSaving) {
                Unit
            } else if (
                v2ManualShiftLoadState.stage == V2ManualShiftLoadStage.SELECT_DATES &&
                !calendarState.editSelectionConfirmed
            ) {
                v2ManualShiftLoadActions.cancel()
                onFinishCalendarEditMode()
            } else {
                v2ManualShiftLoadActions.backToDateSelection()
                onResumeEditSelection()
            }
        },
    )
    BackHandler(
        enabled = destination != MainDestination.CALENDAR && !hasBlockingSurface,
        onBack = { destination = MainDestination.CALENDAR },
    )
    BackHandler(
        enabled = destination == MainDestination.CALENDAR &&
            displayedCalendarState.interactionMode == CalendarInteractionMode.EDIT &&
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
    val dayDetailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (
        selectedDay != null &&
        !v2ShiftEditActive &&
        !v2RecurringActive &&
        !v2ShiftActualActive &&
        !hoursAndExtrasActive
    ) {
        ModalBottomSheet(
            onDismissRequest = onDismissDate,
            sheetState = dayDetailSheetState,
            modifier = Modifier.padding(bottom = navigationBarBottomPadding + 16.dp),
        ) {
            DayDetailSheet(
                day = selectedDay,
                referenceInstant = calendarState.referenceInstant,
                onEditDay = v2ReadyState?.let { v2ShiftEditActions.beginDayEditing },
                onOpenNotes = { shift ->
                    onDismissDate()
                    exceptionsActions.openNotes(shift)
                },
                onOpenWeather = { shiftId ->
                    onDismissDate()
                    weatherActions.openShift(shiftId)
                },
                weatherState = weatherState,
                v2ShiftEditState = v2ShiftEditState.takeIf { v2ReadyState != null },
                onRetryV2Inspection = v2ShiftEditActions.retryInspection,
                v2ShiftActualState = v2ShiftActualState,
                v2ShiftActualActions = v2ShiftActualActions,
                independentExtras = hoursAndExtrasState.extrasOn(selectedDay.date),
                canRegisterIndependentExtra = v2ReadyState != null &&
                    hoursAndExtrasState.canRegisterIndependentExtraOn(selectedDay.date),
                onRegisterIndependentExtra = {
                    hoursAndExtrasActions.openCreateExtra(selectedDay.date)
                },
                onCorrectIndependentExtra = hoursAndExtrasActions.openCorrectExtra,
                onDeleteIndependentExtra = hoursAndExtrasActions.requestDelete,
            )
        }
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
    if (workSetupState.surface != WorkSetupSurface.NONE) {
        WorkSetupSurfaceHost(
            workSetupState,
            workSetupActions.copy(
                openRecurringPlans = {
                    workSetupActions.requestBack()
                    v2RecurringActions.openPlans(workSetupState.rootState)
                },
                openExtraClasses = {
                    workSetupActions.requestBack()
                    v2ShiftActualActions.openCatalog(workSetupState.rootState)
                },
                openHoursProgress = {
                    workSetupActions.requestBack()
                    hoursAndExtrasActions.openProgress()
                },
            ),
        )
    }
    if (hoursAndExtrasActive) {
        HoursAndExtrasSurfaceHost(
            state = hoursAndExtrasState,
            actions = hoursAndExtrasActions,
            onOpenExtraClassCatalog = {
                v2ShiftActualActions.openCatalog(workSetupState.rootState)
            },
        )
    }
    if (v2ShiftEditActive) {
        V2ShiftEditSurfaceHost(
            v2ShiftEditState,
            v2ShiftEditActions.copy(
                changeSeriesFrom = { planId, date ->
                    onDismissDate()
                    v2RecurringActions.changeFrom(planId, date)
                },
                finalizeSeriesFrom = { planId, date ->
                    onDismissDate()
                    v2RecurringActions.finalizeFrom(planId, date)
                },
                correctActual = { shiftId ->
                    val row = v2ShiftEditState.dayRows.firstOrNull { it.shift.id == shiftId }
                    val opened = row != null && v2ShiftActualActions.begin(
                        shiftId,
                        row.ordinal,
                        row.total,
                        row.shift.localStartDate,
                    )
                    if (opened) {
                        v2ShiftEditActions.handoffToActual()
                    } else {
                        v2ShiftEditActions.reportActualHandoffUnavailable()
                    }
                },
                returnActualToPlanned = { shiftId ->
                    if (v2ShiftActualActions.requestReturnToPlanned(shiftId)) {
                        v2ShiftEditActions.handoffToActual()
                    } else {
                        v2ShiftEditActions.reportActualHandoffUnavailable()
                    }
                },
            ),
        )
    }
    if (v2RecurringActive) {
        V2RecurringPlanSurfaceHost(v2RecurringState, v2RecurringActions)
    }
    if (v2ShiftActualActive) {
        V2ShiftActualSurfaceHost(v2ShiftActualState, v2ShiftActualActions)
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
    onFinishEditMode: () -> Unit,
    onOpenPhotos: () -> Unit,
    v2ManualShiftLoadState: V2ManualShiftLoadUiState,
    v2ManualShiftLoadActions: V2ManualShiftLoadActions,
    v2ShiftEditState: V2ShiftEditUiState,
    v2ShiftEditActions: V2ShiftEditActions,
    v2ShiftActualState: V2ShiftActualUiState,
    v2ShiftActualActions: V2ShiftActualActions,
    v2RecurringState: V2RecurringUiState,
    v2RecurringActions: V2RecurringActions,
    hoursAndExtrasState: HoursAndExtrasUiState,
    hoursAndExtrasActions: HoursAndExtrasActions,
    appZoom: AppZoom,
    needsFirstWorkSet: Boolean,
    onOpenWorkSetup: () -> Unit,
    onCreateFirstWorkSet: () -> Unit,
    onStartV2ManualLoad: () -> Unit,
    onStartV2Recurring: () -> Unit,
) {
    val today = state.referenceInstant.atZone(AppDefaults.zoneId()).toLocalDate()
    val verticalScrollState = rememberScrollState()
    var pendingMonthChange by rememberSaveable { mutableStateOf<String?>(null) }
    val v2ManualLoadOpen = v2ManualShiftLoadState.isActive
    val v2DateSelectionOpen = v2ManualLoadOpen &&
        v2ManualShiftLoadState.stage == V2ManualShiftLoadStage.SELECT_DATES
    val v2CalendarSelectionReady = state.loadState == CalendarLoadState.CONTENT &&
        state.editSelectedDates.all { selectedDate -> state.days.any { day -> day.date == selectedDate } }
    val v2DateSelectionInteractive = v2DateSelectionOpen &&
        v2CalendarSelectionReady &&
        !state.editSelectionConfirmed &&
        !v2ManualShiftLoadState.isLoading &&
        !v2ManualShiftLoadState.isSaving
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
        message = v2ShiftEditState.infoMessage
            ?: v2ShiftActualState.infoMessage
            ?: v2ManualShiftLoadState.infoMessage
            ?: v2RecurringState.infoMessage
            ?: hoursAndExtrasState.message,
        onDismiss = if (v2ShiftEditState.infoMessage != null) {
            v2ShiftEditActions.clearMessage
        } else if (v2ShiftActualState.infoMessage != null) {
            v2ShiftActualActions.clearMessage
        } else if (v2ManualShiftLoadState.infoMessage != null) {
            v2ManualShiftLoadActions.clearMessage
        } else if (v2RecurringState.infoMessage != null) {
            v2RecurringActions.clearMessage
        } else if (hoursAndExtrasState.message != null) {
            hoursAndExtrasActions.clearMessage
        } else v2ManualShiftLoadActions.clearMessage,
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
                        text = if (v2ManualLoadOpen) "Cargando jornadas" else "Editando calendario",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.vigiliaColors.active,
                        modifier = Modifier.semantics {
                            contentDescription = if (v2ManualLoadOpen) {
                                "Cargando jornadas. Elegí los días y revisá antes de guardar."
                            } else {
                                "Editando calendario. Las acciones para modificar están habilitadas."
                            }
                        },
                    )
                }
                MonthControls(
                    visibleMonth = state.visibleMonth,
                    onPrevious = previousMonth,
                    onNext = nextMonth,
                    onToday = currentMonth,
                    onPhotos = onOpenPhotos,
                    showPhotos = state.interactionMode == CalendarInteractionMode.VIEW,
                    appZoom = appZoom,
                    navigationEnabled = !v2ManualLoadOpen || v2DateSelectionInteractive,
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
                    val independentExtraCounts = hoursAndExtrasState.source
                        ?.independentExtras
                        .orEmpty()
                        .groupingBy(IndependentExtraWorkRecord::ownerLocalDate)
                        .eachCount()
                    CalendarGridViewport(
                        month = state.visibleMonth,
                        days = state.days,
                        independentExtraCounts = independentExtraCounts,
                        today = today,
                        onPreviousMonth = previousMonth,
                        onNextMonth = nextMonth,
                        onSelectDate = onSelectDate,
                        interactionMode = state.interactionMode,
                        selectedDates = state.editSelectedDates,
                        onEditSelectionChange = onEditSelectionChange,
                        selectionEnabled = !v2ManualLoadOpen || v2DateSelectionInteractive,
                        selectionConfirmed = state.editSelectionConfirmed,
                        monthSwipeEnabled = !v2ManualLoadOpen || v2DateSelectionInteractive,
                        selectedDateDescription = if (v2ManualLoadOpen) {
                            "seleccionado para cargar jornadas"
                        } else {
                            "seleccionado para editar"
                        },
                        shiftDescription = "jornada",
                        appZoom = appZoom,
                    )
                }

                if (needsFirstWorkSet && state.interactionMode == CalendarInteractionMode.VIEW) {
                    V2FirstWorkSetGuide(onCreateFirstPlace = onCreateFirstWorkSet)
                }

                if (state.interactionMode == CalendarInteractionMode.EDIT) {
                    if (v2ManualLoadOpen) {
                        V2ManualShiftLoadContent(
                            state = v2ManualShiftLoadState,
                            calendarSelectedDates = state.editSelectedDates,
                            calendarSelectionConfirmed = state.editSelectionConfirmed,
                            calendarContentReady = v2CalendarSelectionReady,
                            actions = v2ManualShiftLoadActions,
                            onConfirmCalendarSelection = {
                                if (v2CalendarSelectionReady) {
                                    onConfirmEditSelection()
                                    v2ManualShiftLoadActions.confirmDates(state.editSelectedDates)
                                }
                            },
                            onModifyCalendarSelection = {
                                v2ManualShiftLoadActions.backToDateSelection()
                                onResumeEditSelection()
                            },
                        )
                    }
                }

                if (state.interactionMode == CalendarInteractionMode.EDIT) {
                        OutlinedButton(
                            onClick = {
                                if (v2ManualLoadOpen) v2ManualShiftLoadActions.cancel()
                                onFinishEditMode()
                            },
                            enabled = !v2ManualShiftLoadState.isLoading && !v2ManualShiftLoadState.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    v2ManualLoadOpen -> "Salir de la carga"
                                    else -> "Salir de edición"
                                },
                            )
                        }
                    } else {
                        if (!needsFirstWorkSet) {
                            Button(
                                onClick = onStartV2ManualLoad,
                                enabled = state.loadState == CalendarLoadState.CONTENT,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .testTag("calendar-v2-load-shifts"),
                            ) {
                                Text("Cargar jornadas")
                            }
                            OutlinedButton(
                                onClick = onStartV2Recurring,
                                enabled = state.loadState == CalendarLoadState.CONTENT,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .testTag("calendar-v2-repeat-shifts"),
                            ) {
                                Text("Repetir jornadas")
                            }
                            OutlinedButton(
                                onClick = onOpenWorkSetup,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("calendar-work-setup-action"),
                            ) {
                                Text("Mi forma de trabajar")
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
private fun CalendarGridViewport(
    month: YearMonth,
    days: List<CalendarDay>,
    independentExtraCounts: Map<LocalDate, Int>,
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
    selectedDateDescription: String,
    shiftDescription: String,
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
                    independentExtraCounts = independentExtraCounts,
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
                    selectedDateDescription = selectedDateDescription,
                    shiftDescription = shiftDescription,
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
    independentExtraCounts: Map<LocalDate, Int>,
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
    selectedDateDescription: String,
    shiftDescription: String,
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
                            independentExtraCount = independentExtraCounts[day.date] ?: 0,
                            isToday = day.date == today,
                            isSelected = day.date in selectedDates,
                            enabled = interactionMode == CalendarInteractionMode.VIEW ||
                                (selectionEnabled && !selectionConfirmed),
                            selectedDateDescription = selectedDateDescription,
                            shiftDescription = shiftDescription,
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
    independentExtraCount: Int,
    isToday: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    selectedDateDescription: String,
    shiftDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = day.accessibilityDescription(
        isToday,
        shiftDescription,
        independentExtraCount,
    )
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
                contentDescription = if (isSelected) "$description, $selectedDateDescription" else description
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
        if (day.vacation != null) {
            Text(
                text = "V",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = vigilia.vacation,
            )
        } else if (day.shifts.size > 1) {
            AutoSizeSingleLineText(
                text = "${day.shifts.size} turnos",
                maximum = 12.sp,
                minimum = 7.sp,
                fontWeight = FontWeight.Bold,
            )
        } else {
            val firstShift = day.shifts.singleOrNull()
            if (firstShift != null) {
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
            }
        }
        if (independentExtraCount > 0) {
            AutoSizeSingleLineText(
                text = if (independentExtraCount == 1) "Extra" else "$independentExtraCount extras",
                maximum = 10.sp,
                minimum = 6.sp,
                fontWeight = FontWeight.Bold,
            )
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
    onEditDay: (() -> Unit)?,
    onOpenNotes: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
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
        if (
            day.shifts.isEmpty() &&
            independentExtras.isEmpty() &&
            day.explicitStatus == null &&
            !day.hasMedicalLeave
        ) {
            Text(stringResource(R.string.undefined_implicit_detail))
        }
        day.shifts.forEachIndexed { index, calendarShift ->
            if (index > 0) HorizontalDivider()
            ShiftDetail(
                calendarShift = calendarShift,
                excludedByVacation = day.vacation != null && calendarShift.shift.status == ShiftStatus.PLANNED,
                onOpenNotes = onOpenNotes,
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
            V2ShiftActualDetailCard(
                shift = calendarShift.shift,
                ordinal = index + 1,
                count = day.shifts.size,
                ownerDate = day.date,
                rowState = v2ShiftActualState.rows[calendarShift.shift.id],
                actions = v2ShiftActualActions,
            )
        }
        independentExtras.forEach { record ->
            HorizontalDivider()
            IndependentExtraDetailCard(
                record = record,
                onCorrect = { onCorrectIndependentExtra(record) },
                onDelete = { onDeleteIndependentExtra(record) },
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
        if (v2ShiftEditState != null && onEditDay != null) {
            V2DayEditEntry(
                state = v2ShiftEditState,
                date = day.date,
                onBegin = onEditDay,
                onRetry = onRetryV2Inspection,
            )
        }
        if (canRegisterIndependentExtra) {
            OutlinedButton(
                onClick = onRegisterIndependentExtra,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register-independent-extra-${day.date}"),
            ) {
                Text("Registrar trabajo extra")
            }
        }
    }
}

@Composable
private fun ShiftDetail(
    calendarShift: CalendarShift,
    excludedByVacation: Boolean = false,
    onOpenNotes: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
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
            OutlinedButton(onClick = { onOpenNotes(shift) }, modifier = Modifier.fillMaxWidth()) {
                Text("Notas")
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

private fun CalendarDay.accessibilityDescription(
    isToday: Boolean,
    shiftDescription: String,
    independentExtraCount: Int,
): String {
    val parts = mutableListOf(date.fullDisplayName())
    if (isToday) parts += "hoy"
    shifts.forEach { calendarShift ->
        parts += buildString {
            append(shiftDescription)
            append(" ")
            append(calendarShift.shift.objectiveAbbreviationSnapshot)
            append(" de ")
            append(calendarShift.shift.startTimeSnapshot.format(ShiftTimeFormatter))
            append(" a ")
            append(calendarShift.shift.endTimeSnapshot.format(ShiftTimeFormatter))
            append(", ")
            append(calendarShift.temporalStatus.accessibilityLabel())
        }
    }
    if (independentExtraCount > 0) {
        parts += if (independentExtraCount == 1) {
            "un trabajo extra independiente"
        } else {
            "$independentExtraCount trabajos extra independientes"
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
