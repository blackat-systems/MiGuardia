package com.blackatsystems.miguardia

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolates text-layout diagnostics without accessing any app records or system settings. */
class TextLayoutSemanticsDiagnosticTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun stringSemanticsAreComparedWithActualAnnotatedAndCallbackRendering() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.blackatsystems.miguardia.qa")
        val output = File(context.filesDir, "stage7/text-layout-semantics-${System.currentTimeMillis()}")
            .also { check(it.mkdirs()) }
        val rows = JSONArray()
        val callbacks = mutableMapOf<String, TextLayoutResult>()
        var content by mutableStateOf("32 h")
        var alignment by mutableStateOf(TextAlign.Start)
        var fontSize by mutableStateOf(14)
        var reconstructedDiscrepancies = 0
        compose.setContent {
            MiGuardiaTheme {
                val style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Keep all four measure constraints and paint inputs identical. A non-null
                    // callback intentionally exercises Compose's full TextLayoutResult path.
                    Row(Modifier.fillMaxWidth()) {
                        Text(content, Modifier.background(Color.Black).testTag(STRING),
                            color = Color.White, style = style, textAlign = alignment,
                            maxLines = 1, softWrap = false)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(AnnotatedString(content), Modifier.background(Color.Black).testTag(ANNOTATED),
                            color = Color.White, style = style, textAlign = alignment,
                            maxLines = 1, softWrap = false,
                            onTextLayout = { callbacks[ANNOTATED] = it })
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(AnnotatedString(content), Modifier.background(Color.Black).testTag(ANNOTATED_NO_CALLBACK),
                            color = Color.White, style = style, textAlign = alignment,
                            maxLines = 1, softWrap = false)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(content, Modifier.background(Color.Black).testTag(CALLBACK),
                            color = Color.White, style = style, textAlign = alignment,
                            maxLines = 1, softWrap = false,
                            onTextLayout = { callbacks[CALLBACK] = it })
                    }
                }
            }
        }
        try {
            for ((index, value) in listOf("32 h", "32 h 45 min", "Completada").withIndex()) {
                for (align in listOf(TextAlign.Start, TextAlign.End)) {
                    compose.runOnIdle { content = value; alignment = align; fontSize = if (index == 1) 15 else 14 }
                    compose.waitForIdle()
                    val caseId = "$index-${if (align == TextAlign.Start) "start" else "end"}"
                    val row = JSONObject().put("case", caseId).put("text", value).put("alignment", align.toString())
                    rows.put(row)
                    val nodes = listOf(STRING, ANNOTATED, ANNOTATED_NO_CALLBACK, CALLBACK).associateWith {
                        compose.onNodeWithTag(it, useUnmergedTree = true).assertIsDisplayed()
                    }
                    val layouts = nodes.mapValues { (_, node) -> node.layout() }
                    val capturedCallbacks = compose.runOnIdle { callbacks.toMap() }
                    layouts.forEach { (tag, layout) ->
                        row.put(tag, layout.json()
                            .put("bounds", nodes.getValue(tag).fetchSemanticsNode().boundsInRoot.toString())
                            .put("actual_callback", capturedCallbacks[tag]?.json() ?: JSONObject.NULL))
                        assertEquals("$caseId $tag content", value, layout.layoutInput.text.text)
                        assertEquals("$caseId $tag lines", 1, layout.lineCount)
                        assertEquals("$caseId $tag last character", value.length, layout.getLineEnd(0))
                        assertFalse("$caseId $tag ellipsis", layout.isLineEllipsized(0))
                    }
                    val simple = layouts.getValue(STRING)
                    val annotated = layouts.getValue(ANNOTATED)
                    val annotatedWithoutCallback = layouts.getValue(ANNOTATED_NO_CALLBACK)
                    val callback = layouts.getValue(CALLBACK)
                    row.put("annotated_actual_callback", capturedCallbacks.getValue(ANNOTATED).json())
                        .put("string_actual_callback", capturedCallbacks.getValue(CALLBACK).json())
                    assertFalse("$caseId annotated actual overflow", annotated.hasVisualOverflow)
                    assertFalse("$caseId annotated without callback overflow", annotatedWithoutCallback.hasVisualOverflow)
                    assertFalse("$caseId callback actual overflow", callback.hasVisualOverflow)
                    assertEquals("$caseId annotated without callback paragraph", annotated.multiParagraph.width,
                        annotatedWithoutCallback.multiParagraph.width, 0f)
                    assertEquals("$caseId annotated without callback line left", annotated.getLineLeft(0),
                        annotatedWithoutCallback.getLineLeft(0), 0f)
                    assertEquals("$caseId annotated without callback line right", annotated.getLineRight(0),
                        annotatedWithoutCallback.getLineRight(0), 0f)
                    for (tag in listOf(ANNOTATED, CALLBACK)) {
                        assertEquals("$caseId $tag actual/semantics size", capturedCallbacks.getValue(tag).size, layouts.getValue(tag).size)
                        assertEquals("$caseId $tag actual/semantics paragraph", capturedCallbacks.getValue(tag).multiParagraph.width,
                            layouts.getValue(tag).multiParagraph.width, 0f)
                    }
                    val discrepancy = simple.didOverflowWidth && simple.multiParagraph.width > simple.size.width &&
                        simple.multiParagraph.intrinsics.maxIntrinsicWidth <= simple.size.width
                    row.put("reconstructed_paragraph_discrepancy", discrepancy)
                    if (discrepancy) reconstructedDiscrepancies++ else {
                        // A future Compose implementation may repair reconstruction. Such a repair
                        // is valid; this test never requires the dependency defect to keep existing.
                        assertFalse("$caseId simple actual overflow", simple.hasVisualOverflow)
                    }
                    val pixels = nodes.mapValues { (tag, node) -> capture(node, File(output, "$caseId-$tag.png")) }
                    assertEquals("$caseId annotated size", simple.size, annotated.size)
                    assertEquals("$caseId annotated without callback size", simple.size, annotatedWithoutCallback.size)
                    assertEquals("$caseId callback size", simple.size, callback.size)
                    assertTrue("$caseId fixture must contain painted glyphs", pixels.getValue(STRING).any { it != android.graphics.Color.BLACK })
                    assertArrayEquals("$caseId String/AnnotatedString pixels", pixels.getValue(STRING), pixels.getValue(ANNOTATED))
                    assertArrayEquals("$caseId String/AnnotatedString without callback pixels", pixels.getValue(STRING), pixels.getValue(ANNOTATED_NO_CALLBACK))
                    assertArrayEquals("$caseId String/callback pixels", pixels.getValue(STRING), pixels.getValue(CALLBACK))
                    row.put("identical_rendered_pixels", true)
                }
            }
        } finally {
            File(output, "report.json").writeText(JSONObject()
                .put("cases", rows).put("reconstructed_discrepancies", reconstructedDiscrepancies)
                .put("scope", "synthetic Compose root only; no app records or system setting changes")
                .toString(2))
            Log.i("MiGuardiaTextDiagnostic", "report=${output.relativeTo(context.filesDir)}/report.json discrepancies=$reconstructedDiscrepancies")
        }
    }

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val result = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
        assertEquals(1, result.size)
        return result.single()
    }

    private fun capture(node: SemanticsNodeInteraction, destination: File): IntArray {
        compose.runOnIdle { check(compose.activity.hasWindowFocus()) { "QA lost focus; no capture" } }
        val bitmap = node.captureToImage().asAndroidBitmap()
        try {
            compose.runOnIdle { check(compose.activity.hasWindowFocus()) { "QA lost focus during capture" } }
            destination.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return IntArray(bitmap.width * bitmap.height).also {
                bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            }
        } finally { bitmap.recycle() }
    }

    private fun TextLayoutResult.json() = JSONObject()
        .put("size", size.toString()).put("constraints", layoutInput.constraints.toString())
        .put("paragraph_width", multiParagraph.width.toDouble()).put("paragraph_height", multiParagraph.height.toDouble())
        .put("intrinsic_width", multiParagraph.intrinsics.maxIntrinsicWidth.toDouble())
        .put("width_overflow", didOverflowWidth).put("height_overflow", didOverflowHeight)
        .put("line_left", getLineLeft(0).toDouble()).put("line_right", getLineRight(0).toDouble())

    private companion object {
        const val STRING = "text-simple-string"
        const val ANNOTATED = "text-annotated"
        const val ANNOTATED_NO_CALLBACK = "text-annotated-without-callback"
        const val CALLBACK = "text-string-callback"
    }
}
