// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The colour roles of SPEC 10.2 for one theme. Values are written in OKLCH, as DESIGN.md records them,
 * and checked against it by `PaletteTest`.
 *
 * Strip fills each reach 3:1 against [ground], and the strip separates its segments with a 1 dp gap of
 * ground, so no two fills touch (SPEC 10.2 contrast rule). Each state also has its own pattern.
 */
@Immutable
public data class Palette(
    val ground: Color,
    val raised: Color,
    val raisedHigh: Color,
    val hairline: Color,
    val ink: Color,
    val quiet: Color,
    val accent: Color,
    val onAccent: Color,
    val deep: Color,
    val light: Color,
    val active: Color,
    val missing: Color,
    val good: Color,
    val warning: Color,
    val critical: Color,
    val dark: Boolean,
) {
    /** The pattern drawn over a fill: the ground at partial strength, so it reads as texture, not colour. */
    val pattern: Color get() = ground.copy(alpha = PATTERN_ALPHA)

    public companion object {
        private const val PATTERN_ALPHA = 0.55f

        /** Dark is checked first: the app is often opened at night (SPEC 10.2). */
        public val Dark: Palette = Palette(
            ground = oklch(0.155, 0.020, 285.0),
            raised = oklch(0.200, 0.024, 285.0),
            raisedHigh = oklch(0.240, 0.026, 285.0),
            hairline = oklch(0.315, 0.026, 285.0),
            ink = oklch(0.955, 0.008, 285.0),
            quiet = oklch(0.760, 0.020, 285.0),
            accent = oklch(0.880, 0.150, 98.0),
            onAccent = oklch(0.155, 0.020, 285.0),
            deep = oklch(0.880, 0.150, 98.0),
            light = oklch(0.660, 0.110, 98.0),
            active = oklch(0.530, 0.030, 285.0),
            missing = oklch(0.760, 0.020, 285.0),
            good = oklch(0.820, 0.120, 165.0),
            warning = oklch(0.790, 0.140, 62.0),
            critical = oklch(0.700, 0.170, 22.0),
            dark = true,
        )

        /**
         * Light: the departure poster. Yellow cannot carry a fill on white (1.3:1), so deep idle is set in
         * violet ink and yellow stays on accent surfaces under dark type.
         */
        public val Light: Palette = Palette(
            ground = oklch(0.975, 0.006, 285.0),
            raised = oklch(0.945, 0.010, 285.0),
            raisedHigh = oklch(0.920, 0.012, 285.0),
            hairline = oklch(0.840, 0.014, 285.0),
            ink = oklch(0.220, 0.030, 285.0),
            quiet = oklch(0.450, 0.025, 285.0),
            accent = oklch(0.880, 0.150, 98.0),
            onAccent = oklch(0.220, 0.030, 285.0),
            deep = oklch(0.330, 0.070, 285.0),
            light = oklch(0.520, 0.090, 285.0),
            active = oklch(0.640, 0.020, 285.0),
            missing = oklch(0.450, 0.025, 285.0),
            good = oklch(0.500, 0.095, 165.0),
            warning = oklch(0.530, 0.125, 55.0),
            critical = oklch(0.540, 0.180, 25.0),
            dark = false,
        )
    }
}

/** An OKLCH colour converted to sRGB with Björn Ottosson's OKLab matrices, clamped to the gamut. */
@Suppress("MagicNumber") // the published OKLab matrices
public fun oklch(lightness: Double, chroma: Double, hue: Double, alpha: Float = 1f): Color {
    val rad = Math.toRadians(hue)
    val a = chroma * cos(rad)
    val b = chroma * sin(rad)
    val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    val red = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    val green = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    val blue = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    return Color(encode(red), encode(green), encode(blue), alpha)
}

@Suppress("MagicNumber") // the sRGB transfer function
private fun encode(linear: Double): Float {
    val v = linear.coerceIn(0.0, 1.0)
    val srgb = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055
    return srgb.toFloat()
}
