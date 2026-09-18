package com.akylas.enforcedoze;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.text.*;
import android.widget.*;

import java.util.*;

public class PackageChooserActivity extends BaseActivity {
    private final List<String> packages = new ArrayList<>(),
            labels = new ArrayList<>(),
            visible = new ArrayList<>();
    private ListView list;
    private EditText search;
    private TextView status;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        screen("Choose an app", true);
        root.removeViewAt(1);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), 0, dp(16), 0);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search apps or packages");
        search.setMinHeight(dp(56));
        content.addView(search);
        status = text(content, "Loading installed apps…", 15, false);
        list = new ListView(this);
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener(
                (p, v, position, id) -> {
                    setResult(
                            RESULT_OK,
                            new Intent().putExtra("package_name", visible.get(position)));
                    finish();
                });
        search.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        filter();
                    }

                    public void afterTextChanged(Editable s) {}
                });
        AccessExecutor.CATALOG.execute(
                () -> {
                    try {
                        List<ApplicationInfo> installed =
                                getPackageManager().getInstalledApplications(0);
                        List<String[]> rows = new ArrayList<>();
                        for (ApplicationInfo app : installed)
                            rows.add(
                                    new String[] {
                                        app.packageName,
                                        app.loadLabel(getPackageManager()).toString()
                                    });
                        Collections.sort(rows, (a, b) -> a[1].compareToIgnoreCase(b[1]));
                        List<String> p = new ArrayList<>(), l = new ArrayList<>();
                        for (String[] row : rows) {
                            p.add(row[0]);
                            l.add(row[1] + "\n" + row[0]);
                        }
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed()) return;
                                    packages.addAll(p);
                                    labels.addAll(l);
                                    filter();
                                });
                    } catch (Exception e) {
                        runOnUiThread(
                                () -> {
                                    if (!isDestroyed())
                                        status.setText(
                                                "Could not load apps. Close this screen and"
                                                        + " retry.");
                                });
                    }
                });
    }

    private void filter() {
        String query = search.getText().toString().toLowerCase(Locale.ROOT);
        visible.clear();
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < packages.size(); i++)
            if (labels.get(i).toLowerCase(Locale.ROOT).contains(query)) {
                visible.add(packages.get(i));
                rows.add(labels.get(i));
            }
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
        status.setText(rows.isEmpty() ? "No matching apps" : rows.size() + " apps");
    }
}
