// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege.worker

import android.content.Intent
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService

/** Hosts [DozeWorker] in a libsu root process for the root tier (SPEC 4.1 T3). */
public class DozeRootService : RootService() {
    override fun onBind(intent: Intent): IBinder = DozeWorker(this)
}
