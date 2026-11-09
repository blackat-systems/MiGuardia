package com.blackatsystems.miguardia.stage135

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.ui.components.HeroCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * Regression found by Joaquín on the Samsung (8/11/2026): with the style v2 transparent
 * calendar container, the top card's text inherited no color and was drawn black on the dark
 * card. The hero now sets its own content color, whatever its parent provides.
 */
class Stage135HeroContentColorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun heroTextUsesTheThemeTextColorWithoutAParentSurface() {
        var dark by mutableStateOf(true)
        var inherited = Color.Unspecified
        var expected = Color.Unspecified
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = dark) {
                expected = MaterialTheme.colorScheme.onSurface
                // No Surface or Scaffold here: the hero must not depend on one.
                Box {
                    HeroCard(title = "Próxima jornada") {
                        inherited = LocalContentColor.current
                        Text("22:00 – 06:00")
                    }
                }
            }
        }
        for (theme in listOf(true, false)) {
            compose.runOnIdle { dark = theme }
            compose.onNodeWithText("22:00 – 06:00").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals("dark=$theme", expected, inherited)
                assertNotEquals("dark=$theme", Color.Black, inherited)
            }
        }
    }
}
