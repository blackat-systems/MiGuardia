package com.blackatsystems.miguardia.reports

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.core.domain.report.MonthlyWorkReportProjection
import com.blackatsystems.miguardia.core.domain.report.FutureReportMonthException
import com.blackatsystems.miguardia.core.domain.report.ReportFormat
import com.blackatsystems.miguardia.core.domain.report.ReportPrivacySelection
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.SchedulePhotoRepository
import com.blackatsystems.miguardia.profile.GuardProfileStore
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReportsStage {
    LOADING,
    CONTENT,
    EMPTY,
    GENERATING,
    READY,
    SAVING,
    SHARING,
    ERROR,
}

data class ReportPhotoChoice(
    val id: UUID,
    val label: String,
    val selected: Boolean,
    val available: Boolean = true,
)

data class ReportsUiState(
    val isOpen: Boolean = false,
    val month: YearMonth,
    val format: ReportFormat = ReportFormat.PDF,
    val privacy: ReportPrivacySelection = ReportPrivacySelection(),
    val stage: ReportsStage = ReportsStage.LOADING,
    val preview: MonthlyWorkReportProjection? = null,
    val generated: GeneratedLocalReport? = null,
    val availablePhotos: List<ReportPhotoChoice> = emptyList(),
    val photoSelectionExpanded: Boolean = false,
    val displayNameAvailable: Boolean = false,
    val medicalConfirmationPending: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    val projection: MonthlyWorkReportProjection?
        get() = generated?.projection ?: preview

    val artifact: ReportArtifact?
        get() = generated?.artifact

    val isBusy: Boolean
        get() = stage in setOf(
            ReportsStage.LOADING,
            ReportsStage.GENERATING,
            ReportsStage.SAVING,
            ReportsStage.SHARING,
        )
}

data class ReportsActions(
    val open: (YearMonth) -> Unit = {},
    val close: () -> Unit = {},
    val setFormat: (ReportFormat) -> Unit = {},
    val setDisplayNameIncluded: (Boolean) -> Unit = {},
    val setPositionIncluded: (Boolean) -> Unit = {},
    val setShiftNotesIncluded: (Boolean) -> Unit = {},
    val requestMedicalNotes: (Boolean) -> Unit = {},
    val confirmMedicalNotes: () -> Unit = {},
    val dismissMedicalConfirmation: () -> Unit = {},
    val setPhotoSelectionExpanded: (Boolean) -> Unit = {},
    val setPhotoSelected: (UUID, Boolean) -> Unit = { _, _ -> },
    val generate: () -> Unit = {},
    val regenerate: () -> Unit = {},
    val requestSave: () -> Boolean = { false },
    val saveTo: (Uri) -> Unit = {},
    val cancelSave: () -> Unit = {},
    val requestShare: () -> Boolean = { false },
    val shareLaunched: (Boolean, String?) -> Unit = { _, _ -> },
    val retry: () -> Unit = {},
    val clearMessage: () -> Unit = {},
) {
    companion object {
        fun from(viewModel: ReportsViewModel): ReportsActions = ReportsActions(
            open = viewModel::open,
            close = viewModel::close,
            setFormat = viewModel::setFormat,
            setDisplayNameIncluded = viewModel::setDisplayNameIncluded,
            setPositionIncluded = viewModel::setPositionIncluded,
            setShiftNotesIncluded = viewModel::setShiftNotesIncluded,
            requestMedicalNotes = viewModel::requestMedicalNotes,
            confirmMedicalNotes = viewModel::confirmMedicalNotes,
            dismissMedicalConfirmation = viewModel::dismissMedicalConfirmation,
            setPhotoSelectionExpanded = viewModel::setPhotoSelectionExpanded,
            setPhotoSelected = viewModel::setPhotoSelected,
            generate = viewModel::generate,
            regenerate = viewModel::regenerate,
            requestSave = viewModel::requestSave,
            saveTo = viewModel::saveTo,
            cancelSave = viewModel::cancelSave,
            requestShare = viewModel::requestShare,
            shareLaunched = viewModel::shareLaunched,
            retry = viewModel::retry,
            clearMessage = viewModel::clearMessage,
        )
    }
}

class ReportsViewModel(
    private val generator: ReportGenerator,
    private val destinationWriter: ReportDestination,
    private val photoRepository: SchedulePhotoRepository,
    private val profileStore: GuardProfileStore,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val restoredOpen = savedState[KEY_OPEN] ?: false
    private val restoredMonth = savedState.get<String>(KEY_MONTH)
        ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        ?: YearMonth.now(clock.withZone(zoneId))
    private val restoredFormat = savedState.get<String>(KEY_FORMAT)
        ?.let { runCatching { ReportFormat.valueOf(it) }.getOrNull() }
        ?: ReportFormat.PDF
    private val restoredStage = savedState.get<String>(KEY_STAGE)
        ?.let { runCatching { ReportsStage.valueOf(it) }.getOrNull() }
        ?: ReportsStage.LOADING
    private val restoredPrivacy = ReportPrivacySelection(
        includeDisplayName = savedState[KEY_NAME] ?: false,
        includePosition = savedState[KEY_POSITION] ?: false,
        includeShiftNotes = savedState[KEY_SHIFT_NOTES] ?: false,
        includeMedicalNotes = savedState[KEY_MEDICAL_NOTES] ?: false,
        selectedPhotoIds = savedState.get<ArrayList<String>>(KEY_PHOTOS)
            .orEmpty()
            .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            .take(ReportPrivacySelection.MAX_REPORT_PHOTOS)
            .toSet(),
    )
    private val _uiState = MutableStateFlow(
        ReportsUiState(
            isOpen = restoredOpen,
            month = restoredMonth,
            format = restoredFormat,
            privacy = restoredPrivacy,
            photoSelectionExpanded = savedState[KEY_PHOTO_EXPANDED] ?: false,
        ),
    )
    val uiState: StateFlow<ReportsUiState> = _uiState
    private var operation: Job? = null
    private var failureOperation: FailureOperation? = null
    private var restorationMessage: String? = if (
        restoredOpen && restoredStage in setOf(
            ReportsStage.GENERATING,
            ReportsStage.SAVING,
            ReportsStage.SHARING,
        )
    ) {
        "La operación anterior se interrumpió y no se marcó como completada. Revisá y reintentá."
    } else {
        null
    }

    init {
        if (restoredOpen) refreshPreview()
    }

    fun open(month: YearMonth) {
        operation?.cancel()
        failureOperation = null
        savedState[KEY_OPEN] = true
        savedState[KEY_MONTH] = month.toString()
        savedState[KEY_FORMAT] = ReportFormat.PDF.name
        persistStage(ReportsStage.LOADING)
        restorationMessage = null
        clearSavedPrivacy()
        _uiState.value = ReportsUiState(
            isOpen = true,
            month = month,
            format = ReportFormat.PDF,
            privacy = ReportPrivacySelection(),
            stage = ReportsStage.LOADING,
        )
        refreshPreview()
    }

    fun close() {
        if (_uiState.value.stage in setOf(ReportsStage.GENERATING, ReportsStage.SAVING, ReportsStage.SHARING)) return
        operation?.cancel()
        savedState[KEY_OPEN] = false
        clearSavedPrivacy()
        _uiState.update {
            it.copy(
                isOpen = false,
                medicalConfirmationPending = false,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun setFormat(format: ReportFormat) {
        if (!draftCanChange()) return
        val current = _uiState.value
        if (current.format == format) return
        val privacy = if (format == ReportFormat.PDF) {
            current.privacy
        } else {
            current.privacy.copy(selectedPhotoIds = emptySet())
        }
        persistPrivacy(privacy)
        updateDraft {
            it.copy(
                format = format,
                privacy = privacy,
                photoSelectionExpanded = false,
            )
        }
    }

    fun setDisplayNameIncluded(included: Boolean) = updatePrivacy {
        it.copy(includeDisplayName = included && _uiState.value.displayNameAvailable)
    }

    fun setPositionIncluded(included: Boolean) = updatePrivacy { it.copy(includePosition = included) }

    fun setShiftNotesIncluded(included: Boolean) = updatePrivacy { it.copy(includeShiftNotes = included) }

    fun requestMedicalNotes(included: Boolean) {
        if (!draftCanChange()) return
        if (included) {
            _uiState.update { it.copy(medicalConfirmationPending = true) }
        } else {
            updatePrivacy { it.copy(includeMedicalNotes = false) }
        }
    }

    fun confirmMedicalNotes() {
        if (!draftCanChange()) return
        _uiState.update { it.copy(medicalConfirmationPending = false) }
        updatePrivacy { it.copy(includeMedicalNotes = true) }
    }

    fun dismissMedicalConfirmation() =
        _uiState.update { it.copy(medicalConfirmationPending = false) }

    fun setPhotoSelectionExpanded(expanded: Boolean) {
        if (!draftCanChange() || _uiState.value.format != ReportFormat.PDF) return
        savedState[KEY_PHOTO_EXPANDED] = expanded
        if (!expanded) {
            _uiState.update { it.copy(photoSelectionExpanded = false) }
            updatePrivacy { it.copy(selectedPhotoIds = emptySet()) }
        } else {
            _uiState.update { it.copy(photoSelectionExpanded = true, errorMessage = null) }
        }
    }

    fun setPhotoSelected(id: UUID, selected: Boolean) {
        if (!draftCanChange() || _uiState.value.format != ReportFormat.PDF) return
        val choice = _uiState.value.availablePhotos.firstOrNull { it.id == id } ?: return
        if (selected && !choice.available) {
            _uiState.update { it.copy(errorMessage = "Esa foto ya no está disponible. Desmarcala para continuar.") }
            return
        }
        val current = _uiState.value.privacy.selectedPhotoIds
        if (selected && id !in current && current.size >= ReportPrivacySelection.MAX_REPORT_PHOTOS) {
            _uiState.update {
                it.copy(errorMessage = "Podés incluir como máximo 12 fotos. Desmarcá una antes de continuar.")
            }
            return
        }
        updatePrivacy { privacy ->
            privacy.copy(selectedPhotoIds = if (selected) current + id else current - id)
        }
    }

    fun generate() = startGeneration()

    fun regenerate() = startGeneration()

    fun requestSave(): Boolean {
        val state = _uiState.value
        if (state.stage != ReportsStage.READY || state.artifact == null) return false
        persistStage(ReportsStage.SAVING)
        _uiState.update {
            it.copy(
                stage = ReportsStage.SAVING,
                errorMessage = null,
                infoMessage = null,
            )
        }
        return true
    }

    fun saveTo(destination: Uri) {
        val artifact = _uiState.value.artifact
        if (_uiState.value.stage != ReportsStage.SAVING || artifact == null) {
            viewModelScope.launch {
                val discarded = runCatching { destinationWriter.discard(destination) }.getOrDefault(false)
                val message = if (discarded) {
                    "El guardado anterior se interrumpió y retiramos el archivo vacío. Revisá y reintentá."
                } else {
                    "El guardado anterior se interrumpió. Revisá el destino elegido antes de reintentar."
                }
                restorationMessage = message
                _uiState.update { state ->
                    if (state.isOpen) state.copy(infoMessage = message) else state
                }
            }
            return
        }
        operation = viewModelScope.launch {
            try {
                destinationWriter.save(artifact, destination)
                failureOperation = null
                persistStage(ReportsStage.READY)
                _uiState.update { it.copy(stage = ReportsStage.READY, infoMessage = "Informe guardado.") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failureOperation = FailureOperation.SAVE_OR_SHARE
                persistStage(ReportsStage.ERROR)
                _uiState.update {
                    it.copy(
                        stage = ReportsStage.ERROR,
                        errorMessage = error.safeMessage(
                            "No pudimos guardar el informe. El archivo privado sigue listo para reintentar.",
                        ),
                    )
                }
            }
        }
    }

    fun cancelSave() {
        if (_uiState.value.stage == ReportsStage.SAVING) {
            persistStage(ReportsStage.READY)
            _uiState.update { it.copy(stage = ReportsStage.READY, infoMessage = "Guardado cancelado.") }
        }
    }

    fun requestShare(): Boolean {
        val state = _uiState.value
        if (state.stage != ReportsStage.READY || state.artifact == null) return false
        persistStage(ReportsStage.SHARING)
        _uiState.update {
            it.copy(
                stage = ReportsStage.SHARING,
                errorMessage = null,
                infoMessage = null,
            )
        }
        return true
    }

    fun shareLaunched(success: Boolean, message: String?) {
        if (_uiState.value.stage != ReportsStage.SHARING) return
        if (success) {
            failureOperation = null
            persistStage(ReportsStage.READY)
            _uiState.update { it.copy(stage = ReportsStage.READY, infoMessage = "Selector para compartir abierto.") }
        } else {
            failureOperation = FailureOperation.SAVE_OR_SHARE
            persistStage(ReportsStage.ERROR)
            _uiState.update {
                it.copy(
                    stage = ReportsStage.ERROR,
                    errorMessage = message ?: "No encontramos una aplicación compatible para compartir el informe.",
                )
            }
        }
    }

    fun retry() {
        when (failureOperation) {
            FailureOperation.PREVIEW -> refreshPreview()
            FailureOperation.GENERATE -> startGeneration()
            FailureOperation.SAVE_OR_SHARE -> {
                failureOperation = null
                persistStage(ReportsStage.READY)
                _uiState.update { it.copy(stage = ReportsStage.READY, errorMessage = null) }
            }
            null -> refreshPreview()
        }
    }

    fun clearMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    private fun startGeneration() {
        val state = _uiState.value
        if (!state.isOpen || state.isBusy) return
        val month = state.month
        val format = state.format
        val privacy = state.privacy
        val protected = state.artifact?.file
        persistStage(ReportsStage.GENERATING)
        _uiState.update { it.copy(stage = ReportsStage.GENERATING, errorMessage = null, infoMessage = null) }
        operation?.cancel()
        operation = viewModelScope.launch {
            try {
                val generated = generator.generate(month, format, privacy, protected)
                if (!_uiState.value.isOpen || _uiState.value.month != month) return@launch
                failureOperation = null
                persistStage(ReportsStage.READY)
                _uiState.update {
                    it.copy(
                        stage = ReportsStage.READY,
                        generated = generated,
                        preview = generated.projection,
                        errorMessage = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failureOperation = FailureOperation.GENERATE
                persistStage(ReportsStage.ERROR)
                _uiState.update {
                    it.copy(
                        stage = ReportsStage.ERROR,
                        errorMessage = error.safeMessage("No pudimos generar el informe. Revisá las inclusiones y reintentá."),
                    )
                }
            }
        }
    }

    private fun updatePrivacy(change: (ReportPrivacySelection) -> ReportPrivacySelection) {
        if (!draftCanChange()) return
        val updated = change(_uiState.value.privacy)
        persistPrivacy(updated)
        _uiState.update { it.copy(privacy = updated, generated = null, errorMessage = null, infoMessage = null) }
        refreshPreview()
    }

    private fun updateDraft(change: (ReportsUiState) -> ReportsUiState) {
        if (!draftCanChange()) return
        val updated = change(_uiState.value).copy(generated = null, errorMessage = null, infoMessage = null)
        savedState[KEY_FORMAT] = updated.format.name
        savedState[KEY_PHOTO_EXPANDED] = updated.photoSelectionExpanded
        _uiState.value = updated
        refreshPreview()
    }

    private fun draftCanChange(): Boolean = _uiState.value.isOpen && !_uiState.value.isBusy

    private fun refreshPreview() {
        val state = _uiState.value
        if (!state.isOpen) return
        val month = state.month
        val format = state.format
        val privacy = state.privacy
        operation?.cancel()
        persistStage(ReportsStage.LOADING)
        _uiState.update { it.copy(stage = ReportsStage.LOADING, errorMessage = null, infoMessage = null) }
        operation = viewModelScope.launch {
            var previewContext: PreviewContext? = null
            try {
                val context = coroutineScope {
                    val photos = async { photoRepository.observeForMonth(month).first() }
                    val profile = async { profileStore.current() }
                    PreviewContext(photos.await(), profile.await().displayName != null)
                }
                previewContext = context
                if (!_uiState.value.isOpen || _uiState.value.month != month) return@launch
                val choices = context.photoChoices(privacy.selectedPhotoIds)
                _uiState.update {
                    it.copy(
                        availablePhotos = choices,
                        displayNameAvailable = context.displayNameAvailable,
                    )
                }
                val projection = generator.preview(month, format, privacy)
                if (!_uiState.value.isOpen || _uiState.value.month != month) return@launch
                failureOperation = null
                val completedStage = if (projection.hasActivity) ReportsStage.CONTENT else ReportsStage.EMPTY
                persistStage(completedStage)
                val restoredInfo = restorationMessage
                restorationMessage = null
                _uiState.update {
                    it.copy(
                        stage = completedStage,
                        preview = projection,
                        generated = null,
                        availablePhotos = choices,
                        displayNameAvailable = context.displayNameAvailable,
                        errorMessage = null,
                        infoMessage = restoredInfo,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failureOperation = FailureOperation.PREVIEW
                persistStage(ReportsStage.ERROR)
                _uiState.update {
                    it.copy(
                        stage = ReportsStage.ERROR,
                        availablePhotos = previewContext?.photoChoices(privacy.selectedPhotoIds)
                            ?: it.availablePhotos,
                        displayNameAvailable = previewContext?.displayNameAvailable
                            ?: it.displayNameAvailable,
                        errorMessage = error.safeMessage("No pudimos preparar la vista previa del informe."),
                    )
                }
            }
        }
    }

    private fun persistPrivacy(value: ReportPrivacySelection) {
        savedState[KEY_NAME] = value.includeDisplayName
        savedState[KEY_POSITION] = value.includePosition
        savedState[KEY_SHIFT_NOTES] = value.includeShiftNotes
        savedState[KEY_MEDICAL_NOTES] = value.includeMedicalNotes
        savedState[KEY_PHOTOS] = ArrayList(value.selectedPhotoIds.map(UUID::toString).sorted())
    }

    private fun persistStage(stage: ReportsStage) {
        savedState[KEY_STAGE] = stage.name
    }

    private fun clearSavedPrivacy() {
        savedState[KEY_NAME] = false
        savedState[KEY_POSITION] = false
        savedState[KEY_SHIFT_NOTES] = false
        savedState[KEY_MEDICAL_NOTES] = false
        savedState[KEY_PHOTOS] = arrayListOf<String>()
        savedState[KEY_PHOTO_EXPANDED] = false
    }

    class Factory(
        private val generator: ReportGenerator,
        private val destinationWriter: ReportDestination,
        private val photoRepository: SchedulePhotoRepository,
        private val profileStore: GuardProfileStore,
        private val clock: Clock,
        private val zoneId: ZoneId,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(ReportsViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ReportsViewModel(
                generator,
                destinationWriter,
                photoRepository,
                profileStore,
                clock,
                zoneId,
                extras.createSavedStateHandle(),
            ) as T
        }
    }

    private data class PreviewContext(
        val photos: List<SchedulePhoto>,
        val displayNameAvailable: Boolean,
    ) {
        fun photoChoices(selectedIds: Set<UUID>): List<ReportPhotoChoice> {
            val available = photos.map { photo ->
                ReportPhotoChoice(
                    id = photo.id,
                    label = photo.objectiveNameSnapshot?.takeIf(String::isNotBlank)
                        ?: "Foto del ${photo.month}",
                    selected = photo.id in selectedIds,
                )
            }
            val availableIds = available.mapTo(hashSetOf()) { it.id }
            val unavailable = (selectedIds - availableIds)
                .sortedBy(UUID::toString)
                .map { id ->
                    ReportPhotoChoice(
                        id = id,
                        label = "Foto elegida ya no disponible; desmarcala para continuar",
                        selected = true,
                        available = false,
                    )
                }
            return available + unavailable
        }
    }

    private enum class FailureOperation {
        PREVIEW,
        GENERATE,
        SAVE_OR_SHARE,
    }

    private companion object {
        const val KEY_OPEN = "reports.open"
        const val KEY_MONTH = "reports.month"
        const val KEY_FORMAT = "reports.format"
        const val KEY_NAME = "reports.name"
        const val KEY_POSITION = "reports.position"
        const val KEY_SHIFT_NOTES = "reports.shiftNotes"
        const val KEY_MEDICAL_NOTES = "reports.medicalNotes"
        const val KEY_PHOTOS = "reports.photos"
        const val KEY_PHOTO_EXPANDED = "reports.photoExpanded"
        const val KEY_STAGE = "reports.stage"
    }
}

private fun Throwable.safeMessage(fallback: String): String {
    val isSafeReportError = this is FutureReportMonthException ||
        this is InvalidLocalDataException ||
        this is ReportArtifactException ||
        this is ReportAssetException ||
        this is ReportDestinationException
    return if (isSafeReportError) {
        message?.takeIf { it.isNotBlank() && it.length <= 300 } ?: fallback
    } else {
        fallback
    }
}
