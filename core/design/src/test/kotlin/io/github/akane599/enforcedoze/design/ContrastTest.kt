// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * SPEC 10.4 and DESIGN.md: text at least 4.5:1, graphics and strip fills at least 3:1 against their neighbours, in
 * both themes. The ratio is WCAG 2.2's. The pairs are the ones the screens draw: text sits on `ground` and on
 * `raised` (panels, dialogs, sheets, menus, a selected row); `raisedHigh` carries ink only (a selected chip);
 * strips sit on `ground`, and a 1 dp gap of `ground` separates their segments.
 */
class ContrastTest {
    private val themes = mapOf("dark" to Palette.Dark, "light" to Palette.Light)

    private class Pair(val name: String, val front: Color, val back: Color)

    private fun ratio(a: Color, b: Color): Double {
        val (high, low) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (high + 0.05) / (low + 0.05)
    }

    private fun below(floor: Double, pairs: (Palette) -> List<Pair>): List<String> = themes.flatMap { (theme, palette) ->
        pairs(palette).filter { ratio(it.front, it.back) < floor }
            .map { "$theme: ${it.name} is ${"%.2f".format(ratio(it.front, it.back))}:1" }
    }

    private fun Palette.text(): List<kotlin.Pair<String, Color>> =
        listOf("ink" to ink, "quiet" to quiet, "good" to good, "warning" to warning, "critical" to critical)

    @Test
    fun `text reaches 4_5 to 1 on every surface it is set on`() {
        val failures = below(4.5) { p ->
            p.text().flatMap { (name, colour) ->
                listOf(Pair("$name on ground", colour, p.ground), Pair("$name on raised", colour, p.raised))
            } + Pair("ink on raisedHigh", p.ink, p.raisedHigh) + Pair("onAccent on accent", p.onAccent, p.accent)
        }
        assertThat(failures).isEmpty()
    }

    @Test
    fun `every Material colour pair reaches 4_5 to 1, so stock components stay readable`() {
        val failures = below(4.5) { p ->
            val s = p.scheme()
            listOf(
                Pair("onPrimary on primary", s.onPrimary, s.primary),
                Pair("onPrimaryContainer on primaryContainer", s.onPrimaryContainer, s.primaryContainer),
                Pair("onSecondary on secondary", s.onSecondary, s.secondary),
                Pair("onSecondaryContainer on secondaryContainer", s.onSecondaryContainer, s.secondaryContainer),
                Pair("onTertiary on tertiary", s.onTertiary, s.tertiary),
                Pair("onTertiaryContainer on tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer),
                Pair("onBackground on background", s.onBackground, s.background),
                Pair("onSurface on surface", s.onSurface, s.surface),
                Pair("onSurfaceVariant on surfaceVariant", s.onSurfaceVariant, s.surfaceVariant),
                Pair("onSurface on surfaceContainerHighest", s.onSurface, s.surfaceContainerHighest),
                Pair("onSurfaceVariant on surfaceContainerHighest", s.onSurfaceVariant, s.surfaceContainerHighest),
                Pair("inverseOnSurface on inverseSurface", s.inverseOnSurface, s.inverseSurface),
                Pair("onError on error", s.onError, s.error),
                Pair("onErrorContainer on errorContainer", s.onErrorContainer, s.errorContainer),
            )
        }
        assertThat(failures).isEmpty()
    }

    @Test
    fun `strip fills reach 3 to 1 against the ground around them`() {
        val failures = below(3.0) { p ->
            listOf("deep" to p.deep, "light" to p.light, "active" to p.active, "missing" to p.missing)
                .map { (name, fill) -> Pair("$name on ground", fill, p.ground) }
        }
        assertThat(failures).isEmpty()
    }

    @Test
    fun `status marks, outlines and step marks reach 3 to 1`() {
        // quiet is Material's outline and the onboarding's upcoming step; hairline dividers are decoration only.
        val failures = below(3.0) { p ->
            listOf("good" to p.good, "warning" to p.warning, "critical" to p.critical, "ink" to p.ink, "quiet" to p.quiet)
                .flatMap { (name, mark) ->
                    listOf(Pair("$name on ground", mark, p.ground), Pair("$name on raised", mark, p.raised))
                }
        }
        assertThat(failures).isEmpty()
    }
}
