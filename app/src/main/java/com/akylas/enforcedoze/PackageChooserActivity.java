package com.akylas.enforcedoze;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Future;

/** Searchable package metadata with one adapter update per query. */
public class PackageChooserActivity extends UiActivity {
    private final ArrayList<AppsItem> all = new ArrayList<>(), visible = new ArrayList<>();
    private AppsAdapter adapter;
    private EditText search;
    private TextView count;
    private MaterialButton retry;
    private Future<?> loadingTask;
    private int loadVersion;
    private boolean loading;
    private String error;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_package_chooser);
        setSupportActionBar(findViewById(R.id.packageToolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        search = findViewById(R.id.packageSearch);
        count = findViewById(R.id.packageCount);
        retry = findViewById(R.id.packageRetry);
        retry.setOnClickListener(v -> load());
        RecyclerView list = findViewById(R.id.packageList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppsAdapter(this, visible);
        adapter.setOnSelectListener(pkg -> {
            setResult(RESULT_OK, new Intent().putExtra("package_name", pkg));
            finish();
        });
        list.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int c, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int c) { filter(); }
            public void afterTextChanged(Editable s) { }
        });
        load();
    }

    private void load() {
        int version = ++loadVersion;
        if (loadingTask != null) loadingTask.cancel(true);
        loading = true;
        error = null;
        all.clear();
        filter();
        loadingTask = AppCatalog.load(this, null, (apps, failure) -> {
            if (isFinishing() || isDestroyed() || version != loadVersion) return;
            loading = false;
            error = failure;
            all.clear();
            all.addAll(apps);
            filter();
        });
    }

    private void filter() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (AppsItem item : all) {
            if (item.getAppName().toLowerCase(Locale.ROOT).contains(query)
                    || item.getAppPackageName().toLowerCase(Locale.ROOT).contains(query)) visible.add(item);
        }
        adapter.notifyDataSetChanged();
        retry.setVisibility(error == null ? View.GONE : View.VISIBLE);
        if (loading) count.setText(R.string.loading_installed_apps_text);
        else if (error != null) count.setText(R.string.package_load_failed);
        else count.setText(visible.isEmpty() ? getString(R.string.package_empty) : getString(R.string.app_count, visible.size()));
    }

    @Override protected void onDestroy() {
        ++loadVersion;
        if (loadingTask != null) loadingTask.cancel(true);
        super.onDestroy();
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
