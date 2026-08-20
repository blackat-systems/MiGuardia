package com.blackatsystems.miguardia.ui.notifications

import android.Manifest
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.notifications.NotificationAttentionMode
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
import com.blackatsystems.miguardia.notifications.NotificationRhythm
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import java.time.format.DateTimeFormatter
import java.util.UUID

data class NotificationActions(
    val openGlobal: () -> Unit = {},
    val openShift: (Shift) -> Unit = {},
    val close: () -> Unit = {},
    val refreshSystemAccess: () -> Unit = {},
    val setEnabled: (Boolean) -> Unit = {},
    val setPreciseTiming: (Boolean) -> Unit = {},
    val setPersistent: (Boolean) -> Unit = {},
    val setPrivacy: (NotificationPrivacy) -> Unit = {},
    val setAttentionMode: (NotificationAttentionMode) -> Unit = {},
    val applyRhythm: (NotificationRhythm) -> Unit = {},
    val setSound: (Uri?) -> Unit = {},
    val setGlobalReminders: (Collection<Long>) -> Unit = {},
    val setShiftReminders: (Collection<Long>) -> Unit = {},
    val disableShift: () -> Unit = {},
    val useGlobalForShift: () -> Unit = {},
    val restoreNotification: (UUID) -> Unit = {},
    val restoreAllNotifications: () -> Unit = {},
    val sendTestNotification: () -> Unit = {},
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
            setAttentionMode = viewModel::setAttentionMode,
            applyRhythm = viewModel::applyRhythm,
            setSound = viewModel::setSound,
            setGlobalReminders = viewModel::setGlobalReminders,
            setShiftReminders = viewModel::setShiftReminders,
            disableShift = viewModel::disableShift,
            useGlobalForShift = viewModel::useGlobalForShift,
            restoreNotification = viewModel::restoreNotification,
            restoreAllNotifications = viewModel::restoreAllNotifications,
            sendTestNotification = viewModel::sendTestNotification,
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
    RestorableNotifications(state, actions)
    NotificationPreview(state, actions)
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

    NotificationRhythmSettings(state, actions)

    SectionCard(
        title = "Cuándo te acompaña",
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
        title = "Permanencia",
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
    SectionCard(
        title = "Cómo llama tu atención",
        supportingText = attentionSummary(state.preferences.attentionMode),
    ) {
        NotificationAttentionMode.entries.forEach { mode ->
            ChoiceRow(attentionLabel(mode), state.preferences.attentionMode == mode) {
                actions.setAttentionMode(mode)
            }
        }
        if (state.preferences.attentionMode == NotificationAttentionMode.SOUND_AND_VIBRATION) {
            Text(
                if (state.preferences.soundUri == null) {
                    "Sonido: predeterminado de Android"
                } else {
                    "Sonido: elegido en Android"
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = chooseSound) { Text("Elegir sonido") }
                TextButton(onClick = { actions.setSound(null) }) { Text("Predeterminado") }
            }
        }
        Text("Android conserva el control final del canal, el sonido y la vibración.")
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
        SectionCard("Privacidad") {
            Text("Pantalla bloqueada", style = MaterialTheme.typography.titleSmall)
            NotificationPrivacy.entries.forEach { privacy ->
                ChoiceRow(privacyLabel(privacy), state.preferences.privacy == privacy) { actions.setPrivacy(privacy) }
            }
        }
    }
}

@Composable
private fun NotificationPreview(state: NotificationUiState, actions: NotificationActions) {
    SectionCard(
        title = "Vista previa",
        supportingText = "Contenido ficticio. No crea guardias ni modifica tu calendario.",
    ) {
        PulsoVigiliaPreview(state.preferences.privacy)
        Button(
            onClick = actions.sendTestNotification,
            enabled = state.systemAccess.notificationPermissionGranted && !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Enviar notificación de prueba")
        }
        Text("La prueba usa datos ficticios y desaparece sola en un minuto.")
    }
}

@Composable
private fun PulsoVigiliaPreview(privacy: NotificationPrivacy) {
    val colors = MaterialTheme.vigiliaColors
    val accent = if (privacy == NotificationPrivacy.HIDDEN) {
        MaterialTheme.colorScheme.outline
    } else {
        Color(0xFF8B5CFF)
    }
    val title = when (privacy) {
        NotificationPrivacy.COMPLETE -> "PRÓXIMA GUARDIA · Hospital Norte"
        NotificationPrivacy.REDUCED -> "PRÓXIMA GUARDIA"
        NotificationPrivacy.HIDDEN -> "MiGuardia"
    }
    val schedule = when (privacy) {
        NotificationPrivacy.COMPLETE -> "NOR · 19:00–07:00"
        NotificationPrivacy.REDUCED -> "Horario 19:00–07:00"
        NotificationPrivacy.HIDDEN -> "Tenés un aviso de guardia."
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Vista previa Pulso Vigilia" },
        color = colors.surfaceRaised,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.outlineSubtle),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(schedule, style = MaterialTheme.typography.bodyMedium)
                if (privacy == NotificationPrivacy.COMPLETE) {
                    Text("Acceso principal", style = MaterialTheme.typography.bodySmall)
                    Text("Clima: fresco, sin lluvia prevista", style = MaterialTheme.typography.bodySmall)
                }
                if (privacy != NotificationPrivacy.HIDDEN) {
                    Text(
                        "Comienza en 3 h 12 min",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.active,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRhythmSettings(state: NotificationUiState, actions: NotificationActions) {
    val current = state.preferences.rhythm()
    SectionCard(
        title = "Ritmo de avisos",
        supportingText = "Actual: ${rhythmLabel(current)}. Elegí una base y ajustala cuando quieras.",
    ) {
        listOf(
            NotificationRhythm.ACCOMPANIED,
            NotificationRhythm.ESSENTIAL,
            NotificationRhythm.DISCREET,
        ).forEach { rhythm ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { actions.applyRhythm(rhythm) }
                    .padding(vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = current == rhythm, onClick = { actions.applyRhythm(rhythm) })
                    Text(rhythmLabel(rhythm), style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    rhythmDescription(rhythm),
                    modifier = Modifier.padding(start = 48.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (current == NotificationRhythm.CUSTOM) {
            Text("Personalizado combina los ajustes que elegiste.")
        }
    }
}

@Composable
private fun RestorableNotifications(state: NotificationUiState, actions: NotificationActions) {
    if (state.restorableShifts.isEmpty()) return
    val canRestore = state.preferences.enabled &&
        state.systemAccess.notificationPermissionGranted &&
        !state.isSaving
    SectionCard(
        title = if (state.restorableShifts.size == 1) {
            "Notificación oculta"
        } else {
            "Notificaciones ocultas"
        },
        supportingText = if (canRestore) {
            "Podés volver a mostrar cada aviso mientras la guardia siga vigente."
        } else {
            "Activá las notificaciones y resolvé el permiso para volver a mostrarlas."
        },
    ) {
        state.restorableShifts.forEach { shift ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${shift.objectiveAbbreviationSnapshot} · " +
                        "${shift.localStartDate.format(HiddenNotificationDateFormatter)} · " +
                        "${shift.startTimeSnapshot.format(HiddenNotificationTimeFormatter)}–" +
                        shift.endTimeSnapshot.format(HiddenNotificationTimeFormatter),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(
                    onClick = { actions.restoreNotification(shift.id) },
                    enabled = canRestore,
                ) {
                    Text("Mostrar notificación nuevamente")
                }
            }
        }
        if (state.restorableShifts.size > 1) {
            OutlinedButton(
                onClick = actions.restoreAllNotifications,
                enabled = canRestore,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Mostrar todas nuevamente")
            }
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

private fun attentionLabel(value: NotificationAttentionMode): String = when (value) {
    NotificationAttentionMode.SOUND_AND_VIBRATION -> "Sonido y vibración"
    NotificationAttentionMode.VIBRATION_ONLY -> "Sólo vibración"
    NotificationAttentionMode.SILENT -> "Silencioso"
}

private fun attentionSummary(value: NotificationAttentionMode): String = when (value) {
    NotificationAttentionMode.SOUND_AND_VIBRATION -> "Suena y vibra al publicar el aviso."
    NotificationAttentionMode.VIBRATION_ONLY -> "Vibra sin reproducir sonido."
    NotificationAttentionMode.SILENT -> "Aparece sin sonido ni vibración."
}

private fun rhythmLabel(value: NotificationRhythm): String = when (value) {
    NotificationRhythm.ACCOMPANIED -> "Acompañado"
    NotificationRhythm.ESSENTIAL -> "Esencial"
    NotificationRhythm.DISCREET -> "Discreto"
    NotificationRhythm.CUSTOM -> "Personalizado"
}

private fun rhythmDescription(value: NotificationRhythm): String = when (value) {
    NotificationRhythm.ACCOMPANIED -> "12 h y 2 h antes · sonido y vibración · fija."
    NotificationRhythm.ESSENTIAL -> "12 h antes · sonido y vibración · fija."
    NotificationRhythm.DISCREET -> "12 h antes · silenciosa · descartable · privacidad reducida."
    NotificationRhythm.CUSTOM -> "Combinación ajustada por vos."
}

private fun reminderSummary(values: List<Long>): String = when (values.size) {
    0 -> "Sin avisos previos."
    1 -> {
        val minutes = values.single()
        if (minutes % 60L == 0L) {
            val hours = minutes / 60L
            "Un aviso $hours ${if (hours == 1L) "hora" else "horas"} antes."
        } else {
            "Un aviso $minutes minutos antes."
        }
    }
    else -> "${values.size} avisos configurados."
}

private val HiddenNotificationDateFormatter = DateTimeFormatter.ofPattern("dd/MM")
private val HiddenNotificationTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
