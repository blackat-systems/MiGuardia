package com.blackatsystems.miguardia.ui.photos

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PhotosActions(
    val open: (YearMonth) -> Unit = {}, val close: () -> Unit = {}, val back: () -> Unit = {},
    val previous: () -> Unit = {}, val next: () -> Unit = {}, val view: (UUID) -> Unit = {},
    val import: (List<android.net.Uri>) -> Unit = {}, val associate: (UUID, UUID?) -> Unit = {_,_->},
    val replace: (UUID, android.net.Uri) -> Unit = {_,_->},
    val requestDelete: (UUID) -> Unit = {}, val confirmDelete: () -> Unit = {},
    val requestDeleteAll: () -> Unit = {}, val confirmDeleteAll: () -> Unit = {},
    val dismissDelete: () -> Unit = {}, val retry: () -> Unit = {}, val clearMessage: () -> Unit = {},
) { companion object { fun from(vm: PhotosViewModel) = PhotosActions(vm::open, vm::close, vm::back,
    vm::previousMonth, vm::nextMonth, vm::view, { vm.import(it) }, vm::associate, vm::replace,
    vm::requestDelete, vm::confirmDelete, vm::requestDeleteAll, vm::confirmDeleteAll,
    vm::dismissDelete, vm::retry, vm::clearMessage) } }

@Composable
fun PhotosSurfaceHost(state: PhotosUiState, actions: PhotosActions, fileStore: SchedulePhotoFileStore) {
    BackHandler(onBack = actions.back)
    TransientConfirmation(state.infoMessage, actions.clearMessage) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (state.surface) {
                PhotosSurface.NONE -> Unit
                PhotosSurface.LIST -> PhotosList(state, actions, fileStore)
                PhotosSurface.VIEWER -> PhotoViewer(state, actions, fileStore)
            }
        }
    }
    if (state.pendingDeleteId != null) Confirm("Eliminar foto", "La foto se quitará de MiGuardia.", actions.confirmDelete, actions.dismissDelete)
    if (state.confirmDeleteAll) Confirm("Eliminar todas las fotos", "Se eliminarán todas las fotos de este mes.", actions.confirmDeleteAll, actions.dismissDelete)
}

@Composable private fun PhotosList(state: PhotosUiState, actions: PhotosActions, fileStore: SchedulePhotoFileStore) {
    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { actions.import(it) }
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = actions.close) { Text("Cerrar") }
                Text(state.month.label(), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }) { Text("Agregar fotos") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = actions.previous) { Text("‹ Mes anterior") }
                TextButton(onClick = actions.next) { Text("Mes siguiente ›") }
            }
        }
        state.errorMessage?.let { message -> item { Card { Column(Modifier.padding(16.dp)) { Text(message); TextButton(onClick = actions.retry) { Text("Reintentar") } } } } }
        if (state.isLoading) item { CircularProgressIndicator() }
        else if (state.photos.isEmpty()) item { Card { Text("Todavía no hay fotos del cronograma para este mes.", Modifier.padding(20.dp)) } }
        itemsIndexed(state.photos, key = { _, photo -> photo.id }) { index, photo ->
            val replacePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> if (uri != null) actions.replace(photo.id, uri) }
            var menuExpanded by remember(photo.id) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().clickable { actions.view(photo.id) }.semantics { contentDescription = "Foto ${index+1} de ${state.photos.size}${photo.objectiveAbbreviationSnapshot?.let { ", objetivo $it" } ?: ""}" }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoImage(photo, fileStore, Modifier.fillMaxWidth().height(180.dp), maxDimension = 512)
                    Text(photo.objectiveNameSnapshot ?: "Sin objetivo asociado")
                    Box {
                        TextButton(onClick = { menuExpanded = true }) { Text("Acciones") }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            state.objectives.forEach { objective ->
                                DropdownMenuItem(
                                    text = { Text("Asociar: ${objective.abbreviation} · ${objective.fullName}") },
                                    onClick = {
                                        menuExpanded = false
                                        actions.associate(photo.id, objective.id)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Quitar objetivo") },
                                onClick = {
                                    menuExpanded = false
                                    actions.associate(photo.id, null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Reemplazar") },
                                onClick = {
                                    menuExpanded = false
                                    replacePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar") },
                                onClick = {
                                    menuExpanded = false
                                    actions.requestDelete(photo.id)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (state.photos.isNotEmpty()) item { OutlinedButton(onClick = actions.requestDeleteAll, Modifier.fillMaxWidth()) { Text("Eliminar todas las fotos del mes") } }
    }
}

@Composable private fun PhotoViewer(state: PhotosUiState, actions: PhotosActions, fileStore: SchedulePhotoFileStore) {
    val index = state.photos.indexOfFirst { it.id == state.selectedId }
    val photo = state.photos.getOrNull(index)
    Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = actions.back) { Text("Volver") }
            Text(if (photo == null) "Foto no disponible" else "${index+1} de ${state.photos.size}")
            if (photo != null) TextButton(onClick = { actions.requestDelete(photo.id) }) { Text("Eliminar") } else Spacer(Modifier.width(64.dp))
        }
        if (photo != null) ZoomablePhoto(photo, fileStore, Modifier.weight(1f).fillMaxWidth())
        else Text("La foto ya no está disponible.", Modifier.padding(24.dp))
        Row { if (index > 0) TextButton(onClick = { actions.view(state.photos[index-1].id) }) { Text("Anterior") }; if (index >= 0 && index < state.photos.lastIndex) TextButton(onClick = { actions.view(state.photos[index+1].id) }) { Text("Siguiente") } }
    }
}

@Composable private fun ZoomablePhoto(photo: SchedulePhoto, fileStore: SchedulePhotoFileStore, modifier: Modifier) {
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }; var x by remember(photo.id) { mutableFloatStateOf(0f) }; var y by remember(photo.id) { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { _, zoom, pan, _ -> scale = (scale*zoom).coerceIn(1f, 5f); x += pan.x; y += pan.y }
    PhotoImage(photo, fileStore, modifier.transformable(transform).graphicsLayer(scaleX=scale, scaleY=scale, translationX=x, translationY=y), maxDimension = 2048)
}

@Composable private fun PhotoImage(photo: SchedulePhoto, fileStore: SchedulePhotoFileStore, modifier: Modifier, maxDimension: Int) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo.storageKey) { value = withContext(Dispatchers.IO) {
        val sample = generateSequence(1) { it * 2 }.takeWhile { photo.pixelWidth / it > maxDimension || photo.pixelHeight / it > maxDimension }.lastOrNull()?.times(2) ?: 1
        BitmapFactory.decodeFile(fileStore.file(photo.storageKey).absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    } }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Foto del cronograma${photo.objectiveAbbreviationSnapshot?.let { " de $it" } ?: ""}", modifier, contentScale = ContentScale.Fit)
    else Box(modifier, contentAlignment = Alignment.Center) { Text("No se pudo mostrar la foto") }
}

@Composable private fun Confirm(title: String, body: String, confirm: () -> Unit, dismiss: () -> Unit) = AlertDialog(onDismissRequest=dismiss, title={Text(title)}, text={Text(body)}, confirmButton={TextButton(onClick=confirm){Text("Eliminar")}}, dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})
private fun YearMonth.label(): String = "${month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-AR")).replaceFirstChar { it.titlecase() }} $year"
