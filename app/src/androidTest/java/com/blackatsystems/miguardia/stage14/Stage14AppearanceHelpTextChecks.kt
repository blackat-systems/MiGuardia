package com.blackatsystems.miguardia.stage14

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.blackatsystems.miguardia.stage12.stage12VisibleOverflow

/*
 * Shared by Stage14AppearanceScreensTest and Stage14HelpScreensTest only.
 * Every helper returns the problems it found instead of failing at the first one, so a single
 * run reports every theme / text size / step that breaks.
 */

/** The layout Compose reports for one Text node (unmerged tree). */
internal fun SemanticsNodeInteraction.stage14TextLayout(): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    check(layouts.size == 1) { "Expected one text layout, got ${layouts.size}" }
    return layouts.single()
}

/**
 * «Texto entero»: every character laid out, no ellipsis, nothing drawn outside its box and no
 * line break in the middle of a word (the «Disponibilid/ad» kind of break found at 200%).
 */
internal fun TextLayoutResult.stage14WholeTextProblems(): List<String> {
    val text = layoutInput.text.text
    val problems = mutableListOf<String>()
    if (lineCount == 0) return listOf("«$text» has no lines")
    if (stage12VisibleOverflow()) {
        problems += "«$text» overflows its box (width=$didOverflowWidth height=$didOverflowHeight, " +
            "box=${size.width}x${size.height}, paragraph=${multiParagraph.width}x${multiParagraph.height})"
    }
    if ((0 until lineCount).any(::isLineEllipsized)) problems += "«$text» is ellipsized"
    if (getLineEnd(lineCount - 1, visibleEnd = true) < text.trimEnd().length) problems += "«$text» is not laid out to its end"
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line)
        if (end <= 0 || end >= text.length) continue
        val before = text[end - 1]
        val after = text[end]
        val naturalBreak = before.isWhitespace() || after.isWhitespace() || before == '-' || before == '/'
        if (!naturalBreak) {
            problems += "«$text» breaks inside a word: «${text.substring(0, end).takeLast(12)}|${text.substring(end).take(12)}»"
        }
    }
    return problems
}

/** A node cut by a scroll viewport or a parent reports visible bounds smaller than its size. */
internal fun SemanticsNode.stage14ClipProblems(name: String): List<String> {
    val visible = boundsInRoot
    return if (visible.width + 1f < size.width || visible.height + 1f < size.height) {
        listOf("$name is clipped: visible=${visible.width}x${visible.height} of ${size.width}x${size.height}")
    } else {
        emptyList()
    }
}

internal fun SemanticsNode.stage14InsideProblems(outer: SemanticsNode, name: String): List<String> {
    val inner = boundsInRoot
    val box = outer.boundsInRoot
    val inside = inner.left >= box.left - 1f && inner.top >= box.top - 1f &&
        inner.right <= box.right + 1f && inner.bottom <= box.bottom + 1f
    return if (inside) emptyList() else listOf("$name leaves its container: $inner outside $box")
}

/**
 * One button found by test tag whose label is a Text child: reachable, not clipped, label whole
 * and inside the button. [scroll] is false for dialog buttons, which have no scroll container.
 */
internal fun SemanticsNodeInteractionsProvider.stage14ButtonLabelProblems(
    tag: String,
    label: String,
    scroll: Boolean = true,
): List<String> {
    val interaction = onNodeWithTag(tag)
    if (scroll) interaction.performScrollTo()
    val button = interaction.assertIsDisplayed().fetchSemanticsNode()
    val textInteraction = onNode(hasText(label) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
    val textNode = textInteraction.assertIsDisplayed().fetchSemanticsNode()
    return button.stage14ClipProblems("button «$label»") +
        textNode.stage14ClipProblems("label «$label»") +
        textNode.stage14InsideProblems(button, "label «$label»") +
        textInteraction.stage14TextLayout().stage14WholeTextProblems()
}

/**
 * Two buttons sharing one row («Atrás / Siguiente»): both labels whole, neither button steps on
 * the other, both at least [minTouchPx] tall.
 */
internal fun SemanticsNodeInteractionsProvider.stage14ButtonRowProblems(
    startTag: String,
    startLabel: String,
    endTag: String,
    endLabel: String,
    minTouchPx: Float,
): List<String> {
    val problems = mutableListOf<String>()
    problems += stage14ButtonLabelProblems(startTag, startLabel)
    problems += stage14ButtonLabelProblems(endTag, endLabel)
    // Read both at the same scroll position, after the last scroll.
    val startNode = onNodeWithTag(startTag).fetchSemanticsNode()
    val endNode = onNodeWithTag(endTag).fetchSemanticsNode()
    problems += startNode.stage14ClipProblems("button «$startLabel» beside «$endLabel»")
    val start = startNode.boundsInRoot
    val end = endNode.boundsInRoot
    if (start.right > end.left + 1f) problems += "«$startLabel» ($start) steps on «$endLabel» ($end)"
    val sameRow = start.top < end.bottom && end.top < start.bottom
    if (!sameRow) problems += "«$startLabel» ($start) and «$endLabel» ($end) are not on the same row"
    if (start.height + 1f < minTouchPx) problems += "«$startLabel» is ${start.height}px tall, under $minTouchPx"
    if (end.height + 1f < minTouchPx) problems += "«$endLabel» is ${end.height}px tall, under $minTouchPx"
    return problems
}
