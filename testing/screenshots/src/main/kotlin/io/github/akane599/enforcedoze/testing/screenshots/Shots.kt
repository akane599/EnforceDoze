// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.testing.screenshots

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.locale
import com.github.takahirom.roborazzi.size
import com.github.takahirom.roborazzi.uiMode
import io.github.akane599.enforcedoze.design.EdTheme
import java.io.File

private const val PHONE_WIDTH = 384
private const val PHONE_HEIGHT = 832
private const val TABLET_WIDTH = 840
private const val TABLET_HEIGHT = 1100
private const val LARGE_FONT = 2f

/** Secondary states that run past the first viewport are shot whole. */
private const val TALL_HEIGHT = 1500

/** The owner's clock: Istanbul time on a 24-hour clock, so every run renders the same text. */
fun ownerClock(twentyFour: Boolean = true) {
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Istanbul"))
    val resolver = org.robolectric.RuntimeEnvironment.getApplication().contentResolver
    android.provider.Settings.System.putString(resolver, android.provider.Settings.System.TIME_12_24, if (twentyFour) "24" else "12")
}

/**
 * Renders [content] in the app theme for each of [variants] and writes
 * `<root>/<module>/screenshots/<name>/<variant>.png`. The theme follows the variant's night mode, so
 * [content] reads it like the app does. Run `recordRoborazziDebug` to write, `verifyRoborazziDebug` in CI.
 */
fun shoot(module: String, name: String, variants: List<Variant> = Matrix, tall: Boolean = false, content: @Composable () -> Unit) {
    val root = File(System.getProperty("enforcedoze.root") ?: ".")
    variants.forEach { v ->
        val options = RoborazziComposeOptions {
            val height = when {
                tall -> TALL_HEIGHT
                v.tablet -> TABLET_HEIGHT
                else -> PHONE_HEIGHT
            }
            size(widthDp = if (v.tablet) TABLET_WIDTH else PHONE_WIDTH, heightDp = height)
            uiMode(if (v.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            locale(if (v.turkish) "tr" else "en")
            if (v.largeFont) fontScale(LARGE_FONT)
        }
        val file = File(root, "$module/screenshots/$name/${v.name}.png")
        captureRoboImage(file, roborazziComposeOptions = options) { EdTheme { content() } }
    }
}
