package com.blackatsystems.miguardia.stage12

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.blackatsystems.miguardia.QaInstrumentationScope

/** QA-only owner. The real ActivityResultRegistry reaches this boundary; controlled registries do not. */
class Stage12HumanSessionActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
    @Deprecated("Framework ActivityResult launch boundary")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        check(options == null) { "HUMAN_PICKER_OPTIONS_NOT_ADMITTED" }
        if(requestCode == -1) {
            QaInstrumentationScope.requireExplicitHumanMap(this,intent,requestCode)
            super.startActivityForResult(intent,requestCode,options)
            return
        }
        QaInstrumentationScope.requireExplicitHumanPicker(this, intent, requestCode)
        try {
            super.startActivityForResult(intent, requestCode, options)
            QaInstrumentationScope.explicitHumanPickerReturned(this, requestCode, null)
        } catch (error: Throwable) {
            QaInstrumentationScope.explicitHumanPickerReturned(this, requestCode, error)
            throw error
        }
    }
    @Deprecated("Framework ActivityResult return boundary")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        QaInstrumentationScope.explicitHumanPickerResult(this,requestCode,resultCode,data)
        super.onActivityResult(requestCode,resultCode,data)
        QaInstrumentationScope.explicitHumanPickerResultForwarded(this,requestCode)
    }
}
