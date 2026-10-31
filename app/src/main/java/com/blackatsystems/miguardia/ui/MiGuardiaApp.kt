package com.blackatsystems.miguardia.ui

import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.backup.BackupActions
import com.blackatsystems.miguardia.backup.BackupSurfaceHost
import com.blackatsystems.miguardia.backup.BackupUiState
import com.blackatsystems.miguardia.backup.BackupViewModel
import com.blackatsystems.miguardia.security.AccessLockConfiguration
import com.blackatsystems.miguardia.security.AccessLockSettingsScreen
import com.blackatsystems.miguardia.security.AccessLockState
import com.blackatsystems.miguardia.security.AccessLockTimeout
import com.blackatsystems.miguardia.security.DeviceAuthenticationCapability
import com.blackatsystems.miguardia.core.domain.calendar.CalendarShift
import com.blackatsystems.miguardia.core.domain.nextevent.isEligibleForWeather
import com.blackatsystems.miguardia.ui.availability.AvailabilityActions
import com.blackatsystems.miguardia.ui.availability.AvailabilitySurfaceHost
import com.blackatsystems.miguardia.ui.availability.AvailabilityUiState
import com.blackatsystems.miguardia.ui.availability.AvailabilityLoadState
import com.blackatsystems.miguardia.ui.availability.AvailabilityViewModel
import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarSelectionPurpose
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.calendar.CalendarViewportInsets
import com.blackatsystems.miguardia.ui.calendar.calendarViewportInsets
import com.blackatsystems.miguardia.ui.calendar.calendarUsesSideRail
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasActions
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasSurfaceHost
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasUiState
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasLoadState
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasViewModel
import com.blackatsystems.miguardia.ui.help.HelpActions
import com.blackatsystems.miguardia.ui.help.HelpAnchor
import com.blackatsystems.miguardia.ui.help.HelpAnchorRegistry
import com.blackatsystems.miguardia.ui.help.HelpDecisionScreen
import com.blackatsystems.miguardia.ui.help.HelpNavigationTarget
import com.blackatsystems.miguardia.ui.help.HelpScreen
import com.blackatsystems.miguardia.ui.help.HelpSessionStage
import com.blackatsystems.miguardia.ui.help.HelpTourOverlay
import com.blackatsystems.miguardia.ui.help.HelpTourStep
import com.blackatsystems.miguardia.ui.help.HelpUiState
import com.blackatsystems.miguardia.ui.help.HelpViewModel
import com.blackatsystems.miguardia.ui.help.helpAnchor
import com.blackatsystems.miguardia.ui.management.belongsTo
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadActions
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadStage
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadUiState
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadViewModel
import com.blackatsystems.miguardia.ui.management.V2RecurringActions
import com.blackatsystems.miguardia.ui.management.V2RecurringPlanSurfaceHost
import com.blackatsystems.miguardia.ui.management.V2RecurringPlanViewModel
import com.blackatsystems.miguardia.ui.management.V2RecurringUiState
import com.blackatsystems.miguardia.ui.management.V2ShiftEditActions
import com.blackatsystems.miguardia.ui.management.V2ShiftEditSurfaceHost
import com.blackatsystems.miguardia.ui.management.V2ShiftEditUiState
import com.blackatsystems.miguardia.ui.management.V2ShiftEditViewModel
import com.blackatsystems.miguardia.ui.management.V2ShiftActualActions
import com.blackatsystems.miguardia.ui.management.V2ShiftActualSurfaceHost
import com.blackatsystems.miguardia.ui.management.V2ShiftActualUiState
import com.blackatsystems.miguardia.ui.management.V2ShiftActualViewModel
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
import com.blackatsystems.miguardia.ui.summary.SummaryActions
import com.blackatsystems.miguardia.ui.summary.SummaryScreen
import com.blackatsystems.miguardia.reports.ReportsActions
import com.blackatsystems.miguardia.reports.ReportsSurfaceHost
import com.blackatsystems.miguardia.reports.ReportsUiState
import com.blackatsystems.miguardia.reports.ReportsViewModel
import com.blackatsystems.miguardia.ui.summary.SummaryUiState
import com.blackatsystems.miguardia.ui.summary.SummaryViewModel
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
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import com.blackatsystems.miguardia.ui.weather.WeatherActions
import com.blackatsystems.miguardia.ui.weather.WeatherSurface
import com.blackatsystems.miguardia.ui.weather.WeatherSurfaceHost
import com.blackatsystems.miguardia.ui.widget.WidgetActions
import com.blackatsystems.miguardia.ui.widget.WidgetSurface
import com.blackatsystems.miguardia.ui.widget.WidgetSurfaceHost
import com.blackatsystems.miguardia.ui.widget.WidgetUiState
import com.blackatsystems.miguardia.ui.widget.WidgetViewModel
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import com.blackatsystems.miguardia.ui.weather.WeatherViewModel
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupActions
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurfaceHost
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupUiState
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupViewModel
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupStartupScreen
import com.blackatsystems.miguardia.ui.worksetup.previewV2WorkSetupUiState
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

private enum class MainDestination(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
    val glyph: String,
) {
    CALENDAR(R.string.calendar, R.string.drawer_calendar_description, "▦"),
    SUMMARY(R.string.summary, R.string.drawer_summary_description, "≡"),
    ACCESS_LOCK(R.string.access_lock, R.string.drawer_access_lock_description, "▣"),
    APPEARANCE(R.string.appearance, R.string.drawer_appearance_description, "◐"),
    HELP(R.string.help, R.string.drawer_help_description, "?"),
}

internal enum class CalendarSelectionBackOwner {
    NONE,
    GENERAL,
    MANUAL_LOAD,
    HOLIDAY,
}

internal fun calendarSelectionBackOwner(
    calendarVisible: Boolean,
    interactionMode: CalendarInteractionMode,
    selectionPurpose: CalendarSelectionPurpose?,
    manualLoadActive: Boolean,
    holidaySelectionActive: Boolean,
    selectedDayOpen: Boolean,
    generalBackBlocked: Boolean,
): CalendarSelectionBackOwner = when {
    !calendarVisible -> CalendarSelectionBackOwner.NONE
    manualLoadActive && selectionPurpose == CalendarSelectionPurpose.MANUAL_LOAD ->
        CalendarSelectionBackOwner.MANUAL_LOAD
    holidaySelectionActive && selectionPurpose == CalendarSelectionPurpose.HOLIDAY ->
        CalendarSelectionBackOwner.HOLIDAY
    manualLoadActive -> CalendarSelectionBackOwner.MANUAL_LOAD
    holidaySelectionActive -> CalendarSelectionBackOwner.HOLIDAY
    interactionMode == CalendarInteractionMode.EDIT &&
        selectionPurpose == CalendarSelectionPurpose.GENERAL &&
        !selectedDayOpen &&
        !generalBackBlocked -> CalendarSelectionBackOwner.GENERAL
    else -> CalendarSelectionBackOwner.NONE
}

private enum class DrawerAction(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
    val glyph: String,
    val testTag: String,
) {
    CALENDAR_TODAY(R.string.drawer_calendar_today, R.string.drawer_calendar_today_description, "●", "drawer-action-calendar-today"),
    CALENDAR_PHOTOS(R.string.drawer_calendar_photos, R.string.drawer_calendar_photos_description, "▧", "drawer-action-calendar-photos"),
    CALENDAR_LOAD(R.string.drawer_calendar_load, R.string.drawer_calendar_load_description, "+", "drawer-action-calendar-load"),
    CALENDAR_SELECT(R.string.drawer_calendar_select, R.string.drawer_calendar_select_description, "✓", "drawer-action-calendar-select"),
    CALENDAR_REPEAT(R.string.drawer_calendar_repeat, R.string.drawer_calendar_repeat_description, "↻", "drawer-action-calendar-repeat"),
    WORK_SETUP(R.string.work_setup, R.string.drawer_work_setup_description, "◇", "drawer-action-work-setup"),
    HOLIDAYS(R.string.holidays, R.string.drawer_holidays_description, "✦", "drawer-action-holidays"),
    VACATIONS(R.string.vacations, R.string.drawer_vacations_description, "∿", "drawer-action-vacations"),
    NOTIFICATIONS(R.string.notifications, R.string.drawer_notifications_description, "◌", "drawer-action-notifications"),
    WEATHER(R.string.weather, R.string.drawer_weather_description, "☁", "drawer-action-weather"),
    WIDGET(R.string.widget_home, R.string.drawer_widget_description, "▣", "drawer-action-widget"),
    BACKUPS(R.string.backups, R.string.drawer_backups_description, "⇄", "drawer-action-backups"),
    SUPPORT(R.string.support_miguardia, R.string.drawer_support_description, "♡", "drawer-action-support"),
    UPDATES(R.string.updates_title, R.string.updates_description, "↓", "drawer-action-updates"),
}

private val CalendarDrawerActions = listOf(
    DrawerAction.CALENDAR_TODAY,
    DrawerAction.CALENDAR_PHOTOS,
    DrawerAction.CALENDAR_LOAD,
    DrawerAction.CALENDAR_SELECT,
    DrawerAction.CALENDAR_REPEAT,
)
private val WorkDrawerActions = listOf(
    DrawerAction.WORK_SETUP,
    DrawerAction.HOLIDAYS,
    DrawerAction.VACATIONS,
)
private val ContextDrawerActions = listOf(
    DrawerAction.NOTIFICATIONS,
    DrawerAction.WEATHER,
    DrawerAction.WIDGET,
)
private val ApplicationDrawerActions = listOf(DrawerAction.BACKUPS, DrawerAction.SUPPORT, DrawerAction.UPDATES)

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
                .background(MaterialTheme.vigiliaColors.selection),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "M",
                color = MaterialTheme.vigiliaColors.active,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
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
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DrawerDestinationItem(
    item: MainDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationDrawerItem(
        label = { DrawerItemLabel(item.labelRes, item.descriptionRes, selected) },
        selected = selected,
        onClick = onClick,
        icon = { DrawerGlyph(item.glyph) },
        modifier = modifier
            .padding(horizontal = 12.dp)
            .testTag("main-destination-${item.name.lowercase()}")
            .semantics { this.selected = selected },
        shape = MaterialTheme.shapes.medium,
        colors = NavigationDrawerItemDefaults.colors(
            selectedIconColor = MaterialTheme.vigiliaColors.active,
            selectedTextColor = MaterialTheme.vigiliaColors.active,
            selectedContainerColor = MaterialTheme.vigiliaColors.selection,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
            unselectedContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun DrawerActionItem(
    action: DrawerAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes labelRes: Int = action.labelRes,
) {
    if (action == DrawerAction.SUPPORT) {
        // This external action needs a complete explanation, including with large Android text.
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.padding(horizontal = 12.dp).testTag(action.testTag),
            shape = MaterialTheme.shapes.medium,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .padding(start = 16.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DrawerGlyph(action.glyph)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        DrawerItemLabel(labelRes, action.descriptionRes, selected = false)
                    }
                }
            }
        }
        return
    }
    NavigationDrawerItem(
        label = { DrawerItemLabel(labelRes, action.descriptionRes, selected = false) },
        selected = false,
        onClick = { if (enabled) onClick() },
        icon = { DrawerGlyph(action.glyph) },
        modifier = modifier
            .padding(horizontal = 12.dp)
            .testTag(action.testTag)
            .semantics { if (!enabled) disabled() },
        shape = MaterialTheme.shapes.medium,
        colors = NavigationDrawerItemDefaults.colors(
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
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
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
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
        fontWeight = FontWeight.SemiBold,
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
    widgetViewModel: WidgetViewModel,
    workSetupViewModel: WorkSetupViewModel,
    hoursAndExtrasViewModel: HoursAndExtrasViewModel,
    availabilityViewModel: AvailabilityViewModel,
    summaryViewModel: SummaryViewModel,
    reportsViewModel: ReportsViewModel,
    backupViewModel: BackupViewModel,
    helpViewModel: HelpViewModel,
    modifier: Modifier = Modifier,
    calendarNavigationRequest: Long = 0,
    appThemeMode: AppThemeMode = AppThemeMode.DEFAULT,
    onAppThemeModeChange: (AppThemeMode) -> Unit = {},
    onWidgetReconfigure: (Int) -> Unit = {},
    accessLockState: AccessLockState = AccessLockState(
        phase = com.blackatsystems.miguardia.security.AccessLockPhase.READY,
        configuration = AccessLockConfiguration(),
        locked = false,
        privacyCoverVisible = false,
    ),
    deviceAuthenticationCapability: DeviceAuthenticationCapability = DeviceAuthenticationCapability.AVAILABLE,
    onAccessLockActivate: (AccessLockTimeout) -> Unit = {},
    onAccessLockTimeoutChange: (AccessLockTimeout) -> Unit = {},
    onAccessLockDisable: () -> Unit = {},
    onAccessLockNow: () -> Unit = {},
    onRetryDeviceSecurity: () -> Unit = {},
    onOpenDeviceSecurity: () -> Unit = {},
    onSupportMiGuardia: () -> Unit = {},
    onUpdates: () -> Unit = {},
) {
    val calendarState by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val calendarContext = androidx.compose.ui.platform.LocalContext.current
    val nextEventState by nextEventViewModel.uiState.collectAsStateWithLifecycle()
    val eventLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(eventLifecycle, nextEventViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) nextEventViewModel.refreshOnResume()
        }
        eventLifecycle.addObserver(observer)
        onDispose { eventLifecycle.removeObserver(observer) }
    }
    val v2ManualShiftLoadState by v2ManualShiftLoadViewModel.uiState.collectAsStateWithLifecycle()
    val v2ShiftEditState by v2ShiftEditViewModel.uiState.collectAsStateWithLifecycle()
    val v2ShiftActualState by v2ShiftActualViewModel.uiState.collectAsStateWithLifecycle()
    val v2RecurringState by v2RecurringPlanViewModel.uiState.collectAsStateWithLifecycle()
    val exceptionsState by exceptionsViewModel.uiState.collectAsStateWithLifecycle()
    val vacationState by vacationViewModel.uiState.collectAsStateWithLifecycle()
    val photosState by photosViewModel.uiState.collectAsStateWithLifecycle()
    val notificationState by notificationViewModel.uiState.collectAsStateWithLifecycle()
    val weatherState by weatherViewModel.uiState.collectAsStateWithLifecycle()
    val widgetState by widgetViewModel.uiState.collectAsStateWithLifecycle()
    val workSetupState by workSetupViewModel.uiState.collectAsStateWithLifecycle()
    val hoursAndExtrasState by hoursAndExtrasViewModel.uiState.collectAsStateWithLifecycle()
    val availabilityState by availabilityViewModel.uiState.collectAsStateWithLifecycle()
    val summaryState by summaryViewModel.uiState.collectAsStateWithLifecycle()
    val reportsState by reportsViewModel.uiState.collectAsStateWithLifecycle()
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val helpState by helpViewModel.uiState.collectAsStateWithLifecycle()
    MiGuardiaApp(
        calendarState = calendarState,
        nextEventState = nextEventState,
        onNextEventRetry = nextEventViewModel::retry,
        onPreviousMonth = calendarViewModel::showPreviousMonth,
        onNextMonth = calendarViewModel::showNextMonth,
        onToday = calendarViewModel::showCurrentMonth,
        onSelectDate = { date ->
            com.blackatsystems.miguardia.QaInstrumentationScope.calendarDateSelected(calendarContext, date)
            calendarViewModel.selectDate(date)
        },
        onDismissDate = calendarViewModel::clearSelectedDate,
        onEnterCalendarEditMode = calendarViewModel::enterEditMode,
        onEnterManualCalendarSelection = calendarViewModel::enterManualLoadSelection,
        onEnterHolidayCalendarSelection = calendarViewModel::enterHolidaySelection,
        onSetCalendarSelectionPurpose = calendarViewModel::setSelectionPurpose,
        onCalendarCollapsedChange = calendarViewModel::setCalendarCollapsed,
        onPreviewCalendarBatch = calendarViewModel::previewCalendarBatch,
        onDismissCalendarBatchPreview = calendarViewModel::dismissCalendarBatchPreview,
        onApplyCalendarBatch = calendarViewModel::applyCalendarBatch,
        onClearCalendarBatchMessage = calendarViewModel::clearCalendarBatchMessage,
        onEditSelectionChange = calendarViewModel::setEditSelectedDates,
        onConfirmEditSelection = calendarViewModel::confirmEditSelection,
        onResumeEditSelection = calendarViewModel::resumeEditSelection,
        onFinishCalendarEditMode = calendarViewModel::finishEditMode,
        onRetry = calendarViewModel::retry,
        stage2Actions = Stage2DayActions(
            clearMessage = calendarViewModel::clearStage2Message,
            toggleUndefined = calendarViewModel::toggleUndefined,
            saveMedicalLeave = calendarViewModel::saveMedicalLeave,
            deleteMedicalLeave = calendarViewModel::deleteMedicalLeave,
            changeSituation = calendarViewModel::changeShiftSituation,
        ),
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
        widgetState = widgetState,
        widgetActions = WidgetActions.from(widgetViewModel, onWidgetReconfigure),
        workSetupState = workSetupState,
        workSetupActions = WorkSetupActions.from(workSetupViewModel),
        hoursAndExtrasState = hoursAndExtrasState,
        hoursAndExtrasActions = HoursAndExtrasActions.from(hoursAndExtrasViewModel),
        availabilityState = availabilityState,
        availabilityActions = AvailabilityActions.from(availabilityViewModel),
        summaryState = summaryState,
        summaryActions = SummaryActions.from(summaryViewModel).copy(
            openReports = { reportsViewModel.open(summaryState.visibleMonth) },
        ),
        reportsState = reportsState,
        reportsActions = ReportsActions.from(reportsViewModel),
        backupState = backupState,
        backupActions = BackupActions.from(backupViewModel),
        helpState = helpState,
        helpActions = HelpActions.from(helpViewModel),
        calendarNavigationRequest = calendarNavigationRequest,
        appThemeMode = appThemeMode,
        onAppThemeModeChange = onAppThemeModeChange,
        accessLockState = accessLockState,
        deviceAuthenticationCapability = deviceAuthenticationCapability,
        onAccessLockActivate = onAccessLockActivate,
        onAccessLockTimeoutChange = onAccessLockTimeoutChange,
        onAccessLockDisable = onAccessLockDisable,
        onAccessLockNow = onAccessLockNow,
        onRetryDeviceSecurity = onRetryDeviceSecurity,
        onOpenDeviceSecurity = onOpenDeviceSecurity,
        onSupportMiGuardia = onSupportMiGuardia,
        onUpdates = onUpdates,
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
    stage2Actions: Stage2DayActions = Stage2DayActions(),
    modifier: Modifier = Modifier,
    onEnterCalendarEditMode: (LocalDate?) -> Unit = {},
    onEnterManualCalendarSelection: (LocalDate?) -> Unit = {},
    onEnterHolidayCalendarSelection: (LocalDate?) -> Unit = {},
    onSetCalendarSelectionPurpose: (CalendarSelectionPurpose) -> Unit = {},
    onCalendarCollapsedChange: (Boolean) -> Unit = {},
    onPreviewCalendarBatch: (com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAction, com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAvailability?) -> Unit = { _, _ -> },
    onDismissCalendarBatchPreview: () -> Unit = {},
    onApplyCalendarBatch: () -> Unit = {},
    onClearCalendarBatchMessage: () -> Unit = {},
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
    widgetState: WidgetUiState = WidgetUiState(),
    widgetActions: WidgetActions = WidgetActions(),
    workSetupState: WorkSetupUiState = previewV2WorkSetupUiState(),
    workSetupActions: WorkSetupActions = WorkSetupActions(),
    hoursAndExtrasState: HoursAndExtrasUiState = HoursAndExtrasUiState(),
    hoursAndExtrasActions: HoursAndExtrasActions = HoursAndExtrasActions(),
    availabilityState: AvailabilityUiState = AvailabilityUiState(),
    availabilityActions: AvailabilityActions = AvailabilityActions(),
    summaryState: SummaryUiState = SummaryUiState(visibleMonth = calendarState.visibleMonth),
    summaryActions: SummaryActions = SummaryActions(),
    reportsState: ReportsUiState = ReportsUiState(month = calendarState.visibleMonth),
    reportsActions: ReportsActions = ReportsActions(),
    backupState: BackupUiState = BackupUiState(),
    backupActions: BackupActions = BackupActions(),
    helpState: HelpUiState = HelpUiState.completedForPreview(),
    helpActions: HelpActions = HelpActions(),
    calendarNavigationRequest: Long = 0,
    appThemeMode: AppThemeMode = AppThemeMode.DEFAULT,
    onAppThemeModeChange: (AppThemeMode) -> Unit = {},
    accessLockState: AccessLockState = AccessLockState(
        phase = com.blackatsystems.miguardia.security.AccessLockPhase.READY,
        configuration = AccessLockConfiguration(),
        locked = false,
        privacyCoverVisible = false,
    ),
    deviceAuthenticationCapability: DeviceAuthenticationCapability = DeviceAuthenticationCapability.AVAILABLE,
    onAccessLockActivate: (AccessLockTimeout) -> Unit = {},
    onAccessLockTimeoutChange: (AccessLockTimeout) -> Unit = {},
    onAccessLockDisable: () -> Unit = {},
    onAccessLockNow: () -> Unit = {},
    onRetryDeviceSecurity: () -> Unit = {},
    onOpenDeviceSecurity: () -> Unit = {},
    onSupportMiGuardia: () -> Unit = {},
    onUpdates: () -> Unit = {},
) {
    LaunchedEffect(workSetupState.rootState, workSetupState.surface) {
        helpActions.synchronizeWorkSetup(workSetupState.rootState, workSetupState.surface)
    }
    // Keep destination and consumption alive even while setup/help return early below.
    var destination by rememberSaveable { androidx.compose.runtime.mutableStateOf(MainDestination.CALENDAR) }
    val surfaceStateHolder = rememberSaveableStateHolder()
    var lastCalendarNavigationRequest by rememberSaveable { androidx.compose.runtime.mutableLongStateOf(0L) }
    val preserveV2WriteSurface =
        (v2ShiftEditState.isBlocking && v2ShiftEditState.isSaving) ||
            (v2RecurringState.isBlocking && v2RecurringState.isSaving) ||
            (v2ShiftActualState.isBlocking && v2ShiftActualState.isSaving) ||
            (hoursAndExtrasState.isBlocking && hoursAndExtrasState.isSaving) ||
            (availabilityState.isBlocking && availabilityState.isSaving)
    if (
        !preserveV2WriteSurface &&
        (
            workSetupState.rootState == WorkSetupState.Loading ||
                workSetupState.rootState == WorkSetupState.LoadError ||
                workSetupState.rootState == WorkSetupState.FreshInstall
            )
    ) {
        if (backupState.isOpen) {
            BackupSurfaceHost(backupState, backupActions)
        } else {
            WorkSetupStartupScreen(
                workSetupState,
                workSetupActions,
                modifier,
                onRestoreBackup = backupActions.open,
            )
        }
        return
    }
    val blocksReadyContentForHelp =
        workSetupState.rootState is WorkSetupState.V2Ready &&
            workSetupState.surface == WorkSetupSurface.NONE &&
            (
                helpState.session?.stage == HelpSessionStage.INTRODUCTION ||
                    (helpState.session == null && !helpState.currentVersionCompleted)
                )
    if (blocksReadyContentForHelp) {
        HelpDecisionScreen(helpState, helpActions, modifier)
        return
    }
    // Runs only within the existing access/setup/help gates. Keep the draft in its coordinator.
    LaunchedEffect(v2ManualShiftLoadState.creationRequest, v2ManualShiftLoadState.creationResult,
        workSetupState.creationResult, workSetupState.rootState) {
        val result = workSetupState.creationResult
        if (result != null) {
            v2ManualShiftLoadActions.receiveCreationResult(result)
            workSetupActions.acknowledgeCreation(result.requestId)
        } else if (v2ManualShiftLoadState.creationResult == null) {
            v2ManualShiftLoadState.creationRequest?.let(workSetupActions.openContextualCreation)
        }
    }
    val needsFirstWorkSet = workSetupState.rootState is WorkSetupState.V2NeedsFirstSet
    val v2ReadyState = workSetupState.rootState as? WorkSetupState.V2Ready
    val v2ManualLoadActive = v2ManualShiftLoadState.belongsTo(workSetupState.rootState)
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
    val availabilityActive = availabilityState.isBlocking
    val holidaySelectionActive = exceptionsState.holidaySelectionActive
    val hasExternalBlockingSurface = exceptionsState.surface != ExceptionsSurface.NONE ||
        holidaySelectionActive ||
        vacationState.surface != VacationSurface.NONE ||
        photosState.surface != PhotosSurface.NONE ||
        notificationState.surface != NotificationSurface.NONE ||
        weatherState.surface != WeatherSurface.NONE ||
        widgetState.surface != WidgetSurface.NONE ||
        workSetupState.surface != WorkSetupSurface.NONE ||
        v2ManualLoadActive ||
        v2ShiftEditActive ||
        v2RecurringActive ||
        v2ShiftActualActive ||
        hoursAndExtrasActive ||
        availabilityActive ||
        reportsState.isOpen ||
        backupState.isOpen
    val helpAnchors = remember { HelpAnchorRegistry() }
    val currentSetSummaryActive by rememberUpdatedState(summaryActions.setActive)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(destination, lifecycleOwner, hasExternalBlockingSurface) {
        val summaryIsVisible = destination == MainDestination.SUMMARY && !hasExternalBlockingSurface
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> currentSetSummaryActive(summaryIsVisible)
                Lifecycle.Event.ON_STOP -> currentSetSummaryActive(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            currentSetSummaryActive(summaryIsVisible)
        }
        onDispose {
            lifecycle.removeObserver(observer)
            if (summaryIsVisible) currentSetSummaryActive(false)
        }
    }
    val displayedCalendarState = calendarState
    val drawerState = remember { DrawerState(initialValue = DrawerValue.Closed) }
    val tourDrawerTargets = remember {
        listOf(HelpTourStep.PHOTOS, HelpTourStep.LOAD_AND_REPEAT, HelpTourStep.HELP)
            .associateWith { BringIntoViewRequester() }
    }
    val drawerScrollState = rememberScrollState()
    var drawerViewportSize by remember { mutableStateOf(IntSize.Zero) }
    val coroutineScope = rememberCoroutineScope()
    val selectedDay = if (displayedCalendarState.interactionMode == CalendarInteractionMode.VIEW) {
        displayedCalendarState.detailDate?.let { selectedDate ->
        displayedCalendarState.days.firstOrNull { it.date == selectedDate }
        }
    } else {
        null
    }
    val hasBlockingSurface = hasExternalBlockingSurface ||
        (destination == MainDestination.SUMMARY && summaryState.hasSubsurface)
    val calendarBackOwner = calendarSelectionBackOwner(
        calendarVisible = destination == MainDestination.CALENDAR,
        interactionMode = displayedCalendarState.interactionMode,
        selectionPurpose = displayedCalendarState.selectionPurpose,
        manualLoadActive = v2ManualLoadActive,
        holidaySelectionActive = holidaySelectionActive,
        selectedDayOpen = selectedDay != null,
        generalBackBlocked = hasBlockingSurface,
    )
    val canOpenDrawer = !hasBlockingSurface && selectedDay == null
    val supportMenuAction = remember { com.blackatsystems.miguardia.support.SupportMenuAction() }
    val supportAllowed by rememberUpdatedState(
        canOpenDrawer && v2ReadyState != null && helpState.canConsumePendingDestination &&
            accessLockState.allowsSensitiveContent &&
            calendarState.interactionMode == CalendarInteractionMode.VIEW &&
            !workSetupState.isSavingSector && !workSetupState.isSavingWorkSet && !workSetupState.isSavingTemplate,
    )
    val currentSupportMiGuardia by rememberUpdatedState(onSupportMiGuardia)
    val currentUpdates by rememberUpdatedState(onUpdates)
    DisposableEffect(lifecycleOwner, supportMenuAction) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP ||
                event == Lifecycle.Event.ON_DESTROY
            ) supportMenuAction.cancelPendingOpen()
        }
        lifecycle.addObserver(observer)
        onDispose {
            supportMenuAction.cancelPendingOpen()
            lifecycle.removeObserver(observer)
        }
    }
    val activeTourStep = helpState.session
        ?.takeIf { it.stage == HelpSessionStage.TOUR }
        ?.tourStep
    val androidDensity = LocalDensity.current
    LaunchedEffect(helpState.session?.token, activeTourStep, drawerViewportSize, androidDensity) {
        when (activeTourStep) {
            HelpTourStep.LOAD_AND_REPEAT, HelpTourStep.PHOTOS -> {
                destination = MainDestination.CALENDAR
                drawerState.open()
            }
            HelpTourStep.SUMMARY -> {
                drawerState.close()
                destination = MainDestination.SUMMARY
            }
            HelpTourStep.HELP -> {
                drawerState.open()
            }
            null -> Unit
            else -> {
                drawerState.close()
                destination = MainDestination.CALENDAR
            }
        }
        // Wait for the opened drawer's layout, then reveal the actual item. This
        // also runs when returning from HELP, recreating, or resizing the window.
        // No drawer action is invoked to position the guide.
        if (activeTourStep == HelpTourStep.MENU) {
            drawerScrollState.scrollTo(0)
        } else if (activeTourStep in tourDrawerTargets) {
            withFrameNanos { }
            tourDrawerTargets.getValue(requireNotNull(activeTourStep)).bringIntoView()
        }
    }
    LaunchedEffect(helpState.navigationEvent?.sequence) {
        val event = helpState.navigationEvent ?: return@LaunchedEffect
        drawerState.close()
        destination = when (event.target) {
            HelpNavigationTarget.CALENDAR -> MainDestination.CALENDAR
            HelpNavigationTarget.HELP -> MainDestination.HELP
        }
        helpActions.consumeNavigation(event.sequence)
    }
    val calendarNavigationAllowed = v2ReadyState != null && !hasExternalBlockingSurface &&
        helpState.canConsumePendingDestination && accessLockState.allowsSensitiveContent &&
        !workSetupState.isSavingSector && !workSetupState.isSavingWorkSet && !workSetupState.isSavingTemplate
    LaunchedEffect(calendarNavigationRequest, calendarNavigationAllowed) {
        if (shouldConsumeCalendarNavigation(calendarNavigationRequest, lastCalendarNavigationRequest, calendarNavigationAllowed)) {
            drawerState.snapTo(DrawerValue.Closed)
            lastCalendarNavigationRequest = calendarNavigationRequest
            destination = MainDestination.CALENDAR
        }
    }
    LaunchedEffect(holidaySelectionActive) {
        if (holidaySelectionActive) {
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
        workSetupState.rootState,
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
    LaunchedEffect(
        v2ManualLoadActive,
        holidaySelectionActive,
        calendarState.visibleMonth,
        calendarState.interactionMode,
        calendarState.selectionPurpose,
        calendarState.editSelectedDates,
    ) {
        when {
            v2ManualLoadActive && calendarState.interactionMode == CalendarInteractionMode.VIEW -> {
                onEnterManualCalendarSelection(null)
            }
            v2ManualLoadActive && calendarState.selectionPurpose != CalendarSelectionPurpose.MANUAL_LOAD ->
                onSetCalendarSelectionPurpose(CalendarSelectionPurpose.MANUAL_LOAD)
            holidaySelectionActive && calendarState.interactionMode == CalendarInteractionMode.VIEW -> {
                val restoredDates = exceptionsState.holidayDraft.selectedDates
                    .filterTo(linkedSetOf()) { YearMonth.from(it) == calendarState.visibleMonth }
                onEnterHolidayCalendarSelection(null)
                if (restoredDates.isNotEmpty()) onEditSelectionChange(restoredDates)
            }
            holidaySelectionActive && calendarState.selectionPurpose != CalendarSelectionPurpose.HOLIDAY -> {
                onSetCalendarSelectionPurpose(CalendarSelectionPurpose.HOLIDAY)
                val restoredDates = exceptionsState.holidayDraft.selectedDates
                    .filterTo(linkedSetOf()) { YearMonth.from(it) == calendarState.visibleMonth }
                if (restoredDates.isNotEmpty()) onEditSelectionChange(restoredDates)
            }
            holidaySelectionActive && calendarState.interactionMode == CalendarInteractionMode.EDIT -> {
                exceptionsActions.updateHolidaySelection(
                    calendarState.visibleMonth,
                    calendarState.editSelectedDates,
                )
            }
            !v2ManualLoadActive &&
                !holidaySelectionActive &&
                calendarState.interactionMode == CalendarInteractionMode.EDIT &&
                calendarState.selectionPurpose != CalendarSelectionPurpose.GENERAL -> {
                onFinishCalendarEditMode()
            }
        }
    }
    LaunchedEffect(v2ManualShiftLoadState.successSequence) {
        val successSequence = v2ManualShiftLoadState.successSequence
        if (successSequence > 0) {
            v2ManualShiftLoadActions.consumeSuccess(successSequence)
            destination = MainDestination.CALENDAR
        }
    }
    LaunchedEffect(v2ManualLoadActive, v2ManualShiftLoadState.stage,
        calendarState.interactionMode, calendarState.selectionPurpose, calendarState.editSelectedDates) {
        if (v2ManualLoadActive && v2ManualShiftLoadState.stage == V2ManualShiftLoadStage.SELECT_DATES &&
            calendarState.interactionMode == CalendarInteractionMode.EDIT &&
            calendarState.selectionPurpose == CalendarSelectionPurpose.MANUAL_LOAD) {
            // The calendar owns live date picking, including the intentionally empty set.
            // Persist it in the same draft; never restore an abandoned/old selection over it.
            v2ManualShiftLoadActions.updateSelectedDates(calendarState.editSelectedDates)
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
    LaunchedEffect(backupState.successSequence) {
        if (backupState.successSequence > 0) {
            onDismissDate()
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
            if (action == DrawerAction.SUPPORT || action == DrawerAction.UPDATES) {
                if (!drawerState.isOpen || drawerState.targetValue != DrawerValue.Open) return@launch
                supportMenuAction.open(
                    closeMenu = { drawerState.close() },
                    isAllowed = {
                        supportAllowed && drawerState.isClosed && !drawerState.isAnimationRunning &&
                            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    },
                    openLink = { if (action == DrawerAction.UPDATES) currentUpdates() else currentSupportMiGuardia() },
                )
                return@launch
            }
            drawerState.close()
            when (action) {
                DrawerAction.CALENDAR_TODAY -> {
                    destination = MainDestination.CALENDAR
                    onToday()
                }
                DrawerAction.CALENDAR_PHOTOS -> {
                    destination = MainDestination.CALENDAR
                    photosActions.open(calendarState.visibleMonth)
                }
                DrawerAction.CALENDAR_LOAD -> {
                    destination = MainDestination.CALENDAR
                    v2ManualShiftLoadActions.start(workSetupState.rootState)
                    onEnterManualCalendarSelection(null)
                }
                DrawerAction.CALENDAR_SELECT -> {
                    destination = MainDestination.CALENDAR
                    onEnterCalendarEditMode(null)
                }
                DrawerAction.CALENDAR_REPEAT -> {
                    destination = MainDestination.CALENDAR
                    v2RecurringActions.openCreate(workSetupState.rootState)
                }
                DrawerAction.WORK_SETUP -> {
                    destination = MainDestination.CALENDAR
                    workSetupActions.openOverview()
                }
                DrawerAction.HOLIDAYS -> exceptionsActions.openHolidays(calendarState.visibleMonth)
                DrawerAction.VACATIONS -> vacationActions.openList(calendarState.visibleMonth)
                DrawerAction.NOTIFICATIONS -> notificationActions.openGlobal()
                DrawerAction.WEATHER -> weatherActions.openGlobal()
                DrawerAction.WIDGET -> widgetActions.open()
                DrawerAction.BACKUPS -> backupActions.open()
                DrawerAction.SUPPORT -> Unit // Handled above with its fresh access check.
                DrawerAction.UPDATES -> Unit
            }
        }
    }

    var calendarWindowHeightPx by remember { mutableStateOf<Int?>(null) }
    var calendarWindowWidthPx by remember { mutableStateOf<Int?>(null) }
    var calendarViewportBounds by remember { mutableStateOf<Rect?>(null) }
    val calendarThemeDensity = LocalDensity.current
    val stableCalendarDensity = calendarThemeDensity.density
    val calendarWindowHeightBaseDp = calendarWindowHeightPx?.div(stableCalendarDensity) ?: Float.POSITIVE_INFINITY
    val monthlyCalendarSurface = destination == MainDestination.CALENDAR &&
        (!v2ManualLoadActive || v2ManualShiftLoadState.stage == V2ManualShiftLoadStage.SELECT_DATES)
    // One message/owner priority for both the header and the loading wizard.
    val calendarConfirmationMessage = calendarState.batchMessage
        ?: calendarState.stage2Message
        ?: v2ShiftEditState.infoMessage
        ?: v2ShiftActualState.infoMessage
        ?: v2ManualShiftLoadState.infoMessage
        ?: v2RecurringState.infoMessage
        ?: hoursAndExtrasState.message.takeUnless { hoursAndExtrasState.loadState == HoursAndExtrasLoadState.ERROR }
        ?: availabilityState.message.takeUnless { availabilityState.loadState == AvailabilityLoadState.ERROR }
    val dismissCalendarConfirmation = when {
        calendarState.batchMessage != null -> onClearCalendarBatchMessage
        calendarState.stage2Message != null -> stage2Actions.clearMessage
        v2ShiftEditState.infoMessage != null -> v2ShiftEditActions.clearMessage
        v2ShiftActualState.infoMessage != null -> v2ShiftActualActions.clearMessage
        v2ManualShiftLoadState.infoMessage != null -> v2ManualShiftLoadActions.clearMessage
        v2RecurringState.infoMessage != null -> v2RecurringActions.clearMessage
        hoursAndExtrasState.message != null && hoursAndExtrasState.loadState != HoursAndExtrasLoadState.ERROR -> hoursAndExtrasActions.clearMessage
        availabilityState.message != null && availabilityState.loadState != AvailabilityLoadState.ERROR -> availabilityActions.clearMessage
        else -> v2ManualShiftLoadActions.clearMessage
    }
    var calendarNoticeDetails by rememberSaveable { mutableStateOf<String?>(null) }
    val selectionNeedsSpace = monthlyCalendarSurface &&
        displayedCalendarState.interactionMode == CalendarInteractionMode.EDIT &&
        calendarWindowHeightBaseDp < 600f
    // If short-window selection hides branding, retain the owner's message
    // until the header returns; never spend a calendar row on a confirmation.
    val windowSize = LocalWindowInfo.current.containerSize
    val layoutDirection = LocalLayoutDirection.current
    val defaultContentInsets = ScaffoldDefaults.contentWindowInsets
    val viewport = calendarViewportBounds
    val clippedCalendarInsets = if (
        monthlyCalendarSurface && viewport != null && !viewport.isEmpty &&
        windowSize.width > 0 && windowSize.height > 0
    ) {
        calendarViewportInsets(
            windowSize,
            viewport,
            CalendarViewportInsets(
                left = defaultContentInsets.getLeft(calendarThemeDensity, layoutDirection),
                top = defaultContentInsets.getTop(calendarThemeDensity),
                right = defaultContentInsets.getRight(calendarThemeDensity, layoutDirection),
                bottom = defaultContentInsets.getBottom(calendarThemeDensity),
            ),
        )
    } else null
    val monthlyContentInsets = clippedCalendarInsets?.let {
        WindowInsets(left = it.left, top = it.top, right = it.right, bottom = it.bottom)
    } ?: defaultContentInsets
    val calendarWidthPx = calendarWindowWidthPx ?: windowSize.width
    val calendarHeightPx = calendarWindowHeightPx ?: windowSize.height
    val sideRailCalendar = monthlyCalendarSurface && calendarUsesSideRail(
        (calendarWidthPx - monthlyContentInsets.getLeft(calendarThemeDensity, layoutDirection) -
            monthlyContentInsets.getRight(calendarThemeDensity, layoutDirection)) / stableCalendarDensity,
        (calendarHeightPx - monthlyContentInsets.getTop(calendarThemeDensity) -
            monthlyContentInsets.getBottom(calendarThemeDensity)) / stableCalendarDensity,
    )
    val headerNoticeVisible = monthlyCalendarSurface && (sideRailCalendar || !selectionNeedsSpace)

    ModalNavigationDrawer(
        modifier = if (activeTourStep != null) {
            Modifier.clearAndSetSemantics { }
        } else {
            Modifier
        },
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.testTag("main-navigation-drawer")
                    .onSizeChanged { drawerViewportSize = it },
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(drawerScrollState)
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
                    if (
                        (v2ReadyState != null || needsFirstWorkSet) &&
                        calendarState.interactionMode == CalendarInteractionMode.VIEW
                    ) {
                        DrawerSectionTitle(R.string.drawer_section_calendar_actions)
                        CalendarDrawerActions
                            .filterNot { action -> needsFirstWorkSet && action == DrawerAction.CALENDAR_REPEAT }
                            .forEach { action ->
                            DrawerActionItem(
                                action = action,
                                onClick = { openDrawerAction(action) },
                                enabled = action !in setOf(
                                    DrawerAction.CALENDAR_LOAD,
                                    DrawerAction.CALENDAR_SELECT,
                                    DrawerAction.CALENDAR_REPEAT,
                                ) || calendarState.loadState == CalendarLoadState.CONTENT,
                                labelRes = if (
                                    action == DrawerAction.CALENDAR_LOAD && needsFirstWorkSet
                                ) {
                                    R.string.drawer_calendar_load_francos
                                } else {
                                    action.labelRes
                                },
                                modifier = when (action) {
                                    DrawerAction.CALENDAR_LOAD -> Modifier
                                        .bringIntoViewRequester(tourDrawerTargets.getValue(HelpTourStep.LOAD_AND_REPEAT))
                                        .helpAnchor(helpAnchors, HelpAnchor.LOAD_AND_REPEAT)
                                    DrawerAction.CALENDAR_PHOTOS -> Modifier
                                        .bringIntoViewRequester(tourDrawerTargets.getValue(HelpTourStep.PHOTOS))
                                        .helpAnchor(helpAnchors, HelpAnchor.PHOTOS)
                                    else -> Modifier
                                },
                            )
                        }
                    }
                    DrawerSectionTitle(R.string.drawer_section_work)
                    WorkDrawerActions.forEach { action ->
                        DrawerActionItem(action = action, onClick = { openDrawerAction(action) })
                    }
                    DrawerSectionTitle(R.string.drawer_section_context)
                    ContextDrawerActions.forEach { action ->
                        DrawerActionItem(action = action, onClick = { openDrawerAction(action) })
                    }
                    DrawerSectionTitle(R.string.drawer_section_application)
                    ApplicationDrawerActions.forEach { action ->
                        if (action !in setOf(DrawerAction.SUPPORT, DrawerAction.UPDATES) || supportAllowed) {
                            DrawerActionItem(action = action, onClick = { openDrawerAction(action) })
                        }
                    }
                    DrawerDestinationItem(
                        item = MainDestination.HELP,
                        selected = destination == MainDestination.HELP,
                        onClick = { selectDestination(MainDestination.HELP) },
                        modifier = Modifier
                            .bringIntoViewRequester(tourDrawerTargets.getValue(HelpTourStep.HELP))
                            .helpAnchor(helpAnchors, HelpAnchor.HELP),
                    )
                    DrawerDestinationItem(
                        item = MainDestination.ACCESS_LOCK,
                        selected = destination == MainDestination.ACCESS_LOCK,
                        onClick = { selectDestination(MainDestination.ACCESS_LOCK) },
                    )
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
            modifier = modifier.fillMaxSize().onSizeChanged {
                calendarWindowHeightPx = it.height
                calendarWindowWidthPx = it.width
            }.onGloballyPositioned {
                calendarViewportBounds = it.boundsInWindow()
            },
            contentWindowInsets = if (monthlyCalendarSurface) monthlyContentInsets else defaultContentInsets,
            topBar = {
                val monthlyCalendar = monthlyCalendarSurface
                // A short-window selection keeps month navigation and its contextual actions;
                // branding/menu returns as soon as selection ends. This reserves 48-dp rows.
                if (!selectionNeedsSpace && !sideRailCalendar) {
                val openMenuDescription = stringResource(R.string.open_menu)
                CenterAlignedTopAppBar(
                    modifier = Modifier.testTag("main-top-bar"),
                    expandedHeight = if (monthlyCalendar) 56.dp else 64.dp,
                    windowInsets = if (monthlyCalendar && clippedCalendarInsets != null) {
                        WindowInsets(left = clippedCalendarInsets.left, top = clippedCalendarInsets.top,
                            right = clippedCalendarInsets.right, bottom = 0)
                    } else androidx.compose.material3.TopAppBarDefaults.windowInsets,
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
                                .helpAnchor(helpAnchors, HelpAnchor.MENU)
                                .semantics { contentDescription = openMenuDescription },
                        ) {
                            Text(
                                text = "☰",
                                modifier = Modifier.clearAndSetSemantics {},
                                style = if (monthlyCalendar) MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 20.sp, lineHeight = 24.sp,
                                ) else MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                    title = {
                        if (monthlyCalendar && calendarConfirmationMessage != null) {
                            TextButton(
                                onClick = { calendarNoticeDetails = calendarConfirmationMessage },
                                modifier = Modifier.heightIn(min = 48.dp)
                                    .testTag("calendar-header-notice")
                                    .semantics {
                                        contentDescription = "Aviso: $calendarConfirmationMessage. Tocar para ver el mensaje completo"
                                        liveRegion = LiveRegionMode.Polite
                                    },
                            ) {
                                Text("Ver aviso", fontSize = 14.sp, lineHeight = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.vigiliaColors.success)
                            }
                        } else {
                        Text(
                            stringResource(R.string.app_name),
                            style = if (monthlyCalendar) MaterialTheme.typography.titleLarge.copy(
                                fontSize = 16.sp, lineHeight = 20.sp,
                            ) else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        }
                    },
                )
                }
            },
        ) { innerPadding ->
            when (destination) {
                MainDestination.CALENDAR -> CalendarScreen(
                    state = displayedCalendarState,
                    nextEventState = nextEventState,
                    contentPadding = innerPadding,
                    confirmationMessage = calendarConfirmationMessage,
                    onDismissConfirmation = dismissCalendarConfirmation,
                    confirmationInHeader = monthlyCalendarSurface,
                    headerNoticeVisible = headerNoticeVisible,
                    sideRail = sideRailCalendar,
                    onOpenMenu = { coroutineScope.launch { drawerState.open() } },
                    menuEnabled = canOpenDrawer,
                    onShowHeaderNotice = { calendarNoticeDetails = calendarConfirmationMessage },
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onSelectDate = onSelectDate,
                    onEditSelectionChange = onEditSelectionChange,
                    onConfirmEditSelection = onConfirmEditSelection,
                    onResumeEditSelection = onResumeEditSelection,
                    onRetry = onRetry,
                    onNextEventRetry = onNextEventRetry,
                    onFinishEditMode = onFinishCalendarEditMode,
                    onEnterGeneralSelection = onEnterCalendarEditMode,
                    onUseSelectionForManualLoad = {
                        v2ManualShiftLoadActions.start(workSetupState.rootState)
                        onSetCalendarSelectionPurpose(CalendarSelectionPurpose.MANUAL_LOAD)
                    },
                    onPreviewCalendarBatch = onPreviewCalendarBatch,
                    onDismissCalendarBatchPreview = onDismissCalendarBatchPreview,
                    onApplyCalendarBatch = onApplyCalendarBatch,
                    onClearCalendarBatchMessage = onClearCalendarBatchMessage,
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
                    availabilityState = availabilityState,
                    availabilityActions = availabilityActions,
                    stage2Actions = stage2Actions,
                    holidaySelectionState = exceptionsState.takeIf { holidaySelectionActive },
                    holidaySelectionActions = exceptionsActions,
                    needsFirstWorkSet = needsFirstWorkSet && !v2ManualLoadActive,
                    onCreateFirstWorkSet = workSetupActions.openFirstWorkSet,
                    helpAnchors = helpAnchors,
                )

                MainDestination.SUMMARY -> Box(
                    Modifier
                        .fillMaxSize()
                        .helpAnchor(helpAnchors, HelpAnchor.SUMMARY),
                ) {
                    SummaryScreen(
                        state = summaryState,
                        actions = summaryActions,
                        contentPadding = innerPadding,
                    )
                }

                MainDestination.ACCESS_LOCK -> AccessLockSettingsScreen(
                    state = accessLockState,
                    capability = deviceAuthenticationCapability,
                    contentPadding = innerPadding,
                    onActivate = onAccessLockActivate,
                    onChangeTimeout = onAccessLockTimeoutChange,
                    onDisable = onAccessLockDisable,
                    onLockNow = onAccessLockNow,
                    onRetryDeviceSecurity = onRetryDeviceSecurity,
                    onOpenDeviceSecurity = onOpenDeviceSecurity,
                )

                MainDestination.APPEARANCE -> AppearanceScreen(
                    contentPadding = innerPadding,
                    appThemeMode = appThemeMode,
                    onAppThemeModeChange = onAppThemeModeChange,
                )

                MainDestination.HELP -> HelpScreen(
                    contentPadding = innerPadding,
                    canRepeat = v2ReadyState != null && helpState.canRepeat,
                    actions = helpActions,
                )
            }
        }
    }

    calendarNoticeDetails?.let { message ->
        AlertDialog(
            modifier = Modifier.testTag("calendar-notice-details"),
            onDismissRequest = { calendarNoticeDetails = null },
            title = { Text("Aviso") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(message) } },
            confirmButton = { TextButton(onClick = { calendarNoticeDetails = null }) { Text("Entendido") } },
        )
    }

    BackHandler(
        enabled = calendarBackOwner == CalendarSelectionBackOwner.MANUAL_LOAD,
        onBack = {
            if (v2ManualShiftLoadState.isLoading || v2ManualShiftLoadState.isSaving || v2ManualShiftLoadState.creationRequest != null) {
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
        enabled = calendarBackOwner == CalendarSelectionBackOwner.HOLIDAY,
        onBack = {
            exceptionsActions.cancelHolidaySelection()
            onFinishCalendarEditMode()
        },
    )
    BackHandler(
        enabled = destination == MainDestination.SUMMARY && summaryState.hasSubsurface,
        onBack = summaryActions.back,
    )
    BackHandler(
        enabled = destination != MainDestination.CALENDAR &&
            !(destination == MainDestination.SUMMARY && summaryState.hasSubsurface) &&
            !hasBlockingSurface,
        onBack = { destination = MainDestination.CALENDAR },
    )
    BackHandler(
        enabled = calendarBackOwner == CalendarSelectionBackOwner.GENERAL,
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
        ?.filter { it.isEligibleForWeather(calendarState.referenceInstant, listOfNotNull(selectedDay.vacation), selectedDay.medicalLeaves) }
        ?.mapTo(linkedSetOf()) { it.id }
        .orEmpty()
    LaunchedEffect(weatherBriefIds, weatherState.preferences.enabled) {
        if (weatherBriefIds.isEmpty() || !weatherState.preferences.enabled) {
            weatherActions.clearBriefs()
        } else {
            weatherActions.loadBriefs(weatherBriefIds)
        }
    }
    if (
        selectedDay != null &&
        !v2ShiftEditActive &&
        !v2RecurringActive &&
        !v2ShiftActualActive &&
        !hoursAndExtrasActive &&
        !availabilityActive
    ) {
        // Each opening owns its anchors/animation. Do not reuse the Hidden state
        // of a dismissed sheet while asynchronous detail content is being measured.
        val dayDetailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissDate,
            sheetState = dayDetailSheetState,
            modifier = Modifier.testTag("day-detail-modal-sheet"),
        ) {
            DayDetailSheet(
                day = selectedDay,
                referenceInstant = calendarState.referenceInstant,
                onEditDay = v2ReadyState?.let { v2ShiftEditActions.beginDayEditing },
                onManageDayOff = if (v2ReadyState != null || needsFirstWorkSet) {
                    {
                        val date = selectedDay.date
                        onDismissDate()
                        v2ManualShiftLoadActions.start(workSetupState.rootState)
                        onEnterManualCalendarSelection(date)
                        v2ManualShiftLoadActions.confirmDates(setOf(date))
                    }
                } else null,
                onOpenNotes = { shift ->
                    onDismissDate()
                    exceptionsActions.openNotes(shift)
                },
                onOpenNotifications = { shift ->
                    onDismissDate()
                    notificationActions.openShift(shift)
                },
                onOpenWeather = { shiftId ->
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
                availabilityState = availabilityState,
                availabilityActions = availabilityActions,
                stage2Actions = stage2Actions,
                stage2Busy = calendarState.stage2Busy,
                stage2Expectations = calendarState.stage2Expectations,
            )
        }
    }

    if (exceptionsState.surface != ExceptionsSurface.NONE) {
        ExceptionsSurfaceHost(exceptionsState, exceptionsActions, photosViewModel?.fileStore)
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
    if (widgetState.surface != WidgetSurface.NONE) {
        WidgetSurfaceHost(widgetState, widgetActions)
    }
    // The parent stays in its coordinator while a child owns the visible surface and Back.
    // Saveable state preserves the advanced section and scroll position across child/recreation.
    LaunchedEffect(workSetupState.surface) {
        if (workSetupState.surface == WorkSetupSurface.NONE) surfaceStateHolder.removeState("work-setup")
    }
    LaunchedEffect(hoursAndExtrasActive) {
        if (!hoursAndExtrasActive) surfaceStateHolder.removeState("hours-progress")
    }
    if (workSetupState.surface != WorkSetupSurface.NONE &&
        !v2RecurringActive && !v2ShiftActualActive && !hoursAndExtrasActive && !availabilityActive
    ) {
        surfaceStateHolder.SaveableStateProvider("work-setup") {
            WorkSetupSurfaceHost(
                workSetupState,
                workSetupActions.copy(
                    openRecurringPlans = {
                        v2RecurringActions.openPlans(workSetupState.rootState)
                    },
                    openExtraClasses = {
                        v2ShiftActualActions.openCatalog(workSetupState.rootState)
                    },
                    openHoursProgress = {
                        hoursAndExtrasActions.openProgress()
                    },
                    openAvailability = {
                        availabilityActions.openOverview()
                    },
                ),
            )
        }
    }
    if (hoursAndExtrasActive && !v2ShiftActualActive) {
        surfaceStateHolder.SaveableStateProvider("hours-progress") {
            HoursAndExtrasSurfaceHost(
                state = hoursAndExtrasState,
                actions = hoursAndExtrasActions,
                availabilityState = availabilityState,
                onOpenExtraClassCatalog = {
                    v2ShiftActualActions.openCatalog(workSetupState.rootState)
                },
            )
        }
    }
    if (availabilityActive) {
        AvailabilitySurfaceHost(availabilityState, availabilityActions)
    }
    if (reportsState.isOpen) {
        ReportsSurfaceHost(reportsState, reportsActions)
    }
    if (backupState.isOpen) {
        BackupSurfaceHost(backupState, backupActions)
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
                addShift = { date ->
                    v2ShiftEditActions.cancelToDetail()
                    onDismissDate()
                    v2ManualShiftLoadActions.start(workSetupState.rootState)
                    onEnterManualCalendarSelection(date)
                    v2ManualShiftLoadActions.confirmDates(setOf(date))
                },
                markDayOff = { date ->
                    v2ShiftEditActions.cancelToDetail()
                    onDismissDate()
                    v2ManualShiftLoadActions.start(workSetupState.rootState)
                    onEnterManualCalendarSelection(date)
                    v2ManualShiftLoadActions.requestDayOff(setOf(date), true)
                },
                markRetainer = { date ->
                    v2ShiftEditActions.cancelToDetail()
                    onDismissDate()
                    availabilityActions.openRetainer(date)
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
    HelpTourOverlay(
        state = helpState,
        actions = helpActions,
        anchors = helpAnchors,
    )
}

internal fun shouldConsumeCalendarNavigation(request: Long, lastConsumed: Long, allowed: Boolean = true): Boolean =
    allowed && request > 0 && request > lastConsumed
