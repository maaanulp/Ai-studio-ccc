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
    alpha: Float = 0.28f,
    fontSize: Float = 32f
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

    val paintHead = remember(fontSize) {
        Paint().apply {
            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 200, 255, 210)
            textSize = fontSize
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val paintBody = remember(fontSize) {
        Paint().apply {
            color = android.graphics.Color.argb((alpha * 200).toInt().coerceIn(0, 255), 0, 255, 102)
            textSize = fontSize
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val paintTail = remember(fontSize) {
        Paint().apply {
            color = android.graphics.Color.argb((alpha * 80).toInt().coerceIn(0, 255), 5, 80, 25)
            textSize = fontSize
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return@Canvas

        val columnWidth = fontSize * 0.9f
        val columnsCount = (width / columnWidth).toInt().coerceAtLeast(1)

        val activeDrops = drops ?: run {
            val list = List(columnsCount) {
                val len = Random.nextInt(8, 24)
                MatrixDrop(
                    y = Random.nextFloat() * height,
                    speed = Random.nextFloat() * 4f + 3f,
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

                // Move drop down
                drop.y += drop.speed
                if (drop.y - drop.length * fontSize > height) {
                    drop.y = -Random.nextFloat() * 100f
                    drop.length = Random.nextInt(8, 22)
                    drop.chars = CharArray(drop.length) { MATRIX_CHARS[Random.nextInt(MATRIX_CHARS.size)] }
                }

                // Random glyph mutation
                if (Random.nextFloat() < 0.05f) {
                    val changeIdx = Random.nextInt(drop.length)
                    drop.chars[changeIdx] = MATRIX_CHARS[Random.nextInt(MATRIX_CHARS.size)]
                }

                // Draw characters from top of stream to head
                for (j in 0 until drop.length) {
                    val charY = drop.y - (drop.length - 1 - j) * fontSize
                    if (charY in -fontSize..(height + fontSize)) {
                        val paint = when {
                            j == drop.length - 1 -> paintHead
                            j > drop.length - 4 -> paintBody
                            else -> paintTail
                        }
                        native.drawText(drop.chars[j].toString(), x, charY, paint)
                    }
                }
            }
        }
    }
}
