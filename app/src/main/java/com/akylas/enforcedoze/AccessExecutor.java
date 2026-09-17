package com.akylas.enforcedoze;

import android.content.Context;
import android.preference.PreferenceManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Every device transaction shares one queue, including recovery and UI actions. Never runs on UI.
 */
public final class AccessExecutor {
    public static final ExecutorService SERIAL =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "device-operations"));
    public static final ExecutorService CATALOG =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "app-catalog"));
    static final ExecutorService RPC =
            new java.util.concurrent.ThreadPoolExecutor(
                    1,
                    1,
                    0,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(1),
                    r -> new Thread(r, "shizuku-rpc"));
    private final Context context;

    public AccessExecutor(Context context) {
        this.context = context.getApplicationContext();
    }

    public String mode() {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString("executionMode", "shizuku");
    }

    public CommandResult run(String command) {
        return run(mode(), command);
    }

    public CommandResult run(String mode, String command) {
        if (mode.equals("shizuku")) return ShizukuHandler.getInstance(context).run(command);
        if (command.startsWith("@")) {
            if (!mode.equals("root"))
                return new CommandResult(-1, "This control requires Shizuku or root.");
            String helper =
                    "CLASSPATH="
                            + CommandResult.quote(context.getApplicationInfo().sourceDir)
                            + " app_process /system/bin com.akylas.enforcedoze.PrivilegedCommand "
                            + CommandResult.quote(command);
            CommandResult transport = ProcessRunner.run(new String[] {"su", "-c", helper}, 10000);
            if (!transport.ok()) return transport;
            try {
                org.json.JSONObject result = new org.json.JSONObject(transport.output);
                return new CommandResult(result.getInt("code"), result.getString("output"));
            } catch (Exception e) {
                return new CommandResult(-1, "Root helper did not return a valid result.");
            }
        }
        // Root and previously ADB-granted app-shell access are explicit choices, never silent
        // fallbacks.
        return ProcessRunner.run(
                new String[] {mode.equals("root") ? "su" : "sh", "-c", command}, 10000);
    }
}
