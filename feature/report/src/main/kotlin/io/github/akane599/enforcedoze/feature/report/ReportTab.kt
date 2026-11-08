// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.feature.report

/** The report's tabs (SPEC 9.1), in order. */
public enum class ReportTab(internal val title: Int) {
    SUMMARY(R.string.report_tab_summary),
    TIMELINE(R.string.report_tab_timeline),
    WAKERS(R.string.report_tab_wakers),
    SENSORS(R.string.report_tab_sensors),
    ACTIONS(R.string.report_tab_actions),
    RAW(R.string.report_tab_raw),
}
