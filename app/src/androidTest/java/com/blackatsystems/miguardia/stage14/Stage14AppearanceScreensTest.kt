package com.blackatsystems.miguardia.stage14

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.ui.AppearanceScreen
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.theme.vigiliaSystemBarStyle
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Etapa 14 · Apariencia. Screen tests with an in-memory theme mode: the real theme preference is
 * never read or written, and the text size is simulated (the phone setting is not touched).
 */
class Stage14AppearanceScreensTest {
    @get:Rule
    val compose = createComposeRule()

    // A262
    @Test
    fun lightToDarkToggleSendsDarkAndUpdatesTheCurrentThemeText() {
        var mode by mutableStateOf(AppThemeMode.LIGHT)
        val received = mutableListOf<AppThemeMode>()
        compose.setContent {
            MiGuardiaTheme(darkTheme = mode.resolve(systemDarkTheme = false)) {
                AppearanceScreen(
                    contentPadding = PaddingValues(0.dp),
                    appThemeMode = mode,
                    onAppThemeModeChange = {
                        received += it
                        mode = it
                    },
                )
            }
        }

        compose.onNodeWithText("Tema actual: Claro").assertIsDisplayed()
        compose.onNodeWithText("Tema actual: Oscuro").assertDoesNotExist()
        compose.onNodeWithTag("theme-mode-toggle").performScrollTo().assertTextEquals("Usar modo oscuro").performClick()

        compose.runOnIdle { assertEquals(listOf(AppThemeMode.DARK), received) }
        compose.onNodeWithText("Tema actual: Oscuro").assertIsDisplayed()
        compose.onNodeWithText("Tema actual: Claro").assertDoesNotExist()
        // The same control now offers the way back.
        compose.onNodeWithTag("theme-mode-toggle").assertTextEquals("Usar modo claro")
    }

    // A262 (borde): the screen shows the mode it is given, not the one it asked for.
    @Test
    fun currentThemeTextStaysWhenTheChangeIsNotApplied() {
        val received = mutableListOf<AppThemeMode>()
        compose.setContent {
            MiGuardiaTheme(darkTheme = false) {
                AppearanceScreen(PaddingValues(0.dp), AppThemeMode.LIGHT) { received += it }
            }
        }
        compose.onNodeWithTag("theme-mode-toggle").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(AppThemeMode.DARK), received) }
        compose.onNodeWithText("Tema actual: Claro").assertIsDisplayed()
        compose.onNodeWithTag("theme-mode-toggle").assertTextEquals("Usar modo oscuro")
    }

    // A261, A262 (and A263 with a fixed, fictitious system value)
    @Test
    fun changingTheModeChangesThePageBackgroundOfTheRealTheme() {
        var mode by mutableStateOf(AppThemeMode.LIGHT)
        var resolvedBackground = Color.Unspecified
        val received = mutableListOf<AppThemeMode>()
        compose.setContent {
            // Same resolution MainActivity uses, with the system fixed to light for the test.
            MiGuardiaTheme(darkTheme = mode.resolve(systemDarkTheme = false)) {
                val page = MaterialTheme.colorScheme.background
                resolvedBackground = page
                Box(Modifier.fillMaxSize().background(page).testTag(PAGE)) {
                    AppearanceScreen(PaddingValues(0.dp), mode) {
                        received += it
                        mode = it
                    }
                }
            }
        }
        val lightPage = Color(vigiliaSystemBarStyle(darkTheme = false).backgroundArgb)
        val darkPage = Color(vigiliaSystemBarStyle(darkTheme = true).backgroundArgb)
        assertNotEquals(lightPage, darkPage)

        compose.runOnIdle { assertEquals(lightPage, resolvedBackground) }
        assertPagePixel(lightPage, "Claro")

        compose.onNodeWithTag("theme-mode-toggle").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(AppThemeMode.DARK), received)
            assertEquals(darkPage, resolvedBackground)
        }
        assertPagePixel(darkPage, "Oscuro")

        compose.onNodeWithTag("theme-mode-toggle").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(AppThemeMode.DARK, AppThemeMode.LIGHT), received)
            assertEquals(lightPage, resolvedBackground)
        }
        assertPagePixel(lightPage, "Claro otra vez")

        // Back to dark, then «Seguir el sistema» with a light system: the page turns light again.
        compose.onNodeWithTag("theme-mode-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("theme-mode-system").performScrollTo().assertIsEnabled().assertIsNotSelected().performClick()
        compose.runOnIdle {
            assertEquals(AppThemeMode.SYSTEM, received.last())
            assertEquals(lightPage, resolvedBackground)
        }
        compose.onNodeWithText("Tema actual: Seguir el sistema").assertIsDisplayed()
        compose.onNodeWithTag("theme-mode-system").assertIsSelected().assertIsNotEnabled()
        assertPagePixel(lightPage, "Seguir el sistema (claro)")
    }

    // A264 + criterio visual de tema y letra en Apariencia
    @Test
    fun everyControlStaysReachableWithWholeTextAtEveryFontScaleAndTheme() {
        var dark by mutableStateOf(false)
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        var mode by mutableStateOf(AppThemeMode.LIGHT)
        val received = mutableListOf<AppThemeMode>()
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AppearanceScreen(PaddingValues(0.dp), mode) { received += it }
                }
            }
        }
        val problems = mutableListOf<String>()
        for (theme in listOf(false, true)) for (scale in TestFontScale.entries) {
            val current = if (theme) AppThemeMode.DARK else AppThemeMode.LIGHT
            compose.runOnIdle {
                dark = theme
                fontScale = scale
                mode = current
                received.clear()
            }
            val context = "${current.label} ${scale.percent}%"
            val toggleLabel = if (theme) "Usar modo claro" else "Usar modo oscuro"
            listOf(
                "Apariencia",
                APPEARANCE_INTRO,
                "Tema de MiGuardia",
                "Alterná entre claro y oscuro, o dejá que Android elija.",
                "Tema actual: ${current.label}",
            ).forEach { text ->
                val node = compose.onNode(hasText(text), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                problems += (node.fetchSemanticsNode().stage14ClipProblems("«$text»") +
                    node.stage14TextLayout().stage14WholeTextProblems()).map { "$context: $it" }
            }
            problems += compose.stage14ButtonLabelProblems("theme-mode-toggle", toggleLabel).map { "$context: $it" }
            problems += compose.stage14ButtonLabelProblems("theme-mode-system", "Seguir el sistema").map { "$context: $it" }

            compose.onNodeWithTag("theme-mode-toggle").performScrollTo().assertIsEnabled().performClick()
            compose.onNodeWithTag("theme-mode-system").performScrollTo().assertIsEnabled().performClick()
            val expected = listOf(if (theme) AppThemeMode.LIGHT else AppThemeMode.DARK, AppThemeMode.SYSTEM)
            compose.runOnIdle { assertEquals(context, expected, received.toList()) }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** Reads one drawn pixel of the page, away from the content. Nothing is written to disk. */
    private fun assertPagePixel(expected: Color, context: String) {
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag(PAGE).captureToImage().toPixelMap()
        val actual = pixels[pixels.width / 2, pixels.height - 2]
        val close = abs(actual.red - expected.red) < CHANNEL_TOLERANCE &&
            abs(actual.green - expected.green) < CHANNEL_TOLERANCE &&
            abs(actual.blue - expected.blue) < CHANNEL_TOLERANCE
        assertTrue("$context: page drawn as $actual, expected $expected", close)
    }

    private companion object {
        const val PAGE = "stage14-appearance-page"
        const val CHANNEL_TOLERANCE = 0.02f

        // app/src/main/res/values/strings.xml, appearance_intro.
        const val APPEARANCE_INTRO = "Elegí el tema de MiGuardia. El tamaño de letra sigue los ajustes de Android."
    }
}
