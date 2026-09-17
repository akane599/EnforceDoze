package com.akylas.enforcedoze;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

/** Binder availability, authorization, and user-service readiness are deliberately separate. */
public final class ShizukuHandler {
    private static ShizukuHandler instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArraySet<OnAvailibilityChange> listeners = new CopyOnWriteArraySet<>();
    private final Object connectionLock = new Object();
    private final Shizuku.UserServiceArgs args;
    private volatile IPrivilegedService service;
    private volatile boolean binding;
    public interface OnAvailibilityChange { void onChange(Boolean available); }
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (connectionLock) {
                service = IPrivilegedService.Stub.asInterface(binder);
                binding = false;
                connectionLock.notifyAll();
            }
            notifyListeners();
        }
        @Override public void onServiceDisconnected(ComponentName name) { disconnected(); }
    };
    private ShizukuHandler(Context context) {
        args = new Shizuku.UserServiceArgs(new ComponentName(context, PrivilegedService.class))
                .daemon(false).processNameSuffix("privileged").debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE);
        Shizuku.addBinderReceivedListenerSticky(() -> { bind(); notifyListeners(); });
        Shizuku.addBinderDeadListener(this::disconnected);
        Shizuku.addRequestPermissionResultListener((requestCode, grant) -> { bind(); notifyListeners(); });
    }
    public static synchronized ShizukuHandler getInstance(Context context) {
        if (instance == null) instance = new ShizukuHandler(context.getApplicationContext());
        return instance;
    }
    private void disconnected() {
        synchronized (connectionLock) { service = null; binding = false; connectionLock.notifyAll(); }
        notifyListeners();
    }
    public String status() {
        try {
            if (!Shizuku.pingBinder()) return "Shizuku is not running. Open Shizuku and start it again.";
            if (Shizuku.isPreV11()) return "Update Shizuku to version 13 or newer.";
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return "Shizuku authorization is required.";
            return service == null ? "Shizuku authorized; connecting…" : "Shizuku authorized and connected";
        } catch (RuntimeException e) { return "Shizuku disconnected. Open Shizuku to reconnect."; }
    }
    public boolean isShizukuAvailable() {
        try { return Shizuku.pingBinder() && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (RuntimeException e) { return false; }
    }
    public void checkShizukuAvailability() { bind(); }
    public int checkShizukuPermission() { return isShizukuAvailable() ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED; }
    public void requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
                if (!isShizukuAvailable()) Shizuku.requestPermission(0); else bind();
            }
        } catch (RuntimeException ignored) { notifyListeners(); }
    }
    public void addListener(OnAvailibilityChange listener) { listeners.add(listener); }
    public void removeListener(OnAvailibilityChange listener) { listeners.remove(listener); }
    // Compatibility for legacy screens while callers migrate to lifecycle-scoped subscriptions.
    public void setOnAvailibilityChangeListener(OnAvailibilityChange listener) { addListener(listener); }
    public void removePermissionResultListener() { /* process-wide permission listener remains registered */ }
    private void notifyListeners() {
        main.post(() -> { for (OnAvailibilityChange listener : listeners) listener.onChange(isShizukuAvailable()); });
    }
    private void bind() {
        main.post(() -> {
            if (!isShizukuAvailable() || service != null || binding) return;
            binding = true;
            try { Shizuku.bindUserService(args, connection); }
            catch (RuntimeException e) { disconnected(); }
        });
    }
    CommandResult run(String command) {
        if (!isShizukuAvailable()) return new CommandResult(-1, status());
        bind();
        try {
            long end = android.os.SystemClock.elapsedRealtime() + 6000;
            synchronized (connectionLock) {
                while (service == null && isShizukuAvailable()) {
                    long left = end - android.os.SystemClock.elapsedRealtime();
                    if (left <= 0) break;
                    connectionLock.wait(left);
                }
            }
            IPrivilegedService current = service;
            if (current == null) { binding = false; return new CommandResult(-1, "Shizuku user service did not connect. Retry access."); }
            java.util.concurrent.Future<String> call = AccessExecutor.RPC.submit(() -> current.run(command));
            JSONObject result = new JSONObject(call.get(12, TimeUnit.SECONDS));
            return new CommandResult(result.getInt("code"), result.getString("output"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(-2, "Access interrupted; restoration retained.");
        } catch (Exception e) {
            return new CommandResult(-2, "Shizuku operation failed; outcome unknown: " + e.getClass().getSimpleName());
        }
    }
    public void executeCommand(String command, OnCommandResultListener callback) { executeCommand(command, callback, false); }
    public void executeCommand(String command, OnCommandResultListener callback, boolean printOutput) {
        AccessExecutor.SERIAL.execute(() -> {
            CommandResult result = run(command);
            callback.onCommandResult(0, result.code,
                    result.ok() ? Arrays.asList(result.output.split("\\n")) : Collections.emptyList(),
                    result.ok() ? Collections.emptyList() : Collections.singletonList(result.output));
        });
    }
    public interface OnCommandResultListener {
        void onCommandResult(int commandCode, int exitCode, List<String> stdout, List<String> stderr);
    }
}
