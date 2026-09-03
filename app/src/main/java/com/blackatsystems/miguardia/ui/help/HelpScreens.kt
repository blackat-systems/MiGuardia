package com.blackatsystems.miguardia.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class HelpAnchor {
    MENU,
    TODAY_CARD,
    MONTH_AND_GRID,
    DAY_DETAIL,
    PHOTOS,
    LOAD_AND_REPEAT,
    SUMMARY,
    HELP,
}

class HelpAnchorRegistry {
    private val positions = mutableStateMapOf<HelpAnchor, Rect>()

    fun update(anchor: HelpAnchor, bounds: Rect) {
        positions[anchor] = bounds
    }

    fun bounds(anchor: HelpAnchor): Rect? = positions[anchor]?.takeUnless { it.isEmpty }
}

fun Modifier.helpAnchor(registry: HelpAnchorRegistry?, anchor: HelpAnchor): Modifier =
    if (registry == null) this else onGloballyPositioned { coordinates ->
        registry.update(anchor, coordinates.boundsInRoot())
    }

@Composable
fun HelpDecisionScreen(
    state: HelpUiState,
    actions: HelpActions,
    modifier: Modifier = Modifier,
) {
    when {
        state.readState == HelpReadState.Loading -> HelpLoadingScreen(modifier)
        state.readState == HelpReadState.Error -> HelpReadErrorScreen(actions.retryRead, modifier)
        state.session?.stage == HelpSessionStage.INTRODUCTION ->
            HelpIntroductionScreen(state, actions, modifier)
        else -> HelpLoadingScreen(modifier)
    }
}

@Composable
private fun HelpLoadingScreen(modifier: Modifier) {
    Surface(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("help-state-loading")
                .semantics { contentDescription = "Preparando la guía inicial" },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Preparando tu primera guía…")
            }
        }
    }
}

@Composable
private fun HelpReadErrorScreen(onRetry: () -> Unit, modifier: Modifier) {
    Surface(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag("help-state-read-error"),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No pudimos preparar la guía", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "Tus jornadas y tu configuración no cambiaron. Reintentá para decidir de forma segura si corresponde mostrarla.",
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry, modifier = Modifier.testTag("help-read-retry")) {
                Text("Reintentar")
            }
        }
    }
}

private data class IntroductionCopy(val title: String, val body: String)

private val IntroductionCopies = listOf(
    IntroductionCopy(
        "Organizá tu trabajo",
        "Usá una sola grilla para cargar, repetir, consultar, corregir o eliminar jornadas. El horario real, los extras y tu disponibilidad quedan sobre esa misma historia.",
    ),
    IntroductionCopy(
        "Entendé lo que viene y lo que hiciste",
        "La tarjeta de hoy, Horas, Resumen, próximo evento, avisos, Widget e Informes reutilizan la información que ya guardaste.",
    ),
    IntroductionCopy(
        "Tus datos quedan bajo tu control",
        "MiGuardia es local: no exige cuenta ni nube. Copias permite recuperar datos y Bloqueo de acceso protege la entrada.",
    ),
)

@Composable
private fun HelpIntroductionScreen(state: HelpUiState, actions: HelpActions, modifier: Modifier) {
    val session = requireNotNull(state.session)
    val copy = IntroductionCopies[session.stepIndex]
    BackHandler(onBack = actions.back)
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag("help-introduction-${session.stepIndex + 1}"),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Primeros pasos · ${session.stepIndex + 1} de $INTRODUCTION_STEP_COUNT",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (session.stepIndex + 1f) / INTRODUCTION_STEP_COUNT },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                copy.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(copy.body, style = MaterialTheme.typography.bodyLarge)
            state.errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                ErrorCard(message, actions.retryCompletion)
            }
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = actions.back,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f).testTag("help-introduction-back"),
                ) { Text("Atrás") }
                Button(
                    onClick = actions.next,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f).testTag("help-introduction-next"),
                ) {
                    Text(if (session.stepIndex == INTRODUCTION_STEP_COUNT - 1) "Ver recorrido" else "Siguiente")
                }
            }
            if (session.mode == HelpSessionMode.AUTOMATIC) {
                TextButton(
                    onClick = actions.requestExit,
                    enabled = !state.isSaving,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("help-skip"),
                ) { Text(if (state.isSaving) "Guardando…" else "Omitir guía") }
            } else {
                TextButton(
                    onClick = actions.requestExit,
                    enabled = !state.isSaving,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("help-replay-close"),
                ) { Text("Cerrar y volver a Ayuda") }
            }
        }
    }
    ExitConfirmation(state, actions)
}

private data class TourCopy(val title: String, val body: String)

private val TourCopies = mapOf(
    HelpTourStep.MENU to TourCopy(
        "Tu menú principal",
        "Abrir menú reúne tres grupos: Trabajo, Contexto y Aplicación. Desde ahí volvés al Calendario, Resumen, configuración, Copias, Bloqueo, Apariencia y Ayuda.",
    ),
    HelpTourStep.TODAY_CARD to TourCopy(
        "La tarjeta de hoy",
        "Arriba del Calendario encontrás lo próximo o lo que está ocurriendo. Si todavía no hay jornadas, la tarjeta lo explica sin inventar contenido.",
    ),
    HelpTourStep.MONTH_AND_GRID to TourCopy(
        "Un mes, una sola grilla",
        "Cambiá de mes o volvé a hoy desde estos controles. La grilla reúne jornadas, estados y contexto diario.",
    ),
    HelpTourStep.DAY_DETAIL to TourCopy(
        "Detalle de cada día",
        "Tocá un día real para abrir su detalle. Cuando corresponda, ahí aparecen jornadas y accesos a notas. Si el mes está vacío, este paso sólo señala dónde se hará.",
    ),
    HelpTourStep.PHOTOS to TourCopy(
        "Fotos del cronograma",
        "Fotos del mes abre una superficie separada. Elegir o capturar archivos y sus permisos ocurre recién allí, nunca desde esta guía.",
    ),
    HelpTourStep.LOAD_AND_REPEAT to TourCopy(
        "Cargá o repetí jornadas",
        "Cargar jornadas permite guardar directamente; la revisión detallada es opcional. Repetir jornadas muestra cuántas se crearán antes de guardar.",
    ),
    HelpTourStep.SUMMARY to TourCopy(
        "Resumen mensual",
        "Resumen reutiliza lo guardado. Cada cifra disponible puede abrir su detalle; no es una liquidación salarial.",
    ),
    HelpTourStep.HELP to TourCopy(
        "Ayuda queda siempre disponible",
        "La entrada Ayuda vive una sola vez en Aplicación. Podés consultar temas o repetir este recorrido cuando quieras.",
    ),
)

@Composable
fun HelpTourOverlay(
    state: HelpUiState,
    actions: HelpActions,
    anchors: HelpAnchorRegistry,
    modifier: Modifier = Modifier,
) {
    val session = state.session?.takeIf { it.stage == HelpSessionStage.TOUR } ?: return
    val step = requireNotNull(session.tourStep)
    val anchor = anchors.bounds(step.anchor())
    val copy = requireNotNull(TourCopies[step])
    BackHandler(onBack = actions.back)
    BoxWithConstraints(modifier.fillMaxSize().testTag("help-tour-${step.name.lowercase()}")) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val rootWidthPx = with(density) { maxWidth.toPx() }
        val rootHeightPx = with(density) { maxHeight.toPx() }
        val visibleAnchor = anchor?.takeIf { bounds ->
            bounds.right > 0f && bounds.left < rootWidthPx &&
                bounds.bottom > 0f && bounds.top < rootHeightPx
        }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(onClick = {})
                .semantics { contentDescription = "Recorrido activo. Los controles señalados no se activan." },
        )
        visibleAnchor?.let { bounds ->
            Box(
                Modifier
                    .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(
                        width = with(density) { bounds.width.toDp() },
                        height = with(density) { bounds.height.toDp() },
                    )
                    .border(3.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(14.dp))
                    .testTag("help-tour-anchor"),
            )
        }
        Surface(
            modifier = Modifier
                .align(
                    if ((visibleAnchor?.center?.y ?: 0f) > rootHeightPx / 2f) {
                        Alignment.TopCenter
                    } else {
                        Alignment.BottomCenter
                    },
                )
                .safeDrawingPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Recorrido · ${session.stepIndex + 1} de ${HelpTourStep.entries.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(copy.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(copy.body)
                if (visibleAnchor == null) {
                    Text(
                        "Este control no está disponible en el estado actual. La explicación queda como referencia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.errorMessage?.let { ErrorCard(it, actions.retryCompletion) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = actions.back,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f).testTag("help-tour-back"),
                    ) { Text("Atrás") }
                    Button(
                        onClick = if (step == HelpTourStep.HELP) actions.finish else actions.next,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f).testTag("help-tour-next"),
                    ) { Text(if (step == HelpTourStep.HELP) "Finalizar" else "Siguiente") }
                }
                if (session.mode == HelpSessionMode.AUTOMATIC) {
                    TextButton(
                        onClick = actions.requestExit,
                        enabled = !state.isSaving,
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("help-skip"),
                    ) { Text(if (state.isSaving) "Guardando…" else "Omitir guía") }
                } else {
                    TextButton(
                        onClick = actions.requestExit,
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("help-replay-close"),
                    ) { Text("Cerrar y volver a Ayuda") }
                }
            }
        }
    }
    ExitConfirmation(state, actions)
}

private fun HelpTourStep.anchor(): HelpAnchor = when (this) {
    HelpTourStep.MENU -> HelpAnchor.MENU
    HelpTourStep.TODAY_CARD -> HelpAnchor.TODAY_CARD
    HelpTourStep.MONTH_AND_GRID -> HelpAnchor.MONTH_AND_GRID
    HelpTourStep.DAY_DETAIL -> HelpAnchor.DAY_DETAIL
    HelpTourStep.PHOTOS -> HelpAnchor.PHOTOS
    HelpTourStep.LOAD_AND_REPEAT -> HelpAnchor.LOAD_AND_REPEAT
    HelpTourStep.SUMMARY -> HelpAnchor.SUMMARY
    HelpTourStep.HELP -> HelpAnchor.HELP
}

@Composable
private fun ExitConfirmation(state: HelpUiState, actions: HelpActions) {
    if (!state.showExitConfirmation) return
    AlertDialog(
        onDismissRequest = actions.dismissExitConfirmation,
        title = { Text("¿Omitir la guía?") },
        text = { Text("Podés repetirla más adelante desde Ayuda. Para salir, MiGuardia necesita guardar esta decisión.") },
        confirmButton = {
            TextButton(
                onClick = actions.confirmExit,
                enabled = !state.isSaving,
                modifier = Modifier.testTag("help-confirm-skip"),
            ) { Text(if (state.isSaving) "Guardando…" else "Omitir y abrir Calendario") }
        },
        dismissButton = {
            TextButton(onClick = actions.dismissExitConfirmation, enabled = !state.isSaving) {
                Text("Seguir con la guía")
            }
        },
    )
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("help-completion-retry")) {
                Text("Reintentar guardado")
            }
        }
    }
}

private data class HelpTopic(val title: String, val body: String)

private val HelpTopics = listOf(
    HelpTopic("Primeros pasos y Mi forma de trabajar", "Definí un único rubro y luego tus lugares, tipos y horarios desde Trabajo > Mi forma de trabajar. Revisá bien antes de guardar: editar una plantilla no cambia jornadas pasadas."),
    HelpTopic("Calendario, jornadas, feriados, vacaciones, notas y Fotos", "El Calendario es la historia principal. Cargá o corregí jornadas allí; feriados y vacaciones viven en Trabajo, y notas en el detalle del día. Fotos del mes abre una superficie separada y sólo accede a archivos cuando vos lo decidís."),
    HelpTopic("Horario real, horas extra y disponibilidad", "Abrí el detalle de una jornada para registrar lo ocurrido. En Trabajo > Mi forma de trabajar están Tipos de horas extra, Tus horas y tu meta, y Disponibilidad. Revisá la fecha efectiva: el horario real refleja hechos y no cambia la planificación original."),
    HelpTopic("Horas, Resumen y tarjeta de hoy", "La tarjeta superior vive al comienzo del Calendario y anticipa lo inmediato. Abrí Menú > Resumen para reunir el mes y entrar al detalle de cada cifra disponible; las horas son organizativas y no calculan salario."),
    HelpTopic("Notificaciones, Clima y Widget", "Se abren desde Contexto. Cada función explica sus permisos dentro de su propia pantalla; activarlas es opcional y la guía nunca los solicita."),
    HelpTopic("Informes locales", "Desde Resumen podés preparar informes PDF o XLSX con datos del mes. Revisá el contenido y el destino antes de crear o compartir un archivo."),
    HelpTopic("Copias y restauración", "Aplicación > Copias permite crear o recuperar una copia local. La restauración reemplaza o combina datos sólo después de revisión y confirmación; guardá la contraseña fuera de la copia."),
    HelpTopic("Bloqueo de acceso y privacidad", "Aplicación > Bloqueo protege la entrada usando la seguridad del dispositivo. MiGuardia guarda datos localmente, pero las notificaciones, Widgets e informes pueden mostrarlos fuera de la pantalla principal según lo que habilites."),
    HelpTopic("Apariencia y zoom interno", "Aplicación > Apariencia ofrece tema claro, oscuro o del sistema y zoom interno 100 %, 150 % o 200 %. Es un ajuste propio de MiGuardia y no modifica Android."),
)

@Composable
fun HelpScreen(
    contentPadding: PaddingValues,
    canRepeat: Boolean,
    actions: HelpActions,
) {
    var expandedTopic by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("help-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ayuda", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Consultá una función sin mostrar ni cambiar tus datos.")
        if (!canRepeat) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    "Primero terminá tu primer lugar y horario desde el Calendario. Después vas a poder recorrer toda la aplicación.",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        HelpTopics.forEachIndexed { index, topic ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedTopic = if (expandedTopic == index) null else index }
                    .testTag("help-topic-$index"),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(topic.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (expandedTopic == index) {
                        HorizontalDivider()
                        Text(topic.body)
                    } else {
                        Text("Tocá para consultar", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(
            onClick = actions.startReplay,
            enabled = canRepeat,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("help-repeat-tour"),
        ) { Text("Repetir recorrido inicial") }
        Text(
            if (canRepeat) "La repetición no borra ni modifica tu progreso." else "La repetición se habilita al completar la primera configuración.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
