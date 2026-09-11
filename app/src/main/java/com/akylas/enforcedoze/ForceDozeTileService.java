package com.akylas.enforcedoze;

import static com.akylas.enforcedoze.Utils.logToLogcat;

import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.N)
public class ForceDozeTileService extends TileService {

    static String TAG = "ForceDozeTileService";

    private static void log(String message) {
        logToLogcat(TAG, message);
    }

    private SharedPreferences settings() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        log("QuickTile added");
        refresh();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        log("QuickTile removed");
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences settings = settings();
        boolean enabled = settings.getBoolean("serviceEnabled", false);
        log(enabled ? "Disabling EnforceDoze" : "Enabling EnforceDoze");
        settings.edit().putBoolean("serviceEnabled", !enabled).apply();
        if (enabled) Utils.stopForceDozeService(this); else Utils.applyForceDozeSchedule(this);
        refresh();
    }

    /**
     * The subtitle reports what the controller is really doing, so a tile left on after the service
     * was killed no longer looks like a running Doze session.
     */
    private void refresh() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean enabled = settings().getBoolean("serviceEnabled", false);
        boolean running = ForceDozeService.isRunning();
        String status = !enabled ? "OFF" : running ? ForceDozeService.status : "NEEDS_START";
        tile.setLabel(getString(R.string.app_name));
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(UiSupport.statusText(this, status));
        tile.updateTile();
    }
}
