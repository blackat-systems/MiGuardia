package com.blackatsystems.miguardia.stage12

import android.app.KeyguardManager
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.StartupRecoveryState
import com.blackatsystems.miguardia.security.AccessLockPhase

/** Passive initial observation only. No input, tree, screenshot, ViewModel or business snapshot. */
internal object Stage12OwnActivityAdmission {
    private class WrongThread : IllegalStateException("OWN_ACTIVITY_READ_WRONG_THREAD")

    fun awaitReady(scenario: ActivityScenario<MainActivity>, context: Context, device: UiDevice,
                   admission: Stage12ActivityAdmission) {
        admission.phase(Stage12ActivityAdmission.Phase.READINESS)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            admission.readFailed(wrongThread = true)
            throw Stage12ReadinessRejected(admission)
        }
        val start = SystemClock.elapsedRealtime()
        while (true) {
            val snapshot = try {
                if (scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED) {
                    admission.activityDestroyed()
                    throw Stage12ReadinessRejected(admission)
                }
                val foreground = when (device.currentPackageName) {
                    QA -> Stage12ActivityAdmission.Foreground.OWN
                    null, "" -> Stage12ActivityAdmission.Foreground.NONE
                    else -> Stage12ActivityAdmission.Foreground.OTHER
                }
                val screen = device.isScreenOn
                val locked = (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
                var value: Stage12ActivityAdmission.Snapshot? = null
                scenario.onActivity { activity ->
                    if (Looper.myLooper() != Looper.getMainLooper()) throw WrongThread()
                    val app = activity.application as MiGuardiaApplication
                    val startup = when (app.startupRecoveryGate.state.value) {
                        StartupRecoveryState.Recovering -> Stage12ActivityAdmission.Startup.RECOVERING
                        StartupRecoveryState.Ready -> Stage12ActivityAdmission.Startup.READY
                        is StartupRecoveryState.Failed -> Stage12ActivityAdmission.Startup.FAILED
                    }
                    // onCreate already initialized this coordinator; do not read it before launching.
                    val access = app.accessLockCoordinator.state.value
                    val accessPhase = when (access.phase) {
                        AccessLockPhase.WAITING_FOR_RECOVERY -> Stage12ActivityAdmission.Access.WAITING
                        AccessLockPhase.LOADING -> Stage12ActivityAdmission.Access.LOADING
                        AccessLockPhase.READY -> Stage12ActivityAdmission.Access.READY
                        AccessLockPhase.STORE_ERROR -> Stage12ActivityAdmission.Access.FAILED
                    }
                    val decor = activity.window.peekDecorView()
                    val life = if (activity.isDestroyed || activity.isFinishing)
                        Stage12ActivityAdmission.Life.DESTROYED else
                        Stage12ActivityAdmission.Life.valueOf(activity.lifecycle.currentState.name)
                    value = Stage12ActivityAdmission.Snapshot(context.packageName == QA, foreground, screen, locked,
                        life, decor?.isAttachedToWindow, decor?.hasWindowFocus(),
                        startup, access.allowsSensitiveContent, true, accessPhase, decor != null)
                }
                requireNotNull(value)
            } catch (failure: Throwable) {
                admission.readFailed(failure is WrongThread || failure.cause is WrongThread)
                // Keep the original Throwable; the runner exposes only the fixed safe report in raw.
                throw failure
            }
            when (admission.observe(snapshot, SystemClock.elapsedRealtime() - start)) {
                Stage12ActivityAdmission.State.READY -> { admission.requireReady(); return }
                Stage12ActivityAdmission.State.REJECTED -> throw Stage12ReadinessRejected(admission)
                Stage12ActivityAdmission.State.PREPARING -> SystemClock.sleep(50)
            }
        }
    }

    const val QA = "com.blackatsystems.miguardia.qa"
}
