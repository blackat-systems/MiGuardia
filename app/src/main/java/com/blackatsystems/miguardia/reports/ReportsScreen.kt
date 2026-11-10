package com.blackatsystems.miguardia.reports

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.report.ReportFormat
import com.blackatsystems.miguardia.core.domain.report.ReportPrivacySelection
import com.blackatsystems.miguardia.ui.components.MiGuardiaButtonShape
import com.blackatsystems.miguardia.ui.components.MiGuardiaSpacing
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.PrimaryAction
import com.blackatsystems.miguardia.ui.components.SecondaryAction
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.components.vigiliaScreenBackground
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ReportMonthFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("es-AR"))

@Composable
fun ReportsSurfaceHost(
    state: ReportsUiState,
    actions: ReportsActions,
) {
    if (!state.isOpen) return
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(state.format.mimeType),
    ) { uri ->
        if (uri == null) actions.cancelSave() else actions.saveTo(uri)
    }
    BackHandler {
        if (!state.isBusy) actions.close()
    }
    Surface(
        modifier = Modifier.fillMaxSize().testTag("reports-surface"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize().vigiliaScreenBackground().safeDrawingPadding()) {
            ReportsHeader(
                onNavigation = actions.close,
                modifier = Modifier.testTag("reports-header"),
            )
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(MiGuardiaSpacing.large),
                verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.large),
            ) {
                Text(
                    "Generá una fotografía privada y de sólo lectura de ${state.month.format(ReportMonthFormatter)}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.errorMessage?.let { message ->
                    PersistentMessage(
                        message = message,
                        modifier = Modifier.testTag("reports-error"),
                        onRetry = actions.retry,
                    )
                }
                state.infoMessage?.let { message ->
                    SectionCard("Listo", modifier = Modifier.testTag("reports-info")) {
                        Text(message)
                        TextButton(onClick = actions.clearMessage) { Text("Cerrar mensaje") }
                    }
                }
                when (state.stage) {
                    ReportsStage.LOADING -> BusyState("Preparando una vista previa coherente…", "reports-loading")
                    ReportsStage.GENERATING -> BusyState("Generando el archivo privado…", "reports-generating")
                    ReportsStage.SAVING -> BusyState("Guardando en el destino elegido…", "reports-saving")
                    ReportsStage.SHARING -> BusyState("Abriendo el selector para compartir…", "reports-sharing")
                    ReportsStage.CONTENT,
                    ReportsStage.EMPTY,
                    ReportsStage.READY,
                    ReportsStage.ERROR,
                    -> ReportContent(
                        state = state,
                        actions = actions,
                        onSaveRequested = {
                            val name = state.artifact?.suggestedFileName
                            if (name != null && actions.requestSave()) {
                                try {
                                    saveLauncher.launch(name)
                                } catch (_: ActivityNotFoundException) {
                                    actions.cancelSave()
                                }
                            }
                        },
                        onShareRequested = {
                            val artifact = state.artifact
                            if (artifact != null && actions.requestShare()) {
                                try {
                                    context.startActivity(ReportShareIntentFactory.createChooser(context, artifact))
                                    actions.shareLaunched(true, null)
                                } catch (_: ActivityNotFoundException) {
                                    actions.shareLaunched(
                                        false,
                                        "No encontramos una aplicación compatible para compartir el informe.",
                                    )
                                } catch (_: Exception) {
                                    actions.shareLaunched(
                                        false,
                                        "No pudimos abrir el selector para compartir el informe.",
                                    )
                                }
                            }
                        },
                    )
                }
                Spacer(Modifier.height(MiGuardiaSpacing.medium))
            }
        }
    }
    if (state.medicalConfirmationPending) {
        AlertDialog(
            onDismissRequest = actions.dismissMedicalConfirmation,
            title = { Text("Incluir una nota médica privada") },
            text = {
                Text(
                    "La marca de carpeta médica ya puede aparecer sin esta opción. Si continuás, el texto privado de la nota quedará dentro del archivo que guardes o compartas.",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                Button(
                    onClick = actions.confirmMedicalNotes,
                    modifier = Modifier.testTag("reports-confirm-medical-note"),
                    shape = MiGuardiaButtonShape,
                ) {
                    Text("Sí, incluirla")
                }
            },
            dismissButton = {
                TextButton(onClick = actions.dismissMedicalConfirmation) { Text("Cancelar") }
            },
        )
    }
}

/**
 * Style v2: kept local instead of the shared SurfaceHeader on purpose. When the title and the
 * action do not fit in one row, this header puts the action *below* the title with an explicit
 * 48 dp target, and ReportsComposeTest's native geometry diagnostic (320/360/384 dp, every font
 * scale) requires exactly that order and gap. SurfaceHeader puts the action above the title.
 * Only its styles are aligned with SurfaceHeader (theme title without a forced weight, heading).
 */
@Composable
private fun ReportsHeader(
    onNavigation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val titleStyle = MaterialTheme.typography.titleLarge
    val navigationStyle = MaterialTheme.typography.labelLarge
    val navigationPadding = ButtonDefaults.TextButtonContentPadding
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val contentModifier = Modifier.fillMaxWidth().padding(
            horizontal = MiGuardiaSpacing.large,
            vertical = MiGuardiaSpacing.small,
        )
        val titleWidth = measurer.measure("Informes locales", style = titleStyle, softWrap = false).size.width
        val navigationTextWidth = measurer.measure("Volver al Resumen", style = navigationStyle, softWrap = false).size.width
        val navigationWidth = with(density) {
            maxOf(ButtonDefaults.MinWidth.roundToPx(), navigationTextWidth +
                navigationPadding.calculateLeftPadding(layoutDirection).roundToPx() +
                navigationPadding.calculateRightPadding(layoutDirection).roundToPx())
        }
        // Measure the native SP styles and the same button padding used below; do not shrink text to fit a row.
        val fitsInline = titleWidth + navigationWidth + with(density) { MiGuardiaSpacing.small.roundToPx() } <=
            constraints.maxWidth - with(density) { 2 * MiGuardiaSpacing.large.roundToPx() }
        if (!fitsInline) {
            Column(
                modifier = contentModifier,
                verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.extraSmall),
            ) {
                Text(
                    text = "Informes locales",
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                    style = titleStyle,
                )
                TextButton(
                    onClick = onNavigation,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                    contentPadding = navigationPadding,
                ) {
                    Text("Volver al Resumen", style = navigationStyle)
                }
            }
        } else {
            Row(
                modifier = contentModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
            ) {
                Text(
                    text = "Informes locales",
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = titleStyle,
                )
                TextButton(
                    onClick = onNavigation,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = navigationPadding,
                ) { Text("Volver al Resumen", style = navigationStyle) }
            }
        }
    }
}

@Composable
private fun ReportContent(
    state: ReportsUiState,
    actions: ReportsActions,
    onSaveRequested: () -> Unit,
    onShareRequested: () -> Unit,
) {
    val artifact = state.artifact
    FormatSection(state, actions)
    PrivacySection(state, actions)
    ReviewSection(state)
    if (state.stage == ReportsStage.READY && artifact != null) {
        SectionCard("Archivo listo", modifier = Modifier.testTag("reports-ready")) {
            Text(artifact.suggestedFileName, style = MaterialTheme.typography.titleSmall)
            Text(
                "${artifact.byteSize} bytes · ${artifact.format.mimeType}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryAction(
                label = "Guardar informe",
                onClick = onSaveRequested,
                modifier = Modifier.testTag("reports-save"),
            )
            SecondaryAction(
                label = "Compartir",
                onClick = onShareRequested,
                modifier = Modifier.fillMaxWidth().testTag("reports-share"),
            )
            SecondaryAction(
                label = "Regenerar desde los datos actuales",
                onClick = actions.regenerate,
                modifier = Modifier.fillMaxWidth().testTag("reports-regenerate"),
            )
        }
    } else if (state.stage != ReportsStage.ERROR || state.projection != null) {
        PrimaryAction(
            label = "Generar archivo",
            onClick = actions.generate,
            enabled = state.projection != null && !state.isBusy,
            modifier = Modifier.testTag("reports-generate"),
        )
    }
    SecondaryAction(
        label = "Volver al Resumen",
        onClick = actions.close,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().testTag("reports-back-summary"),
    )
}

@Composable
private fun FormatSection(state: ReportsUiState, actions: ReportsActions) {
    SectionCard("Formato", modifier = Modifier.selectableGroup()) {
        FormatChoice("PDF", ReportFormat.PDF, state, actions)
        Text("Para leer, imprimir, adjuntar y, si lo elegís, incluir fotos.")
        FormatChoice("Excel / XLSX", ReportFormat.XLSX, state, actions)
        Text("Planilla OOXML con Resumen, Jornadas, Disponibilidad, Situaciones y Notas cuando corresponda.")
        if (state.format == ReportFormat.XLSX) {
            Text(
                "Las fotos sólo pueden incluirse en PDF.",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("reports-xlsx-photo-explanation"),
            )
        }
    }
}

@Composable
private fun FormatChoice(
    label: String,
    format: ReportFormat,
    state: ReportsUiState,
    actions: ReportsActions,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = state.format == format,
                enabled = !state.isBusy,
                role = Role.RadioButton,
                onClick = { actions.setFormat(format) },
            )
            .testTag("reports-format-${format.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = state.format == format,
            onClick = null,
            enabled = !state.isBusy,
            modifier = Modifier.testTag("reports-format-${format.name.lowercase()}-radio"),
        )
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun PrivacySection(state: ReportsUiState, actions: ReportsActions) {
    SectionCard(
        title = "Inclusiones privadas",
        supportingText = "Empiezan apagadas en cada sesión nueva. Sólo se aplican al archivo que generes ahora.",
    ) {
        PrivacySwitch(
            label = "Incluir mi nombre o apodo",
            checked = state.privacy.includeDisplayName,
            enabled = state.displayNameAvailable && !state.isBusy,
            unavailableText = if (!state.displayNameAvailable) "No hay un nombre o apodo guardado." else null,
            onChecked = actions.setDisplayNameIncluded,
            tag = "reports-include-name",
        )
        PrivacySwitch(
            label = "Incluir puesto o función",
            checked = state.privacy.includePosition,
            enabled = !state.isBusy,
            onChecked = actions.setPositionIncluded,
            tag = "reports-include-position",
        )
        PrivacySwitch(
            label = "Incluir notas de jornadas",
            checked = state.privacy.includeShiftNotes,
            enabled = !state.isBusy,
            onChecked = actions.setShiftNotesIncluded,
            tag = "reports-include-shift-notes",
        )
        PrivacySwitch(
            label = "Incluir notas privadas de carpeta médica",
            checked = state.privacy.includeMedicalNotes,
            enabled = !state.isBusy,
            onChecked = actions.requestMedicalNotes,
            tag = "reports-include-medical-notes",
        )
        if (state.format == ReportFormat.PDF) {
            PrivacySwitch(
                label = "Incluir fotos mensuales",
                checked = state.photoSelectionExpanded,
                enabled = state.availablePhotos.isNotEmpty() && !state.isBusy,
                unavailableText = if (state.availablePhotos.isEmpty()) "No hay fotos mensuales para elegir." else null,
                onChecked = actions.setPhotoSelectionExpanded,
                tag = "reports-include-photos",
            )
            if (state.photoSelectionExpanded) {
                Text(
                    "La imagen puede contener nombres u otros datos de terceros. MiGuardia no hace OCR, recorte ni redacción. Elegí hasta 12 fotos.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("reports-photo-warning"),
                )
                Text(
                    "Seleccionadas: ${state.privacy.selectedPhotoIds.size} de ${ReportPrivacySelection.MAX_REPORT_PHOTOS}",
                    style = MaterialTheme.typography.titleSmall,
                )
                state.availablePhotos.forEachIndexed { index, photo ->
                    val enabled = !state.isBusy && (photo.available || photo.selected)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = photo.id in state.privacy.selectedPhotoIds,
                                enabled = enabled,
                                role = Role.Checkbox,
                                onValueChange = { actions.setPhotoSelected(photo.id, it) },
                            )
                            .testTag("reports-photo-choice-$index"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = photo.id in state.privacy.selectedPhotoIds,
                            onCheckedChange = null,
                            enabled = enabled,
                            modifier = Modifier.testTag("reports-photo-choice-$index-checkbox"),
                        )
                        Text(
                            photo.label,
                            color = if (photo.available) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacySwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
    tag: String,
    unavailableText: String? = null,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChecked,
            )
            .testTag(tag),
    ) {
        val labelContent: @Composable (Modifier) -> Unit = { modifier ->
            Text(label, modifier = modifier.testTag("$tag-label"), style = MaterialTheme.typography.bodyMedium)
        }
        val switchContent: @Composable (Modifier) -> Unit = { modifier ->
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = modifier.testTag("$tag-switch"),
            )
        }
        if (maxWidth < 240.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium)) {
                labelContent(Modifier.fillMaxWidth())
                switchContent(Modifier.align(Alignment.End))
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labelContent(Modifier.weight(1f))
                switchContent(Modifier)
            }
        }
    }
    unavailableText?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReviewSection(state: ReportsUiState) {
    val projection = state.projection
    SectionCard(
        title = "Revisión antes de crear",
        modifier = Modifier.testTag("reports-review"),
    ) {
        if (projection == null) {
            Text("La vista previa todavía no está disponible.")
            return@SectionCard
        }
        Text(projection.statusText, style = MaterialTheme.typography.titleSmall)
        Text(if (projection.hasActivity) "Actividad registrada" else "Sin actividad registrada")
        Text("Rubros: ${projection.sectors.joinToString { it.displayName }.ifBlank { "sin actividad laboral" }}")
        val total = projection.summary.essentials.totalWorked?.value ?: 0L
        Text("Total trabajado: ${readableMinutes(total)}")
        Text("Jornadas y extras: ${projection.workRows.size}")
        Text("Disponibilidades: ${projection.availabilityRows.size} (separadas del trabajo)")
        Text("Situaciones: ${projection.situations.size}")
        Text("Notas incluidas: ${projection.notes.size}")
        Text("Fotos incluidas: ${projection.photos.size}")
        Text(
            "Siempre quedan afuera direcciones, IDs, rutas, EXIF, explicaciones internas y montos. " +
                "Las notas privadas y las fotos sólo se incluyen si las elegiste.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BusyState(message: String, tag: String) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        if (maxWidth < 240.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium),
                verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun readableMinutes(minutes: Long): String =
    "${minutes / 60} h ${kotlin.math.abs(minutes % 60)} min"
