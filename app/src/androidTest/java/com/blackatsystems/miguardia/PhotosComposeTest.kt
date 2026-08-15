package com.blackatsystems.miguardia

import android.graphics.Bitmap
import android.content.ContextWrapper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import com.blackatsystems.miguardia.ui.photos.PhotosActions
import com.blackatsystems.miguardia.ui.photos.PhotosSurface
import com.blackatsystems.miguardia.ui.photos.PhotosSurfaceHost
import com.blackatsystems.miguardia.ui.photos.PhotosUiState
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import java.time.Instant
import java.time.YearMonth
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PhotosComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyMonthShowsActionAndExplanation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent { MiGuardiaTheme { PhotosSurfaceHost(PhotosUiState(surface=PhotosSurface.LIST, month=YearMonth.of(2026,8), isLoading=false), PhotosActions(), SchedulePhotoFileStore(context)) } }
        compose.onNodeWithText("Agregar fotos").assertIsDisplayed()
        compose.onNodeWithText("Todavía no hay fotos del cronograma para este mes.").assertIsDisplayed()
    }

    @Test fun deleteAllConfirmationIsExplicit() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent { MiGuardiaTheme { PhotosSurfaceHost(PhotosUiState(surface=PhotosSurface.LIST, month=YearMonth.of(2026,8), isLoading=false, confirmDeleteAll=true), PhotosActions(), SchedulePhotoFileStore(context)) } }
        compose.onNodeWithText("Eliminar todas las fotos").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
    }

    @Test fun emptyListIsUsableInLandscapeAndLightTheme() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            device.setOrientationLeft()
            device.waitForIdle()
            compose.setContent {
                MiGuardiaTheme(darkTheme = false) {
                    PhotosSurfaceHost(
                        PhotosUiState(
                            surface = PhotosSurface.LIST,
                            month = YearMonth.of(2026, 8),
                            isLoading = false,
                        ),
                        PhotosActions(),
                        SchedulePhotoFileStore(context),
                    )
                }
            }

            compose.onNodeWithText("Agregar fotos").assertIsDisplayed()
            compose.onNodeWithText("Cerrar").assertIsDisplayed()
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    @Test fun individualMenuOffersEveryActiveObjective() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val photoId = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val objectiveId = UUID.fromString("20000000-0000-0000-0000-000000000002")
        val photo = SchedulePhoto(photoId, YearMonth.of(2026, 8), null, null, null,
            "$photoId.jpg", "image/jpeg", 10, 2, 3, Instant.EPOCH, Instant.EPOCH)
        val objective = Objective(objectiveId, "Objetivo QA", "QA", null, null, true, Instant.EPOCH, Instant.EPOCH)
        var associated: UUID? = null
        compose.setContent {
            MiGuardiaTheme {
                PhotosSurfaceHost(
                    PhotosUiState(
                        surface = PhotosSurface.LIST,
                        month = YearMonth.of(2026, 8),
                        photos = listOf(photo),
                        objectives = listOf(objective),
                        isLoading = false,
                    ),
                    PhotosActions(associate = { _, id -> associated = id }),
                    SchedulePhotoFileStore(context),
                )
            }
        }

        compose.onNodeWithText("Acciones").performClick()
        compose.onNodeWithText("Asociar: QA · Objetivo QA").performClick()

        compose.runOnIdle { assertEquals(objectiveId, associated) }

        compose.onNodeWithText("Acciones").performClick()
        compose.onNodeWithText("Quitar objetivo").performClick()

        compose.runOnIdle { assertEquals(null, associated) }
    }

    @Test fun viewerAcceptsPinchAndPanGestures() {
        val baseContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val photoId = UUID.fromString("10000000-0000-0000-0000-000000000003")
        val isolatedFilesDir = File(baseContext.cacheDir, "photos-compose-$photoId")
        val context = object : ContextWrapper(baseContext) {
            override fun getFilesDir(): File = isolatedFilesDir
        }
        val fileStore = SchedulePhotoFileStore(context)
        val photoFile = fileStore.file("$photoId.png")
        photoFile.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        try {
            photoFile.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        } finally {
            bitmap.recycle()
        }
        val photo = SchedulePhoto(photoId, YearMonth.of(2026, 8), null, null, null,
            photoFile.name, "image/png", photoFile.length(), 64, 64, Instant.EPOCH, Instant.EPOCH)

        try {
            compose.setContent {
                MiGuardiaTheme {
                    PhotosSurfaceHost(
                        PhotosUiState(
                            surface = PhotosSurface.VIEWER,
                            month = YearMonth.of(2026, 8),
                            photos = listOf(photo),
                            selectedId = photoId,
                            isLoading = false,
                        ),
                        PhotosActions(),
                        fileStore,
                    )
                }
            }

            compose.waitUntil(timeoutMillis = 5_000L) {
                compose.onAllNodesWithContentDescription(
                    "Foto del cronograma",
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithContentDescription("Foto del cronograma", useUnmergedTree = true).performTouchInput {
                pinch(
                    start0 = center + Offset(-40f, 0f),
                    end0 = center + Offset(-120f, 0f),
                    start1 = center + Offset(40f, 0f),
                    end1 = center + Offset(120f, 0f),
                )
                swipe(center, center + Offset(60f, 40f), durationMillis = 300L)
            }
            compose.onNodeWithContentDescription("Foto del cronograma", useUnmergedTree = true).assertIsDisplayed()
        } finally {
            isolatedFilesDir.deleteRecursively()
        }

    }

    @Test fun viewerDoesNotSubstituteAnotherPhotoWhenSelectionIsMissing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val existing = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val missing = UUID.fromString("10000000-0000-0000-0000-000000000002")
        val photo = SchedulePhoto(existing, YearMonth.of(2026, 8), null, null, null,
            "$existing.jpg", "image/jpeg", 10, 2, 3, Instant.EPOCH, Instant.EPOCH)
        compose.setContent {
            MiGuardiaTheme {
                PhotosSurfaceHost(
                    PhotosUiState(
                        surface = PhotosSurface.VIEWER,
                        month = YearMonth.of(2026, 8),
                        photos = listOf(photo),
                        selectedId = missing,
                        isLoading = false,
                    ),
                    PhotosActions(),
                    SchedulePhotoFileStore(context),
                )
            }
        }

        compose.onNodeWithText("Foto no disponible").assertIsDisplayed()
        compose.onNodeWithText("1 de 1").assertDoesNotExist()
    }
}
