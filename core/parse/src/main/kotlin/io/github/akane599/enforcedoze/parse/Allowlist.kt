// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

/** One row of `cmd deviceidle whitelist`: `system-excidle,com.android.shell,2000`. */
public data class AllowlistEntry(val kind: String, val packageName: String, val uid: Int?)

public data class Allowlist(val entries: List<AllowlistEntry>, val unknownLines: List<String>) {
    public fun contains(packageName: String, kind: String? = null): Boolean =
        entries.any { it.packageName == packageName && (kind == null || it.kind == kind) }

    public companion object {
        private val ROW = Regex("""^([a-z-]+),([A-Za-z0-9_.]+),(\d+)$""")

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
