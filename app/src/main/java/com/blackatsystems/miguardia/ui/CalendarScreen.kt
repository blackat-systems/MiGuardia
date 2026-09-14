package com.blackatsystems.miguardia.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import com.blackatsystems.miguardia.core.domain.calendar.ShiftTemporalStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.availability.AvailabilityActions
import com.blackatsystems.miguardia.ui.availability.AvailabilityLoadState
import com.blackatsystems.miguardia.ui.availability.AvailabilityUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import com.blackatsystems.miguardia.ui.components.AdvancedOptionsSection
import com.blackatsystems.miguardia.ui.components.ContextHelp
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
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import com.blackatsystems.miguardia.ui.worksetup.V2FirstWorkSetGuide
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import kotlin.math.roundToInt

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
internal fun CalendarScreen(
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
    availabilityState: AvailabilityUiState,
    availabilityActions: AvailabilityActions,
    holidaySelectionState: ExceptionsUiState?,
    holidaySelectionActions: ExceptionsActions,
    appZoom: AppZoom,
    needsFirstWorkSet: Boolean,
    onOpenWorkSetup: () -> Unit,
    onCreateFirstWorkSet: () -> Unit,
    onStartV2ManualLoad: () -> Unit,
    onStartV2Recurring: () -> Unit,
    helpAnchors: HelpAnchorRegistry? = null,
    helpTourStep: HelpTourStep? = null,
) {
    val today = state.referenceInstant.atZone(AppDefaults.zoneId()).toLocalDate()
    val verticalScrollState = rememberScrollState()
    LaunchedEffect(helpTourStep, verticalScrollState.maxValue) {
        when (helpTourStep) {
            HelpTourStep.LOAD_AND_REPEAT -> verticalScrollState.scrollTo(verticalScrollState.maxValue)
            HelpTourStep.MENU,
            HelpTourStep.TODAY_CARD,
            HelpTourStep.MONTH_AND_GRID,
            HelpTourStep.DAY_DETAIL,
            HelpTourStep.PHOTOS,
            -> verticalScrollState.scrollTo(0)
            HelpTourStep.SUMMARY,
            HelpTourStep.HELP,
            null,
            -> Unit
        }
    }
    var pendingMonthChange by rememberSaveable { mutableStateOf<String?>(null) }
    var showManualLoadDiscardConfirmation by rememberSaveable { mutableStateOf(false) }
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
    val currentMonth = {
        if (state.visibleMonth == YearMonth.from(today)) onToday() else requestMonthChange("today", onToday)
    }
    TransientConfirmation(
        modifier = Modifier.padding(contentPadding).consumeWindowInsets(contentPadding),
        message = v2ShiftEditState.infoMessage
            ?: v2ShiftActualState.infoMessage
            ?: v2ManualShiftLoadState.infoMessage
            ?: v2RecurringState.infoMessage
            ?: hoursAndExtrasState.message.takeUnless {
                hoursAndExtrasState.loadState == HoursAndExtrasLoadState.ERROR
            }
            ?: availabilityState.message.takeUnless {
                availabilityState.loadState == AvailabilityLoadState.ERROR
            },
        onDismiss = if (v2ShiftEditState.infoMessage != null) {
            v2ShiftEditActions.clearMessage
        } else if (v2ShiftActualState.infoMessage != null) {
            v2ShiftActualActions.clearMessage
        } else if (v2ManualShiftLoadState.infoMessage != null) {
            v2ManualShiftLoadActions.clearMessage
        } else if (v2RecurringState.infoMessage != null) {
            v2RecurringActions.clearMessage
        } else if (
            hoursAndExtrasState.message != null &&
            hoursAndExtrasState.loadState != HoursAndExtrasLoadState.ERROR
        ) {
            hoursAndExtrasActions.clearMessage
        } else if (
            availabilityState.message != null &&
            availabilityState.loadState != AvailabilityLoadState.ERROR
        ) {
            availabilityActions.clearMessage
        } else v2ManualShiftLoadActions.clearMessage,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val verticalSpacing = if (maxHeight < 1_200.dp) 4.dp else 8.dp
            CalendarScrollContainer(
                scrollState = verticalScrollState,
                verticalSpacing = verticalSpacing,
            ) {
                Box(Modifier.helpAnchor(helpAnchors, HelpAnchor.TODAY_CARD)) {
                    NextEventCard(state = nextEventState, onRetry = onNextEventRetry)
                }
                if (state.interactionMode == CalendarInteractionMode.EDIT) {
                    Text(
                        text = when {
                            v2ManualLoadOpen -> "Cargando jornadas"
                            holidaySelectionOpen -> "Eligiendo feriados"
                            else -> "Editando calendario"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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
                MonthControls(
                    visibleMonth = state.visibleMonth,
                    onPrevious = previousMonth,
                    onNext = nextMonth,
                    onToday = currentMonth,
                    onPhotos = onOpenPhotos,
                    showPhotos = state.interactionMode == CalendarInteractionMode.VIEW,
                    appZoom = appZoom,
                    navigationEnabled = !v2ManualLoadOpen || v2DateSelectionInteractive,
                    helpAnchors = helpAnchors,
                )

                when (state.loadState) {
                    CalendarLoadState.LOADING -> LoadingCalendar()
                    CalendarLoadState.ERROR -> ErrorCalendar(
                        message = state.errorMessage ?: stringResource(R.string.calendar_error),
                        onRetry = onRetry,
                    )

                    CalendarLoadState.CONTENT -> Unit
                }

                if (hoursAndExtrasState.loadState == HoursAndExtrasLoadState.ERROR) {
                    PersistentMessage(
                        message = hoursAndExtrasState.message
                            ?: "No pudimos actualizar los extras del calendario.",
                        onRetry = hoursAndExtrasActions.retry,
                    )
                }
                if (availabilityState.loadState == AvailabilityLoadState.ERROR) {
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
                    Box(Modifier.helpAnchor(helpAnchors, HelpAnchor.DAY_DETAIL)) {
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
                            appZoom = appZoom,
                        )
                    }
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
                    } else if (activeHolidaySelectionState != null) {
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
                        )
                    }
                }

                if (state.interactionMode == CalendarInteractionMode.EDIT) {
                    if (!holidaySelectionOpen) {
                        OutlinedButton(
                            onClick = {
                                if (v2ManualLoadOpen) {
                                    showManualLoadDiscardConfirmation = true
                                } else {
                                    onFinishEditMode()
                                }
                            },
                            enabled = !v2ManualShiftLoadState.isLoading && !v2ManualShiftLoadState.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (v2ManualLoadOpen) "Cancelar carga" else "Salir de edición",
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.helpAnchor(helpAnchors, HelpAnchor.LOAD_AND_REPEAT),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onStartV2ManualLoad,
                            enabled = state.loadState == CalendarLoadState.CONTENT,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag("calendar-v2-load-shifts"),
                        ) {
                            Text(if (needsFirstWorkSet) "Cargar francos" else "Cargar jornadas o francos")
                        }
                        if (!needsFirstWorkSet) {
                            AdvancedOptionsSection(
                                initiallyExpanded = helpTourStep == HelpTourStep.LOAD_AND_REPEAT,
                                help = ContextHelp(
                                    title = "Otras formas de organizarte",
                                    whatItDoes = "Permite repetir automáticamente un horario y cambiar la configuración de tus objetivos.",
                                    howToUseIt = "Abrí estas opciones sólo si necesitás crear muchas jornadas iguales o modificar tu forma de trabajar.",
                                    example = "Si trabajás todos los lunes y miércoles, elegí Repetir jornadas.",
                                ),
                            ) {
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
                    changeEditSelection(emptySet())
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
    if (showManualLoadDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showManualLoadDiscardConfirmation = false },
            title = { Text("¿Descartar esta carga?") },
            text = { Text("Todavía no se guardó ninguna jornada. Si salís, vas a perder lo que elegiste.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showManualLoadDiscardConfirmation = false
                        v2ManualShiftLoadActions.cancel()
                        onFinishEditMode()
                    },
                ) { Text("Descartar carga") }
            },
            dismissButton = {
                TextButton(onClick = { showManualLoadDiscardConfirmation = false }) {
                    Text("Seguir cargando")
                }
            },
        )
    }
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
    helpAnchors: HelpAnchorRegistry? = null,
) {
    val previousDescription = stringResource(R.string.previous_month)
    val nextDescription = stringResource(R.string.next_month)
    val todayInline = appZoom == AppZoom.STANDARD && !showPhotos
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .helpAnchor(helpAnchors, HelpAnchor.MONTH_AND_GRID),
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
                                    .helpAnchor(helpAnchors, HelpAnchor.PHOTOS)
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
                                    .helpAnchor(helpAnchors, HelpAnchor.PHOTOS)
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
    availabilityCount: Int,
    availabilityLabel: String?,
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
        availabilityCount,
        availabilityLabel,
    )
    val isCompletedDay = day.isFullyCompleted
    val vigilia = MaterialTheme.vigiliaColors
    val background = when {
        isSelected -> vigilia.active.copy(alpha = if (vigilia.isDark) 0.30f else 0.18f)
        day.vacation != null -> vigilia.vacation.copy(alpha = if (vigilia.isDark) 0.20f else 0.12f)
        isCompletedDay -> if (vigilia.isDark) Color(0xFF173B2A) else Color(0xFFE2F4E8)
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
        if (isCompletedDay) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (vigilia.isDark) Color(0xFF9DDBB1) else Color(0xFF23663A),
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
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
        if (availabilityCount > 0) {
            if (availabilityCount == 1) {
                AutoSizeWrappedText(
                    text = requireNotNull(availabilityLabel),
                    maximum = 10.sp,
                    minimum = 6.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                AutoSizeSingleLineText(
                    text = "$availabilityCount disponibil.",
                    maximum = 10.sp,
                    minimum = 6.sp,
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
private fun AutoSizeWrappedText(
    text: String,
    maximum: TextUnit,
    minimum: TextUnit,
    fontWeight: FontWeight? = null,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        var currentSize by remember(text, maxWidth, maximum, minimum) {
            mutableStateOf(maximum)
        }
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Clip,
            fontSize = currentSize,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center,
            onTextLayout = { result ->
                if ((result.didOverflowWidth || result.didOverflowHeight) && currentSize.value > minimum.value) {
                    currentSize = (currentSize.value - 0.5f).coerceAtLeast(minimum.value).sp
                }
            },
        )
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
