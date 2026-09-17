package com.akylas.enforcedoze;

import android.content.*;
import android.preference.PreferenceManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/** Exported Tasker entry points require explicit opt-in and a locally generated token. */
public final class Automation {
    private Automation() {}

    public static String token(Context c) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(c);
        String token = p.getString("automationToken", null);
        if (token == null) {
            token = UUID.randomUUID().toString() + UUID.randomUUID();
            p.edit().putString("automationToken", token).commit();
        }
        return token;
    }

    public static boolean allowed(Context c, Intent i) {
        if (i == null
                || !PreferenceManager.getDefaultSharedPreferences(c)
                        .getBoolean("allowAutomation", false)) return false;
        String actual;
        try {
            actual = i.getStringExtra("automationToken");
        } catch (RuntimeException malformed) {
            return false;
        }
        String expected =
                PreferenceManager.getDefaultSharedPreferences(c).getString("automationToken", null);
        return actual != null
                && expected != null
                && MessageDigest.isEqual(
                        actual.getBytes(StandardCharsets.UTF_8),
                        expected.getBytes(StandardCharsets.UTF_8));
    }

    public static void whitelist(BroadcastReceiver receiver, Context c, Intent i, boolean add) {
        if (!allowed(c, i)) return;
        String pkg = i.getStringExtra("packageName");
        if (!CommandResult.validPackage(pkg)) return;
        BroadcastReceiver.PendingResult pending = receiver.goAsync();
        AccessExecutor.SERIAL.execute(
                () -> {
                    try {
                        CommandResult r =
                                new AccessExecutor(c)
                                        .run(
                                                "dumpsys deviceidle whitelist "
                                                        + (add ? "+" : "-")
                                                        + CommandResult.quote(pkg));
                        new EvidenceStore(c)
                                .record(
                                        "Automation exemption",
                                        pkg
                                                + ": "
                                                + (r.ok()
                                                        ? "command accepted; check exemption list"
                                                        : r.output));
                    } finally {
                        pending.finish();
                    }
                });
    }
}
