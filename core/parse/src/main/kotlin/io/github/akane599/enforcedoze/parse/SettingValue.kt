// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

/**
 * One `key=value` from the Settings section of `dumpsys deviceidle`. Durations are printed by AOSP
 * `TimeUtils.formatDuration` (`+1h0m0s0ms`, `+30s0ms`, `0`), some keys as plain numbers
 * (`light_idle_linear_increase_factor_ms=300000`), factors as decimals (`2.0`), `location_accuracy`
 * with a unit (`20.0m`) and flags as `true` / `false`. The raw text is always kept.
 */
public data class SettingValue(val raw: String) {
    /** Milliseconds when the value is in duration syntax, else null. */
    public val durationMillis: Long? get() = AndroidDuration.parseMillis(raw)

    /** The value as a whole number: a duration in milliseconds or a plain integer. */
    public val longValue: Long? get() = durationMillis ?: raw.toLongOrNull()

    /** Decimal value, ignoring a trailing unit letter such as the `m` in `20.0m`. */
    public val number: Double? get() = longValue?.toDouble() ?: raw.removeSuffix("m").toDoubleOrNull()

    public val flag: Boolean? get() = raw.toBooleanStrictOrNull()

    override fun toString(): String = raw
}

/** Parser for durations printed by AOSP `android.util.TimeUtils.formatDuration`. */
public object AndroidDuration {
    private val PATTERN =
        Regex("""([+-])(?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m(?!s))?(?:(\d+)s)?(?:(\d+)ms)?""")

    private const val SECOND = 1_000L
    private const val MINUTE = 60 * SECOND
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** Milliseconds for `+1h0m0s0ms`, `-2h30m25s431ms` or `0`; null for anything else. */
    public fun parseMillis(text: String): Long? {
        val value = text.trim()
        if (value == "0") return 0
        val match = PATTERN.matchEntire(value) ?: return null
        val groups = match.groupValues
        if (groups.drop(2).all { it.isEmpty() }) return null
        val units = listOf(DAY, HOUR, MINUTE, SECOND, 1L)
        val total = groups.drop(2).zip(units).sumOf { (digits, unit) -> (digits.toLongOrNull() ?: 0) * unit }
        return if (groups[1] == "-") -total else total
    }
}
