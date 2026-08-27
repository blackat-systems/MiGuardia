package com.blackatsystems.miguardia.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.repository.AvailabilityWindowRepository
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import com.blackatsystems.miguardia.core.domain.repository.IndependentExtraWorkRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkCatalogRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryInput
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryProjection
import com.blackatsystems.miguardia.core.domain.summary.SummaryMetric
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalFamily
import com.blackatsystems.miguardia.core.domain.summary.calculateMonthlySummary
import com.blackatsystems.miguardia.core.domain.summary.resolveSummaryComplianceSegments
import com.blackatsystems.miguardia.core.domain.work.HoursReferenceSegment
import com.blackatsystems.miguardia.core.domain.work.HoursTargetState
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SummaryLoadState {
    LOADING,
    CONTENT,
    EMPTY,
    ERROR,
}

enum class SummarySurface {
    OVERVIEW,
    PERSONALIZATION,
    DETAIL,
}

data class SummaryUiState(
    val visibleMonth: YearMonth,
    val loadState: SummaryLoadState = SummaryLoadState.LOADING,
    val projection: MonthlySummaryProjection? = null,
    val preferences: SummaryPreferences = SummaryPreferences(),
    val surface: SummarySurface = SummarySurface.OVERVIEW,
    val selectedMetricId: String? = null,
    val introVisible: Boolean = false,
    val errorMessage: String? = null,
    val preferenceErrorMessage: String? = null,
    val overviewScrollPosition: Int = 0,
) {
    val selectedMetric: SummaryMetric?
        get() = selectedMetricId?.let { projection?.metric(it) }

    val hasSubsurface: Boolean
        get() = surface != SummarySurface.OVERVIEW

    fun visibleOptionalFamilies(): List<SummaryOptionalFamily> = preferences.orderedFamilies.filter { family ->
        preferences.isVisible(family) && projection?.optionalSections?.any { it.family == family } == true
    }
}

internal fun reduceSummaryFailure(
    current: SummaryUiState,
    requestedMonth: YearMonth,
): SummaryUiState {
    val cached = current.projection?.takeIf { it.month == requestedMonth }
    return current.copy(
        visibleMonth = requestedMonth,
        loadState = when {
            cached == null -> SummaryLoadState.ERROR
            cached.hasContent -> SummaryLoadState.CONTENT
            else -> SummaryLoadState.EMPTY
        },
        projection = cached,
        errorMessage = if (cached == null) {
            "No pudimos cargar este resumen. Reintentá."
        } else {
            "No pudimos actualizar este resumen. Reintentá sin perder el último resultado válido."
        },
    )
}

internal fun reduceSummaryProjection(
    current: SummaryUiState,
    requestedMonth: YearMonth,
    projection: MonthlySummaryProjection,
    preferences: SummaryPreferences,
    introDismissedThisSession: Boolean,
): SummaryUiState {
    require(projection.month == requestedMonth) {
        "Una observación mensual no puede publicar la proyección de otro mes"
    }
    val restoredMetric = current.selectedMetricId?.takeIf {
        current.surface == SummarySurface.DETAIL && projection.metric(it) != null
    }
    val restoredSurface = if (current.surface == SummarySurface.DETAIL && restoredMetric == null) {
        SummarySurface.OVERVIEW
    } else {
        current.surface
    }
    return current.copy(
        visibleMonth = requestedMonth,
        loadState = if (projection.hasContent) SummaryLoadState.CONTENT else SummaryLoadState.EMPTY,
        projection = projection,
        preferences = preferences,
        surface = restoredSurface,
        selectedMetricId = restoredMetric,
        introVisible = current.introVisible || (!preferences.introSeen && !introDismissedThisSession),
        errorMessage = null,
    )
}

data class SummaryActions(
    val setActive: (Boolean) -> Unit = {},
    val previousMonth: () -> Unit = {},
    val nextMonth: () -> Unit = {},
    val currentMonth: () -> Unit = {},
    val retry: () -> Unit = {},
    val openMetric: (String) -> Unit = {},
    val openPersonalization: () -> Unit = {},
    val showIntro: () -> Unit = {},
    val dismissIntro: () -> Unit = {},
    val setFamilyVisible: (SummaryOptionalFamily, Boolean) -> Unit = { _, _ -> },
    val moveFamilyUp: (SummaryOptionalFamily) -> Unit = {},
    val moveFamilyDown: (SummaryOptionalFamily) -> Unit = {},
    val retryPreferenceWrite: () -> Unit = {},
    val dismissPreferenceError: () -> Unit = {},
    val updateOverviewScrollPosition: (Int) -> Unit = {},
    val back: () -> Unit = {},
) {
    companion object {
        fun from(viewModel: SummaryViewModel): SummaryActions = SummaryActions(
            setActive = viewModel::setActive,
            previousMonth = viewModel::showPreviousMonth,
            nextMonth = viewModel::showNextMonth,
            currentMonth = viewModel::showCurrentMonth,
            retry = viewModel::retry,
            openMetric = viewModel::openMetric,
            openPersonalization = viewModel::openPersonalization,
            showIntro = viewModel::showIntro,
            dismissIntro = viewModel::dismissIntro,
            setFamilyVisible = viewModel::setFamilyVisible,
            moveFamilyUp = viewModel::moveFamilyUp,
            moveFamilyDown = viewModel::moveFamilyDown,
            retryPreferenceWrite = viewModel::retryPreferenceWrite,
            dismissPreferenceError = viewModel::dismissPreferenceError,
            updateOverviewScrollPosition = viewModel::updateOverviewScrollPosition,
            back = viewModel::back,
        )
    }
}

class SummaryViewModel(
    private val observer: MonthlySummaryObserver,
    private val preferencesStore: SummaryPreferencesStore,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val savedStateHandle: SavedStateHandle,
    externalScope: CoroutineScope? = null,
) : ViewModel() {
    private val initialMonth = savedStateHandle.get<String>(KEY_MONTH)
        ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        ?: YearMonth.now(clock.withZone(zoneId))
    private val initialSurface = savedStateHandle.get<String>(KEY_SURFACE)
        ?.let { runCatching { SummarySurface.valueOf(it) }.getOrNull() }
        ?: SummarySurface.OVERVIEW
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(
        SummaryUiState(
            visibleMonth = initialMonth,
            surface = initialSurface,
            selectedMetricId = savedStateHandle[KEY_METRIC],
            overviewScrollPosition = savedStateHandle[KEY_OVERVIEW_SCROLL] ?: 0,
        ),
    )
    val uiState: kotlinx.coroutines.flow.StateFlow<SummaryUiState> = _uiState
    private val scope = externalScope ?: viewModelScope
    private var observationJob: Job? = null
    private var introDismissedThisSession = false
    private var active = false
    private val pendingPreferenceMutations = ArrayDeque<SummaryPreferenceMutation>()
    private var preferenceMutationJob: Job? = null
    private var failedPreferenceMutation: SummaryPreferenceMutation? = null

    fun setActive(isActive: Boolean) {
        if (active == isActive) return
        active = isActive
        if (isActive) {
            observe(_uiState.value.visibleMonth)
        } else {
            observationJob?.cancel()
            observationJob = null
        }
    }

    fun showPreviousMonth() = setMonth(_uiState.value.visibleMonth.minusMonths(1))

    fun showNextMonth() = setMonth(_uiState.value.visibleMonth.plusMonths(1))

    fun showCurrentMonth() = setMonth(YearMonth.now(clock.withZone(zoneId)))

    fun retry() {
        if (active) observe(_uiState.value.visibleMonth)
    }

    fun openMetric(id: String) {
        if (_uiState.value.projection?.metric(id) == null) return
        savedStateHandle[KEY_SURFACE] = SummarySurface.DETAIL.name
        savedStateHandle[KEY_METRIC] = id
        _uiState.update { it.copy(surface = SummarySurface.DETAIL, selectedMetricId = id) }
    }

    fun openPersonalization() {
        savedStateHandle[KEY_SURFACE] = SummarySurface.PERSONALIZATION.name
        savedStateHandle.remove<String>(KEY_METRIC)
        _uiState.update { it.copy(surface = SummarySurface.PERSONALIZATION, selectedMetricId = null) }
    }

    fun showIntro() = _uiState.update { it.copy(introVisible = true) }

    fun dismissIntro() {
        introDismissedThisSession = true
        _uiState.update { it.copy(introVisible = false) }
        applyPreferenceMutation(SummaryPreferenceMutation.MarkIntroSeen)
    }

    fun setFamilyVisible(family: SummaryOptionalFamily, visible: Boolean) {
        applyPreferenceMutation(SummaryPreferenceMutation.SetVisible(family, visible))
    }

    fun moveFamilyUp(family: SummaryOptionalFamily) {
        applyPreferenceMutation(SummaryPreferenceMutation.Move(family, -1))
    }

    fun moveFamilyDown(family: SummaryOptionalFamily) {
        applyPreferenceMutation(SummaryPreferenceMutation.Move(family, 1))
    }

    fun retryPreferenceWrite() {
        if (failedPreferenceMutation == null) return
        failedPreferenceMutation = null
        _uiState.update { it.copy(preferenceErrorMessage = null) }
        drainPreferenceMutations()
    }

    fun dismissPreferenceError() {
        val failed = failedPreferenceMutation
        if (failed != null && pendingPreferenceMutations.firstOrNull() == failed) {
            pendingPreferenceMutations.removeFirst()
        }
        failedPreferenceMutation = null
        _uiState.update { it.copy(preferenceErrorMessage = null) }
        drainPreferenceMutations()
    }

    fun updateOverviewScrollPosition(position: Int) {
        require(position >= 0) { "La posición del Resumen no puede ser negativa" }
        if (position == _uiState.value.overviewScrollPosition) return
        savedStateHandle[KEY_OVERVIEW_SCROLL] = position
        _uiState.update { it.copy(overviewScrollPosition = position) }
    }

    fun back() {
        savedStateHandle[KEY_SURFACE] = SummarySurface.OVERVIEW.name
        savedStateHandle.remove<String>(KEY_METRIC)
        _uiState.update { it.copy(surface = SummarySurface.OVERVIEW, selectedMetricId = null) }
    }

    private fun setMonth(month: YearMonth) {
        if (month == _uiState.value.visibleMonth) return
        savedStateHandle[KEY_MONTH] = month.toString()
        savedStateHandle[KEY_SURFACE] = SummarySurface.OVERVIEW.name
        savedStateHandle.remove<String>(KEY_METRIC)
        savedStateHandle[KEY_OVERVIEW_SCROLL] = 0
        _uiState.update {
            it.copy(
                visibleMonth = month,
                loadState = SummaryLoadState.LOADING,
                projection = null,
                surface = SummarySurface.OVERVIEW,
                selectedMetricId = null,
                errorMessage = null,
                overviewScrollPosition = 0,
            )
        }
        if (active) observe(month)
    }

    private fun observe(month: YearMonth) {
        observationJob?.cancel()
        _uiState.update { current ->
            val sameMonthProjection = current.projection?.takeIf { it.month == month }
            current.copy(
                loadState = if (sameMonthProjection == null) SummaryLoadState.LOADING else current.loadState,
                errorMessage = null,
            )
        }
        observationJob = scope.launch {
            combine(observer.observe(month), preferencesStore.preferences) { projection, preferences ->
                projection to preferences
            }
                .catch {
                    _uiState.update { current -> reduceSummaryFailure(current, month) }
                }
                .collect { (projection, preferences) ->
                    val current = _uiState.value
                    val updated = reduceSummaryProjection(
                        current = current,
                        requestedMonth = month,
                        projection = projection,
                        preferences = preferences,
                        introDismissedThisSession = introDismissedThisSession,
                    )
                    if (updated.surface != current.surface) {
                        savedStateHandle[KEY_SURFACE] = updated.surface.name
                    }
                    if (updated.selectedMetricId != current.selectedMetricId) {
                        savedStateHandle.remove<String>(KEY_METRIC)
                    }
                    _uiState.value = updated
                }
        }
    }

    private fun applyPreferenceMutation(mutation: SummaryPreferenceMutation) {
        pendingPreferenceMutations.addLast(mutation)
        drainPreferenceMutations()
    }

    private fun drainPreferenceMutations() {
        if (
            failedPreferenceMutation != null ||
            preferenceMutationJob?.isActive == true ||
            pendingPreferenceMutations.isEmpty()
        ) {
            return
        }
        preferenceMutationJob = scope.launch {
            while (failedPreferenceMutation == null && pendingPreferenceMutations.isNotEmpty()) {
                val mutation = pendingPreferenceMutations.first()
                try {
                    when (mutation) {
                        SummaryPreferenceMutation.MarkIntroSeen -> preferencesStore.markIntroSeen()
                        is SummaryPreferenceMutation.Move -> preferencesStore.move(mutation.family, mutation.offset)
                        is SummaryPreferenceMutation.SetVisible ->
                            preferencesStore.setVisible(mutation.family, mutation.visible)
                    }
                    pendingPreferenceMutations.removeFirst()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    failedPreferenceMutation = mutation
                    _uiState.update {
                        it.copy(
                            preferenceErrorMessage =
                                "No pudimos guardar este cambio. Podés reintentarlo sin cerrar la app.",
                        )
                    }
                }
            }
        }
    }

    class Factory(
        private val observer: MonthlySummaryObserver,
        private val preferencesStore: SummaryPreferencesStore,
        private val clock: Clock,
        private val zoneId: ZoneId,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(SummaryViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return SummaryViewModel(
                observer,
                preferencesStore,
                clock,
                zoneId,
                extras.createSavedStateHandle(),
            ) as T
        }
    }

    private companion object {
        const val KEY_MONTH = "summary.visibleMonth"
        const val KEY_SURFACE = "summary.surface"
        const val KEY_METRIC = "summary.metric"
        const val KEY_OVERVIEW_SCROLL = "summary.overviewScroll"
    }
}

private sealed interface SummaryPreferenceMutation {
    data object MarkIntroSeen : SummaryPreferenceMutation
    data class Move(val family: SummaryOptionalFamily, val offset: Int) : SummaryPreferenceMutation
    data class SetVisible(
        val family: SummaryOptionalFamily,
        val visible: Boolean,
    ) : SummaryPreferenceMutation
}

data class SummarySectorSources(
    val sector: WorkSector,
    val shifts: List<V2ShiftWrite>,
    val actuals: List<ShiftActualAggregate>,
    val extras: List<IndependentExtraWorkRecord>,
    val availability: List<AvailabilityWindowRecord>,
    val catalog: WorkCatalog,
)

fun interface MonthlySummaryObserver {
    fun observe(month: YearMonth): Flow<MonthlySummaryProjection>
}

class SummaryObserver(
    private val configurations: WorkConfigurationRepository,
    private val catalogs: WorkCatalogRepository,
    private val shifts: V2ShiftRepository,
    private val actuals: ShiftActualRepository,
    private val extras: IndependentExtraWorkRepository,
    private val availability: AvailabilityWindowRepository,
    private val holidays: HolidayRepository,
    private val medicalLeaves: MedicalLeaveRepository,
    private val vacations: VacationRepository,
    private val explicitStatuses: ExplicitDayStatusRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : MonthlySummaryObserver {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observe(month: YearMonth): Flow<MonthlySummaryProjection> = configurations.observe()
        .filterNotNull()
        .flatMapLatest { history ->
            observeSectorSources(history).flatMapLatest { sectorSources ->
                observeDateSources(history, month, sectorSources).map { dates ->
                    calculateMonthlySummary(
                        MonthlySummaryInput(
                            month = month,
                            configuration = history,
                            shifts = sectorSources.flatMap(SummarySectorSources::shifts),
                            actuals = sectorSources.flatMap(SummarySectorSources::actuals),
                            independentExtras = sectorSources.flatMap(SummarySectorSources::extras),
                            availabilityWindows = sectorSources.flatMap(SummarySectorSources::availability),
                            catalogs = sectorSources.map(SummarySectorSources::catalog),
                            holidays = dates.holidays,
                            medicalLeaves = dates.medicalLeaves,
                            vacations = dates.vacations,
                            explicitDayStatuses = dates.explicitStatuses,
                        ),
                        clock = clock,
                        zoneId = zoneId,
                    )
                }
            }
        }

    private fun observeSectorSources(history: WorkConfigurationHistory): Flow<List<SummarySectorSources>> {
        val timelineId = history.timeline.id
        val sectors = history.timeline.revisions.map { it.value.sector }.distinct()
        val sectorFlows = sectors.map { sector ->
            val shiftsAndActuals = combine(
                shifts.observeAll(timelineId, sector),
                actuals.observeAllActuals(timelineId, sector),
            ) { shiftValues, actualValues -> shiftValues to actualValues.values.toList() }
            val extrasAndAvailability = combine(
                extras.observeAll(timelineId, sector),
                availability.observeAll(timelineId, sector),
            ) { extraValues, availabilityValues -> extraValues to availabilityValues }
            combine(
                shiftsAndActuals,
                extrasAndAvailability,
                catalogs.observeCatalog(timelineId, sector),
            ) { work, secondary, catalog ->
                SummarySectorSources(
                    sector = sector,
                    shifts = work.first,
                    actuals = work.second,
                    extras = secondary.first,
                    availability = secondary.second,
                    catalog = catalog,
                )
            }
        }
        return if (sectorFlows.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(sectorFlows) { values -> values.toList() }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeDateSources(
        history: WorkConfigurationHistory,
        month: YearMonth,
        sectors: List<SummarySectorSources>,
    ): Flow<SummaryDateSources> {
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        val complianceSegments = resolveSummaryComplianceSegments(history, month).filter { segment ->
            segment.endExclusive != LocalDate.MAX && when (segment.target) {
                is HoursTargetState.Defined,
                HoursTargetState.MissingPerPeriodValue,
                -> true
                HoursTargetState.Unknown -> segment.naturalWindow != null
                HoursTargetState.PendingSetup,
                HoursTargetState.NotUsed,
                -> false
            }
        }
        val protectionStart = minOf(monthStart, complianceSegments.minOfOrNull { it.startInclusive } ?: monthStart)
        val protectionEnd = maxOf(
            monthEnd,
            complianceSegments.maxOfOrNull { it.endExclusive.minusDays(1) } ?: monthEnd,
        )
        val actualsById = sectors.flatMap(SummarySectorSources::actuals).associateBy { it.record.shiftId }
        val sourceLastDates = buildList {
            sectors.flatMap(SummarySectorSources::shifts).forEach { write ->
                val actual = actualsById[write.shift.id]
                val owner = actual?.record?.actualStart?.atZone(write.shift.zoneId)?.toLocalDate()
                    ?: write.shift.startAt.atZone(write.shift.zoneId).toLocalDate()
                if (YearMonth.from(owner) == month) {
                    val end = actual?.record?.actualEnd ?: write.shift.endAt
                    add(end.minusNanos(1).atZone(write.shift.zoneId).toLocalDate())
                }
            }
            sectors.flatMap(SummarySectorSources::extras).forEach { extra ->
                if (YearMonth.from(extra.ownerLocalDate) == month) {
                    add(extra.end.minusNanos(1).atZone(extra.zoneId).toLocalDate())
                }
            }
        }
        val holidayEnd = maxOf(monthEnd, sourceLastDates.maxOrNull() ?: monthEnd)
        return combine(
            medicalLeaves.observeIntersecting(protectionStart, protectionEnd),
            vacations.observeOverlapping(protectionStart, protectionEnd),
            holidays.observeBetween(monthStart, holidayEnd),
            explicitStatuses.observeBetween(monthStart, monthEnd),
            temporalPulse(month, complianceSegments, sectors),
        ) { medical, vacationValues, holidayValues, statusValues, _ ->
            SummaryDateSources(medical, vacationValues, holidayValues, statusValues)
        }
    }

    private fun temporalPulse(
        month: YearMonth,
        complianceSegments: List<HoursReferenceSegment>,
        sectors: List<SummarySectorSources>,
    ): Flow<Unit> = flow {
        val intervals = temporalIntervals(month, complianceSegments, sectors)
        while (true) {
            emit(Unit)
            val now = clock.instant()
            val nextUpdate = nextSummaryTemporalUpdate(now, intervals) ?: break
            val waitMillis = runCatching { Duration.between(now, nextUpdate).toMillis() }
                .getOrDefault(Long.MAX_VALUE)
                .coerceAtLeast(1L)
            delay(waitMillis)
        }
    }

    private fun temporalIntervals(
        month: YearMonth,
        complianceSegments: List<HoursReferenceSegment>,
        sectors: List<SummarySectorSources>,
    ): List<Pair<Instant, Instant>> = buildList {
        fun contributesToVisibleSummary(ownerDate: LocalDate): Boolean =
            YearMonth.from(ownerDate) == month || complianceSegments.any { segment ->
                ownerDate >= segment.startInclusive && ownerDate < segment.endExclusive
            }
        val actualsByShiftId = sectors.flatMap(SummarySectorSources::actuals).associateBy { it.record.shiftId }
        sectors.flatMap(SummarySectorSources::shifts)
            .filter { it.shift.status == com.blackatsystems.miguardia.core.domain.model.ShiftStatus.PLANNED }
            .forEach { write ->
                val actual = actualsByShiftId[write.shift.id]
                val start = actual?.record?.actualStart ?: write.shift.startAt
                val end = actual?.record?.actualEnd ?: write.shift.endAt
                if (contributesToVisibleSummary(start.atZone(write.shift.zoneId).toLocalDate())) {
                    add(start to end)
                }
            }
        sectors.flatMap(SummarySectorSources::extras).forEach { extra ->
            if (contributesToVisibleSummary(extra.ownerLocalDate)) add(extra.start to extra.end)
        }
        sectors.flatMap(SummarySectorSources::availability).forEach { window ->
            if (YearMonth.from(window.ownerLocalDate) == month) add(window.start to window.end)
        }
    }
}

internal fun nextSummaryTemporalUpdate(
    now: Instant,
    intervals: List<Pair<Instant, Instant>>,
): Instant? {
    intervals.forEach { (start, end) -> require(start < end) { "Un límite temporal debe tener duración positiva" } }
    val activeEnd = intervals
        .asSequence()
        .filter { (start, end) -> start <= now && now < end }
        .map { it.second }
        .minOrNull()
    if (activeEnd != null) {
        val nextMinute = runCatching {
            now.truncatedTo(ChronoUnit.MINUTES).plus(1L, ChronoUnit.MINUTES)
        }.getOrNull()
        return listOfNotNull(activeEnd, nextMinute).minOrNull()
    }
    return intervals
        .asSequence()
        .map { it.first }
        .filter { it > now }
        .minOrNull()
}

private data class SummaryDateSources(
    val medicalLeaves: List<com.blackatsystems.miguardia.core.domain.model.MedicalLeave>,
    val vacations: List<com.blackatsystems.miguardia.core.domain.model.Vacation>,
    val holidays: List<com.blackatsystems.miguardia.core.domain.model.Holiday>,
    val explicitStatuses: List<com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus>,
)
