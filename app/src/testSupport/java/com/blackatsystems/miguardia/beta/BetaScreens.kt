package com.blackatsystems.miguardia.beta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel

@Composable
internal fun rememberBetaSafePoint() = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }

internal fun BetaState.allowsContentAt(safePoint: Boolean): Boolean =
    allowsContent || (activationValid && !safePoint)

@Composable
internal fun BetaExitBackHandler(enabled: Boolean, coordinator: BetaCoordinator, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled) { coordinator.cancelActivation(); onClose() }
}

/** Deliberately memory-only: no SavedStateHandle and no access code on disk. */
internal class BetaDraftViewModel : ViewModel() {
    var code by mutableStateOf("")
    var category by mutableStateOf<BetaCategory?>(null)
    var area by mutableStateOf<BetaArea?>(null)
    var message by mutableStateOf("")
    var consent by mutableStateOf(false)
    fun clearOpinion() { category = null; area = null; message = ""; consent = false }
}

@Composable
internal fun BetaScreen(state: BetaState, draft: BetaDraftViewModel, coordinator: BetaCoordinator, onClose: () -> Unit) {
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState())
            .padding(20.dp).testTag("beta-screen"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (state.phase == BetaPhase.OPINION) "Tu opinión sobre MiGuardia" else "Acceso a la beta",
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            when (state.phase) {
                BetaPhase.LOADING -> { CircularProgressIndicator(); Text("Comprobando el acceso…") }
                BetaPhase.UNAVAILABLE, BetaPhase.REPAIR -> {
                    Button(onClick = coordinator::retryRead) { Text("Reintentar") }
                }
                BetaPhase.ACCESS -> {
                    Text("Ingresá el código que recibiste desde la web. La primera activación necesita Internet; después podés trabajar sin conexión.")
                    OutlinedTextField(draft.code, { if (it.length <= 80) draft.code = it },
                        label = { Text("Código de acceso") }, enabled = !state.busy, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("beta-code"))
                    Button(onClick = { coordinator.activate(draft.code) }, enabled = !state.busy && draft.code.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("beta-activate")) { Text(if (state.busy) "Activando…" else "Activar esta instalación") }
                }
                BetaPhase.OPINION -> {
                    Text("Pasaron quince días desde la activación. Contanos tu experiencia, aunque algo no te haya gustado. No incluyas nombres, horarios, direcciones, datos médicos ni otra información privada.")
                    Text("Categoría", modifier = Modifier.semantics { heading() })
                    BetaCategory.entries.forEach { item ->
                        BetaChoice(item.label, draft.category == item, !state.busy) { draft.category = item }
                    }
                    Text("Área", modifier = Modifier.semantics { heading() })
                    BetaArea.entries.forEach { item ->
                        BetaChoice(item.label, draft.area == item, !state.busy) { draft.area = item }
                    }
                    OutlinedTextField(draft.message, { if (it.length <= 2200) draft.message = it }, label = { Text("Tu opinión (hasta 2.000 caracteres)") },
                        enabled = !state.busy, minLines = 3, modifier = Modifier.fillMaxWidth().testTag("beta-message"))
                    Row(Modifier.fillMaxWidth().toggleable(draft.consent, enabled = !state.busy, role = Role.Checkbox) { draft.consent = it },
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(draft.consent, onCheckedChange = null)
                        Text("Acepto enviar esta opinión a Blackat Systems para mejorar MiGuardia.", Modifier.weight(1f))
                    }
                    val opinion = runCatching { BetaOpinion.create(checkNotNull(draft.category), checkNotNull(draft.area), draft.message, draft.consent) }.getOrNull()
                    Text("Si no podemos enviarla, primero la guardamos en este teléfono y podés continuar. Reintentamos con la app abierta; el envío pendiente no habilita futuras actualizaciones.")
                    if (draft.message.isNotBlank() && runCatching { BetaOpinion.create(BetaCategory.OTHER, BetaArea.OTHER, draft.message, true) }.isFailure) {
                        Text("Revisá el mensaje: debe tener entre 1 y 2.000 caracteres y no contener caracteres de control ocultos.", color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = { opinion?.let(coordinator::confirm) }, enabled = opinion != null && !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("beta-confirm")) { Text(if (state.busy) "Guardando…" else "Confirmar mi opinión") }
                }
                else -> {
                    Text(when (state.phase) {
                        BetaPhase.RECEIVED -> "Opinión recibida y comprobada. Gracias por contar tu experiencia."
                        BetaPhase.SENDING -> "Tu opinión está guardada. Intentando enviarla…"
                        BetaPhase.QUEUED, BetaPhase.SEND_ERROR -> "Opinión guardada en este teléfono. Recepción pendiente."
                        else -> "Esta instalación está activada. Podés usar MiGuardia sin conexión."
                    }, Modifier.testTag("beta-status"))
                    if (state.phase == BetaPhase.ACTIVE) Text("A los quince días desde la activación te pediremos una opinión honesta.")
                    if (state.phase in listOf(BetaPhase.QUEUED, BetaPhase.SENDING, BetaPhase.SEND_ERROR)) {
                        Text("Podés usar esta versión. Una futura actualización requiere que el servicio confirme la recepción. Los reintentos se hacen con MiGuardia abierta.")
                        OutlinedButton(onClick = { coordinator.retryFeedback(explicit = true) }, enabled = !state.busy) { Text("Reintentar envío") }
                    }
                }
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            OutlinedButton(onClick = { if (!state.activationValid) coordinator.cancelActivation(); onClose() },
                enabled = state.phase != BetaPhase.OPINION || !state.busy,
                modifier = Modifier.fillMaxWidth()) { Text(if (state.allowsContent) "Continuar" else "Salir") }
        }
    }
}
@Composable
private fun BetaChoice(label: String, selected: Boolean, enabled: Boolean, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = choose),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, Modifier.weight(1f))
    }
}
