package com.blackatsystems.miguardia.upgrade

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking

/** Opt-in ONLY. Never starts MiGuardiaApplication, UI, services or a normal suite. */
class Stage10UpgradeRunner : Instrumentation() {
    private lateinit var arguments: Bundle
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        check(context.packageName == Stage10UpgradeContract.QA) { "QA target required" }
        return super.newApplication(cl, Application::class.java.name, context)
    }
    override fun callApplicationOnCreate(app: Application) {
        check(app.javaClass == Application::class.java) { "Neutral test application required" }
        super.callApplicationOnCreate(app)
    }
    override fun onCreate(arguments: Bundle?) {
        this.arguments = arguments ?: Bundle()
        super.onCreate(arguments)
        start()
    }
    override fun onStart() {
        var emulatorPackageInfo: android.content.pm.PackageInfo? = null
        try {
            check(Build.VERSION.SDK_INT >= 28)
            val target = targetContext
            check(target.applicationContext.javaClass == Application::class.java)
            check(target.applicationInfo.splitSourceDirs.isNullOrEmpty())
            check(context.packageName == "${Stage10UpgradeContract.QA}.test")
            val certificate = signer(target, target.packageName)
            check(signer(context, context.packageName) == certificate)
            val args = arguments.keySet().associateWith { requireNotNull(arguments.getString(it)) }
            val emulator = if ("stage10EmulatorOperation" in args) Stage10EmulatorContract.scenario(args) else null
            val apk = hash(File(target.applicationInfo.sourceDir))
            val request = if (emulator == null) Stage10UpgradeContract.request(args, target.packageName, context.packageName, certificate, apk)
                else {
                    check(Build.VERSION.SDK_INT == 36 && Build.SUPPORTED_ABIS.contains("x86_64"))
                    val properties = Class.forName("android.os.SystemProperties")
                    check(properties.getMethod("get", String::class.java).invoke(null, "ro.kernel.qemu") == "1")
                    Stage10EmulatorContract.request(args, target.packageName, context.packageName, certificate, apk)
                }
            if (emulator != null) emulatorPackageInfo = target.packageManager.getPackageInfo(target.packageName, 0)
            val profileBaseline = if (emulator == null) null else Stage10EmulatorContract.profileBaseline(args)
            val capture = runBlocking { Stage10UpgradeSession(target, context, emulator, profileBaseline).run(request) }
            finish(Activity.RESULT_OK, Bundle().apply {
                putString("stage10Status", "CAPTURE_COMPLETE")
                putString("stage10CaptureId", request.captureId)
                putString("stage10Bundle", capture.name)
                putString("stage10BundleSha256", hash(capture))
            })
        } catch (error: Throwable) {
            // No private paths/values, messages or stack traces. Validated synthetic
            // emulator requests may report one allowlisted static code site and package times.
            finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("stage10Status", "REJECTED_OR_FAILED_REVIEW_REQUIRED")
                putString("stage10ErrorType", error.javaClass.simpleName)
                emulatorPackageInfo?.let { info ->
                    putLong("stage10FirstInstallTime", info.firstInstallTime)
                    putLong("stage10LastUpdateTime", info.lastUpdateTime)
                    error.stackTrace.firstOrNull { frame -> frame.className in setOf(
                        Stage10EmulatorContract::class.java.name, Stage10UpgradeSession::class.java.name,
                    ) }?.let { frame ->
                        putString("stage10ErrorSite", "${frame.className.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}")
                    }
                }
            })
        }
    }
    private fun signer(context: Context, name: String): String {
        val info = context.packageManager.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
        val signers = requireNotNull(info.signingInfo).apkContentsSigners
        check(signers.size == 1)
        return digest(signers.single().toByteArray())
    }
    private fun hash(file: File): String = digest(file.readBytes())
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
