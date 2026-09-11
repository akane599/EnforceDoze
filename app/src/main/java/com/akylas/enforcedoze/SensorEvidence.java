package com.akylas.enforcedoze;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small transition-only log; never polls sensors or registers a sensor listener. */
final class SensorEvidence {
    static final int MAX_EVENTS = 24;
    static SharedPreferences preferences(Context context) { return context.getSharedPreferences("sensor_evidence", Context.MODE_PRIVATE); }
    static synchronized void record(Context context, String mode, String command, CommandResult result) {
        if (!(command.startsWith(PrivilegedOperations.PREFIX + "motion ") || command.startsWith(PrivilegedOperations.PREFIX + "sensors "))
                || command.endsWith(" get") && result.success() || !DozeEvidence.enabled(context)) return;
        try {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            JSONObject observation = new JSONObject().put("time", System.currentTimeMillis())
                    .put("elapsed", SystemClock.elapsedRealtime()).put("mode", mode)
                    .put("device", Build.MANUFACTURER + " " + Build.MODEL + " · API " + Build.VERSION.SDK_INT)
                    .put("version", BuildConfig.VERSION_NAME).put("command", command)
                    .put("interactive", power.isInteractive()).put("exit", result.exitCode)
                    .put("output", result.output.substring(0, Math.min(2048, result.output.length())));
            SharedPreferences prefs = preferences(context);
            JSONArray existing;
            try { existing = new JSONArray(prefs.getString("events", "[]")); }
            catch (Exception invalid) { existing = new JSONArray(); }
            JSONArray bounded = new JSONArray();
            for (int i = Math.max(0, existing.length() - MAX_EVENTS + 1); i < existing.length(); i++) bounded.put(existing.get(i));
            bounded.put(observation);
            prefs.edit().putString("events", bounded.toString()).commit();
        } catch (Exception ignored) { /* Logging must never prevent restoration. */ }
    }
    static synchronized void clear(Context context) { preferences(context).edit().remove("events").apply(); }
    static synchronized String report(Context context) {
        StringBuilder text = new StringBuilder(context.getString(R.string.sensor_evidence_explanation));
        if (!DozeEvidence.enabled(context)) text.append("\n\n").append(context.getString(R.string.evidence_disabled));
        try {
            JSONArray events = new JSONArray(preferences(context).getString("events", "[]"));
            if (events.length() == 0) return text.append("\n\n").append(context.getString(R.string.sensor_evidence_empty)).toString();
            SimpleDateFormat time = new SimpleDateFormat("dd MMM yyyy HH:mm:ss.SSS z", Locale.getDefault());
            for (int i = events.length() - 1; i >= 0; i--) {
                JSONObject event = events.getJSONObject(i);
                text.append("\n\n").append(time.format(new Date(event.getLong("time"))))
                        .append("\n").append(event.getString("device")).append(" · ").append(event.getString("version"))
                        .append("\nMode: ").append(event.getString("mode"))
                        .append("\nScreen interactive when command returned: ").append(event.getBoolean("interactive"))
                        .append("\nElapsed realtime: ").append(event.getLong("elapsed")).append(" ms")
                        .append("\n$ ").append(event.getString("command"))
                        .append("\nExit: ").append(event.getInt("exit")).append("\n").append(event.getString("output"));
            }
        } catch (Exception invalid) { text.append("\n\nSaved sensor evidence could not be read."); }
        return text.toString();
    }
}
