// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.probe

import android.os.Build
import io.github.akane599.enforcedoze.privilege.Tier
import io.github.akane599.enforcedoze.privilege.WorkerSource

public enum class ProbeStatus { OK, MISSING, FAILED, SKIPPED }

public data class ProbeResult(val id: String, val title: String, val status: ProbeStatus, val detail: String)

public data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val release: String,
    val sdk: Int,
    val buildId: String,
    val fingerprint: String,
) {
    public companion object {
        /** This device, from `android.os.Build`. */
        public fun current(): DeviceInfo = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            release = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            buildId = Build.ID,
            fingerprint = Build.FINGERPRINT,
        )
    }
}

/** The capability probe results (SPEC 4.4), shown on the Privileges screen and exported to `docs/capabilities/`. */
public data class CapabilityReport(
    val device: DeviceInfo,
    val tier: Tier,
    val workerSource: WorkerSource?,
    val workerUid: Int?,
    val includedChanges: Boolean,
    val capturedAt: String,
    val results: List<ProbeResult>,
) {
    public fun result(id: String): ProbeResult? = results.firstOrNull { it.id == id }

    /** File name for `docs/capabilities/`: `<model>_api<sdk>_<build id>.md`, lower-case model. */
    public val fileName: String
        get() = "${device.model.lowercase().replace(Regex("[^a-z0-9-]+"), "-")}_api${device.sdk}_${device.buildId}.md"

    public fun toMarkdown(): String = buildString {
        appendLine("# Capabilities: ${device.manufacturer} ${device.model}, API ${device.sdk} (${device.buildId})")
        appendLine()
        appendLine("Captured $capturedAt by the app's capability probes (SPEC 4.4).")
        appendLine()
        appendLine("- Android ${device.release}, fingerprint `${device.fingerprint}`")
        val worker = workerSource?.let { " through $it, worker uid $workerUid" }.orEmpty()
        appendLine("- Tier ${tier.name}$worker")
        appendLine(
            "- Probes that change state (lever A test write, forced idle, sensor restriction, sensor privacy setter) " +
                if (includedChanges) "ran and were undone" else "were skipped",
        )
        appendLine()
        appendLine("| Probe | Result | Detail |")
        appendLine("| --- | --- | --- |")
        results.forEach { appendLine("| ${it.title} (`${it.id}`) | ${it.status} | ${it.detail.replace("|", "\\|").replace("\n", " ")} |") }
    }
}
