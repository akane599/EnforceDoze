// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

private val LocalPalette = staticCompositionLocalOf { Palette.Dark }

/** Access to the palette roles Material has no slot for (strip fills, status, hairline). */
public object Ed {
    public val palette: Palette
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current
}

/** Motion explains state, nothing else (SPEC 10.3). Durations are scaled by the system's animator setting. */
public object Motion {
    public const val SHORT_MS: Int = 150
    public const val MEDIUM_MS: Int = 300
    public const val STRIP_DRAW_MS: Int = 700
    public const val VERDICT_SETTLE_MS: Int = 450

    /** Exponential ease-out: things arrive and settle. */
    public val Settle: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
}

/**
 * The app theme. Dynamic colour is off by default because verdict colours must stay stable; with
 * [wallpaperAccent] only the accent follows the wallpaper, never the strip or the status colours (SPEC 10.2).
 */
@Composable
public fun EdTheme(dark: Boolean = isSystemInDarkTheme(), wallpaperAccent: Boolean = false, content: @Composable () -> Unit) {
    val base = if (dark) Palette.Dark else Palette.Light
    val palette = if (wallpaperAccent) {
        val context = LocalContext.current
        val wall = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        base.copy(accent = wall.primaryContainer, onAccent = wall.onPrimaryContainer)
    } else {
        base
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = palette.scheme(), typography = EdTypography, shapes = EdShapes, content = content)
    }
}

/**
 * Every Material role mapped onto the palette, so no component falls back to the baseline purple. Text
 * actions are set in ink; the accent marks the one primary action and the current item (ADR 0028).
 */
public fun Palette.scheme(): ColorScheme {
    val roles = if (dark) darkColorScheme() else lightColorScheme()
    return roles.copy(
        primary = ink,
        onPrimary = ground,
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        inversePrimary = accent,
        secondary = quiet,
        onSecondary = ground,
        secondaryContainer = accent,
        onSecondaryContainer = onAccent,
        tertiary = quiet,
        onTertiary = ground,
        tertiaryContainer = raisedHigh,
        onTertiaryContainer = ink,
        background = ground,
        onBackground = ink,
        surface = ground,
        onSurface = ink,
        surfaceVariant = raised,
        onSurfaceVariant = quiet,
        surfaceTint = ground,
        inverseSurface = ink,
        inverseOnSurface = ground,
        error = critical,
        onError = ground,
        errorContainer = raisedHigh,
        onErrorContainer = critical,
        outline = quiet,
        outlineVariant = hairline,
        scrim = ground.copy(alpha = 0.6f),
        surfaceBright = raisedHigh,
        surfaceDim = ground,
        surfaceContainerLowest = ground,
        surfaceContainerLow = raised,
        surfaceContainer = raised,
        surfaceContainerHigh = raisedHigh,
        surfaceContainerHighest = raisedHigh,
    )
}
