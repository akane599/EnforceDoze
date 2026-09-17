package com.akylas.enforcedoze;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.*;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.*;

/**
 * Common validated editor for package lists. Whitelist commands run off the UI and are read back.
 */
public abstract class PackageListActivity extends BaseActivity {
    protected abstract String mode();

    private TextView status;
    private ListView list;
    private List<String> packages = new ArrayList<>();
    private Set<String> userExemptions = new HashSet<>();

    private String key() {
        return mode().equals("apps") ? "dozeAppBlockList" : "notificationBlockList";
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        screen(
                mode().equals("exempt")
                        ? "Doze exemptions"
                        : mode().equals("apps") ? "Suspend selected apps" : "Filter notifications",
                true);
        root.removeViewAt(1);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), 0, dp(16), 0);
        status = text(content, "Loading…", 15, false);
        if (mode().equals("notifications"))
            text(
                    content,
                    "Requires notification access in Access & permissions. New dismissible"
                        + " notifications are removed; they cannot be restored.",
                    14,
                    false);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        content.addView(actions);
        button(
                actions,
                "Add app",
                () -> startActivityForResult(new Intent(this, PackageChooserActivity.class), 10));
        button(actions, "Enter package name", this::manual);
        button(actions, "Refresh", this::load);
        list = new ListView(this);
        list.setDivider(null);
        list.addHeaderView(content, null, false);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener(
                (parent, view, position, id) -> {
                    int index = position - list.getHeaderViewsCount();
                    if (index < 0 || index >= packages.size()) return;
                    String pkg = packages.get(index);
                    if (mode().equals("exempt") && !userExemptions.contains(pkg)) {
                        message(
                                "System exemption",
                                "Android controls this exemption. It cannot be removed as a user"
                                    + " exemption.");
                        return;
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Remove " + pkg + "?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Remove", (d, w) -> modify(pkg, false))
                            .show();
                });
        load();
    }

    private void manual() {
        EditText value = new EditText(this);
        value.setSingleLine(true);
        value.setHint("com.example.app");
        value.setPadding(dp(20), dp(12), dp(20), dp(12));
        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Package name")
                        .setView(value)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Add", null)
                        .create();
        dialog.setOnShowListener(
                d ->
                        dialog.getButton(-1)
                                .setOnClickListener(
                                        v -> {
                                            String pkg = value.getText().toString().trim();
                                            if (!CommandResult.validPackage(pkg)) {
                                                value.setError("Enter a valid package name");
                                                return;
                                            }
                                            dialog.dismiss();
                                            modify(pkg, true);
                                        }));
        dialog.show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 10 && result == RESULT_OK && data != null)
            modify(data.getStringExtra("package_name"), true);
    }

    private Set<String> parse(String output, boolean userOnly) {
        Set<String> values = new TreeSet<>();
        for (String line : output.split("\\n")) {
            String[] fields = line.split(",");
            if (fields.length >= 3
                    && CommandResult.validPackage(fields[1])
                    && (!userOnly || fields[0].equals("user"))) values.add(fields[1]);
        }
        return values;
    }

    private void load() {
        status.setText("Loading…");
        AccessExecutor.SERIAL.execute(
                () -> {
                    Set<String> values;
                    String error = "";
                    if (mode().equals("exempt")) {
                        CommandResult result =
                                new AccessExecutor(this).run("dumpsys deviceidle whitelist");
                        values = result.ok() ? parse(result.output, false) : new TreeSet<>();
                        userExemptions = result.ok() ? parse(result.output, true) : new HashSet<>();
                        if (!result.ok()) error = result.output;
                    } else
                        values =
                                new TreeSet<>(
                                        PreferenceManager.getDefaultSharedPreferences(this)
                                                .getStringSet(key(), Collections.emptySet()));
                    List<String> data = new ArrayList<>(values), labels = new ArrayList<>();
                    for (String pkg : data) {
                        String label = pkg;
                        try {
                            label =
                                    getPackageManager()
                                            .getApplicationLabel(
                                                    getPackageManager().getApplicationInfo(pkg, 0))
                                            .toString();
                        } catch (Exception ignored) {
                        }
                        labels.add(
                                label
                                        + "\n"
                                        + pkg
                                        + (mode().equals("exempt") && !userExemptions.contains(pkg)
                                                ? " · system"
                                                : ""));
                    }
                    String problem = error;
                    runOnUiThread(
                            () -> {
                                if (isDestroyed()) return;
                                packages = data;
                                list.setAdapter(
                                        new ArrayAdapter<>(
                                                this, android.R.layout.simple_list_item_1, labels));
                                status.setText(
                                        !problem.isEmpty()
                                                ? "Could not load: "
                                                        + problem.substring(
                                                                0, Math.min(300, problem.length()))
                                                : data.isEmpty()
                                                        ? "No apps selected. Use Add app to choose"
                                                              + " one."
                                                        : data.size()
                                                                + " apps · tap an entry to remove"
                                                                + " it");
                            });
                });
    }

    private void modify(String pkg, boolean add) {
        if (!CommandResult.validPackage(pkg)) {
            message("Invalid package", "Use a complete Android package name.");
            return;
        }
        if (add && mode().equals("apps")) {
            try {
                ApplicationInfo app = getPackageManager().getApplicationInfo(pkg, 0);
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        || pkg.equals(getPackageName())
                        || pkg.equals("moe.shizuku.privileged.api")) {
                    message(
                            "Protected app",
                            "System apps, EnforceDoze and Shizuku are protected from suspension.");
                    return;
                }
            } catch (Exception e) {
                message("App not found", "Install the app before adding it to this list.");
                return;
            }
        }
        status.setText("Applying change…");
        AccessExecutor.SERIAL.execute(
                () -> {
                    String error = "";
                    if (mode().equals("exempt")) {
                        AccessExecutor access = new AccessExecutor(this);
                        CommandResult result =
                                access.run(
                                        "dumpsys deviceidle whitelist "
                                                + (add ? "+" : "-")
                                                + CommandResult.quote(pkg));
                        CommandResult read = access.run("dumpsys deviceidle whitelist");
                        if (!result.ok()
                                || !read.ok()
                                || parse(read.output, true).contains(pkg) != add)
                            error = "Exemption change was not verified. " + result.output;
                        new EvidenceStore(this)
                                .record(
                                        "App exemption",
                                        pkg
                                                + ": "
                                                + (error.isEmpty()
                                                        ? "observed " + (add ? "added" : "removed")
                                                        : error));
                    } else {
                        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
                        Set<String> values =
                                new HashSet<>(p.getStringSet(key(), Collections.emptySet()));
                        if (add) values.add(pkg);
                        else values.remove(pkg);
                        p.edit().putStringSet(key(), values).commit();
                        SettingsActivity.reloadSettings(this);
                    }
                    String problem = error;
                    runOnUiThread(
                            () -> {
                                if (isDestroyed()) return;
                                if (!problem.isEmpty()) message("Change failed", problem);
                                load();
                            });
                });
    }
}
