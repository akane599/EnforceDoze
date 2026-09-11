package com.akylas.enforcedoze;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/** The tile mirrors the enabled preference and refreshes only while Android listens. */
@RequiresApi(Build.VERSION_CODES.N)
public class ForceDozeTileService extends TileService {
    private SharedPreferences preferences;
    private boolean listening;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (prefs, key) -> {
        if (key == null || "serviceEnabled".equals(key)) refresh();
    };
    private final BroadcastReceiver stateListener = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };

    @Override public void onTileAdded() {
        super.onTileAdded();
        requestListeningState(this, new ComponentName(this, ForceDozeTileService.class));
    }

    @Override public void onStartListening() {
        super.onStartListening();
        if (!listening) {
            listening = true;
            preferences = PreferenceManager.getDefaultSharedPreferences(this);
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
            LocalBroadcastManager.getInstance(this).registerReceiver(stateListener, new IntentFilter(ForceDozeService.ACTION_STATE));
        }
        refresh();
    }

    @Override public void onStopListening() {
        stopListening();
        super.onStopListening();
    }

    @Override public void onDestroy() {
        stopListening();
        super.onDestroy();
    }

    private void stopListening() {
        if (!listening) return;
        listening = false;
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stateListener);
    }

    @Override public void onClick() {
        super.onClick();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enabled = !prefs.getBoolean("serviceEnabled", false);
        prefs.edit().putBoolean("serviceEnabled", enabled).apply();
        if (enabled) Utils.applyForceDozeSchedule(this); else Utils.stopForceDozeService(this);
        refresh();
    }

    private void refresh() {
        if (!listening) return;
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean enabled = preferences.getBoolean("serviceEnabled", false);
        tile.setLabel(getString(R.string.app_name));
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) {
            String status = !enabled ? "OFF" : Utils.isMyServiceRunning(ForceDozeService.class, this)
                    ? ForceDozeService.status : "NEEDS_START";
            tile.setSubtitle(UiSupport.statusText(this, status));
        }
        tile.updateTile();
    }
}
