// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku
import rikka.sui.Sui

/** Reads the durable grants this app holds. */
public fun interface GrantReader {
    public fun read(): Set<Grant>
}

/** The Shizuku client library behind an interface, so tier logic is testable without a server. */
public interface ShizukuGateway {
    public fun status(): ShizukuStatus

    public fun requestPermission()

    /** Calls [onChange] when the binder arrives or dies and on permission results. */
    public fun observe(onChange: () -> Unit): AutoCloseable
}

public interface RootGateway {
    /** Whether libsu already knows this app has root; null until a shell was opened. Never prompts. */
    public fun isGranted(): Boolean

    /** Opens libsu's shell, which asks the root manager; blocking. */
    public fun request()
}

internal class AndroidGrantReader(private val context: Context) : GrantReader {
    override fun read(): Set<Grant> = buildSet {
        fun has(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        if (has("android.permission.DUMP")) add(Grant.DUMP)
        if (has("android.permission.WRITE_SECURE_SETTINGS")) add(Grant.WRITE_SECURE_SETTINGS)
        if (has("android.permission.READ_LOGS")) add(Grant.READ_LOGS)
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val usage = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        if (usage == AppOpsManager.MODE_ALLOWED) add(Grant.USAGE_STATS)
        if (context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) add(Grant.EXACT_ALARM)
    }
}

/**
 * Shizuku API 13 (`dev.rikka.shizuku:api`). `Sui.init` runs first, as SPEC 4.2 asks; with Sui the same
 * API talks to Sui's root server. Server forks that keep Shizuku's binder protocol work unchanged
 * (ADR 0011).
 */
internal class RealShizukuGateway(packageName: String) : ShizukuGateway {
    private val sui: Boolean = Sui.init(packageName)

    override fun status(): ShizukuStatus = try {
        when {
            !Shizuku.pingBinder() -> ShizukuStatus.NotRunning

            Shizuku.isPreV11() -> ShizukuStatus.Unsupported

            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                ShizukuStatus.Ready(Shizuku.getUid(), Shizuku.getVersion(), sui)

            else -> ShizukuStatus.PermissionNeeded(canRequest = !Shizuku.shouldShowRequestPermissionRationale())
        }
    } catch (_: IllegalStateException) {
        // The binder died between the calls.
        ShizukuStatus.NotRunning
    }

    override fun requestPermission() {
        Shizuku.requestPermission(REQUEST_CODE)
    }

    override fun observe(onChange: () -> Unit): AutoCloseable {
        val received = Shizuku.OnBinderReceivedListener { onChange() }
        val dead = Shizuku.OnBinderDeadListener { onChange() }
        val result = Shizuku.OnRequestPermissionResultListener { _, _ -> onChange() }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(result)
        return AutoCloseable {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(result)
        }
    }

    private companion object {
        const val REQUEST_CODE = 0x5a1
    }
}

internal object LibsuRootGateway : RootGateway {
    // libsu 6: Shell.isAppGrantedRoot() is null before the first shell, so this stays false until the
    // user asks for root once (request) or a previous request this process already succeeded.
    override fun isGranted(): Boolean = Shell.isAppGrantedRoot() == true

    override fun request() {
        Shell.getShell()
    }
}
