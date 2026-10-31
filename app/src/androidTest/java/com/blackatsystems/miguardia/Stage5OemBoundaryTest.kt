package com.blackatsystems.miguardia

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.backup.BackupActions
import com.blackatsystems.miguardia.backup.BackupStage
import com.blackatsystems.miguardia.backup.BackupSurfaceHost
import com.blackatsystems.miguardia.backup.BackupUiState
import com.blackatsystems.miguardia.ui.components.ObjectiveLocationCapture
import com.blackatsystems.miguardia.ui.management.UnifiedTemplateSelectionStep
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadActions
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadStage
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadUiState
import com.blackatsystems.miguardia.ui.photos.*
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import com.blackatsystems.miguardia.security.AccessLockStoreRead
import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Supervised real Android boundaries, productive Composables with isolated callback state.
 * No DB reset, personal media traversal, location capture, network, or credential handling.
 * Does NOT certify full MainActivity persistence or a human TalkBack review. */
class Stage5OemBoundaryTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val qa = "com.blackatsystems.miguardia.qa"
    private val documents = setOf("com.google.android.documentsui", "com.android.documentsui", "com.sec.android.app.myfiles")
    private val photos = setOf("com.google.android.providers.media.module", "com.android.providers.media.module", "com.google.android.photopicker", "com.samsung.android.providers.media") + documents
    private val permissions = setOf("com.google.android.permissioncontroller", "com.android.permissioncontroller")

    @Before fun requireExplicitSupervision() {
        check(context.packageName == qa)
        assumeTrue("Requires explicit operator and restoration protocol", InstrumentationRegistry.getArguments().getString("stage5OemAuthorized") == "true")
    }

    private fun phase(name: String, packages: Set<String>) {
        instrumentation.sendStatus(0, Bundle().apply {
            putString("oem_phase", name)
            putString("expected_packages", packages.sorted().joinToString(","))
        })
    }

    private fun awaitOwner(owners: Set<String>) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < end && device.currentPackageName !in owners) SystemClock.sleep(100)
        assertTrue("Expected bounded OEM owner", device.currentPackageName in owners)
    }

    private fun cancelExternal(owners: Set<String>) {
        awaitOwner(owners)
        repeat(3) {
            val owner = device.currentPackageName
            check(owner == qa || owner in owners) { "Unexpected owner: stop without input" }
            if (owner in owners) { device.pressBack(); SystemClock.sleep(500) }
        }
        awaitOwner(setOf(qa))
        phase("QA", emptySet())
    }

    @Test fun realKeyboardShowsEditsAndBackDismissesWithoutSaving() {
        var draft by mutableStateOf(V2ManualShiftLoadUiState(stage = V2ManualShiftLoadStage.CHOOSE_TEMPLATE, selectedDates = setOf(LocalDate.of(2026, 10, 9))))
        lateinit var rootView: android.view.View
        val writes = AtomicInteger()
        compose.setContent { rootView = LocalView.current; MiGuardiaTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            UnifiedTemplateSelectionStep(draft, V2ManualShiftLoadActions(updatePosition = { draft = draft.copy(position = it) }, save = { writes.incrementAndGet() }, requestSave = { writes.incrementAndGet() }), {})
        } } }
        compose.onNodeWithTag("manual-toggle-position").performScrollTo().performClick()
        phase("IME", setOf("com.samsung.android.honeyboard", "com.google.android.inputmethod.latin"))
        compose.onNodeWithTag("v2-manual-position").performScrollTo().performClick()
        compose.waitUntil(15_000) { ViewCompat.getRootWindowInsets(rootView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        // Input events enter the live editor while the OEM keyboard is visible; no suggestions read.
        check(device.currentPackageName == qa)
        device.executeShellCommand("input text QA_PUESTO_20261009")
        compose.waitUntil(10_000) { draft.position == "QA_PUESTO_20261009" }
        compose.onNodeWithTag("v2-manual-position").assertTextContains("QA_PUESTO_20261009")
        device.pressBack()
        compose.waitUntil(10_000) { ViewCompat.getRootWindowInsets(rootView)?.isVisible(WindowInsetsCompat.Type.ime()) != true }
        compose.onNodeWithTag("v2-manual-position").assertTextContains("QA_PUESTO_20261009")
        assertEquals(0, writes.get())
        phase("QA", emptySet())
    }

    @Test fun realPhotoPickerCancellationReturnsEmptyWithoutReadingGallery() {
        var callback: List<Uri>? = null
        compose.setContent { MiGuardiaTheme { PhotosSurfaceHost(PhotosUiState(surface = PhotosSurface.LIST, month = java.time.YearMonth.of(2026, 10), isLoading = false), PhotosActions(import = { callback = it }), SchedulePhotoFileStore(context)) } }
        phase("PHOTO_PICKER_CANCEL", photos)
        compose.onNodeWithText("Agregar fotos").performClick()
        cancelExternal(photos)
        compose.waitUntil(10_000) { callback != null }
        assertTrue(requireNotNull(callback).isEmpty())
        compose.onNodeWithText("Agregar fotos").assertIsDisplayed()
    }

    @Test fun realSafCreateCancellationReturnsNull() {
        val callbacks = AtomicInteger()
        var result: Uri? = null
        var state by mutableStateOf(BackupUiState(isOpen = true, stage = BackupStage.WAITING_FOR_CREATE_DESTINATION, suggestedName = "MIGUARDIA_QA_CANCEL_${UUID.randomUUID()}.miguardia-backup"))
        phase("SAF_CREATE_CANCEL", documents)
        compose.setContent { MiGuardiaTheme { BackupSurfaceHost(state, BackupActions(destinationSelected = { state = state.copy(stage = BackupStage.IDLE); result = it; callbacks.incrementAndGet() })) } }
        cancelExternal(documents)
        compose.waitUntil(10_000) { callbacks.get() == 1 }
        assertNull(result)
        compose.onNodeWithTag("backup-surface").assertIsDisplayed()
    }

    @Test fun realSafOpenCancellationReturnsNullWithoutReadingDocuments() {
        val callbacks = AtomicInteger()
        var result: Uri? = null
        var state by mutableStateOf(BackupUiState(isOpen = true, stage = BackupStage.WAITING_FOR_OPEN_SOURCE))
        phase("SAF_OPEN_CANCEL", documents)
        compose.setContent { MiGuardiaTheme { BackupSurfaceHost(state, BackupActions(sourceSelected = { state = state.copy(stage = BackupStage.IDLE); result = it; callbacks.incrementAndGet() })) } }
        cancelExternal(documents)
        compose.waitUntil(10_000) { callbacks.get() == 1 }
        assertNull(result)
        compose.onNodeWithTag("backup-surface").assertIsDisplayed()
    }

    @Test fun realSafWritesReadsAndDeletesOnlyNewSyntheticDocument() {
        val name = "MIGUARDIA_QA_OEM_${UUID.randomUUID()}.miguardia-backup"
        var result: Uri? = null
        var state by mutableStateOf(BackupUiState(isOpen = true, stage = BackupStage.WAITING_FOR_CREATE_DESTINATION, suggestedName = name))
        phase("SAF_NEW_DOCUMENT", documents)
        compose.setContent { MiGuardiaTheme { BackupSurfaceHost(state, BackupActions(destinationSelected = { state = state.copy(stage = BackupStage.IDLE); result = it })) } }
        awaitOwner(documents)
        val owner = requireNotNull(device.currentPackageName)
        // Exact known save control only: no directory listing, gallery capture or existing-file selection.
        val end = SystemClock.elapsedRealtime() + 10_000
        fun saveButton() = device.findObject(By.res(owner, "button1")) ?: device.findObject(By.res("android:id/button1"))
        var save = saveButton()
        while (save == null && SystemClock.elapsedRealtime() < end) { SystemClock.sleep(100); save = saveButton() }
        assertNotNull("Known SAF save button required; fail closed otherwise", save)
        check(device.currentPackageName == owner)
        requireNotNull(save).click()
        var ownedNewDocument = false
        try {
            compose.waitUntil(15_000) { result != null }
            awaitOwner(setOf(qa))
            phase("QA", emptySet())
            val uri = requireNotNull(result)
            // Verify the returned document is exactly the proposed disposable name before touching bytes.
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)!!.use { cursor ->
                check(cursor.moveToFirst()); assertEquals(name, cursor.getString(0)); assertEquals(0L, cursor.getLong(1))
            }
            ownedNewDocument = true
            val bytes = "MIGUARDIA QA synthetic writer-only fixture; not a valid backup".toByteArray()
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
            val restored = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertArrayEquals(bytes, restored)
        } finally {
            if (ownedNewDocument) result?.let { assertTrue("Delete exact new fixture", DocumentsContract.deleteDocument(context.contentResolver, it)) }
        }
    }

    @Test fun realLocationDenialKeepsAlternativesWithoutCapturingCoordinates() {
        check(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED) { "Operator must snapshot and temporarily revoke QA coarse location first" }
        val captures = AtomicInteger()
        compose.setContent { MiGuardiaTheme { Column { ObjectiveLocationCapture("", false, onLocationCaptured = { _, _ -> captures.incrementAndGet(); true }) } } }
        assertEquals(0, captures.get())
        phase("LOCATION_DENY", permissions)
        compose.onNodeWithTag("objective-location-capture").performClick()
        awaitOwner(permissions)
        val owner = requireNotNull(device.currentPackageName)
        val end = SystemClock.elapsedRealtime() + 10_000
        // Google's installed controller retains com.android.permissioncontroller resource names.
        fun denyButton() = permissions.firstNotNullOfOrNull { resourcePackage ->
            device.findObject(By.res(resourcePackage, "permission_deny_button"))
                ?: device.findObject(By.res(resourcePackage, "permission_deny_and_dont_ask_again_button"))
        }
        var deny = denyButton()
        while (deny == null && SystemClock.elapsedRealtime() < end) { SystemClock.sleep(100); deny = denyButton() }
        assertNotNull("Known deny control required", deny)
        check(device.currentPackageName == owner)
        requireNotNull(deny).click()
        awaitOwner(setOf(qa)); phase("QA", emptySet())
        compose.onNodeWithText("El permiso fue rechazado. Podés usar una dirección, continuar sin clima o habilitarlo desde Ajustes.").assertIsDisplayed()
        compose.onNodeWithTag("objective-location-open-settings").assertIsDisplayed()
        compose.onNodeWithTag("objective-location-capture").assertIsEnabled()
        assertEquals(0, captures.get())
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    @Test fun supervisedRealAccessLockCancelsAuthenticatesProtectsAndRestores() {
        val app = context.applicationContext as MiGuardiaApplication
        compose.waitUntil(15_000) { app.startupRecoveryGate.isReady }
        val before = runBlocking { app.accessLockPreferences.read() } as AccessLockStoreRead.Ready
        check(!before.configuration.enabled) { "Existing enabled lock needs a separate preservation protocol" }
        val systemAuth = setOf("com.android.systemui", "com.android.settings", "com.samsung.android.biometrics.app.setting")
        var scenario: ActivityScenario<MainActivity>? = null
        fun awaitTag(tag: String) = compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitTag("main-menu-button")
            compose.onNodeWithTag("main-menu-button").performClick()
            compose.onNodeWithTag("main-destination-access_lock").performScrollTo().performClick()
            awaitTag("access-lock-settings")
            phase("BIOMETRIC_CANCEL", systemAuth)
            compose.onNodeWithTag("access-lock-enabled-control").performScrollTo().performClick()
            compose.waitUntil(10_000) { app.accessLockCoordinator.state.value.authenticationInProgress }
            // Only the authenticator launched by this exact QA action is cancelled.
            val end = SystemClock.elapsedRealtime() + 10_000
            while (device.currentPackageName !in systemAuth && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100)
            check(device.currentPackageName in systemAuth)
            device.pressBack()
            compose.waitUntil(15_000) { !app.accessLockCoordinator.state.value.authenticationInProgress }
            assertEquals(before, runBlocking { app.accessLockPreferences.read() })
            assertTrue(app.accessLockCoordinator.state.value.allowsSensitiveContent)
            phase("HUMAN_ENABLE", systemAuth)
            compose.onNodeWithTag("access-lock-enabled-control").performScrollTo().performClick()
            compose.waitUntil(180_000) { app.accessLockCoordinator.state.value.configuration?.enabled == true && !app.accessLockCoordinator.state.value.persistenceInProgress }
            phase("QA", emptySet())
            compose.onNodeWithTag("access-lock-now").performScrollTo().performClick()
            awaitTag("access-lock-gate")
            assertFalse(app.accessLockCoordinator.state.value.allowsSensitiveContent)
            compose.onNodeWithTag("month-grid").assertDoesNotExist()
            compose.onNodeWithTag("main-menu-button").assertDoesNotExist()
            phase("HUMAN_UNLOCK", systemAuth)
            compose.onNodeWithTag("access-lock-unlock").performClick()
            compose.waitUntil(180_000) { app.accessLockCoordinator.state.value.allowsSensitiveContent }
            phase("QA", emptySet())
            awaitTag("access-lock-settings")
            phase("HUMAN_DISABLE", systemAuth)
            compose.onNodeWithTag("access-lock-enabled-control").performScrollTo().performClick()
            compose.waitUntil(180_000) { app.accessLockCoordinator.state.value.configuration?.enabled == false && !app.accessLockCoordinator.state.value.persistenceInProgress }
            assertEquals(before, runBlocking { app.accessLockPreferences.read() })
            phase("QA", emptySet())
            scenario.recreate(); awaitTag("main-menu-button")
            assertTrue(app.accessLockCoordinator.state.value.allowsSensitiveContent)
        } finally {
            scenario?.close()
            // Cleanup only, never used as authentication evidence or to continue protected assertions.
            runBlocking { app.accessLockPreferences.replace(before.configuration); app.accessLockCoordinator.initializeAfterRecovery() }
            assertEquals(before, runBlocking { app.accessLockPreferences.read() })
            instrumentation.sendStatus(0, Bundle().apply { putString("qa_lock_configuration_restored", "true") })
        }
    }

    @Test fun supervisedTalkBackRemainsConnectedForAccessibleSelectionAndCancellation() {
        val manager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        val bridge = instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        compose.waitUntil(30_000) {
            manager.isTouchExplorationEnabled && manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_SPOKEN).any { it.id == "com.samsung.android.accessibility.talkback/com.samsung.android.marvin.talkback.TalkBackService" }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("main-menu-button").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            fun accessibleAction(label: String) {
                var target: android.view.accessibility.AccessibilityNodeInfo? = null
                fun search(node: android.view.accessibility.AccessibilityNodeInfo) {
                    if (node.packageName?.toString() != qa) return
                    if (node.contentDescription?.toString() == label || node.text?.toString() == label) {
                        var clickable = node
                        while (!clickable.isClickable && clickable.parent?.packageName?.toString() == qa) clickable = clickable.parent
                        if (clickable.isClickable) target = clickable
                    }
                    for (i in 0 until node.childCount) node.getChild(i)?.let(::search)
                }
                compose.waitUntil(15_000) {
                    bridge.rootInActiveWindow?.takeIf { it.packageName?.toString() == qa }?.let(::search)
                    target != null
                }
                val node = requireNotNull(target)
                assertTrue("Accessibility focus for exact QA control", node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                assertTrue("Accessibility activation for exact QA control", node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                instrumentation.sendStatus(0, Bundle().apply { putString("talkback_QA_target", label) })
            }
            accessibleAction("Abrir menú")
            accessibleAction("Seleccionar días")
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("calendar-selection-cancel").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            scenario.onActivity { assertEquals(CalendarInteractionMode.EDIT, ViewModelProvider(it)[CalendarViewModel::class.java].uiState.value.interactionMode) }
            accessibleAction("Cancelar")
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("calendar-selection-cancel").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
            scenario.onActivity { assertEquals(CalendarInteractionMode.VIEW, ViewModelProvider(it)[CalendarViewModel::class.java].uiState.value.interactionMode) }
            assertTrue(manager.isTouchExplorationEnabled)
        }
    }
}
