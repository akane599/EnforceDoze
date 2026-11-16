// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.github.akane599.enforcedoze.ui.PickApp

/** App names for packages and uids, from the package manager (`QUERY_ALL_PACKAGES`, upstream's manifest). */
internal class Labels(context: Context) {
    private val packages: PackageManager = context.packageManager

    /** The app's name, or null when the package is not installed. */
    fun label(packageName: String): String? = runCatching {
        packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

    /** The first package of [uid] and its name; null for a uid no installed package has (a kernel or a removed app). */
    fun forUid(uid: Int): Pair<String, String>? {
        val packageName = packages.getPackagesForUid(uid)?.firstOrNull() ?: return null
        return (label(packageName) ?: packageName) to packageName
    }

    fun installed(packageName: String): Boolean = label(packageName) != null

    /** Every installed app for a picker, by name; [exclude] (the app itself, say) left out. */
    fun apps(exclude: Set<String> = emptySet()): List<PickApp> = packages.getInstalledApplications(0)
        .filter { it.packageName !in exclude }
        .map { info ->
            PickApp(
                packageName = info.packageName,
                label = packages.getApplicationLabel(info).toString(),
                system = info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            )
        }
        .sortedBy { it.label.lowercase() }
}
