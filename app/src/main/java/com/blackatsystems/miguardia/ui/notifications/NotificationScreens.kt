package com.blackatsystems.miguardia.ui.notifications

import android.Manifest
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SectionCard

data class NotificationActions(
    val openGlobal: () -> Unit = {},
    val openShift: (Shift) -> Unit = {},
    val close: () -> Unit = {},
    val refreshSystemAccess: () -> Unit = {},
    val setEnabled: (Boolean) -> Unit = {},
    val setPreciseTiming: (Boolean) -> Unit = {},
    val setPersistent: (Boolean) -> Unit = {},
    val setPrivacy: (NotificationPrivacy) -> Unit = {},
    val setSound: (Uri?) -> Unit = {},
    val setGlobalReminders: (Collection<Long>) -> Unit = {},
    val setShiftReminders: (Collection<Long>) -> Unit = {},
    val disableShift: () -> Unit = {},
    val useGlobalForShift: () -> Unit = {},
    val clearMessage: () -> Unit = {},
    val retry: () -> Unit = {},
) {
    companion object {
        fun from(viewModel: NotificationViewModel) = NotificationActions(
            openGlobal = viewModel::openGlobal,
            openShift = viewModel::openShift,
            close = viewModel::close,
            refreshSystemAccess = viewModel::refreshSystemAccess,
            setEnabled = viewModel::setEnabled,
            setPreciseTiming = viewModel::setPreciseTiming,
            setPersistent = viewModel::setPersistent,
            setPrivacy = viewModel::setPrivacy,
            setSound = viewModel::setSound,
            setGlobalReminders = viewModel::setGlobalReminders,
            setShiftReminders = viewModel::setShiftReminders,
            disableShift = viewModel::disableShift,
            useGlobalForShift = viewModel::useGlobalForShift,
            clearMessage = viewModel::clearMessage,
            retry = viewModel::retry,
        )
    }
}
@Composable
fun NotificationSurfaceHost(state: NotificationUiState, actions: NotificationActions) {
    if (state.surface == NotificationSurface.NONE) return
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { actions.refreshSystemAccess() }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { actions.refreshSystemAccess() }
    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        @Suppress("DEPRECATION")
        val selected = if (Build.VERSION.SDK_INT >= 33) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        if (result.resultCode == android.app.Activity.RESULT_OK) actions.setSound(selected)
    }
    Dialog(onDismissRequest = actions.close) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            ScreenHeading(
                if (state.surface == NotificationSurface.GLOBAL) "Notificaciones" else "Avisos de la guardia",
                supportingText = if (state.surface == NotificationSurface.GLOBAL) {
                    "MiGuardia funciona aunque no concedas permisos; podés corregirlos después."
                } else {
                    state.selectedShift?.let { "${it.objectiveAbbreviationSnapshot} · ${it.startTimeSnapshot}–${it.endTimeSnapshot}" }.orEmpty()
                },
            )
            state.errorMessage?.let {
                PersistentMessage(it, onDismiss = actions.clearMessage, onRetry = actions.retry)
            }
            state.infoMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (state.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            } else when (state.surface) {
                NotificationSurface.GLOBAL -> GlobalSettings(
                    state = state,
                    actions = actions,
                    requestPermission = {
                        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else actions.refreshSystemAccess()
                    },
                    requestExactAccess = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            settingsLauncher.launch(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData("package:${context.packageName}".toUri()),
                            )
                        } else {
                            actions.refreshSystemAccess()
                        }
                    },
                    openAppSettings = {
                        settingsLauncher.launch(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                    chooseSound = {
                        ringtoneLauncher.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, state.preferences.soundUri),
                        )
                    },
                )
                NotificationSurface.SHIFT -> ShiftSettings(state, actions)
                NotificationSurface.NONE -> Unit
            }
                OutlinedButton(onClick = actions.close, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
            }
        }
    }
}

@Composable
private fun GlobalSettings(
    state: NotificationUiState,
    actions: NotificationActions,
    requestPermission: () -> Unit,
    requestExactAccess: () -> Unit,
    openAppSettings: () -> Unit,
    chooseSound: () -> Unit,
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    SectionCard(
        title = if (state.preferences.enabled) "Avisos activados" else "Avisos desactivados",
        supportingText = if (state.preferences.enabled) {
            "Te guiamos en tres pasos. Podés cambiar cada detalle cuando quieras."
        } else {
            "MiGuardia no mostrará avisos hasta que los actives."
        },
    ) {
        ToggleRow("Usar notificaciones", state.preferences.enabled, actions.setEnabled)
        if (!state.preferences.enabled) {
            Button(onClick = { actions.setEnabled(true) }, modifier = Modifier.fillMaxWidth()) {
                Text("Activar avisos")
            }
            TextButton(onClick = openAppSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir ajustes de notificaciones")
            }
        }
    }
    if (!state.preferences.enabled) return

    SectionCard(
        title = "1. Permití los avisos",
        supportingText = "Android necesita tu permiso para mostrar notificaciones.",
    ) {
        PermissionRow(
            "Notificaciones",
            state.systemAccess.notificationPermissionGranted,
            if (state.systemAccess.notificationPermissionGranted) openAppSettings else requestPermission,
        )
        TextButton(onClick = openAppSettings) {
            Text("Abrir ajustes de notificaciones")
        }
        if (!state.systemAccess.notificationPermissionGranted) {
            Text("Cuando lo resuelvas, vas a poder elegir cuándo y cómo querés recibir cada aviso.")
        }
    }
    if (!state.systemAccess.notificationPermissionGranted) return

    SectionCard(
        title = "2. Elegí cuándo avisar",
        supportingText = reminderSummary(state.preferences.globalReminderLeadMinutes),
    ) {
        Text("Elegí una opción. Recomendamos 12 horas antes.")
        listOf(6L, 8L, 12L, 24L).chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { hours ->
                    val selected = state.preferences.globalReminderLeadMinutes == listOf(hours * 60L)
                    if (selected) {
                        Button(
                            onClick = { actions.setGlobalReminders(listOf(hours * 60L)) },
                            modifier = Modifier.weight(1f),
                        ) { Text("$hours h") }
                    } else {
                        OutlinedButton(
                            onClick = { actions.setGlobalReminders(listOf(hours * 60L)) },
                            modifier = Modifier.weight(1f),
                        ) { Text("$hours h") }
                    }
                }
            }
        }
    }
    SectionCard(
        title = "3. Elegí cómo se muestra",
        supportingText = if (state.preferences.persistentWhileActive) {
            "Queda visible mientras la guardia está en curso."
        } else {
            "Podés descartarla como cualquier otra notificación."
        },
    ) {
        ChoiceRow("Fija durante la guardia", state.preferences.persistentWhileActive) {
            actions.setPersistent(true)
        }
        ChoiceRow("Descartable", !state.preferences.persistentWhileActive) {
            actions.setPersistent(false)
        }
        Text("El contador queda dentro de la notificación y Android lo actualiza sin despertar MiGuardia cada minuto.")
    }
    OutlinedButton(
        onClick = { showAdvanced = !showAdvanced },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (showAdvanced) "Ocultar opciones avanzadas" else "Ver opciones avanzadas")
    }
    if (showAdvanced) {
        ReminderEditor(
            title = "Más de un aviso",
            values = state.preferences.globalReminderLeadMinutes,
            onChange = actions.setGlobalReminders,
        )
        SectionCard(
            title = "Puntualidad",
            supportingText = "Es opcional. Sin este acceso, Android puede demorar algunos minutos el aviso.",
        ) {
            PermissionRow("Puntualidad exacta", state.systemAccess.exactAlarmAccessGranted, requestExactAccess)
            ToggleRow("Intentar publicar exactamente a horario", state.preferences.preciseTiming, actions.setPreciseTiming)
            Text("Es una notificación común: nunca funciona como despertador.")
        }
        SectionCard("Privacidad y sonido") {
            Text("Pantalla bloqueada", style = MaterialTheme.typography.titleSmall)
            NotificationPrivacy.entries.forEach { privacy ->
                ChoiceRow(privacyLabel(privacy), state.preferences.privacy == privacy) { actions.setPrivacy(privacy) }
            }
            Text(if (state.preferences.soundUri == null) "Sonido: predeterminado de Android" else "Sonido: elegido en Android")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = chooseSound) { Text("Elegir sonido") }
                TextButton(onClick = { actions.setSound(null) }) { Text("Predeterminado") }
            }
            Text("Vibración: MiGuardia la solicita; Android conserva el control final.")
        }
    }
}

@Composable
private fun ShiftSettings(state: NotificationUiState, actions: NotificationActions) {
    var showEditor by rememberSaveable { mutableStateOf(false) }
    val override = state.shiftOverride
    val effective = override?.reminderLeadMinutes ?: state.preferences.globalReminderLeadMinutes
    SectionCard(
        title = when {
            override == null -> "Usa la configuración general"
            override.reminderLeadMinutes.isEmpty() -> "Avisos desactivados"
            else -> "Configuración propia"
        },
        supportingText = if (effective.isEmpty()) "Esta guardia no tendrá avisos." else reminderSummary(effective),
    ) {
        when {
            override == null -> {
                Button(
                    onClick = {
                        actions.setShiftReminders(state.preferences.globalReminderLeadMinutes)
                        showEditor = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Personalizar esta guardia") }
                OutlinedButton(onClick = actions.disableShift, modifier = Modifier.fillMaxWidth()) {
                    Text("Desactivar sólo en esta guardia")
                }
            }
            override.reminderLeadMinutes.isEmpty() -> {
                Button(onClick = actions.useGlobalForShift, modifier = Modifier.fillMaxWidth()) {
                    Text("Volver a usar la configuración general")
                }
                OutlinedButton(
                    onClick = {
                        actions.setShiftReminders(state.preferences.globalReminderLeadMinutes)
                        showEditor = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Personalizar esta guardia") }
            }
            else -> {
                Button(onClick = { showEditor = !showEditor }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showEditor) "Listo" else "Cambiar horarios de aviso")
                }
                OutlinedButton(onClick = actions.useGlobalForShift, modifier = Modifier.fillMaxWidth()) {
                    Text("Usar configuración general")
                }
                TextButton(onClick = actions.disableShift, modifier = Modifier.fillMaxWidth()) {
                    Text("Desactivar sólo en esta guardia")
                }
            }
        }
    }
    if (showEditor && override?.reminderLeadMinutes?.isNotEmpty() == true) {
        ReminderEditor("Horarios de esta guardia", effective) { actions.setShiftReminders(it) }
    }
}

@Composable
private fun ReminderEditor(title: String, values: List<Long>, onChange: (Collection<Long>) -> Unit) {
    var customHours by remember { mutableStateOf("") }
    SectionCard(
        title = title,
        supportingText = "Entre cero y cinco avisos únicos; los avisos ya pasados no se recuperan tarde.",
    ) {
        if (values.isEmpty()) Text("No hay avisos configurados.")
        listOf(6L, 8L, 12L, 24L).forEach { hours ->
            val minutes = hours * 60L
            val selected = minutes in values
            ChoiceRow("$hours horas antes", selected) {
                val updated = if (selected) values - minutes else (values + minutes).distinct()
                if (updated.size <= 5) onChange(updated)
            }
        }
        values.filter { it % 60L != 0L || it / 60L !in setOf(6L, 8L, 12L, 24L) }.forEach { minutes ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$minutes minutos antes", modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(values - minutes) }) { Text("Quitar") }
            }
        }
        OutlinedTextField(
            value = customHours,
            onValueChange = { customHours = it.filter(Char::isDigit) },
            label = { Text("Valor personalizado en minutos") },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            enabled = customHours.toLongOrNull()?.let { it > 0L && it !in values && values.size < 5 } == true,
            onClick = {
                customHours.toLongOrNull()?.let { onChange(values + it) }
                customHours = ""
            },
        ) { Text("Agregar aviso") }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onResolve: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ${if (granted) "concedido" else "pendiente"}", modifier = Modifier.weight(1f))
        TextButton(onClick = onResolve) { Text(if (granted) "Ajustes" else "Resolver") }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = onClick)
        Text(label)
    }
}

private fun privacyLabel(value: NotificationPrivacy): String = when (value) {
    NotificationPrivacy.COMPLETE -> "Completa: objetivo, horario y puesto"
    NotificationPrivacy.REDUCED -> "Reducida: estado y horario"
    NotificationPrivacy.HIDDEN -> "Oculta: mensaje genérico"
}

private fun reminderSummary(values: List<Long>): String = when (values.size) {
    0 -> "Sin avisos previos."
    1 -> {
        val minutes = values.single()
        if (minutes % 60L == 0L) "Un aviso ${minutes / 60L} horas antes." else "Un aviso $minutes minutos antes."
    }
    else -> "${values.size} avisos configurados."
}
