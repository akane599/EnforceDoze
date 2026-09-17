package com.akylas.enforcedoze;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class RecoveryStore implements RestorationJournal.Store {
    private final SharedPreferences prefs;
    public RecoveryStore(Context context) { this(context,"recovery"); }
    public RecoveryStore(Context context,String name) { prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE); }
    @Override public List<RestorationJournal.Entry> load() {
        List<RestorationJournal.Entry> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("journal", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject j = array.getJSONObject(i);
                entries.add(new RestorationJournal.Entry(j.getString("key"), j.getString("mode"),
                        j.getString("query"), j.getString("kind"), j.getString("original"),
                        j.getString("target"), j.getString("apply"), j.getString("undo")));
            }
        } catch (Exception e) {
            // Corruption is a blocking error, never an empty journal and permission for new changes.
            throw new IllegalStateException("Recovery record is unreadable. Saved data has been retained.", e);
        }
        return entries;
    }
    @Override public boolean save(List<RestorationJournal.Entry> entries) {
        try {
            JSONArray array = new JSONArray();
            for (RestorationJournal.Entry e : entries) array.put(new JSONObject()
                    .put("key", e.key).put("mode", e.mode).put("query", e.query).put("kind", e.kind)
                    .put("original", e.original).put("target", e.target).put("apply", e.apply).put("undo", e.undo));
            return prefs.edit().putString("journal", array.toString()).commit();
        } catch (Exception e) { return false; }
    }
    public boolean pending() {
        try { return !load().isEmpty(); } catch (IllegalStateException e) { return true; }
    }
    public String summary() {
        try {
            StringBuilder text = new StringBuilder();
            for (RestorationJournal.Entry e : load()) text.append(e.key).append(" → ").append(e.original)
                    .append(" (").append(e.mode).append(")\n");
            return text.toString().trim();
        } catch (IllegalStateException e) { return e.getMessage(); }
    }
}
