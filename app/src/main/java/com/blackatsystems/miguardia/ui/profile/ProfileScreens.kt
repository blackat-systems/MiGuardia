package com.blackatsystems.miguardia.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.profile.GUARD_PROFESSION
import com.blackatsystems.miguardia.ui.components.EmptyState
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.PrimaryAction
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.components.SurfaceHeader
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import java.time.format.DateTimeFormatter

data class ProfileActions(
    val open: () -> Unit = {},
    val updateDisplayName: (String) -> Unit = {},
    val updateCompany: (String) -> Unit = {},
    val save: () -> Unit = {},
    val requestBack: () -> Unit = {},
    val dismissDiscard: () -> Unit = {},
    val confirmDiscard: () -> Unit = {},
    val clearMessage: () -> Unit = {},
    val retry: () -> Unit = {},
    val openObjectives: () -> Unit = {},
) {
    companion object {
        fun from(viewModel: ProfileViewModel) = ProfileActions(
            open = viewModel::open,
            updateDisplayName = viewModel::updateDisplayName,
            updateCompany = viewModel::updateCompany,
            save = viewModel::save,
            requestBack = viewModel::requestBack,
            dismissDiscard = viewModel::dismissDiscard,
            confirmDiscard = viewModel::confirmDiscard,
            clearMessage = viewModel::clearMessage,
            retry = viewModel::retry,
        )
    }
}

@Composable
fun ProfileSurfaceHost(
    state: ProfileUiState,
    actions: ProfileActions,
) {
    if (state.surface == ProfileSurface.NONE) return
    BackHandler(onBack = actions.requestBack)
    TransientConfirmation(state.infoMessage, actions.clearMessage) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                SurfaceHeader(
                    title = "Perfil laboral",
                    navigationLabel = "Cerrar",
                    onNavigation = actions.requestBack,
                )
                HorizontalDivider()
                state.errorMessage?.let { message ->
                    PersistentMessage(
                        message = message,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        onDismiss = actions.clearMessage,
                        onRetry = actions.retry.takeIf { state.canRetryLoad },
                    )
                }
                if (state.isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text("Cargando perfil…", Modifier.padding(start = 12.dp))
                    }
                } else {
                    ProfileEditor(state, actions)
                }
            }
        }
    }

    if (state.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = actions.dismissDiscard,
            title = { Text("Descartar cambios") },
            text = { Text("Los cambios del perfil todavía no fueron guardados. ¿Querés descartarlos?") },
            confirmButton = {
                TextButton(onClick = actions.confirmDiscard) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = actions.dismissDiscard) { Text("Seguir editando") }
            },
        )
    }
}

@Composable
private fun ProfileEditor(state: ProfileUiState, actions: ProfileActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Este perfil es local. No crea una cuenta ni se envía fuera de MiGuardia.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard(
            title = "Información laboral",
            supportingText = "La empresa configurada será la fuente de los informes futuros.",
        ) {
            OutlinedTextField(
                value = state.draft.displayName,
                onValueChange = actions.updateDisplayName,
                modifier = Modifier.fillMaxWidth().testTag("profile-display-name"),
                label = { Text("Nombre o apodo (opcional)") },
                singleLine = true,
                enabled = !state.isSaving,
            )
            OutlinedTextField(
                value = GUARD_PROFESSION,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth().testTag("profile-profession"),
                label = { Text("Profesión") },
                readOnly = true,
                singleLine = true,
            )
            OutlinedTextField(
                value = state.draft.company,
                onValueChange = actions.updateCompany,
                modifier = Modifier.fillMaxWidth().testTag("profile-company"),
                label = { Text("Empresa") },
                supportingText = if (state.draft.company.isBlank()) {
                    { Text("La empresa es obligatoria.") }
                } else {
                    null
                },
                isError = state.draft.company.isBlank(),
                singleLine = true,
                enabled = !state.isSaving,
            )
            PrimaryAction(
                label = "Guardar perfil",
                onClick = actions.save,
                enabled = state.draft.company.isNotBlank(),
                working = state.isSaving,
            )
        }
        SectionCard(
            title = "Objetivos y horarios activos",
            supportingText = "Se leen desde tus plantillas actuales; Perfil no guarda copias.",
        ) {
            if (state.activeObjectives.isEmpty()) {
                EmptyState(
                    title = "Todavía no hay objetivos activos",
                    message = "Creá un objetivo y sus horarios para usarlos en las cargas.",
                    actionLabel = "Ir a Objetivos y horarios",
                    onAction = actions.openObjectives,
                )
            } else {
                state.activeObjectives.forEachIndexed { index, projection ->
                    if (index > 0) HorizontalDivider()
                    Text(
                        "${projection.objective.fullName} (${projection.objective.abbreviation})",
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (projection.schedules.isEmpty()) {
                        Text(
                            "Sin horarios activos.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        projection.schedules.forEach { schedule ->
                            Text("${schedule.startTime.format(TimeFormatter)}–${schedule.endTime.format(TimeFormatter)}")
                        }
                    }
                }
                OutlinedButton(onClick = actions.openObjectives, modifier = Modifier.fillMaxWidth()) {
                    Text("Administrar Objetivos y horarios")
                }
            }
        }
        SectionCard(
            title = "Puesto en cada guardia",
            supportingText = "El puesto puede cambiar entre cargas y no forma parte del perfil global.",
        ) {
            Text("Podés escribirlo al crear o editar cada guardia, y también dejarlo vacío.")
        }
    }
}

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
