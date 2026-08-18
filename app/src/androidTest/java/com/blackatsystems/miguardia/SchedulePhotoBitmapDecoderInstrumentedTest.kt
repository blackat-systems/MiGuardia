package com.blackatsystems.miguardia

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.ui.photos.DecodedSchedulePhoto
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoBitmapDecoder
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchedulePhotoBitmapDecoderInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val testRoot = File(context.cacheDir, "photo-decoder-${UUID.randomUUID()}").apply { mkdirs() }
    private val decoder = SchedulePhotoBitmapDecoder()

    @After
    fun cleanUp() {
        testRoot.deleteRecursively()
    }

    @Test
    fun normalOrientationPreservesDimensionsAndContent() {
        val file = createAsymmetricJpeg("normal.jpg", ExifInterface.ORIENTATION_NORMAL)

        decoder.decode(file, maxDimension = 1_000).useBitmap { decoded ->
            assertEquals(RAW_WIDTH, decoded.bitmap.width)
            assertEquals(RAW_HEIGHT, decoded.bitmap.height)
            assertEquals(listOf("R", "G", "B", "Y"), decoded.bitmap.quadrants())
        }
    }

    @Test
    fun rotationsShowTheExpectedVisualOrientation() {
        val cases = listOf(
            ExifInterface.ORIENTATION_ROTATE_90 to listOf("B", "R", "Y", "G"),
            ExifInterface.ORIENTATION_ROTATE_180 to listOf("Y", "B", "G", "R"),
            ExifInterface.ORIENTATION_ROTATE_270 to listOf("G", "Y", "R", "B"),
        )

        cases.forEach { (orientation, expected) ->
            val file = createAsymmetricJpeg("rotation-$orientation.jpg", orientation)
            decoder.decode(file, maxDimension = 1_000).useBitmap { decoded ->
                if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                    assertEquals(RAW_WIDTH, decoded.bitmap.width)
                    assertEquals(RAW_HEIGHT, decoded.bitmap.height)
                } else {
                    assertEquals(RAW_HEIGHT, decoded.bitmap.width)
                    assertEquals(RAW_WIDTH, decoded.bitmap.height)
                }
                assertEquals(expected, decoded.bitmap.quadrants())
            }
        }
    }

    @Test
    fun mirroredOrientationsShowTheExpectedVisualOrientation() {
        val cases = listOf(
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to listOf("G", "R", "Y", "B"),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to listOf("B", "Y", "R", "G"),
            ExifInterface.ORIENTATION_TRANSPOSE to listOf("R", "B", "G", "Y"),
            ExifInterface.ORIENTATION_TRANSVERSE to listOf("Y", "G", "B", "R"),
        )

        cases.forEach { (orientation, expected) ->
            val file = createAsymmetricJpeg("mirror-$orientation.jpg", orientation)
            decoder.decode(file, maxDimension = 1_000).useBitmap { decoded ->
                if (orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                    orientation == ExifInterface.ORIENTATION_TRANSVERSE
                ) {
                    assertEquals(RAW_HEIGHT, decoded.bitmap.width)
                    assertEquals(RAW_WIDTH, decoded.bitmap.height)
                }
                assertEquals(expected, decoded.bitmap.quadrants())
            }
        }
    }

    @Test
    fun horizontalPixelsWithPortraitExifBecomeVisuallyVertical() {
        val file = createAsymmetricJpeg("portrait-exif.jpg", ExifInterface.ORIENTATION_ROTATE_90)

        decoder.decode(file, maxDimension = 1_000).useBitmap { decoded ->
            assertTrue(decoded.bitmap.height > decoded.bitmap.width)
            assertEquals(RAW_HEIGHT, decoded.visualWidth)
            assertEquals(RAW_WIDTH, decoded.visualHeight)
        }
    }

    @Test
    fun samplingUsesTheLargestVisualSideBeforeTransforming() {
        val file = createAsymmetricJpeg(
            name = "sampled.jpg",
            orientation = ExifInterface.ORIENTATION_ROTATE_90,
            width = 1_024,
            height = 256,
        )

        decoder.decode(file, maxDimension = 200).useBitmap { decoded ->
            assertEquals(8, decoded.inSampleSize)
            assertTrue(decoded.bitmap.height <= 200)
            assertTrue(decoded.bitmap.width < decoded.bitmap.height)
        }
        assertEquals(8, SchedulePhotoBitmapDecoder.sampleSizeFor(256, 1_024, 200))
    }

    @Test
    fun decodingLeavesThePrivateFileByteForByteUnchanged() {
        val file = createAsymmetricJpeg("unchanged.jpg", ExifInterface.ORIENTATION_TRANSVERSE)
        val before = file.sha256()

        decoder.decode(file, maxDimension = 48).useBitmap { decoded ->
            assertEquals(listOf("Y", "G", "B", "R"), decoded.bitmap.quadrants())
        }

        assertArrayEquals(before, file.sha256())
    }

    @Test
    fun metadataFailureFallsBackToNormalBitmapDecoding() {
        val file = createAsymmetricJpeg("metadata-error.jpg", ExifInterface.ORIENTATION_NORMAL)
        val decoderWithBrokenMetadata = SchedulePhotoBitmapDecoder {
            throw IOException("Metadatos EXIF inválidos")
        }

        decoderWithBrokenMetadata.decode(file, maxDimension = 1_000).useBitmap { decoded ->
            assertEquals(RAW_WIDTH, decoded.bitmap.width)
            assertEquals(RAW_HEIGHT, decoded.bitmap.height)
            assertEquals(listOf("R", "G", "B", "Y"), decoded.bitmap.quadrants())
        }
    }

    @Test
    fun unknownOrientationFallsBackToNormalBitmapDecoding() {
        val file = createAsymmetricJpeg("unknown-orientation.jpg", ExifInterface.ORIENTATION_NORMAL)
        val decoderWithUnknownOrientation = SchedulePhotoBitmapDecoder { Int.MAX_VALUE }

        decoderWithUnknownOrientation.decode(file, maxDimension = 1_000).useBitmap { decoded ->
            assertEquals(RAW_WIDTH, decoded.bitmap.width)
            assertEquals(RAW_HEIGHT, decoded.bitmap.height)
            assertEquals(listOf("R", "G", "B", "Y"), decoded.bitmap.quadrants())
        }
    }

    @Test
    fun reopeningAnExistingExifPhotoKeepsTheCorrection() {
        val file = createAsymmetricJpeg("existing.jpg", ExifInterface.ORIENTATION_ROTATE_270)

        decoder.decode(file, maxDimension = 1_000).useBitmap { first ->
            assertEquals(listOf("G", "Y", "R", "B"), first.bitmap.quadrants())
        }
        SchedulePhotoBitmapDecoder().decode(file, maxDimension = 1_000).useBitmap { reopened ->
            assertEquals(listOf("G", "Y", "R", "B"), reopened.bitmap.quadrants())
            assertEquals(RAW_HEIGHT, reopened.bitmap.width)
            assertEquals(RAW_WIDTH, reopened.bitmap.height)
        }
    }

    private fun createAsymmetricJpeg(
        name: String,
        orientation: Int,
        width: Int = RAW_WIDTH,
        height: Int = RAW_HEIGHT,
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { style = Paint.Style.FILL }
        fun fill(color: Int, left: Float, top: Float, right: Float, bottom: Float) {
            paint.color = color
            canvas.drawRect(left, top, right, bottom, paint)
        }
        fill(Color.RED, 0f, 0f, width / 2f, height / 2f)
        fill(Color.GREEN, width / 2f, 0f, width.toFloat(), height / 2f)
        fill(Color.BLUE, 0f, height / 2f, width / 2f, height.toFloat())
        fill(Color.YELLOW, width / 2f, height / 2f, width.toFloat(), height.toFloat())

        val file = File(testRoot, name)
        try {
            file.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    private fun Bitmap.quadrants(): List<String> = listOf(
        colorName(getPixel(width / 4, height / 4)),
        colorName(getPixel(width * 3 / 4, height / 4)),
        colorName(getPixel(width / 4, height * 3 / 4)),
        colorName(getPixel(width * 3 / 4, height * 3 / 4)),
    )

    private fun colorName(color: Int): String {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return when {
            red > 150 && green < 100 && blue < 100 -> "R"
            red < 100 && green > 100 && blue < 100 -> "G"
            red < 100 && green < 100 && blue > 150 -> "B"
            red > 150 && green > 150 && blue < 100 -> "Y"
            else -> "?($red,$green,$blue)"
        }
    }

    private fun File.sha256(): ByteArray =
        inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest()
        }

    private inline fun DecodedSchedulePhoto?.useBitmap(block: (DecodedSchedulePhoto) -> Unit) {
        requireNotNull(this) { "La imagen sintética debía decodificarse" }
        try {
            block(this)
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val RAW_WIDTH = 160
        const val RAW_HEIGHT = 100
    }
}
