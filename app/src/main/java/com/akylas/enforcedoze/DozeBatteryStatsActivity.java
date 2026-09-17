package com.akylas.enforcedoze;

import android.os.Bundle;
import android.widget.TextView;

public class DozeBatteryStatsActivity extends BaseActivity {
    private TextView history;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        screen("Monitoring history", true);
        button(
                body,
                "Clear history",
                () ->
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                .setTitle("Clear history?")
                                .setMessage(
                                        "This clears recorded intervals. Recovery information is"
                                                + " preserved.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton(
                                        "Clear",
                                        (d, w) ->
                                                AccessExecutor.SERIAL.execute(
                                                        () -> {
                                                            new SessionStore(this).clear();
                                                            runOnUiThread(this::refresh);
                                                        }))
                                .show());
        button(body, "Older records (unverified)", () -> open(DozeStatsActivity.class));
        history = text(card("Screen-off intervals", null), "Loading…", 16, false);
        history.setTextIsSelectable(true);
        refresh();
    }

    private void refresh() {
        history.setText(new SessionStore(this).text());
    }
}
