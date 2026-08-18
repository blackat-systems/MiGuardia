package com.blackatsystems.miguardia.ui.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max

internal data class DecodedSchedulePhoto(
    val bitmap: Bitmap,
    val inSampleSize: Int,
    val visualWidth: Int,
    val visualHeight: Int,
)

internal class SchedulePhotoBitmapDecoder(
    private val orientationReader: (File) -> Int = ::readExifOrientation,
) {
    fun decode(file: File, maxDimension: Int): DecodedSchedulePhoto? {
        require(maxDimension > 0) { "La dimensión máxima debe ser positiva" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val orientation = try {
            orientationReader(file)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
            .takeIf(::isSupportedOrientation)
            ?: ExifInterface.ORIENTATION_NORMAL
        val swapsDimensions = orientation in SWAPPED_DIMENSION_ORIENTATIONS
        val visualWidth = if (swapsDimensions) bounds.outHeight else bounds.outWidth
        val visualHeight = if (swapsDimensions) bounds.outWidth else bounds.outHeight
        val inSampleSize = sampleSizeFor(visualWidth, visualHeight, maxDimension)
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { this.inSampleSize = inSampleSize },
        ) ?: return null
        val transformed = decoded.transformFor(orientation)
        if (transformed !== decoded) decoded.recycle()

        return DecodedSchedulePhoto(
            bitmap = transformed,
            inSampleSize = inSampleSize,
            visualWidth = visualWidth,
            visualHeight = visualHeight,
        )
    }

    private fun Bitmap.transformFor(orientation: Int): Bitmap {
        val matrix = when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply {
                setRotate(180f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply {
                setRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply {
                setRotate(-90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(-90f) }
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    internal companion object {
        private val SWAPPED_DIMENSION_ORIENTATIONS = setOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )

        internal fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
            require(width > 0 && height > 0) { "Las dimensiones deben ser positivas" }
            require(maxDimension > 0) { "La dimensión máxima debe ser positiva" }
            val largestSide = max(width, height)
            var sampleSize = 1
            while (largestSide / sampleSize > maxDimension && sampleSize <= Int.MAX_VALUE / 2) {
                sampleSize *= 2
            }
            return sampleSize
        }

        private fun isSupportedOrientation(orientation: Int): Boolean =
            orientation in ExifInterface.ORIENTATION_NORMAL..ExifInterface.ORIENTATION_ROTATE_270

        private fun readExifOrientation(file: File): Int =
            ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
    }
}
