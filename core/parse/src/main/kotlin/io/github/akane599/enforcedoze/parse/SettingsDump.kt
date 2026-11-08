// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

/**
 * One row of a `dumpsys settings` table (`SettingsProvider.dumpSettingsLocked`, AOSP 17):
 * `_id:<id> name:<name>[ pkg:<package>] value:<value>[ default:<value> defaultSystemSet:<bool>][ tag:<tag>]
 * [ notPreservedInRestore]`. [packageName] is the package SettingsProvider recorded for the last write
 * (ADR 0014); [value] is null for `{null}`.
 */
public data class SettingsRow(val id: String, val name: String, val packageName: String?, val value: String?, val raw: String)

/** Finds single rows in `dumpsys settings` output without keeping the rest, which runs to hundreds of KB. */
public object SettingsDump {
    /** The tables in dump order; CONFIG and GLOBAL are printed for user 0 only. */
    public enum class Table { CONFIG, GLOBAL, SECURE, SYSTEM }

    private val HEADER = Regex("""^(CONFIG|GLOBAL|SECURE|SYSTEM) SETTINGS \(user (\d+)\)$""")
    private val ROW = Regex("""^_id:(\S+) name:(\S+)(?: pkg:(\S+))? value:(.*)$""")
    private val TAIL = Regex("""( default:.*| tag:.*| notPreservedInRestore)$""")

    /** What a lookup found: the row, a table without that row (the setting is not set), or no such table at all. */
    public sealed interface Lookup {
        public data class Found(val row: SettingsRow) : Lookup

        /** The table was dumped and has no row named so: the setting is absent. */
        public data object NoRow : Lookup

        /** The table is not in the text, for example a `Permission Denial` instead of a dump. */
        public data object NoTable : Lookup
    }

    /**
     * The row named [name] in [table] for [user], or null when the table or the row is absent (for example a
     * `Permission Denial` instead of a dump). A value is cut at the first ` default:`, ` tag:` or a trailing
     * ` notPreservedInRestore`, so a foreign value containing those words is cut too; the [SettingsRow.raw] line
     * keeps everything.
     */
    public fun findRow(text: String, table: Table, name: String, user: Int = 0): SettingsRow? =
        (lookup(text, table, name, user) as? Lookup.Found)?.row

    /** Like [findRow], but tells a setting that is not set from a dump that does not show the table. */
    public fun lookup(text: String, table: Table, name: String, user: Int = 0): Lookup {
        var inTable = false
        var seenTable = false
        for (line in text.lineSequence()) {
            val header = HEADER.matchEntire(line)
            if (header != null) inTable = header.groupValues[1] == table.name && header.groupValues[2].toInt() == user
            seenTable = seenTable || (inTable && header != null)
            val row = if (inTable && header == null) row(line) else null
            if (row?.name == name) return Lookup.Found(row)
        }
        return if (seenTable) Lookup.NoRow else Lookup.NoTable
    }

    private fun row(line: String): SettingsRow? {
        if (!line.startsWith("_id:")) return null
        val match = ROW.matchEntire(line) ?: return null
        val groups = match.groupValues
        val value = groups[VALUE].replace(TAIL, "")
        return SettingsRow(groups[ID], groups[NAME], groups[PKG].ifEmpty { null }, value.takeUnless { it == "{null}" }, line)
    }

    private const val ID = 1
    private const val NAME = 2
    private const val PKG = 3
    private const val VALUE = 4
}
