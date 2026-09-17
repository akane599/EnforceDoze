package com.akylas.enforcedoze;

import android.content.Context;
import android.preference.PreferenceManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Every device transaction shares one queue, including recovery and UI actions. Never runs on UI. */
public final class AccessExecutor {
    public static final ExecutorService SERIAL = Executors.newSingleThreadExecutor(r -> new Thread(r, "device-operations"));
    static final ExecutorService RPC = Executors.newSingleThreadExecutor(r -> new Thread(r, "shizuku-rpc"));
    private final Context context;
    public AccessExecutor(Context context) { this.context = context.getApplicationContext(); }
    public String mode() {
        return PreferenceManager.getDefaultSharedPreferences(context).getString("executionMode", "shizuku");
    }
    public CommandResult run(String command) { return run(mode(), command); }
    public CommandResult run(String mode, String command) {
        if (mode.equals("shizuku")) return ShizukuHandler.getInstance(context).run(command);
        if (command.startsWith("@")) return new CommandResult(-1, "This control requires the Shizuku user service.");
        // Root and previously ADB-granted app-shell access are explicit choices, never silent fallbacks.
        return ProcessRunner.run(new String[]{mode.equals("root") ? "su" : "sh", "-c", command}, 10000);
    }
}
