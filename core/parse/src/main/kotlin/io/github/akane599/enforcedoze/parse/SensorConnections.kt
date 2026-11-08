// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.parse

/** One sensor a client holds open (SPEC 8.1 "Sensor snapshot"). */
public data class SensorConnection(
    /** The connection's package name: an app package, or the listener class for system clients. */
    val listener: String,
    val uid: Int,
    val sensor: String,
    val handle: Int,
    /** The sampling period the newest registration for this listener asked for; null when the log no longer has it. */
    val samplingPeriodUs: Long?,
)

/**
 * The open event connections of `dumpsys sensorservice` with their sampling periods. Formats from
 * AOSP `frameworks/native/services/sensorservice`:
 *
 * - `SensorEventConnection::dump` prints `Connection Number: n`, then
 *   `\t <package> | WakeLockRefCount n | uid n | …` and one `\t <sensor> 0x<handle> | …` line per
 *   sensor. That holds on `android-15.0.0_r1` (`| status:`), `android-16.0.0_r1` and
 *   `android-17.0.0_r1` (`| first flush pending:`).
 * - `SensorRegistrationInfo::dump` logs each registration, newest first, under `Previous Registrations:`:
 *   - 15: `hh:mm:ss ± 0x<handle> pid= n uid= n package=<package>[ samplingPeriod=nus batchingPeriod=nus]`;
 *   - 16 and 17: `… samplingPeriod=   nus batchingPeriod=   nus result=OK (sensor, package): (<sensor>, <package>)`,
 *     with `N/A` for a removal and the sensor name padded to 27 characters.
 */
public object SensorConnections {
    private val CONNECTION = Regex("""^Connection Number: \d+\s*$""")
    private val LISTENER = Regex("""^\s+(?<name>.+?) \| WakeLockRefCount -?\d+ \| uid (?<uid>\d+) \|.*$""")
    private val SENSOR = Regex("""^\s+(?<sensor>.+?) 0x(?<handle>[0-9a-f]{8}) \| .*$""")
    private val END = Regex("""^(?:\d+ (?:open )?direct connections|Previous Registrations:)\s*$""")
    private const val REGISTRATIONS = "Previous Registrations:"
    private const val HEX = 16

    private const val PREFIX = """^\d{2}:\d{2}:\d{2} (?<sign>[+-]) 0x(?<handle>[0-9a-f]{8}) pid=\s*-?\d+ uid=\s*(?<uid>\d+)"""
    private val REGISTRATION_16 =
        Regex("""$PREFIX samplingPeriod=\s*(?<period>\d+|N/A)\s*us batchingPeriod=.*\(sensor, package\): \(.*, (?<pkg>[^,]*)\)$""")
    private val REGISTRATION_15 = Regex("""$PREFIX package=(?<pkg>\S+)(?: samplingPeriod=(?<period>\d+)us batchingPeriod=\d+us)?$""")

    private data class Key(val handle: Int, val uid: Int, val listener: String)

    public fun parse(text: String): List<SensorConnection> {
        val lines = text.lines()
        val periods = periods(lines.dropWhile { it.trim() != REGISTRATIONS }.drop(1))
        val connections = mutableListOf<SensorConnection>()
        var inBlock = false
        var listener: Pair<String, Int>? = null
        for (line in lines) {
            when {
                CONNECTION.matches(line) -> {
                    inBlock = true
                    listener = null
                }

                END.matches(line.trim()) -> inBlock = false

                inBlock -> listener = read(line, listener, periods, connections)
            }
        }
        return connections
    }

    /** Reads one line of a connection block; returns the block's listener. */
    private fun read(
        line: String,
        listener: Pair<String, Int>?,
        periods: Map<Key, Long?>,
        into: MutableList<SensorConnection>,
    ): Pair<String, Int>? {
        LISTENER.matchEntire(line)?.let { match ->
            val uid = match.group("uid").toIntOrNull() ?: return listener
            return match.group("name") to uid
        }
        val sensor = SENSOR.matchEntire(line) ?: return listener
        val (name, uid) = listener ?: return null
        val handle = sensor.group("handle").toLong(HEX).toInt()
        into += SensorConnection(name, uid, sensor.group("sensor").trim(), handle, periods[Key(handle, uid, name)])
        return listener
    }

    /** The newest registration per listener and sensor decides: its period when it is an activation, else null. */
    private fun periods(registrations: List<String>): Map<Key, Long?> {
        val periods = mutableMapOf<Key, Long?>()
        val matches = registrations.mapNotNull { REGISTRATION_16.matchEntire(it) ?: REGISTRATION_15.matchEntire(it) }
        for (match in matches) {
            val uid = match.group("uid").toIntOrNull() ?: continue
            val key = Key(match.group("handle").toLong(HEX).toInt(), uid, match.group("pkg"))
            // A removal stores null, so presence, not the value, marks a key as decided.
            if (key !in periods) periods[key] = if (match.group("sign") == "+") match.group("period").toLongOrNull() else null
        }
        return periods
    }

    private fun MatchResult.group(name: String): String = groups[name]?.value.orEmpty()
}
