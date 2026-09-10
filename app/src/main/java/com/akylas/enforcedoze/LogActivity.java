package com.akylas.enforcedoze;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import androidx.core.content.FileProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class LogActivity extends UiActivity {
    private String log = "";
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_log);
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        fetch(false);
    }
    private void fetch(boolean full) {
        EditText display = findViewById(R.id.editText); display.setText(R.string.diagnostics_running);
        CommandExecutor.execute(this, full ? "logcat -d -t 1500" : "logcat -d -t 1500 -s EnforceDoze ForceDozeService Shizuku Settings", result -> {
            if (isFinishing() || isDestroyed()) return;
            log = result.output; display.setText(log); display.setTextIsSelectable(true);
            if (full && result.success()) share();
        });
    }
    private void share() {
        try {
            File directory = new File(getCacheDir(), "logs");
            if (!directory.exists() && !directory.mkdirs()) throw new java.io.IOException("Cannot create log directory");
            File file = new File(directory, "enforcedoze-log.txt");
            try (FileOutputStream stream = new FileOutputStream(file)) { stream.write(log.getBytes(StandardCharsets.UTF_8)); }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("Log", uri));
            startActivity(Intent.createChooser(intent, getString(R.string.dashboard_diagnostics)));
        } catch (Exception e) { new MaterialAlertDialogBuilder(this).setMessage(e.toString()).setPositiveButton(R.string.close_button_text, null).show(); }
    }
    @Override public boolean onCreateOptionsMenu(Menu menu) { getMenuInflater().inflate(R.menu.debug_logs_menu, menu); return true; }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_share_log) { share(); return true; }
        if (item.getItemId() == R.id.action_share_fulllog) { fetch(true); return true; }
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
