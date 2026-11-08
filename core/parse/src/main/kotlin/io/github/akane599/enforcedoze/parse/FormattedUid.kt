// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

/**
 * Uids as AOSP `UserHandle.formatUid` prints them in `dumpsys batterystats` and `dumpsys alarm`
 * (`android-16.0.0_r1`, `android-17.0.0_r1`): the plain number below 10000, otherwise `u<user>`
 * followed by `a`, `i`, `ai`, `p` or `s` and the app id minus that range's first uid.
 */
public object FormattedUid {
    /** The printed form, for parsers that match a uid inside a longer line. */
    public const val PATTERN: String = """-?\d+|u\d+(?:ai|[aisp])\d+"""

    private val FORMATTED = Regex("""^u(\d+)(ai|[aisp])(\d+)$""")

    // android.os.Process and UserHandle at android-17.0.0_r1.
    private const val PER_USER_RANGE = 100_000
    private const val FIRST_APPLICATION_UID = 10_000
    private const val FIRST_PCC_UID = 30_000
    private const val FIRST_APP_ZYGOTE_ISOLATED_UID = 90_000
    private const val FIRST_ISOLATED_UID = 99_000

    /** `1000`, `-1`, `u0a357` (10357), `u10a5`, `u0i12`, `u0ai3`, `u0p1`, `u0s1000`; null for anything else. */
    public fun parse(text: String): Int? = text.toIntOrNull() ?: FORMATTED.matchEntire(text)?.let { match ->
        val (user, kind, number) = match.destructured
        val base = when (kind) {
            "a" -> FIRST_APPLICATION_UID
            "i" -> FIRST_ISOLATED_UID
            "ai" -> FIRST_APP_ZYGOTE_ISOLATED_UID
            "p" -> FIRST_PCC_UID
            else -> 0
        }
        val n = number.toIntOrNull()
        user.toIntOrNull()?.let { u -> n?.let { u * PER_USER_RANGE + base + it } }
    }
}
