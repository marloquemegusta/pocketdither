package com.arquimea.dithercamera.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.ImageProxy
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object DitherProcessor {
    fun process(
        image: ImageProxy,
        settings: DitherSettings,
        targetSize: Size? = null,
    ): Bitmap {
        val rgbBitmap = image.toBitmap()
        val rotationDegrees = image.imageInfo.rotationDegrees
        image.close()
        val rotated = rgbBitmap.rotate(rotationDegrees)
        val prepared = targetSize?.let { rotated.cropAndScaleTo(it) } ?: rotated
        return applyOrderedColorDither(prepared, settings)
    }

    private fun applyOrderedColorDither(
        source: Bitmap,
        settings: DitherSettings,
    ): Bitmap {
        val width = source.width
        val height = source.height
        val pixelSize = max(1, settings.pixelSize)
        val matrix = settings.pattern.matrix
        val matrixSize = settings.pattern.size
        val levels = max(2, settings.colorLevels)

        val src = IntArray(width * height)
        val out = IntArray(width * height)
        source.getPixels(src, 0, width, 0, 0, width, height)

        var blockY = 0
        while (blockY < height) {
            var blockX = 0
            while (blockX < width) {
                val sampleX = min(blockX + pixelSize / 2, width - 1)
                val sampleY = min(blockY + pixelSize / 2, height - 1)
                val sampleColor = src[sampleY * width + sampleX]

                val row = (blockY / pixelSize) % matrixSize
                val col = (blockX / pixelSize) % matrixSize
                val threshold = (matrix[row][col] + 0.5f) / (matrixSize * matrixSize).toFloat()

                val outputColor = outputColorForSample(
                    sampleColor = sampleColor,
                    threshold = threshold,
                    levels = levels,
                    settings = settings,
                )

                val maxY = min(blockY + pixelSize, height)
                val maxX = min(blockX + pixelSize, width)
                for (y in blockY until maxY) {
                    val rowOffset = y * width
                    for (x in blockX until maxX) {
                        out[rowOffset + x] = outputColor
                    }
                }

                blockX += pixelSize
            }
            blockY += pixelSize
        }

        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun quantizeChannel(
        channel: Int,
        threshold: Float,
        levels: Int,
        contrast: Float,
    ): Int {
        val normalized = ((((channel / 255f) - 0.5f) * contrast) + 0.5f).coerceIn(0f, 1f)
        val scaled = normalized * (levels - 1)
        val base = floor(scaled).toInt().coerceIn(0, levels - 1)
        val fraction = scaled - base
        val index = if (base < levels - 1 && fraction > threshold) base + 1 else base
        return ((index / (levels - 1).toFloat()) * 255f).toInt().coerceIn(0, 255)
    }

    private fun outputColorForSample(
        sampleColor: Int,
        threshold: Float,
        levels: Int,
        settings: DitherSettings,
    ): Int {
        val adjustedRed = adjustChannel(Color.red(sampleColor), settings.contrast)
        val adjustedGreen = adjustChannel(Color.green(sampleColor), settings.contrast)
        val adjustedBlue = adjustChannel(Color.blue(sampleColor), settings.contrast)
        val adjustedColor = Color.rgb(adjustedRed, adjustedGreen, adjustedBlue)

        return when (settings.colorProfile.mode) {
            PaletteMode.FULL_COLOR -> {
                Color.argb(
                    255,
                    quantizeChannel(adjustedRed, threshold, levels, 1f),
                    quantizeChannel(adjustedGreen, threshold, levels, 1f),
                    quantizeChannel(adjustedBlue, threshold, levels, 1f),
                )
            }

            PaletteMode.LUMA_PALETTE -> {
                val palette = settings.colorProfile.palette
                val levelsInPalette = max(2, palette.size)
                val luminance = ((adjustedRed * 0.299f) + (adjustedGreen * 0.587f) + (adjustedBlue * 0.114f))
                    .toInt()
                    .coerceIn(0, 255)
                val index = paletteIndex(luminance, threshold, levelsInPalette)
                palette[index]
            }

            PaletteMode.NEAREST_COLOR_PALETTE -> {
                val palette = settings.colorProfile.palette
                if (palette.isEmpty()) {
                    adjustedColor
                } else {
                    nearestPaletteColor(adjustedColor, palette)
                }
            }
        }
    }

    private fun adjustChannel(
        channel: Int,
        contrast: Float,
    ): Int {
        return ((((channel / 255f) - 0.5f) * contrast + 0.5f) * 255f)
            .toInt()
            .coerceIn(0, 255)
    }

    private fun paletteIndex(
        channel: Int,
        threshold: Float,
        levels: Int,
    ): Int {
        val scaled = (channel / 255f) * (levels - 1)
        val base = floor(scaled).toInt().coerceIn(0, levels - 1)
        val fraction = scaled - base
        return if (base < levels - 1 && fraction > threshold) base + 1 else base
    }

    private fun nearestPaletteColor(
        sampleColor: Int,
        palette: IntArray,
    ): Int {
        var bestColor = palette.first()
        var bestDistance = Double.MAX_VALUE
        for (candidate in palette) {
            val redDiff = Color.red(sampleColor) - Color.red(candidate)
            val greenDiff = Color.green(sampleColor) - Color.green(candidate)
            val blueDiff = Color.blue(sampleColor) - Color.blue(candidate)
            val distance =
                (redDiff * redDiff + greenDiff * greenDiff + blueDiff * blueDiff).toDouble()
            if (distance < bestDistance) {
                bestDistance = distance
                bestColor = candidate
            }
        }
        return bestColor
    }

    private fun Bitmap.rotate(rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return this
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun Bitmap.cropAndScaleTo(targetSize: Size): Bitmap {
        val targetAspect = targetSize.width.toFloat() / targetSize.height.toFloat()
        val sourceAspect = width.toFloat() / height.toFloat()

        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspect > targetAspect) {
            cropHeight = height
            cropWidth = (height * targetAspect).toInt().coerceAtMost(width)
        } else {
            cropWidth = width
            cropHeight = (width / targetAspect).toInt().coerceAtMost(height)
        }

        val x = ((width - cropWidth) / 2).coerceAtLeast(0)
        val y = ((height - cropHeight) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(this, x, y, cropWidth, cropHeight)
        if (cropped.width == targetSize.width && cropped.height == targetSize.height) {
            return cropped
        }
        return Bitmap.createScaledBitmap(cropped, targetSize.width, targetSize.height, true)
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val width = width
        val height = height
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val uvRow = y / 2
            for (x in 0 until width) {
                val uvCol = x / 2

                val yValue = yBuffer.get(y * yRowStride + x * yPixelStride).toInt() and 0xFF
                val uValue = uBuffer.get(uvRow * uRowStride + uvCol * uPixelStride).toInt() and 0xFF
                val vValue = vBuffer.get(uvRow * vRowStride + uvCol * vPixelStride).toInt() and 0xFF

                val r = (yValue + 1.402f * (vValue - 128)).toInt().coerceIn(0, 255)
                val g = (yValue - 0.344136f * (uValue - 128) - 0.714136f * (vValue - 128))
                    .toInt()
                    .coerceIn(0, 255)
                val b = (yValue + 1.772f * (uValue - 128)).toInt().coerceIn(0, 255)

                pixels[y * width + x] = Color.rgb(r, g, b)
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
