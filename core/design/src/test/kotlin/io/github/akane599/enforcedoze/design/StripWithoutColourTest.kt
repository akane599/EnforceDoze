// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.cbrt

/**
 * SPEC 10.4: the timeline strip reads without colour, so it survives the colour-vision deficiencies and a
 * greyscale screen. Each fill is drawn as the strip draws it, reduced to lightness alone (CIE L*), and every pair
 * of fills must differ over most of its area, in both themes. Deep idle is solid, light idle pinstriped, active
 * dotted and missing data hatched, so the patterns differ even where two lightnesses come close.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StripWithoutColourTest {
    private val width = 120
    private val height = 48

    /** The fill on its ground, as CIE L* per pixel (0 black to 100 white). */
    private fun lightness(fill: DozeFill, palette: Palette): DoubleArray {
        val bitmap = ImageBitmap(width, height)
        val size = Size(width.toFloat(), height.toFloat())
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(bitmap), size) {
            drawRect(palette.ground)
            drawFill(fill, palette, Offset.Zero, size)
        }
        val pixels = bitmap.toPixelMap()
        return DoubleArray(width * height) { i ->
            val y = pixels[i % width, i / width].luminance().toDouble()
            if (y > CIE_EPSILON) 116 * cbrt(y) - 16 else CIE_KAPPA * y
        }
    }

    @Test
    fun `every pair of fills differs in lightness alone over most of the strip`() {
        listOf("dark" to Palette.Dark, "light" to Palette.Light).forEach { (theme, palette) ->
            val drawn = DozeFill.entries.associateWith { lightness(it, palette) }
            DozeFill.entries.forEachIndexed { i, a ->
                DozeFill.entries.drop(i + 1).forEach { b ->
                    val first = drawn.getValue(a)
                    val second = drawn.getValue(b)
                    val share = first.indices.count { abs(first[it] - second[it]) >= MIN_STEP }.toDouble() / first.size
                    assertWithMessage("$theme: $a and $b differ in lightness over ${"%.0f".format(share * 100)}% of the strip")
                        .that(share).isAtLeast(MIN_SHARE)
                }
            }
        }
    }

    private companion object {
        /** A lightness step the eye tells apart at strip size, in CIE L* units. */
        const val MIN_STEP = 10.0

        /** The share of the strip over which two fills must differ by [MIN_STEP]. */
        const val MIN_SHARE = 0.5

        const val CIE_EPSILON = 216.0 / 24389.0
        const val CIE_KAPPA = 24389.0 / 27.0
    }
}
