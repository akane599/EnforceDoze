// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.testing.screenshots

/** One cell of the screenshot matrix (SPEC 10.6). */
data class Variant(val dark: Boolean, val turkish: Boolean, val largeFont: Boolean, val tablet: Boolean) {
    val name: String = listOf(
        if (dark) "dark" else "light",
        if (turkish) "tr" else "en",
        if (largeFont) "font200" else "font100",
        if (tablet) "tablet" else "phone",
    ).joinToString("-")
}

/**
 * The matrix: every screen in light and dark, English and Turkish, font scale 1.0 and 2.0, phone and
 * tablet width. A half fraction of the 16 combinations (the tablet column is the parity of the other
 * three) still shows every value of every dimension four times and every pair of values twice (ADR 0030).
 */
val Matrix: List<Variant> = listOf(false, true).flatMap { dark ->
    listOf(false, true).flatMap { tr ->
        listOf(false, true).map { big -> Variant(dark, tr, big, tablet = dark xor tr xor big) }
    }
}

/** The single cell a screen's secondary states are shown in: dark (checked first), English, phone. */
val Primary: Variant = Variant(dark = true, turkish = false, largeFont = false, tablet = false)
