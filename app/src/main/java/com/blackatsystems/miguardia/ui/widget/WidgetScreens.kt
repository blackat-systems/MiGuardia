package com.blackatsystems.miguardia.ui.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.ui.components.MiGuardiaSpacing
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SectionCard

data class WidgetActions(
    val open: () -> Unit = {},
    val close: () -> Unit = {},
    val refresh: () -> Unit = {},
    val reconfigure: (Int) -> Unit = {},
) {
    companion object {
        fun from(viewModel: WidgetViewModel, reconfigure: (Int) -> Unit) = WidgetActions(
            open = viewModel::open,
            close = viewModel::close,
            refresh = viewModel::refresh,
            reconfigure = reconfigure,
        )
    }
}

@Composable
fun WidgetSurfaceHost(state: WidgetUiState, actions: WidgetActions) {
    if (state.surface == WidgetSurface.NONE) return
    Dialog(onDismissRequest = actions.close) {
        // Style v2: same background, width limit and padding as Notificaciones and Clima.
        Surface(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .padding(MiGuardiaSpacing.large)
                    .testTag("widget-management-surface"),
                verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    ScreenHeading(title = "Widget de inicio", modifier = Modifier.weight(1f))
                    TextButton(onClick = actions.close, modifier = Modifier.testTag("widget-management-close")) {
                        Text("Cerrar")
                    }
                }
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium),
                ) {
                    Text(
                        "Gestioná las instancias que agregaste a la pantalla de inicio de Android.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.errorMessage?.let { message ->
                        PersistentMessage(message, onDismiss = actions.close, onRetry = actions.refresh)
                    }
                    if (state.isLoading) {
                        Row(
                            Modifier.fillMaxWidth().testTag("widget-loading"),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (state.instances.isEmpty()) {
                        SectionCard("Todavía no hay Widgets") {
                            Text("Mantené presionada la pantalla de inicio de Android, elegí Widgets y buscá MiGuardia.")
                            Text("La configuración inicial es obligatoria y empieza en privacidad Oculta.")
                        }
                    } else {
                        state.instances.forEach { instance ->
                            SectionCard(
                                title = "Widget ${instance.position}",
                                supportingText = if (instance.preferences.configured) {
                                    "${instance.preferences.mode.label()} · ${instance.preferences.privacy.label()}"
                                } else {
                                    "Configuración pendiente · Oculta"
                                },
                            ) {
                                Text(
                                    if (instance.preferences.includeWeather) {
                                        "Clima opcional activado para esta instancia."
                                    } else {
                                        "Clima opcional desactivado para esta instancia."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Button(
                                    onClick = { actions.reconfigure(instance.appWidgetId) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("widget-reconfigure-${instance.position}"),
                                ) {
                                    Text(if (instance.preferences.configured) "Reconfigurar" else "Configurar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun WidgetMode.label(): String = when (this) {
    WidgetMode.NEXT_SHIFT -> "Próxima jornada"
    WidgetMode.NEXT_DAY_OFF -> "Próximo franco"
    WidgetMode.AUTOMATIC -> "Automático"
}

private fun WidgetPrivacy.label(): String = when (this) {
    WidgetPrivacy.COMPLETE -> "Completa"
    WidgetPrivacy.REDUCED -> "Reducida"
    WidgetPrivacy.HIDDEN -> "Oculta"
}
