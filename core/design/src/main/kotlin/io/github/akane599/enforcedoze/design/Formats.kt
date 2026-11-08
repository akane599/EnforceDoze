// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.design

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Numbers, times and durations as the screens print them, in the owner's language and clock format.
 * Percent signs go after the number in English and before it in Turkish (`ed_unit_first`).
 */
public class Formats(private val context: Context, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val locale: Locale get() = context.resources.configuration.locales[0]
    private val clockPattern: String get() = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"

    public fun clock(millis: Long): String =
        DateTimeFormatter.ofPattern(clockPattern, locale).withZone(zone).format(Instant.ofEpochMilli(millis))

    /** A wall-clock time with no date, such as a plan's window edge, on the owner's clock format. */
    public fun time(time: LocalTime): String = DateTimeFormatter.ofPattern(clockPattern, locale).format(time)

    /** The hour alone for strip axes: "02" on a 24-hour clock, "2 AM" on a 12-hour one. */
    public fun hour(millis: Long): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH" else "h a"
        return DateTimeFormatter.ofPattern(pattern, locale).withZone(zone).format(Instant.ofEpochMilli(millis))
    }

    /** A date as the owner's locale writes it in full, for report titles. */
    public fun date(millis: Long, skeleton: String = "dMMMM"): String {
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        return DateTimeFormatter.ofPattern(pattern, locale).withZone(zone).format(Instant.ofEpochMilli(millis))
    }

    public fun duration(millis: Long): String {
        val minutes = (millis / MINUTE.toDouble()).roundToInt()
        val h = minutes / MINUTES_PER_HOUR
        val m = minutes % MINUTES_PER_HOUR
        return when {
            millis in 1 until MINUTE -> context.getString(R.string.ed_duration_s, (millis / SECOND).toInt().coerceAtLeast(1))
            h == 0 -> context.getString(R.string.ed_duration_m, m)
            m == 0 -> context.getString(R.string.ed_duration_h, h)
            else -> context.getString(R.string.ed_duration_hm, h, m)
        }
    }

    /** A share as a [Figure]: 0.86 becomes "86" with "%" after it (English) or before it (Turkish). */
    public fun percent(share: Double, label: String): Figure =
        Figure((share * PERCENT).roundToInt().toString(), label, string(R.string.ed_percent_before), string(R.string.ed_percent_after))

    /** Drain per hour as a [Figure], two decimals with the locale's decimal mark. */
    public fun drain(perHour: Double, label: String): Figure =
        Figure(decimal(perHour, 2), label, string(R.string.ed_drain_before), string(R.string.ed_drain_after))

    /** A share inline in a sentence: "86 %" or "%86". */
    public fun percentText(share: Double): String = percent(share, "").plain()

    /** Drain inline in a sentence: "0.40 %/h" or "%0,40/sa". */
    public fun drainText(perHour: Double): String = drain(perHour, "").plain()

    private fun string(id: Int): String? = context.getString(id).ifEmpty { null }

    public fun decimal(value: Double, places: Int): String = String.format(locale, "%.${places}f", value)

    private companion object {
        const val SECOND = 1_000L
        const val MINUTE = 60_000L
        const val MINUTES_PER_HOUR = 60
        const val PERCENT = 100
    }
}

/** The screen's [Formats], rebuilt when the context (and so the language or clock format) changes. */
@Composable
public fun rememberFormats(): Formats {
    val context = LocalContext.current
    return remember(context) { Formats(context) }
}
