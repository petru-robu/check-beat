package com.debloated.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Pure grey: equal red, green and blue. */
private fun gray(level: Int) = Color(level, level, level)

// Every colour role is a grey (including error), so no hue can sneak into the UI. Dynamic
// (wallpaper) colour is deliberately not used for the same reason.
private val LightColors = lightColorScheme(
    primary = gray(0x21),
    onPrimary = gray(0xFA),
    primaryContainer = gray(0xDD),
    onPrimaryContainer = gray(0x1A),
    inversePrimary = gray(0xC8),
    secondary = gray(0x4A),
    onSecondary = gray(0xFA),
    secondaryContainer = gray(0xE2),
    onSecondaryContainer = gray(0x1A),
    tertiary = gray(0x5E),
    onTertiary = gray(0xFA),
    tertiaryContainer = gray(0xE6),
    onTertiaryContainer = gray(0x1A),
    background = gray(0xFA),
    onBackground = gray(0x1A),
    surface = gray(0xFA),
    onSurface = gray(0x1A),
    surfaceVariant = gray(0xE4),
    onSurfaceVariant = gray(0x5C),
    surfaceTint = gray(0x21),
    inverseSurface = gray(0x2E),
    inverseOnSurface = gray(0xF2),
    error = gray(0x42),
    onError = gray(0xFA),
    errorContainer = gray(0xDD),
    onErrorContainer = gray(0x1A),
    outline = gray(0x8A),
    outlineVariant = gray(0xCC),
    scrim = gray(0x00),
    surfaceBright = gray(0xFA),
    surfaceDim = gray(0xD8),
    surfaceContainerLowest = gray(0xFF),
    surfaceContainerLow = gray(0xF4),
    surfaceContainer = gray(0xEE),
    surfaceContainerHigh = gray(0xE8),
    surfaceContainerHighest = gray(0xE2),
)

private val DarkColors = darkColorScheme(
    primary = gray(0xE6),
    onPrimary = gray(0x1A),
    primaryContainer = gray(0x44),
    onPrimaryContainer = gray(0xEC),
    inversePrimary = gray(0x42),
    secondary = gray(0xC4),
    onSecondary = gray(0x1A),
    secondaryContainer = gray(0x3A),
    onSecondaryContainer = gray(0xE6),
    tertiary = gray(0xB0),
    onTertiary = gray(0x1A),
    tertiaryContainer = gray(0x36),
    onTertiaryContainer = gray(0xE6),
    background = gray(0x12),
    onBackground = gray(0xE6),
    surface = gray(0x12),
    onSurface = gray(0xE6),
    surfaceVariant = gray(0x3A),
    onSurfaceVariant = gray(0xB4),
    surfaceTint = gray(0xE6),
    inverseSurface = gray(0xE6),
    inverseOnSurface = gray(0x2E),
    error = gray(0xC0),
    onError = gray(0x1A),
    errorContainer = gray(0x44),
    onErrorContainer = gray(0xEC),
    outline = gray(0x80),
    outlineVariant = gray(0x40),
    scrim = gray(0x00),
    surfaceBright = gray(0x38),
    surfaceDim = gray(0x0E),
    surfaceContainerLowest = gray(0x0C),
    surfaceContainerLow = gray(0x1A),
    surfaceContainer = gray(0x1E),
    surfaceContainerHigh = gray(0x28),
    surfaceContainerHighest = gray(0x32),
)

@Composable
fun DebloatedPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
