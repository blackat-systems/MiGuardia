package com.blackatsystems.miguardia.stage12

import androidx.compose.ui.text.TextLayoutResult

/**
 * Overflow as the person sees it, for layouts obtained through the GetTextLayoutResult semantics action.
 *
 * Compose reconstructs that layout instead of returning the drawn one, and for a simple single-line Text it
 * can report a paragraph wider than its box although the glyphs fit (shown with pixels by
 * TextLayoutSemanticsDiagnosticTest). Only that exact narrow case is excused, with the same rule as
 * ReportsComposeTest: one complete line, no ellipsis, no height overflow and the real glyph width
 * inside the box. Any other overflow still fails.
 */
internal fun TextLayoutResult.stage12VisibleOverflow(): Boolean {
    if (!hasVisualOverflow) return false
    val lines = lineCount
    val complete = lines > 0 && getLineEnd(lines - 1, visibleEnd = true) == layoutInput.text.length
    val ellipsized = (0 until lines).any(::isLineEllipsized)
    val knownWidthReconstruction = didOverflowWidth && !didOverflowHeight &&
        multiParagraph.width > size.width && multiParagraph.height <= size.height + 1f &&
        lines == 1 && complete && !ellipsized &&
        multiParagraph.intrinsics.maxIntrinsicWidth <= size.width + 1f
    return !knownWidthReconstruction
}
