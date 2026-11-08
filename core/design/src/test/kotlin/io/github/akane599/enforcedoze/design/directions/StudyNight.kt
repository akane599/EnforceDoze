// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design.directions

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.time.Duration
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// The direction study of SPEC 10.5 (ADR 0028): one synthetic night drawn by three directions. Nothing here
// ships; the chosen direction becomes the real tokens and components in main.

internal enum class Phase { ACTIVE, LIGHT, DEEP, MISSING }

internal data class Span(val from: LocalTime, val to: LocalTime, val phase: Phase)

internal data class Break(val at: LocalTime, val reason: String)

internal data class Waker(val app: String, val wakeups: Int, val detail: String)

internal data class PastNight(val label: String, val deepShare: Double, val drainPerHour: Double, val spans: List<Span>)

/** A synthetic unplugged night on the Night profile: screen off 23:41, deep idle at 00:24, one break for motion. */
internal object StudyNight {
    val screenOff: LocalTime = LocalTime.of(23, 41)
    val screenOn: LocalTime = LocalTime.of(6, 58)
    val deepDue: LocalTime = LocalTime.of(0, 23)
    val deepAt: LocalTime = LocalTime.of(0, 24)
    val spans = listOf(
        Span(LocalTime.of(23, 41), LocalTime.of(0, 5), Phase.ACTIVE),
        Span(LocalTime.of(0, 5), LocalTime.of(0, 24), Phase.LIGHT),
        Span(LocalTime.of(0, 24), LocalTime.of(3, 40), Phase.DEEP),
        Span(LocalTime.of(3, 40), LocalTime.of(3, 44), Phase.ACTIVE),
        Span(LocalTime.of(3, 44), LocalTime.of(3, 58), Phase.LIGHT),
        Span(LocalTime.of(3, 58), LocalTime.of(6, 58), Phase.DEEP),
    )
    val maintenance = listOf(LocalTime.of(1, 12), LocalTime.of(2, 21), LocalTime.of(4, 51), LocalTime.of(5, 47), LocalTime.of(6, 30))
    val breaks = listOf(Break(LocalTime.of(3, 40), "motion"))
    const val DEEP_SHARE = 0.86
    const val LIGHT_SHARE = 0.08
    const val ACTIVE_SHARE = 0.06
    const val DRAIN_PER_HOUR = 0.40
    const val DRAIN_MAH = 146
    const val MOTION_RESETS = 1
    const val APP_WAKEUPS = 0
    val deepTime: Duration = Duration.ofMinutes(376)
    val wakers = listOf(
        Waker("Messages", 4, "4 alarm wakeups"),
        Waker("Galaxy Wearable", 3, "2 alarm wakeups, 1 network"),
        Waker("Clock", 1, "the 06:45 alarm"),
    )
    const val STOCK_DRAIN_PER_HOUR = 0.92
    const val STOCK_DEEP_SHARE = 0.41
    val week = listOf(
        PastNight("Sat", 0.88, 0.37, nightLike(0, 18, 40)),
        PastNight("Fri", 0.62, 0.71, nightLike(25, 52, 15)),
        PastNight("Thu", 0.84, 0.42, nightLike(5, 31, 30)),
        PastNight("Wed", 0.90, 0.33, nightLike(0, 12, 55)),
        PastNight("Tue", 0.79, 0.48, nightLike(12, 40, 5)),
        PastNight("Mon", 0.85, 0.40, nightLike(8, 22, 20)),
    )

    /** A plausible night for the comparison rows: [lateMin] after 23:30 to light, [toDeepMin] more to deep. */
    private fun nightLike(lateMin: Long, toDeepMin: Long, breakMin: Long): List<Span> {
        val off = LocalTime.of(23, 30).plusMinutes(lateMin)
        val light = off.plusMinutes(toDeepMin / 2)
        val deep = off.plusMinutes(toDeepMin)
        val brk = LocalTime.of(3, 0).plusMinutes(breakMin)
        return listOf(
            Span(off, light, Phase.ACTIVE),
            Span(light, deep, Phase.LIGHT),
            Span(deep, brk, Phase.DEEP),
            Span(brk, brk.plusMinutes(6), Phase.ACTIVE),
            Span(brk.plusMinutes(6), brk.plusMinutes(20), Phase.LIGHT),
            Span(brk.plusMinutes(20), LocalTime.of(6, 50), Phase.DEEP),
        )
    }

    /** Minutes from [screenOff] (or from 21:00 for the week rows), across midnight. */
    fun minutesFrom(origin: LocalTime, time: LocalTime): Long {
        val m = Duration.between(origin, time).toMinutes()
        return if (m < 0) m + 24 * 60 else m
    }
}

/** True when [t] falls inside [span], on the night's axis from screen off. */
internal fun inside(t: LocalTime, span: Span): Boolean {
    val at = StudyNight.minutesFrom(StudyNight.screenOff, t)
    return at > StudyNight.minutesFrom(StudyNight.screenOff, span.from) && at < StudyNight.minutesFrom(StudyNight.screenOff, span.to)
}

/** The parts of [spans] inside the window [from]..[to], for strips that show only a slice of the night. */
internal fun clip(spans: List<Span>, from: LocalTime, to: LocalTime): List<Span> {
    fun axis(t: LocalTime) = StudyNight.minutesFrom(LocalTime.of(18, 0), t)
    return spans.mapNotNull { s ->
        val start = maxOf(axis(s.from), axis(from))
        val end = minOf(axis(s.to), axis(to))
        if (end <= start) null else Span(LocalTime.of(18, 0).plusMinutes(start), LocalTime.of(18, 0).plusMinutes(end), s.phase)
    }
}

/** The study's fonts, read from the files beside it; the chosen face moves to `res/font` in main. */
internal object StudyFonts {
    private val dir = File(System.getProperty("enforcedoze.root"), "core/design/src/test/fonts")

    val barlow = FontFamily(
        Font(File(dir, "barlowsemicondensed/BarlowSemiCondensed-Regular.ttf"), FontWeight.Normal),
        Font(File(dir, "barlowsemicondensed/BarlowSemiCondensed-Medium.ttf"), FontWeight.Medium),
        Font(File(dir, "barlowsemicondensed/BarlowSemiCondensed-SemiBold.ttf"), FontWeight.SemiBold),
    )

    val atkinson = variable(File(dir, "atkinsonhyperlegiblenext/AtkinsonHyperlegibleNext-wght.ttf"))

    val overpass = variable(File(dir, "overpass/Overpass-wght.ttf"))

    private fun variable(file: File) = FontFamily(
        Font(file, FontWeight.Normal),
        Font(file, FontWeight.Medium),
        Font(file, FontWeight.SemiBold),
        Font(file, FontWeight.Bold),
    )
}

/** An OKLCH colour, as DESIGN.md writes them, converted to sRGB (Björn Ottosson's OKLab matrices). */
internal fun oklch(l: Double, c: Double, h: Double, alpha: Float = 1f): Color {
    val rad = Math.toRadians(h)
    val a = c * cos(rad)
    val b = c * sin(rad)
    val l1 = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m1 = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s1 = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    val r = 4.0767416621 * l1 - 3.3077115913 * m1 + 0.2309699292 * s1
    val g = -1.2684380046 * l1 + 2.6097574011 * m1 - 0.3413193965 * s1
    val bl = -0.0041960863 * l1 - 0.7034186147 * m1 + 1.7076147010 * s1
    return Color(encode(r), encode(g), encode(bl), alpha)
}

private fun encode(linear: Double): Float {
    val v = linear.coerceIn(0.0, 1.0)
    val s = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055
    return s.toFloat()
}
