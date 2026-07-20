package com.pawelwywiol.minervahelper.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark-first: the app lives on a gaming handheld between launchers and emulators that are all
 * dark, where the template's light purple looked foreign. One amber accent carries the primary
 * action on each screen; green is reserved for "you already have this", red for errors.
 */
private val Scheme = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF2A1B00),
    primaryContainer = Color(0xFF5A4000),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondaryContainer = Color(0xFF3A3F4B),
    onSecondaryContainer = Color(0xFFE0E3EC),
    tertiary = Color(0xFF86D993),
    background = Color(0xFF14161A),
    onBackground = Color(0xFFE4E6EB),
    surface = Color(0xFF14161A),
    onSurface = Color(0xFFE4E6EB),
    surfaceVariant = Color(0xFF23262E),
    onSurfaceVariant = Color(0xFF9BA1AE),
    surfaceContainer = Color(0xFF1B1E24),
    surfaceContainerHigh = Color(0xFF23262E),
    surfaceContainerHighest = Color(0xFF2B2F38),
)

@Composable
fun MinervaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
