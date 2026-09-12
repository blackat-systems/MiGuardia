package com.blackatsystems.miguardia.updates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun UpdatesScreen(state: UpdatesState, onSearch: () -> Unit, onDownload: () -> Unit,
    onSave: () -> Unit, onCancel: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize().testTag("updates-surface"), color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
        .padding(20.dp).testTag("updates-screen"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onClose, modifier = Modifier.testTag("updates-back")) { Text("Volver") }
        Text(stringResource(R.string.updates_title), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() })
        Text("Versión instalada: ${state.installed?.versionName ?: "No disponible"}")
        Text(stringResource(R.string.updates_intro))
        val status = when (state.stage) {
            UpdateStage.IDLE -> "Buscá cuando quieras comprobar si hay otra versión."
            UpdateStage.CHECKING -> "Buscando actualización…"
            UpdateStage.NO_RELEASE -> "Todavía no hay una versión publicada para descargar."
            UpdateStage.CURRENT -> "No hay una versión más nueva que la instalada."
            UpdateStage.AVAILABLE -> "Hay una actualización disponible."
            UpdateStage.INCOMPATIBLE -> "Esta versión no es compatible con esta instalación."
            UpdateStage.DOWNLOADING -> "Descargando: ${size(state.bytes)} de ${size(state.release?.byteSize ?: 0)}"
            UpdateStage.VERIFYING -> "Comprobando el APK…"
            UpdateStage.READY -> "El APK está verificado y listo para guardar."
            UpdateStage.PICKER -> "Elegí dónde guardar el APK."
            UpdateStage.SAVING -> "Guardando y comprobando el archivo…"
            UpdateStage.SAVED -> "APK guardado. La actualización todavía no está instalada."
            UpdateStage.CANCELLED -> "Operación cancelada."
            UpdateStage.INTERRUPTED -> "La operación se interrumpió. Volvé a buscar para continuar."
            UpdateStage.INVALID_RESPONSE -> "La respuesta recibida no es válida. Volvé a buscar más tarde."
            UpdateStage.UNAVAILABLE -> "Actualizaciones no disponibles por el momento."
            UpdateStage.ERROR -> "No se completó la operación."
        }
        Text(status, modifier = Modifier.testTag("updates-status").semantics { liveRegion = LiveRegionMode.Polite })
        state.release?.let { Text("Versión: ${it.versionName}\nTamaño: ${size(it.byteSize)}") }
        state.message?.let { Text(it) }
        if (state.retryRemainingMillis > 0) Text("Podés reintentar en ${(state.retryRemainingMillis + 999) / 1000} segundos.")
        if (state.busy && state.stage != UpdateStage.PICKER) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (state.stage) {
            UpdateStage.AVAILABLE -> Button(onClick = onDownload, enabled = state.retryRemainingMillis == 0L, modifier = Modifier.fillMaxWidth().testTag("updates-download")) { Text("Descargar actualización") }
            UpdateStage.READY -> {
                Text("Elegí una ubicación fácil de encontrar, por ejemplo Descargas.")
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth().testTag("updates-save")) { Text("Guardar APK") }
            }
            else -> if (!state.busy) Button(onClick = onSearch, enabled = state.retryRemainingMillis == 0L, modifier = Modifier.fillMaxWidth().testTag("updates-search")) { Text("Buscar actualización") }
        }
        if (state.busy || state.stage == UpdateStage.READY || state.stage == UpdateStage.AVAILABLE) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().testTag("updates-cancel")) { Text("Cancelar") }
        }
        if (state.stage == UpdateStage.SAVED) {
            Text(if (state.saveVerification == UpdateSaveVerification.READ_BACK) "Comprobamos el archivo guardado volviéndolo a leer."
                else "Comprobamos los bytes escritos. El destino no permitió volver a leer el archivo.")
        }
        Text(stringResource(R.string.updates_install_help))
        Text("Si querés, antes podés crear una copia desde Copias de seguridad en el menú.")
    }
    }
}

private fun size(bytes: Long) = String.format(Locale.forLanguageTag("es-AR"), "%.1f MiB", bytes / (1024.0 * 1024.0))
