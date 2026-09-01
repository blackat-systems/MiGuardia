package com.blackatsystems.miguardia.ui.worksetup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.ui.components.EmptyState
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.PrimaryAction
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.components.SurfaceHeader
import com.blackatsystems.miguardia.ui.management.RgbColorPickerDialog
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import java.util.UUID
import java.util.Locale

data class WorkSetupActions(
    val retryLoad: () -> Unit = {},
    val selectSector: (WorkSector) -> Unit = {},
    val saveInitialSector: () -> Unit = {},
    val openOverview: () -> Unit = {},
    val openFirstWorkSet: () -> Unit = {},
    val openRecurringPlans: () -> Unit = {},
    val openExtraClasses: () -> Unit = {},
    val openHoursProgress: () -> Unit = {},
    val openAvailability: () -> Unit = {},
    val updatePlaceDraft: ((WorkPlaceDraft) -> WorkPlaceDraft) -> Unit = {},
    val updateTemplateDraft: ((WorkTemplateDraft) -> WorkTemplateDraft) -> Unit = {},
    val continueToTemplate: () -> Unit = {},
    val saveFirstWorkSet: () -> Unit = {},
    val openAdditionalTemplate: () -> Unit = {},
    val selectTemplatePlace: (UUID) -> Unit = {},
    val selectTemplateType: (UUID) -> Unit = {},
    val saveAdditionalTemplate: () -> Unit = {},
    val startAnotherPlace: () -> Unit = {},
    val saveAdditionalPlace: () -> Unit = {},
    val returnToCalendar: () -> Unit = {},
    val requestBack: () -> Unit = {},
    val dismissDiscard: () -> Unit = {},
    val confirmDiscard: () -> Unit = {},
    val clearMessage: () -> Unit = {},
) {
    companion object {
        fun from(viewModel: WorkSetupViewModel) = WorkSetupActions(
            retryLoad = viewModel::retryLoad,
            selectSector = viewModel::selectSector,
            saveInitialSector = viewModel::saveInitialSector,
            openOverview = viewModel::openOverview,
            openFirstWorkSet = viewModel::openFirstWorkSet,
            updatePlaceDraft = viewModel::updatePlaceDraft,
            updateTemplateDraft = viewModel::updateTemplateDraft,
            continueToTemplate = viewModel::continueToTemplate,
            saveFirstWorkSet = viewModel::saveFirstWorkSet,
            openAdditionalTemplate = viewModel::openAdditionalTemplate,
            selectTemplatePlace = viewModel::selectTemplatePlace,
            selectTemplateType = viewModel::selectTemplateType,
            saveAdditionalTemplate = viewModel::saveAdditionalTemplate,
            startAnotherPlace = viewModel::startAnotherPlace,
            saveAdditionalPlace = viewModel::saveAdditionalPlace,
            returnToCalendar = viewModel::returnToCalendar,
            requestBack = viewModel::requestBack,
            dismissDiscard = viewModel::dismissDiscard,
            confirmDiscard = viewModel::confirmDiscard,
            clearMessage = viewModel::clearMessage,
        )
    }
}

@Composable
fun WorkSetupStartupScreen(
    state: WorkSetupUiState,
    actions: WorkSetupActions,
    modifier: Modifier = Modifier,
    onRestoreBackup: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (state.rootState) {
            WorkSetupState.Loading -> WorkSetupLoading()
            WorkSetupState.LoadError -> WorkSetupLoadError(state, actions)
            WorkSetupState.FreshInstall -> WorkSectorSelection(state, actions, onRestoreBackup)
            else -> Unit
        }
    }
}

@Composable
fun WorkSetupSurfaceHost(
    state: WorkSetupUiState,
    actions: WorkSetupActions,
) {
    if (state.surface == WorkSetupSurface.NONE) return
    BackHandler(onBack = actions.requestBack)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (state.surface) {
            WorkSetupSurface.OVERVIEW -> WorkSetupOverview(state, actions)
            WorkSetupSurface.FIRST_WORK_SET -> FirstWorkSetScreen(state, actions)
            WorkSetupSurface.ADDITIONAL_PLACE -> AdditionalPlaceScreen(state, actions)
            WorkSetupSurface.ADDITIONAL_TEMPLATE -> AdditionalTemplateScreen(state, actions)
            WorkSetupSurface.COMPLETION -> WorkSetupCompletion(state, actions)
            WorkSetupSurface.NONE -> Unit
        }
    }
    if (state.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = actions.dismissDiscard,
            title = { Text("¿Descartar borrador?") },
            text = {
                Text("Todavía hay datos sin guardar. ¿Querés descartarlos y volver al Calendario?")
            },
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
fun V2FirstWorkSetGuide(
    onCreateFirstPlace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        title = "Todavía no cargaste ningún lugar de trabajo",
        message = "Creá tu primer lugar, sus reglas básicas y un horario reutilizable. No se cargará ninguna jornada todavía.",
        actionLabel = "Crear primer lugar",
        onAction = onCreateFirstPlace,
        modifier = modifier.testTag("work-setup-calendar-guide"),
    )
}

@Composable
private fun WorkSetupLoading() {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.testTag("work-setup-loading"))
        Spacer(Modifier.height(16.dp))
        Text("Preparando MiGuardia…", textAlign = TextAlign.Center)
    }
}

@Composable
private fun WorkSetupLoadError(state: WorkSetupUiState, actions: WorkSetupActions) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            title = "No pudimos abrir tu configuración laboral",
            message = state.errorMessage ?: "Tus datos no se interpretaron como una instalación nueva.",
            actionLabel = "Reintentar",
            onAction = actions.retryLoad,
            modifier = Modifier.testTag("work-setup-load-error"),
        )
    }
}

@Composable
private fun WorkSectorSelection(
    state: WorkSetupUiState,
    actions: WorkSetupActions,
    onRestoreBackup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("work-sector-selection"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeading(
            title = "¿En qué rubro trabajás?",
            supportingText = "Esta elección adapta palabras y ejemplos. Tus horarios y reglas se completan después.",
        )
        state.errorMessage?.let {
            PersistentMessage(message = it, onDismiss = actions.clearMessage)
        }
        state.sectorOptions.forEach { sector ->
            SectorCard(
                sector = sector,
                selected = state.selectedSector == sector,
                enabled = !state.isSavingSector,
                onClick = { actions.selectSector(sector) },
            )
        }
        PrimaryAction(
            label = "Continuar",
            onClick = actions.saveInitialSector,
            enabled = state.canContinueSector,
            working = state.isSavingSector,
            modifier = Modifier.testTag("work-sector-continue"),
        )
        OutlinedButton(
            onClick = onRestoreBackup,
            enabled = !state.isSavingSector,
            modifier = Modifier.fillMaxWidth().testTag("work-setup-restore-backup"),
        ) {
            Text("Restaurar una copia existente")
        }
        Text(
            "MiGuardia no define horas, nocturnidad ni disponibilidad por pertenecer a un rubro.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectorCard(
    sector: WorkSector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val selectionDescription = if (selected) "seleccionado" else "sin seleccionar"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("work-sector-${sector.name.lowercase(Locale.ROOT)}")
            .semantics {
                this.selected = selected
                role = Role.RadioButton
                contentDescription = "${sector.displayName}, $selectionDescription"
                if (!enabled) disabled()
            }
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.vigiliaColors.active.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.vigiliaColors.active else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sector.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Usaremos “${sector.suggestedVocabulary.placeLabel}” y “${sector.suggestedVocabulary.shiftLabel}” como ayuda visible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkSetupOverview(state: WorkSetupUiState, actions: WorkSetupActions) {
    val root = state.rootState
    val sector = when (root) {
        is WorkSetupState.V2NeedsFirstSet -> root.configurationRevision.value.sector
        is WorkSetupState.V2Ready -> root.configurationRevision.value.sector
        else -> null
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        SurfaceHeader("Mi forma de trabajar", "Cerrar", actions.requestBack)
        HorizontalDivider()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.errorMessage?.let {
                PersistentMessage(it, onDismiss = actions.clearMessage)
            }
            state.infoMessage?.let { message ->
                SectionCard(
                    title = "Agregar otro lugar",
                    supportingText = "Tu configuración actual permanece intacta.",
                ) {
                    Text(message)
                }
            }
            SectionCard(
                title = sector?.displayName ?: "Configuración laboral",
                supportingText = "Existe una sola configuración y sus cambios futuros conservarán el pasado.",
            ) {
                Text("Lugar de trabajo: ${sector?.suggestedVocabulary?.placeLabel ?: "Lugar"}")
                Text("Tipo de jornada: ${sector?.suggestedVocabulary?.shiftLabel ?: "Jornada"}")
            }
            if (root is WorkSetupState.V2NeedsFirstSet) {
                V2FirstWorkSetGuide(onCreateFirstPlace = actions.openFirstWorkSet)
            } else {
                SectionCard(
                    title = "Catálogo actual",
                    supportingText = "Los cambios afectan selecciones futuras; las jornadas históricas no se reescriben.",
                ) {
                    Text("Lugares activos: ${state.activePlaceOptions.size}")
                    Text("Tipos activos: ${state.activeTypeOptions.size}")
                    Text("Horarios activos: ${state.catalog?.workTemplates?.count { it.isActive } ?: 0}")
                    OutlinedButton(
                        onClick = actions.openAdditionalTemplate,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Agregar otro horario") }
                    OutlinedButton(
                        onClick = actions.startAnotherPlace,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Agregar otro lugar") }
                    OutlinedButton(
                        onClick = actions.openRecurringPlans,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("work-setup-recurring-plans"),
                    ) { Text("Planes recurrentes") }
                    OutlinedButton(
                        onClick = actions.openExtraClasses,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("work-setup-extra-classes"),
                    ) { Text("Clases de horas extra") }
                    OutlinedButton(
                        onClick = actions.openHoursProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("work-setup-hours-progress"),
                    ) { Text("Referencia y avance de horas") }
                    OutlinedButton(
                        onClick = actions.openAvailability,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("work-setup-availability"),
                    ) { Text("Guardias pasivas y disponibilidad") }
                }
            }
        }
    }
}

@Composable
private fun FirstWorkSetScreen(state: WorkSetupUiState, actions: WorkSetupActions) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        SurfaceHeader(
            title = if (state.step == WorkSetupStep.PLACE_AND_RULES) "Lugar y reglas" else "Tipo y horario",
            navigationLabel = if (state.step == WorkSetupStep.PLACE_AND_RULES) "Cerrar" else "Atrás",
            onNavigation = actions.requestBack,
        )
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("first-work-set-form"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (state.step == WorkSetupStep.PLACE_AND_RULES) "Paso 1 de 2" else "Paso 2 de 2",
                color = MaterialTheme.vigiliaColors.active,
                fontWeight = FontWeight.Bold,
            )
            state.errorMessage?.let {
                PersistentMessage(it, onDismiss = actions.clearMessage)
            }
            if (state.step == WorkSetupStep.PLACE_AND_RULES) {
                PlaceAndRulesForm(
                    state = state,
                    actions = actions,
                    primaryLabel = "Continuar al tipo y horario",
                    onPrimary = actions.continueToTemplate,
                )
            } else {
                TypeAndTemplateForm(state, actions, saveLabel = "Guardar lugar y horario")
            }
        }
    }
}

@Composable
private fun PlaceAndRulesForm(
    state: WorkSetupUiState,
    actions: WorkSetupActions,
    primaryLabel: String,
    onPrimary: () -> Unit,
) {
    val draft = state.placeDraft
    val sector = state.selectedSector
    SectionCard(
        title = sector?.suggestedVocabulary?.placeLabel ?: "Lugar de trabajo",
        supportingText = "Estos datos quedan sólo en tu teléfono.",
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { value -> actions.updatePlaceDraft { it.copy(name = value) } },
            modifier = Modifier.fillMaxWidth().testTag("work-place-name"),
            label = { Text("Nombre") },
            singleLine = true,
            enabled = !state.isSavingWorkSet,
        )
        OutlinedTextField(
            value = draft.abbreviation,
            onValueChange = { value ->
                actions.updatePlaceDraft {
                    it.copy(abbreviation = value.uppercase(Locale.ROOT).take(5))
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("work-place-abbreviation"),
            label = { Text("Nombre corto") },
            supportingText = { Text("Entre 3 y 5 caracteres, sin espacios.") },
            singleLine = true,
            enabled = !state.isSavingWorkSet,
        )
        OutlinedTextField(
            value = draft.address,
            onValueChange = { value -> actions.updatePlaceDraft { it.copy(address = value) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Dirección (opcional)") },
            singleLine = true,
            enabled = !state.isSavingWorkSet,
        )
        OutlinedTextField(
            value = draft.note,
            onValueChange = { value -> actions.updatePlaceDraft { it.copy(note = value) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nota personal y privada (opcional)") },
            minLines = 2,
            enabled = !state.isSavingWorkSet,
        )
    }
    SectionCard(
        title = "Reglas para clasificar horas",
        supportingText = "Sólo sirven para mostrar horas aparte. No calculan montos ni convierten tiempo en extras.",
    ) {
        CheckRow(
            label = "¿En este lugar contás horas nocturnas?",
            checked = draft.nightHoursEnabled,
            enabled = !state.isSavingWorkSet,
            onCheckedChange = { checked ->
                actions.updatePlaceDraft { it.copy(nightHoursEnabled = checked) }
            },
        )
        if (draft.nightHoursEnabled) {
            TimeFields(
                start = draft.nightStart,
                end = draft.nightEnd,
                startLabel = "Inicio nocturno",
                endLabel = "Final nocturno",
                onStartChange = { value -> actions.updatePlaceDraft { it.copy(nightStart = value) } },
                onEndChange = { value -> actions.updatePlaceDraft { it.copy(nightEnd = value) } },
                enabled = !state.isSavingWorkSet,
            )
        }
        CheckRow(
            label = "Distinguir sábados",
            checked = draft.classifySaturday,
            enabled = !state.isSavingWorkSet,
            onCheckedChange = { checked ->
                actions.updatePlaceDraft { it.copy(classifySaturday = checked) }
            },
        )
        CheckRow(
            label = "Distinguir domingos",
            checked = draft.classifySunday,
            enabled = !state.isSavingWorkSet,
            onCheckedChange = { checked ->
                actions.updatePlaceDraft { it.copy(classifySunday = checked) }
            },
        )
        if (draft.classifySaturday || draft.classifySunday) {
            CheckRow(
                label = "Mostrar esas horas aparte en el Resumen futuro",
                checked = draft.showWeekendSummary,
                enabled = !state.isSavingWorkSet,
                onCheckedChange = { checked ->
                    actions.updatePlaceDraft { it.copy(showWeekendSummary = checked) }
                },
            )
        }
        CheckRow(
            label = "Distinguir feriados",
            checked = draft.classifyHoliday,
            enabled = !state.isSavingWorkSet,
            onCheckedChange = { checked ->
                actions.updatePlaceDraft { it.copy(classifyHoliday = checked) }
            },
        )
        if (draft.classifyHoliday) {
            CheckRow(
                label = "Mostrar feriados aparte en el Resumen futuro",
                checked = draft.showHolidaySummary,
                enabled = !state.isSavingWorkSet,
                onCheckedChange = { checked ->
                    actions.updatePlaceDraft { it.copy(showHolidaySummary = checked) }
                },
            )
        }
    }
    PrimaryAction(
        label = primaryLabel,
        onClick = onPrimary,
        enabled = validatePlaceDraft(draft).isValid,
        working = state.isSavingWorkSet,
        modifier = Modifier.testTag("work-place-continue"),
    )
}

@Composable
private fun AdditionalPlaceScreen(state: WorkSetupUiState, actions: WorkSetupActions) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        SurfaceHeader("Agregar otro lugar", "Cerrar", actions.requestBack)
        HorizontalDivider()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.errorMessage?.let { PersistentMessage(it, onDismiss = actions.clearMessage) }
            PlaceAndRulesForm(
                state = state,
                actions = actions,
                primaryLabel = "Guardar lugar",
                onPrimary = actions.saveAdditionalPlace,
            )
            Text(
                "Después podés agregarle uno o más horarios reutilizando un tipo de trabajo existente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TypeAndTemplateForm(
    state: WorkSetupUiState,
    actions: WorkSetupActions,
    saveLabel: String,
) {
    val draft = state.templateDraft
    var choosingColor by remember { mutableStateOf(false) }
    SectionCard(
        title = "Trabajo habitual",
        supportingText = "El nombre es editable. Siempre contará como trabajo normal.",
    ) {
        OutlinedTextField(
            value = draft.typeName,
            onValueChange = { value -> actions.updateTemplateDraft { it.copy(typeName = value) } },
            modifier = Modifier.fillMaxWidth().testTag("work-type-name"),
            label = { Text("Tipo de trabajo") },
            singleLine = true,
            enabled = !state.isSavingWorkSet,
        )
        Text(
            "Las horas extras, una extensión del turno, la disponibilidad y las situaciones especiales se podrán registrar por separado cuando esas funciones estén disponibles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SectionCard(
        title = "Primer horario",
        supportingText = "Guardamos el inicio y el final exactos; no usamos categorías genéricas de día o noche.",
    ) {
        TimeFields(
            start = draft.startTime,
            end = draft.endTime,
            startLabel = "Hora de inicio",
            endLabel = "Hora de finalización",
            onStartChange = { value -> actions.updateTemplateDraft { it.copy(startTime = value) } },
            onEndChange = { value -> actions.updateTemplateDraft { it.copy(endTime = value) } },
            enabled = !state.isSavingWorkSet,
        )
        templateTimingExplanation(draft)?.let {
            Text(it, fontWeight = FontWeight.SemiBold, color = MaterialTheme.vigiliaColors.info)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        draft.colorArgb?.let { color -> Color(color) }
                            ?: MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
            )
            OutlinedButton(
                onClick = { choosingColor = true },
                enabled = !state.isSavingWorkSet,
                modifier = Modifier.weight(1f).testTag("work-template-color"),
            ) {
                Text(if (draft.colorArgb == null) "Elegir color" else "Cambiar color")
            }
        }
    }
    PrimaryAction(
        label = saveLabel,
        onClick = actions.saveFirstWorkSet,
        enabled = validateTemplateDraft(draft, requireTypeName = true).isValid,
        working = state.isSavingWorkSet,
        modifier = Modifier.testTag("work-set-save"),
    )
    if (choosingColor) {
        RgbColorPickerDialog(
            initialColor = draft.colorArgb ?: DEFAULT_TEMPLATE_COLOR,
            onDismiss = { choosingColor = false },
            onConfirm = { selected ->
                actions.updateTemplateDraft { it.copy(colorArgb = selected) }
                choosingColor = false
            },
        )
    }
}

@Composable
private fun AdditionalTemplateScreen(state: WorkSetupUiState, actions: WorkSetupActions) {
    var choosingColor by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        SurfaceHeader("Agregar otro horario", "Cerrar", actions.requestBack)
        HorizontalDivider()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.errorMessage?.let { PersistentMessage(it, onDismiss = actions.clearMessage) }
            SectionCard("Lugar") {
                state.activePlaceOptions.forEach { option ->
                SelectionRow(
                        title = "${option.label} (${option.abbreviation})",
                        selected = state.selectedTemplatePlaceId == option.id,
                        onClick = { actions.selectTemplatePlace(option.id) },
                        enabled = !state.isSavingTemplate,
                    )
                }
            }
            SectionCard("Tipo de trabajo") {
                state.activeTypeOptions.forEach { option ->
                    SelectionRow(
                        title = option.label,
                        selected = state.selectedTemplateTypeId == option.id,
                        onClick = { actions.selectTemplateType(option.id) },
                        enabled = !state.isSavingTemplate,
                    )
                }
            }
            SectionCard(
                title = "Horario exacto",
                supportingText = "Este horario reutiliza el lugar y el tipo elegidos.",
            ) {
                TimeFields(
                    start = state.templateDraft.startTime,
                    end = state.templateDraft.endTime,
                    startLabel = "Hora de inicio",
                    endLabel = "Hora de finalización",
                    onStartChange = { value -> actions.updateTemplateDraft { it.copy(startTime = value) } },
                    onEndChange = { value -> actions.updateTemplateDraft { it.copy(endTime = value) } },
                    enabled = !state.isSavingTemplate,
                )
                templateTimingExplanation(state.templateDraft)?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(32.dp).background(
                            state.templateDraft.colorArgb?.let { color -> Color(color) }
                                ?: MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        ),
                    )
                    OutlinedButton(
                        onClick = { choosingColor = true },
                        enabled = !state.isSavingTemplate,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (state.templateDraft.colorArgb == null) "Elegir color" else "Cambiar color") }
                }
            }
            PrimaryAction(
                label = "Guardar horario",
                onClick = actions.saveAdditionalTemplate,
                enabled = state.selectedTemplatePlaceId != null &&
                    state.selectedTemplateTypeId != null &&
                    validateTemplateDraft(state.templateDraft, requireTypeName = false).isValid,
                working = state.isSavingTemplate,
            )
        }
    }
    if (choosingColor) {
        RgbColorPickerDialog(
            initialColor = state.templateDraft.colorArgb ?: DEFAULT_TEMPLATE_COLOR,
            onDismiss = { choosingColor = false },
            onConfirm = { selected ->
                actions.updateTemplateDraft { it.copy(colorArgb = selected) }
                choosingColor = false
            },
        )
    }
}

@Composable
private fun WorkSetupCompletion(state: WorkSetupUiState, actions: WorkSetupActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("work-setup-completion"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Listo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            state.infoMessage ?: "Tu configuración laboral quedó guardada.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = actions.returnToCalendar, modifier = Modifier.fillMaxWidth()) {
            Text("Volver al Calendario")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = actions.openAdditionalTemplate, modifier = Modifier.fillMaxWidth()) {
            Text("Agregar otro horario")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = actions.startAnotherPlace, modifier = Modifier.fillMaxWidth()) {
            Text("Agregar otro lugar")
        }
    }
}

@Composable
private fun TimeFields(
    start: String,
    end: String,
    startLabel: String,
    endLabel: String,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 360.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkTimeField(start, startLabel, onStartChange, enabled, "work-time-start", Modifier.fillMaxWidth())
                WorkTimeField(end, endLabel, onEndChange, enabled, "work-time-end", Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkTimeField(start, startLabel, onStartChange, enabled, "work-time-start", Modifier.weight(1f))
                WorkTimeField(end, endLabel, onEndChange, enabled, "work-time-end", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WorkTimeField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    testTag: String,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(5)) },
        modifier = modifier.testTag(testTag),
        label = { Text(label) },
        placeholder = { Text("HH:mm") },
        singleLine = true,
        enabled = enabled,
    )
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Checkbox
                if (!enabled) disabled()
            }
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SelectionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
                role = Role.RadioButton
                if (!enabled) disabled()
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(
                    if (selected) MaterialTheme.vigiliaColors.active else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
        )
        Text(title, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun templateTimingExplanation(draft: WorkTemplateDraft): String? {
    val start = parseWorkTimeOrNull(draft.startTime) ?: return null
    val end = parseWorkTimeOrNull(draft.endTime) ?: return null
    return when {
        start == end -> "Este horario dura 24 horas."
        end.isBefore(start) -> "Termina al día siguiente."
        else -> "Finaliza el mismo día."
    }
}

private val DEFAULT_TEMPLATE_COLOR: Int = 0xFF5C4DFF.toInt()
