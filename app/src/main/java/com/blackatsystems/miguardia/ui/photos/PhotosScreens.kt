package com.blackatsystems.miguardia.ui.photos

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.ui.components.DestructiveAction
import com.blackatsystems.miguardia.ui.components.EmptyState
import com.blackatsystems.miguardia.ui.components.MonthNavigator
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.PrimaryAction
import com.blackatsystems.miguardia.ui.components.SurfaceHeader
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PhotosActions(
    val open: (YearMonth) -> Unit = {},
    val close: () -> Unit = {},
    val back: () -> Unit = {},
    val previous: () -> Unit = {},
    val next: () -> Unit = {},
    val view: (UUID) -> Unit = {},
    val import: (List<Uri>) -> Unit = {},
    val associate: (UUID, UUID?) -> Unit = { _, _ -> },
    val replace: (UUID, Uri) -> Unit = { _, _ -> },
    val requestDelete: (UUID) -> Unit = {},
    val confirmDelete: () -> Unit = {},
    val dismissDelete: () -> Unit = {},
    val retry: () -> Unit = {},
    val clearMessage: () -> Unit = {},
) {
    companion object {
        fun from(vm: PhotosViewModel) = PhotosActions(
            open = vm::open,
            close = vm::close,
            back = vm::back,
            previous = vm::previousMonth,
            next = vm::nextMonth,
            view = vm::view,
            import = vm::import,
            associate = vm::associate,
            replace = vm::replace,
            requestDelete = vm::requestDelete,
            confirmDelete = vm::confirmDelete,
            dismissDelete = vm::dismissDelete,
            retry = vm::retry,
            clearMessage = vm::clearMessage,
        )
    }
}

@Composable
fun PhotosSurfaceHost(
    state: PhotosUiState,
    actions: PhotosActions,
    fileStore: SchedulePhotoFileStore,
) {
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
    if (state.pendingDeleteId != null) {
        ConfirmDestructive(
            title = "Eliminar foto",
            body = "La foto se quitará de MiGuardia. Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar foto",
            onConfirm = actions.confirmDelete,
            onDismiss = actions.dismissDelete,
        )
    }
}

@Composable
private fun PhotosList(
    state: PhotosUiState,
    actions: PhotosActions,
    fileStore: SchedulePhotoFileStore,
) {
    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { actions.import(it) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        SurfaceHeader(
            title = "Fotos del cronograma",
            navigationLabel = "Cerrar",
            onNavigation = actions.close,
        )
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MonthNavigator(
                    monthLabel = state.month.label(),
                    previousDescription = "Mes anterior de fotos",
                    nextDescription = "Mes siguiente de fotos",
                    onPrevious = actions.previous,
                    onNext = actions.next,
                )
            }
            item {
                PrimaryAction(
                    label = "Agregar fotos",
                    onClick = {
                        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    },
                    enabled = !state.isWorking,
                    working = state.isWorking,
                )
            }
            item {
                Text(
                    "Tocá cualquier foto para verla completa. Cada imagen se elimina individualmente desde Acciones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.errorMessage?.let { message ->
                item {
                    PersistentMessage(
                        message = message,
                        onDismiss = actions.clearMessage,
                        onRetry = actions.retry,
                    )
                }
            }
            when {
                state.isLoading -> item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Cargando fotos…")
                    }
                }

                state.photos.isEmpty() -> item {
                    EmptyState(
                        title = "Sin fotos para este mes",
                        message = "Todavía no hay fotos del cronograma para este mes.",
                    )
                }
            }
            itemsIndexed(state.photos, key = { _, photo -> photo.id }) { index, photo ->
                PhotoCard(
                    photo = photo,
                    index = index,
                    total = state.photos.size,
                    state = state,
                    actions = actions,
                    fileStore = fileStore,
                )
            }
        }
    }
}

@Composable
private fun PhotoCard(
    photo: SchedulePhoto,
    index: Int,
    total: Int,
    state: PhotosUiState,
    actions: PhotosActions,
    fileStore: SchedulePhotoFileStore,
) {
    val replacePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) actions.replace(photo.id, uri)
    }
    var menuExpanded by remember(photo.id) { mutableStateOf(false) }
    val objective = photo.objectiveAbbreviationSnapshot?.let { ", objetivo $it" }.orEmpty()
    Card(
        onClick = { actions.view(photo.id) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Foto ${index + 1} de $total$objective" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Foto ${index + 1} de $total",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        photo.objectiveNameSnapshot ?: "Sin objetivo asociado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    TextButton(onClick = { menuExpanded = true }) { Text("Acciones") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        state.objectives.forEach { objectiveRow ->
                            DropdownMenuItem(
                                text = { Text("Asociar: ${objectiveRow.abbreviation} · ${objectiveRow.fullName}") },
                                onClick = {
                                    menuExpanded = false
                                    actions.associate(photo.id, objectiveRow.id)
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
            PhotoImage(
                photo = photo,
                fileStore = fileStore,
                modifier = Modifier.fillMaxWidth().height(210.dp),
                maxDimension = 512,
            )
            Text(
                "Tocá la foto para abrir el visor con zoom.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhotoViewer(
    state: PhotosUiState,
    actions: PhotosActions,
    fileStore: SchedulePhotoFileStore,
) {
    val index = state.photos.indexOfFirst { it.id == state.selectedId }
    val photo = state.photos.getOrNull(index)
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SurfaceHeader(
            title = if (photo == null) "Foto no disponible" else "${index + 1} de ${state.photos.size}",
            navigationLabel = "Volver",
            onNavigation = actions.back,
        )
        HorizontalDivider()
        if (photo != null) {
            ZoomablePhoto(photo, fileStore, Modifier.weight(1f).fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { actions.view(state.photos[index - 1].id) },
                    enabled = index > 0,
                ) { Text("Anterior") }
                DestructiveAction(
                    label = "Eliminar",
                    onClick = { actions.requestDelete(photo.id) },
                )
                TextButton(
                    onClick = { actions.view(state.photos[index + 1].id) },
                    enabled = index in 0 until state.photos.lastIndex,
                ) { Text("Siguiente") }
            }
        } else {
            EmptyState(
                title = "La foto ya no está disponible",
                message = "Volvé al listado para elegir otra foto.",
                modifier = Modifier.padding(24.dp),
                actionLabel = "Volver al listado",
                onAction = actions.back,
            )
        }
    }
}

@Composable
private fun ZoomablePhoto(
    photo: SchedulePhoto,
    fileStore: SchedulePhotoFileStore,
    modifier: Modifier,
) {
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var x by remember(photo.id) { mutableFloatStateOf(0f) }
    var y by remember(photo.id) { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { _, zoom, pan, _ ->
        val nextScale = (scale * zoom).coerceIn(1f, 5f)
        if (nextScale == 1f) {
            x = 0f
            y = 0f
        } else {
            x += pan.x
            y += pan.y
        }
        scale = nextScale
    }
    PhotoImage(
        photo = photo,
        fileStore = fileStore,
        modifier = modifier
            .transformable(transform)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = x,
                translationY = y,
            ),
        maxDimension = 2048,
    )
}

@Composable
private fun PhotoImage(
    photo: SchedulePhoto,
    fileStore: SchedulePhotoFileStore,
    modifier: Modifier,
    maxDimension: Int,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo.storageKey) {
        value = withContext(Dispatchers.IO) {
            val sample = generateSequence(1) { it * 2 }
                .takeWhile {
                    photo.pixelWidth / it > maxDimension || photo.pixelHeight / it > maxDimension
                }
                .lastOrNull()
                ?.times(2)
                ?: 1
            BitmapFactory.decodeFile(
                fileStore.file(photo.storageKey).absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }
    }
    val loadedBitmap = bitmap
    if (loadedBitmap != null) {
        Image(
            bitmap = loadedBitmap.asImageBitmap(),
            contentDescription = "Foto del cronograma${photo.objectiveAbbreviationSnapshot?.let { " de $it" }.orEmpty()}",
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("No se pudo mostrar la foto")
        }
    }
}

@Composable
private fun ConfirmDestructive(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            DestructiveAction(label = confirmLabel, onClick = onConfirm)
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

private fun YearMonth.label(): String {
    val locale = Locale.forLanguageTag("es-AR")
    val monthName = month.getDisplayName(TextStyle.FULL, locale)
        .replaceFirstChar { it.titlecase(locale) }
    return "$monthName de $year"
}
