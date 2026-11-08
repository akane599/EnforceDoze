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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resumeWithException

/** Starts and connects one kind of worker process. */
public interface WorkerBinder {
    public val source: WorkerSource

    /** Suspends until connected; [onLost] runs if the connection dies later. */
    public suspend fun bind(onLost: () -> Unit): DozeWorkerApi

    /** Disconnects and lets the worker process end. Safe to call from any thread, and more than once. */
    public fun unbind()

    /**
     * Suspends until a worker unbound earlier is gone, for at most [WorkerContract.UNBIND_SETTLE_MILLIS], so the
     * next [bind] starts clean. Not part of the bind timeout.
     */
    public suspend fun awaitUnbound() {}
}

/** Shizuku or Sui UserService (SPEC 4.3). Not a daemon: the process ends when it is unbound. */
internal class ShizukuWorkerBinder(packageName: String, debuggable: Boolean, override val source: WorkerSource) : WorkerBinder {
    private val args = Shizuku.UserServiceArgs(ComponentName(packageName, WorkerContract.WORKER_CLASS))
        .daemon(false)
        .processNameSuffix(WorkerContract.PROCESS_SUFFIX)
        .debuggable(debuggable)
        .version(WorkerContract.VERSION)

    /** One bind: [gone] completes when its disconnect arrives; [live] once a binder was delivered. */
    private class Binding(val conn: ServiceConnection, val gone: CompletableDeferred<Unit>) {
        @Volatile var live = false
    }

    @Volatile private var binding: Binding? = null

    /**
     * Shizuku-API 13.1.5 caches one `ShizukuServiceConnection` per service (`ShizukuServiceConnections.get`) and drops it
     * only when the old process's death has been delivered on the main thread (`ShizukuServiceConnection.died`). A bind
     * made before that joins the dying connection; the death callback then clears its listeners, so the new bind never
     * hears "connected". After unbinding a live worker, wait for its disconnect first. The cache is per process, so the
     * pending teardown is too: a new binder (a new controller) must also wait for the old one's worker.
     */
    override suspend fun awaitUnbound() {
        val pending = teardown ?: return
        withTimeoutOrNull(WorkerContract.UNBIND_SETTLE_MILLIS) { pending.await() }
        if (teardown === pending) teardown = null
    }

    override suspend fun bind(onLost: () -> Unit): DozeWorkerApi = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val gone = CompletableDeferred<Unit>()
            lateinit var current: Binding
            val conn = connection(onLost = {
                gone.complete(Unit)
                onLost()
            }) { result ->
                if (result.isSuccess) current.live = true
                if (cont.isActive) cont.resumeWith(result)
            }
            current = Binding(conn, gone)
            binding = current
            fun fail(e: RuntimeException) {
                binding = null
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
        val current = binding ?: return
        binding = null
        if (current.live && !current.gone.isCompleted) teardown = current.gone
        try {
            Shizuku.unbindUserService(args, current.conn, true)
        } catch (_: RuntimeException) {
            // The server is already gone, and the worker with it.
        }
    }

    private companion object {
        /** The disconnect of the last live worker unbound in this process, until a bind has waited for it. */
        @Volatile var teardown: CompletableDeferred<Unit>? = null
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
