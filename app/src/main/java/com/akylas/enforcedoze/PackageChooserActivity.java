package com.akylas.enforcedoze;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;
import java.util.Locale;

/** Loads labels once off the UI thread, includes system apps, and searches package IDs too. */
public class PackageChooserActivity extends UiActivity {
    private final ArrayList<AppsItem> all = new ArrayList<>(), visible = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private EditText search;
    private TextView count;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle(R.string.app_picker_title); root.addView(toolbar);
        search = new EditText(this); search.setSingleLine(); search.setHint(R.string.package_search); root.addView(search);
        count = new TextView(this); count.setText(R.string.loading_installed_apps_text); root.addView(count);
        ListView list = new ListView(this); root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>()); list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            setResult(RESULT_OK, new Intent().putExtra("package_name", visible.get(position).getAppPackageName())); finish();
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int c, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int c) { filter(); }
            public void afterTextChanged(Editable s) { }
        });
        setContentView(root); setSupportActionBar(toolbar); getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        CommandExecutor.submit(() -> {
            ArrayList<AppsItem> loaded = new ArrayList<>();
            try {
                for (ApplicationInfo info : getPackageManager().getInstalledApplications(0)) {
                    AppsItem item = new AppsItem(); item.setAppPackageName(info.packageName);
                    item.setAppName(info.loadLabel(getPackageManager()).toString()); loaded.add(item);
                }
                loaded.sort((a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));
            } catch (RuntimeException ignored) { }
            runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) { all.clear(); all.addAll(loaded); filter(); } });
        });
    }
    private void filter() {
        if (adapter == null) return;
        String query = search.getText().toString().toLowerCase(Locale.ROOT);
        visible.clear(); adapter.clear();
        for (AppsItem item : all) if (item.getAppName().toLowerCase(Locale.ROOT).contains(query) || item.getAppPackageName().toLowerCase(Locale.ROOT).contains(query)) {
            visible.add(item); adapter.add(item.getAppName() + "\n" + item.getAppPackageName());
        }
        count.setText(visible.isEmpty() ? getString(R.string.package_empty) : getString(R.string.app_count, visible.size()));
        adapter.notifyDataSetChanged();
    }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
