package com.akylas.enforcedoze;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Only touched on CommandExecutor's queue. Persist undo BEFORE a system mutation. */
final class RecoveryJournal {
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
            if (!result.success()) recordFailure(command, result);
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
            for (int i = pending.length() - 1; i >= 0; i--) {
                try {
                    JSONObject item = pending.getJSONObject(i);
                    if (enhancementsOnly && item.optBoolean("core", false)) continue;
                    String command = item.getString("command");
                    CommandResult result = runner.run(item.getString("mode"), command);
                    if (result.success()) {
                        pending.remove(i);
                        // A second process death must not replay already completed restorations.
                        if (!prefs.edit().putString("undo", pending.toString()).commit()) return false;
                    } else {
                        recordFailure("Restoration: " + command, result);
                        success = false;
                    }
                } catch (Exception e) {
                    // A denied or broken enhancement must not prevent unforcing Doze,
                    // restoring connectivity, or attempting other independent cleanup.
                    recordFailure("Restoration entry " + i, new CommandResult(-1, e.toString()));
                    success = false;
                }
            }
            return success;
        } catch (Exception e) {
            Utils.logToLogcat("EnforceDoze", "Restoration failed: " + e);
            return false;
        }
    }
    private void recordFailure(String command, CommandResult result) {
        String output = result.output.length() > 4096 ? result.output.substring(0, 4096) : result.output;
        Utils.logToLogcat("EnforceDoze", command + "\n" + output);
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("lastError", command + "\nExit: " + result.exitCode + "\n" + output).apply();
    }
}
