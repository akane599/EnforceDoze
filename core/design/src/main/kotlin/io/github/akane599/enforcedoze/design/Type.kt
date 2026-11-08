// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Barlow Semi Condensed (SIL OFL 1.1, `res/raw/barlow_ofl.txt`): the timetable's condensed grotesk. */
public val Barlow: FontFamily = FontFamily(
    Font(R.font.barlow_semi_condensed_regular, FontWeight.Normal),
    Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
)

/** Raw dump lines only (SPEC 10.1); everything else is Barlow. */
public val RawLine: TextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)

/** Tabular figures wherever a number can change (SPEC 10.1). */
private const val FIGURES = "tnum"

private fun style(size: TextUnit, weight: FontWeight, line: TextUnit, tracking: Double = 0.005) = TextStyle(
    fontFamily = Barlow,
    fontSize = size,
    fontWeight = weight,
    lineHeight = line,
    letterSpacing = tracking.em,
    fontFeatureSettings = FIGURES,
)

/**
 * The type scale. Hierarchy comes from scale contrast: the verdict word is set at display size and
 * everything else at title size or below (ADR 0028).
 */
public val EdTypography: Typography = Typography(
    displayLarge = style(64.sp, FontWeight.SemiBold, 66.sp, -0.01),
    displayMedium = style(52.sp, FontWeight.SemiBold, 56.sp, -0.01),
    displaySmall = style(40.sp, FontWeight.SemiBold, 44.sp, -0.005),
    headlineLarge = style(32.sp, FontWeight.SemiBold, 38.sp),
    headlineMedium = style(28.sp, FontWeight.SemiBold, 34.sp),
    headlineSmall = style(24.sp, FontWeight.Medium, 30.sp),
    titleLarge = style(22.sp, FontWeight.Medium, 28.sp),
    titleMedium = style(17.sp, FontWeight.Medium, 24.sp),
    titleSmall = style(15.sp, FontWeight.Medium, 20.sp),
    bodyLarge = style(16.sp, FontWeight.Normal, 23.sp),
    bodyMedium = style(15.sp, FontWeight.Normal, 21.sp),
    bodySmall = style(13.sp, FontWeight.Normal, 18.sp),
    labelLarge = style(15.sp, FontWeight.Medium, 20.sp, 0.01),
    labelMedium = style(13.sp, FontWeight.Medium, 17.sp, 0.02),
    labelSmall = style(12.sp, FontWeight.Medium, 16.sp, 0.03),
)
