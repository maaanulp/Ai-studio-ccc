package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = MatrixGreenPrimary,
    secondary = CyberCyan,
    tertiary = CyberAmber,
    background = MatrixDarkBackground,
    surface = MatrixDarkSurface,
    surfaceVariant = MatrixDarkSurfaceVariant,
    onPrimary = MatrixDarkBackground,
    onSecondary = MatrixDarkBackground,
    onBackground = MatrixTextPrimary,
    onSurface = MatrixTextPrimary,
    error = PurgeRed
)

@Composable
fun Crypt0Cr3wTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
