package com.blackatsystems.miguardia.stage13

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.stage12.Stage12CalculationStore
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule

/**
 * Etapa 13 en el teléfono: la app real (MiGuardiaApp, ViewModels, Room, Copias) sobre una base y una
 * carpeta propias de cada corrida (`stage13-<uuid>`), con la fixture de 12.2. Nunca abre ni modifica los
 * datos QA: Copias usa el coordinador de la corrida y los selectores los contesta la prueba ([pickers]).
 */
abstract class Stage13DeviceBase {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    protected val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    protected val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    internal lateinit var graph: Stage12CalculationStore
    internal val pickers = Stage13Pickers()

    /** true: selectores reales de la Activity (sesión humana); false: los contesta la prueba. */
    internal open val useRealPickers: Boolean = false

    /** Datos extra de cada clase, después de la fixture común. */
    internal open suspend fun prepare() {}

    @Before fun openIsolatedApp() {
        val context = instrumentation.targetContext
        check(context.packageName == "com.blackatsystems.miguardia.qa") { "Sólo en el paquete QA" }
        graph = Stage12CalculationStore(context, UUID.randomUUID(), tagPrefix = "stage13",
            allowedEffects = setOf("photos.import"))
        runBlocking {
            graph.seed()
            prepare()
        }
        compose.setContent { AndroidFontScaleTheme { graph.Host(if (useRealPickers) null else pickers) } }
        waitTag("month-grid")
    }

    @After fun closeIsolatedApp() {
        // Primero se detienen los ViewModels de la pantalla: si siguen vivos pueden reabrir la base ya cerrada.
        runCatching { compose.runOnUiThread { compose.activity.viewModelStore.clear() } }
        compose.waitForIdle()
        if (::graph.isInitialized) graph.close()
        assertEquals("Selectores lanzados sin respuesta preparada", emptyList<Stage13Pickers.Kind>(), pickers.unexpected.toList())
        assertEquals("Respuestas de selector sin usar", 0, pickers.pending)
    }

    protected fun openDrawerAction(tag: String) {
        compose.onNodeWithTag("main-menu-button").performClick()
        waitTag(tag)
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    protected fun openDay(date: LocalDate) {
        waitFor {
            compose.onAllNodesWithTag("day-$date").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("completed-day-$date").fetchSemanticsNodes().isNotEmpty()
        }
        val tag = if (compose.onAllNodesWithTag("day-$date").fetchSemanticsNodes().isNotEmpty()) "day-$date" else "completed-day-$date"
        compose.onNodeWithTag(tag).performClick()
        waitTag("day-detail-modal-sheet")
    }

    protected fun assertCalendarMonth(label: String) = waitFor {
        compose.onAllNodes(hasText(label) and hasAnyAncestor(hasTestTag("calendar-month-controls"))).fetchSemanticsNodes().isNotEmpty()
    }

    /** Nodo tocable único con ese texto o descripción exactos; [scope] limita la búsqueda. */
    protected fun clickable(label: String, scope: SemanticsMatcher? = null): SemanticsNodeInteraction {
        val base = (hasText(label) or hasContentDescription(label)) and hasClickAction()
        val matcher = if (scope == null) base else base and scope
        waitFor { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNode(matcher)
    }

    /** Espera a que el nodo exista Y esté habilitado (p. ej. después de guardar) y lo toca. */
    protected fun tap(label: String, scope: SemanticsMatcher? = null, scroll: Boolean = false) {
        val base = (hasText(label) or hasContentDescription(label)) and hasClickAction() and isEnabled()
        val matcher = if (scope == null) base else base and scope
        closeKeyboard()
        waitFor { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(matcher).let { if (scroll) it.performScrollTo() else it }.performClick()
    }

    /** En el teléfono el teclado queda abierto tras escribir y tapa la parte baja: se cierra antes de tocar. */
    protected fun closeKeyboard() {
        runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
        compose.waitForIdle()
    }

    protected fun clickInDialog(label: String) = tap(label, hasAnyAncestor(isDialog()))

    /** Fila o botón con acción de toque cuyo rótulo accesible de clic es [label] (p. ej. «Ver imagen 1»). */
    protected fun hasClickLabel(label: String) = SemanticsMatcher("clic «$label»") { node ->
        node.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == label
    }

    protected fun hasTagPrefix(prefix: String) = SemanticsMatcher("etiqueta $prefix*") { node ->
        node.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true
    }

    protected fun waitText(text: String, scope: SemanticsMatcher? = null, substring: Boolean = false) = waitFor {
        val matcher = hasText(text, substring = substring)
        compose.onAllNodes(if (scope == null) matcher else matcher and scope).fetchSemanticsNodes().isNotEmpty()
    }

    protected fun waitGone(text: String) = waitFor {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()
    }

    protected fun waitTag(tag: String) = waitFor { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    protected fun exists(matcher: SemanticsMatcher): Boolean = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    protected fun waitFor(condition: () -> Boolean) {
        try {
            compose.waitUntil(WAIT, condition)
        } catch (error: Throwable) {
            runCatching { dumpScreen(Throwable().stackTrace.getOrNull(2)?.lineNumber?.toString() ?: "?") }
            throw error
        }
    }

    /** Diagnóstico sólo ante una espera vencida: árbol de la pantalla (datos ficticios) por logcat. */
    protected fun dumpScreen(where: String) {
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes().size
        for (index in 0 until roots) {
            compose.onAllNodes(isRoot())[index].printToString(Int.MAX_VALUE)
                .chunked(3_000).forEachIndexed { part, chunk -> Log.i(LOG_TAG, "dump[$where] root=$index part=$part: $chunk") }
        }
    }

    protected companion object {
        const val WAIT = 30_000L
        const val LOG_TAG = "MiGuardia13"
    }
}
