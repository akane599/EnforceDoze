package com.akylas.enforcedoze;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared, searchable editor with explicit removal and undo on every app list. */
public abstract class AppListActivity extends UiActivity {
    protected abstract String preferenceKey(); // null is Android's actual Doze whitelist
    protected abstract int titleResource();
    private final ArrayList<AppsItem> all = new ArrayList<>(), visible = new ArrayList<>();
    private AppsAdapter adapter;
    private EditText search;
    private TextView empty;
    private View root;
    private SharedPreferences prefs;
    private int loadVersion;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); prefs = PreferenceManager.getDefaultSharedPreferences(this);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); root = layout;
        MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle(titleResource()); layout.addView(toolbar);
        int dp = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp, 0, dp, 0);
        layout.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        search = new EditText(this); search.setSingleLine(); search.setHint(R.string.package_search); body.addView(search);
        MaterialButton add = new MaterialButton(this); add.setText(R.string.app_picker_title); body.addView(add);
        add.setOnClickListener(v -> startActivityForResult(new Intent(this, PackageChooserActivity.class), 99));
        MaterialButton manual = new MaterialButton(this); manual.setText(R.string.add_package_button); body.addView(manual);
        manual.setOnClickListener(v -> {
            EditText input = new EditText(this); input.setSingleLine(); input.setHint("com.spotify.music");
            new MaterialAlertDialogBuilder(this).setTitle(R.string.app_picker_title).setView(input)
                    .setPositiveButton(R.string.okay_button_text, (dialog, which) -> modify(input.getText().toString().trim(), false))
                    .setNegativeButton(R.string.close_button_text, null).show();
        });
        if (preferenceKey() == null) {
            MaterialButton system = new MaterialButton(this); system.setText(R.string.dashboard_battery_settings); body.addView(system);
            system.setOnClickListener(v -> UiSupport.open(this, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        }
        empty = new TextView(this); body.addView(empty);
        RecyclerView list = new RecyclerView(this); list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppsAdapter(this, visible); adapter.setOnRemoveListener(pkg -> modify(pkg, true)); list.setAdapter(adapter);
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout); setSupportActionBar(toolbar); getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            public void afterTextChanged(Editable e) { }
        });
        load();
    }
    private void load() {
        int version = ++loadVersion;
        empty.setText(R.string.loading_installed_apps_text);
        CommandExecutor.submit(() -> {
            Set<String> packages = new LinkedHashSet<>();
            String error = "";
            if (preferenceKey() == null) {
                CommandResult result = CommandExecutor.run(this, CommandExecutor.mode(this), "dumpsys deviceidle whitelist");
                if (result.success()) {
                    for (String line : result.lines()) {
                        String[] parts = line.split(",");
                        if (parts.length >= 2 && CommandPolicy.validPackage(parts[1].trim())) packages.add(parts[1].trim());
                    }
                } else error = result.output;
            } else packages.addAll(prefs.getStringSet(preferenceKey(), Collections.emptySet()));
            ArrayList<AppsItem> items = new ArrayList<>();
            for (String pkg : packages) {
                AppsItem item = new AppsItem(); item.setAppPackageName(pkg);
                try { item.setAppName(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString()); }
                catch (Exception e) { item.setAppName(pkg); }
                items.add(item);
            }
            items.sort((a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));
            String failure = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || version != loadVersion) return;
                all.clear(); all.addAll(items); filter();
                if (!failure.isEmpty()) empty.setText(failure);
            });
        });
    }
    private void filter() {
        String query = search.getText().toString().toLowerCase(Locale.ROOT);
        visible.clear();
        for (AppsItem item : all) if (item.getAppName().toLowerCase(Locale.ROOT).contains(query) || item.getAppPackageName().toLowerCase(Locale.ROOT).contains(query)) visible.add(item);
        adapter.notifyDataSetChanged();
        empty.setText(visible.isEmpty() ? getString(R.string.package_empty) : getString(R.string.app_count, visible.size()));
    }
    private void modify(String pkg, boolean remove) {
        if (!CommandPolicy.validPackage(pkg)) { message(getString(R.string.invalid_package)); return; }
        if (!remove && preferenceKey() != null && CommandPolicy.protectedPackage(pkg)) {
            message(getString(R.string.protected_package)); return;
        }
        if (preferenceKey() == null) {
            CommandExecutor.execute(this, "dumpsys deviceidle whitelist " + (remove ? "-" : "+") + pkg, result -> {
                if (isFinishing() || isDestroyed()) return;
                load();
                if (!result.success()) message(result.output);
                else if (remove) undo(pkg);
            });
        } else {
            Set<String> packages = new LinkedHashSet<>(prefs.getStringSet(preferenceKey(), Collections.emptySet()));
            if (remove) packages.remove(pkg); else packages.add(pkg);
            prefs.edit().putStringSet(preferenceKey(), packages).apply();
            SettingsActivity.reloadSettings(this); load(); if (remove) undo(pkg);
        }
    }
    private void undo(String pkg) { Snackbar.make(root, pkg, Snackbar.LENGTH_LONG).setAction(R.string.undo_action, v -> modify(pkg, false)).show(); }
    private void message(String text) { new MaterialAlertDialogBuilder(this).setMessage(text).setPositiveButton(R.string.close_button_text, null).show(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 99 && result == RESULT_OK && data != null) modify(data.getStringExtra("package_name"), false);
    }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
