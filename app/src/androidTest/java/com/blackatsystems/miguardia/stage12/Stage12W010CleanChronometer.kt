package com.blackatsystems.miguardia.stage12

import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import com.blackatsystems.miguardia.R
import java.util.Locale

/** Controls are new public Android components. No fixture or candidate text is assigned to them. */
internal class Stage12W010CleanChronometer private constructor(private val context: Context,
    private val before: Map<Stage12W010Observation.Field, Sample>) {
    private val consumed = mutableSetOf<Stage12W010Observation.Field>()
    // Non-data classes: accidental toString must never print descriptions, clock values or configuration.
    private class Sample(val start: Long, val end: Long, val configuration: Configuration,
                         val locale: Locale, val formatLocale: Locale, val stable: Boolean,
                         val exactClassAndNeutral: Boolean, val description: String?, val ignoresMarkerAndReset: Boolean)

    fun scopeBefore(candidate: View, effectivelyVisible: Boolean): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper()) { "W010_PUBLIC_CONTROL_WRONG_THREAD" }
        return candidate.javaClass == Chronometer::class.java && candidate.id == R.id.widget_countdown &&
            candidate.context === context && !effectivelyVisible && candidate.visibility == View.GONE &&
            (candidate as Chronometer).base == 0L && candidate.format == null && !candidate.isCountDown && candidate.text.isNullOrEmpty()
    }

    fun observe(candidate: Chronometer, value: String, field: Stage12W010Observation.Field,
                effectivelyVisible: Boolean, fixture: Stage12W010Fixture, neutralBeforeReading: Boolean): Stage12W010PublicDescription.Evidence {
        check(Looper.myLooper() == Looper.getMainLooper()) { "W010_PUBLIC_CONTROL_WRONG_THREAD" }
        check(consumed.add(field)) { "W010_PUBLIC_CONTROL_PROOF_REUSED" }
        val initial = checkNotNull(before[field]) { "W010_PUBLIC_CONTROL_FIELD_NOT_PREPARED" }
        val after = sample(context, field)
        val first = initial.description
        val second = after.description
        fun rejectedClean(text: String?): Boolean {
            if (text.isNullOrEmpty() || !text.any(Char::isDigit) || fixture.forbiddenText.any { text.contains(it, ignoreCase = true) }) return false
            return try { Stage12W010Oracle.requireNoSecretText(listOf(text), fixture); false }
            catch (error: IllegalStateException) { if (error.message != "W010_APPLIED_SECRET_TEXT") throw error; true }
        }
        return Stage12W010PublicDescription.Evidence(field,
            candidate.javaClass == Chronometer::class.java && candidate.id == R.id.widget_countdown && candidate.context === context,
            neutralBeforeReading && !effectivelyVisible && candidate.visibility == View.GONE && candidate.base == 0L && candidate.format == null &&
                !candidate.isCountDown && candidate.text.isNullOrEmpty(),
            fixture.forbiddenText.any { value.contains(it, ignoreCase = true) },
            initial.exactClassAndNeutral && after.exactClassAndNeutral,
            rejectedClean(first) && rejectedClean(second),
            initial.ignoresMarkerAndReset && after.ignoresMarkerAndReset,
            Stage12W010PublicDescription.boundedWindow(initial.start, initial.end, after.start, after.end),
            initial.stable && after.stable && initial.configuration == after.configuration &&
                initial.locale == after.locale && initial.formatLocale == after.formatLocale,
            first != null && first == second,
            first != null && value == first && value == second)
    }

    companion object {
        private const val MARKER = "PUBLIC_CHRONOMETER_CONTROL"
        fun beforeReapply(context: Context): Stage12W010CleanChronometer {
            check(Looper.myLooper() == Looper.getMainLooper()) { "W010_PUBLIC_CONTROL_WRONG_THREAD" }
            // Each field gets its own newly constructed before/after components and proof.
            return Stage12W010CleanChronometer(context, listOf(Stage12W010Observation.Field.VIEW_DESCRIPTION,
                Stage12W010Observation.Field.NODE_DESCRIPTION).associateWith { sample(context, it) })
        }

        private fun nodeDescription(view: Chronometer): String? {
            val node = view.createAccessibilityNodeInfo()
            return try { node.contentDescription?.toString() }
            finally { @Suppress("DEPRECATION") node.recycle() }
        }

        private fun sample(context: Context, field: Stage12W010Observation.Field): Sample {
            val start = SystemClock.elapsedRealtime()
            val config = Configuration(context.resources.configuration)
            val locale = Locale.getDefault()
            val formatLocale = Locale.getDefault(Locale.Category.FORMAT)
            val clean = Chronometer(context)
            try {
                clean.stop(); clean.visibility = View.GONE; clean.contentDescription = null
                clean.base = 0L; clean.format = null; clean.isCountDown = false; clean.text = ""
                fun read(): String? = when (field) {
                    Stage12W010Observation.Field.VIEW_DESCRIPTION -> clean.contentDescription?.toString()
                    Stage12W010Observation.Field.NODE_DESCRIPTION -> nodeDescription(clean)
                    else -> error("W010_PUBLIC_CONTROL_FIELD_NOT_ALLOWED")
                }
                val description = read()
                clean.contentDescription = MARKER
                val withMarker = read()
                clean.contentDescription = null
                val afterReset = read()
                val ignoresMarker = description != MARKER && withMarker == description && afterReset == description
                val neutral = clean.javaClass == Chronometer::class.java && clean.visibility == View.GONE &&
                    clean.base == 0L && clean.format == null && !clean.isCountDown && clean.text.isNullOrEmpty()
                val stable = config == Configuration(context.resources.configuration) && locale == Locale.getDefault() &&
                    formatLocale == Locale.getDefault(Locale.Category.FORMAT)
                return Sample(start, SystemClock.elapsedRealtime(), config, locale, formatLocale, stable, neutral,
                    description, ignoresMarker)
            } finally { clean.stop() }
        }
    }
}
