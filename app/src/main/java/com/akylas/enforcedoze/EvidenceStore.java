package com.akylas.enforcedoze;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

/** Local bounded observations; no background polling and no automatic sharing. */
public final class EvidenceStore implements RestorationJournal.Evidence {
    private static final Object LOCK = new Object();
    private final Context context;
    private final SharedPreferences prefs;

    public EvidenceStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences("evidence", Context.MODE_PRIVATE);
    }

    @Override
    public void record(String event, String detail) {
        synchronized (LOCK) {
            try {
                JSONArray previous = new JSONArray(prefs.getString("events", "[]"));
                JSONArray next = new JSONArray();
                for (int i = Math.max(0, previous.length() - 199); i < previous.length(); i++)
                    next.put(previous.get(i));
                PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                next.put(
                        new JSONObject()
                                .put("time", System.currentTimeMillis())
                                .put("elapsed", SystemClock.elapsedRealtime())
                                .put("screenOn", power.isInteractive())
                                .put("idle", power.isDeviceIdleMode())
                                .put("event", event)
                                .put(
                                        "detail",
                                        detail.substring(0, Math.min(768, detail.length()))));
                prefs.edit()
                        .putString("events", next.toString())
                        .putString(
                                "latest",
                                DateFormat.getDateTimeInstance().format(new Date())
                                        + "\n"
                                        + event
                                        + "\n"
                                        + detail.substring(0, Math.min(256, detail.length())))
                        .apply();
            } catch (Exception e) {
                android.util.Log.e("EnforceDoze", "Could not save observation", e);
            }
        }
    }

    public String text() {
        synchronized (LOCK) {
            StringBuilder text =
                    new StringBuilder(
                            "Saved observations, not continuous monitoring.\n"
                                + "Sensor access restrictions do not prove physical sensors are"
                                + " powered off.\n\n");
            try {
                JSONArray events = new JSONArray(prefs.getString("events", "[]"));
                if (events.length() == 0)
                    text.append(
                            "No observations yet. Start monitoring, then turn the screen off and"
                                + " on.\n");
                for (int i = events.length() - 1; i >= 0; i--) {
                    JSONObject e = events.getJSONObject(i);
                    text.append(
                                    DateFormat.getDateTimeInstance()
                                            .format(new Date(e.getLong("time"))))
                            .append(" • ")
                            .append(e.getString("event"))
                            .append('\n')
                            .append("Screen ")
                            .append(e.getBoolean("screenOn") ? "on" : "off")
                            .append(" • Android deep-idle API: ")
                            .append(e.getBoolean("idle"))
                            .append(" • uptime ")
                            .append(e.getLong("elapsed") / 1000)
                            .append("s\n")
                            .append(e.getString("detail"))
                            .append("\n\n");
                }
            } catch (Exception e) {
                text.append("Saved observations could not be read: ").append(e.getMessage());
            }
            return text.toString();
        }
    }

    public void clear() {
        synchronized (LOCK) {
            prefs.edit().remove("events").remove("latest").apply();
        }
    }
}
