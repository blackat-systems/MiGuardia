package com.blackatsystems.miguardia.ui.management

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.ui.worksetup.WorkCreationKind
import com.blackatsystems.miguardia.ui.photos.NoteDraftAttachment
import com.blackatsystems.miguardia.ui.photos.NoteImageSelection
import com.blackatsystems.miguardia.ui.photos.PrivateNoteImageDialog
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import java.time.format.DateTimeFormatter
import java.text.Collator
import java.util.Locale

@Composable
internal fun UnifiedTemplateSelectionStep(
    state: V2ManualShiftLoadUiState,
    actions: V2ManualShiftLoadActions,
    onModifyCalendarSelection: () -> Unit,
) {
    var catalogOpen by rememberSaveable { mutableStateOf(false) }
    var placeId by rememberSaveable { mutableStateOf<String?>(null) }
    var datesExpanded by rememberSaveable { mutableStateOf(false) }
    var noteExpanded by rememberSaveable { mutableStateOf(false) }
    var positionExpanded by rememberSaveable { mutableStateOf(false) }
    var viewingImage by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val draftImages = remember(context) { SchedulePhotoFileStore(context, draftOnly = true) }
    val enabled = state.canCreate && state.batchPreview == null
    val singular = state.selectedDates.size == 1
    val locale = Locale.forLanguageTag("es-AR")
    val dates = state.selectedDates.sorted().map { it.format(DateTimeFormatter.ofPattern("dd/MM/yy", locale)) }
    val fullDates = state.selectedDates.sorted().map {
        it.format(DateTimeFormatter.ofPattern("EEEE dd/MM/yy", locale)).replaceFirstChar { c -> c.titlecase(locale) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(NoteImageSelection.MAX_IMAGES)) {
        actions.importNoteImages(it)
    }
    Text("Cargando jornada para los días:", style = MaterialTheme.typography.titleLarge)
    Column(Modifier.fillMaxWidth()) {
            Text(if (datesExpanded && dates.size > 3) fullDates.joinToString("\n") else dates.take(3).joinToString(" - "),
                modifier = Modifier.testTag("v2-manual-exact-dates").semantics {
                    contentDescription = fullDates.joinToString(", ")
                    stateDescription = if (datesExpanded) "Fechas desplegadas" else "Fechas contraídas"
                })
            if (dates.size > 3) TextButton(onClick = { datesExpanded = !datesExpanded },
                modifier = Modifier.testTag("manual-expand-dates")) {
                Text(if (datesExpanded) "Mostrar menos" else "Mostrar todos")
            }
    }
    OutlinedButton(onClick = { catalogOpen = !catalogOpen }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("manual-institutions").semantics {
            stateDescription = if (catalogOpen) "Instituciones desplegadas" else "Instituciones contraídas"
        }) {
        Text(if (catalogOpen) "Ocultar instituciones o servicios" else "Mostrar instituciones o servicios")
    }
    if (catalogOpen) OutlinedCard(Modifier.fillMaxWidth().testTag("manual-inline-catalog")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { actions.beginCreation(WorkCreationKind.PLACE_AND_TEMPLATE) },
                enabled = enabled, modifier = Modifier.testTag("v2-manual-create-place")) { Text("Crear institución o servicio") }
            HorizontalDivider()
            val places = state.places.ifEmpty { state.templateOptions.distinctBy { it.workPlace.id }.map { ManualWorkPlaceOption(it.objective, it.workPlace) } }
            val collator = remember { Collator.getInstance(locale).apply { strength = Collator.PRIMARY } }
            val ordered = places.distinctBy { it.place.id }.sortedWith { a, b ->
                collator.compare(a.objective.fullName, b.objective.fullName).takeIf { it != 0 }
                    ?: a.place.id.compareTo(b.place.id)
            }
            if (ordered.isEmpty()) Text("Todavía no hay instituciones o servicios.")
            ordered.forEach { place ->
                val options = state.templateOptions.filter { it.workPlace.id == place.place.id }
                val expanded = placeId == place.place.id.toString()
                OutlinedButton(onClick = { placeId = place.place.id.toString() }, enabled = enabled,
                    border = BorderStroke(1.dp, if (expanded) MaterialTheme.vigiliaColors.active else MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (expanded) MaterialTheme.vigiliaColors.selection else Color.Transparent,
                        contentColor = if (expanded) MaterialTheme.vigiliaColors.onSelection else MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("manual-place-${place.place.id}").semantics {
                        stateDescription = if (expanded) "Horarios desplegados" else "Horarios contraídos"
                    }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.firstOrNull()?.template?.colorArgb?.let {
                            Box(Modifier.size(12.dp).background(Color(it), CircleShape))
                        }
                        Text("${place.objective.fullName} (${place.objective.abbreviation})")
                    }
                }
                if (placeId == place.place.id.toString()) {
                    options.forEach { option -> V2TemplateOptionCard(option, state.selectedTemplateId == option.template.id, enabled) {
                        actions.chooseTemplate(option.template.id); catalogOpen = false
                    } }
                    if (options.isEmpty()) Text("Esta institución todavía no tiene horarios disponibles.")
                    TextButton(onClick = { actions.beginTemplateForPlace(place.place.id) }, enabled = enabled,
                        modifier = Modifier.testTag("v2-manual-create-template")) { Text("Agregar horario") }
                }
            }
        }
    }
    state.selectedOption?.let { option ->
        Text("${option.objective.fullName} (${option.objective.abbreviation})", style = MaterialTheme.typography.titleMedium)
        Text("${option.template.startTime}–${option.template.endTime} · ${option.workType.name}")
        if (!catalogOpen) TextButton(onClick = { actions.beginTemplateForPlace(option.workPlace.id) }, enabled = enabled,
            modifier = Modifier.testTag("v2-manual-create-template")) { Text("Agregar horario") }
    }
    OutlinedButton(onClick = { actions.requestDayOff(state.selectedDates, true) }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("v2-mark-day-off")) {
        Text("Marcar como franco")
    }
    TextButton(onClick = { positionExpanded = !positionExpanded }, enabled = enabled,
        modifier = Modifier.testTag("manual-toggle-position").semantics {
            stateDescription = if (positionExpanded) "Editor desplegado" else "Editor contraído"
        }) { Text(when {
            positionExpanded -> "Contraer puesto o función"
            state.position.isNotBlank() -> "Editar puesto o función"
            else -> "Agregar puesto o función"
        }) }
    if (positionExpanded) {
        OutlinedTextField(state.position, actions.updatePosition, enabled = enabled,
            label = { Text("Puesto o función (opcional)") }, modifier = Modifier.fillMaxWidth().testTag("v2-manual-position"))
        if (!singular) Text("Se aplicará a todas las jornadas de esta carga.", style = MaterialTheme.typography.bodySmall)
        if (state.position.isNotEmpty()) TextButton(onClick = { actions.updatePosition(""); positionExpanded = false }, enabled = enabled,
            modifier = Modifier.testTag("manual-remove-position")) { Text("Quitar puesto o función") }
    } else if (state.position.isNotBlank()) {
        Text("Puesto o función: ${state.position}", modifier = Modifier.testTag("manual-position-summary"))
    }
    TextButton(onModifyCalendarSelection, enabled = enabled, modifier = Modifier.testTag("v2-manual-modify-dates")) {
        Text("Volver a la selección de días")
    }
    if (state.selectedOption != null) {
        val hasNote = state.noteBody.isNotBlank() || state.noteAttachments.isNotEmpty()
        TextButton(onClick = { noteExpanded = !noteExpanded }, enabled = enabled,
            modifier = Modifier.testTag("manual-add-note").semantics {
                stateDescription = if (noteExpanded) "Editor desplegado" else "Editor contraído"
            }) {
            Text(when {
                noteExpanded -> "Contraer nota"
                hasNote -> "Editar nota"
                else -> "Agregar nota"
            })
        }
        if (noteExpanded) {
            OutlinedTextField(state.noteBody, actions.updateNote, enabled = enabled,
                label = { Text("Nota opcional") }, modifier = Modifier.fillMaxWidth().testTag("manual-note"))
            TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = enabled, modifier = Modifier.testTag("manual-note-images")) {
                Text("Agregar imágenes")
            }
            state.noteAttachments.forEachIndexed { index, image ->
                NoteDraftAttachment(image, index, draftImages, enabled,
                    onView = { viewingImage = image.storageKey }, onRemove = { actions.removeNoteImage(image.id) })
            }
            if (!singular) Text("Cada jornada tendrá su propia nota y sus imágenes independientes.", style = MaterialTheme.typography.bodySmall)
        } else if (hasNote) {
            if (state.noteBody.isNotBlank()) Text(state.noteBody, modifier = Modifier.testTag("manual-note-summary"))
            if (state.noteAttachments.isNotEmpty()) Text(
                "${state.noteAttachments.size} ${if (state.noteAttachments.size == 1) "imagen adjunta" else "imágenes adjuntas"}")
        }
    }
    Button(onClick = actions.requestSave, enabled = enabled && state.selectedOption != null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("v2-manual-save-direct")) {
        Text(if (singular) "Guardar jornada" else "Guardar jornadas")
    }
    TextButton(actions.requestReview, enabled = enabled && state.selectedOption != null,
        modifier = Modifier.testTag("v2-manual-review")) { Text("Ver fechas y detalles") }
    TextButton(onClick = actions.requestClear, enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("manual-clear-days")) {
        Text(if (singular) "Limpiar día" else "Limpiar días")
    }
    TextButton(actions.cancel, enabled = !state.isSaving && !state.isLoading,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("manual-cancel-load")) { Text("Cancelar carga") }
    viewingImage?.let { key -> PrivateNoteImageDialog(key, draftImages, onDismiss = { viewingImage = null }) }
}
