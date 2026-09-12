package com.blackatsystems.miguardia

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.blackatsystems.miguardia.ui.components.AdvancedOptionsSection
import com.blackatsystems.miguardia.ui.components.ContextHelp
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import org.junit.Rule
import org.junit.Test

class ProgressiveDisclosureComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun advancedContentStartsHiddenAndHelpExplainsWhatHowAndExample() {
        compose.setContent {
            MiGuardiaTheme {
                AdvancedOptionsSection(
                    help = ContextHelp(
                        title = "Configuración ficticia",
                        whatItDoes = "Cambia un detalle ficticio.",
                        howToUseIt = "Elegí la opción que corresponda.",
                        example = "Por ejemplo, una elección ficticia.",
                    ),
                ) {
                    Text("Control avanzado ficticio", Modifier.testTag("advanced-control"))
                }
            }
        }

        compose.onNodeWithTag("advanced-control").assertDoesNotExist()
        compose.onNodeWithContentDescription("Ayuda sobre Configuración ficticia").performClick()
        compose.onNodeWithTag("context-help-dialog").assertIsDisplayed()
        compose.onNodeWithText("Qué hace").assertIsDisplayed()
        compose.onNodeWithText("Cómo usarlo").assertIsDisplayed()
        compose.onNodeWithText("Ejemplo").assertIsDisplayed()
        compose.onNodeWithText("Entendido").performClick()
        compose.onNodeWithTag("advanced-options-toggle").performClick()
        compose.onNodeWithTag("advanced-control").assertIsDisplayed()
    }
}
