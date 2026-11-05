package com.blackatsystems.miguardia.stage12

import android.content.Context
import java.io.File

/** Trusted values originate exclusively in the current QA Context, never instrumentation path arguments. */
internal fun stage12RecurringPathAccess(context: Context): Stage12RecurringPathAccess {
    check(context.packageName == "com.blackatsystems.miguardia.qa")
    return object : Stage12RecurringPathAccess {
        override fun application() = File(context.applicationInfo.dataDir)
        override fun database(name: String) = context.getDatabasePath(name)
        override fun files() = context.filesDir
    }
}
