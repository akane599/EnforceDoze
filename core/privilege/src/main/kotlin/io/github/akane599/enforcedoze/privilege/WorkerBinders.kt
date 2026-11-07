// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.privilege

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.topjohnwu.superuser.ipc.RootService
import io.github.akane599.enforcedoze.privilege.api.IDozeWorker
import io.github.akane599.enforcedoze.privilege.api.WorkerContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.coroutines.resumeWithException

/** Starts and connects one kind of worker process. */
public interface WorkerBinder {
    public val source: WorkerSource

    /** Suspends until connected; [onLost] runs if the connection dies later. */
    public suspend fun bind(onLost: () -> Unit): DozeWorkerApi

    /** Disconnects and lets the worker process end. Safe to call from any thread, and more than once. */
    public fun unbind()
}

/** Shizuku or Sui UserService (SPEC 4.3). Not a daemon: the process ends when it is unbound. */
internal class ShizukuWorkerBinder(packageName: String, debuggable: Boolean, override val source: WorkerSource) : WorkerBinder {
    private val args = Shizuku.UserServiceArgs(ComponentName(packageName, WorkerContract.WORKER_CLASS))
        .daemon(false)
        .processNameSuffix(WorkerContract.PROCESS_SUFFIX)
        .debuggable(debuggable)
        .version(WorkerContract.VERSION)
    private var connection: ServiceConnection? = null

    override suspend fun bind(onLost: () -> Unit): DozeWorkerApi = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val conn = connection(onLost) { result -> if (cont.isActive) cont.resumeWith(result) }
            connection = conn
            fun fail(e: RuntimeException) {
                connection = null
                if (cont.isActive) cont.resumeWithException(e)
            }
            try {
                Shizuku.bindUserService(args, conn)
            } catch (e: IllegalStateException) {
                fail(e) // no binder
            } catch (e: SecurityException) {
                fail(e) // no permission
            }
        }
    }

    override fun unbind() {
        val conn = connection ?: return
        connection = null
        try {
            Shizuku.unbindUserService(args, conn, true)
        } catch (_: RuntimeException) {
            // The server is already gone, and the worker with it.
        }
    }
}

/** libsu RootService hosting the same worker (SPEC 4.1 T3). `RootService.bind` must run on the main thread. */
internal class RootWorkerBinder(private val context: Context) : WorkerBinder {
    override val source: WorkerSource = WorkerSource.ROOT
    private var connection: ServiceConnection? = null

    override suspend fun bind(onLost: () -> Unit): DozeWorkerApi = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val conn = connection(onLost) { result -> if (cont.isActive) cont.resumeWith(result) }
            connection = conn
            RootService.bind(Intent().setComponent(ComponentName(context, WorkerContract.ROOT_SERVICE_CLASS)), conn)
        }
    }

    override fun unbind() {
        val conn = connection ?: return
        connection = null
        onMain { RootService.unbind(conn) }
    }
}

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

private fun onMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
}

private fun connection(onLost: () -> Unit, deliver: (Result<DozeWorkerApi>) -> Unit) = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        if (service == null || !service.pingBinder()) {
            deliver(Result.failure(IllegalStateException("worker returned no live binder")))
        } else {
            deliver(Result.success(BinderDozeWorkerApi(IDozeWorker.Stub.asInterface(service))))
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) = onLost()

    override fun onBindingDied(name: ComponentName?) = onLost()
}
