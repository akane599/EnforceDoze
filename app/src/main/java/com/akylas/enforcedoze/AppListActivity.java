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
import androidx.recyclerview.widget.ConcatAdapter;
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
import java.util.concurrent.Future;

/** Shared, searchable editor with explicit removal and undo on every app list. */
public abstract class AppListActivity extends UiActivity {
    protected abstract String preferenceKey(); // null is Android's actual Doze whitelist
    protected abstract int titleResource();
    private final ArrayList<AppsItem> all = new ArrayList<>(), visible = new ArrayList<>();
    private final android.os.Handler debounce = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable filter = this::filter;
    private AppsAdapter adapter;
    private EditText search;
    private TextView empty;
    private View root;
    private SharedPreferences prefs;
    private int loadVersion;
    private Future<?> loadingTask;
    private boolean loading;
    private String loadError;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); prefs = PreferenceManager.getDefaultSharedPreferences(this);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); root = layout;
        MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle(titleResource()); layout.addView(toolbar);
        int dp = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp, 0, dp, 0);
        search = new EditText(this); search.setId(R.id.packageSearch); search.setSingleLine(); search.setHint(R.string.package_search); body.addView(search);
        MaterialButton add = new MaterialButton(this); add.setText(R.string.app_picker_title); body.addView(add);
        add.setOnClickListener(v -> startActivityForResult(new Intent(this, PackageChooserActivity.class), 99));
        MaterialButton manual = new MaterialButton(this); manual.setText(R.string.add_package_button); body.addView(manual);
        manual.setOnClickListener(v -> {
            EditText input = new EditText(this); input.setSingleLine(); input.setHint("com.spotify.music");
            androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.add_package_button).setView(input)
                    .setPositiveButton(R.string.okay_button_text, null)
                    .setNegativeButton(R.string.close_button_text, null).create();
            dialog.setOnShowListener(ignored -> dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(button -> {
                String pkg = input.getText().toString().trim();
                if (!CommandPolicy.validPackage(pkg)) { input.setError(getString(R.string.invalid_package)); return; }
                modify(pkg, false);
                dialog.dismiss();
            }));
            dialog.show();
        });
        if (preferenceKey() == null) {
            MaterialButton system = new MaterialButton(this); system.setText(R.string.dashboard_battery_settings); body.addView(system);
            system.setOnClickListener(v -> UiSupport.open(this, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        }
        empty = new TextView(this); empty.setId(R.id.packageCount); body.addView(empty);
        RecyclerView list = new RecyclerView(this); list.setId(R.id.packageList); list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppsAdapter(this, visible); adapter.setOnRemoveListener(pkg -> modify(pkg, true));
        // The controls scroll with the rows so large fonts and landscape cannot squeeze the list to zero height.
        body.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
        body.setPadding(0, 0, 0, 0);
        list.setPadding(dp, 0, dp, 0);
        list.setItemAnimator(null);
        list.setAdapter(new ConcatAdapter(new ListHeaderAdapter(body), adapter));
        layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout); setSupportActionBar(toolbar); getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { scheduleFilter(); }
            public void afterTextChanged(Editable e) { }
        });
        if (state != null) search.setText(state.getString("search", ""));
    }
    private void load() {
        int version = ++loadVersion;
        if (loadingTask != null) loadingTask.cancel(true);
        loading = true;
        loadError = null;
        all.clear();
        filter();
        if (preferenceKey() == null) {
            CommandExecutor.execute(this, "dumpsys deviceidle whitelist", result -> {
                if (!current(version)) return;
                if (!result.success()) {
                    loading = false;
                    loadError = result.output;
                    filter();
                    return;
                }
                Set<String> packages = new LinkedHashSet<>();
                for (String line : result.lines()) {
                    String[] parts = line.split(",");
                    if (parts.length >= 2 && CommandPolicy.validPackage(parts[1].trim())) packages.add(parts[1].trim());
                }
                loadLabels(version, packages);
            });
        } else loadLabels(version, new LinkedHashSet<>(prefs.getStringSet(preferenceKey(), Collections.emptySet())));
    }
    private boolean current(int version) { return !isFinishing() && !isDestroyed() && version == loadVersion; }
    private void loadLabels(int version, Set<String> packages) {
        loadingTask = AppCatalog.load(this, packages, (items, failure) -> {
            if (!current(version)) return;
            loading = false;
            loadError = failure == null ? null : getString(R.string.package_load_failed);
            all.clear(); all.addAll(items); filter();
        });
    }
    /** Coalesces fast typing into one pass so every keystroke does not re-diff the whole list. */
    private void scheduleFilter() {
        debounce.removeCallbacks(filter);
        debounce.postDelayed(filter, 120);
    }

    private void filter() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (AppsItem item : all) if (item.getAppName().toLowerCase(Locale.ROOT).contains(query) || item.getAppPackageName().toLowerCase(Locale.ROOT).contains(query)) visible.add(item);
        adapter.submit(visible);
        if (loading) empty.setText(R.string.loading_installed_apps_text);
        else if (loadError != null) empty.setText(loadError);
        else empty.setText(visible.isEmpty() ? getString(R.string.package_empty) : getString(R.string.app_count, visible.size()));
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
    @Override protected void onResume() { super.onResume(); load(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("search", search.getText().toString());
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        ++loadVersion;
        debounce.removeCallbacks(filter);
        if (loadingTask != null) loadingTask.cancel(true);
        super.onDestroy();
    }
}
