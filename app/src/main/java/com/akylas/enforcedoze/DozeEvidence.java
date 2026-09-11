package com.akylas.enforcedoze;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Small, local evidence log. Appends run on the existing command worker. */
final class DozeEvidence {
    static final int MAX_EVENTS = 120;
    private static final String KEY = "observations_v1";
    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("doze_evidence", Context.MODE_PRIVATE);
    }
    static boolean enabled(Context context) {
        return !PreferenceManager.getDefaultSharedPreferences(context).getBoolean("disableStats", false);
    }
    static synchronized void append(Context context, DozeObservation observation) {
        if (!enabled(context)) return;
        List<DozeObservation> records = read(context);
        records.add(observation);
        JSONArray data = new JSONArray();
        try {
            for (int i = Math.max(0, records.size() - MAX_EVENTS); i < records.size(); i++) data.put(encode(records.get(i)));
            if (!preferences(context).edit().putString(KEY, data.toString()).commit())
                Utils.logToLogcat("DozeEvidence", "Could not save the latest observation");
        } catch (JSONException e) { Utils.logToLogcat("DozeEvidence", "Could not encode observation: " + e); }
    }
    static List<DozeObservation> read(Context context) {
        List<DozeObservation> records = new ArrayList<>();
        try {
            String saved = preferences(context).getString(KEY, "[]");
            if (saved == null || saved.length() > 300000) return records;
            JSONArray data = new JSONArray(saved);
            for (int i = Math.max(0, data.length() - MAX_EVENTS); i < data.length(); i++) {
                try { records.add(decode(data.getJSONObject(i))); }
                catch (JSONException ignored) { /* Missing fields must never become a confirmed sample. */ }
            }
        } catch (JSONException | ClassCastException ignored) { }
        return records;
    }
    static synchronized void clear(Context context) { preferences(context).edit().remove(KEY).apply(); }

    private static JSONObject encode(DozeObservation o) throws JSONException {
        return new JSONObject().put("schema", 1).put("time", o.time).put("elapsed", o.elapsed)
                .put("readMillis", o.readMillis).put("session", o.session).put("event", o.event)
                .put("mode", o.mode).put("device", o.device).put("version", o.version)
                .put("exit", o.exitCode == null ? JSONObject.NULL : o.exitCode)
                .put("state", o.state).put("raw", o.raw).put("interactiveBefore", o.interactiveBefore)
                .put("interactiveAfter", o.interactiveAfter).put("idleBefore", o.idleBefore)
                .put("idleAfter", o.idleAfter).put("uninterrupted", o.uninterrupted);
    }
    private static DozeObservation decode(JSONObject o) throws JSONException {
        if (o.getInt("schema") != 1 || !o.has("exit")) throw new JSONException("Unknown observation format");
        return new DozeObservation(o.getLong("time"), o.getLong("elapsed"), o.getLong("readMillis"),
                o.getString("session"), o.getString("event"), o.getString("mode"), o.getString("device"),
                o.getString("version"), o.isNull("exit") ? null : o.getInt("exit"), o.getString("state"),
                o.getString("raw"), o.getBoolean("interactiveBefore"), o.getBoolean("interactiveAfter"),
                o.getBoolean("idleBefore"), o.getBoolean("idleAfter"), o.getBoolean("uninterrupted"));
    }
    static String timestamp(DozeObservation o) {
        return new SimpleDateFormat("d MMM yyyy HH:mm:ss.SSS z", Locale.getDefault()).format(new Date(o.time));
    }
    static int eventLabel(String event) {
        switch (event) {
            case "ENTRY": return R.string.evidence_entry;
            case "IDLE_CHANGED": return R.string.evidence_idle_changed;
            case "IDLE_BROADCAST": return R.string.evidence_idle_broadcast;
            case "RESTORED": return R.string.evidence_restored;
            case "RESTORE_PENDING": return R.string.evidence_restore_pending;
            case "ENTRY_ABORTED": return R.string.evidence_aborted;
            case "LEGACY_TUNABLES": return R.string.evidence_legacy;
            default: return R.string.evidence_observation;
        }
    }
    static String details(Context context, DozeObservation o) {
        StringBuilder text = new StringBuilder(timestamp(o)).append('\n').append(context.getString(eventLabel(o.event)))
                .append("\n\n").append(context.getString(R.string.evidence_interactive, o.interactiveBefore, o.interactiveAfter))
                .append('\n').append(context.getString(R.string.evidence_android_idle, o.idleBefore, o.idleAfter));
        if (!o.uninterrupted) text.append('\n').append(context.getString(R.string.evidence_interrupted));
        if (o.exitCode != null) {
            text.append("\n\n$ dumpsys deviceidle\nExit: ").append(o.exitCode).append('\n')
                    .append(o.raw.isEmpty() ? context.getString(R.string.evidence_no_fields) : o.raw);
        } else text.append("\n\n").append(context.getString(R.string.evidence_api_only));
        text.append("\n\n").append(context.getString(R.string.evidence_read_time, o.readMillis))
                .append("\nMode: ").append(o.mode).append(" · ").append(o.version)
                .append('\n').append(o.device).append("\nSession: ").append(o.session)
                .append("\nElapsed realtime: ").append(o.elapsed).append(" ms");
        return text.toString();
    }
    static String report(Context context) {
        StringBuilder text = new StringBuilder(context.getString(R.string.evidence_title)).append('\n')
                .append(context.getString(R.string.evidence_explanation)).append('\n');
        List<DozeObservation> records = read(context);
        if (records.isEmpty()) return text.append('\n').append(context.getString(R.string.evidence_empty)).toString();
        for (int i = records.size() - 1; i >= 0; i--) {
            DozeObservation o = records.get(i);
            text.append("\n\n").append(context.getString(o.confirmedScreenOffIdle()
                    ? R.string.evidence_confirmed : R.string.evidence_observation))
                    .append('\n').append(details(context, o));
        }
        return text.toString();
    }
}
