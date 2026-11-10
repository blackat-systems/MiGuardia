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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.ui.components.MiGuardiaButtonShape
import com.blackatsystems.miguardia.ui.components.MiGuardiaSpacing
import com.blackatsystems.miguardia.ui.components.PrimaryAction
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SecondaryAction
import com.blackatsystems.miguardia.ui.components.cardBorder
import com.blackatsystems.miguardia.ui.components.vigiliaCardSurface
import com.blackatsystems.miguardia.ui.components.vigiliaScreenBackground
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

    fun remove(anchor: HelpAnchor) {
        positions.remove(anchor)
    }
}

fun Modifier.helpAnchor(registry: HelpAnchorRegistry?, anchor: HelpAnchor): Modifier =
    if (registry == null) this else composed {
        DisposableEffect(registry, anchor) {
            onDispose { registry.remove(anchor) }
        }
        onGloballyPositioned { coordinates ->
            registry.update(anchor, coordinates.boundsInRoot())
        }
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
                .vigiliaScreenBackground()
                .testTag("help-state-loading")
                .semantics { contentDescription = "Preparando la guía inicial" },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(MiGuardiaSpacing.large))
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
                .vigiliaScreenBackground()
                .verticalScroll(rememberScrollState())
                .padding(MiGuardiaSpacing.large)
                .testTag("help-state-read-error"),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "No pudimos preparar la guía",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(MiGuardiaSpacing.medium))
            Text(
                "Tus jornadas y tu configuración no cambiaron. Reintentá para decidir de forma segura si corresponde mostrarla.",
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(MiGuardiaSpacing.extraLarge))
            PrimaryAction(
                label = "Reintentar",
                onClick = onRetry,
                modifier = Modifier.testTag("help-read-retry"),
            )
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
        "El próximo evento, Horas, Resumen, avisos, Widget e Informes usan la información que ya guardaste.",
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
                .vigiliaScreenBackground()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MiGuardiaSpacing.large)
                .testTag("help-introduction-${session.stepIndex + 1}"),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Primeros pasos · ${session.stepIndex + 1} de $INTRODUCTION_STEP_COUNT",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(MiGuardiaSpacing.small))
            LinearProgressIndicator(
                progress = { (session.stepIndex + 1f) / INTRODUCTION_STEP_COUNT },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MiGuardiaSpacing.extraLarge))
            Text(
                copy.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(MiGuardiaSpacing.medium))
            Text(copy.body, style = MaterialTheme.typography.bodyLarge)
            state.errorMessage?.let { message ->
                Spacer(Modifier.height(MiGuardiaSpacing.large))
                ErrorCard(message, actions.retryCompletion)
            }
            Spacer(Modifier.height(MiGuardiaSpacing.extraLarge))
            val introductionNextLabel =
                if (session.stepIndex == INTRODUCTION_STEP_COUNT - 1) "Ver recorrido" else "Siguiente"
            HelpStepActions(
                startLabel = "Atrás",
                endLabel = introductionNextLabel,
                spacing = MiGuardiaSpacing.medium,
                start = { slot ->
                    SecondaryAction(
                        label = "Atrás",
                        onClick = actions.back,
                        enabled = !state.isSaving,
                        modifier = slot.testTag("help-introduction-back"),
                    )
                },
                end = { slot ->
                    PrimaryAction(
                        label = introductionNextLabel,
                        onClick = actions.next,
                        enabled = !state.isSaving,
                        modifier = slot.testTag("help-introduction-next"),
                    )
                },
            )
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
        "El menú abre Calendario y Resumen. Después reúne Acciones del Calendario, Tu trabajo, Avisos y contexto y Aplicación. Ahí encontrás también Copias, Bloqueo, Apariencia y Ayuda.",
    ),
    HelpTourStep.TODAY_CARD to TourCopy(
        "Tu próximo evento",
        "Cuando hay espacio, la tarjeta superior anticipa lo próximo o lo que está ocurriendo. Si falta altura, se reserva el espacio para el mes completo. El detalle de cada día sigue disponible.",
    ),
    HelpTourStep.MONTH_AND_GRID to TourCopy(
        "Un mes, una sola grilla",
        "Las flechas cambian de mes. Para volver al mes actual, abrí el menú y tocá Ir a hoy en Acciones del Calendario. Tocá un día para ver sus jornadas y contexto.",
    ),
    HelpTourStep.DAY_DETAIL to TourCopy(
        "Detalle de cada día",
        "Un toque abre el detalle. Mantené presionado para seleccionar varios días, o usá Menú > Acciones del Calendario > Seleccionar días. Consultar o seleccionar no guarda cambios.",
    ),
    HelpTourStep.PHOTOS to TourCopy(
        "Fotos del cronograma",
        "Menú > Acciones del Calendario > Fotos del mes abre las imágenes del mes visible. Elegir o capturar archivos ocurre recién allí, nunca desde esta guía.",
    ),
    HelpTourStep.LOAD_AND_REPEAT to TourCopy(
        "Cargá jornadas o francos",
        "En Acciones del Calendario, Cargar jornadas o francos empieza por las fechas. Después elegís institución y horario, o Franco. Podés volver a las fechas sin perder el borrador. Repetir jornadas permite revisar la cantidad antes de guardar.",
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
        val spaceAbove = visibleAnchor?.top?.coerceIn(0f, rootHeightPx) ?: 0f
        val spaceBelow = visibleAnchor?.bottom?.let { (rootHeightPx - it).coerceIn(0f, rootHeightPx) }
            ?: rootHeightPx
        val placeAbove = spaceAbove > spaceBelow
        // Keep the actual drawer control visible even in a short window with large text.
        // The explanation scrolls locally; it must not cover its own reference.
        val drawerTarget = step == HelpTourStep.PHOTOS || step == HelpTourStep.LOAD_AND_REPEAT ||
            step == HelpTourStep.HELP
        val panelHeight = if (drawerTarget && visibleAnchor != null) {
            with(density) { maxOf(spaceAbove, spaceBelow).toDp() }.coerceAtMost(420.dp)
        } else 420.dp
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
                    if (placeAbove) {
                        Alignment.TopCenter
                    } else {
                        Alignment.BottomCenter
                    },
                )
                .heightIn(max = panelHeight)
                .safeDrawingPadding()
                .padding(MiGuardiaSpacing.large)
                .fillMaxWidth()
                .testTag("help-tour-explanation"),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(MiGuardiaSpacing.large),
                verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium),
            ) {
                Text(
                    "Recorrido · ${session.stepIndex + 1} de ${HelpTourStep.entries.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(copy.title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                Text(copy.body)
                if (visibleAnchor == null) {
                    Text(
                        "Este control no está disponible en el estado actual. La explicación queda como referencia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.errorMessage?.let { ErrorCard(it, actions.retryCompletion) }
                val tourNextLabel = if (step == HelpTourStep.HELP) "Finalizar" else "Siguiente"
                HelpStepActions(
                    startLabel = "Atrás",
                    endLabel = tourNextLabel,
                    spacing = MiGuardiaSpacing.small,
                    start = { slot ->
                        SecondaryAction(
                            label = "Atrás",
                            onClick = actions.back,
                            enabled = !state.isSaving,
                            modifier = slot.testTag("help-tour-back"),
                        )
                    },
                    end = { slot ->
                        PrimaryAction(
                            label = tourNextLabel,
                            onClick = if (step == HelpTourStep.HELP) actions.finish else actions.next,
                            enabled = !state.isSaving,
                            modifier = slot.testTag("help-tour-next"),
                        )
                    },
                )
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
    // Not PersistentMessage: that one labels its retry «Reintentar» and has no tag, while this
    // card keeps «Reintentar guardado» and `help-completion-retry`. Same surface and spacing.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.padding(MiGuardiaSpacing.large),
            verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = onRetry,
                shape = MiGuardiaButtonShape,
                modifier = Modifier.testTag("help-completion-retry"),
            ) {
                Text("Reintentar guardado")
            }
        }
    }
}

private data class HelpTopic(val title: String, val body: String)

private val HelpTopics = listOf(
    HelpTopic("Primeros pasos y Mi forma de trabajar", "Definí un único rubro y luego tus lugares, tipos y horarios desde Menú > Tu trabajo > Mi forma de trabajar. Editar un horario guardado no cambia jornadas pasadas. También podés crear instituciones y horarios al cargar jornadas."),
    HelpTopic("Calendario, jornadas, feriados, vacaciones, notas y Fotos", "Un toque abre el día; una pulsación larga inicia la selección. El menú ofrece Seleccionar días, Ir a hoy, Cargar jornadas o francos, Repetir jornadas y Fotos del mes en Acciones del Calendario. Feriados y vacaciones están en Tu trabajo. Las notas e imágenes de una jornada se consultan desde su detalle."),
    HelpTopic("Horario real, horas extra y disponibilidad", "Abrí el detalle de una jornada para registrar lo ocurrido. En Menú > Tu trabajo > Mi forma de trabajar > Opciones avanzadas encontrás Tipos de horas extra, Tus horas y tu meta, Disponibilidad y Repeticiones guardadas. Revisá desde qué fecha se aplica cada cambio: registrar el horario real no modifica la planificación original."),
    HelpTopic("Horas, Resumen y próximo evento", "La tarjeta superior anticipa lo inmediato cuando hay espacio. Si falta altura, se reserva el espacio para el mes completo. Abrí Menú > Resumen para ver las horas del mes y tocar cada cifra para saber qué incluye. Las horas no calculan salario."),
    HelpTopic("Notificaciones, Clima y Widget", "Se abren desde Menú > Avisos y contexto. Cada función explica sus permisos dentro de su pantalla; activarlas es opcional y esta guía nunca los solicita."),
    HelpTopic("Informes locales", "En Resumen, abrí Más opciones > Generar informe para preparar un PDF o XLSX del mes. También encontrás Generar informe al final del resumen. Revisá el contenido y el destino antes de crear o compartir un archivo."),
    HelpTopic("Copias y restauración", "Aplicación > Copias permite crear o recuperar una copia local. La restauración reemplaza o combina datos sólo después de revisión y confirmación; guardá la contraseña fuera de la copia."),
    HelpTopic("Bloqueo de acceso y privacidad", "Aplicación > Bloqueo protege la entrada usando la seguridad del dispositivo. MiGuardia guarda datos localmente, pero las notificaciones, Widgets e informes pueden mostrarlos fuera de la pantalla principal según lo que habilites."),
    HelpTopic("Apariencia", "Aplicación > Apariencia ofrece tema claro, oscuro o del sistema. MiGuardia respeta el tamaño de letra que elegiste en Android y funciona con TalkBack, sin controles adicionales dentro de la aplicación."),
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
            .padding(MiGuardiaSpacing.large)
            .testTag("help-screen"),
        verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.large),
    ) {
        ScreenHeading(
            title = "Ayuda",
            supportingText = "Consultá una función sin mostrar ni cambiar tus datos.",
        )
        if (!canRepeat) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                border = cardBorder(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Text(
                    "Primero terminá tu primer lugar y horario desde el Calendario. Después vas a poder recorrer toda la aplicación.",
                    modifier = Modifier.padding(MiGuardiaSpacing.large),
                )
            }
        }
        HelpTopics.forEachIndexed { index, topic ->
            val topicShape = MaterialTheme.shapes.large
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .vigiliaCardSurface(topicShape, MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (expandedTopic == index) "Contraer tema" else "Leer tema",
                    ) { expandedTopic = if (expandedTopic == index) null else index }
                    .semantics { stateDescription = if (expandedTopic == index) "Expandido" else "Contraído" }
                    .testTag("help-topic-$index"),
                shape = topicShape,
                border = cardBorder(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Column(
                    Modifier.padding(MiGuardiaSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(topic.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (expandedTopic == index) "−" else "+",
                            modifier = Modifier.padding(start = MiGuardiaSpacing.medium).clearAndSetSemantics {},
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (expandedTopic == index) {
                        HorizontalDivider()
                        Text(topic.body)
                    }
                }
            }
        }
        PrimaryAction(
            label = "Repetir recorrido inicial",
            onClick = actions.startReplay,
            enabled = canRepeat,
            modifier = Modifier.testTag("help-repeat-tour"),
        )
        Text(
            if (canRepeat) "La repetición no borra ni modifica tu progreso." else "La repetición se habilita al completar la primera configuración.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Los dos botones de un paso («Atrás» y el que avanza). Van lado a lado, repartiendo el ancho, mientras
 * cada rótulo entre entero en su mitad. Con la letra de Android muy grande la palabra más larga ya no
 * entra (visto: «Siguient/e» al 200 %): entonces se apilan a todo el ancho, en el mismo orden.
 */
@Composable
private fun HelpStepActions(
    startLabel: String,
    endLabel: String,
    spacing: Dp,
    start: @Composable (Modifier) -> Unit,
    end: @Composable (Modifier) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Relleno horizontal de un botón de Material (24 dp por lado) más un margen de redondeo.
        val chrome = with(density) { 52.dp.roundToPx() }
        val half = (constraints.maxWidth - with(density) { spacing.roundToPx() }) / 2
        fun fits(label: String): Boolean = label.split(' ').all { word ->
            measurer.measure(word, labelStyle, softWrap = false).size.width + chrome <= half
        }
        if (fits(startLabel) && fits(endLabel)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                start(Modifier.weight(1f))
                end(Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                start(Modifier.fillMaxWidth())
                end(Modifier.fillMaxWidth())
            }
        }
    }
}
