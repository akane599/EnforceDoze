package com.akylas.enforcedoze;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import rikka.shizuku.Shizuku;

/** Application-scoped connection; activities cannot replace the service's listener. */
@android.annotation.SuppressLint("StaticFieldLeak") // The singleton only ever holds the app context.
public final class ShizukuHandler {
    /** One UI can take several seconds to hand back a user service after a cold Shizuku start. */
    private static final long BIND_TIMEOUT_SECONDS = 15;
    private static final long CALL_TIMEOUT_SECONDS = 20;
    private static ShizukuHandler instance;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<OnAvailibilityChange> listeners = new CopyOnWriteArraySet<>();
    private final Shizuku.UserServiceArgs args;
    private volatile IPrivilegedService service;
    private volatile CountDownLatch binding;
    private boolean available;
    /**
     * A one-way Binder call cannot be interrupted, so a wedged transaction would keep every later
     * command queued behind it. The executor is replaced after a timeout to release the queue.
     */
    private volatile ExecutorService binderCalls = newCallExecutor();
    public interface OnAvailibilityChange { void onChange(Boolean value); }
    public interface OnCommandResultListener {
        void onCommandResult(int commandCode, int exitCode, List<String> stdout, List<String> stderr);
    }
    private static ExecutorService newCallExecutor() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "shizuku-binder");
            thread.setDaemon(true);
            return thread;
        });
    }
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = binder == null ? null : IPrivilegedService.Stub.asInterface(binder);
            CountDownLatch latch = binding;
            if (latch != null) latch.countDown();
        }
        @Override public void onServiceDisconnected(ComponentName name) { disconnect(); }
    };
    private ShizukuHandler(Context context) {
        this.context = context;
        args = new Shizuku.UserServiceArgs(new ComponentName(context, PrivilegedService.class))
                .daemon(false).processNameSuffix("privileged").tag("enforcedoze-commands")
                .version(BuildConfig.VERSION_CODE);
        Shizuku.addBinderReceivedListenerSticky(this::refresh);
        Shizuku.addBinderDeadListener(() -> { disconnect(); refresh(); });
        Shizuku.addRequestPermissionResultListener((code, result) -> { if (code == 412) refresh(); });
    }
    public static synchronized ShizukuHandler getInstance(Context context) {
        if (instance == null) instance = new ShizukuHandler(context.getApplicationContext());
        return instance;
    }
    private void disconnect() {
        service = null;
        CountDownLatch latch = binding;
        if (latch != null) latch.countDown();
        binding = null;
    }
    private void refresh() {
        main.post(() -> {
            boolean current = isShizukuAvailable();
            if (available != current) {
                available = current;
                for (OnAvailibilityChange listener : listeners) listener.onChange(current);
            }
        });
    }
    public void addAvailabilityListener(OnAvailibilityChange listener) { listeners.add(listener); }
    public void removeAvailabilityListener(OnAvailibilityChange listener) { listeners.remove(listener); }
    public void checkShizukuAvailability() { refresh(); }
    public boolean isBinderAlive() {
        try { return Shizuku.pingBinder() && !Shizuku.isPreV11(); }
        catch (RuntimeException e) { return false; }
    }
    public int checkShizukuPermission() {
        try { return isBinderAlive() ? Shizuku.checkSelfPermission() : PackageManager.PERMISSION_DENIED; }
        catch (RuntimeException e) { return PackageManager.PERMISSION_DENIED; }
    }
    public boolean isShizukuAvailable() { return checkShizukuPermission() == PackageManager.PERMISSION_GRANTED; }
    public void requestShizukuPermission() {
        if (isBinderAlive() && !isShizukuAvailable()) {
            try { Shizuku.requestPermission(412); } catch (RuntimeException e) { refresh(); }
        }
    }
    private String text(int resource) { return context.getString(resource); }
    /** Binds the privileged user service, waiting out a Shizuku that is still starting up. */
    private IPrivilegedService connect() throws InterruptedException {
        IPrivilegedService remote = service;
        if (remote != null && remote.asBinder().pingBinder()) return remote;
        CountDownLatch latch = new CountDownLatch(1);
        binding = latch;
        main.post(() -> {
            try { Shizuku.bindUserService(args, connection); }
            catch (RuntimeException e) {
                Utils.logToLogcat("Shizuku", "Unable to bind the user service: " + e);
                latch.countDown();
            }
        });
        if (!latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) binding = null;
        return service;
    }
    public CommandResult executeBlocking(String command) {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Blocking command on UI thread");
        if (!isShizukuAvailable()) return new CommandResult(-1, text(R.string.shizuku_not_authorized));
        try {
            IPrivilegedService remote = connect();
            // A cold One UI start can drop the first bind; one retry avoids a spurious failure.
            if (remote == null) {
                disconnect();
                remote = connect();
            }
            if (remote == null) return new CommandResult(124, text(R.string.shizuku_bind_timeout));
            final IPrivilegedService target = remote;
            Future<String[]> call = binderCalls.submit(() -> target.execute(command));
            try { return CommandResult.decode(call.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)); }
            catch (TimeoutException e) {
                call.cancel(true);
                // The wedged thread keeps the dead transaction; later commands get a fresh queue.
                binderCalls.shutdownNow();
                binderCalls = newCallExecutor();
                disconnect();
                main.post(() -> { try { Shizuku.unbindUserService(args, connection, true); } catch (RuntimeException ignored) { } });
                return new CommandResult(124, text(R.string.shizuku_command_timeout));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(130, text(R.string.command_interrupted));
        } catch (Exception e) {
            disconnect(); refresh();
            return new CommandResult(-1, text(R.string.shizuku_command_failed) + "\n" + e);
        }
    }
    public void executeCommand(String command, OnCommandResultListener callback) { executeCommand(command, callback, false); }
    public void executeCommand(String command, OnCommandResultListener callback, boolean print) {
        CommandExecutor.submit(() -> {
            CommandResult result = executeBlocking(command);
            if (print) Utils.logToLogcat("Shizuku", result.output);
            main.post(() -> callback.onCommandResult(0, result.exitCode, result.success() ? result.lines() : Collections.emptyList(),
                    result.success() ? Collections.emptyList() : result.lines()));
        });
    }
}
