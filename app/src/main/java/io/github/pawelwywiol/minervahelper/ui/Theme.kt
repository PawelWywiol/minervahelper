package io.github.pawelwywiol.minervahelper.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

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

/**
 * [fontScale] multiplies the device's own text scaling rather than replacing the type scale.
 * Nothing in this app hardcodes a text size — every size comes from a `MaterialTheme.typography`
 * token — so scaling the density here reaches all of it, including Material's own components,
 * without a single call site knowing about the setting.
 */
@Composable
fun MinervaTheme(fontScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * fontScale),
    ) {
        MaterialTheme(colorScheme = Scheme, content = content)
    }
}
