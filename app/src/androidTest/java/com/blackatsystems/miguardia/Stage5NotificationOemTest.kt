package com.blackatsystems.miguardia

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationSystemAccessState
import com.blackatsystems.miguardia.ui.notifications.*
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real OEM results with productive notification UI and callback doubles. Never sends alerts. */
class Stage5NotificationOemTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val qa = "com.blackatsystems.miguardia.qa"
    private val permissionPackages = setOf("com.google.android.permissioncontroller", "com.android.permissioncontroller")
    @Before fun requireSupervision() {
        check(context.packageName == qa)
        assumeTrue(InstrumentationRegistry.getArguments().getString("stage5OemAuthorized") == "true")
    }
    private fun phase(name: String) { instrumentation.sendStatus(0, Bundle().apply { putString("oem_phase", name) }) }
    private fun awaitOwner(owners: Set<String>) {
        val end = SystemClock.elapsedRealtime()+15_000
        while (device.currentPackageName !in owners && SystemClock.elapsedRealtime()<end) SystemClock.sleep(100)
        assertTrue("Expected bounded OEM owner", device.currentPackageName in owners)
    }
    @Test fun realNotificationsPermissionDenialRefreshesWithoutSendingAnAlert() {
        check(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED)
        val refreshes = AtomicInteger(); val alerts = AtomicInteger()
        compose.setContent { MiGuardiaTheme { NotificationSurfaceHost(
            NotificationUiState(surface=NotificationSurface.GLOBAL,isLoading=false,preferences=NotificationPreferences(enabled=true)),
            NotificationActions(refreshSystemAccess={refreshes.incrementAndGet()},sendTestNotification={alerts.incrementAndGet()}),
        ) } }
        phase("NOTIFICATIONS_DENY")
        compose.onNodeWithText("Permiso de Android: pendiente").performScrollTo().assertIsDisplayed()
        // SectionCard flattens layout-only Rows in the merged semantics tree. The first of the
        // two resolvers is Android's runtime permission; the OEM owner below proves that routing.
        compose.onAllNodesWithText("Resolver").assertCountEquals(2)
        compose.onAllNodesWithText("Resolver")[0].performClick()
        awaitOwner(permissionPackages)
        val end=SystemClock.elapsedRealtime()+10_000
        var deny = permissionPackages.firstNotNullOfOrNull { device.findObject(By.res(it,"permission_deny_button")) }
        while (deny==null && SystemClock.elapsedRealtime()<end) {
            SystemClock.sleep(100); deny=permissionPackages.firstNotNullOfOrNull { device.findObject(By.res(it,"permission_deny_button")) }
        }
        assertNotNull("Known denial control",deny);check(device.currentPackageName in permissionPackages);requireNotNull(deny).click()
        awaitOwner(setOf(qa));phase("QA")
        compose.waitUntil(10_000) {refreshes.get()==1}
        compose.onNodeWithTag("notification-close").assertIsDisplayed()
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        assertEquals(0,alerts.get())
    }
    @Test fun realRingtoneBackPreservesExistingSoundEvenIfOemCommits() {
        val existing=android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        val returned=java.util.concurrent.CopyOnWriteArrayList<android.net.Uri?>()
        compose.setContent { MiGuardiaTheme { NotificationSurfaceHost(
            NotificationUiState(surface=NotificationSurface.GLOBAL,isLoading=false,preferences=NotificationPreferences(enabled=true,soundUri=existing),systemAccess=NotificationSystemAccessState(true,true)),
            NotificationActions(setSound={returned.add(it)}),
        ) } }
        phase("RINGTONE_CANCEL")
        compose.onNodeWithText("Elegir sonido").performScrollTo().performClick()
        val owners=setOf("com.samsung.android.secsoundpicker","com.android.soundpicker","com.google.android.soundpicker")
        awaitOwner(owners)
        repeat(3) {
            check(device.currentPackageName in owners || device.currentPackageName==qa)
            if (device.currentPackageName in owners) { device.pressBack();SystemClock.sleep(500) }
        }
        awaitOwner(setOf(qa));phase("QA")
        compose.onNodeWithText("Elegir sonido").assertIsDisplayed()
        // Samsung may return RESULT_OK on Back. The invariant is unchanged effective sound,
        // not a fictitious RESULT_CANCELED. Use a public system alias, never a personal ringtone.
        assertTrue("At most one OEM result",returned.size<=1)
        assertTrue("Back must preserve the existing sound",returned.all { it==existing })
        instrumentation.sendStatus(0,Bundle().apply {putString("ringtone_back_result",if(returned.isEmpty()) "CANCELLED_NO_WRITE" else "COMMITTED_SAME_SYSTEM_ALIAS")})
    }
}
