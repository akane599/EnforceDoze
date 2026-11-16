// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/** SPEC 10.2: DESIGN.md is the single source of truth for colour; the theme must match its table value for value. */
class PaletteTest {
    private val roles: Map<String, (Palette) -> Color> = mapOf(
        "ground" to { it.ground },
        "raised" to { it.raised },
        "raisedHigh" to { it.raisedHigh },
        "hairline" to { it.hairline },
        "ink" to { it.ink },
        "quiet" to { it.quiet },
        "accent" to { it.accent },
        "onAccent" to { it.onAccent },
        "deep" to { it.deep },
        "light" to { it.light },
        "active" to { it.active },
        "missing" to { it.missing },
        "good" to { it.good },
        "warning" to { it.warning },
        "critical" to { it.critical },
    )

    private class Row(val token: String, val dark: Color, val light: Color)

    /** The colour table: `| `token` | oklch(L C H) | oklch(L C H) | role |`. */
    private fun table(): List<Row> {
        val design = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "DESIGN.md") }.first { it.isFile }
        val value = Regex("""oklch\(([\d.]+) ([\d.]+) ([\d.]+)\)""")
        fun color(cell: String): Color {
            val (l, c, h) = value.matchEntire(cell.trim())?.destructured ?: error("not an oklch value: $cell")
            return oklch(l.toDouble(), c.toDouble(), h.toDouble())
        }
        val start = design.readLines().dropWhile { !it.startsWith("| Token | Dark | Light | Role |") }.drop(2)
        return start.takeWhile { it.startsWith("| `") }.map { line ->
            val cells = line.split('|').map { it.trim() }
            Row(cells[1].removeSurrounding("`"), color(cells[2]), color(cells[3]))
        }
    }

    @Test
    fun everyRoleIsInDesignMdOnce() {
        assertThat(table().map { it.token }).containsExactlyElementsIn(roles.keys)
    }

    @Test
    fun theThemeMatchesDesignMd() {
        for (row in table()) {
            val role = roles.getValue(row.token)
            assertWithMessage("${row.token}, dark").that(role(Palette.Dark)).isEqualTo(row.dark)
            assertWithMessage("${row.token}, light").that(role(Palette.Light)).isEqualTo(row.light)
        }
    }
}
