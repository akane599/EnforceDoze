package com.akylas.enforcedoze;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** All app commands share one queue, so restoration cannot overtake an earlier mutation. */
public final class CommandExecutor {
    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    public interface Callback { void complete(CommandResult result); }
    public static void submit(Runnable task) { QUEUE.execute(task); }
    public static void execute(Context context, String command, Callback callback) {
        Context app = context.getApplicationContext();
        String mode = mode(app);
        submit(() -> {
            CommandResult result = run(app, mode, command);
            if (callback != null) MAIN.post(() -> callback.complete(result));
        });
    }
    public static String mode(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString("executionMode", "shizuku");
    }
    public static CommandResult run(Context context, String mode, String command) {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Blocking command on UI thread");
        if ("shizuku".equals(mode)) return ShizukuHandler.getInstance(context).executeBlocking(command);
        boolean root = "root".equals(mode) && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("isSuAvailable", false);
        if ("root".equals(mode) && !root) return new CommandResult(-1, "Reconnect root access before running commands");
        if (!root && !"adb".equals(mode)) return new CommandResult(-1, "Unknown execution mode: " + mode);
        if (command.startsWith(PrivilegedOperations.PREFIX)) {
            if (!root) return new CommandResult(-1, "This operation needs Shizuku or root");
            command = "CLASSPATH=" + CommandPolicy.quote(context.getApplicationInfo().sourceDir)
                    + " app_process /system/bin com.akylas.enforcedoze.PrivilegedOperations " + CommandPolicy.quote(command);
        }
        return ProcessRunner.run(8000, root ? "su" : "sh", "-c", command);
    }
}
