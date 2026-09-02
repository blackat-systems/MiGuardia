package com.blackatsystems.miguardia.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
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
        val permission = context.packageManager.getPermissionInfo(Manifest.permission.USE_BIOMETRIC, 0)

        assertTrue(Manifest.permission.USE_BIOMETRIC in requested)
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
    fun fragmentCompatibilityBridgeDispatchesTheRealPermissionCallback() {
        val permissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        val key = "access-lock-permission-bridge-${System.nanoTime()}"
        var callbackResult: Boolean? = null
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            scenario.onActivity { activity ->
                val launcher = activity.activityResultRegistry.register(
                    key,
                    ActivityResultContracts.RequestPermission(),
                ) { result ->
                    callbackResult = result
                }
                val registryClass = activity.activityResultRegistry.javaClass.superclass
                    ?: activity.activityResultRegistry.javaClass
                val keyToRequestCode = registryClass.getDeclaredField("keyToRc")
                keyToRequestCode.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val requestCode =
                    (keyToRequestCode.get(activity.activityResultRegistry) as Map<String, Int>)
                        .getValue(key)
                val launchedKeysField = registryClass.getDeclaredField("launchedKeys")
                launchedKeysField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                (launchedKeysField.get(activity.activityResultRegistry) as MutableList<String>) += key

                activity.onRequestPermissionsResult(
                    requestCode,
                    permissions,
                    intArrayOf(PackageManager.PERMISSION_DENIED),
                )
                launcher.unregister()
            }
        } finally {
            scenario.close()
        }

        assertEquals(false, callbackResult)
    }
}
