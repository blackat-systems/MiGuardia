package com.blackatsystems.miguardia.stage12

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import com.blackatsystems.miguardia.QaInstrumentationScope
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object Stage12Capture {
    private fun activity(context: Context): Activity? {
        var current = context
        repeat(20) {
            if (current is Activity) return current as Activity
            if (current !is ContextWrapper) return null
            val next = (current as ContextWrapper).baseContext
            if (next === current) return null
            current = next
        }
        return null
    }

    private fun validate(window: Window, owner: Activity, overlay: Window? = null, dialogValidation: Boolean = false) {
        check(owner.javaClass.name == "com.blackatsystems.miguardia.stage12.Stage12CaptureActivity")
        check(owner.packageName == "com.blackatsystems.miguardia.qa" && QaInstrumentationScope.isBound(owner))
        check(owner.baseContext is Stage12RootContext)
        check((window === owner.window || (dialogValidation && window === overlay)) && activity(window.decorView.context) === owner)
        check(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0) { "Protected window rejected" }
        check(window.decorView.isAttachedToWindow && window.decorView.width > 0 && window.decorView.height > 0)
        if (!window.decorView.hasWindowFocus()) {
            checkNotNull(overlay) { "Window lost focus to an unverified window" }
            check(activity(overlay.decorView.context) === owner && overlay.decorView.isAttachedToWindow && overlay.decorView.hasWindowFocus())
            check(overlay.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0)
        }
    }

    fun pixelCopy(window: Window, owner: Activity, overlay: Window? = null): Bitmap {
        check(window === owner.window)
        return copy(window, owner, overlay, false)
    }

    /** Only the synthetic own Dialog; callers must discard this in-memory validation bitmap. */
    fun pixelCopyOwnedDialogForValidation(window: Window, owner: Activity): Bitmap {
        check(window !== owner.window)
        return copy(window, owner, window, true)
    }

    private fun copy(window: Window, owner: Activity, overlay: Window?, dialogValidation: Boolean): Bitmap {
        check(Looper.myLooper() != Looper.getMainLooper()) { "PixelCopy must not block the UI thread" }
        val runner = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        var width = 0
        var height = 0
        runner.runOnMainSync { validate(window, owner, overlay, dialogValidation); width = window.decorView.width; height = window.decorView.height }
        check(width.toLong() * height in 1..16_000_000L)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        runner.runOnMainSync { PixelCopy.request(window, bitmap, { code -> result = code; latch.countDown() }, Handler(Looper.getMainLooper())) }
        check(latch.await(5, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS) { "Owned Window PixelCopy did not complete" }
        runner.runOnMainSync { validate(window, owner, overlay, dialogValidation); check(width == window.decorView.width && height == window.decorView.height) }
        return bitmap
    }

    fun tree(window: Window, owner: Activity, overlay: Window? = null): JSONObject {
        check(Looper.myLooper() == Looper.getMainLooper())
        validate(window, owner, overlay)
        var nodes = 0
        fun visit(view: View, depth: Int): JSONObject {
            check(depth <= 40 && ++nodes <= 1000)
            val password = view is TextView && (view.transformationMethod is PasswordTransformationMethod ||
                (view.inputType and InputType.TYPE_MASK_VARIATION) in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_NUMBER_VARIATION_PASSWORD))
            val result = JSONObject().put("class", view.javaClass.simpleName).put("width", view.width).put("height", view.height)
            result.put("description", if (password) "[REDACTED]" else view.contentDescription?.toString().orEmpty())
            if (view is TextView) result.put("text", if (password) "[REDACTED]" else view.text.toString())
            val children = JSONArray()
            if (view is ViewGroup) for (index in 0 until view.childCount) children.put(visit(view.getChildAt(index), depth + 1))
            return result.put("children", children)
        }
        return JSONObject().put("schema", "stage12-owned-view-tree-v1").put("root", visit(window.decorView, 0))
    }

    /** Call only after test-specific pixel assertions. No display screenshot or global tree APIs. */
    fun persistValidated(bitmap: Bitmap, tree: JSONObject) {
        val session = Stage12Session.current
        val file = session.artifact("owned-window.png")
        check(file.createNewFile())
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        session.writeJson("owned-tree.json", tree)
    }
}
