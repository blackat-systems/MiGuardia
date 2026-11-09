package com.blackatsystems.miguardia.updates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.ui.components.MiGuardiaSpacing
import com.blackatsystems.miguardia.ui.components.PrimaryAction
import com.blackatsystems.miguardia.ui.components.SecondaryAction
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.components.vigiliaScreenBackground
import java.util.Locale

@Composable
internal fun UpdatesScreen(
    state: UpdatesState,
    onSearch: () -> Unit,
    onDownload: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Surface(
        modifier = Modifier.fillMaxSize().testTag("updates-surface"),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .vigiliaScreenBackground()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(MiGuardiaSpacing.large)
                .testTag("updates-screen"),
            verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.large),
        ) {
            UpdatesHeader(title = stringResource(R.string.updates_title), onClose = onClose)
            Text(
                stringResource(R.string.updates_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Versión instalada: ${state.installed?.versionName ?: "No disponible"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionCard("Estado de la actualización") {
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
                Text(
                    status,
                    modifier = Modifier.testTag("updates-status").semantics { liveRegion = LiveRegionMode.Polite },
                )
                state.release?.let { Text("Versión: ${it.versionName}\nTamaño: ${size(it.byteSize)}") }
                state.message?.let { Text(it) }
                if (state.retryRemainingMillis > 0) {
                    Text("Podés reintentar en ${(state.retryRemainingMillis + 999) / 1000} segundos.")
                }
                if (state.busy && state.stage != UpdateStage.PICKER) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                when (state.stage) {
                    UpdateStage.AVAILABLE -> PrimaryAction(
                        label = "Descargar actualización",
                        onClick = onDownload,
                        enabled = state.retryRemainingMillis == 0L,
                        modifier = Modifier.testTag("updates-download"),
                    )
                    UpdateStage.READY -> {
                        Text("Elegí una ubicación fácil de encontrar, por ejemplo Descargas.")
                        PrimaryAction(
                            label = "Guardar APK",
                            onClick = onSave,
                            modifier = Modifier.testTag("updates-save"),
                        )
                    }
                    else -> if (!state.busy) {
                        PrimaryAction(
                            label = "Buscar actualización",
                            onClick = onSearch,
                            enabled = state.retryRemainingMillis == 0L,
                            modifier = Modifier.testTag("updates-search"),
                        )
                    }
                }
                if (state.busy || state.stage == UpdateStage.READY || state.stage == UpdateStage.AVAILABLE) {
                    SecondaryAction(
                        label = "Cancelar",
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth().testTag("updates-cancel"),
                    )
                }
                if (state.stage == UpdateStage.SAVED) {
                    Text(
                        if (state.saveVerification == UpdateSaveVerification.READ_BACK) {
                            "Comprobamos el archivo guardado volviéndolo a leer."
                        } else {
                            "Comprobamos los bytes escritos. El destino no permitió volver a leer el archivo."
                        },
                    )
                }
            }
            SectionCard("Instalación manual") {
                Text(stringResource(R.string.updates_install_help))
                Text("Si querés, antes podés crear una copia desde Copias de seguridad en el menú.")
            }
        }
    }
}

/**
 * Same layout as the common SurfaceHeader (title on the left, action on the right, action on top
 * when the longest word does not fit beside it), kept local because the action needs its
 * `updates-back` tag and must stay inside the scroll container that the tests scroll to.
 */
@Composable
private fun UpdatesHeader(title: String, onClose: () -> Unit) {
    val titleStyle = MaterialTheme.typography.titleLarge
    val actionStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val longestWord = title.split(' ').maxByOrNull { it.length }.orEmpty()
        val wordWidth = measurer.measure(longestWord, titleStyle, softWrap = false).size.width
        val actionWidth = measurer.measure("Volver", actionStyle, softWrap = false).size.width +
            with(density) { (24.dp + MiGuardiaSpacing.small).roundToPx() }
        val back: @Composable (Modifier) -> Unit = { modifier ->
            TextButton(onClick = onClose, modifier = modifier.testTag("updates-back")) { Text("Volver") }
        }
        if (wordWidth + actionWidth <= constraints.maxWidth) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
            ) {
                Text(title, modifier = Modifier.weight(1f).semantics { heading() }, style = titleStyle)
                back(Modifier)
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                back(Modifier.align(Alignment.End))
                Text(title, modifier = Modifier.fillMaxWidth().semantics { heading() }, style = titleStyle)
            }
        }
    }
}

private fun size(bytes: Long) = String.format(Locale.forLanguageTag("es-AR"), "%.1f MiB", bytes / (1024.0 * 1024.0))
