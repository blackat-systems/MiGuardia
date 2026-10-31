package com.blackatsystems.miguardia

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.blackatsystems.miguardia.ui.photos.PhotosSurface
import com.blackatsystems.miguardia.ui.photos.PhotosActions
import com.blackatsystems.miguardia.ui.photos.PhotosSurfaceHost
import com.blackatsystems.miguardia.ui.photos.PhotosViewModel
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import com.blackatsystems.miguardia.ui.calendar.CalendarInteractionMode
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Supervised positive Photo Picker and human speech window. No personal media or DB reset. */
class Stage5PositiveMediaAndSpeechTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val qa = "com.blackatsystems.miguardia.qa"
    private val app get() = context.applicationContext as MiGuardiaApplication
    @Before fun requireSupervision() {
        check(context.packageName == qa)
        assumeTrue(InstrumentationRegistry.getArguments().getString("stage5OemAuthorized") == "true")
    }
    private fun phase(name: String) = instrumentation.sendStatus(0, Bundle().apply { putString("oem_phase", name) })
    private fun status(key: String, value: String) = instrumentation.sendStatus(0, Bundle().apply { putString(key, value) })
    private fun awaitTag(tag: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** No picker here: isolate the recreated viewer's native return with one private fixture. */
    @Test fun diagnosticNativeViewerReturnAfterRecreationWithPrivateFixture() {
        val month=YearMonth.of(2088,1)
        val repository=app.localDataStore.schedulePhotos
        check(runBlocking { repository.observeForMonth(month).first() }.isEmpty())
        val source=java.io.File(context.filesDir,"reports/artifacts/qa-viewer-${UUID.randomUUID()}.png").also { it.parentFile!!.mkdirs() }
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        var id: UUID?=null;var key: String?=null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                awaitTag("main-menu-button")
                lateinit var photos: PhotosViewModel
                scenario.onActivity {
                    photos=ViewModelProvider(it)[PhotosViewModel::class.java];photos.open(month)
                    photos.import(listOf(androidx.core.content.FileProvider.getUriForFile(context,"$qa.fileprovider",source)))
                }
                compose.waitUntil(15_000) { photos.uiState.value.photos.size==1 && !photos.uiState.value.isWorking }
                val photo=photos.uiState.value.photos.single();id=photo.id;key=photo.storageKey
                compose.onNodeWithContentDescription("Foto 1 de 1").performScrollTo().performClick()
                compose.waitUntil(15_000) { photos.uiState.value.surface==PhotosSurface.VIEWER }
                scenario.recreate()
                scenario.onActivity { photos=ViewModelProvider(it)[PhotosViewModel::class.java] }
                compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Foto del cronograma").fetchSemanticsNodes().isNotEmpty() }
                val back=compose.onNode(hasText("Volver") and hasClickAction())
                val message=compose.onAllNodesWithTag("transient-confirmation").fetchSemanticsNodes()
                status("photo_back_diagnostic","before=${photos.uiState.value.surface};message=${photos.uiState.value.infoMessage};back=${back.fetchSemanticsNode().boundsInRoot};snackbar=${message.map { it.boundsInRoot }}")
                back.assertIsDisplayed().performClick()
                compose.runOnIdle { status("photo_back_state",photos.uiState.value.surface.name) }
                compose.waitUntil(10_000) { photos.uiState.value.surface==PhotosSurface.LIST }
            }
        } finally {
            id?.let { runBlocking { repository.delete(it) } }
            key?.let { SchedulePhotoFileStore(context).file(it).let { file -> if(file.exists()) check(file.delete()) } }
            check(source.delete());check(runBlocking { repository.observeForMonth(month).first() }.isEmpty())
        }
    }

    @Test fun realPhotoPickerImportsSyntheticImageViewsRecreatesAndDeletesOnlyItsOwnData() {
        val device = UiDevice.getInstance(instrumentation)
        val token = UUID.randomUUID().toString()
        val album = "000 MiGuardia QA ${token.take(8)}"
        val name = "MIGUARDIA_QA_IMAGE_$token.png"
        val relative = "Pictures/$album/"
        val month = YearMonth.of(2088, 1)
        val repository = app.localDataStore.schedulePhotos
        val original = runBlocking { repository.observeForMonth(month).first() }
        check(original.isEmpty()) { "Dedicated QA month must be empty; never alter existing photos" }
        val bitmap = Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(20,80,60))
            val paint = Paint().apply { color=Color.WHITE; textSize=38f; isAntiAlias=true }
            drawText("MiGuardia QA",40f,100f,paint)
            paint.textSize=24f;drawText("IMAGEN FICTICIA - SIN DATOS PERSONALES",40f,170f,paint)
            paint.color=Color.rgb(160,240,190);drawRect(40f,230f,600f,350f,paint)
        }
        val bytes = ByteArrayOutputStream().use { stream -> check(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));stream.toByteArray() }
        bitmap.recycle()
        val resolver = context.contentResolver
        val source = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,name);put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,relative);put(MediaStore.Images.Media.IS_PENDING,1)
        }))
        var importedId: UUID? = null
        var importedKey: String? = null
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            resolver.openOutputStream(source,"w")!!.use { it.write(bytes) }
            check(resolver.update(source,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)==1)
            resolver.query(source,arrayOf(MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.SIZE),null,null,null)!!.use {
                check(it.moveToFirst());assertEquals(name,it.getString(0));assertEquals(bytes.size.toLong(),it.getLong(1))
            }
            resolver.query(source,arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),null,null,null)!!.use {
                check(it.moveToFirst());assertEquals(album,it.getString(0))
            }
            status("synthetic_media", "$token:${source.lastPathSegment}:${bytes.size}:${digest(bytes)}")
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitTag("main-menu-button")
            compose.onNodeWithTag("main-menu-button").performClick()
            compose.onNodeWithTag("drawer-action-calendar-photos").performScrollTo().performClick()
            lateinit var photos: PhotosViewModel
            scenario.onActivity { photos=ViewModelProvider(it)[PhotosViewModel::class.java];photos.open(month) }
            compose.waitUntil(15_000) { !photos.uiState.value.isLoading }
            val rejected=java.util.concurrent.atomic.AtomicBoolean()
            scenario.onActivity { activity -> activity.setContent {
                val state by photos.uiState.collectAsState()
                MiGuardiaTheme { PhotosSurfaceHost(state,PhotosActions.from(photos).copy(import={ selected ->
                    // Reject every nonfixture URI before reading metadata/bytes or importing.
                    val uri=selected.singleOrNull()
                    val segments=uri?.pathSegments.orEmpty()
                    val owned=uri?.authority=="media" && segments.firstOrNull() in setOf("picker","picker_get_content") &&
                        segments.takeLast(3)==listOf("com.android.providers.media.photopicker","media",source.lastPathSegment)
                    if(owned) photos.import(selected) else rejected.set(true)
                }),photos.fileStore) }
            } }
            phase("PHOTO_PICKER_SYNTHETIC")
            compose.onNodeWithText("Agregar fotos").performClick()
            val owners=setOf("com.google.android.photopicker","com.google.android.providers.media.module","com.android.providers.media.module")
            val end=SystemClock.elapsedRealtime()+15_000
            while (device.currentPackageName !in owners && SystemClock.elapsedRealtime()<end) SystemClock.sleep(100)
            check(device.currentPackageName in owners) { "Only reviewed Photo Picker owner is allowed" }
            val pickerOwner=requireNotNull(device.currentPackageName)
            val modern=pickerOwner=="com.google.android.photopicker"
            // Package visibility intentionally hides the system app's resources from QA.
            // These exact ES/ES-US/default labels were verified in the installed picker APK;
            // no manifest/permissions or Android locale settings are changed to reach them.
            val tabs=if(modern) listOf("Colecciones","Collections") else listOf("Álbumes","Albums")
            // A resumed OEM activity is not yet proof that its Compose tab has been laid out.
            val tabEnd=SystemClock.elapsedRealtime()+15_000
            var tab=tabs.firstNotNullOfOrNull { device.findObject(By.text(it)) ?: device.findObject(By.desc(it)) }
            while(tab==null && SystemClock.elapsedRealtime()<tabEnd) {
                SystemClock.sleep(100);tab=tabs.firstNotNullOfOrNull { device.findObject(By.text(it)) ?: device.findObject(By.desc(it)) }
            }
            checkNotNull(tab) { "Known album tab absent; do not browse personal media" }.click()
            check(device.currentPackageName in owners)
            val ownedAlbum=device.wait(Until.findObject(By.textContains(album)),15_000)
                ?: device.findObject(By.descContains(album))
            if(ownedAlbum==null) {
                // This OEM does not expose the new album in its initial Collections viewport.
                // Stop automatic inspection. Only the human may locate and select the fixture.
                phase("HUMAN_IMAGE")
                status("human_image_ready",album)
                compose.waitUntil(150_000) { rejected.get() || photos.uiState.value.photos.size==1 }
            } else {
            ownedAlbum.click()
            check(device.currentPackageName in owners)
            checkNotNull(device.wait(Until.findObject(By.textContains(album)),10_000)
                ?: device.findObject(By.descContains(album))) { "Owned album header required" }
            resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH} = ?",arrayOf(relative),null)!!.use {
                assertEquals(1,it.count);check(it.moveToFirst());assertEquals(source.lastPathSegment!!.toLong(),it.getLong(0))
            }
            val mediaSelector=if(modern) {
                By.desc(java.util.regex.Pattern.compile("(?:Foto (?:hecha|tomado) el|Photo taken on) .+"))
            } else By.res(pickerOwner,"icon_thumbnail")
            val thumb=device.wait(Until.findObject(mediaSelector),10_000)
            checkNotNull(thumb) { "Reviewed thumbnail control absent inside synthetic-only album" }
            assertEquals(1,device.findObjects(mediaSelector).size)
            check(device.currentPackageName in owners);thumb.click()
            val add=if(modern) listOf("Hecho","Listo","Done").firstNotNullOfOrNull { device.findObject(By.text(it)) } else device.findObject(By.res(pickerOwner,"button_add"))
            checkNotNull(add) { "Reviewed confirmation control absent" };check(device.currentPackageName in owners);add.click()
            }
            assertFalse("Only the exact generated local picker URI may be read",rejected.get())
            compose.waitUntil(20_000) { photos.uiState.value.photos.size==1 && !photos.uiState.value.isWorking }
            check(device.currentPackageName==qa);phase("QA")
            val photo=photos.uiState.value.photos.single();importedId=photo.id;importedKey=photo.storageKey
            assertEquals(month,photo.month);assertEquals(640,photo.pixelWidth);assertEquals(400,photo.pixelHeight)
            assertEquals("image/png",photo.mimeType)
            val privateFile=SchedulePhotoFileStore(context).file(photo.storageKey)
            assertEquals(digest(bytes),digest(privateFile.readBytes()))
            assertFalse(photo.storageKey.contains(token))
            compose.onNodeWithContentDescription("Foto 1 de 1").performScrollTo().performClick()
            compose.waitUntil(15_000) { photos.uiState.value.surface==PhotosSurface.VIEWER }
            compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Foto del cronograma").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Foto del cronograma").assertIsDisplayed()
            scenario.recreate()
            scenario.onActivity { photos=ViewModelProvider(it)[PhotosViewModel::class.java] }
            compose.waitUntil(15_000) { !photos.uiState.value.isLoading && photos.uiState.value.selectedId==photo.id }
            assertEquals(PhotosSurface.VIEWER,photos.uiState.value.surface)
            assertEquals(digest(bytes),digest(privateFile.readBytes()))
            compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Foto del cronograma").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Foto del cronograma").assertIsDisplayed()
            scenario.onActivity { assertSame(photos,ViewModelProvider(it)[PhotosViewModel::class.java]) }
            compose.onNode(hasText("Volver") and hasClickAction()).assertIsDisplayed().performClick()
            compose.runOnIdle { status("photo_back_state",photos.uiState.value.surface.name) }
            compose.waitUntil(10_000) { photos.uiState.value.surface==PhotosSurface.LIST }
            compose.onNodeWithText("Acciones").performScrollTo().performClick()
            compose.onNodeWithText("Eliminar",useUnmergedTree=true).performClick()
            compose.onNode(hasText("Eliminar foto") and hasClickAction()).performClick()
            compose.waitUntil(15_000) { photos.uiState.value.photos.isEmpty() && !photos.uiState.value.isWorking }
            assertNull(runBlocking { repository.getById(photo.id) });assertFalse(privateFile.exists())
            importedId=null;importedKey=null
            status("positive_media_result","IMPORTED_PRIVATE_VIEWED_RECREATED_DELETED")
        } finally {
            scenario?.close()
            // Exact IDs created in this test only; cleanup never counts as the UI delete assertion.
            importedId?.let { id -> runBlocking { repository.delete(id) } }
            importedKey?.let { key -> SchedulePhotoFileStore(context).file(key).let { if(it.exists()) check(it.delete()) } }
            check(resolver.delete(source,null,null)==1) { "Remove only the exact synthetic MediaStore row" }
            assertEquals(original,runBlocking { repository.observeForMonth(month).first() })
            status("synthetic_media_removed",token)
        }
    }

    @Test fun supervisedTalkBackOffersHumanListeningWindowWithoutClaimingAudition() {
        val manager=context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        val bridge=instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        compose.waitUntil(30_000) { manager.isTouchExplorationEnabled && manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_SPOKEN).any { it.id=="com.samsung.android.accessibility.talkback/com.samsung.android.marvin.talkback.TalkBackService" } }
        phase("TALKBACK_ACTIVE")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTag("main-menu-button")
            fun focusAndActivate(label: String) {
                var target: android.view.accessibility.AccessibilityNodeInfo?=null
                fun find(node: android.view.accessibility.AccessibilityNodeInfo) {
                    if(node.packageName?.toString()!=qa)return
                    if(node.contentDescription?.toString()==label || node.text?.toString()==label) {
                        var candidate=node
                        while(!candidate.isClickable && candidate.parent?.packageName?.toString()==qa) candidate=candidate.parent
                        if(candidate.isClickable)target=candidate
                    }
                    for(index in 0 until node.childCount) node.getChild(index)?.let(::find)
                }
                compose.waitUntil(15_000) { bridge.rootInActiveWindow?.takeIf { it.packageName?.toString()==qa }?.let(::find);target!=null }
                val node=requireNotNull(target)
                assertTrue(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                status("human_listen_control",label)
                // An explicit bounded listening window, not a retry or proof of human hearing.
                SystemClock.sleep(15_000)
                assertTrue(manager.isTouchExplorationEnabled)
                assertTrue(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            }
            focusAndActivate("Abrir menú")
            focusAndActivate("Seleccionar días")
            awaitTag("calendar-selection-cancel")
            scenario.onActivity { assertEquals(CalendarInteractionMode.EDIT,ViewModelProvider(it)[CalendarViewModel::class.java].uiState.value.interactionMode) }
            focusAndActivate("Cancelar")
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("calendar-selection-cancel").fetchSemanticsNodes(atLeastOneRootRequired=false).isEmpty() }
            scenario.onActivity { assertEquals(CalendarInteractionMode.VIEW,ViewModelProvider(it)[CalendarViewModel::class.java].uiState.value.interactionMode) }
            status("human_audition","REQUIRES_USER_REPORT")
        }
    }
}
