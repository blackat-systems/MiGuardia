package com.blackatsystems.miguardia.backup

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.StartupRecoveryGate
import com.blackatsystems.miguardia.StartupRecoveryState
import com.blackatsystems.miguardia.core.domain.backup.BackupConflict
import com.blackatsystems.miguardia.core.domain.backup.BackupConflictResolution
import com.blackatsystems.miguardia.core.domain.backup.BackupAuthenticationException
import com.blackatsystems.miguardia.core.domain.backup.BackupPasswordRequiredException
import com.blackatsystems.miguardia.core.domain.backup.BackupPreview
import com.blackatsystems.miguardia.core.domain.backup.InvalidBackupException
import com.blackatsystems.miguardia.core.domain.backup.ResolvedBackupConflict
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.theme.AppZoom
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BackupStage {
    IDLE,
    CAPTURING,
    WAITING_FOR_CREATE_DESTINATION,
    COPYING_OUT,
    CANCELLING,
    WAITING_FOR_OPEN_SOURCE,
    READING,
    PASSWORD_REQUIRED,
    VALIDATING,
    PREVIEW,
    RESOLVING_CONFLICTS,
    READY_TO_APPLY,
    APPLYING,
    RECOVERING,
    SUCCESS,
    ERROR,
}

data class BackupUiState(
    val isOpen: Boolean = false,
    val stage: BackupStage = BackupStage.IDLE,
    val includePhotos: Boolean = true,
    val encryptionEnabled: Boolean = true,
    val unencryptedWarningAccepted: Boolean = false,
    val passwordVisible: Boolean = false,
    val password: String = "",
    val passwordConfirmation: String = "",
    val passwordError: String? = null,
    val suggestedName: String? = null,
    val formatVersion: Int? = null,
    val preview: BackupPreview? = null,
    val restoreChoice: RestoreChoice? = null,
    val conflicts: List<BackupConflict> = emptyList(),
    val resolutions: Map<String, BackupConflictResolution> = emptyMap(),
    val conflictsReviewed: Boolean = false,
    val replaceConfirmation: String = "",
    val errorMessage: String? = null,
    val recoveryRequired: Boolean = false,
    val infoMessage: String? = null,
    val successSequence: Int = 0,
    val restoredZoom: AppZoom? = null,
    val restoredTheme: AppThemeMode? = null,
) {
    val isBusy: Boolean get() = stage in setOf(
        BackupStage.CAPTURING,
        BackupStage.COPYING_OUT,
        BackupStage.CANCELLING,
        BackupStage.READING,
        BackupStage.VALIDATING,
        BackupStage.APPLYING,
        BackupStage.RECOVERING,
    )

    val canCancel: Boolean get() = stage in setOf(
        BackupStage.CAPTURING,
        BackupStage.COPYING_OUT,
        BackupStage.READING,
        BackupStage.VALIDATING,
    )

    val canCreate: Boolean get() = !isBusy && if (encryptionEnabled) {
        password.length in BackupCreateOptions.MIN_PASSWORD_LENGTH..BackupCreateOptions.MAX_PASSWORD_LENGTH &&
            password == passwordConfirmation
    } else {
        unencryptedWarningAccepted
    }
    val allConflictsResolved: Boolean get() = conflicts.all { it.id in resolutions }
}

internal fun initialBackupUiState(
    recoveryState: StartupRecoveryState,
    savedOpen: Boolean,
    includePhotos: Boolean,
    encryptionEnabled: Boolean,
): BackupUiState {
    val recoveryError = (recoveryState as? StartupRecoveryState.Failed)?.message
    return BackupUiState(
        isOpen = recoveryState != StartupRecoveryState.Ready || savedOpen,
        stage = when (recoveryState) {
            StartupRecoveryState.Recovering -> BackupStage.RECOVERING
            is StartupRecoveryState.Failed -> BackupStage.ERROR
            StartupRecoveryState.Ready -> BackupStage.IDLE
        },
        includePhotos = includePhotos,
        encryptionEnabled = encryptionEnabled,
        errorMessage = recoveryError,
        recoveryRequired = recoveryState != StartupRecoveryState.Ready,
    )
}

internal fun backupCreationValidationError(state: BackupUiState): String? = when {
    !state.encryptionEnabled && !state.unencryptedWarningAccepted ->
        "Confirmá que entendés que la copia sin contraseña podrá ser leída por cualquiera con acceso al archivo."
    state.encryptionEnabled && state.password.length !in
        BackupCreateOptions.MIN_PASSWORD_LENGTH..BackupCreateOptions.MAX_PASSWORD_LENGTH ->
        "La contraseña debe tener entre 8 y 256 caracteres."
    state.encryptionEnabled && state.password != state.passwordConfirmation ->
        "Las dos contraseñas no coinciden."
    else -> null
}

data class BackupActions(
    val open: () -> Unit = {},
    val close: () -> Unit = {},
    val setIncludePhotos: (Boolean) -> Unit = {},
    val setEncryptionEnabled: (Boolean) -> Unit = {},
    val setUnencryptedWarningAccepted: (Boolean) -> Unit = {},
    val setPasswordVisible: (Boolean) -> Unit = {},
    val setPassword: (String) -> Unit = {},
    val setPasswordConfirmation: (String) -> Unit = {},
    val create: () -> Unit = {},
    val destinationSelected: (Uri?) -> Unit = {},
    val chooseSource: () -> Unit = {},
    val sourceSelected: (Uri?) -> Unit = {},
    val unlockSource: () -> Unit = {},
    val chooseMerge: () -> Unit = {},
    val chooseReplace: () -> Unit = {},
    val resolve: (String, BackupConflictResolution) -> Unit = { _, _ -> },
    val confirmResolutions: () -> Unit = {},
    val setReplaceConfirmation: (String) -> Unit = {},
    val apply: () -> Unit = {},
    val cancelOperation: () -> Unit = {},
    val retryRecovery: () -> Unit = {},
    val finishSuccess: () -> Unit = {},
    val clearMessage: () -> Unit = {},
) {
    companion object {
        fun from(viewModel: BackupViewModel): BackupActions = BackupActions(
            open = viewModel::open,
            close = viewModel::close,
            setIncludePhotos = viewModel::setIncludePhotos,
            setEncryptionEnabled = viewModel::setEncryptionEnabled,
            setUnencryptedWarningAccepted = viewModel::setUnencryptedWarningAccepted,
            setPasswordVisible = viewModel::setPasswordVisible,
            setPassword = viewModel::setPassword,
            setPasswordConfirmation = viewModel::setPasswordConfirmation,
            create = viewModel::create,
            destinationSelected = viewModel::destinationSelected,
            chooseSource = viewModel::chooseSource,
            sourceSelected = viewModel::sourceSelected,
            unlockSource = viewModel::unlockSource,
            chooseMerge = viewModel::chooseMerge,
            chooseReplace = viewModel::chooseReplace,
            resolve = viewModel::resolve,
            confirmResolutions = viewModel::confirmResolutions,
            setReplaceConfirmation = viewModel::setReplaceConfirmation,
            apply = viewModel::apply,
            cancelOperation = viewModel::cancelOperation,
            retryRecovery = viewModel::retryRecovery,
            finishSuccess = viewModel::finishSuccess,
            clearMessage = viewModel::clearMessage,
        )
    }
}

class BackupViewModel(
    private val coordinator: LocalBackupCoordinator,
    private val startupRecoveryGate: StartupRecoveryGate,
    private val savedStateHandle: SavedStateHandle,
    private val discardIncompleteDocument: suspend (Uri) -> Boolean = { false },
) : ViewModel() {
    private val initialRecoveryState = startupRecoveryGate.state.value
    private var awaitingAutomaticStartupRecovery = initialRecoveryState == StartupRecoveryState.Recovering
    private var recoveryError: String? = (initialRecoveryState as? StartupRecoveryState.Failed)?.message
    private val _uiState = MutableStateFlow(
        initialBackupUiState(
            recoveryState = initialRecoveryState,
            savedOpen = savedStateHandle.get<Boolean>(KEY_OPEN) ?: false,
            includePhotos = savedStateHandle.get<Boolean>(KEY_INCLUDE_PHOTOS) ?: true,
            encryptionEnabled = savedStateHandle.get<Boolean>(KEY_ENCRYPTION_ENABLED) ?: true,
        ),
    )
    val uiState: StateFlow<BackupUiState> = _uiState
    private var operation: Job? = null
    private var artifact: PreparedBackupArtifact? = null
    private var stagedSource: StagedBackupSource? = null
    private var importSession: BackupImportSession? = null
    private var activeDestination: Uri? = null

    init {
        viewModelScope.launch {
            startupRecoveryGate.state.collect { recovery ->
                when (recovery) {
                    StartupRecoveryState.Recovering -> {
                        _uiState.update {
                            it.copy(
                                isOpen = true,
                                stage = BackupStage.RECOVERING,
                                errorMessage = null,
                                recoveryRequired = true,
                            )
                        }
                    }
                    is StartupRecoveryState.Failed -> {
                        awaitingAutomaticStartupRecovery = false
                        recoveryError = recovery.message
                        _uiState.update {
                            it.copy(
                                isOpen = true,
                                stage = BackupStage.ERROR,
                                errorMessage = recovery.message,
                                recoveryRequired = true,
                            )
                        }
                    }
                    StartupRecoveryState.Ready -> {
                        recoveryError = null
                        if (awaitingAutomaticStartupRecovery) {
                            awaitingAutomaticStartupRecovery = false
                            _uiState.value = BackupUiState(
                                isOpen = savedStateHandle.get<Boolean>(KEY_OPEN) ?: false,
                                includePhotos = savedStateHandle.get<Boolean>(KEY_INCLUDE_PHOTOS) ?: true,
                                encryptionEnabled = savedStateHandle.get<Boolean>(KEY_ENCRYPTION_ENABLED) ?: true,
                            )
                        }
                    }
                }
            }
        }
    }

    fun open() {
        if (_uiState.value.isOpen) return
        _uiState.value = BackupUiState(
            isOpen = true,
            stage = if (recoveryError == null) BackupStage.IDLE else BackupStage.ERROR,
            includePhotos = savedStateHandle.get<Boolean>(KEY_INCLUDE_PHOTOS) ?: true,
            encryptionEnabled = savedStateHandle.get<Boolean>(KEY_ENCRYPTION_ENABLED) ?: true,
            errorMessage = recoveryError,
            recoveryRequired = recoveryError != null,
        )
        persistSafeDraft()
    }

    fun close() {
        if (_uiState.value.isBusy || _uiState.value.recoveryRequired) return
        try {
            clearPrivateState()
        } catch (error: Exception) {
            fail(error, "No pudimos retirar los archivos temporales privados de la operación.")
            return
        }
        _uiState.value = BackupUiState()
        persistSafeDraft()
    }

    fun setIncludePhotos(value: Boolean) = updateDraft { it.copy(includePhotos = value) }

    fun setEncryptionEnabled(value: Boolean) = updateDraft {
        it.copy(
            encryptionEnabled = value,
            unencryptedWarningAccepted = false,
            password = if (value) it.password else "",
            passwordConfirmation = if (value) it.passwordConfirmation else "",
            passwordVisible = if (value) it.passwordVisible else false,
            passwordError = null,
        )
    }

    fun setUnencryptedWarningAccepted(value: Boolean) = updateDraft {
        if (it.encryptionEnabled) it else it.copy(unencryptedWarningAccepted = value)
    }

    fun setPasswordVisible(value: Boolean) {
        val state = _uiState.value
        if (!state.isOpen || state.isBusy) return
        _uiState.update { it.copy(passwordVisible = value) }
    }

    fun setPassword(value: String) = updatePassword(value, confirmation = false)

    fun setPasswordConfirmation(value: String) = updatePassword(value, confirmation = true)

    fun create() {
        val state = _uiState.value
        if (!state.isOpen || state.isBusy) return
        val validationError = backupCreationValidationError(state)
        if (validationError != null) {
            _uiState.update {
                if (state.encryptionEnabled) {
                    it.copy(passwordError = validationError)
                } else {
                    it.copy(errorMessage = validationError)
                }
            }
            return
        }
        val options = BackupCreateOptions(
            includePhotos = state.includePhotos,
            password = state.password.takeIf { state.encryptionEnabled },
        )
        operation?.cancel()
        savedStateHandle[KEY_CREATE_DESTINATION_PENDING] = false
        _uiState.update {
            it.copy(
                stage = BackupStage.CAPTURING,
                password = "",
                passwordConfirmation = "",
                passwordVisible = false,
                passwordError = null,
                errorMessage = null,
                infoMessage = null,
            )
        }
        operation = viewModelScope.launch {
            try {
                artifact?.close()
                artifact = coordinator.prepareBackup(options)
                currentCoroutineContext().ensureActive()
                savedStateHandle[KEY_CREATE_DESTINATION_PENDING] = true
                _uiState.update {
                    it.copy(
                        stage = BackupStage.WAITING_FOR_CREATE_DESTINATION,
                        suggestedName = artifact?.suggestedName,
                        password = "",
                        passwordConfirmation = "",
                        passwordVisible = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                fail(error, "No pudimos preparar una copia local completa.")
            }
        }
    }

    fun destinationSelected(destination: Uri?) {
        if (savedStateHandle.get<Boolean>(KEY_CREATE_DESTINATION_PENDING) != true) return
        savedStateHandle[KEY_CREATE_DESTINATION_PENDING] = false
        val prepared = artifact
        if (prepared == null) {
            if (destination != null) {
                operation = viewModelScope.launch {
                    val discarded = discardIncompleteDocument(destination)
                    _uiState.update {
                        it.copy(
                            stage = BackupStage.IDLE,
                            suggestedName = null,
                            infoMessage = if (discarded) {
                                "La preparación se interrumpió y el documento incompleto fue retirado o vaciado."
                            } else {
                                "La preparación se interrumpió. Android no pudo retirar el documento incompleto; eliminá ese archivo desde el selector de documentos."
                            },
                        )
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        stage = BackupStage.IDLE,
                        suggestedName = null,
                        infoMessage = "Guardado cancelado.",
                    )
                }
            }
            return
        }
        if (_uiState.value.stage != BackupStage.WAITING_FOR_CREATE_DESTINATION) return
        if (destination == null) {
            try {
                prepared.close()
            } catch (error: Exception) {
                fail(error, "El guardado se canceló, pero todavía no pudimos retirar sus archivos temporales privados.")
                return
            }
            artifact = null
            _uiState.update {
                it.copy(stage = BackupStage.IDLE, suggestedName = null, infoMessage = "Guardado cancelado.")
            }
            return
        }
        activeDestination = destination
        _uiState.update { it.copy(stage = BackupStage.COPYING_OUT, errorMessage = null) }
        operation = viewModelScope.launch {
            try {
                coordinator.copyToDocument(prepared, destination)
                currentCoroutineContext().ensureActive()
                prepared.close()
                artifact = null
                activeDestination = null
                _uiState.update {
                    it.copy(stage = BackupStage.SUCCESS, suggestedName = null, infoMessage = "Copia guardada.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                activeDestination = null
                fail(error, "No pudimos guardar la copia elegida.")
            }
        }
    }

    fun chooseSource() {
        if (!_uiState.value.isOpen || _uiState.value.isBusy) return
        try {
            clearImportState()
        } catch (error: Exception) {
            fail(error, "No pudimos retirar los archivos temporales privados de la copia anterior.")
            return
        }
        _uiState.update {
            it.copy(
                stage = BackupStage.WAITING_FOR_OPEN_SOURCE,
                preview = null,
                restoreChoice = null,
                conflicts = emptyList(),
                resolutions = emptyMap(),
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun sourceSelected(source: Uri?) {
        if (_uiState.value.stage != BackupStage.WAITING_FOR_OPEN_SOURCE) return
        if (source == null) {
            _uiState.update { it.copy(stage = BackupStage.IDLE, infoMessage = "Selección cancelada.") }
            return
        }
        _uiState.update { it.copy(stage = BackupStage.READING, errorMessage = null) }
        operation = viewModelScope.launch {
            try {
                stagedSource = coordinator.stageSource(source)
                currentCoroutineContext().ensureActive()
                if (stagedSource?.header?.encrypted == true) {
                    _uiState.update {
                        it.copy(stage = BackupStage.PASSWORD_REQUIRED, password = "", passwordError = null)
                    }
                } else {
                    validateSource(null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                fail(error, "No pudimos leer la copia elegida.")
            }
        }
    }

    fun unlockSource() {
        val state = _uiState.value
        if (state.stage != BackupStage.PASSWORD_REQUIRED) return
        if (state.password.isEmpty() || state.password.length > BackupCreateOptions.MAX_PASSWORD_LENGTH) {
            _uiState.update { it.copy(passwordError = "Ingresá la contraseña de esta copia.") }
            return
        }
        val password = state.password
        _uiState.update { it.copy(password = "", passwordVisible = false) }
        operation = viewModelScope.launch { validateSource(password) }
    }

    fun chooseMerge() {
        val session = importSession ?: return
        if (_uiState.value.stage !in setOf(BackupStage.PREVIEW, BackupStage.READY_TO_APPLY)) return
        if (!session.comparison.timelineCompatible) {
            _uiState.update {
                it.copy(errorMessage = "Esta copia pertenece a otra línea temporal y no puede combinarse.")
            }
            return
        }
        session.comparison.mergeBlockedReason?.let { reason ->
            _uiState.update { it.copy(errorMessage = reason) }
            return
        }
        val defaults = session.conflicts.associate { it.id to BackupConflictResolution.KEEP_CURRENT }
        _uiState.update {
            it.copy(
                stage = if (session.conflicts.isEmpty()) BackupStage.READY_TO_APPLY else BackupStage.RESOLVING_CONFLICTS,
                restoreChoice = RestoreChoice.MERGE,
                conflicts = session.conflicts,
                resolutions = defaults,
                conflictsReviewed = session.conflicts.isEmpty(),
                replaceConfirmation = "",
                errorMessage = null,
            )
        }
    }

    fun chooseReplace() {
        if (importSession == null || _uiState.value.stage !in setOf(BackupStage.PREVIEW, BackupStage.READY_TO_APPLY)) return
        _uiState.update {
            it.copy(
                stage = BackupStage.READY_TO_APPLY,
                restoreChoice = RestoreChoice.REPLACE_ALL,
                replaceConfirmation = "",
                conflictsReviewed = false,
                errorMessage = null,
            )
        }
    }

    fun resolve(conflictId: String, resolution: BackupConflictResolution) {
        val state = _uiState.value
        val conflict = state.conflicts.firstOrNull { it.id == conflictId } ?: return
        if (state.stage != BackupStage.RESOLVING_CONFLICTS ||
            resolution == BackupConflictResolution.KEEP_BOTH && !conflict.keepBothAllowed
        ) return
        _uiState.update {
            it.copy(
                resolutions = it.resolutions + (conflictId to resolution),
                conflictsReviewed = false,
                errorMessage = null,
            )
        }
    }

    fun confirmResolutions() {
        val state = _uiState.value
        if (state.stage != BackupStage.RESOLVING_CONFLICTS || !state.allConflictsResolved) return
        _uiState.update { it.copy(stage = BackupStage.READY_TO_APPLY, conflictsReviewed = true) }
    }

    fun setReplaceConfirmation(value: String) {
        if (_uiState.value.restoreChoice != RestoreChoice.REPLACE_ALL || value.length > 64) return
        _uiState.update { it.copy(replaceConfirmation = value, errorMessage = null) }
    }

    fun apply() {
        val session = importSession ?: return
        val state = _uiState.value
        if (state.stage != BackupStage.READY_TO_APPLY || state.restoreChoice == null) return
        if (state.restoreChoice == RestoreChoice.MERGE && !state.conflictsReviewed) return
        if (state.restoreChoice == RestoreChoice.REPLACE_ALL &&
            state.replaceConfirmation != LocalBackupCoordinator.REPLACE_CONFIRMATION
        ) {
            _uiState.update { it.copy(errorMessage = "Escribí exactamente “Reemplazar todo” para confirmar.") }
            return
        }
        _uiState.update { it.copy(stage = BackupStage.APPLYING, errorMessage = null, infoMessage = null) }
        operation = viewModelScope.launch {
            try {
                val settings = coordinator.apply(
                    session,
                    state.restoreChoice,
                    state.resolutions.map { (id, resolution) -> ResolvedBackupConflict(id, resolution) },
                    state.replaceConfirmation.takeIf { state.restoreChoice == RestoreChoice.REPLACE_ALL },
                )
                clearImportState()
                _uiState.update {
                    it.copy(
                        stage = BackupStage.SUCCESS,
                        successSequence = it.successSequence + 1,
                        restoredZoom = settings.zoom,
                        restoredTheme = settings.theme,
                        password = "",
                        replaceConfirmation = "",
                        infoMessage = "Restauración verificada. Tus datos ya están listos.",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error is RestoreRecoveryRequiredException) {
                    startupRecoveryGate.failed(
                        error.message ?: "La restauración necesita completar una recuperación segura.",
                    )
                }
                fail(error, "No pudimos aplicar la restauración; se conservó el estado anterior.")
            }
        }
    }

    fun retryRecovery() {
        if (!_uiState.value.isOpen || _uiState.value.isBusy) return
        startupRecoveryGate.recovering()
        _uiState.update { it.copy(stage = BackupStage.RECOVERING, errorMessage = null) }
        operation = viewModelScope.launch {
            try {
                val settings = coordinator.recoverAndResume()
                recoveryError = null
                startupRecoveryGate.ready()
                clearPrivateState()
                _uiState.update {
                    it.copy(
                        stage = BackupStage.SUCCESS,
                        recoveryRequired = false,
                        successSequence = it.successSequence + 1,
                        restoredZoom = settings.zoom,
                        restoredTheme = settings.theme,
                        infoMessage = "La recuperación pendiente terminó correctamente.",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = when (error) {
                    is InvalidBackupException, is RestoreRecoveryRequiredException ->
                        error.message?.takeIf { it.isNotBlank() && it.length <= 300 }
                    else -> null
                } ?: "La recuperación pendiente todavía no pudo completarse."
                startupRecoveryGate.failed(message)
                fail(error, "La recuperación pendiente todavía no pudo completarse.")
            }
        }
    }

    fun finishSuccess() {
        if (_uiState.value.stage != BackupStage.SUCCESS) return
        try {
            clearPrivateState()
        } catch (error: Exception) {
            fail(error, "La operación terminó, pero todavía no pudimos retirar sus archivos temporales privados.")
            return
        }
        _uiState.update {
            it.copy(
                isOpen = false,
                stage = BackupStage.IDLE,
                infoMessage = null,
                successSequence = 0,
                restoredZoom = null,
                restoredTheme = null,
            )
        }
        persistSafeDraft()
    }

    fun clearMessage() {
        if (_uiState.value.stage == BackupStage.ERROR && recoveryError == null && !_uiState.value.recoveryRequired) {
            try {
                clearPrivateState()
            } catch (error: Exception) {
                fail(error, "Todavía no pudimos retirar los archivos temporales privados de la operación.")
                return
            }
            _uiState.update {
                it.copy(
                    stage = BackupStage.IDLE,
                    errorMessage = null,
                    infoMessage = null,
                    preview = null,
                    restoreChoice = null,
                    conflicts = emptyList(),
                    resolutions = emptyMap(),
                    replaceConfirmation = "",
                )
            }
        } else {
            _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
        }
    }

    private suspend fun validateSource(password: String?) {
        val source = stagedSource ?: return
        _uiState.update { it.copy(stage = BackupStage.VALIDATING, passwordError = null, errorMessage = null) }
        try {
            importSession?.close()
            importSession = coordinator.openSource(source, password)
            currentCoroutineContext().ensureActive()
            stagedSource = null
            _uiState.update {
                it.copy(
                    stage = BackupStage.PREVIEW,
                    preview = importSession?.preview,
                    formatVersion = importSession?.source?.header?.formatVersion,
                    conflicts = importSession?.conflicts.orEmpty(),
                    password = "",
                    passwordError = null,
                )
            }
        } catch (error: BackupPasswordRequiredException) {
            _uiState.update {
                it.copy(stage = BackupStage.PASSWORD_REQUIRED, password = "", passwordError = error.message)
            }
        } catch (error: BackupAuthenticationException) {
            _uiState.update {
                it.copy(
                    stage = BackupStage.PASSWORD_REQUIRED,
                    password = "",
                    passwordError = error.message ?: "La contraseña no es correcta.",
                )
            }
        } catch (error: InvalidBackupException) {
            fail(error, "La copia no superó la validación.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            fail(error, "La copia no superó la validación.")
        }
    }

    fun cancelOperation() {
        val state = _uiState.value
        if (!state.canCancel) return
        val cancelled = operation
        val destination = activeDestination
        _uiState.update { it.copy(stage = BackupStage.CANCELLING, errorMessage = null) }
        operation = viewModelScope.launch {
            var destinationDiscarded = destination == null
            var cleanupError: Exception? = null
            try {
                cancelled?.cancel()
                cancelled?.join()
                if (destination != null) {
                    destinationDiscarded = runCatching {
                        discardIncompleteDocument(destination)
                    }.getOrDefault(false)
                }
            } finally {
                activeDestination = null
                try {
                    withContext(Dispatchers.IO) { clearPrivateState() }
                } catch (error: Exception) {
                    cleanupError = error
                }
                savedStateHandle[KEY_CREATE_DESTINATION_PENDING] = false
                val cleanupFailure = cleanupError
                if (cleanupFailure != null) {
                    fail(
                        cleanupFailure,
                        "La operación se canceló, pero todavía no pudimos retirar sus archivos temporales privados.",
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            stage = BackupStage.IDLE,
                            suggestedName = null,
                            formatVersion = null,
                            preview = null,
                            restoreChoice = null,
                            conflicts = emptyList(),
                            resolutions = emptyMap(),
                            conflictsReviewed = false,
                            replaceConfirmation = "",
                            password = "",
                            passwordConfirmation = "",
                            passwordVisible = false,
                            passwordError = null,
                            recoveryRequired = false,
                            infoMessage = if (destinationDiscarded) {
                                "Operación cancelada; el documento incompleto fue retirado o vaciado."
                            } else {
                                "Operación cancelada sin cambiar tus datos. Android no pudo retirar el documento incompleto; eliminá ese archivo desde el selector de documentos."
                            },
                        )
                    }
                }
            }
        }
    }

    private fun updateDraft(change: (BackupUiState) -> BackupUiState) {
        val state = _uiState.value
        if (!state.isOpen || state.isBusy || state.stage != BackupStage.IDLE) return
        _uiState.value = change(state).copy(errorMessage = null, infoMessage = null)
        persistSafeDraft()
    }

    private fun persistSafeDraft() {
        val state = _uiState.value
        savedStateHandle[KEY_OPEN] = state.isOpen
        savedStateHandle[KEY_INCLUDE_PHOTOS] = state.includePhotos
        savedStateHandle[KEY_ENCRYPTION_ENABLED] = state.encryptionEnabled
    }

    private fun updatePassword(value: String, confirmation: Boolean) {
        if (value.length > BackupCreateOptions.MAX_PASSWORD_LENGTH) return
        val state = _uiState.value
        if (state.stage !in setOf(BackupStage.IDLE, BackupStage.PASSWORD_REQUIRED)) return
        _uiState.update {
            if (confirmation) it.copy(passwordConfirmation = value, passwordError = null)
            else it.copy(password = value, passwordError = null)
        }
    }

    private fun fail(error: Exception, fallback: String) {
        val safe = when (error) {
            is InvalidBackupException,
            is RestoreRecoveryRequiredException,
            is IncompleteBackupDocumentException ->
                error.message?.takeIf { it.isNotBlank() && it.length <= 300 }
            else -> null
        } ?: fallback
        _uiState.update {
            it.copy(
                stage = BackupStage.ERROR,
                errorMessage = safe,
                recoveryRequired = error is RestoreRecoveryRequiredException ||
                    recoveryError != null || it.recoveryRequired,
                password = "",
                passwordConfirmation = "",
                passwordVisible = false,
            )
        }
    }

    private fun clearImportState() {
        val session = importSession
        importSession = null
        val source = stagedSource
        stagedSource = null
        var failure: Exception? = null
        try {
            session?.close()
        } catch (error: Exception) {
            failure = error
        }
        try {
            source?.close()
        } catch (error: Exception) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        failure?.let { throw it }
    }

    private fun clearPrivateState() {
        val preparedArtifact = artifact
        artifact = null
        activeDestination = null
        savedStateHandle[KEY_CREATE_DESTINATION_PENDING] = false
        var failure: Exception? = null
        try {
            preparedArtifact?.close()
        } catch (error: Exception) {
            failure = error
        }
        try {
            clearImportState()
        } catch (error: Exception) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        failure?.let { throw it }
    }

    override fun onCleared() {
        operation?.cancel()
        runCatching { clearPrivateState() }
    }

    class Factory(
        private val coordinator: LocalBackupCoordinator,
        private val startupRecoveryGate: StartupRecoveryGate,
        private val discardIncompleteDocument: suspend (Uri) -> Boolean = { false },
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(BackupViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return BackupViewModel(
                coordinator,
                startupRecoveryGate,
                extras.createSavedStateHandle(),
                discardIncompleteDocument,
            ) as T
        }
    }

    private companion object {
        const val KEY_OPEN = "backup.open"
        const val KEY_INCLUDE_PHOTOS = "backup.include_photos"
        const val KEY_ENCRYPTION_ENABLED = "backup.encryption_enabled"
        const val KEY_CREATE_DESTINATION_PENDING = "backup.create_destination_pending"
    }
}
