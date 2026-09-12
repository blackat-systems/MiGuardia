package com.blackatsystems.miguardia.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.MainActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessLockManifestInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun biometricPermissionIsNormalAndNoComponentWasAddedForTheLock() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_SERVICES,
        )
        val requested = packageInfo.requestedPermissions.orEmpty().toSet()

        assertTrue(Manifest.permission.USE_BIOMETRIC in requested)
        val supportedPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Manifest.permission.USE_BIOMETRIC
        } else {
            assertTrue(Manifest.permission.USE_FINGERPRINT in requested)
            Manifest.permission.USE_FINGERPRINT
        }
        val permission = context.packageManager.getPermissionInfo(supportedPermission, 0)
        assertEquals(
            android.content.pm.PermissionInfo.PROTECTION_NORMAL,
            permission.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE,
        )
        assertEquals(
            setOf(
                "com.blackatsystems.miguardia.MainActivity",
                "com.blackatsystems.miguardia.widget.WidgetConfigurationActivity",
            ),
            packageInfo.activities.orEmpty().map { it.name }.filter {
                it.startsWith("com.blackatsystems.miguardia.")
            }.toSet(),
        )
        assertEquals(3, packageInfo.receivers.orEmpty().count {
            it.name.startsWith("com.blackatsystems.miguardia.")
        })
        assertFalse(packageInfo.services.orEmpty().any {
            it.name.startsWith("com.blackatsystems.miguardia.")
        })
    }

    @Test
    fun accessLockStoreLivesInsideTheCurrentVariantSandboxAndPersistsNoSession() = runBlocking {
        val file = context.preferencesDataStoreFile(AccessLockPreferencesStore.DEFAULT_FILE_NAME)
        val testFile = context.preferencesDataStoreFile("instrumented-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = AccessLockPreferencesStore(testFile, scope)
        try {
            store.replace(AccessLockConfiguration(true, AccessLockTimeout.FIFTEEN_MINUTES))

            assertTrue(file.canonicalPath.startsWith(context.filesDir.canonicalPath))
            assertTrue(file.canonicalPath.contains(context.packageName))
            assertEquals(
                AccessLockStoreRead.Ready(
                    AccessLockConfiguration(true, AccessLockTimeout.FIFTEEN_MINUTES),
                ),
                store.read(),
            )
            assertFalse(file.name.contains("session", ignoreCase = true))
        } finally {
            scope.cancel()
            testFile.delete()
        }
    }

    @Test
    fun activityResultPermissionLauncherCompletesWithoutLegacyRequestCodeCrash() {
        val key = "permission-launcher-${System.nanoTime()}"
        var callbackResult: Boolean? = null
        var launcher: ActivityResultLauncher<String>? = null
        val callback = CountDownLatch(1)
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            scenario.onActivity { activity ->
                val registeredLauncher = activity.activityResultRegistry.register(
                    key,
                    ActivityResultContracts.RequestPermission(),
                ) { result ->
                    callbackResult = result
                    callback.countDown()
                }
                launcher = registeredLauncher
                registeredLauncher.launch(Manifest.permission.CAMERA)
            }
            assertTrue(callback.await(10, TimeUnit.SECONDS))
        } finally {
            scenario.onActivity { launcher?.unregister() }
            scenario.close()
        }

        assertEquals(false, callbackResult)
    }
}
