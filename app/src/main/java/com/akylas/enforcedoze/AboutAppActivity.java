package com.akylas.enforcedoze;

import android.os.Bundle;
import android.widget.LinearLayout;

public class AboutAppActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        screen("About EnforceDoze", true);
        card(
                "EnforceDoze",
                BuildConfig.VERSION_NAME
                        + " • build "
                        + BuildConfig.VERSION_CODE
                        + "\nA fork of ForceDoze by theblixguy. Free, open source, no ads.");
        LinearLayout project =
                card(
                        "Project & support",
                        "Local diagnostics are shared only when you choose to copy or share them.");
        button(
                project,
                "Source code",
                () -> Utils.openUrl(this, "https://github.com/Akylas/EnforceDoze"));
        button(
                project,
                "Support the project",
                () -> Utils.openUrl(this, "https://github.com/sponsors/farfromrefug"));
        button(
                project,
                "Translation contributors",
                () -> Utils.openUrl(this, "https://hosted.weblate.org/engage/enforcedoze/"));
        button(
                project,
                "Privacy",
                () ->
                        message(
                                "Your data stays here",
                                "EnforceDoze stores options, recovery records and bounded"
                                    + " diagnostic observations locally. It sends no analytics or"
                                    + " automatic diagnostic uploads. Copy and Share are explicit"
                                    + " actions. Source, support and documentation links open your"
                                    + " browser. Clear observations and history separately;"
                                    + " restoring device changes does not require sharing data."));
        button(
                project,
                "Licenses",
                () -> {
                    StringBuilder licenses =
                            new StringBuilder(
                                    "EnforceDoze: GNU GPL v3\n"
                                            + "AndroidX, Material Components, Kotlin: Apache 2.0\n"
                                            + "Shizuku API: MIT (RikkaW)\n\n");
                    try {
                        for (String file :
                                new String[] {
                                    "Shizuku-API.txt", "Apache-2.0.txt", "EnforceDoze-GPL-3.0.txt"
                                }) {
                            java.io.InputStream stream = getAssets().open("licenses/" + file);
                            try (java.io.BufferedReader reader =
                                    new java.io.BufferedReader(
                                            new java.io.InputStreamReader(
                                                    stream,
                                                    java.nio.charset.StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null)
                                    licenses.append(line).append('\n');
                            }
                            licenses.append("\n\n");
                        }
                    } catch (java.io.IOException e) {
                        licenses.append("License file unavailable: ").append(e.getMessage());
                    }
                    message("Open-source licenses", licenses.toString());
                });
    }
}
