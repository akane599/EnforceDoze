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
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

/** Application-scoped connection; activities cannot replace the service's listener. */
public final class ShizukuHandler {
    private static ShizukuHandler instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<OnAvailibilityChange> listeners = new CopyOnWriteArraySet<>();
    private final Shizuku.UserServiceArgs args;
    private volatile IPrivilegedService service;
    private volatile CountDownLatch binding;
    private boolean available;
    private final java.util.concurrent.ExecutorService binderCalls = java.util.concurrent.Executors.newSingleThreadExecutor();
    public interface OnAvailibilityChange { void onChange(Boolean value); }
    public interface OnCommandResultListener {
        void onCommandResult(int commandCode, int exitCode, List<String> stdout, List<String> stderr);
    }
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IPrivilegedService.Stub.asInterface(binder);
            CountDownLatch latch = binding;
            if (latch != null) latch.countDown();
        }
        @Override public void onServiceDisconnected(ComponentName name) { disconnect(); }
    };
    private ShizukuHandler(Context context) {
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
    public CommandResult executeBlocking(String command) {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Blocking command on UI thread");
        if (!isShizukuAvailable()) return new CommandResult(-1, "Start Shizuku and authorize EnforceDoze");
        try {
            IPrivilegedService remote = service;
            if (remote == null || !remote.asBinder().pingBinder()) {
                CountDownLatch latch = new CountDownLatch(1);
                binding = latch;
                main.post(() -> {
                    try { Shizuku.bindUserService(args, connection); }
                    catch (RuntimeException e) { latch.countDown(); }
                });
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    binding = null;
                    return new CommandResult(124, "Shizuku user service connection timed out");
                }
                remote = service;
            }
            if (remote == null) return new CommandResult(-1, "Shizuku user service disconnected");
            final IPrivilegedService target = remote;
            java.util.concurrent.Future<String[]> call = binderCalls.submit(() -> target.execute(command));
            try { return CommandResult.decode(call.get(10, TimeUnit.SECONDS)); }
            catch (java.util.concurrent.TimeoutException e) {
                call.cancel(true);
                disconnect();
                main.post(() -> { try { Shizuku.unbindUserService(args, connection, true); } catch (RuntimeException ignored) { } });
                return new CommandResult(124, "Shizuku operation timed out; retry after the user service reconnects");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(130, "Interrupted");
        } catch (Exception e) {
            disconnect(); refresh();
            return new CommandResult(-1, "Shizuku command failed: " + e);
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
