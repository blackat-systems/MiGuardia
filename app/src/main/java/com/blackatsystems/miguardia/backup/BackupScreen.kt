package com.blackatsystems.miguardia.backup

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.backup.BackupConflict
import com.blackatsystems.miguardia.core.domain.backup.BackupConflictResolution
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupContract
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoMode
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.SectionCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BackupSurfaceHost(
    state: BackupUiState,
    actions: BackupActions,
) {
    if (!state.isOpen) return
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MiGuardiaBackupContract.MIME_TYPE),
        actions.destinationSelected,
    )
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
        actions.sourceSelected,
    )
    LaunchedEffect(state.stage, state.suggestedName) {
        if (state.stage == BackupStage.WAITING_FOR_CREATE_DESTINATION) {
            try {
                createDocument.launch(state.suggestedName ?: "MiGuardia_copia${MiGuardiaBackupContract.FILE_EXTENSION}")
            } catch (_: ActivityNotFoundException) {
                actions.destinationSelected(null)
            }
        }
    }
    LaunchedEffect(state.stage) {
        if (state.stage == BackupStage.WAITING_FOR_OPEN_SOURCE) {
            try {
                openDocument.launch(arrayOf(MiGuardiaBackupContract.MIME_TYPE, "application/octet-stream"))
            } catch (_: ActivityNotFoundException) {
                actions.sourceSelected(null)
            }
        }
    }
    BackHandler(enabled = state.canCancel) { actions.cancelOperation() }
    BackHandler(enabled = state.isBusy && !state.canCancel) {
        // Applying and recovery are atomic phases: leaving the surface must not cancel them.
    }
    BackHandler(enabled = !state.isBusy && !state.recoveryRequired) { actions.close() }
    Surface(
        modifier = Modifier.fillMaxSize().testTag("backup-surface"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Copias y restauración",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = if (state.canCancel) actions.cancelOperation else actions.close,
                    enabled = state.canCancel || (!state.isBusy && !state.recoveryRequired),
                ) { Text(if (state.canCancel) "Cancelar" else "Volver") }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Creá un archivo local completo o revisá una copia antes de cambiar tus datos.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                state.errorMessage?.let { message ->
                    PersistentMessage(
                        message = message,
                        modifier = Modifier.testTag("backup-error"),
                        onRetry = actions.retryRecovery.takeIf {
                            state.stage == BackupStage.ERROR && state.recoveryRequired
                        },
                        onDismiss = actions.clearMessage.takeIf { !state.recoveryRequired },
                    )
                }
                state.infoMessage?.let { message ->
                    SectionCard("Listo", modifier = Modifier.testTag("backup-info")) {
                        Text(message)
                        if (state.stage != BackupStage.SUCCESS) {
                            TextButton(onClick = actions.clearMessage) { Text("Cerrar mensaje") }
                        }
                    }
                }
                when (state.stage) {
                    BackupStage.IDLE -> BackupHome(state, actions)
                    BackupStage.CAPTURING -> BusyBackup(
                        "Preparando una instantánea coherente…",
                        "backup-capturing",
                        actions.cancelOperation,
                    )
                    BackupStage.WAITING_FOR_CREATE_DESTINATION -> BusyBackup("Abriendo el selector para guardar…", "backup-waiting-create")
                    BackupStage.COPYING_OUT -> BusyBackup(
                        "Copiando al destino elegido…",
                        "backup-copying-out",
                        actions.cancelOperation,
                    )
                    BackupStage.CANCELLING -> BusyBackup(
                        "Cancelando y retirando los archivos incompletos…",
                        "backup-cancelling",
                    )
                    BackupStage.WAITING_FOR_OPEN_SOURCE -> BusyBackup("Abriendo el selector de copias…", "backup-waiting-open")
                    BackupStage.READING -> BusyBackup(
                        "Leyendo la copia en un área privada…",
                        "backup-reading",
                        actions.cancelOperation,
                    )
                    BackupStage.PASSWORD_REQUIRED -> PasswordRequired(state, actions)
                    BackupStage.VALIDATING -> BusyBackup(
                        "Verificando cifrado, integridad y relaciones…",
                        "backup-validating",
                        actions.cancelOperation,
                    )
                    BackupStage.PREVIEW -> BackupPreviewContent(state, actions)
                    BackupStage.RESOLVING_CONFLICTS -> ConflictResolver(state, actions)
                    BackupStage.READY_TO_APPLY -> ReadyToApply(state, actions)
                    BackupStage.APPLYING -> BusyBackup("Aplicando con recuperación automática…", "backup-applying")
                    BackupStage.RECOVERING -> BusyBackup("Recuperando el último estado íntegro…", "backup-recovering")
                    BackupStage.SUCCESS -> BackupSuccess(state, actions)
                    BackupStage.ERROR -> Unit
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun BackupHome(state: BackupUiState, actions: BackupActions) {
    SectionCard("Crear una copia completa", modifier = Modifier.testTag("backup-create-card")) {
        SettingToggle(
            title = "Incluir fotos del cronograma",
            detail = "Suma los archivos privados y sus metadatos verificados.",
            checked = state.includePhotos,
            onChecked = actions.setIncludePhotos,
            tag = "backup-include-photos",
        )
        SettingToggle(
            title = "Proteger con contraseña",
            detail = "Recomendado. La contraseña no se guarda en MiGuardia.",
            checked = state.encryptionEnabled,
            onChecked = actions.setEncryptionEnabled,
            tag = "backup-encryption",
        )
        if (state.encryptionEnabled) {
            val passwordTransformation = if (state.passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            }
            OutlinedTextField(
                value = state.password,
                onValueChange = actions.setPassword,
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = passwordTransformation,
                modifier = Modifier.fillMaxWidth().testTag("backup-password"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text("Entre 8 y 256 caracteres.") },
                isError = state.passwordError != null,
            )
            OutlinedTextField(
                value = state.passwordConfirmation,
                onValueChange = actions.setPasswordConfirmation,
                label = { Text("Repetir contraseña") },
                singleLine = true,
                visualTransformation = passwordTransformation,
                modifier = Modifier.fillMaxWidth().testTag("backup-password-confirmation"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = state.passwordError != null,
                supportingText = state.passwordError?.let { message -> ({ Text(message) }) },
            )
            TextButton(
                onClick = { actions.setPasswordVisible(!state.passwordVisible) },
                modifier = Modifier.testTag("backup-password-visibility"),
            ) {
                Text(if (state.passwordVisible) "Ocultar contraseñas" else "Mostrar contraseñas")
            }
            Text(
                "Guardá esta contraseña en un lugar seguro: si la olvidás, nadie puede recuperar el contenido de la copia.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
        } else {
            SettingToggle(
                title = "Entiendo el riesgo de crearla sin contraseña",
                detail = "Cualquiera con acceso al archivo podrá leer su contenido.",
                checked = state.unencryptedWarningAccepted,
                onChecked = actions.setUnencryptedWarningAccepted,
                tag = "backup-unencrypted-confirmation",
            )
        }
        Button(
            onClick = actions.create,
            enabled = state.canCreate,
            modifier = Modifier.fillMaxWidth().testTag("backup-create"),
        ) {
            Text("Crear copia local")
        }
    }
    SectionCard("Restaurar desde un archivo", modifier = Modifier.testTag("backup-restore-card")) {
        Text("MiGuardia validará todo y mostrará el impacto antes de escribir.")
        OutlinedButton(
            onClick = actions.chooseSource,
            modifier = Modifier.fillMaxWidth().testTag("backup-choose-source"),
        ) {
            Text("Elegir copia")
        }
    }
}

@Composable
private fun PasswordRequired(state: BackupUiState, actions: BackupActions) {
    SectionCard("Copia protegida", modifier = Modifier.testTag("backup-password-required")) {
        Text("Ingresá la contraseña usada al crearla. MiGuardia verificará la autenticación antes de leer el contenido.")
        OutlinedTextField(
            value = state.password,
            onValueChange = actions.setPassword,
            label = { Text("Contraseña de la copia") },
            singleLine = true,
            visualTransformation = if (state.passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            isError = state.passwordError != null,
            supportingText = state.passwordError?.let { message -> ({ Text(message) }) },
            modifier = Modifier.fillMaxWidth().testTag("backup-unlock-password"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        TextButton(
            onClick = { actions.setPasswordVisible(!state.passwordVisible) },
            modifier = Modifier.testTag("backup-unlock-password-visibility"),
        ) {
            Text(if (state.passwordVisible) "Ocultar contraseña" else "Mostrar contraseña")
        }
        Button(
            onClick = actions.unlockSource,
            enabled = state.password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("backup-unlock"),
        ) {
            Text("Verificar copia")
        }
    }
}

@Composable
private fun BackupPreviewContent(state: BackupUiState, actions: BackupActions) {
    val preview = state.preview ?: return
    val created = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-AR"))
        .format(Instant.ofEpochMilli(preview.manifest.createdAtEpochMillis).atZone(ZoneId.of(preview.manifest.zoneId)))
    SectionCard("Vista previa verificada", modifier = Modifier.testTag("backup-preview")) {
        Text("Creada el $created")
        Text(
            "Formato de copia V${state.formatVersion ?: "?"} · datos internos Room V${preview.manifest.roomVersion}",
        )
        val sectorLabels = preview.historicalSectors.map { stored ->
            WorkSector.entries.firstOrNull { it.name == stored }?.displayName ?: stored
        }
        Text(
            if (sectorLabels.isEmpty()) "Sin rubro laboral configurado" else
                "Rubros históricos: ${sectorLabels.joinToString()}",
        )
        Text(
            "Actual: ${preview.currentCounts.values.sum()} registros · " +
                "Copia: ${preview.incomingCounts.values.sum()} registros lógicos en 27 tablas",
        )
        Text(
            if (preview.manifest.photoMode == BackupPhotoMode.OMITTED) {
                "La copia fue creada sin fotos"
            } else {
                "${preview.photosInBackup} fotos incluidas"
            },
        )
        Text("${preview.newRecords} nuevos · ${preview.identicalRecords} idénticos · ${preview.conflicts.size} conflictos")
        if (!preview.timelineCompatible) {
            Text(
                "La línea temporal es distinta: combinar está bloqueado.",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
        preview.mergeBlockedReason?.let { reason ->
            Text(
                reason,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("backup-merge-blocked-reason"),
            )
        }
        if (preview.photosMissingFromBackup > 0) {
            Text("La copia no incluye ${preview.photosMissingFromBackup} fotos que hoy existen en este dispositivo.")
        }
    }
    Button(
        onClick = actions.chooseMerge,
        enabled = preview.timelineCompatible && preview.mergeBlockedReason == null,
        modifier = Modifier.fillMaxWidth().testTag("backup-choose-merge"),
    ) {
        Text("Combinar con mis datos")
    }
    OutlinedButton(
        onClick = actions.chooseReplace,
        modifier = Modifier.fillMaxWidth().testTag("backup-choose-replace"),
    ) {
        Text("Reemplazar todo")
    }
}

@Composable
private fun ConflictResolver(state: BackupUiState, actions: BackupActions) {
    Text(
        "Revisá cada diferencia. MiGuardia no escribirá hasta confirmar todas las resoluciones.",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
    )
    state.conflicts.forEachIndexed { index, conflict ->
        ConflictCard(index + 1, conflict, state.resolutions[conflict.id], actions)
    }
    Button(
        onClick = actions.confirmResolutions,
        enabled = state.allConflictsResolved,
        modifier = Modifier.fillMaxWidth().testTag("backup-confirm-resolutions"),
    ) {
        Text("Confirmar resoluciones")
    }
}

@Composable
private fun ConflictCard(
    number: Int,
    conflict: BackupConflict,
    selected: BackupConflictResolution?,
    actions: BackupActions,
) {
    SectionCard("Conflicto $number · ${conflict.table}", modifier = Modifier.testTag("backup-conflict-$number")) {
        Text(conflict.summary)
        conflict.currentDescription?.let { Text("Actual: $it") }
        conflict.incomingDescription?.let { Text("Copia: $it") }
        Column(Modifier.selectableGroup()) {
            ResolutionRow(
                "Conservar actual",
                "Opción segura y predeterminada.",
                selected == BackupConflictResolution.KEEP_CURRENT,
                { actions.resolve(conflict.id, BackupConflictResolution.KEEP_CURRENT) },
            )
            ResolutionRow(
                "Usar copia",
                "Reemplaza este dato por el contenido validado del archivo.",
                selected == BackupConflictResolution.USE_BACKUP,
                { actions.resolve(conflict.id, BackupConflictResolution.USE_BACKUP) },
            )
            if (conflict.keepBothAllowed) {
                ResolutionRow(
                    "Conservar ambos",
                    "Sólo disponible para identidades distintas y compatibles.",
                    selected == BackupConflictResolution.KEEP_BOTH,
                    { actions.resolve(conflict.id, BackupConflictResolution.KEEP_BOTH) },
                )
            }
        }
    }
}

@Composable
private fun ResolutionRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReadyToApply(state: BackupUiState, actions: BackupActions) {
    val preview = state.preview ?: return
    when (state.restoreChoice) {
        RestoreChoice.MERGE -> SectionCard("Combinar sin sobreescritura silenciosa") {
            Text("Se agregarán los datos nuevos y se aplicarán únicamente las resoluciones que acabás de revisar.")
            Text("${preview.newRecords} nuevos · ${preview.identicalRecords} idénticos")
            if (state.resolutions.isNotEmpty()) {
                Text(
                    "Decisiones: " +
                        "${state.resolutions.values.count { it == BackupConflictResolution.KEEP_CURRENT }} conservar actual · " +
                        "${state.resolutions.values.count { it == BackupConflictResolution.USE_BACKUP }} usar copia · " +
                        "${state.resolutions.values.count { it == BackupConflictResolution.KEEP_BOTH }} conservar ambos",
                )
            }
            Text("El estado actual queda protegido por un journal recuperable hasta verificar el resultado.")
            Button(
                onClick = actions.apply,
                modifier = Modifier.fillMaxWidth().testTag("backup-apply-merge"),
            ) {
                Text("Combinar con mis datos")
            }
        }
        RestoreChoice.REPLACE_ALL -> SectionCard("Segunda confirmación obligatoria") {
            val incomingTotal = preview.incomingCounts.values.sum()
            Text(
                "Datos actuales que desaparecerán o serán reemplazados: " +
                    "${recordLabel(preview.currentRecordsRemovedOrReplaced)} y " +
                    preferenceLabel(preview.currentPreferencesRemovedOrReplaced) + ". " +
                    "La copia recuperará ${recordLabel(incomingTotal)} y " +
                    preferenceLabel(preview.incomingPreferenceCount) + ".",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            if (preview.photosMissingFromBackup > 0) {
                Text(
                    if (preview.manifest.photoMode == BackupPhotoMode.OMITTED) {
                        "También desaparecerán ${preview.photosMissingFromBackup} fotos: la copia fue creada sin fotos."
                    } else {
                        "También desaparecerán ${preview.photosMissingFromBackup} fotos que no están en la copia."
                    },
                )
            }
            Text("Para continuar, escribí exactamente: Reemplazar todo")
            OutlinedTextField(
                value = state.replaceConfirmation,
                onValueChange = actions.setReplaceConfirmation,
                label = { Text("Confirmación") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("backup-replace-confirmation"),
            )
            Button(
                onClick = actions.apply,
                enabled = state.replaceConfirmation == LocalBackupCoordinator.REPLACE_CONFIRMATION,
                modifier = Modifier.fillMaxWidth().testTag("backup-apply-replace"),
            ) {
                Text("Reemplazar todo")
            }
        }
        null -> Unit
    }
}

@Composable
private fun BackupSuccess(state: BackupUiState, actions: BackupActions) {
    SectionCard("Operación terminada", modifier = Modifier.testTag("backup-success")) {
        Text(state.infoMessage ?: "La operación terminó correctamente.")
        if (state.successSequence > 0) {
            Text("Avisos y Widgets fueron reconciliados sin generar una notificación de prueba.")
        }
        Button(
            onClick = actions.finishSuccess,
            modifier = Modifier.fillMaxWidth().testTag("backup-finish"),
        ) {
            Text("Volver al Calendario")
        }
    }
}

@Composable
private fun BusyBackup(message: String, tag: String, onCancel: (() -> Unit)? = null) {
    SectionCard("Procesando", modifier = Modifier.testTag(tag)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(message, modifier = Modifier.weight(1f))
        }
        Text("No cierres MiGuardia durante este paso.", style = MaterialTheme.typography.bodySmall)
        if (onCancel != null) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().testTag("$tag-cancel"),
            ) {
                Text("Cancelar sin cambiar mis datos")
            }
        }
    }
}

private fun recordLabel(count: Int): String = "$count ${if (count == 1) "registro" else "registros"}"

private fun preferenceLabel(count: Int): String = "$count ${if (count == 1) "ajuste" else "ajustes"}"

@Composable
private fun SettingToggle(
    title: String,
    detail: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    tag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Switch, onValueChange = onChecked)
            .padding(vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
