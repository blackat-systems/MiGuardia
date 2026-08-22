package com.blackatsystems.miguardia

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ManagementActivityRecreationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun wakeDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        UiDevice.getInstance(instrumentation).wakeUp()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
        ensureMigratedV1ActivityFixture(context)
        val dataStore = (context.applicationContext as MiGuardiaApplication).localDataStore
        runBlocking {
            dataStore.objectives.delete(OBJECTIVE.id)
            dataStore.objectives.create(OBJECTIVE)
            dataStore.scheduleCombinations.create(SCHEDULE)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("Calendario").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun removeFixture() {
        scenario?.close()
        scenario = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dataStore = (context.applicationContext as MiGuardiaApplication).localDataStore
        runBlocking { dataStore.objectives.delete(OBJECTIVE.id) }
    }

    @Test
    fun objectiveDraftSurvivesActivityRecreation() {
        composeRule.onNodeWithContentDescription("Abrir menú").performClick()
        composeRule.onNodeWithText("Objetivos y horarios").performClick()
        composeRule.onNodeWithText("Crear objetivo").performClick()
        composeRule.onNodeWithText("Nombre completo").performTextInput("Objetivo de recreación")

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val title = checkNotNull(
            UiDevice.getInstance(instrumentation).wait(Until.findObject(By.text("Objetivo")), 5_000L),
        ) { "No se encontró el título Objetivo dentro del tiempo esperado." }
        val titleBounds = title.visibleBounds
        val statusBarResource = instrumentation.targetContext.resources
            .getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = instrumentation.targetContext.resources.getDimensionPixelSize(statusBarResource)
        assertTrue("El título no debe solaparse con la barra de estado.", titleBounds.top >= statusBarHeight)

        requireNotNull(scenario).recreate()

        composeRule.onNodeWithText("Objetivo de recreación").assertExists()
        composeRule.onNodeWithText("Guardar objetivo").assertExists()
    }

    @Test
    fun progressiveShiftStageDatesAndOptionalPositionSurviveActivityRecreation() {
        val today = LocalDate.now(AppDefaults.zoneId())
        val month = YearMonth.from(today)
        val selectedDate = today
        val hasInitialDataEntry = composeRule.onAllNodesWithText("Cargar datos")
            .fetchSemanticsNodes().isNotEmpty()
        if (hasInitialDataEntry) {
            composeRule.onNodeWithText("Cargar datos").performScrollTo().performClick()
            composeRule.onNodeWithText("Continuar y elegir días").performScrollTo().performClick()
        } else {
            composeRule.onNodeWithText("Editar calendario").performScrollTo().performClick()
        }
        composeRule.onNodeWithContentDescription(selectedDate.spanishDisplayName(), substring = true)
            .performClick()
        composeRule.onNodeWithText("Terminar de elegir días").performScrollTo().performClick()
        composeRule.onNodeWithText("¿Qué querés cargar?").assertExists()

        requireNotNull(scenario).recreate()

        composeRule.onNodeWithText("¿Qué querés cargar?").assertExists()
        composeRule.onNodeWithContentDescription(selectedDate.spanishDisplayName(), substring = true)
            .assertIsSelected()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Agregar guardia").performClick()

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("Objetivo recreación (REC)").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Elegir otro objetivo u horario").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText("Elegir otro objetivo u horario").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Elegir otro objetivo u horario").performScrollTo().performClick()
        }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("Objetivo recreación (REC)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Objetivo recreación (REC)").performScrollTo().performClick()
        composeRule.onNodeWithText("18:37–06:23").performScrollTo().performClick()
        composeRule.onNodeWithText("+ Agregar puesto opcional").performScrollTo().performClick()
        composeRule.onNodeWithText("Puesto opcional").performTextInput("Puesto ficticio recreado")
        composeRule.onNodeWithTag("selected-combination-summary").assertExists()
        composeRule.onNodeWithTag("shift-preview").assertExists()

        requireNotNull(scenario).recreate()

        composeRule.onNodeWithTag("month-grid").assertExists()
        composeRule.onNodeWithTag("calendar-inline-management").assertExists()
        composeRule.onNodeWithTag("calendar-edit-tools").assertDoesNotExist()
        composeRule.onNodeWithText("Herramientas de edición").assertDoesNotExist()
        composeRule.onNodeWithText("Elegí uno o varios días").assertDoesNotExist()
        composeRule.onNodeWithTag("calendar-edit-selection-count").assertDoesNotExist()
        composeRule.onNodeWithContentDescription(selectedDate.spanishDisplayName(), substring = true)
            .assertIsSelected()
        composeRule.onNodeWithTag("selected-combination-summary").assertExists()
        composeRule.onNodeWithText("REC · Objetivo recreación").assertExists()
        composeRule.onNodeWithText("Elegí objetivo y horario").assertDoesNotExist()
        composeRule.onNodeWithText("+ Agregar horario").assertDoesNotExist()
        composeRule.onNodeWithText("Modificar días elegidos").performScrollTo().assertExists()
        composeRule.onNodeWithTag("optional-position-field").performScrollTo().assertExists()
        composeRule.onNodeWithText("Puesto ficticio recreado").assertExists()
        composeRule.onNodeWithTag("shift-preview").assertExists()
        composeRule.onAllNodesWithText("Puesto: Puesto ficticio recreado").assertCountEquals(1)

        val lockedDate = when {
            selectedDate.dayOfMonth < month.lengthOfMonth() -> selectedDate.plusDays(1)
            else -> selectedDate.minusDays(1)
        }
        composeRule.onNodeWithContentDescription(lockedDate.spanishDisplayName(), substring = true)
            .assertIsNotEnabled()
            .assertIsNotSelected()
        composeRule.onNodeWithContentDescription(selectedDate.spanishDisplayName(), substring = true)
            .assertIsSelected()

        composeRule.onNodeWithTag("review-shift").performScrollTo().performClick()
        composeRule.onNodeWithText("Confirmar guardia").assertExists()
        composeRule.onAllNodesWithText("Fechas: ${selectedDate.format(EXACT_DATE_FORMATTER)}")
            .assertCountEquals(2)
        composeRule.onAllNodesWithText("Puesto: Puesto ficticio recreado").assertCountEquals(2)
    }

    private fun LocalDate.spanishDisplayName(): String = format(FULL_DATE_FORMATTER)
        .replaceFirstChar { it.titlecase(SPANISH_ARGENTINA) }

    private companion object {
        val SPANISH_ARGENTINA: Locale = Locale.forLanguageTag("es-AR")
        val FULL_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", SPANISH_ARGENTINA)
        val EXACT_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
        val OBJECTIVE = Objective(
            id = UUID.fromString("92000000-0000-0000-0000-000000000001"),
            fullName = "Objetivo recreación",
            abbreviation = "REC",
            address = null,
            note = null,
            isActive = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val SCHEDULE = ScheduleCombination(
            id = UUID.fromString("92000000-0000-0000-0000-000000000002"),
            objectiveId = OBJECTIVE.id,
            startTime = LocalTime.of(18, 37),
            endTime = LocalTime.of(6, 23),
            colorArgb = 0xFF315DA8.toInt(),
            isActive = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
