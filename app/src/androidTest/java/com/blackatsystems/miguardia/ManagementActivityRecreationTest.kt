package com.blackatsystems.miguardia

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ManagementActivityRecreationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun wakeDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        UiDevice.getInstance(instrumentation).wakeUp()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
    }

    @Test
    fun objectiveDraftSurvivesActivityRecreation() {
        composeRule.onNodeWithText("Configuración").performClick()
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

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("Objetivo de recreación").assertExists()
        composeRule.onNodeWithText("Guardar objetivo").assertExists()
    }
}
