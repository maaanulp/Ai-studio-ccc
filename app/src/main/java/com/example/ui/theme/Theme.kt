package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HackerColorScheme = darkColorScheme(
    primary = MatrixGreenPrimary,
    onPrimary = MatrixDarkBackground,
    primaryContainer = MatrixDarkSurfaceVariant,
    onPrimaryContainer = MatrixGreenGlow,
    secondary = MatrixGreenSecondary,
    onSecondary = MatrixDarkBackground,
    secondaryContainer = MatrixDarkSurface,
    onSecondaryContainer = MatrixGreenSecondary,
    tertiary = MatrixGreenTertiary,
    onTertiary = MatrixDarkBackground,
    background = MatrixDarkBackground,
    onBackground = MatrixTextPrimary,
    surface = MatrixDarkSurface,
    onSurface = MatrixTextPrimary,
    surfaceVariant = MatrixDarkSurfaceVariant,
    onSurfaceVariant = MatrixTextSecondary,
    outline = MatrixBorder,
    outlineVariant = MatrixGreenDim,
    error = PurgeRed,
    onError = MatrixDarkBackground,
    errorContainer = PurgeRedContainer,
    onErrorContainer = PurgeRedText
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HackerColorScheme,
        typography = HackerTypography,
        content = content
    )
}
