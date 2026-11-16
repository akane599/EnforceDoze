// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.ui

import io.github.akane599.enforcedoze.doze.android.NeverSuspend
import io.github.akane599.enforcedoze.ui.R as UiR

/** The never-suspend reason in the owner's language (ADR 0038). */
internal fun NeverSuspend.label(): Int = when (this) {
    NeverSuspend.NOT_INSTALLED -> UiR.string.ui_never_suspend_not_installed
    NeverSuspend.UNDO -> UiR.string.ui_never_suspend_undo
    NeverSuspend.SYSTEM -> UiR.string.ui_never_suspend_system
    NeverSuspend.DIALER -> UiR.string.ui_never_suspend_dialer
    NeverSuspend.SMS -> UiR.string.ui_never_suspend_sms
    NeverSuspend.HOME -> UiR.string.ui_never_suspend_home
    NeverSuspend.KEYBOARD -> UiR.string.ui_never_suspend_keyboard
    NeverSuspend.ACCESSIBILITY -> UiR.string.ui_never_suspend_accessibility
    NeverSuspend.ALARMS -> UiR.string.ui_never_suspend_alarms
    NeverSuspend.NEXT_ALARM -> UiR.string.ui_never_suspend_next_alarm
    NeverSuspend.CALLS -> UiR.string.ui_never_suspend_calls
    NeverSuspend.KEEP_AWAKE -> UiR.string.ui_never_suspend_keep_awake
}
