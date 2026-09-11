package com.akylas.enforcedoze;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public class TaskerBroadcastsActivity extends UiActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasker_broadcasts);
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        ListView list = findViewById(R.id.listViewBroadcasts);
        TextView notice = new TextView(this);
        notice.setId(R.id.automationNotice);
        notice.setText(R.string.automation_instructions);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        notice.setPadding(0, 0, 0, padding);
        list.addHeaderView(notice, null, false);
        ArrayList<TaskerBroadcastsItem> items = new ArrayList<>();
        items.add(new TaskerBroadcastsItem("com.akylas.enforcedoze.ENABLE_FORCEDOZE", getString(R.string.tasker_no_extras)));
        items.add(new TaskerBroadcastsItem("com.akylas.enforcedoze.DISABLE_FORCEDOZE", getString(R.string.tasker_no_extras)));
        items.add(new TaskerBroadcastsItem("com.akylas.enforcedoze.ADD_WHITELIST", getString(R.string.tasker_package_extra)));
        items.add(new TaskerBroadcastsItem("com.akylas.enforcedoze.REMOVE_WHITELIST", getString(R.string.tasker_package_extra)));
        // Generate this list from the receiver's allowlist so documentation cannot silently drift.
        items.add(new TaskerBroadcastsItem("com.akylas.enforcedoze.CHANGE_SETTING",
                getString(R.string.tasker_settings_extras, android.text.TextUtils.join("\n", Utils.updatableSettings()))));
        list.setAdapter(new TaskerBroadcastsAdapter(this, items));
        list.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            if (!(item instanceof TaskerBroadcastsItem)) return;
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Tasker action", ((TaskerBroadcastsItem) item).getBroadcastName()));
            // Android 13+ already displays a clipboard overlay.
            if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.tasker_copied, Toast.LENGTH_SHORT).show();
        });
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
