// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing steps: tight inside a group, generous between groups, more above a heading than below it. */
public object Space {
    public val hair: Dp = 1.dp
    public val xs: Dp = 4.dp
    public val s: Dp = 8.dp
    public val m: Dp = 12.dp
    public val l: Dp = 16.dp
    public val xl: Dp = 24.dp
    public val xxl: Dp = 32.dp

    /** The side gutter of every screen. */
    public val gutter: Dp = 16.dp

    /** The widest a reading column grows on large screens. */
    public val column: Dp = 640.dp
}

/** Sizes the components share. */
public object Sizes {
    public val touch: Dp = 48.dp
    public val stripSmall: Dp = 28.dp
    public val stripLarge: Dp = 40.dp
    public val stripRow: Dp = 16.dp
    public val icon: Dp = 22.dp

    /** A right-aligned value in a table: timings in the profile and switch-over tables, counts in the report. */
    public val valueColumn: Dp = 76.dp

    /** A clock time at the start of a report row (timeline, wakers). */
    public val timeColumn: Dp = 56.dp

    /** A narrow column: a lever letter, or the timeline's route line. */
    public val markColumn: Dp = 28.dp

    /** A profile's lever mark (A–D), and the ink outline of a lever in use. */
    public val mark: Dp = 22.dp
    public val markStroke: Dp = 1.5.dp
}

/** Printed matter has small radii: boards and notices are cut sheets, not pills. Controls stay Material. */
public val EdShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)
