package com.akylas.enforcedoze;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

/** Monitoring intervals are distinct from point-in-time proof of Deep Doze. */
public final class SessionStore {
    private final Context context;
    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences("sessions", Context.MODE_PRIVATE);
    }

    public void begin() {
        if (android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("disableStats", false)) return;
        try {
            JSONObject session =
                    new JSONObject()
                            .put("start", System.currentTimeMillis())
                            .put("elapsed", SystemClock.elapsedRealtime())
                            .put("battery", Utils.getBatteryLevel(context))
                            .put("charging", Utils.isConnectedToCharger(context))
                            .put("observed", false);
            prefs.edit().putString("active", session.toString()).commit();
        } catch (Exception e) {
            new EvidenceStore(context).record("History error", e.toString());
        }
    }

    public void observe(boolean deep) {
        update("observed", deep);
    }

    public void charging() {
        update("charging", true);
    }

    private void update(String key, boolean value) {
        try {
            String data = prefs.getString("active", null);
            if (data == null) return;
            JSONObject session = new JSONObject(data);
            if (value && !session.optBoolean(key))
                prefs.edit().putString("active", session.put(key, true).toString()).apply();
        } catch (Exception e) {
            new EvidenceStore(context).record("History error", e.toString());
        }
    }

    public void finish(String reason, boolean interrupted) {
        try {
            String data = prefs.getString("active", null);
            if (data == null) return;
            JSONObject session = new JSONObject(data);
            session.put("end", System.currentTimeMillis())
                    .put("reason", reason)
                    .put("interrupted", interrupted);
            long duration = SystemClock.elapsedRealtime() - session.getLong("elapsed");
            session.put("duration", interrupted || duration < 0 ? -1 : duration);
            int start = session.getInt("battery"), end = Utils.getBatteryLevel(context);
            boolean charging =
                    session.optBoolean("charging") || Utils.isConnectedToCharger(context);
            session.put("drop", validDrop(start, end, charging, interrupted) ? start - end : -1);
            JSONArray old = new JSONArray(prefs.getString("history", "[]")), next = new JSONArray();
            for (int i = Math.max(0, old.length() - 99); i < old.length(); i++)
                next.put(old.get(i));
            next.put(session);
            prefs.edit().remove("active").putString("history", next.toString()).commit();
        } catch (Exception e) {
            new EvidenceStore(context).record("History error", e.toString());
        }
    }

    public static boolean validDrop(int start, int end, boolean charging, boolean interrupted) {
        return !charging && !interrupted && start >= 0 && start <= 100 && end >= 0 && end <= start;
    }

    public String text() {
        StringBuilder text =
                new StringBuilder(
                        "Monitoring duration is not time continuously spent in Deep Doze. Battery"
                            + " changes are coarse percentage points, not savings caused by"
                            + " EnforceDoze.\n\n");
        try {
            JSONArray list = new JSONArray(prefs.getString("history", "[]"));
            if (prefs.contains("active")) text.append("A monitoring interval is in progress.\n\n");
            if (list.length() == 0) text.append("No completed monitoring intervals yet.\n");
            for (int i = list.length() - 1; i >= 0; i--) {
                JSONObject s = list.getJSONObject(i);
                text.append(DateFormat.getDateTimeInstance().format(new Date(s.getLong("start"))))
                        .append('\n');
                long d = s.optLong("duration", -1);
                text.append(
                                d < 0
                                        ? "Interrupted • duration unknown"
                                        : "Monitoring: "
                                                + d / 60000
                                                + " min "
                                                + (d / 1000) % 60
                                                + " sec")
                        .append("\nDeep Doze while screen off: ")
                        .append(
                                s.optBoolean("observed")
                                        ? "observed at least once"
                                        : "not observed")
                        .append("\nBattery change: ")
                        .append(
                                s.optInt("drop", -1) < 0
                                        ? "unavailable (charging, interrupted or missing reading)"
                                        : s.getInt("drop") + " percentage points")
                        .append("\nEnded: ")
                        .append(s.optString("reason"))
                        .append("\n\n");
            }
        } catch (Exception e) {
            text.append("Some saved history could not be read.");
        }
        return text.toString();
    }

    public void clear() {
        prefs.edit().remove("history").remove("active").apply();
    }
}
