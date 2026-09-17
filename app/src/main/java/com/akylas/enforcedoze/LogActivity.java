package com.akylas.enforcedoze;

import android.content.*;
import android.os.*;
import android.widget.*;

public class LogActivity extends BaseActivity {
    private TextView evidence;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        screen("Diagnostics", true);
        LinearLayout controls =
                card(
                        "Saved on this device",
                        "Up to 200 observations are kept locally. They record individual checks,"
                            + " not continuous monitoring. Sharing is always your choice.");
        button(controls, "Refresh", this::refresh);
        button(controls, "Copy diagnostics", () -> copy("EnforceDoze diagnostics", report()));
        button(
                controls,
                "Share diagnostics",
                () ->
                        safeStart(
                                Intent.createChooser(
                                        new Intent(Intent.ACTION_SEND)
                                                .setType("text/plain")
                                                .putExtra(Intent.EXTRA_TEXT, report()),
                                        "Share diagnostics")));
        button(
                controls,
                "Clear observations",
                () ->
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                .setTitle("Clear saved observations?")
                                .setMessage(
                                        "Recovery records and monitoring history are kept"
                                            + " separately.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton(
                                        "Clear",
                                        (d, w) -> {
                                            new EvidenceStore(this).clear();
                                            refresh();
                                        })
                                .show());
        evidence = text(card("Observations", null), "Loading…", 15, false);
        evidence.setTextIsSelectable(true);
        evidence.setId(R.id.evidence_text);
        refresh();
    }

    private String report() {
        return "EnforceDoze "
                + BuildConfig.VERSION_NAME
                + " ("
                + BuildConfig.VERSION_CODE
                + ")\nAndroid "
                + Build.VERSION.RELEASE
                + " / API "
                + Build.VERSION.SDK_INT
                + " • "
                + Build.MANUFACTURER
                + " "
                + Build.MODEL
                + "\nAccess mode: "
                + new AccessExecutor(this).mode()
                + "\nStatus: "
                + getSharedPreferences("runtime", MODE_PRIVATE)
                        .getString("status", "Monitoring off")
                + "\nTemporary changes: "
                + (new RecoveryStore(this).pending() ? new RecoveryStore(this).summary() : "none")
                + "\nPersistent tunable originals: "
                + new RecoveryStore(this, "tunable_recovery").summary()
                + "\n\n"
                + new EvidenceStore(this).text();
    }

    private void refresh() {
        if (evidence != null) evidence.setText(report());
    }
}
