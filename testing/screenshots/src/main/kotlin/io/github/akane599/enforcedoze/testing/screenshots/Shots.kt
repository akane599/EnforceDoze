// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.testing.screenshots

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.RoborazziComposeCaptureOption
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

/**
 * At font scale 2.0 a screen runs about twice as long; the capture is twice as tall so what would scroll is
 * still in the picture and can be checked for clipping (the P5 audit found Home's actions cut off at 832 dp).
 */
private const val LARGE_FONT_LENGTH = 2

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
fun shoot(
    module: String,
    name: String,
    variants: List<Variant> = Matrix,
    tall: Boolean = false,
    /** The screen's whole length in dp at font scale 1.0, for a screen longer than [TALL_HEIGHT]. */
    length: Int? = null,
    content: @Composable () -> Unit,
) {
    val root = File(System.getProperty("enforcedoze.root") ?: ".")
    // Compose scales every animation by the system animator scale; at 0 each one lands on its end in the
    // first frame, so a scrolling tab row or a fading notice is shot where it settles, on every run.
    val resolver = org.robolectric.RuntimeEnvironment.getApplication().contentResolver
    android.provider.Settings.Global.putFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    variants.forEach { v ->
        val options = RoborazziComposeOptions {
            val base = when {
                length != null -> length
                tall -> TALL_HEIGHT
                v.tablet -> TABLET_HEIGHT
                else -> PHONE_HEIGHT
            }
            val height = if (v.largeFont) base * LARGE_FONT_LENGTH else base
            size(widthDp = if (v.tablet) TABLET_WIDTH else PHONE_WIDTH, heightDp = height)
            uiMode(if (v.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            locale(if (v.turkish) "tr" else "en")
            if (v.largeFont) fontScale(LARGE_FONT)
            addOption(Settle)
        }
        val file = File(root, "$module/screenshots/$name/${v.name}.png")
        collectGarbage()
        captureRoboImage(file, roborazziComposeOptions = options) { EdTheme { content() } }
        check(!file.exists() || !flat(file)) { "${file.path} came out as one flat colour: the capture missed the frame" }
    }
}

/**
 * Roborazzi copies the window inside `onActivity`, straight after `setContent`, while the first frames and
 * effects are still queued: a scrollable tab row was caught mid-scroll, and a sheet now and then before
 * its first frame (all black). Robolectric's clock is simulated, so moving it one second runs those frames
 * to the settled screen the owner sees, the same on every run (ADR 0030).
 */
private object Settle : RoborazziComposeCaptureOption {
    override fun beforeCapture() =
        org.robolectric.shadows.ShadowLooper.idleMainLooper(SETTLE_MS, java.util.concurrent.TimeUnit.MILLISECONDS)

    override fun afterCapture() = Unit
}

private const val SETTLE_MS = 1_000L

/**
 * Robolectric's hardware PixelCopy (ShadowPixelCopy and HardwareRenderingScreenshot, 4.17 and master) draws the
 * window on HWUI's render thread with `syncAndDraw()` and reads the frame straight back, without waiting for it
 * to be presented. Now and then that read came back empty, every pixel 0x00000000, although the window was
 * composed and laid out: a second hardware render of the same window, made straight after, showed the screen.
 * The miss depends on garbage-collection timing: it struck any screen, mostly after a dozen captures in one JVM,
 * and collecting garbage and running finalizers before each capture removed it (0 flat in 120 captures of the
 * empty log, against 26 in 260 without). It is a mitigation, not a guarantee, so the flat check in [shoot] stays.
 */
// The collection is the point here (see above); runFinalization is still the only way to drain pending finalizers.
@Suppress("DEPRECATION", "ExplicitGarbageCollectionCall")
private fun collectGarbage() {
    System.gc()
    System.runFinalization()
    System.gc()
}

/** A capture that missed its frame is a single colour; every real screen has text on its ground. */
private fun flat(file: File): Boolean {
    val image = javax.imageio.ImageIO.read(file) ?: return false
    val first = image.getRGB(0, 0)
    for (y in 0 until image.height step FLAT_STEP) {
        for (x in 0 until image.width step FLAT_STEP) {
            if (image.getRGB(x, y) != first) return false
        }
    }
    return true
}

private const val FLAT_STEP = 4
