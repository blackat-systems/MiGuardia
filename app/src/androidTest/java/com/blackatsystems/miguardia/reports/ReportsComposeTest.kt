package com.blackatsystems.miguardia.reports

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.ComponentActivity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.report.ReportFormat
import com.blackatsystems.miguardia.core.domain.report.ReportPrivacySelection
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import java.io.File
import java.time.YearMonth
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReportsComposeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everyPrivateInclusionStartsOffAndXlsxExplainsThatPhotosArePdfOnly() {
        var state by mutableStateOf(contentState(displayNameAvailable = true))
        val actions = ReportsActions(
            setFormat = { format -> state = state.copy(format = format) },
            setDisplayNameIncluded = { included ->
                state = state.copy(privacy = state.privacy.copy(includeDisplayName = included))
            },
        )
        setReports({ state }, actions)

        listOf(
            "reports-include-name",
            "reports-include-position",
            "reports-include-shift-notes",
            "reports-include-medical-notes",
            "reports-include-photos",
        ).forEach { tag -> compose.onNodeWithTag(tag).performScrollTo().assertIsOff() }
        compose.onNodeWithTag("reports-include-name").assertIsEnabled().performClick()
        compose.onNodeWithTag("reports-include-name").assertIsOn()
        compose.onNodeWithTag("reports-include-name")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        compose.onNodeWithTag("reports-include-name-switch", useUnmergedTree = true)
            .assertHasNoClickAction()

        compose.onNodeWithTag("reports-format-xlsx").performScrollTo().performClick()
        compose.onNodeWithTag("reports-format-xlsx")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithTag("reports-format-xlsx-radio", useUnmergedTree = true)
            .assertHasNoClickAction()
        compose.onNodeWithTag("reports-xlsx-photo-explanation").assertIsDisplayed()
        compose.runOnIdle { assertEquals(ReportFormat.XLSX, state.format) }
    }

    @Test
    fun medicalNoteRequiresASecondExplicitConfirmation() {
        var state by mutableStateOf(contentState())
        val actions = ReportsActions(
            requestMedicalNotes = { included ->
                assertTrue(included)
                state = state.copy(medicalConfirmationPending = true)
            },
            confirmMedicalNotes = {
                state = state.copy(
                    medicalConfirmationPending = false,
                    privacy = state.privacy.copy(includeMedicalNotes = true),
                )
            },
        )
        setReports({ state }, actions)

        compose.onNodeWithTag("reports-include-medical-notes").performScrollTo().performClick()
        compose.onNodeWithText("Incluir una nota médica privada").assertIsDisplayed()
        compose.onNodeWithTag("reports-include-medical-notes").assertIsOff()
        compose.onNodeWithTag("reports-confirm-medical-note").performClick()
        compose.onNodeWithTag("reports-include-medical-notes").performScrollTo().assertIsOn()
    }

    @Test
    fun loadingEmptyGeneratingSavingSharingAndErrorRemainDistinctAndActionable() {
        var retries = 0
        var state by mutableStateOf(
            ReportsUiState(
                isOpen = true,
                month = MONTH,
                stage = ReportsStage.LOADING,
            ),
        )
        setReports({ state }, ReportsActions(retry = { retries++ }))

        compose.onNodeWithTag("reports-loading").assertIsDisplayed()
        compose.runOnIdle {
            state = contentState().copy(
                stage = ReportsStage.EMPTY,
                preview = reportProjectionFixture().copy(situations = emptyList()),
            )
        }
        compose.onNodeWithText("Sin actividad registrada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reports-generate").performScrollTo().assertIsEnabled()
        compose.runOnIdle { state = state.copy(stage = ReportsStage.GENERATING) }
        compose.onNodeWithTag("reports-generating").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(stage = ReportsStage.SAVING) }
        compose.onNodeWithTag("reports-saving").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(stage = ReportsStage.SHARING) }
        compose.onNodeWithTag("reports-sharing").assertIsDisplayed()
        compose.runOnIdle {
            state = state.copy(stage = ReportsStage.ERROR, errorMessage = "Error verificable de archivo")
        }
        compose.onNodeWithTag("reports-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun surfaceRemainsReachableInLightDarkAndEveryAndroidFontScale() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        check(target.packageName == "com.blackatsystems.miguardia.qa")
        val output = File(target.filesDir, "stage7/reports-native-${System.currentTimeMillis()}")
            .also { check(it.mkdirs()) }
        val rows = JSONArray()
        val violations = mutableListOf<String>()
        var dark by mutableStateOf(false)
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        var widthDp by mutableStateOf(320)
        var density = 1f
        var navigationCalls = 0
        val state = contentState()
        compose.setContent {
            // Set only geometric constraints; the inner theme uses Android's native SP converter.
            AndroidFontScaleTheme(fontScale = TestFontScale.STANDARD) {
                Box(Modifier.width(widthDp.dp).height(640.dp).testTag("reports-native-root")) {
                    AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                        density = LocalDensity.current.density
                        ReportsSurfaceHost(state, ReportsActions(close = { navigationCalls++ }))
                    }
                }
            }
        }
        fun flush() {
            File(output, "report.json").writeText(JSONObject()
                .put("scope", "QA synthetic ReportsSurfaceHost only; no records or system settings accessed")
                .put("render_authority", "Original productive Text(String); PNGs require human visual review")
                .put("reconstruction_policy", "Only complete one-line intrinsic-fit reconstruction is classified as known; every other mismatch remains inconclusive and fails")
                .put("cases", rows).put("violations", JSONArray(violations)).toString(2))
        }
        try {
            for (width in listOf(320, 360, 384)) {
                for (theme in listOf(false, true)) for (candidate in TestFontScale.entries) {
                    compose.runOnIdle { widthDp = width; dark = theme; fontScale = candidate }
                    compose.waitForIdle()
                    val caseId = "$width-${if (theme) "dark" else "light"}-${candidate.percent}"
                    val row = JSONObject().put("case", caseId).put("width_dp", width)
                        .put("dark", theme).put("android_font_scale", candidate.scale.toDouble())
                        .put("density", density.toDouble())
                    rows.put(row)
                    val root = compose.onNodeWithTag("reports-native-root")
                    val header = compose.onNodeWithTag("reports-header")
                    val title = compose.onNode(
                        hasText("Informes locales") and hasAnyAncestor(hasTestTag("reports-header")),
                        useUnmergedTree = true,
                    )
                    val navigationText = compose.onNode(
                        hasText("Volver al Resumen") and hasAnyAncestor(hasTestTag("reports-header")),
                        useUnmergedTree = true,
                    )
                    val navigationControl = compose.onNode(
                        hasText("Volver al Resumen") and hasClickAction() and
                            hasAnyAncestor(hasTestTag("reports-header")),
                    )
                    // Capture and flush before checking layout: a failing case keeps its original image.
                    row.put("png", "$caseId.png")
                    captureReportsRoot(root, File(output, "$caseId.png"))
                    flush()
                    val rootBounds = root.fetchSemanticsNode().boundsInRoot
                    val headerBounds = header.fetchSemanticsNode().boundsInRoot
                    val titleBounds = title.fetchSemanticsNode().boundsInRoot
                    val textBounds = navigationText.fetchSemanticsNode().boundsInRoot
                    val controlNode = navigationControl.fetchSemanticsNode()
                    val controlBounds = controlNode.boundsInRoot
                    val touchBounds = controlNode.touchBoundsInRoot
                    row.put("root_bounds", rootBounds.json())
                        .put("header_bounds", headerBounds.json())
                        .put("title_bounds", titleBounds.json())
                        .put("navigation_text_bounds", textBounds.json())
                        .put("navigation_control_bounds", controlBounds.json())
                        .put("navigation_touch_bounds", touchBounds.json())
                        .put("title", inspectReportsText(title, "Informes locales", "$caseId title", violations))
                        .put("navigation", inspectReportsText(navigationText, "Volver al Resumen", "$caseId navigation", violations))
                    flush()

                    fun requireLayout(condition: Boolean, reason: String) {
                        if (!condition) violations += "$caseId $reason"
                    }
                    requireLayout(kotlin.math.abs(rootBounds.width - width * density) <= 1f,
                        "requested viewport was constrained by host; coverage incomplete")
                    requireLayout(rootBounds.containsRendered(headerBounds), "header outside viewport")
                    requireLayout(headerBounds.containsRendered(titleBounds), "title outside header")
                    requireLayout(headerBounds.containsRendered(textBounds), "navigation text outside header")
                    requireLayout(headerBounds.containsRendered(controlBounds), "navigation control outside header")
                    requireLayout(headerBounds.containsRendered(touchBounds), "navigation touch target outside header")
                    requireLayout(touchBounds.width >= 48 * density - 1 &&
                        touchBounds.height >= 48 * density - 1, "navigation touch target smaller than 48 dp")
                    requireLayout(touchBounds.left >= titleBounds.right - 1 ||
                        touchBounds.top >= titleBounds.bottom - 1 ||
                        titleBounds.left >= touchBounds.right - 1 ||
                        titleBounds.top >= touchBounds.bottom - 1, "navigation touch target overlaps the title")
                    for ((name, bounds) in listOf("title" to titleBounds, "navigation" to controlBounds)) {
                        requireLayout(bounds.left - headerBounds.left >= 16 * density - 1 &&
                            headerBounds.right - bounds.right >= 16 * density - 1,
                            "$name lost 16 dp horizontal header padding")
                        requireLayout(bounds.top - headerBounds.top >= 8 * density - 1 &&
                            headerBounds.bottom - bounds.bottom >= 8 * density - 1,
                            "$name lost 8 dp vertical header padding")
                    }
                    // Native text measurements choose row/column without shrinking dp geometry or font size.
                    val verticalGap = controlBounds.top - titleBounds.bottom
                    val horizontalGap = controlBounds.left - titleBounds.right
                    row.put("vertical_gap_px", verticalGap.toDouble())
                        .put("horizontal_gap_px", horizontalGap.toDouble())
                    requireLayout(verticalGap >= 4 * density - 1 ||
                        horizontalGap >= 8 * density - 1, "header items overlap or lose their existing gap")
                    try {
                        title.assertIsDisplayed()
                        navigationText.assertIsDisplayed()
                        navigationControl.assertIsDisplayed().assertIsEnabled().assertHasClickAction()
                        compose.onNodeWithTag("reports-surface").assertIsDisplayed()
                        compose.onNodeWithTag("reports-back-summary").performScrollTo().assertIsDisplayed()
                        val before = compose.runOnIdle { navigationCalls }
                        navigationControl.performClick()
                        val after = compose.runOnIdle { navigationCalls }
                        row.put("navigation_calls_before", before).put("navigation_calls_after", after)
                        requireLayout(after == before + 1, "navigation must invoke exactly one close callback")
                    } catch (failure: AssertionError) {
                        violations += "$caseId reachability/action: ${failure.message}"
                        row.put("action_assertion", failure.message ?: failure.javaClass.simpleName)
                    }
                    row.put("checked", true)
                    flush()
                }
            }
            assertEquals("All native geometry/theme/font cases must be captured", 18, rows.length())
            assertTrue("Reports diagnostic failed; inspect ${output.name}/report.json and PNGs:\n" +
                violations.joinToString("\n"), violations.isEmpty())
        } finally {
            flush()
            Log.i("MiGuardiaReportsDiagnostic", "report=${output.relativeTo(target.filesDir)}/report.json")
        }
    }

    @Test
    fun medicalInclusionKeepsEveryWordSeparatedFromOneAccessibleSwitch() {
        var dark by mutableStateOf(false)
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        var density = 1f
        var requests = 0
        val actions = ReportsActions(requestMedicalNotes = { included ->
            assertTrue(included)
            requests++
        })
        compose.setContent {
            AndroidFontScaleTheme(fontScale = TestFontScale.STANDARD) {
                Box(Modifier.width(360.dp).height(640.dp)) {
                    AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                        density = LocalDensity.current.density
                        ReportsSurfaceHost(contentState(), actions)
                    }
                }
            }
        }
        val tag = "reports-include-medical-notes"
        val expected = "Incluir notas privadas de carpeta médica"
        for (theme in listOf(false, true)) for (scale in TestFontScale.entries) {
            compose.runOnIdle { dark = theme; fontScale = scale }
            val context = "theme=$theme fontScale=$scale"
            val row = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                .assertIsEnabled().assertIsOff().assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            val label = compose.onNodeWithTag("$tag-label", useUnmergedTree = true)
            val control = compose.onNodeWithTag("$tag-switch", useUnmergedTree = true)
                .assertIsDisplayed().assertHasNoClickAction()
            val layouts = mutableListOf<TextLayoutResult>()
            label.assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(context, 1, layouts.size)
            val layout = layouts.single()
            assertEquals(context, expected, layout.layoutInput.text.text)
            assertFalse(context, layout.hasVisualOverflow)
            assertEquals(context, expected.length, layout.getLineEnd(layout.lineCount - 1))
            for (line in 0 until layout.lineCount) {
                assertFalse(context, layout.isLineEllipsized(line))
                val end = layout.getLineEnd(line, visibleEnd = true)
                if (end > 0 && end < expected.length) {
                    assertTrue("$context word split at $end", expected[end].isWhitespace() || expected[end - 1].isWhitespace())
                }
            }
            val labelBounds = label.fetchSemanticsNode().boundsInRoot
            val controlBounds = control.fetchSemanticsNode().boundsInRoot
            val rowBounds = row.fetchSemanticsNode().boundsInRoot
            assertTrue("$context clipped label", labelBounds.width + 1 >= layout.size.width &&
                labelBounds.height + 1 >= layout.size.height)
            assertTrue(context, rowBounds.containsRendered(labelBounds) && rowBounds.containsRendered(controlBounds))
            assertTrue("$context missing 12 dp separation",
                controlBounds.left - labelBounds.right >= 12 * density - 1 ||
                    controlBounds.top - labelBounds.bottom >= 12 * density - 1)
            val before = requests
            row.performClick()
            compose.runOnIdle { assertEquals("$context one action per activation", before + 1, requests) }
            row.assertIsOff() // Requesting inclusion still requires the existing second confirmation.
        }
    }

    private fun captureReportsRoot(node: SemanticsNodeInteraction, destination: File) {
        compose.runOnIdle { check(compose.activity.hasWindowFocus()) { "QA lost focus; no capture" } }
        val bitmap = node.captureToImage().asAndroidBitmap()
        try {
            compose.runOnIdle { check(compose.activity.hasWindowFocus()) { "QA lost focus during capture" } }
            destination.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    private fun inspectReportsText(
        node: SemanticsNodeInteraction,
        expected: String,
        context: String,
        violations: MutableList<String>,
    ): JSONObject {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val report = JSONObject().put("expected_text", expected).put("layout_count", layouts.size)
        if (layouts.size != 1) {
            violations += "$context missing unique layout diagnostic"
            return report.put("classification", "INCONCLUSIVE")
        }
        val layout = layouts.single()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val text = layout.layoutInput.text.text
        val lines = JSONArray()
        val splitOffsets = mutableListOf<Int>()
        for (line in 0 until layout.lineCount) {
            val start = layout.getLineStart(line)
            val end = layout.getLineEnd(line)
            val visibleEnd = layout.getLineEnd(line, visibleEnd = true)
            val split = visibleEnd > 0 && visibleEnd < text.length &&
                !text[visibleEnd - 1].isWhitespace() && !text[visibleEnd].isWhitespace()
            if (split) splitOffsets += visibleEnd
            lines.put(JSONObject().put("index", line).put("start", start).put("end", end)
                .put("visible_end", visibleEnd).put("text", text.substring(start, end))
                .put("left", layout.getLineLeft(line).toDouble())
                .put("right", layout.getLineRight(line).toDouble())
                .put("ellipsis", layout.isLineEllipsized(line)).put("intra_word_split", split))
        }
        val complete = layout.lineCount > 0 &&
            layout.getLineEnd(layout.lineCount - 1) == expected.length
        val ellipsized = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        val reconstructedWidth = layout.multiParagraph.width > layout.size.width + 1f
        val reconstructedHeight = layout.multiParagraph.height > layout.size.height + 1f
        // This exact narrow case was demonstrated by the four-renderer/pixel diagnostic.
        // No multi-line or intrinsic-too-wide reconstruction is accepted as proof of real wrapping.
        val knownIntrinsicReconstruction = reconstructedWidth && !reconstructedHeight &&
            layout.lineCount == 1 && complete && !ellipsized && !layout.didOverflowHeight &&
            layout.multiParagraph.intrinsics.maxIntrinsicWidth <= layout.size.width + 1f
        val unresolved = (reconstructedWidth || reconstructedHeight) && !knownIntrinsicReconstruction
        report.put("text", text).put("size_width", layout.size.width).put("size_height", layout.size.height)
            .put("constraints", layout.layoutInput.constraints.toString())
            .put("paragraph_width", layout.multiParagraph.width.toDouble())
            .put("paragraph_height", layout.multiParagraph.height.toDouble())
            .put("intrinsic_width", layout.multiParagraph.intrinsics.maxIntrinsicWidth.toDouble())
            .put("has_visual_overflow", layout.hasVisualOverflow).put("complete", complete)
            .put("lines", lines).put("word_split_offsets", JSONArray(splitOffsets))
            .put("classification", when {
                unresolved -> "INCONCLUSIVE_RECONSTRUCTION_REQUIRES_IMAGE_REVIEW"
                knownIntrinsicReconstruction -> "KNOWN_INTRINSIC_WIDTH_RECONSTRUCTION"
                else -> "CONSISTENT_LAYOUT_GEOMETRY"
            })
        if (text != expected) violations += "$context text changed"
        if (!complete || ellipsized) violations += "$context missing characters or ellipsis"
        if (bounds.width + 1 < layout.size.width || bounds.height + 1 < layout.size.height) {
            violations += "$context layout outside its rendered semantic bounds"
        }
        if (unresolved) {
            // Fail closed, but never mislabel reconstructed line breaks as a proven product defect.
            violations += "$context INCONCLUSIVE reconstruction; inspect untouched PNG before attribution"
        } else {
            if (splitOffsets.isNotEmpty()) violations += "$context intraword fragmentation at $splitOffsets"
            if (!knownIntrinsicReconstruction && layout.hasVisualOverflow) {
                violations += "$context overflow in internally consistent layout"
            }
        }
        return report
    }

    private fun Rect.json() = JSONObject().put("left", left.toDouble()).put("top", top.toDouble())
        .put("right", right.toDouble()).put("bottom", bottom.toDouble())

    private fun Rect.containsRendered(other: Rect): Boolean =
        other.left >= left - 1 && other.top >= top - 1 &&
            other.right <= right + 1 && other.bottom <= bottom + 1

    private fun setReports(
        state: () -> ReportsUiState,
        actions: ReportsActions,
    ) {
        compose.setContent {
            AndroidFontScaleTheme {
                ReportsSurfaceHost(state(), actions)
            }
        }
    }

    private fun contentState(displayNameAvailable: Boolean = false): ReportsUiState = ReportsUiState(
        isOpen = true,
        month = MONTH,
        format = ReportFormat.PDF,
        privacy = ReportPrivacySelection(),
        stage = ReportsStage.CONTENT,
        preview = reportProjectionFixture(),
        displayNameAvailable = displayNameAvailable,
    )

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
    }
}
