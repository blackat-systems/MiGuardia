package com.blackatsystems.miguardia.stage12

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class Stage12CaptureInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun scenario(): ActivityScenario<Activity> {
        val intent = Intent().setClassName("com.blackatsystems.miguardia.qa", "com.blackatsystems.miguardia.stage12.Stage12CaptureActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return ActivityScenario.launch(intent)
    }
    private fun awaitFrames(view: View) {
        check(Looper.myLooper() != Looper.getMainLooper())
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync { view.postOnAnimation { view.postOnAnimation { latch.countDown() } } }
        check(latch.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    @Test fun activityPixelCopyExcludesOwnDialogAndRedactsPassword() {
        scenario().use { scenario ->
            lateinit var owner: Activity
            lateinit var overlay: Dialog
            scenario.onActivity { activity ->
                owner = activity
                val content = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout
                content.addView(EditText(activity).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    setText("STAGE12_SYNTHETIC_PASSWORD")
                    contentDescription = "STAGE12_SYNTHETIC_PASSWORD"
                }, FrameLayout.LayoutParams(250, 80))
                overlay = Dialog(activity).apply {
                    setContentView(View(activity).apply { setBackgroundColor(Color.MAGENTA); contentDescription = "stage12-owned-magenta-overlay" })
                    show()
                    window!!.setLayout(220, 220)
                    window!!.setBackgroundDrawableResource(android.R.color.transparent)
                }
            }
            try {
                awaitFrames(overlay.window!!.decorView)
                val overlayBitmap = Stage12Capture.pixelCopyOwnedDialogForValidation(overlay.window!!, owner)
                val overlayPixels = IntArray(overlayBitmap.width * overlayBitmap.height)
                overlayBitmap.getPixels(overlayPixels, 0, overlayBitmap.width, 0, 0, overlayBitmap.width, overlayBitmap.height)
                assertTrue("Owned native overlay must actually render magenta", overlayPixels.count { it == Color.MAGENTA } > overlayPixels.size / 4)
                overlayBitmap.recycle() // Validation only; never persist the overlay image.
                val bitmap = Stage12Capture.pixelCopy(owner.window, owner, overlay.window)
                var green = 0
                var magenta = 0
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                pixels.forEach { if (it == Color.GREEN) green++; if (it == Color.MAGENTA) magenta++ }
                assertTrue("Synthetic Activity content must be visible", green > pixels.size / 4)
                assertEquals("Own Dialog must not enter the Activity surface capture", 0, magenta)
                lateinit var tree: JSONObject
                instrumentation.runOnMainSync { tree = Stage12Capture.tree(owner.window, owner, overlay.window) }
                assertTrue(tree.toString().contains("[REDACTED]"))
                assertFalse(tree.toString().contains("STAGE12_SYNTHETIC_PASSWORD"))
                Stage12Capture.persistValidated(bitmap, tree)
                bitmap.recycle()
                Stage12Session.current.record("S12-B04", "owned-window", "pixelcopy-activity-excludes-dialog", "ACTIVITY_NATIVE", "owned-window-pixelcopy")
                Stage12Session.current.record("S12-B04", "owned-window", "password-redacted-before-persist", "ACTIVITY_NATIVE", "owned-view-tree")
            } finally { instrumentation.runOnMainSync { overlay.dismiss() } }
        }
    }

    @Test fun protectedWindowRejectsCaptureWithoutRemovingSecureFlag() {
        scenario().use { scenario ->
            lateinit var owner: Activity
            scenario.onActivity { activity -> owner = activity; activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            awaitFrames(owner.window.decorView)
            val before = Stage12Session.current.evidence.listFiles()!!.map { it.name }.sorted()
            assertThrows(IllegalStateException::class.java) { Stage12Capture.pixelCopy(owner.window, owner) }
            instrumentation.runOnMainSync { assertTrue(owner.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
            assertEquals(before, Stage12Session.current.evidence.listFiles()!!.map { it.name }.sorted())
            Stage12Session.current.record("S12-B04", "protected-window", "secure-window-rejected-without-file", "ACTIVITY_NATIVE", "secure-window-guard")
            // Keep FLAG_SECURE for the rest of this owned Activity's lifetime.
        }
    }
}
