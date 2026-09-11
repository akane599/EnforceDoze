package com.akylas.enforcedoze;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Saved verification remains available after waking; opening this screen runs no commands. */
public final class SensorEvidenceActivity extends UiActivity {
    private TextView report;
    private SharedPreferences saved;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> refresh();
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        saved = SensorEvidence.preferences(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle(R.string.sensor_evidence_title); root.addView(toolbar);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density); body.setPadding(padding, padding, padding, padding);
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        MaterialButton copy = new MaterialButton(this); copy.setText(R.string.evidence_copy); body.addView(copy);
        copy.setOnClickListener(v -> ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText(getString(R.string.sensor_evidence_title), SensorEvidence.report(this))));
        MaterialButton clear = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
        clear.setText(R.string.evidence_clear); body.addView(clear);
        clear.setOnClickListener(v -> new MaterialAlertDialogBuilder(this).setMessage(R.string.sensor_evidence_clear_question)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.evidence_clear,
                        (dialog, which) -> SensorEvidence.clear(this)).show());
        report = new TextView(this); report.setId(R.id.sensorEvidenceReport); report.setTextIsSelectable(true); body.addView(report);
        setContentView(root); setSupportActionBar(toolbar); getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
    @Override protected void onStart() { super.onStart(); saved.registerOnSharedPreferenceChangeListener(listener); refresh(); }
    @Override protected void onStop() { saved.unregisterOnSharedPreferenceChangeListener(listener); super.onStop(); }
    private void refresh() { if (!isDestroyed()) report.setText(SensorEvidence.report(this)); }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
