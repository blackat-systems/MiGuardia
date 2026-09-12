package com.blackatsystems.miguardia

import androidx.test.core.app.ApplicationProvider
import com.blackatsystems.miguardia.updates.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.Assume.assumeTrue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import android.content.Intent
import java.io.File
import java.util.UUID


/** Device-only package inspector precondition. Full public SAF/upgrade needs the signed Release in stage 21. */
class UpdatesOemSafActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun supervisedOemWriterCancelSaveAndReturnUsesNoPublicationAuthority() {
        assumeTrue("Specific operator authorization for OEM writer-only fixture required",
            InstrumentationRegistry.getArguments().getString("updatesPublicSaf") == "authorized")
        val app = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        check(app.packageName.endsWith(".qa"))
        compose.waitUntil(15000) { app.startupRecoveryGate.isReady }
        check(app.accessLockCoordinator.state.value.allowsSensitiveContent)
        val folder = File(app.cacheDir,"public-saf-${UUID.randomUUID()}").apply { check(mkdir()) }
        val bytes = "Synthetic writer-only data; not an APK; do not install".toByteArray()
        val file = File(folder,"source").apply { writeBytes(bytes) }
        val installed = InstalledUpdateApp(InstalledAppIdentity(PUBLIC_PACKAGE,PUBLIC_CERTIFICATE,2),"2.0.0-fixture",26)
        val release = UpdateRelease("public_writer_fixture","2.0.0-fixture",3,PUBLIC_PACKAGE,26,
            bytes.size.toLong(),updateHash(bytes),PUBLIC_CERTIFICATE,Instant.EPOCH)
        val operations = object : UpdateOperations {
            override fun installed() = installed
            override suspend fun release() = release
            override suspend fun select(release: UpdateRelease, installed: InstalledUpdateApp) = UpdateSelection(release,installed,this,0)
            override suspend fun download(selection: UpdateSelection, allowed: () -> Boolean, progress: (Long)->Unit, verifying: ()->Unit): VerifiedUpdate {
                check(allowed()); progress(bytes.size.toLong()); verifying(); return VerifiedUpdate(selection,file)
            }
            override suspend fun validate(artifact: VerifiedUpdate, allowed: () -> Boolean) { check(allowed()); check(file.readBytes().contentEquals(bytes)) }
            override fun discard(artifact: VerifiedUpdate) = Unit
        }
        // Test-only replacement; production service/pin is never weakened. PackageManager is not tested here.
        val field = MiGuardiaApplication::class.java.getDeclaredField("updateOperations\$delegate").apply { isAccessible = true }
        val previous = field.get(app)
        try {
            field.set(app,lazyOf(operations)); check(app.updateOperations === operations)
            ActivityScenario.launch<MainActivity>(Intent(app,MainActivity::class.java)).use { scenario ->
                // Returning from the external picker can temporarily leave no Compose root.
                // Keep the same deadline and require the target before proceeding.
                fun awaitTag(tag: String) { compose.waitUntil(15000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() } }
                awaitTag("month-grid")
                compose.onNodeWithContentDescription("Abrir menú").performClick()
                compose.onNodeWithTag("main-destination-help").performScrollTo().performClick()
                compose.onNodeWithContentDescription("Abrir menú").performClick()
                compose.onNodeWithTag("drawer-action-updates").performScrollTo().performClick()
                awaitTag("updates-screen")
                compose.onNodeWithTag("updates-search").performScrollTo().performClick()
                awaitTag("updates-download"); compose.onNodeWithTag("updates-download").performScrollTo().performClick()
                awaitTag("updates-save"); compose.onNodeWithTag("updates-save").performScrollTo().performClick()
                val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                val pickerPackages=setOf("com.google.android.documentsui","com.android.documentsui","com.sec.android.app.myfiles")
                val deadline=android.os.SystemClock.elapsedRealtime()+15000
                while(device.currentPackageName !in pickerPackages && android.os.SystemClock.elapsedRealtime()<deadline) Thread.sleep(100)
                check(device.currentPackageName in pickerPackages)
                device.waitForIdle()
                // Samsung/DocumentsUI may consume the first Back to dismiss its keyboard.
                // Only repeat while the picker still owns the foreground; never back out of MiGuardia.
                repeat(3) {
                    val picker = device.currentPackageName
                    if (picker in pickerPackages) {
                        device.pressBack()
                        device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.pkg(picker)), 1500)
                    }
                }
                check(device.currentPackageName !in pickerPackages)
                awaitTag("updates-save")
                compose.onNodeWithTag("updates-save").performScrollTo().performClick()
                // Operator selects a NEW disposable document, notes exact provider/path and returns.
                InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply {
                    putString("public_saf_stage","SAVE_NEW_DISPOSABLE_DOCUMENT_AND_RETURN; NOT_AN_APK")
                })
                compose.waitUntil(180000) {
                    compose.onAllNodesWithText("APK guardado. La actualización todavía no está instalada.").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                }
                compose.onNodeWithTag("updates-back").performScrollTo().performClick()
                awaitTag("help-screen"); scenario.recreate(); awaitTag("help-screen")
                File(folder,"verified.txt").writeText("Writer-only OEM cancel/save/return/recreation; no APK inspection, network, installation or upgrade.")
            }
        } finally {
            field.set(app,previous); check(field.get(app) === previous)
            check(file.delete()) // Exact fixture only; external document cleanup belongs to operator.
        }
    }

    @Test fun qaInstallationIsExplicitlyIncompatibleWithPublicRelease() {
        val app = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
        check(app.packageName.endsWith(".qa"))
        val installed = AndroidUpdateApkInspector(app).installed()
        val release = UpdateRelease("public_stage21", "2.0.0", installed.identity.versionCode + 1,
            PUBLIC_PACKAGE, 26, 1, "a".repeat(64), PUBLIC_CERTIFICATE, Instant.parse("2026-09-09T00:00:00Z"))
        assertEquals(UpdateAvailability.INCOMPATIBLE, release.availability(installed))
    }
}
