package com.akylas.enforcedoze;

import android.os.Bundle;
import android.preference.PreferenceManager;

import java.util.*;

public class DozeStatsActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        screen("Older records", true);
        card(
                "Unverified legacy data",
                "Older versions recorded command attempts without proving Deep Doze. Charging and"
                    + " elapsed-time values may be inaccurate. These records are retained as raw"
                    + " data and excluded from the new history.");
        ArrayList<String> entries =
                new ArrayList<>(
                        PreferenceManager.getDefaultSharedPreferences(this)
                                .getStringSet("dozeUsageDataAdvanced", Collections.emptySet()));
        Collections.sort(entries, Collections.reverseOrder());
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(200, entries.size()); i++)
            out.append(entries.get(i)).append('\n');
        text(body, entries.isEmpty() ? "No older records." : out.toString(), 15, false)
                .setTextIsSelectable(true);
        button(
                body,
                "Clear older records",
                () -> {
                    PreferenceManager.getDefaultSharedPreferences(this)
                            .edit()
                            .remove("dozeUsageDataAdvanced")
                            .apply();
                    recreate();
                });
    }
}
