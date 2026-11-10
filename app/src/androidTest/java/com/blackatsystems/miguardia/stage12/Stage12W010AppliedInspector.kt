package com.blackatsystems.miguardia.stage12

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Chronometer
import android.widget.TextView
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.widget.WidgetSize
import java.util.Collections
import java.util.IdentityHashMap

/** Reads the actual applied tree, including non-visible descendants and accessibility nodes. No image is saved. */
internal object Stage12W010AppliedInspector {
    data class Counts(var nodes: Int = 0, var textFields: Int = 0, var accessibilityFields: Int = 0,
        var colorAttributes: Int = 0, var drawablePixels: Int = 0, var publicDescriptionProofs: Int = 0)

    fun requireHidden(root: View, fixture: Stage12W010Fixture, palette: Stage12W010Oracle.Palette,
        size: WidgetSize, variant: Stage12W010Observation.Variant, publicControl: Stage12W010CleanChronometer): Counts {
        check(Looper.myLooper() == Looper.getMainLooper())
        val counts = Counts()
        fun text(view: View, value: CharSequence?, visible: Boolean, field: Stage12W010Observation.Field,
            accessibility: Boolean = false, neutralBeforeReading: Boolean = false) {
            val content = value?.toString().orEmpty()
            if (content.isEmpty()) return
            val evidence = if (view is Chronometer && view.id == R.id.widget_countdown &&
                field in setOf(Stage12W010Observation.Field.VIEW_DESCRIPTION, Stage12W010Observation.Field.NODE_DESCRIPTION))
                publicControl.observe(view, content, field, visible, fixture, neutralBeforeReading) else null
            try { if (Stage12W010PublicDescription.requireText(content, fixture, evidence)) counts.publicDescriptionProofs++ }
            catch (error: IllegalStateException) {
                if (error.message != "W010_APPLIED_SECRET_TEXT") throw error
                val node = when (view.id) {
                    R.id.widget_root -> Stage12W010Observation.Node.ROOT
                    R.id.widget_title -> Stage12W010Observation.Node.TITLE
                    R.id.widget_primary -> Stage12W010Observation.Node.PRIMARY
                    R.id.widget_schedule -> Stage12W010Observation.Node.SCHEDULE
                    R.id.widget_countdown -> Stage12W010Observation.Node.COUNTDOWN
                    R.id.widget_simultaneous -> Stage12W010Observation.Node.SIMULTANEOUS
                    R.id.widget_row_1 -> Stage12W010Observation.Node.ROW_1
                    R.id.widget_row_2 -> Stage12W010Observation.Node.ROW_2
                    R.id.widget_row_3 -> Stage12W010Observation.Node.ROW_3
                    R.id.widget_weather_area -> Stage12W010Observation.Node.WEATHER_AREA
                    R.id.widget_weather -> Stage12W010Observation.Node.WEATHER
                    R.id.widget_weather_credit -> Stage12W010Observation.Node.WEATHER_CREDIT
                    R.id.widget_configure -> Stage12W010Observation.Node.CONFIGURE
                    else -> Stage12W010Observation.Node.OTHER
                }
                val kind = when (view) {
                    is Chronometer -> Stage12W010Observation.Kind.CHRONOMETER
                    is TextView -> Stage12W010Observation.Kind.TEXT
                    is ImageView -> Stage12W010Observation.Kind.IMAGE
                    is ViewGroup -> Stage12W010Observation.Kind.GROUP
                    else -> Stage12W010Observation.Kind.OTHER
                }
                val visibility = when (view.visibility) {
                    View.VISIBLE -> Stage12W010Observation.Visibility.VISIBLE
                    View.INVISIBLE -> Stage12W010Observation.Visibility.INVISIBLE
                    View.GONE -> Stage12W010Observation.Visibility.GONE
                    else -> Stage12W010Observation.Visibility.UNKNOWN
                }
                val timer = (view as? Chronometer)?.let { Stage12W010Observation.Timer(it.base == 0L,
                    it.format == null, it.isCountDown, it.text.isNullOrEmpty()) }
                val failure = Stage12W010Observation.failure(content, fixture.forbiddenText, variant, field, node, kind,
                    visibility, visible, counts.nodes, timer)
                evidence?.let { failure.addSuppressed(IllegalStateException(it.safe(Stage12W010PublicDescription.decide(it)))) }
                throw failure
            }
            if (accessibility) counts.accessibilityFields++ else counts.textFields++
            if (visible) check(content in Stage12W010Oracle.visibleText) { "W010_APPLIED_UNEXPECTED_PUBLIC_TEXT" }
        }
        fun color(value: Int) { Stage12W010Oracle.requireNeutralColor(value, fixture); counts.colorAttributes++ }
        fun colors(value: ColorStateList?) {
            if (value == null) return
            color(value.defaultColor)
            // Every combination of the standard visual states, including non-current states.
            val states = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed,
                android.R.attr.state_focused, android.R.attr.state_selected, android.R.attr.state_checked,
                android.R.attr.state_activated, android.R.attr.state_hovered, android.R.attr.state_window_focused)
            if (value.isStateful) for (mask in 0 until (1 shl states.size)) {
                val state = states.mapIndexed { index, id -> if (mask and (1 shl index) != 0) id else -id }.toIntArray()
                color(value.getColorForState(state, value.defaultColor))
            }
        }
        val seen: MutableSet<Drawable> = Collections.newSetFromMap(IdentityHashMap())
        fun drawable(value: Drawable?) {
            if (value == null || !seen.add(value)) return
            when (val filter = value.colorFilter) {
                is PorterDuffColorFilter -> error("W010_UNREADABLE_COLOR_FILTER")
                is BlendModeColorFilter -> color(filter.color)
                null -> Unit
                else -> error("W010_UNSUPPORTED_COLOR_FILTER")
            }
            when (value) {
                is ColorDrawable -> color(value.color)
                is GradientDrawable -> { colors(value.color); value.colors?.forEach(::color) }
                is LayerDrawable -> for (index in 0 until value.numberOfLayers) drawable(value.getDrawable(index))
                is StateListDrawable -> for (index in 0 until value.stateCount) drawable(value.getStateDrawable(index))
                is DrawableWrapper -> drawable(value.drawable)
            }
            if (value.current !== value) drawable(value.current)
            val old = Rect(value.bounds)
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            try {
                value.setBounds(0, 0, 32, 32); value.draw(Canvas(bitmap))
                val pixels = IntArray(32 * 32); bitmap.getPixels(pixels, 0, 32, 0, 0, 32, 32)
                pixels.forEach { color(it) }; counts.drawablePixels += pixels.size
            } finally { value.bounds = old; bitmap.recycle() }
        }
        fun visit(view: View, ancestorsVisible: Boolean) {
            counts.nodes++
            check(counts.nodes <= 128) { "W010_APPLIED_TREE_BOUND" }
            val visible = ancestorsVisible && view.visibility == View.VISIBLE
            val descriptionNeutralBefore = publicControl.scopeBefore(view, visible)
            text(view, view.contentDescription, visible, Stage12W010Observation.Field.VIEW_DESCRIPTION,
                neutralBeforeReading = descriptionNeutralBefore)
            text(view, view.stateDescription, visible, Stage12W010Observation.Field.VIEW_STATE)
            text(view, view.tooltipText, visible, Stage12W010Observation.Field.VIEW_TOOLTIP)
            text(view, view.accessibilityPaneTitle, visible, Stage12W010Observation.Field.VIEW_PANE)
            val nodeNeutralBefore = publicControl.scopeBefore(view, visible)
            val node = view.createAccessibilityNodeInfo()
            try {
                text(view, node.text, visible, Stage12W010Observation.Field.NODE_TEXT, true)
                text(view, node.contentDescription, visible, Stage12W010Observation.Field.NODE_DESCRIPTION, true,
                    neutralBeforeReading = nodeNeutralBefore)
                text(view, node.stateDescription, visible, Stage12W010Observation.Field.NODE_STATE, true)
                text(view, node.hintText, visible, Stage12W010Observation.Field.NODE_HINT, true)
                text(view, node.error, visible, Stage12W010Observation.Field.NODE_ERROR, true)
                text(view, node.tooltipText, visible, Stage12W010Observation.Field.NODE_TOOLTIP, true)
                text(view, node.paneTitle, visible, Stage12W010Observation.Field.NODE_PANE, true)
            } finally { @Suppress("DEPRECATION") node.recycle() }
            colors(view.backgroundTintList); colors(view.foregroundTintList)
            drawable(view.background); drawable(view.foreground)
            if (view is TextView) {
                text(view, view.text, visible, Stage12W010Observation.Field.TEXT)
                text(view, view.hint, visible, Stage12W010Observation.Field.HINT)
                text(view, view.error, visible, Stage12W010Observation.Field.ERROR)
                colors(view.textColors); colors(view.hintTextColors); colors(view.linkTextColors)
                colors(view.compoundDrawableTintList)
                view.compoundDrawables.forEach(::drawable); view.compoundDrawablesRelative.forEach(::drawable)
            }
            if (view is ImageView) { colors(view.imageTintList); drawable(view.drawable) }
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index), visible)
        }
        visit(root, true)
        check(root.id == R.id.widget_root && root.contentDescription?.toString() == Stage12W010Oracle.ROOT_DESCRIPTION) { "W010_APPLIED_ROOT" }
        check(root.findViewById<TextView>(R.id.widget_title).text.toString() == Stage12W010Oracle.TITLE &&
            root.findViewById<TextView>(R.id.widget_primary).text.toString() == Stage12W010Oracle.PRIMARY) { "W010_APPLIED_LITERAL" }
        check(root.findViewById<TextView>(R.id.widget_title).currentTextColor == palette.secondary &&
            root.findViewById<TextView>(R.id.widget_primary).currentTextColor == palette.primary &&
            (root.findViewById<View>(R.id.widget_accent).background as? ColorDrawable)?.color == palette.accent) { "W010_APPLIED_NEUTRAL_PALETTE" }
        val background = requireNotNull(root.background)
        val old = Rect(background.bounds)
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        try {
            background.setBounds(0, 0, 40, 40); background.draw(Canvas(bitmap))
            check(bitmap.getPixel(20, 20) == palette.background) { "W010_APPLIED_BACKGROUND" }
        } finally { background.bounds = old; bitmap.recycle() }
        val forbiddenIds = intArrayOf(R.id.widget_schedule, R.id.widget_countdown, R.id.widget_simultaneous,
            R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_weather_area,
            R.id.widget_weather, R.id.widget_weather_credit)
        forbiddenIds.forEach { id ->
            val field = root.findViewById<View>(id)
            check(field == null || field.visibility == View.GONE) { "W010_APPLIED_PRIVATE_FIELD_VISIBLE" }
            check(field == null || !field.hasOnClickListeners()) { "W010_APPLIED_HIDDEN_ACTION" }
        }
        check(root.hasOnClickListeners()) { "W010_APPLIED_ROOT_ACTION" }
        if (size == WidgetSize.EXPANDED) {
            val configure = root.findViewById<TextView>(R.id.widget_configure)
            check(configure.visibility == View.VISIBLE && configure.hasOnClickListeners() &&
                configure.currentTextColor == palette.action) { "W010_APPLIED_CONFIGURE_ACTION" }
            check(forbiddenIds.all { root.findViewById<View>(it) != null }) { "W010_APPLIED_EXPANDED_FIELDS" }
        }
        check(counts.nodes > 5 && counts.textFields > 1 && counts.accessibilityFields > 0 && counts.drawablePixels > 0) { "W010_APPLIED_INSPECTION_EMPTY" }
        return counts
    }

    fun allText(root: View): List<String> = buildList {
        if (root is TextView) add(root.text.toString())
        root.contentDescription?.let { add(it.toString()) }
        if (root is ViewGroup) for (index in 0 until root.childCount) addAll(allText(root.getChildAt(index)))
    }

    fun requireComplete(root: View, fixture: Stage12W010Fixture, size: WidgetSize) {
        check(allText(root).any { it.contains(fixture.institution) }) { "W010_VISIBLE_DETAILS_NOT_RESTORED" }
        check(root.hasOnClickListeners() && root.findViewById<View>(R.id.widget_schedule).visibility == View.VISIBLE &&
            root.findViewById<View>(R.id.widget_countdown).visibility == View.VISIBLE) { "W010_VISIBLE_STATE_NOT_RESTORED" }
        if (size == WidgetSize.EXPANDED) {
            for (id in intArrayOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_weather_area)) {
                val field = root.findViewById<View>(id)
                check(field.visibility == View.VISIBLE && field.hasOnClickListeners() && !field.contentDescription.isNullOrBlank()) {
                    "W010_VISIBLE_ACTION_NOT_RESTORED"
                }
            }
            val unused = root.findViewById<TextView>(R.id.widget_row_3)
            check(unused.visibility == View.GONE && unused.text.isNullOrEmpty() &&
                !unused.hasOnClickListeners() && unused.contentDescription.isNullOrEmpty()) { "W010_UNUSED_ROW_NOT_CLEARED" }
        }
    }

    /** Reduced/empty/day-off/status transitions may show dates, but cannot retain prior private details. */
    fun requireNoPriorDetails(root: View, fixture: Stage12W010Fixture) {
        fun visit(view: View) {
            val values = buildList {
                if (view is TextView) { add(view.text.toString()); add(view.hint?.toString().orEmpty()) }
                add(view.contentDescription?.toString().orEmpty())
                val node = view.createAccessibilityNodeInfo()
                try { add(node.text?.toString().orEmpty()); add(node.contentDescription?.toString().orEmpty()) }
                finally { @Suppress("DEPRECATION") node.recycle() }
            }
            val secrets = listOf(fixture.institution, fixture.type, fixture.abbreviation, fixture.position, fixture.address,
                fixture.sourceCanary, "PRIVATE_WEATHER_W010")
            check(values.none { text -> secrets.any { text.contains(it, ignoreCase = true) } }) { "W010_REAPPLY_PRIOR_DETAILS" }
            if (view.id == R.id.widget_weather_area || view.id in intArrayOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3)) {
                if (view.visibility == View.GONE) check(!view.hasOnClickListeners() && view.contentDescription.isNullOrEmpty()) {
                    "W010_REAPPLY_HIDDEN_ACTION"
                }
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
    }
}
