package com.blackatsystems.miguardia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import com.blackatsystems.miguardia.core.domain.calendar.ShiftTemporalStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAction
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAvailability
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchDateState
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.availability.AvailabilityActions
import com.blackatsystems.miguardia.ui.availability.AvailabilityLoadState
import com.blackatsystems.miguardia.ui.availability.AvailabilityUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarSelectionPurpose
import com.blackatsystems.miguardia.ui.calendar.CalendarDateTextBounds
import com.blackatsystems.miguardia.ui.calendar.CalendarDateFontSizePx
import com.blackatsystems.miguardia.ui.calendar.CalendarDateTextOverflow
import com.blackatsystems.miguardia.ui.calendar.calendarCanShowNextEvent
import com.blackatsystems.miguardia.ui.calendar.calendarCellVisibleTextLines
import com.blackatsystems.miguardia.ui.calendar.calendarCellVisibleSignals
import com.blackatsystems.miguardia.ui.calendar.calendarCellTextFitsWidth
import com.blackatsystems.miguardia.ui.calendar.CALENDAR_SIDE_RAIL_WIDTH_DP
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.AutomaticTimeField
import com.blackatsystems.miguardia.ui.components.MiGuardiaButtonShape
import com.blackatsystems.miguardia.ui.components.MiGuardiaSpacing
import com.blackatsystems.miguardia.ui.components.PICKER_BUTTON_SUFFIX
import com.blackatsystems.miguardia.ui.components.destructiveButtonColors
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasActions
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasLoadState
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasUiState
import com.blackatsystems.miguardia.ui.help.HelpAnchor
import com.blackatsystems.miguardia.ui.help.HelpAnchorRegistry
import com.blackatsystems.miguardia.ui.help.HelpTourStep
import com.blackatsystems.miguardia.ui.help.helpAnchor
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadActions
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadContent
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadStage
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadUiState
import com.blackatsystems.miguardia.ui.management.V2RecurringActions
import com.blackatsystems.miguardia.ui.management.V2RecurringUiState
import com.blackatsystems.miguardia.ui.management.V2ShiftEditActions
import com.blackatsystems.miguardia.ui.management.V2ShiftEditUiState
import com.blackatsystems.miguardia.ui.management.V2ShiftActualActions
import com.blackatsystems.miguardia.ui.management.V2ShiftActualUiState
import com.blackatsystems.miguardia.ui.nextevent.NextEventCard
import com.blackatsystems.miguardia.ui.nextevent.NextEventUiState
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsActions
import com.blackatsystems.miguardia.ui.exceptions.HolidayCalendarSelectionContent
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsUiState
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import com.blackatsystems.miguardia.ui.worksetup.V2FirstWorkSetGuide
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
internal fun calendarMonthCellCount(month: YearMonth): Int {
    val occupiedCells = month.atDay(1).dayOfWeek.value - 1 + month.lengthOfMonth()
    return ((occupiedCells + 6) / 7) * 7
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CalendarScreen(
    state: CalendarUiState,
    nextEventState: NextEventUiState,
    contentPadding: PaddingValues,
    confirmationMessage: String?,
    onDismissConfirmation: () -> Unit,
    confirmationInHeader: Boolean,
    headerNoticeVisible: Boolean,
    sideRail: Boolean,
    onOpenMenu: () -> Unit,
    menuEnabled: Boolean,
    onShowHeaderNotice: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onEditSelectionChange: (Set<LocalDate>) -> Unit,
    onConfirmEditSelection: () -> Unit,
    onResumeEditSelection: () -> Unit,
    onRetry: () -> Unit,
    onNextEventRetry: () -> Unit,
    onFinishEditMode: () -> Unit,
    onEnterGeneralSelection: (LocalDate?) -> Unit,
    onUseSelectionForManualLoad: () -> Unit,
    onPreviewCalendarBatch: (CalendarBatchAction, CalendarBatchAvailability?) -> Unit,
    onDismissCalendarBatchPreview: () -> Unit,
    onApplyCalendarBatch: () -> Unit,
    onClearCalendarBatchMessage: () -> Unit,
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
    availabilityState: AvailabilityUiState,
    availabilityActions: AvailabilityActions,
    stage2Actions: Stage2DayActions,
    holidaySelectionState: ExceptionsUiState?,
    holidaySelectionActions: ExceptionsActions,
    needsFirstWorkSet: Boolean,
    onCreateFirstWorkSet: () -> Unit,
    helpAnchors: HelpAnchorRegistry? = null,
) {
    val today = state.referenceInstant.atZone(AppDefaults.zoneId()).toLocalDate()
    var pendingMonthChange by rememberSaveable { mutableStateOf<String?>(null) }
    var showManualLoadDiscardConfirmation by rememberSaveable { mutableStateOf(false) }
    var showGeneralActionSheet by rememberSaveable { mutableStateOf(false) }
    var showAvailabilityInput by rememberSaveable { mutableStateOf(false) }
    var showManualSelectionOptions by rememberSaveable { mutableStateOf(false) }
    var showCalendarProblems by rememberSaveable { mutableStateOf(false) }
    val hasCalendarProblems = state.loadState == CalendarLoadState.ERROR ||
        hoursAndExtrasState.loadState == HoursAndExtrasLoadState.ERROR ||
        availabilityState.loadState == AvailabilityLoadState.ERROR
    LaunchedEffect(hasCalendarProblems) {
        if (!hasCalendarProblems) showCalendarProblems = false
    }
    val v2ManualLoadOpen = v2ManualShiftLoadState.isActive
    val activeHolidaySelectionState = holidaySelectionState?.takeIf {
        it.holidaySelectionActive
    }
    val holidaySelectionOpen = activeHolidaySelectionState != null
    val v2DateSelectionOpen = v2ManualLoadOpen &&
        v2ManualShiftLoadState.stage == V2ManualShiftLoadStage.SELECT_DATES
    val v2CalendarSelectionReady = state.loadState == CalendarLoadState.CONTENT &&
        state.editSelectedDates.all { selectedDate -> state.days.any { day -> day.date == selectedDate } }
    val v2DateSelectionInteractive = v2DateSelectionOpen &&
        v2CalendarSelectionReady &&
        !state.editSelectionConfirmed &&
        !v2ManualShiftLoadState.isLoading &&
        !v2ManualShiftLoadState.isSaving
    val changeEditSelection: (Set<LocalDate>) -> Unit = { requestedDates ->
        val selectedDates = if (
            activeHolidaySelectionState?.holidayDraft?.editingId != null &&
            requestedDates.size > 1
        ) {
            val previousDates = state.editSelectedDates
            val addedDate = (requestedDates - previousDates).firstOrNull()
            addedDate?.let(::setOf) ?: requestedDates.sorted().take(1).toSet()
        } else {
            requestedDates
        }
        onEditSelectionChange(selectedDates)
        if (holidaySelectionOpen) {
            holidaySelectionActions.updateHolidaySelection(state.visibleMonth, selectedDates)
        }
    }
    val requestMonthChange: (String, () -> Unit) -> Unit = { key, action ->
        if (state.interactionMode == CalendarInteractionMode.EDIT && state.editSelectedDates.isNotEmpty()) {
            pendingMonthChange = key
        } else {
            action()
        }
    }
    val previousMonth = { requestMonthChange("previous", onPreviousMonth) }
    val nextMonth = { requestMonthChange("next", onNextMonth) }
    val manualWorkflowContentOnly = v2ManualLoadOpen && !v2DateSelectionInteractive
    TransientConfirmation(
        modifier = Modifier.padding(contentPadding).consumeWindowInsets(contentPadding),
        message = confirmationMessage.takeIf { !confirmationInHeader || headerNoticeVisible },
        onDismiss = onDismissConfirmation,
        showNotice = !confirmationInHeader,
        reserveSpace = manualWorkflowContentOnly,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val verticalSpacing = if (maxHeight < 700.dp) 2.dp else 4.dp
            val fontScale = LocalDensity.current.fontScale
            val compactSelection = maxHeight < 650.dp || fontScale >= 2f
            val horizontalContentPadding = when {
                manualWorkflowContentOnly -> 16.dp
                maxWidth >= 352.dp -> 8.dp
                else -> 0.dp
            }
            val showNextEvent = calendarCanShowNextEvent(
                maxHeight.value, calendarMonthCellCount(state.visibleMonth) / 7, fontScale,
            )
            val primaryContent: @Composable ColumnScope.() -> Unit = {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = horizontalContentPadding)
                        .testTag("calendar-static-content"),
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                ) {
                if (!sideRail) Box(Modifier.helpAnchor(helpAnchors, HelpAnchor.TODAY_CARD)) {
                    if (state.interactionMode == CalendarInteractionMode.VIEW && showNextEvent) {
                        NextEventCard(state = nextEventState, onRetry = onNextEventRetry)
                    }
                }
                if (state.interactionMode == CalendarInteractionMode.EDIT && !manualWorkflowContentOnly && !compactSelection) {
                    Text(
                        text = when {
                            v2ManualLoadOpen -> "Cargando jornadas"
                            holidaySelectionOpen -> "Eligiendo feriados"
                            else -> "Editando calendario"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.vigiliaColors.active,
                        modifier = Modifier.semantics {
                            contentDescription = when {
                                v2ManualLoadOpen ->
                                    "Cargando jornadas. Elegí los días y revisá antes de guardar."
                                holidaySelectionOpen ->
                                    "Eligiendo feriados en la grilla principal del Calendario."
                                else ->
                                    "Editando calendario. Las acciones para modificar están habilitadas."
                            }
                        },
                    )
                }
                if (!manualWorkflowContentOnly) {
                if (!sideRail) MonthControls(
                    visibleMonth = state.visibleMonth,
                    onPrevious = previousMonth,
                    onNext = nextMonth,
                    navigationEnabled = !v2ManualLoadOpen || v2DateSelectionInteractive,
                    helpAnchors = helpAnchors,
                )

                when (state.loadState) {
                    CalendarLoadState.LOADING -> if (!sideRail || state.days.isEmpty()) LoadingCalendar()
                    CalendarLoadState.ERROR -> if (!sideRail || state.days.isEmpty()) ErrorCalendar(
                        message = state.errorMessage ?: stringResource(R.string.calendar_error),
                        onRetry = onRetry,
                    )

                    CalendarLoadState.CONTENT -> Unit
                }

                if (!sideRail && hoursAndExtrasState.loadState == HoursAndExtrasLoadState.ERROR) {
                    PersistentMessage(
                        message = hoursAndExtrasState.message
                            ?: "No pudimos actualizar los extras del calendario.",
                        onRetry = hoursAndExtrasActions.retry,
                    )
                }
                if (!sideRail && availabilityState.loadState == AvailabilityLoadState.ERROR) {
                    PersistentMessage(
                        message = availabilityState.message
                            ?: "No pudimos actualizar la disponibilidad del calendario.",
                        onRetry = availabilityActions.retry,
                    )
                }

                if (state.days.isNotEmpty()) {
                    val independentExtraCounts = hoursAndExtrasState.source
                        ?.independentExtras
                        .orEmpty()
                        .groupingBy(IndependentExtraWorkRecord::ownerLocalDate)
                        .eachCount()
                    val availabilityIndicators = availabilityState.source
                        ?.windows
                        .orEmpty()
                        .groupBy { it.ownerLocalDate }
                        .mapValues { (_, records) ->
                            records.size to records.map { it.labelSnapshot }.distinct().joinToString(" / ")
                        }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .helpAnchor(helpAnchors, HelpAnchor.DAY_DETAIL),
                    ) {
                        CalendarGridViewport(
                            month = state.visibleMonth,
                            days = state.days,
                            independentExtraCounts = independentExtraCounts,
                            availabilityIndicators = availabilityIndicators,
                            today = today,
                            onPreviousMonth = previousMonth,
                            onNextMonth = nextMonth,
                            onSelectDate = onSelectDate,
                            interactionMode = state.interactionMode,
                            selectedDates = state.editSelectedDates,
                            onEditSelectionChange = changeEditSelection,
                            selectionEnabled = !v2ManualLoadOpen || v2DateSelectionInteractive,
                            selectionConfirmed = state.editSelectionConfirmed,
                            monthSwipeEnabled = !v2ManualLoadOpen || v2DateSelectionInteractive,
                            selectedDateDescription = when {
                                v2ManualLoadOpen -> "seleccionado para cargar jornadas"
                                holidaySelectionOpen -> "seleccionado como feriado"
                                else -> "seleccionado para editar"
                            },
                            shiftDescription = "jornada",
                            onEnterGeneralSelection = onEnterGeneralSelection,
                        )
                    }
                }
                }

                if (manualWorkflowContentOnly) {
                    when (state.loadState) {
                        CalendarLoadState.LOADING -> LoadingCalendar()
                        CalendarLoadState.ERROR -> ErrorCalendar(
                            message = state.errorMessage ?: stringResource(R.string.calendar_error),
                            onRetry = onRetry,
                        )
                        CalendarLoadState.CONTENT -> Unit
                    }
                }

                if (!sideRail && needsFirstWorkSet && state.interactionMode == CalendarInteractionMode.VIEW) {
                    V2FirstWorkSetGuide(onCreateFirstPlace = onCreateFirstWorkSet)
                }

                if (state.interactionMode == CalendarInteractionMode.EDIT) {
                    if (v2ManualLoadOpen && (manualWorkflowContentOnly || !compactSelection)) {
                        V2ManualShiftLoadContent(
                            state = v2ManualShiftLoadState,
                            calendarSelectedDates = state.editSelectedDates,
                            calendarSelectionConfirmed = state.editSelectionConfirmed,
                            calendarContentReady = v2CalendarSelectionReady,
                            actions = v2ManualShiftLoadActions.copy(cancel = { showManualLoadDiscardConfirmation = true }),
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
                            showDateSelectionActions = false,
                            modifier = if (manualWorkflowContentOnly) {
                                Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 8.dp)
                            } else {
                                Modifier
                            },
                        )
                    } else if (activeHolidaySelectionState != null && !compactSelection) {
                        HolidayCalendarSelectionContent(
                            state = activeHolidaySelectionState,
                            selectedDates = state.editSelectedDates,
                            onConfirm = {
                                holidaySelectionActions.confirmHolidaySelection(
                                    state.visibleMonth,
                                    state.editSelectedDates,
                                )
                            },
                            onCancel = holidaySelectionActions.cancelHolidaySelection,
                            showActions = false,
                        )
                    }
                }

                if (state.interactionMode == CalendarInteractionMode.EDIT) {
                    if (
                        v2ManualLoadOpen &&
                        v2ManualShiftLoadState.stage == V2ManualShiftLoadStage.REVIEW &&
                        !holidaySelectionOpen
                    ) {
                        OutlinedButton(
                            onClick = {
                                showManualLoadDiscardConfirmation = true
                            },
                            enabled = !v2ManualShiftLoadState.isLoading && !v2ManualShiftLoadState.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Cancelar carga")
                        }
                    }
                }
                }
            }
            val selectionActions: @Composable (Boolean) -> Unit = { inRail ->
                CalendarSelectionActions(
                    state = state,
                    selectionEnabled = when (state.selectionPurpose) {
                        CalendarSelectionPurpose.MANUAL_LOAD -> v2CalendarSelectionReady &&
                            !v2ManualShiftLoadState.isLoading &&
                            !v2ManualShiftLoadState.isSaving &&
                            v2ManualShiftLoadState.creationRequest == null &&
                            v2ManualShiftLoadState.pendingDayOff == null
                        CalendarSelectionPurpose.HOLIDAY -> activeHolidaySelectionState?.isSaving != true
                        else -> !state.batchBusy
                    },
                    onContinueSelection = {
                        when (state.selectionPurpose) {
                            null, CalendarSelectionPurpose.GENERAL -> showGeneralActionSheet = true
                            CalendarSelectionPurpose.MANUAL_LOAD -> {
                                onConfirmEditSelection()
                                v2ManualShiftLoadActions.confirmDates(state.editSelectedDates)
                            }
                            CalendarSelectionPurpose.HOLIDAY -> holidaySelectionActions.confirmHolidaySelection(
                                state.visibleMonth,
                                state.editSelectedDates,
                            )
                        }
                    },
                    onCancelSelection = {
                        when (state.selectionPurpose) {
                            null, CalendarSelectionPurpose.GENERAL -> onFinishEditMode()
                            CalendarSelectionPurpose.MANUAL_LOAD -> showManualLoadDiscardConfirmation = true
                            CalendarSelectionPurpose.HOLIDAY -> holidaySelectionActions.cancelHolidaySelection()
                        }
                    },
                    manualDateSelection = v2ManualLoadOpen && v2ManualShiftLoadState.stage == V2ManualShiftLoadStage.SELECT_DATES,
                    holidaySelection = holidaySelectionOpen,
                    compact = compactSelection,
                    sideRail = inRail,
                    onManualOptions = if (v2DateSelectionInteractive && compactSelection) {
                        { showManualSelectionOptions = true }
                    } else null,
                )
            }
            if (sideRail) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalendarSideRail(
                        visibleMonth = state.visibleMonth,
                        onPrevious = previousMonth,
                        onNext = nextMonth,
                        navigationEnabled = !v2ManualLoadOpen || v2DateSelectionInteractive,
                        onOpenMenu = onOpenMenu,
                        menuEnabled = menuEnabled,
                        confirmationMessage = confirmationMessage,
                        onShowHeaderNotice = onShowHeaderNotice,
                        hasProblems = hasCalendarProblems,
                        onShowProblems = { showCalendarProblems = true },
                        refreshing = state.loadState == CalendarLoadState.LOADING && state.days.isNotEmpty(),
                        onCreateFirstWorkSet = onCreateFirstWorkSet.takeIf {
                            needsFirstWorkSet && state.interactionMode == CalendarInteractionMode.VIEW
                        },
                        helpAnchors = helpAnchors,
                        selectionActions = { selectionActions(true) },
                    )
                    Column(Modifier.weight(1f).fillMaxHeight(), content = primaryContent)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    primaryContent()
                    selectionActions(false)
                }
            }
        }
    }
    if (showCalendarProblems && hasCalendarProblems) {
        AlertDialog(
            modifier = Modifier.testTag("calendar-problem-details"),
            onDismissRequest = { showCalendarProblems = false },
            title = { Text("Actualización del calendario") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small)) {
                    if (state.loadState == CalendarLoadState.ERROR) ErrorCalendar(
                        state.errorMessage ?: stringResource(R.string.calendar_error), onRetry,
                    )
                    if (hoursAndExtrasState.loadState == HoursAndExtrasLoadState.ERROR) PersistentMessage(
                        hoursAndExtrasState.message ?: "No pudimos actualizar los extras del calendario.",
                        modifier = Modifier.testTag("calendar-hours-problem"),
                        onRetry = hoursAndExtrasActions.retry,
                    )
                    if (availabilityState.loadState == AvailabilityLoadState.ERROR) PersistentMessage(
                        availabilityState.message ?: "No pudimos actualizar la disponibilidad del calendario.",
                        modifier = Modifier.testTag("calendar-availability-problem"),
                        onRetry = availabilityActions.retry,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showCalendarProblems = false }) { Text("Cerrar") } },
        )
    }
    if (showManualSelectionOptions && v2DateSelectionInteractive) {
        AlertDialog(
            onDismissRequest = { showManualSelectionOptions = false },
            title = { Text("Opciones para los días seleccionados") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small)) {
                    OutlinedButton(onClick = {
                        showManualSelectionOptions = false
                        v2ManualShiftLoadActions.requestDayOff(state.editSelectedDates, true)
                    }, enabled = state.editSelectedDates.isNotEmpty(), shape = MiGuardiaButtonShape,
                        modifier = Modifier.fillMaxWidth().testTag("v2-mark-day-off")) { Text("Marcar Franco") }
                    TextButton(onClick = {
                        showManualSelectionOptions = false
                        v2ManualShiftLoadActions.requestDayOff(state.editSelectedDates, false)
                    }, enabled = state.editSelectedDates.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("v2-remove-day-off")) { Text("Quitar Franco") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showManualSelectionOptions = false }) { Text("Cerrar") } },
        )
    }
    pendingMonthChange?.let { requested ->
        AlertDialog(
            onDismissRequest = { pendingMonthChange = null },
            title = { Text("Cambiar de mes") },
            text = { Text("Al cambiar de mes se limpiará la selección actual. ¿Querés continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingMonthChange = null
                    changeEditSelection(emptySet())
                    when (requested) {
                        "previous" -> onPreviousMonth()
                        "next" -> onNextMonth()
                        else -> Unit
                    }
                }) { Text("Cambiar de mes") }
            },
            dismissButton = { TextButton(onClick = { pendingMonthChange = null }) { Text("Conservar selección") } },
        )
    }
    if (showManualLoadDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showManualLoadDiscardConfirmation = false },
            title = { Text("¿Descartar esta carga?") },
            text = { Text("Todavía no se guardó ninguna jornada. Si salís, vas a perder lo que elegiste.") },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        showManualLoadDiscardConfirmation = false
                        v2ManualShiftLoadActions.cancel()
                        onFinishEditMode()
                    },
                    shape = MiGuardiaButtonShape,
                    colors = destructiveButtonColors(),
                ) { Text("Descartar carga") }
            },
            dismissButton = {
                TextButton(onClick = { showManualLoadDiscardConfirmation = false }) {
                    Text("Seguir cargando")
                }
            },
        )
    }
    if (showGeneralActionSheet) {
        CalendarBatchActionDialog(
            onDismiss = { showGeneralActionSheet = false },
            onManualLoad = {
                showGeneralActionSheet = false
                onUseSelectionForManualLoad()
            },
            onDayOff = {
                showGeneralActionSheet = false
                onPreviewCalendarBatch(CalendarBatchAction.DAY_OFF, null)
            },
            onUndefined = {
                showGeneralActionSheet = false
                onPreviewCalendarBatch(CalendarBatchAction.UNDEFINED, null)
            },
            onAvailability = {
                showGeneralActionSheet = false
                showAvailabilityInput = true
            },
        )
    }
    if (showAvailabilityInput) {
        CalendarAvailabilityInputDialog(
            onDismiss = { showAvailabilityInput = false },
            onContinue = { input ->
                showAvailabilityInput = false
                onPreviewCalendarBatch(CalendarBatchAction.AVAILABILITY, input)
            },
        )
    }
    state.batchPreview?.let { preview ->
        CalendarBatchPreviewDialog(
            preview = preview,
            busy = state.batchBusy,
            onDismiss = onDismissCalendarBatchPreview,
            onApply = onApplyCalendarBatch,
        )
    }
}

@Composable
private fun CalendarSideRail(
    visibleMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    navigationEnabled: Boolean,
    onOpenMenu: () -> Unit,
    menuEnabled: Boolean,
    confirmationMessage: String?,
    onShowHeaderNotice: () -> Unit,
    hasProblems: Boolean,
    onShowProblems: () -> Unit,
    refreshing: Boolean,
    onCreateFirstWorkSet: (() -> Unit)?,
    helpAnchors: HelpAnchorRegistry?,
    selectionActions: @Composable () -> Unit,
) {
    Surface(Modifier.width(CALENDAR_SIDE_RAIL_WIDTH_DP.dp).fillMaxHeight()
        .testTag("calendar-side-rail"), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onOpenMenu, enabled = menuEnabled,
                    modifier = Modifier.size(48.dp).testTag("main-menu-button")
                        .helpAnchor(helpAnchors, HelpAnchor.MENU)
                        .semantics { contentDescription = "Abrir menú" }) {
                    Text("☰", Modifier.clearAndSetSemantics {}, fontSize = 20.sp, lineHeight = 24.sp)
                }
                if (confirmationMessage != null) IconButton(onClick = onShowHeaderNotice,
                    modifier = Modifier.size(48.dp).testTag("calendar-header-notice").semantics {
                        contentDescription = "Aviso: $confirmationMessage. Tocar para ver el mensaje completo"
                        liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                    }) {
                    Text("✓", Modifier.clearAndSetSemantics {}, color = MaterialTheme.vigiliaColors.success,
                        fontSize = 20.sp, lineHeight = 24.sp)
                }
                if (hasProblems) IconButton(onClick = onShowProblems,
                    modifier = Modifier.size(48.dp).testTag("calendar-update-problems").semantics {
                        contentDescription = "Hay datos del calendario que no pudimos actualizar. Revisar avisos y reintentar"
                        liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                    }) {
                    Text("!", Modifier.clearAndSetSemantics {}, color = MaterialTheme.colorScheme.error,
                        fontSize = 20.sp, lineHeight = 24.sp)
                }
            }
            if (refreshing) LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp)
                    .testTag("calendar-loading-progress")
                    .semantics { contentDescription = "Cargando calendario" },
            )
            MonthControls(visibleMonth, onPrevious, onNext, navigationEnabled, helpAnchors, vertical = true)
            onCreateFirstWorkSet?.let { create ->
                TextButton(onClick = create, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Crear primer lugar", fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            selectionActions()
        }
    }
}

@Composable
private fun CalendarSelectionActions(
    state: CalendarUiState,
    selectionEnabled: Boolean,
    onContinueSelection: () -> Unit,
    onCancelSelection: () -> Unit,
    manualDateSelection: Boolean,
    holidaySelection: Boolean,
    compact: Boolean,
    sideRail: Boolean,
    onManualOptions: (() -> Unit)?,
) {
    val selectionActionsVisible = state.interactionMode == CalendarInteractionMode.EDIT && (
        state.selectionPurpose == CalendarSelectionPurpose.GENERAL ||
            state.selectionPurpose == CalendarSelectionPurpose.MANUAL_LOAD && manualDateSelection ||
            state.selectionPurpose == CalendarSelectionPurpose.HOLIDAY && holidaySelection
        )
    if (!selectionActionsVisible) return
    Surface(
        modifier = Modifier.testTag("calendar-primary-actions"),
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
    ) {
        if (sideRail) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${state.editSelectedDates.size} días",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 14.sp),
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "${state.editSelectedDates.size} días seleccionados"
                    })
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = onContinueSelection,
                        enabled = state.editSelectedDates.isNotEmpty() && selectionEnabled && !state.batchBusy,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp).testTag(when (state.selectionPurpose) {
                            CalendarSelectionPurpose.MANUAL_LOAD -> "v2-manual-confirm-dates"
                            CalendarSelectionPurpose.HOLIDAY -> "holiday-confirm-calendar-selection"
                            else -> "calendar-selection-continue"
                        }).semantics { contentDescription = "Continuar" }) {
                        CalendarActionGlyph(confirm = true)
                    }
                    OutlinedButton(onClick = onCancelSelection,
                        enabled = selectionEnabled && !state.batchBusy,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp).testTag(
                            if (holidaySelection) "holiday-cancel-calendar-selection" else "calendar-selection-cancel"
                        ).semantics { contentDescription = "Cancelar" }) {
                        CalendarActionGlyph(confirm = false)
                    }
                    onManualOptions?.let { action ->
                        IconButton(onClick = action, modifier = Modifier.size(48.dp)
                            .testTag("calendar-manual-selection-options")
                            .semantics { contentDescription = "Más opciones para los días seleccionados" }) {
                            Text("⋯", Modifier.clearAndSetSemantics {}, fontSize = 18.sp, lineHeight = 24.sp)
                        }
                    }
                }
            }
        } else if (compact) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.editSelectedDates.size.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 14.sp),
                    modifier = Modifier.width(32.dp).semantics {
                        contentDescription = "${state.editSelectedDates.size} días seleccionados"
                    })
                Button(onClick = onContinueSelection,
                    enabled = state.editSelectedDates.isNotEmpty() && selectionEnabled && !state.batchBusy,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag(when(state.selectionPurpose) {
                        CalendarSelectionPurpose.MANUAL_LOAD -> "v2-manual-confirm-dates"
                        CalendarSelectionPurpose.HOLIDAY -> "holiday-confirm-calendar-selection"
                        else -> "calendar-selection-continue"
                    })) { Text("Continuar", fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1) }
                OutlinedButton(onClick = onCancelSelection, enabled = selectionEnabled && !state.batchBusy,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag(
                        if (holidaySelection) "holiday-cancel-calendar-selection" else "calendar-selection-cancel")) {
                    Text("Cancelar", fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1)
                }
                onManualOptions?.let { action ->
                    IconButton(onClick = action, modifier = Modifier.testTag("calendar-manual-selection-options")
                        .semantics { contentDescription = "Más opciones para los días seleccionados" }) {
                        Text("⋯", modifier = Modifier.clearAndSetSemantics {}, fontSize = 18.sp)
                    }
                }
            }
        } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selectionActionsVisible) {
                Text(
                    if (state.editSelectedDates.size == 1) "1 día seleccionado" else "${state.editSelectedDates.size} días seleccionados",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { contentDescription = if (state.editSelectedDates.size == 1) "1 día seleccionado" else "${state.editSelectedDates.size} días seleccionados" },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onContinueSelection,
                        enabled = state.editSelectedDates.isNotEmpty() && selectionEnabled && !state.batchBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag(
                                when (state.selectionPurpose) {
                                    CalendarSelectionPurpose.GENERAL -> "calendar-selection-continue"
                                    CalendarSelectionPurpose.MANUAL_LOAD -> "v2-manual-confirm-dates"
                                    CalendarSelectionPurpose.HOLIDAY -> "holiday-confirm-calendar-selection"
                                },
                            ),
                    ) { Text("Continuar") }
                    OutlinedButton(
                        onClick = onCancelSelection,
                        enabled = selectionEnabled && !state.batchBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag(
                                if (state.selectionPurpose == CalendarSelectionPurpose.HOLIDAY) {
                                    "holiday-cancel-calendar-selection"
                                } else {
                                    "calendar-selection-cancel"
                                },
                            ),
                    ) { Text("Cancelar") }
                }
            }
        }
        }
    }
}

@Composable
private fun CalendarActionGlyph(confirm: Boolean) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        if (confirm) {
            drawLine(color, Offset(size.width * .12f, size.height * .5f),
                Offset(size.width * .4f, size.height * .78f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * .4f, size.height * .78f),
                Offset(size.width * .9f, size.height * .2f), stroke, StrokeCap.Round)
        } else {
            drawLine(color, Offset(size.width * .2f, size.height * .2f),
                Offset(size.width * .8f, size.height * .8f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * .8f, size.height * .2f),
                Offset(size.width * .2f, size.height * .8f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun CalendarBatchActionDialog(
    onDismiss: () -> Unit,
    onManualLoad: () -> Unit,
    onDayOff: () -> Unit,
    onUndefined: () -> Unit,
    onAvailability: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Qué querés hacer?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small)) {
                Button(onClick = onManualLoad, modifier = Modifier.fillMaxWidth(), shape = MiGuardiaButtonShape) { Text("Cargar jornadas") }
                OutlinedButton(onClick = onDayOff, modifier = Modifier.fillMaxWidth(), shape = MiGuardiaButtonShape) { Text("Marcar Franco") }
                OutlinedButton(onClick = onUndefined, modifier = Modifier.fillMaxWidth(), shape = MiGuardiaButtonShape) { Text("Marcar Sin definir") }
                OutlinedButton(onClick = onAvailability, modifier = Modifier.fillMaxWidth(), shape = MiGuardiaButtonShape) { Text("Agregar Retén") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Volver") } },
    )
}

@Composable
internal fun CalendarAvailabilityInputDialog(
    onDismiss: () -> Unit,
    onContinue: (CalendarBatchAvailability) -> Unit,
) {
    var startText by rememberSaveable { mutableStateOf("") }
    var endText by rememberSaveable { mutableStateOf("") }
    var labelName by rememberSaveable { mutableStateOf(AvailabilityLabel.ON_CALL_RETAINER.name) }
    val start = runCatching { LocalTime.parse(startText) }.getOrNull()
    val end = runCatching { LocalTime.parse(endText) }.getOrNull()
    val label = AvailabilityLabel.valueOf(labelName)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar disponibilidad") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
            ) {
                Text("La misma franja y etiqueta se aplicarán a todos los días como registros independientes.")
                // Stacked: a dialog is too narrow for two large time fields side by side. The
                // clock only writes HH:mm into the draft; "Revisar" stays the person's step.
                AutomaticTimeField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = "Desde (HH:mm)",
                    withPicker = true,
                    pickerTestTag = "calendar-availability-start$PICKER_BUTTON_SUFFIX",
                    textStyle = MaterialTheme.typography.headlineSmall,
                    showHint = false,
                    modifier = Modifier.fillMaxWidth().testTag("calendar-availability-start"),
                )
                AutomaticTimeField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = "Hasta (HH:mm)",
                    withPicker = true,
                    pickerTestTag = "calendar-availability-end$PICKER_BUTTON_SUFFIX",
                    textStyle = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth().testTag("calendar-availability-end"),
                )
                AvailabilityLabel.entries.forEach { option ->
                    if (option == label) {
                        Button(onClick = { labelName = option.name }, modifier = Modifier.fillMaxWidth(), shape = MiGuardiaButtonShape) { Text(option.displayName) }
                    } else {
                        OutlinedButton(onClick = { labelName = option.name }, modifier = Modifier.fillMaxWidth(), shape = MiGuardiaButtonShape) { Text(option.displayName) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onContinue(CalendarBatchAvailability(requireNotNull(start), requireNotNull(end), label)) },
                enabled = start != null && end != null && start != end,
                shape = MiGuardiaButtonShape,
                modifier = Modifier.testTag("calendar-availability-review"),
            ) { Text("Revisar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun CalendarBatchPreviewDialog(
    preview: com.blackatsystems.miguardia.core.domain.repository.CalendarBatchPreview,
    busy: Boolean,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Revisá antes de guardar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small)) {
                Text("Se aplicará: ${preview.applicable.size}")
                Text("Ya estaba así: ${preview.unchanged.size}")
                Text("Conflicto: ${preview.conflicts.size}")
                preview.results.forEach { result ->
                    val prefix = when (result.state) {
                        CalendarBatchDateState.APPLICABLE -> "Se aplicará"
                        CalendarBatchDateState.UNCHANGED -> "Ya estaba así"
                        CalendarBatchDateState.CONFLICT -> "Conflicto"
                    }
                    Text("$prefix · ${result.date.fullDisplayName()}: ${result.explanation}", style = MaterialTheme.typography.bodySmall)
                }
                if (preview.conflicts.isNotEmpty()) Text("No se guardará nada mientras haya conflictos.")
            }
        },
        confirmButton = {
            Button(
                onClick = onApply,
                enabled = !busy && preview.conflicts.isEmpty() && preview.applicable.isNotEmpty(),
                shape = MiGuardiaButtonShape,
            ) { Text(if (busy) "Guardando…" else "Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Modificar selección") } },
    )
}

@Composable
private fun CalendarGridViewport(
    month: YearMonth,
    days: List<CalendarDay>,
    independentExtraCounts: Map<LocalDate, Int>,
    availabilityIndicators: Map<LocalDate, Pair<Int, String>>,
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
    onEnterGeneralSelection: (LocalDate?) -> Unit,
) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                WeekdayHeader()
                MonthGrid(
                    modifier = Modifier.weight(1f),
                    month = month,
                    days = days,
                    independentExtraCounts = independentExtraCounts,
                    availabilityIndicators = availabilityIndicators,
                    today = today,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onSelectDate = onSelectDate,
                    interactionMode = interactionMode,
                    selectedDates = selectedDates,
                    onEditSelectionChange = onEditSelectionChange,
                    selectionEnabled = selectionEnabled,
                    selectionConfirmed = selectionConfirmed,
                    enableMonthSwipe = monthSwipeEnabled,
                    selectedDateDescription = selectedDateDescription,
                    shiftDescription = shiftDescription,
                    onLongClickDate = { date -> onEnterGeneralSelection(date) },
                )
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
    navigationEnabled: Boolean,
    helpAnchors: HelpAnchorRegistry? = null,
    vertical: Boolean = false,
) {
    val previousDescription = stringResource(R.string.previous_month)
    val nextDescription = stringResource(R.string.next_month)
    val previous: @Composable () -> Unit = {
        IconButton(onClick = onPrevious, enabled = navigationEnabled,
            modifier = Modifier.size(48.dp).testTag("calendar-previous-month")
                .semantics { contentDescription = previousDescription }) {
            Text("‹", Modifier.clearAndSetSemantics {}, fontSize = 20.sp, lineHeight = 24.sp)
        }
    }
    val next: @Composable () -> Unit = {
        IconButton(onClick = onNext, enabled = navigationEnabled,
            modifier = Modifier.size(48.dp).testTag("calendar-next-month")
                .semantics { contentDescription = nextDescription }) {
            Text("›", Modifier.clearAndSetSemantics {}, fontSize = 20.sp, lineHeight = 24.sp)
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar-month-controls")
            .helpAnchor(helpAnchors, HelpAnchor.MONTH_AND_GRID),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            if (vertical) {
                Text(visibleMonth.displayName(),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    previous()
                    next()
                }
            } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                previous()
                Text(
                    text = visibleMonth.displayName(),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                next()
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
    Row(modifier = Modifier.fillMaxWidth().testTag("calendar-weekday-header")) {
        labels.forEach { (shortLabel, fullLabel) ->
            val fullDayName = stringResource(fullLabel)
            Text(
                text = stringResource(shortLabel),
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = fullDayName },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, lineHeight = 12.sp),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    modifier: Modifier = Modifier,
    month: YearMonth,
    days: List<CalendarDay>,
    independentExtraCounts: Map<LocalDate, Int>,
    availabilityIndicators: Map<LocalDate, Pair<Int, String>>,
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
    onLongClickDate: (LocalDate) -> Unit,
) {
    val dayByDate = remember(days) { days.associateBy { it.date } }
    val cellTextMeasurer = rememberTextMeasurer()
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cells = List<CalendarDay?>(calendarMonthCellCount(month)) { index ->
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
        modifier = modifier
            .fillMaxWidth()
            .testTag("month-grid")
            .then(swipeModifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { day ->
                    if (day == null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                        )
                    } else {
                        DayCell(
                            textMeasurer = cellTextMeasurer,
                            day = day,
                            independentExtraCount = independentExtraCounts[day.date] ?: 0,
                            availabilityCount = availabilityIndicators[day.date]?.first ?: 0,
                            availabilityLabel = availabilityIndicators[day.date]?.second,
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
                            onLongClick = {
                                if (interactionMode == CalendarInteractionMode.VIEW) onLongClickDate(day.date)
                            },
                            longClickEnabled = interactionMode == CalendarInteractionMode.VIEW,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DayCell(
    textMeasurer: TextMeasurer,
    day: CalendarDay,
    independentExtraCount: Int,
    availabilityCount: Int,
    availabilityLabel: String?,
    isToday: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    selectedDateDescription: String,
    shiftDescription: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    longClickEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = day.accessibilityDescription(
        isToday,
        shiftDescription,
        independentExtraCount,
        availabilityCount,
        availabilityLabel,
    )
    val isCompletedDay = day.isFullyCompleted
    val isPlannedDay = day.shifts.any { it.temporalStatus == ShiftTemporalStatus.UPCOMING }
    val vigilia = MaterialTheme.vigiliaColors
    val background = when {
        isSelected -> vigilia.selection
        day.vacation != null -> vigilia.vacation.copy(alpha = if (vigilia.isDark) 0.20f else 0.12f)
        isCompletedDay -> vigilia.completedContainer
        isPlannedDay -> vigilia.plannedContainer
        else -> vigilia.cellNeutral
    }
    val foreground = when {
        isSelected -> vigilia.onSelection
        day.vacation != null -> MaterialTheme.colorScheme.onSurface
        isCompletedDay -> vigilia.onCompletedContainer
        isPlannedDay -> vigilia.onPlannedContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    var dateTextBounds by remember(day.date) { mutableStateOf(Rect.Zero) }
    var dateTextOverflows by remember(day.date) { mutableStateOf(false) }
    val density = LocalDensity.current
    val dateFontSizePx = with(density) { 14.sp.toPx() }
    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 1.dp)
            .fillMaxSize()
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
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick.takeIf { longClickEnabled },
            )
            .testTag(if (isCompletedDay) "completed-day-${day.date}" else "day-${day.date}")
            .clearAndSetSemantics {
                contentDescription = if (isSelected) "$description, $selectedDateDescription" else description
                role = Role.Button
                selected = isSelected
                this[CalendarDateTextBounds] = dateTextBounds
                this[CalendarDateFontSizePx] = dateFontSizePx
                this[CalendarDateTextOverflow] = dateTextOverflows
                if (enabled) {
                    onClick(action = {
                        onClick()
                        true
                    })
                    if (longClickEnabled) {
                        onLongClick(label = "Seleccionar este día", action = {
                            onLongClick()
                            true
                        })
                    }
                } else {
                    disabled()
                }
            },
    ) {
        val firstShift = day.shifts.singleOrNull()
        val commonMarkers = buildList {
            when (day.explicitStatus) {
                ExplicitDayStatusType.DAY_OFF -> add("F")
                ExplicitDayStatusType.UNDEFINED -> add("?")
                null -> Unit
            }
            if (day.hasMedicalLeave) add("CM")
            if (day.holiday != null) add("Fer.")
            if (day.isImplicitlyUndefined) add("?")
        }
        val signals = buildList {
            if (day.shifts.isNotEmpty()) add(CalendarCellSignal.WORK)
            if (day.explicitStatus == ExplicitDayStatusType.DAY_OFF) add(CalendarCellSignal.DAY_OFF)
            if (day.hasMedicalLeave) add(CalendarCellSignal.MEDICAL)
            if (day.vacation != null) add(CalendarCellSignal.VACATION)
            if (day.holiday != null) add(CalendarCellSignal.HOLIDAY)
            if (independentExtraCount > 0) add(CalendarCellSignal.EXTRA)
            if (availabilityCount > 0) add(CalendarCellSignal.AVAILABILITY)
            if (day.explicitStatus == ExplicitDayStatusType.UNDEFINED || day.isImplicitlyUndefined) {
                add(CalendarCellSignal.UNDEFINED)
            }
        }
        // Important coexisting conditions precede optional time/status. A compact signal
        // always remains, even where text at Android's chosen size cannot physically fit.
        val textLines = buildList<Pair<String, Float>> {
            when {
                day.vacation != null -> add("V" to 14f)
                day.shifts.size > 1 -> add("${day.shifts.size} turnos" to 14f)
                firstShift != null -> add(firstShift.shift.objectiveAbbreviationSnapshot to 14f)
            }
            if (commonMarkers.isNotEmpty()) add(commonMarkers.joinToString(" · ") to 14f)
            if (independentExtraCount > 0) {
                add((if (independentExtraCount == 1) "Extra" else "$independentExtraCount extras") to 12f)
            }
            if (availabilityCount > 0) {
                add((if (availabilityCount == 1) requireNotNull(availabilityLabel) else "$availabilityCount disponibil.") to 12f)
            }
            if (firstShift != null && day.vacation == null) {
                add(firstShift.shift.calendarTimeRange() to 12f)
                add(firstShift.temporalStatus.shortLabel() to 12f)
            }
        }.filter { (text, lineHeight) ->
            // Secondary labels, like times, are indivisible. In particular do not
            // replace "Completada" with "Completa…"; its check and description remain.
            val style = MaterialTheme.typography.labelMedium.copy(fontSize = (lineHeight - 2f).sp,
                lineHeight = lineHeight.sp, fontWeight = FontWeight.SemiBold,
                platformStyle = PlatformTextStyle(includeFontPadding = false))
            calendarCellTextFitsWidth(with(density) { maxWidth.roundToPx() - 2 * 2.dp.roundToPx() },
                textMeasurer.measure(text, style = style, maxLines = 1, softWrap = false).size.width)
        }
        val dateStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, lineHeight = 16.sp,
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
            platformStyle = PlatformTextStyle(includeFontPadding = false))
        val lineStyles = textLines.map { (_, lineHeight) ->
            MaterialTheme.typography.labelMedium.copy(fontSize = (lineHeight - 2f).sp,
                lineHeight = lineHeight.sp, fontWeight = FontWeight.SemiBold,
                platformStyle = PlatformTextStyle(includeFontPadding = false))
        }
        // Default Trim.Both retains a single line's natural FontMetrics. Do not force
        // a tighter line or reduce its font; budget the same style that Text renders.
        val dateHeightPx = remember(textMeasurer, day.date.dayOfMonth, dateStyle) {
            textMeasurer.measure(day.date.dayOfMonth.toString(), style = dateStyle,
                maxLines = 1, softWrap = false).size.height
        }
        val lineHeightsPx = remember(textMeasurer, textLines, lineStyles) {
            textLines.mapIndexed { index, (text, _) ->
                textMeasurer.measure(text, style = lineStyles[index], maxLines = 1,
                    softWrap = false).size.height
            }
        }
        val cellHeightBase = maxHeight
        val visibleLines = with(density) {
            calendarCellVisibleTextLines(cellHeightBase.roundToPx(), dateHeightPx,
                if (signals.isNotEmpty()) 8.dp.roundToPx() else 0, lineHeightsPx,
                verticalPaddingPx = 1.dp.roundToPx(), spacingPx = 2.dp.roundToPx())
        }
        val cellWidthBase = maxWidth.value - 4f
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            modifier = Modifier.onGloballyPositioned { dateTextBounds = it.boundsInRoot() },
            style = dateStyle,
            color = if (isToday && !isSelected) MaterialTheme.colorScheme.primary else foreground,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { dateTextOverflows = it.hasVisualOverflow },
        )
        if (signals.isNotEmpty()) {
            CalendarCellSignals(day.date, signals, cellWidthBase,
                day.shifts.take(3).map { Color(it.shift.colorArgbSnapshot) },
                checked = isSelected || isCompletedDay,
                checkColor = if (isSelected) vigilia.active else vigilia.onCompletedContainer)
        }
        textLines.take(visibleLines).forEachIndexed { index, (text, _) ->
            Text(
                text = text,
                style = lineStyles[index],
                color = foreground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        }
    }
}

private enum class CalendarCellSignal { WORK, DAY_OFF, MEDICAL, VACATION, HOLIDAY, EXTRA, AVAILABILITY, UNDEFINED, CHECK, MORE }

/** Conventional pictograms, not an unexplained color legend. The entire cell opens the
 * full detail and already carries every condition/count in its accessible description. */
@Composable
private fun CalendarCellSignals(date: LocalDate, signals: List<CalendarCellSignal>, width: Float,
    workColors: List<Color>, checked: Boolean, checkColor: Color) {
    // A work/check shares one slot: identity colors remain below the vector check.
    // Without work, selection still gets its own first, non-optional slot.
    val ordered = if (checked && CalendarCellSignal.WORK !in signals) listOf(CalendarCellSignal.CHECK) + signals else signals
    val visible = calendarCellVisibleSignals(width, ordered.size)
    val rendered = ordered.take(visible) + if (visible < ordered.size) listOf(CalendarCellSignal.MORE) else emptyList()
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        rendered.forEach { signal ->
            val showsCheck = signal == CalendarCellSignal.CHECK || (signal == CalendarCellSignal.WORK && checked)
            Box(Modifier.size(8.dp).then(if (showsCheck) Modifier.testTag("calendar-check-$date") else Modifier)) {
            Canvas(Modifier.fillMaxSize().testTag("calendar-signal-${signal.name.lowercase()}-$date")) {
                val unit = size.width / 8f
                fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
                fun line(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float = 1f) =
                    drawLine(tint, point(x1, y1), point(x2, y2), thickness * unit)
                when (signal) {
                    CalendarCellSignal.WORK -> {
                        val colors = workColors.ifEmpty { listOf(tint) }
                        if (checked) {
                            drawLine(checkColor, point(1f, 3f), point(3f, 5f), 1.3f * unit)
                            drawLine(checkColor, point(3f, 5f), point(7f, 1f), 1.3f * unit)
                            colors.forEachIndexed { index, color ->
                                drawRect(color, point(index * 8f / colors.size, 6f),
                                    androidx.compose.ui.geometry.Size(8f / colors.size * unit, 2f * unit))
                            }
                        } else {
                        colors.forEachIndexed { index, color ->
                            drawRect(color, point(1f + index * 6f / colors.size, 3f),
                                androidx.compose.ui.geometry.Size(6f / colors.size * unit, 4f * unit))
                        }
                        drawRect(tint, point(1f, 3f), androidx.compose.ui.geometry.Size(6f * unit, 4f * unit), style = Stroke(unit))
                        line(3f, 3f, 3f, 1f); line(3f, 1f, 5f, 1f); line(5f, 1f, 5f, 3f)
                        }
                    }
                    CalendarCellSignal.DAY_OFF -> {
                        line(1f, 2f, 1f, 7f); line(7f, 4f, 7f, 7f); line(1f, 5f, 7f, 5f)
                        drawCircle(tint, unit, point(3f, 3f)); line(4f, 4f, 7f, 4f)
                    }
                    CalendarCellSignal.MEDICAL -> {
                        line(4f, 1f, 4f, 7f, 2f); line(1f, 4f, 7f, 4f, 2f)
                    }
                    CalendarCellSignal.VACATION -> {
                        val umbrella = Path().apply {
                            moveTo(unit, 4f * unit); quadraticTo(4f * unit, -unit, 7f * unit, 4f * unit); close()
                        }
                        drawPath(umbrella, tint, style = Stroke(unit)); line(4f, 4f, 4f, 7f); line(4f, 7f, 6f, 7f)
                    }
                    CalendarCellSignal.HOLIDAY -> {
                        line(2f, 1f, 2f, 7f)
                        val flag = Path().apply { moveTo(2f * unit, unit); lineTo(7f * unit, 2f * unit); lineTo(2f * unit, 4f * unit); close() }
                        drawPath(flag, tint)
                    }
                    CalendarCellSignal.EXTRA -> {
                        drawCircle(tint, 3f * unit, point(4f, 4f), style = Stroke(unit))
                        line(4f, 2f, 4f, 6f); line(2f, 4f, 6f, 4f)
                    }
                    CalendarCellSignal.AVAILABILITY -> {
                        drawCircle(tint, 3f * unit, point(4f, 4f), style = Stroke(unit))
                        line(4f, 2f, 4f, 4f); line(4f, 4f, 6f, 5f)
                    }
                    CalendarCellSignal.UNDEFINED -> {
                        val question = Path().apply {
                            moveTo(2f * unit, 2f * unit); cubicTo(2f * unit, -unit, 7f * unit, unit, 6f * unit, 3f * unit)
                            lineTo(4f * unit, 5f * unit)
                        }
                        drawPath(question, tint, style = Stroke(unit)); drawCircle(tint, .65f * unit, point(4f, 7f))
                    }
                    CalendarCellSignal.MORE -> {
                        line(4f, 1f, 4f, 7f); line(1f, 4f, 7f, 4f)
                    }
                    CalendarCellSignal.CHECK -> {
                        drawLine(checkColor, point(1f, 3f), point(3f, 5f), 1.3f * unit)
                        drawLine(checkColor, point(3f, 5f), point(7f, 1f), 1.3f * unit)
                    }
                }
            }
            }
        }
    }
}

private fun ShiftTemporalStatus.shortLabel(): String = when (this) {
    ShiftTemporalStatus.UPCOMING -> "Próxima"
    ShiftTemporalStatus.IN_PROGRESS -> "En curso"
    ShiftTemporalStatus.COMPLETED -> "Completada"
    ShiftTemporalStatus.CANCELLED -> "Cancelada"
    ShiftTemporalStatus.ABSENT -> "Ausente"
}

private fun CalendarDay.accessibilityDescription(
    isToday: Boolean,
    shiftDescription: String,
    independentExtraCount: Int,
    availabilityCount: Int,
    availabilityLabel: String?,
): String {
    val parts = mutableListOf(date.fullDisplayName())
    if (isToday) parts += "hoy"
    if (isFullyCompleted) parts += "día completado, todas las jornadas activas están completadas"
    if (shifts.size > 1) parts += "${shifts.size} turnos"
    shifts.forEach { calendarShift ->
        parts += buildString {
            append(shiftDescription)
            append(" ")
            append(calendarShift.shift.objectiveAbbreviationSnapshot)
            append(" de ")
            append(calendarShift.shift.startTimeSnapshot.format(ShiftTimeFormatter))
            append(" a ")
            append(calendarShift.shift.endTimeSnapshot.format(ShiftTimeFormatter))
            val endDayOffset = java.time.temporal.ChronoUnit.DAYS.between(
                calendarShift.shift.startAt.atZone(calendarShift.shift.zoneId).toLocalDate(),
                calendarShift.shift.endAt.atZone(calendarShift.shift.zoneId).toLocalDate(),
            )
            if (endDayOffset > 0) append(if (endDayOffset == 1L) " del día siguiente" else " $endDayOffset días después")
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
    if (availabilityCount > 0) {
        parts += if (availabilityCount == 1) {
            "una ventana de disponibilidad: ${requireNotNull(availabilityLabel)}"
        } else {
            "$availabilityCount ventanas de disponibilidad: ${requireNotNull(availabilityLabel)}"
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
