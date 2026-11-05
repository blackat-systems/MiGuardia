package com.blackatsystems.miguardia

import android.app.Activity
import android.content.Context

/** Non-portable capability supplied only by the verified runner for one own human session. */
internal interface QaExplicitHumanActionSession {
    val runId: String
    val ownerInstance: Activity
    fun retainFailure(error: Throwable)
    fun requireAction(context: QaExplicitHumanActionContext, operation: String)
    fun approximateLocationRequest(context: QaExplicitHumanActionContext, request: () -> Unit)
    fun approximateLocationResult(context: QaExplicitHumanActionContext)
    fun requireMapLaunch(owner: Activity, intent: android.content.Intent, requestCode: Int)
    fun requirePickerLaunch(owner: Activity, intent: android.content.Intent, requestCode: Int)
    fun pickerResult(owner: Activity, requestCode: Int, resultCode: Int, data: android.content.Intent?)
    fun pickerResultForwarded(owner: Activity, requestCode: Int)
    fun pickerLaunchReturned(owner: Activity, requestCode: Int, error: Throwable?)
}
internal interface QaExplicitHumanActionContext : QaMainActivityFileContext {
    val explicitHumanSession: QaExplicitHumanActionSession
    val humanOwnerInstance: Activity
}
