// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

/** One row of `cmd deviceidle whitelist`: `system-excidle,com.android.shell,2000`. */
public data class AllowlistEntry(val kind: String, val packageName: String, val uid: Int?)

public data class Allowlist(val entries: List<AllowlistEntry>, val unknownLines: List<String>) {
    public fun contains(packageName: String, kind: String? = null): Boolean =
        entries.any { it.packageName == packageName && (kind == null || it.kind == kind) }

    public companion object {
        private val ROW = Regex("""^([a-z-]+),([A-Za-z0-9_.]+),(\d+)$""")

        /**
         * The same list from `dumpsys deviceidle`, which needs only `DUMP` (below the worker's tier). Its sections are
         * `Whitelist (except idle) system apps`, `Whitelist system apps` and `Whitelist user apps`, one package a line
         * and no uid, as both API 37 captures under `fixtures/` print them; there they list the same rows as
         * `cmd deviceidle whitelist`. A section the device leaves out, as the emulator does for user apps when there
         * are none, holds no rows.
         */
        public fun fromDump(dump: DeviceIdleDump): Allowlist {
            val entries = DUMP_SECTIONS.flatMap { (section, kind) ->
                dump.sections[section].orEmpty().map { AllowlistEntry(kind, it.trim(), null) }
            }
            val (valid, unknown) = entries.partition { PACKAGE.matches(it.packageName) }
            return Allowlist(valid, unknown.map { it.packageName })
        }

        private val PACKAGE = Regex("""[A-Za-z0-9_.]+""")

        private val DUMP_SECTIONS = listOf(
            "Whitelist (except idle) system apps" to "system-excidle",
            "Whitelist system apps" to "system",
            "Whitelist user apps" to "user",
        )

        public fun parse(text: String): Allowlist {
            val entries = mutableListOf<AllowlistEntry>()
            val unknown = mutableListOf<String>()
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                val match = ROW.matchEntire(line)
                if (match == null) {
                    unknown += line
                } else {
                    val (kind, pkg, uid) = match.destructured
                    entries += AllowlistEntry(kind, pkg, uid.toIntOrNull())
                }
            }
            return Allowlist(entries, unknown)
        }
    }
}
