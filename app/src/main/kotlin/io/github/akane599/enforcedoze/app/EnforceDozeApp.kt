// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app

import android.app.Application
import io.github.akane599.enforcedoze.app.automation.AutomationReceiver
import kotlin.concurrent.thread

/** The application: upstream's `MyApplication`, the last of its classes, ported (ADR 0037). */
class EnforceDozeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The automation receiver follows its switch again after a backup restore (ADR 0033); off the main thread, so
        // the preference read and the package manager call stay out of the cold start.
        thread(name = "automation-sync") { AutomationReceiver.sync(this) }
    }
}
