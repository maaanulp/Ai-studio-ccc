package com.example.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.random.Random

private val MATRIX_CHARS = "0123456789ABCDEF$#@&*!%<>:/{}\\[]=+*~^?|ｦｱｳｴｵｶｷｹｺｻｼｽｾｿﾀﾂﾃﾅﾆﾇﾈﾊﾋﾎﾏﾐﾑﾒﾓﾔﾕﾗﾘﾜ".toCharArray()

data class MatrixDrop(
    var y: Float,
    val speed: Float,
    var length: Int,
    var chars: CharArray
)

@Composable
fun MatrixRainCanvas(
    modifier: Modifier = Modifier.fillMaxSize(),
    alpha: Float = 0.06f,
    fontSize: Float = 30f
) {
    var drops by remember { mutableStateOf<List<MatrixDrop>?>(null) }
    var tick by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                tick += 1f
            }
        }
    }

    val paintHead = remember(fontSize, alpha) {
        Paint().apply {
            color = android.graphics.Color.argb((alpha * 220).toInt().coerceIn(0, 255), 170, 255, 185)
            textSize = fontSize
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val paintBody = remember(fontSize, alpha) {
        Paint().apply {
            color = android.graphics.Color.argb((alpha * 150).toInt().coerceIn(0, 255), 0, 200, 70)
            textSize = fontSize
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val paintTail = remember(fontSize, alpha) {
        Paint().apply {
            color = android.graphics.Color.argb((alpha * 55).toInt().coerceIn(0, 255), 0, 65, 15)
            textSize = fontSize
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    Canvas(modifier = modifier) {
        // Read tick to trigger redraw on each frame
        val _frame = tick
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return@Canvas

        val columnWidth = fontSize * 0.95f
        val columnsCount = (width / columnWidth).toInt().coerceAtLeast(1)

        val activeDrops = drops ?: run {
            val list = List(columnsCount) {
                val len = Random.nextInt(6, 18)
                MatrixDrop(
                    y = Random.nextFloat() * height,
                    speed = Random.nextFloat() * 2.5f + 1.5f,
                    length = len,
                    chars = CharArray(len) { MATRIX_CHARS[Random.nextInt(MATRIX_CHARS.size)] }
                )
            }
            drops = list
            list
        }

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (i in 0 until minOf(columnsCount, activeDrops.size)) {
                val drop = activeDrops[i]
                val x = i * columnWidth

                // Move drop down smoothly
                drop.y += drop.speed
                if (drop.y - drop.length * fontSize > height) {
                    drop.y = -Random.nextFloat() * 80f
                    drop.length = Random.nextInt(6, 18)
                    drop.chars = CharArray(drop.length) { MATRIX_CHARS[Random.nextInt(MATRIX_CHARS.size)] }
                }

                // Subtle random glyph mutation
                if (Random.nextFloat() < 0.03f) {
                    val changeIdx = Random.nextInt(drop.length)
                    drop.chars[changeIdx] = MATRIX_CHARS[Random.nextInt(MATRIX_CHARS.size)]
                }

                // Draw characters from top of stream to head
                for (j in 0 until drop.length) {
                    val charY = drop.y - (drop.length - 1 - j) * fontSize
                    if (charY in -fontSize..(height + fontSize)) {
                        val paint = when {
                            j == drop.length - 1 -> paintHead
                            j > drop.length - 3 -> paintBody
                            else -> paintTail
                        }
                        native.drawText(drop.chars[j].toString(), x, charY, paint)
                    }
                }
            }
        }
    }
}
