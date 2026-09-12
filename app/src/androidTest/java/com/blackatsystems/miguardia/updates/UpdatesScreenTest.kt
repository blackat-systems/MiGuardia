package com.blackatsystems.miguardia.updates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.File

class UpdatesScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun statesActionsAndFullTextRemainReachableInBothThemesOrientationsAndAllInternalZooms() {
        var dark by mutableStateOf(false); var wide by mutableStateOf(false); var zoom by mutableStateOf(AppZoom.STANDARD)
        var stage by mutableStateOf(UpdateStage.IDLE)
        val installed = InstalledUpdateApp(InstalledAppIdentity("com.blackatsystems.miguardia.qa", "b".repeat(64), 1), "1.0.0", 26)
        var searches = 0; var downloads = 0; var saves = 0
        var expectedBackground = 0
        compose.setContent {
            MiGuardiaTheme(darkTheme = dark, appZoom = zoom) {
                expectedBackground = MaterialTheme.colorScheme.background.toArgb()
                Box(Modifier.size(if (wide) 600.dp else 320.dp, if (wide) 320.dp else 600.dp)) {
                    UpdatesScreen(UpdatesState(open = true, installed = installed, stage = stage),
                        { searches++ }, { downloads++ }, { saves++ }, {}, {})
                }
            }
        }
        for (theme in listOf(false, true)) for (landscape in listOf(false, true)) for (scale in AppZoom.entries) {
            compose.runOnIdle { dark = theme; wide = landscape; zoom = scale }
            for ((state, tag) in listOf(UpdateStage.IDLE to "updates-search", UpdateStage.AVAILABLE to "updates-download", UpdateStage.READY to "updates-save")) {
                compose.runOnIdle { stage = state }
                compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()
            }
            compose.runOnIdle { stage = UpdateStage.SAVED }
            val text = compose.onNodeWithTag("updates-status").performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            val evidence = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "updates28-screen-qa").apply { mkdirs() }
            val screenshot = compose.onNodeWithTag("updates-surface").captureToImage().asAndroidBitmap()
            assertEquals("Background must follow the selected theme", expectedBackground, screenshot.getPixel(1, 1))
            File(evidence, "${if (theme) "dark" else "light"}-${if (landscape) "wide" else "tall"}-${scale.name}.png").outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            screenshot.recycle()
            val bounds = text.fetchSemanticsNode().boundsInRoot
            // Compose reconstructs the semantic Text paragraph at the available maximum width.
            // Compare actual line extents and clipped node bounds, as in the existing menu QA.
            layouts.forEach { layout ->
                val label = "$theme/$landscape/$scale bounds=$bounds size=${layout.size}"
                assertFalse(label, layout.multiParagraph.didExceedMaxLines)
                assertTrue(label, bounds.width + 1 >= layout.size.width)
                assertTrue(label, bounds.height + 1 >= layout.size.height)
                for (line in 0 until layout.lineCount) {
                    assertFalse(label, layout.isLineEllipsized(line))
                    assertTrue(label, layout.getLineLeft(line) >= -1)
                    assertTrue(label, layout.getLineRight(line) <= layout.size.width + 1)
                    assertTrue(label, layout.getLineBottom(line) <= layout.size.height + 1)
                }
            }
            compose.onNodeWithTag("updates-back").performScrollTo().assertIsDisplayed()
        }
        compose.runOnIdle { assertEquals(12, searches); assertEquals(12, downloads); assertEquals(12, saves) }
        for (state in UpdateStage.entries) {
            compose.runOnIdle { stage = state }
            compose.onNodeWithTag("updates-status").performScrollTo().assertIsDisplayed()
            if (state in listOf(UpdateStage.DOWNLOADING, UpdateStage.VERIFYING, UpdateStage.SAVING))
                compose.onNodeWithTag("updates-cancel").performScrollTo().assertIsDisplayed()
        }
    }
}
