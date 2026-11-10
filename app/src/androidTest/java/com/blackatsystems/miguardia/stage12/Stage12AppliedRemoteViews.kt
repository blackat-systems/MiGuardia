package com.blackatsystems.miguardia.stage12

import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.RemoteViews
import com.blackatsystems.miguardia.R
import kotlin.math.roundToInt

/** Applies actual Android RemoteViews inside an unregistered, owned View host. No launcher/widget IDs. */
internal object Stage12AppliedRemoteViews {
    fun apply(context: Context, remoteViews: RemoteViews): View {
        check(Looper.myLooper() == Looper.getMainLooper()) { "APPLIED_WIDGET_REQUIRES_MAIN" }
        check(context.packageName == "com.blackatsystems.miguardia.qa") { "APPLIED_WIDGET_REQUIRES_QA" }
        val themed = ContextThemeWrapper(context, R.style.Theme_MiGuardia)
        val density = themed.resources.displayMetrics.density
        check(density.isFinite() && density > 0f)
        val configuration = themed.resources.configuration
        val widthDp = configuration.screenWidthDp.takeIf { it > 0 } ?: 360
        val heightDp = configuration.screenHeightDp.takeIf { it > 0 } ?: 480
        check(widthDp in 96..1600 && heightDp in 64..2400) { "APPLIED_WIDGET_HOST_BOUNDS" }
        val width = (widthDp * density).roundToInt()
        val height = (heightDp * density).roundToInt()
        val host = FrameLayout(themed)
        val view = remoteViews.apply(themed, host)
        host.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, width, height)
        check(view.parent === host && view.width > 0 && view.height > 0)
        return view
    }

    /** Stops only the timers and children belonging to the returned in-memory host. */
    fun release(view: View) {
        check(Looper.myLooper() == Looper.getMainLooper())
        fun stop(current: View) {
            if (current is Chronometer) current.stop()
            current.clearAnimation()
            if (current is ViewGroup) for (index in 0 until current.childCount) stop(current.getChildAt(index))
        }
        stop(view)
        val host = view.parent as? FrameLayout
        check(host == null || host.childCount == 1 && host.getChildAt(0) === view)
        host?.removeView(view)
        check(view.parent == null)
    }
}
