package com.blackatsystems.miguardia.stage12

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.blackatsystems.miguardia.QaInstrumentationScope

/** A synthetic, non-exported window. It is not MainActivity or a public export/share host. */
class Stage12CaptureActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(QaInstrumentationScope.wrapActivity(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        check(QaInstrumentationScope.isBound(this)) { "Capture host requires an admitted instrumentation scope" }
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.holo_green_light)
        setContentView(FrameLayout(this).apply { setBackgroundColor(Color.GREEN); contentDescription = "stage12-synthetic-green-host" })
    }
}
