package com.akylas.enforcedoze;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Only touched on CommandExecutor's queue. Persist undo BEFORE a system mutation. */
final class RecoveryJournal {
    /** How many times a device may reject an undo command before it is reported and dropped. */
    private static final int MAX_ATTEMPTS = 5;
    private final Context context;
    private final SharedPreferences prefs;
    interface Runner { CommandResult run(String mode, String command); }
    private final Runner runner;
    RecoveryJournal(Context context) { this(context, (mode, command) -> CommandExecutor.run(context, mode, command)); }
    RecoveryJournal(Context context, Runner runner) {
        this.context = context.getApplicationContext();
        this.runner = runner;
        prefs = context.getSharedPreferences("recovery", Context.MODE_PRIVATE);
    }
    boolean hasPending() { return !prefs.getString("undo", "[]").equals("[]"); }
    boolean apply(String mode, String command, String undo) { return apply(mode, command, undo, false); }
    boolean applyCore(String mode, String command, String undo) { return apply(mode, command, undo, true); }
    private boolean apply(String mode, String command, String undo, boolean core) {
        try {
            JSONArray pending = new JSONArray(prefs.getString("undo", "[]"));
            pending.put(new JSONObject().put("mode", mode).put("command", undo).put("core", core));
            if (!prefs.edit().putString("undo", pending.toString()).commit()) return false;
            CommandResult result = runner.run(mode, command);
            if (!result.success()) {
                Utils.logToLogcat("EnforceDoze", result.output);
                android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().putString("lastError", command + "\nExit: " + result.exitCode + "\n" + (result.output.length() > 4096 ? result.output.substring(0, 4096) : result.output)).apply();
            }
            // Retain undo even after failure: a command can mutate then time out.
            return result.success();
        } catch (Exception e) {
            Utils.logToLogcat("EnforceDoze", "Unable to save restoration: " + e);
            return false;
        }
    }
    boolean restore() { return restore(false); }
    boolean restoreEnhancements() { return restore(true); }
    private boolean restore(boolean enhancementsOnly) {
        try {
            JSONArray pending = new JSONArray(prefs.getString("undo", "[]"));
            boolean success = true;
            StringBuilder abandoned = new StringBuilder();
            for (int i = pending.length() - 1; i >= 0; i--) {
                JSONObject item = pending.getJSONObject(i);
                if (enhancementsOnly && item.optBoolean("core", false)) continue;
                CommandResult result = runner.run(item.getString("mode"), item.getString("command"));
                if (result.success()) {
                    pending.remove(i);
                    // A second process death must not replay already completed restorations.
                    if (!prefs.edit().putString("undo", pending.toString()).commit()) return false;
                    continue;
                }
                success = false;
                // Only a command the system actually ran and rejected counts against the budget.
                // A lost Shizuku connection or a timeout must not burn a device's chance to recover.
                if (result.exitCode <= 0) continue;
                int failures = item.optInt("failures", 0) + 1;
                if (failures < MAX_ATTEMPTS) {
                    item.put("failures", failures);
                } else {
                    // This device will never accept the command. Holding it forever would leave the
                    // app permanently in recovery with no way out, so report it and let go.
                    abandoned.append(item.getString("command")).append('\n').append(result.output).append("\n\n");
                    pending.remove(i);
                }
                if (!prefs.edit().putString("undo", pending.toString()).commit()) return false;
            }
            if (abandoned.length() > 0) report(abandoned.toString().trim());
            return success;
        } catch (Exception e) {
            Utils.logToLogcat("EnforceDoze", "Restoration failed: " + e);
            return false;
        }
    }

    private void report(String abandoned) {
        Utils.logToLogcat("EnforceDoze", "Giving up on restoration commands:\n" + abandoned);
        String message = context.getString(R.string.error_restoration_abandoned, abandoned);
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("lastError", message.length() > 4096 ? message.substring(0, 4096) : message).apply();
    }
}
