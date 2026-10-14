package com.example.parser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

/**
 * ImagePreprocessor for Crypt0 Cr3w Central [CCC] OCR Engine.
 *
 * Implements:
 * 1. Negative Image Inversion: Converts neon green / dark terminal screenshots into high-contrast black-on-white.
 * 2. Dynamic Adaptive Binarization: Filters profile-specific background noise (skull patterns, matrix grids, fiber textures, watermark lines).
 * 3. Region of Interest (ROI / Bounding Boxes) Segmentation: Crops key regions (Header/User, System Info, Software Grid, Wallet) for focused extraction.
 * 4. Aggressive Contrast Rescan: Enhances edge definition when the software matrix requires a second pass.
 */
object ImagePreprocessor {

    /**
     * Applies negative color inversion to convert neon-green-on-black into dark-on-white.
     * Uses a weighted grayscale inverted color matrix that heavily weighs the green channel
     * to ensure Hack EX green typography is transformed into crisp dark text on pure white.
     */
    fun invertToNegative(src: Bitmap): Bitmap {
        val safeSrc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && src.config == Bitmap.Config.HARDWARE) {
            src.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            src
        }

        val width = safeSrc.width
        val height = safeSrc.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val invertedMatrix = ColorMatrix(
            floatArrayOf(
                -0.25f, -0.65f, -0.10f, 0f, 255f, // Red
                -0.25f, -0.65f, -0.10f, 0f, 255f, // Green
                -0.25f, -0.65f, -0.10f, 0f, 255f, // Blue
                 0.00f,  0.00f,  0.00f, 1f,   0f  // Alpha
            )
        )

        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(invertedMatrix)
        }
        canvas.drawBitmap(safeSrc, 0f, 0f, paint)

        if (safeSrc != src) {
            safeSrc.recycle()
        }

        return output
    }

    /**
     * Applies Dynamic Adaptive Binarization after negative inversion.
     * Divides the image into dynamic blocks to compute local thresholds,
     * stripping background noise, skulls, matrix patterns, and watermark lines.
     */
    fun applyAdaptiveBinarization(invertedBitmap: Bitmap, sensitivity: Float = 0.88f): Bitmap {
        val safeBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && invertedBitmap.config == Bitmap.Config.HARDWARE) {
            invertedBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            invertedBitmap
        }

        val width = safeBitmap.width
        val height = safeBitmap.height
        val pixels = IntArray(width * height)
        safeBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        if (safeBitmap != invertedBitmap) {
            safeBitmap.recycle()
        }

        val outPixels = IntArray(width * height)
        val blockSize = max(16, width / 40) // Responsive grid block size

        val blocksX = (width + blockSize - 1) / blockSize
        val blocksY = (height + blockSize - 1) / blockSize
        val blockMeans = IntArray(blocksX * blocksY)

        for (by in 0 until blocksY) {
            val yStart = by * blockSize
            val yEnd = min(yStart + blockSize, height)
            for (bx in 0 until blocksX) {
                val xStart = bx * blockSize
                val xEnd = min(xStart + blockSize, width)

                var sum = 0L
                var count = 0
                for (y in yStart until yEnd) {
                    val rowOffset = y * width
                    for (x in xStart until xEnd) {
                        val c = pixels[rowOffset + x]
                        val lum = (c and 0xFF) // grayscale byte
                        sum += lum
                        count++
                    }
                }
                blockMeans[by * blocksX + bx] = if (count > 0) (sum / count).toInt() else 128
            }
        }

        for (y in 0 until height) {
            val by = min(y / blockSize, blocksY - 1)
            val rowOffset = y * width
            for (x in 0 until width) {
                val bx = min(x / blockSize, blocksX - 1)
                val localMean = blockMeans[by * blocksX + bx]
                val threshold = (localMean * sensitivity).toInt()

                val c = pixels[rowOffset + x]
                val lum = (c and 0xFF)

                if (lum < threshold) {
                    outPixels[rowOffset + x] = 0xFF000000.toInt() // Text glyph -> Pure Black
                } else {
                    outPixels[rowOffset + x] = 0xFFFFFFFF.toInt() // Background -> Pure White
                }
            }
        }

        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    /**
     * Complete primary preprocessing pipeline:
     * 1. Inverts colors (negative)
     * 2. Binarizes adaptively for maximum character edge sharpness.
     */
    fun preprocessNegativeAdaptiveBinarization(src: Bitmap): Bitmap {
        val inverted = invertToNegative(src)
        val binarized = applyAdaptiveBinarization(inverted, sensitivity = 0.88f)
        if (inverted != src && inverted != binarized) {
            inverted.recycle()
        }
        return binarized
    }

    /**
     * Aggressive contrast enhancement used for secondary software matrix rescans.
     */
    fun preprocessAggressiveContrastBinarization(src: Bitmap): Bitmap {
        val inverted = invertToNegative(src)
        val binarized = applyAdaptiveBinarization(inverted, sensitivity = 0.94f)
        if (inverted != src && inverted != binarized) {
            inverted.recycle()
        }
        return binarized
    }

    /**
     * Regional Bounding Box / Segment Cropping Functions:
     * Focuses scanning on specific regions if global background noise interferes.
     */
    fun cropHeaderRegion(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val targetH = (h * 0.28f).toInt().coerceIn(10, h)
        return Bitmap.createBitmap(src, 0, 0, w, targetH)
    }

    fun cropSystemInfoRegion(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val startY = (h * 0.50f).toInt().coerceIn(0, h - 10)
        val targetH = (h * 0.40f).toInt().coerceIn(10, h - startY)
        return Bitmap.createBitmap(src, 0, startY, w, targetH)
    }

    fun cropSoftwareGridRegion(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val startY = (h * 0.12f).toInt().coerceIn(0, h - 10)
        val targetH = (h * 0.86f).toInt().coerceIn(10, h - startY)
        return Bitmap.createBitmap(src, 0, startY, w, targetH)
    }

    fun cropWalletRegion(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val startY = (h * 0.08f).toInt().coerceIn(0, h - 10)
        val targetH = (h * 0.60f).toInt().coerceIn(10, h - startY)
        return Bitmap.createBitmap(src, 0, startY, w, targetH)
    }

    fun cropSoftwareRoi(bitmap: Bitmap, startYRatio: Float = 0.12f, endYRatio: Float = 0.98f): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val startY = (height * startYRatio).toInt().coerceIn(0, height - 10)
        val targetHeight = (height * (endYRatio - startYRatio)).toInt().coerceIn(10, height - startY)
        return Bitmap.createBitmap(bitmap, 0, startY, width, targetHeight)
    }

    /**
     * Splits the software list container into 11 equal horizontal strips.
     * Each strip corresponds 1-to-1 with the master list of 11 software tools:
     * 1. Antivirus, 2. Spam, 3. Rootkit, 4. Firewall, 5. Bypasser,
     * 6. Password Cracker, 7. Password Encryptor, 8. Proxy, 9. Trace, 10. Keygen, 11. Siphon.
     */
    fun crop11EquidistantStrips(softwareRoi: Bitmap): List<Bitmap> {
        val width = softwareRoi.width
        val height = softwareRoi.height
        val stripHeight = (height / 11).coerceAtLeast(1)
        val strips = mutableListOf<Bitmap>()
        for (i in 0 until 11) {
            val startY = i * stripHeight
            val currentH = if (i == 10) (height - startY) else stripHeight
            val safeH = currentH.coerceIn(1, height - startY)
            strips.add(Bitmap.createBitmap(softwareRoi, 0, startY, width, safeH))
        }
        return strips
    }
}
