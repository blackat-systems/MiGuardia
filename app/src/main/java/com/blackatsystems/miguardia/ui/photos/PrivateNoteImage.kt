package com.blackatsystems.miguardia.ui.photos

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
import com.blackatsystems.miguardia.ui.components.MiGuardiaSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val noteBitmapDecoder = SchedulePhotoBitmapDecoder()
private data class NoteImageLoad(val bitmap: Bitmap? = null, val loading: Boolean = true)

/** The same sampled, EXIF-aware private decoder serves drafts and confirmed notes. */
@Composable
internal fun PrivateNoteImage(
    storageKey: String,
    imageStore: SchedulePhotoFileStore,
    modifier: Modifier = Modifier,
    description: String = "Imagen adjunta a la nota",
    maxDimension: Int = 384,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val loadedState by produceState(NoteImageLoad(), imageStore, storageKey, maxDimension) {
        value = NoteImageLoad()
        value = withContext(Dispatchers.IO) {
            NoteImageLoad(runCatching { noteBitmapDecoder.decode(imageStore.file(storageKey), maxDimension)?.bitmap }.getOrNull(),
                loading = false)
        }
    }
    val loaded = loadedState.bitmap
    if (loaded == null) {
        val message = if (loadedState.loading) "Cargando imagen" else "Imagen no disponible"
        Box(modifier.semantics { contentDescription = "$description, $message" },
            contentAlignment = Alignment.Center) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Image(loaded.asImageBitmap(), description, modifier, contentScale = contentScale)
    }
}

@Composable
internal fun NoteDraftAttachment(
    attachment: ShiftNoteAttachment,
    index: Int,
    imageStore: SchedulePhotoFileStore,
    enabled: Boolean,
    onView: () -> Unit,
    onRemove: () -> Unit,
) {
    val number = index + 1
    Row(Modifier.fillMaxWidth().testTag("note-draft-image-$number"),
        horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
        PrivateNoteImage(attachment.storageKey, imageStore,
            Modifier.size(88.dp).clickable(enabled = enabled, role = Role.Button,
                onClickLabel = "Ver imagen $number", onClick = onView),
            description = "Imagen $number de la nota")
        Column(Modifier.weight(1f)) {
            Text("Imagen $number", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onRemove, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Quitar imagen $number")
            }
        }
    }
}

@Composable
internal fun PrivateNoteImageDialog(storageKey: String, imageStore: SchedulePhotoFileStore, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Imagen de la nota") },
        text = { PrivateNoteImage(storageKey, imageStore, Modifier.fillMaxWidth().heightIn(max = 420.dp),
            maxDimension = 1600, contentScale = ContentScale.Fit) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}
