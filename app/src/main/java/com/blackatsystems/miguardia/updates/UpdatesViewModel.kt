package com.blackatsystems.miguardia.updates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

internal enum class UpdateStage { IDLE, CHECKING, NO_RELEASE, CURRENT, AVAILABLE, INCOMPATIBLE, DOWNLOADING, VERIFYING, READY, PICKER, SAVING, SAVED, CANCELLED, INTERRUPTED, INVALID_RESPONSE, UNAVAILABLE, ERROR }
internal data class UpdatesState(
    val open: Boolean = false,
    val installed: InstalledUpdateApp? = null,
    val stage: UpdateStage = UpdateStage.IDLE,
    val release: UpdateRelease? = null,
    val bytes: Long = 0,
    val message: String? = null,
    val retryAt: Instant? = null,
    val retryRemainingMillis: Long = 0,
    val saveVerification: UpdateSaveVerification? = null,
) {
    val busy get() = stage in setOf(UpdateStage.CHECKING, UpdateStage.DOWNLOADING, UpdateStage.VERIFYING, UpdateStage.PICKER, UpdateStage.SAVING)
}

/** No receipt/token/file/URI is saved. A new process can restore only the visible destination. */
internal class UpdatesViewModel(
    private val operations: UpdateOperations,
    private val exporter: UpdateExporter,
    private val saved: SavedStateHandle,
    private val clock: Clock = Clock.systemUTC(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val testScope: CoroutineScope? = null,
) : ViewModel() {
    private val workScope get() = testScope ?: viewModelScope
    private val mutableState = MutableStateFlow(UpdatesState(open = saved[OPEN] ?: false,
        stage = if (saved.get<Boolean>(INTERRUPTED) == true) UpdateStage.INTERRUPTED else UpdateStage.IDLE))
    val state = mutableState.asStateFlow()
    private val generation = AtomicLong()
    private var job: Job? = null
    private var artifact: VerifiedUpdate? = null
    private var pickerGeneration: Long? = if (saved.get<Boolean>(PICKER_PENDING) == true) Long.MIN_VALUE else null
    private var pendingDocument: UpdateDocument? = null
    private var resultReceived = false
    @Volatile private var foreground = false
    @Volatile private var transientPause = false
    @Volatile private var gate: (() -> Boolean)? = null
    private var host: Any? = null
    private var retryTicker: Job? = null
    private var retryInstant: Instant? = null

    fun attachHost(owner: Any, allowed: () -> Boolean) {
        host = owner
        gate = allowed
        foreground = true
        transientPause = false
        if (state.value.open) { refreshInstalled(); observeRetry() }
        if (state.value.busy && state.value.stage != UpdateStage.PICKER && !allowed()) cancel(interrupted = true)
        if (resultReceived) consumeDocument()
    }
    fun pauseHost() { transientPause = true; foreground = false }
    fun detachHost(owner: Any) { if (host === owner) { gate = null; host = null } }
    fun onBackground(changingConfiguration: Boolean) {
        if (!changingConfiguration) transientPause = false
        if (!changingConfiguration && state.value.busy && state.value.stage != UpdateStage.PICKER) cancel(interrupted = true)
    }
    private fun allowed(token: Long = generation.get()): Boolean = foreground && generation.get() == token && gate?.invoke() == true && state.value.open
    private fun transferAllowed(token: Long): Boolean = generation.get() == token && state.value.open &&
        ((foreground && gate?.invoke() == true) || (!foreground && transientPause))

    fun open() {
        if (!foreground || gate?.invoke() != true || state.value.open) return
        saved[OPEN] = true
        mutableState.value = UpdatesState(open = true, retryAt = retryInstant?.takeIf { coolingDown() })
        refreshInstalled()
        observeRetry()
    }
    private fun refreshInstalled() {
        val current = runCatching { operations.installed() }.getOrNull()
        val previous = state.value.installed
        if (previous != null && previous != current) cancel(interrupted = true)
        mutableState.value = state.value.copy(installed = current,
            stage = if (current == null) UpdateStage.UNAVAILABLE else state.value.stage)
    }
    fun checkRelease() {
        if (!allowed() || state.value.busy || coolingDown()) return
        discardArtifact()
        val token = generation.incrementAndGet()
        saved[INTERRUPTED] = true
        mutableState.value = state.value.copy(stage = UpdateStage.CHECKING, release = null, message = null, retryAt = null)
        job = workScope.launch {
            try {
                val installed = withContext(io) { operations.installed() }
                val release = withContext(io) { operations.release() }
                if (!transferAllowed(token)) { interruptCurrent(token); return@launch }
                check(operations.installed() == installed)
                val status = release?.availability(installed)
                mutableState.value = state.value.copy(installed = installed, release = release, stage = when (status) {
                    null -> UpdateStage.NO_RELEASE
                    UpdateAvailability.AVAILABLE -> UpdateStage.AVAILABLE
                    UpdateAvailability.NO_NEWER_VERSION -> UpdateStage.CURRENT
                    else -> UpdateStage.INCOMPATIBLE
                }, message = null)
                saved[INTERRUPTED] = false
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { fail(token, error) }
        }
    }
    fun download() {
        val current = state.value
        if (!allowed() || current.busy || coolingDown() || current.stage != UpdateStage.AVAILABLE) return
        val release = current.release ?: return
        val installed = current.installed ?: return
        val token = generation.incrementAndGet()
        saved[INTERRUPTED] = true
        mutableState.value = current.copy(stage = UpdateStage.DOWNLOADING, bytes = 0, message = null)
        job = workScope.launch {
            var downloaded: VerifiedUpdate? = null
            try {
                val selection = withContext(io) { operations.select(release, installed) }
                check(transferAllowed(token))
                downloaded = operations.download(selection, { transferAllowed(token) },
                    { bytes -> mutableState.update { if (transferAllowed(token)) it.copy(bytes = bytes) else it } },
                    { mutableState.update { if (transferAllowed(token)) it.copy(stage = UpdateStage.VERIFYING) else it } })
                if (!transferAllowed(token)) { interruptCurrent(token); return@launch }
                artifact = downloaded
                downloaded = null
                mutableState.value = state.value.copy(stage = UpdateStage.READY)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { fail(token, error)
            } finally { downloaded?.let { withContext(NonCancellable + io) { runCatching { operations.discard(it) } } } }
        }
    }
    fun prepareSave(launch: (String) -> Unit) {
        val ready = artifact ?: return
        if (!allowed() || state.value.stage != UpdateStage.READY) return
        if (pickerGeneration != null) {
            mutableState.value = state.value.copy(message = "Cerrá el selector anterior antes de volver a guardar.")
            return
        }
        val token = generation.get()
        mutableState.value = state.value.copy(stage = UpdateStage.VERIFYING)
        job = workScope.launch {
            try {
                withContext(io) { operations.validate(ready) { allowed(token) } }
                if (!allowed(token)) { interruptCurrent(token); return@launch }
                pickerGeneration = token
                saved[PICKER_PENDING] = true
                mutableState.value = state.value.copy(stage = UpdateStage.PICKER)
                launch(ready.selection.release.fileName)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (generation.get() == token) { pickerGeneration = null; saved[PICKER_PENDING] = false }
                fail(token, error)
            }
        }
    }
    fun documentReturned(document: UpdateDocument?) {
        if (pickerGeneration == null || resultReceived) return
        pendingDocument = document
        resultReceived = true
        if (foreground) consumeDocument()
    }
    private fun consumeDocument() {
        val token = pickerGeneration
        val document = pendingDocument
        val ready = artifact
        pickerGeneration = null; pendingDocument = null; resultReceived = false
        saved[PICKER_PENDING] = false
        if (token == null || token != generation.get()) return
        if (ready == null || !allowed(token)) { cancel(interrupted = true); return }
        if (document == null) { mutableState.value = state.value.copy(stage = UpdateStage.READY, message = "Guardado cancelado. El APK verificado sigue listo."); return }
        mutableState.value = state.value.copy(stage = UpdateStage.SAVING)
        job = workScope.launch {
            try {
                withContext(io) { operations.validate(ready) { allowed(token) } }
                val verification = exporter.save(ready, document) { allowed(token) }
                if (!allowed(token)) { interruptCurrent(token); return@launch }
                mutableState.value = state.value.copy(stage = UpdateStage.SAVED, saveVerification = verification)
                saved[INTERRUPTED] = false
                discardArtifact()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { fail(token, error) }
        }
    }
    fun cancel(interrupted: Boolean = false) {
        val wasSaving = state.value.stage == UpdateStage.SAVING
        generation.incrementAndGet()
        job?.cancel(); job = null
        // A launched selector still owns its next callback after cancellation. Do not let
        // an old result be interpreted as a subsequent save operation's destination.
        if (resultReceived) {
            pickerGeneration = null; pendingDocument = null; resultReceived = false
            saved[PICKER_PENDING] = false
        }
        discardArtifact()
        saved[INTERRUPTED] = false
        mutableState.value = state.value.copy(stage = if (interrupted) UpdateStage.INTERRUPTED else UpdateStage.CANCELLED,
            release = null, bytes = 0, message = if (wasSaving) PARTIAL_DOCUMENT_NOTICE else null)
    }
    private fun interruptCurrent(token: Long) { if (generation.get() == token) cancel(interrupted = true) }
    fun close() {
        cancel()
        retryTicker?.cancel()
        saved[OPEN] = false
        mutableState.value = state.value.copy(open = false)
    }
    private fun remainingRetry() = operations.retryRemainingMillis()
    private fun coolingDown() = remainingRetry() > 0
    private fun observeRetry() {
        retryTicker?.cancel()
        mutableState.value = state.value.copy(retryRemainingMillis = remainingRetry())
        if (!coolingDown()) return
        retryTicker = workScope.launch {
            while (state.value.open && coolingDown()) {
                mutableState.value = state.value.copy(retryRemainingMillis = remainingRetry())
                delay(250)
            }
            mutableState.value = state.value.copy(retryRemainingMillis = remainingRetry())
        }
    }
    private fun fail(token: Long, error: Exception) {
        if (generation.get() != token) return
        val wasSaving = state.value.stage == UpdateStage.SAVING
        val service = error as? UpdateServiceFailure
        service?.retryAt?.let {
            retryInstant = it
        }
        discardArtifact()
        mutableState.value = state.value.copy(stage = when {
            error is InvalidUpdateResponse -> UpdateStage.INVALID_RESPONSE
            service != null -> UpdateStage.UNAVAILABLE
            else -> UpdateStage.ERROR
        }, release = null, bytes = 0, retryAt = retryInstant, message = when {
            wasSaving -> "No pudimos guardar y verificar el APK. $PARTIAL_DOCUMENT_NOTICE"
            service?.status == 409 -> "La publicación cambió. Volvé a buscar y elegí la versión antes de descargar."
            service?.status == 429 -> "Hay demasiados intentos. Esperá a que termine la cuenta regresiva antes de buscar de nuevo."
            service?.status == 401 || service?.status == 403 -> "El servicio rechazó la solicitud. Podés continuar usando MiGuardia y buscar más tarde."
            service != null -> "El servicio de actualizaciones no está disponible. Podés continuar y buscar más tarde."
            else -> "No pudimos completar o verificar la operación. Revisá la conexión y el espacio disponible y volvé a buscar. Tus datos de MiGuardia siguen en el teléfono."
        })
        saved[INTERRUPTED] = false
        observeRetry()
    }
    private fun discardArtifact() {
        val old = artifact ?: return
        artifact = null
        // Cleanup must outlive a cleared ViewModel, but never starts network or touches user data.
        CoroutineScope(SupervisorJob() + io).launch { runCatching { operations.discard(old) } }
    }
    override fun onCleared() { generation.incrementAndGet(); job?.cancel(); discardArtifact(); gate = null }
    companion object {
        private const val PARTIAL_DOCUMENT_NOTICE = "Puede haber quedado un archivo incompleto en el destino elegido. No lo instales. No iniciaremos otro guardado hasta liberar el anterior. Podés revisar el destino desde Archivos cuando el proveedor responda."
        private const val OPEN = "updates.open"
        private const val INTERRUPTED = "updates.interrupted"
        private const val PICKER_PENDING = "updates.pickerPending"
    }
    class Factory(private val operations: UpdateOperations) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            UpdatesViewModel(operations, UpdateDocumentWriter(), extras.createSavedStateHandle()) as T
    }
}
