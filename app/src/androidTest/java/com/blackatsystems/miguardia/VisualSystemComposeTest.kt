package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SurfaceHeader
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import org.junit.Rule
import org.junit.Test

class VisualSystemComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun headingsAndPersistentErrorsExposeTheirMeaningToAccessibilityServices() {
        compose.setContent {
            MiGuardiaTheme {
                Column {
                    ScreenHeading("Pantalla de prueba")
                    SurfaceHeader(
                        title = "Sección de prueba",
                        navigationLabel = "Cerrar",
                        onNavigation = {},
                    )
                    PersistentMessage(
                        message = "No pudimos guardar.",
                        modifier = Modifier.testTag("persistent-error"),
                    )
                }
            }
        }

        compose.onNodeWithText("Pantalla de prueba")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("Sección de prueba")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithTag("persistent-error")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Assertive,
                ),
            )
    }
}
