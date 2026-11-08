package com.blackatsystems.miguardia.stage12

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.stage12.Stage12CalculationFixture as F
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Etapa 12.2, segunda tanda: editores de «Tus horas y tu meta», clases de extra y extra independiente,
 * sobre la app real y una base propia de la corrida sembrada con [Stage12CalculationFixture].
 * «Hoy» es el 30/9/2026 (reloj anclado del arnés).
 */
class Stage12HoursEditorsDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var graph: Stage12CalculationStore
    private val store get() = graph.store

    @Before fun openIsolatedApp() {
        val context = instrumentation.targetContext
        check(context.packageName == "com.blackatsystems.miguardia.qa") { "Sólo en el paquete QA" }
        graph = Stage12CalculationStore(context, UUID.randomUUID())
        runBlocking { graph.seed() }
        compose.setContent { AndroidFontScaleTheme { graph.Host() } }
        waitTag("month-grid")
    }

    @After fun closeIsolatedApp() {
        runCatching { compose.runOnUiThread { compose.activity.viewModelStore.clear() } }
        compose.waitForIdle()
        if (::graph.isInitialized) graph.close()
    }

    /** A090, A093, A094, A096: semana con primer día elegido, desde hoy, corte del tramo anterior y guardado. */
    @Test fun weeklyReferenceFromTodayCutsThePreviousSegmentAndSaves() {
        openReferenceEditor()
        click("La cantidad es fija")
        click("Semana")
        click("miércoles")
        replaceText("hours-fixed-minutes", "600")
        click("Desde hoy (2026-09-30)")
        clickTag("hours-reference-review")
        waitText("Período: Semanal, empieza el miércoles", substring = true, optional = true)
        waitText("El tramo anterior termina: 29 de septiembre de 2026", substring = true)
        waitText("El tramo nuevo comienza: 30 de septiembre de 2026", substring = true)
        waitText("El primer tramo coincide con el límite elegido.")
        clickTag("hours-reference-save")
        waitFor { history().timeline.revisions.size == 2 }
        val revisions = history().timeline.revisions
        assertEquals("El tramo anterior se conserva", F.configuration.timeline.revisions.single(), revisions.first())
        val saved = revisions.last()
        assertEquals(LocalDate.of(2026, 9, 30), saved.effectiveFrom)
        val reference = saved.value.hoursReference as HoursReference.Fixed
        assertEquals(HoursPeriod.Weekly(DayOfWeek.WEDNESDAY), reference.period)
        assertEquals(600L, reference.requiredMinutes.value)
        waitTag("hours-progress-content")
        waitText("Te faltan")
        waitText("10 h")
    }

    /** A091, A093, A095: ciclo con anclaje, fecha propia pasada y tramo corto exigen confirmar antes de guardar. */
    @Test fun cycleFromAPastDateRequiresBothConfirmationsBeforeSaving() {
        openReferenceEditor()
        click("La cantidad es fija")
        click("Ciclo")
        replaceText("hours-cycle-length", "14")
        replaceText("hours-cycle-anchor", "2026-09-30")
        replaceText("hours-fixed-minutes", "4200")
        click("Elegir otra fecha")
        replaceText("hours-custom-start", "2026-09-20")
        clickTag("hours-reference-review")
        waitText("Confirmo recalcular los tramos desde la fecha mostrada.")
        waitText("Confirmo usar la meta completa aunque el primer tramo sea más corto.")
        clickTag("hours-reference-save")
        compose.waitForIdle()
        Thread.sleep(1_000)
        assertEquals("Sin confirmar no se guarda", 1, history().timeline.revisions.size)
        click("Confirmo recalcular los tramos desde la fecha mostrada.")
        click("Confirmo usar la meta completa aunque el primer tramo sea más corto.")
        clickTag("hours-reference-save")
        waitFor { history().timeline.revisions.size == 2 }
        val saved = history().timeline.revisions.last()
        assertEquals(LocalDate.of(2026, 9, 20), saved.effectiveFrom)
        val reference = saved.value.hoursReference as HoursReference.Fixed
        assertEquals(HoursPeriod.Cycle(LocalDate.of(2026, 9, 30), 14), reference.period)
        assertEquals(4200L, reference.requiredMinutes.value)
    }

    /** A097, A098: meta por período sin valor, informarla y corregirla sólo con confirmación expresa. */
    @Test fun perPeriodValueIsReportedThenCorrectedOnlyWithConfirmation() {
        openReferenceEditor()
        click("La cantidad cambia en cada período")
        click("Mes calendario")
        click("Desde hoy (2026-09-30)")
        clickTag("hours-reference-review")
        waitText("Confirmo usar la meta completa aunque el primer tramo sea más corto.")
        click("Confirmo usar la meta completa aunque el primer tramo sea más corto.")
        clickTag("hours-reference-save")
        waitFor { history().timeline.revisions.size == 2 }
        assertTrue(history().timeline.revisions.last().value.hoursReference is HoursReference.PerPeriod)
        assertTrue("Sin valor no se inventa una meta", history().perPeriodHoursValues.entries.isEmpty())

        waitTag("hours-progress-content")
        expandAdvanced("hours-progress-content")
        waitText("Informar meta de este período")
        clickTag("hours-period-value-edit")
        replaceText("hours-period-value-minutes", "900")
        clickTag("hours-period-value-save")
        waitFor { history().perPeriodHoursValues.entries.size == 1 }
        val first = history().perPeriodHoursValues.entries.single()
        assertEquals(900L, first.requiredMinutes.value)

        waitTag("hours-progress-content")
        expandAdvanced("hours-progress-content")
        waitText("Corregir meta de este período")
        clickTag("hours-period-value-edit")
        waitText("Valor actual: 15 h", substring = true)
        replaceText("hours-period-value-minutes", "1000")
        clickTag("hours-period-value-save")
        compose.waitForIdle()
        Thread.sleep(1_000)
        assertEquals("Sin confirmar no se corrige", 900L, history().perPeriodHoursValues.entries.single().requiredMinutes.value)
        click("Confirmo corregir sólo este período sin cambiar su identidad.")
        clickTag("hours-period-value-save")
        waitFor { history().perPeriodHoursValues.entries.single().requiredMinutes.value == 1000L }
        assertEquals("La corrección conserva la identidad", first.id, history().perPeriodHoursValues.entries.single().id)
    }

    /** A078–A084: crear por sugerencia, ayuda de ambas decisiones, editar sin reinterpretar, archivar y reactivar. */
    @Test fun classCatalogCreatesEditsArchivesAndReactivatesWithoutRewritingHistory() {
        openClassCatalog()
        clickTag("v2-extra-class-new")
        click("Servicio extra")
        waitFor { textOf("v2-extra-class-name") == "Servicio extra" }
        waitText("Faltan 2 respuestas en Opciones avanzadas.")
        expandAdvanced(null)
        clickTag("context-help-cuenta-para-tu-meta")
        waitText("Meta 160 h", substring = true)
        click("Entendido", optional = true) || click("Cerrar")
        clickTag("context-help-ver-este-tipo-por-separado")
        waitText("Con Sí: Servicio extra, 8 h.", substring = true)
        click("Entendido", optional = true) || click("Cerrar")
        clickTag("v2-extra-class-helps-no")
        clickTag("v2-extra-class-dedicated-sí")
        clickTag("v2-extra-class-save")
        waitFor { classes().any { it.name == "Servicio extra" } }
        val created = classes().single { it.name == "Servicio extra" }
        assertFalse(created.helpsMeetHoursReference)
        assertTrue(created.showDedicatedSummary)
        assertTrue(created.isActive)

        // Editar la clase usada por el extra de la jornada del 7/9: el pasado conserva su fotografía.
        compose.onNode(hasText("Editar") and hasParent(hasAnyChild(hasTestTag("v2-extra-class-toggle-${F.HELPING_CLASS_ID}"))))
            .performScrollTo().performClick()
        replaceText("v2-extra-class-name", "Refuerzo renombrado")
        clickTag("v2-extra-class-save")
        waitFor { classes().single { it.id == F.HELPING_CLASS_ID }.name == "Refuerzo renombrado" }
        val actual = runBlocking { checkNotNull(store.shiftActuals.getExpectation(F.DAY_SHIFT_ID)?.previousActual) }
        assertEquals("El extra guardado no se reinterpreta", F.HELPING_CLASS_NAME, actual.extraIntervals.single().classNameSnapshot)
        assertTrue(actual.extraIntervals.single().helpsMeetHoursReferenceSnapshot)

        clickTag("v2-extra-class-toggle-${F.NOT_HELPING_CLASS_ID}")
        waitFor { !classes().single { it.id == F.NOT_HELPING_CLASS_ID }.isActive }
        val extra = runBlocking { checkNotNull(store.independentExtraWork.get(F.INDEPENDENT_EXTRA_ID)) }
        assertEquals("Archivar no toca el extra existente", F.NOT_HELPING_CLASS_NAME, extra.snapshot.className)
        waitText("Reactivar")
        clickTag("v2-extra-class-toggle-${F.NOT_HELPING_CLASS_ID}")
        waitFor { classes().single { it.id == F.NOT_HELPING_CLASS_ID }.isActive }
    }

    /**
     * A100–A102, A105–A107, A109: sin alta para fechas futuras; extra elegido completo que cruza la medianoche
     * y dura más de 24 h; superposición confirmada; duración y meta en la revisión; borrar sólo ese extra.
     */
    @Test fun independentExtraAcrossMidnightNeedsOverlapConfirmationAndDeletesOnlyItself() {
        calendarNext()
        openDay(LocalDate.of(2026, 10, 1))
        assertTrue(
            "Una fecha futura no ofrece registrar extra",
            compose.onAllNodesWithTag("register-independent-extra-2026-10-01").fetchSemanticsNodes().isEmpty() &&
                runCatching { clickTag("day-other-options") }.let {
                    compose.onAllNodesWithTag("register-independent-extra-2026-10-01").fetchSemanticsNodes().isEmpty()
                },
        )
        pressBack()
        calendarPrevious()

        val date = F.NIGHT_SHIFT_DATE
        openDay(date)
        clickTag("day-other-options")
        clickTag("register-independent-extra-$date")
        waitTag("extra-review")
        clickTag("extra-place-${F.WORK_PLACE_ID}")
        clickTag("extra-type-${F.WORK_TYPE_ID}")
        click("Sin plantilla")
        replaceText("extra-start-time", "20:00")
        replaceText("extra-end-date", "2026-09-16")
        replaceText("extra-end-time", "01:00")
        clickTag("extra-class-${F.HELPING_CLASS_ID}")
        clickTag("extra-color")
        clickTag("common-color-rojo")
        click("Usar color")
        replaceText("extra-position", "Cobertura 12.2")
        clickTag("extra-review")
        waitTag("extra-save")
        waitText("Duración: 29 h")
        waitText("Clase: ${F.HELPING_CLASS_NAME}")
        waitText("Cuenta para tu meta")
        waitText("Hay trabajos activos superpuestos. Confirmo conservarlos y sumar cada uno completo.")
        clickTag("extra-save")
        compose.waitForIdle()
        Thread.sleep(1_000)
        assertEquals("Sin confirmar la superposición no se guarda", 1, extras().size)
        click("Hay trabajos activos superpuestos. Confirmo conservarlos y sumar cada uno completo.")
        clickTag("extra-save")
        waitFor { extras().size == 2 }
        val created = extras().single { it.id != F.INDEPENDENT_EXTRA_ID }
        assertEquals(F.local(date, LocalTime.of(20, 0)), created.start)
        assertEquals(F.local(LocalDate.of(2026, 9, 16), LocalTime.of(1, 0)), created.end)
        assertEquals(29L * 60L, created.durationMinutes)
        assertEquals(F.HELPING_CLASS_NAME, created.snapshot.className)
        assertEquals("Cobertura 12.2", created.snapshot.position)

        val deleteTag = "delete-independent-extra-${created.id}"
        // Al guardar, la app vuelve al detalle del día con sus opciones abiertas: no se vuelven a tocar.
        compose.waitForIdle()
        if (!exists(deleteTag)) {
            if (!exists("day-detail-modal-sheet")) openDay(date)
            if (!exists(deleteTag)) clickTag("day-other-options")
        }
        clickTag(deleteTag)
        clickTag("extra-delete-confirm")
        waitFor { extras().size == 1 }
        assertTrue("La nocturna del día sigue", runBlocking {
            store.v2Shifts.getShift(F.NIGHT_SHIFT_ID) is com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup.V2
        })
        assertEquals(F.INDEPENDENT_EXTRA_ID, extras().single().id)
    }

    /** A104 y A106: crear la clase que falta sin perder el borrador; fecha con carpeta médica pide confirmar. */
    @Test fun missingClassIsCreatedKeepingTheDraftAndProtectedDateNeedsConfirmation() {
        val date = F.MEDICAL_DATE
        openDay(date)
        clickTag("day-other-options")
        clickTag("register-independent-extra-$date")
        waitTag("extra-review")
        click("Sin plantilla")
        replaceText("extra-start-time", "09:00")
        replaceText("extra-end-time", "11:00")
        replaceText("extra-position", "Borrador conservado")
        // Las clases dejan de estar utilizables fuera de este borrador (como si se archivaran en otra pantalla).
        runBlocking {
            listOf(F.HELPING_CLASS_ID, F.NOT_HELPING_CLASS_ID).forEach { id ->
                val current = classes().single { it.id == id }
                store.shiftActuals.saveExtraWorkClass(current, current.updated(isActive = false, timestamp = java.time.Instant.now()))
            }
        }
        clickTag("extra-open-class-catalog")
        clickTag("v2-extra-class-new")
        replaceText("v2-extra-class-name", "Clase nueva 12.2")
        expandAdvanced(null)
        clickTag("v2-extra-class-helps-sí")
        clickTag("v2-extra-class-dedicated-no")
        clickTag("v2-extra-class-save")
        waitFor { classes().any { it.name == "Clase nueva 12.2" } }
        val created = classes().single { it.name == "Clase nueva 12.2" }
        pressBack()
        waitTag("extra-position")
        waitFor { textOf("extra-position") == "Borrador conservado" }
        clickTag("extra-class-${created.id}")
        clickTag("extra-color")
        clickTag("common-color-rojo")
        click("Usar color")
        clickTag("extra-review")
        waitText("Hay carpeta médica o vacaciones. Confirmo conservar el trabajo real sin borrar la protección.")
        clickTag("extra-save")
        compose.waitForIdle()
        Thread.sleep(1_000)
        assertEquals("Sin confirmar la protección no se guarda", 1, extras().size)
        click("Hay carpeta médica o vacaciones. Confirmo conservar el trabajo real sin borrar la protección.")
        clickTag("extra-save")
        waitFor { extras().size == 2 }
        val saved = extras().single { it.id != F.INDEPENDENT_EXTRA_ID }
        assertEquals("Clase nueva 12.2", saved.snapshot.className)
        assertEquals("Borrador conservado", saved.snapshot.position)
        assertEquals(120L, saved.durationMinutes)
        val leave = runBlocking { store.medicalLeaves.observeIntersecting(date, date).first().single() }
        assertEquals("La carpeta médica no se borra", F.MEDICAL_LEAVE_ID, leave.id)
    }

    // ---- navegación ----

    private fun openHours() {
        clickTag("main-menu-button")
        clickTag("drawer-action-work-setup")
        waitTag("work-setup-overview")
        compose.onNode(hasTestTag("advanced-options-toggle") and hasAnyAncestor(hasTestTag("work-setup-overview")))
            .performScrollTo().performClick()
        clickTag("work-setup-hours-progress")
        waitTag("hours-progress-content")
    }

    private fun openReferenceEditor() {
        openHours()
        expandAdvanced("hours-progress-content")
        clickTag("hours-reference-configure")
        waitTag("hours-reference-review")
    }

    private fun openClassCatalog() {
        clickTag("main-menu-button")
        clickTag("drawer-action-work-setup")
        waitTag("work-setup-overview")
        compose.onNode(hasTestTag("advanced-options-toggle") and hasAnyAncestor(hasTestTag("work-setup-overview")))
            .performScrollTo().performClick()
        clickTag("work-setup-extra-classes")
        waitTag("v2-extra-class-new")
    }

    private fun expandAdvanced(scopeTag: String?) {
        val matcher = if (scopeTag == null) hasTestTag("advanced-options-toggle")
        else hasTestTag("advanced-options-toggle") and hasAnyAncestor(hasTestTag(scopeTag))
        waitFor { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(matcher)[0].performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun openDay(date: LocalDate) {
        waitFor {
            compose.onAllNodesWithTag("day-$date").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("completed-day-$date").fetchSemanticsNodes().isNotEmpty()
        }
        val tag = if (compose.onAllNodesWithTag("day-$date").fetchSemanticsNodes().isNotEmpty()) "day-$date" else "completed-day-$date"
        compose.onNodeWithTag(tag).performClick()
        waitTag("day-detail-modal-sheet")
    }

    private fun calendarNext() = clickDescription("Mes siguiente")
    private fun calendarPrevious() = clickDescription("Mes anterior")

    private fun clickDescription(value: String) {
        val matcher = SemanticsMatcher("descripción «$value»") { node ->
            node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.contains(value) == true
        } and hasAnyAncestor(hasTestTag("calendar-month-controls"))
        waitFor { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(matcher).performClick()
        compose.waitForIdle()
    }

    private fun pressBack() {
        androidx.test.uiautomator.UiDevice.getInstance(instrumentation).pressBack()
        compose.waitForIdle()
    }

    // ---- lecturas de la base de la corrida ----

    private fun history(): WorkConfigurationHistory = runBlocking { checkNotNull(store.workConfiguration.get()) }
    private fun classes(): List<ExtraWorkClass> =
        runBlocking { store.shiftActuals.observeExtraWorkClasses(F.TIMELINE_ID, F.SECTOR).first() }
    private fun extras(): List<IndependentExtraWorkRecord> =
        runBlocking { store.independentExtraWork.observeAll(F.TIMELINE_ID, F.SECTOR).first() }

    // ---- acciones y esperas ----

    private fun clickTag(tag: String) {
        waitTag(tag)
        runCatching { compose.onNodeWithTag(tag).performScrollTo() }
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    /** Toca el nodo cuyo texto es exactamente [text]; con [optional] devuelve false si no aparece. */
    private fun click(text: String, optional: Boolean = false): Boolean {
        val found = runCatching { waitFor(if (optional) 3_000 else WAIT) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() } }
        if (found.isFailure) { if (optional) return false; throw found.exceptionOrNull()!! }
        val node = compose.onAllNodes(hasText(text))[0]
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
        return true
    }

    private fun replaceText(tag: String, value: String) {
        waitTag(tag)
        compose.onNodeWithTag(tag).performScrollTo()
        compose.onNodeWithTag(tag).performTextClearance()
        compose.onNodeWithTag(tag).performTextInput(value)
        compose.waitForIdle()
    }

    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun textOf(tag: String): String? = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrElseNullable(SemanticsProperties.EditableText) { null }?.text

    private fun waitText(text: String, substring: Boolean = false, optional: Boolean = false) {
        val result = runCatching {
            waitFor(if (optional) 3_000 else WAIT) {
                compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
            }
        }
        if (result.isFailure && !optional) throw result.exceptionOrNull()!!
    }

    private fun waitTag(tag: String) = waitFor { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun waitFor(timeout: Long = WAIT, condition: () -> Boolean) {
        try {
            compose.waitUntil(timeout, condition)
        } catch (error: Throwable) {
            if (timeout >= WAIT) runCatching { dumpScreen() }
            throw error
        }
    }

    /** Diagnóstico sólo ante una espera vencida: árbol de la pantalla con datos ficticios. */
    private fun dumpScreen() {
        val where = Throwable().stackTrace.firstOrNull { it.methodName != "dumpScreen" && it.methodName != "waitFor" &&
            it.className.endsWith("Stage12HoursEditorsDeviceTest") }?.lineNumber ?: -1
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes().size
        for (index in 0 until roots) {
            compose.onAllNodes(isRoot())[index].printToString(Int.MAX_VALUE).chunked(3_000)
                .forEachIndexed { part, chunk -> Log.i(LOG_TAG, "dump[$where] root=$index part=$part: $chunk") }
        }
    }

    private companion object {
        const val WAIT = 30_000L
        const val LOG_TAG = "MiGuardia122"
    }
}
