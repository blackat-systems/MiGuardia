package com.blackatsystems.miguardia.ui.photos

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.SchedulePhotoRepository
import java.time.Clock
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PhotosViewModel(
    private val repository: SchedulePhotoRepository,
    private val objectives: ObjectiveRepository,
    val fileStore: SchedulePhotoFileStore,
    private val savedState: SavedStateHandle,
    private val clock: Clock = Clock.system(AppDefaults.zoneId()),
) : ViewModel() {
    private val initialMonth = savedState.get<String>(MONTH)?.let(YearMonth::parse) ?: YearMonth.now(clock)
    private val initialSurface = savedState.get<String>(SURFACE)?.let(PhotosSurface::valueOf) ?: PhotosSurface.NONE
    private val initialSelected = savedState.get<String>(SELECTED)?.let(UUID::fromString)
    private val _uiState = MutableStateFlow(PhotosUiState(surface = initialSurface, month = initialMonth, selectedId = initialSelected))
    val uiState: StateFlow<PhotosUiState> = _uiState
    private val mutex = Mutex()
    private var observer: Job? = null

    init {
        viewModelScope.launch {
            try { fileStore.reconcile { repository.getById(it)?.storageKey } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { _uiState.update { it.copy(errorMessage = "No pudimos verificar el almacenamiento de fotos.") } }
        }
        observe(initialMonth)
    }

    fun open(month: YearMonth) { setMonth(month); setSurface(PhotosSurface.LIST) }
    fun close() = setSurface(PhotosSurface.NONE)
    fun back() = if (_uiState.value.surface == PhotosSurface.VIEWER) setSurface(PhotosSurface.LIST) else close()
    fun previousMonth() = setMonth(_uiState.value.month.minusMonths(1))
    fun nextMonth() = setMonth(_uiState.value.month.plusMonths(1))
    fun retry() = observe(_uiState.value.month)
    fun clearMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    fun view(id: UUID) { savedState[SELECTED] = id.toString(); _uiState.update { it.copy(selectedId = id) }; setSurface(PhotosSurface.VIEWER) }
    fun requestDelete(id: UUID) = _uiState.update { it.copy(pendingDeleteId = id) }
    fun dismissDelete() = _uiState.update { it.copy(pendingDeleteId = null) }

    fun import(uris: List<Uri>, objectiveId: UUID? = null) = write {
        if (uris.isEmpty()) return@write
        val objective = objectiveId?.let { id -> _uiState.value.objectives.firstOrNull { it.id == id } }
        var imported = 0; var failed = 0
        uris.forEach { uri ->
            val id = UUID.randomUUID()
            try {
                val stored = fileStore.import(uri, id)
                val now = clock.instant()
                try {
                    repository.insert(SchedulePhoto(id, _uiState.value.month, objective?.id, objective?.fullName,
                        objective?.abbreviation, stored.storageKey, stored.mimeType, stored.byteSize,
                        stored.width, stored.height, now, now))
                    imported++
                } catch (error: Exception) { fileStore.file(stored.storageKey).delete(); throw error }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { failed++ }
        }
        _uiState.update { it.copy(infoMessage = "$imported foto(s) agregada(s)." + if (failed > 0) " $failed no pudieron importarse." else "") }
    }

    fun associate(id: UUID, objectiveId: UUID?) = write {
        val photo = repository.getById(id) ?: return@write
        val objective = objectiveId?.let { value -> _uiState.value.objectives.firstOrNull { it.id == value } }
        repository.update(photo.copy(objectiveId = objective?.id, objectiveNameSnapshot = objective?.fullName,
            objectiveAbbreviationSnapshot = objective?.abbreviation, updatedAt = clock.instant()))
        _uiState.update { it.copy(infoMessage = "Objetivo de la foto actualizado.") }
    }

    fun replace(id: UUID, uri: Uri) = write {
        val previous = repository.getById(id) ?: return@write
        val stored = fileStore.import(uri, previous.id, versioned = true)
        try {
            repository.update(previous.copy(storageKey = stored.storageKey, mimeType = stored.mimeType,
                byteSize = stored.byteSize, pixelWidth = stored.width, pixelHeight = stored.height,
                updatedAt = clock.instant()))
            fileStore.file(previous.storageKey).delete()
            _uiState.update { it.copy(infoMessage = "Foto reemplazada.") }
        } catch (error: Exception) {
            fileStore.file(stored.storageKey).delete(); throw error
        }
    }

    fun confirmDelete() = write {
        val id = _uiState.value.pendingDeleteId ?: return@write
        val photo = repository.getById(id) ?: return@write
        fileStore.removeRecoverably(photo.storageKey) { repository.delete(id) }
        clearSelectedPhoto()
        _uiState.update { it.copy(pendingDeleteId = null, infoMessage = "Foto eliminada.") }
        if (_uiState.value.surface == PhotosSurface.VIEWER) setSurface(PhotosSurface.LIST)
    }

    private fun setMonth(month: YearMonth) {
        savedState[MONTH] = month.toString()
        clearSelectedPhoto()
        _uiState.update { it.copy(month = month) }
        observe(month)
    }
    private fun clearSelectedPhoto() {
        savedState[SELECTED] = null
        _uiState.update { it.copy(selectedId = null) }
    }
    private fun setSurface(surface: PhotosSurface) { savedState[SURFACE] = surface.name; _uiState.update { it.copy(surface = surface) } }
    private fun observe(month: YearMonth) {
        observer?.cancel(); _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        observer = viewModelScope.launch {
            combine(repository.observeForMonth(month), objectives.observeActive()) { photos, active -> photos to active }
                .catch { _uiState.update { it.copy(isLoading = false, errorMessage = "No pudimos cargar las fotos.") } }
                .collect { (photos, active) -> if (_uiState.value.month == month) _uiState.update { it.copy(photos = photos, objectives = active, isLoading = false) } }
        }
    }
    private fun write(block: suspend () -> Unit) = viewModelScope.launch { mutex.withLock {
        _uiState.update { it.copy(isWorking = true, errorMessage = null) }
        try { block() } catch (error: CancellationException) { throw error }
        catch (_: Exception) { _uiState.update { it.copy(errorMessage = "No pudimos completar la operación. Reintentá.") } }
        finally { _uiState.update { it.copy(isWorking = false) } }
    } }

    class Factory(private val repository: SchedulePhotoRepository, private val objectives: ObjectiveRepository,
        private val fileStore: SchedulePhotoFileStore) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST") return PhotosViewModel(repository, objectives, fileStore, extras.createSavedStateHandle()) as T
        }
    }
    private companion object { const val MONTH="photos.month"; const val SURFACE="photos.surface"; const val SELECTED="photos.selected" }
}
