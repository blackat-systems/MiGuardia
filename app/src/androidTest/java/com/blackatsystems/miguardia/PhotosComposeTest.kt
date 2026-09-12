package com.blackatsystems.miguardia

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.exifinterface.media.ExifInterface
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
import org.junit.Assert.assertTrue
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

    @Test fun listHasNoBulkDeleteOrRedundantOpenButton() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val photoId = UUID.fromString("10000000-0000-0000-0000-000000000010")
        val photo = SchedulePhoto(photoId, YearMonth.of(2026, 8), null, null, null,
            "$photoId.jpg", "image/jpeg", 10, 2, 3, Instant.EPOCH, Instant.EPOCH)
        var opened: UUID? = null
        compose.setContent {
            MiGuardiaTheme {
                PhotosSurfaceHost(
                    PhotosUiState(
                        surface = PhotosSurface.LIST,
                        month = YearMonth.of(2026, 8),
                        photos = listOf(photo),
                        isLoading = false,
                    ),
                    PhotosActions(view = { opened = it }),
                    SchedulePhotoFileStore(context),
                )
            }
        }
        compose.onNodeWithText("Eliminar todas las fotos del mes").assertDoesNotExist()
        compose.onNodeWithText("Abrir foto").assertDoesNotExist()
        compose.onNodeWithContentDescription("Foto 1 de 1").performClick()
        compose.runOnIdle { assertEquals(photoId, opened) }
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

    @Test fun thumbnailAndViewerRenderTheSameExifOrientation() {
        val baseContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val photoId = UUID.fromString("10000000-0000-0000-0000-000000000013")
        val isolatedFilesDir = File(baseContext.cacheDir, "photos-compose-exif-$photoId")
        val context = object : ContextWrapper(baseContext) {
            override fun getFilesDir(): File = isolatedFilesDir
        }
        val fileStore = SchedulePhotoFileStore(context)
        val photoFile = fileStore.file("$photoId.jpg")
        photoFile.parentFile?.mkdirs()
        createQuadrantJpeg(photoFile)
        ExifInterface(photoFile).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val photo = SchedulePhoto(
            photoId,
            YearMonth.of(2026, 8),
            null,
            null,
            null,
            photoFile.name,
            "image/jpeg",
            photoFile.length(),
            160,
            100,
            Instant.EPOCH,
            Instant.EPOCH,
        )
        val surface = mutableStateOf(PhotosSurface.LIST)

        try {
            compose.setContent {
                MiGuardiaTheme {
                    PhotosSurfaceHost(
                        PhotosUiState(
                            surface = surface.value,
                            month = YearMonth.of(2026, 8),
                            photos = listOf(photo),
                            selectedId = photoId.takeIf { surface.value == PhotosSurface.VIEWER },
                            isLoading = false,
                        ),
                        PhotosActions(),
                        fileStore,
                    )
                }
            }

            val thumbnailOrder = renderedQuadrantOrder()
            compose.runOnIdle { surface.value = PhotosSurface.VIEWER }
            val viewerOrder = renderedQuadrantOrder()

            assertEquals(listOf("B", "R", "Y", "G"), thumbnailOrder)
            assertEquals(thumbnailOrder, viewerOrder)
        } finally {
            isolatedFilesDir.deleteRecursively()
        }
    }

    @Test fun replacementAndReopenKeepTheExifCorrection() {
        val baseContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val photoId = UUID.fromString("10000000-0000-0000-0000-000000000014")
        val isolatedFilesDir = File(baseContext.cacheDir, "photos-compose-replace-$photoId")
        val context = object : ContextWrapper(baseContext) {
            override fun getFilesDir(): File = isolatedFilesDir
        }
        val fileStore = SchedulePhotoFileStore(context)
        val original = fileStore.file("$photoId.jpg")
        val replacement = fileStore.file("${photoId}_a1b2c3d4.jpg")
        original.parentFile?.mkdirs()
        createQuadrantJpeg(original)
        createQuadrantJpeg(replacement)
        ExifInterface(replacement).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val initialPhoto = SchedulePhoto(
            photoId,
            YearMonth.of(2026, 8),
            null,
            null,
            null,
            original.name,
            "image/jpeg",
            original.length(),
            160,
            100,
            Instant.EPOCH,
            Instant.EPOCH,
        )
        val photo = mutableStateOf(initialPhoto)
        val surface = mutableStateOf(PhotosSurface.VIEWER)

        try {
            compose.setContent {
                MiGuardiaTheme {
                    PhotosSurfaceHost(
                        PhotosUiState(
                            surface = surface.value,
                            month = YearMonth.of(2026, 8),
                            photos = listOf(photo.value),
                            selectedId = photoId,
                            isLoading = false,
                        ),
                        PhotosActions(),
                        fileStore,
                    )
                }
            }

            assertEquals(listOf("R", "G", "B", "Y"), renderedQuadrantOrder())
            compose.runOnIdle {
                photo.value = initialPhoto.copy(
                    storageKey = replacement.name,
                    byteSize = replacement.length(),
                    updatedAt = Instant.ofEpochMilli(1),
                )
            }
            assertEquals(listOf("B", "R", "Y", "G"), renderedQuadrantOrder())

            compose.runOnIdle { surface.value = PhotosSurface.LIST }
            assertEquals(listOf("B", "R", "Y", "G"), renderedQuadrantOrder())
            compose.runOnIdle { surface.value = PhotosSurface.VIEWER }
            assertEquals(listOf("B", "R", "Y", "G"), renderedQuadrantOrder())
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

    private fun renderedQuadrantOrder(): List<String> {
        compose.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                compose.onAllNodesWithContentDescription(
                    "Foto del cronograma",
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().size == 1
            }.getOrDefault(false)
        }
        val pixels = compose.onNodeWithContentDescription(
            "Foto del cronograma",
            useUnmergedTree = true,
        ).captureToImage().toPixelMap()
        val centroids = mutableMapOf<String, Triple<Long, Long, Long>>()
        for (y in 0 until pixels.height step 3) {
            for (x in 0 until pixels.width step 3) {
                val color = pixels[x, y]
                val name = when {
                    color.red > .6f && color.green < .4f && color.blue < .4f -> "R"
                    color.red < .4f && color.green > .4f && color.blue < .4f -> "G"
                    color.red < .4f && color.green < .4f && color.blue > .6f -> "B"
                    color.red > .6f && color.green > .6f && color.blue < .4f -> "Y"
                    else -> null
                } ?: continue
                val current = centroids[name] ?: Triple(0L, 0L, 0L)
                centroids[name] = Triple(current.first + x, current.second + y, current.third + 1L)
            }
        }
        assertEquals(setOf("R", "G", "B", "Y"), centroids.keys)
        val ordered = centroids.mapValues { (_, sums) ->
            (sums.first.toDouble() / sums.third) to (sums.second.toDouble() / sums.third)
        }
        val rows = ordered.entries.sortedBy { it.value.second }
        val top = rows.take(2).sortedBy { it.value.first }
        val bottom = rows.takeLast(2).sortedBy { it.value.first }
        assertTrue(top.maxOf { it.value.second } < bottom.minOf { it.value.second })
        return top.map { it.key } + bottom.map { it.key }
    }

    private fun createQuadrantJpeg(file: File) {
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { style = Paint.Style.FILL }
        fun fill(color: Int, left: Float, top: Float, right: Float, bottom: Float) {
            paint.color = color
            canvas.drawRect(left, top, right, bottom, paint)
        }
        fill(AndroidColor.RED, 0f, 0f, 80f, 50f)
        fill(AndroidColor.GREEN, 80f, 0f, 160f, 50f)
        fill(AndroidColor.BLUE, 0f, 50f, 80f, 100f)
        fill(AndroidColor.YELLOW, 80f, 50f, 160f, 100f)
        try {
            file.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
