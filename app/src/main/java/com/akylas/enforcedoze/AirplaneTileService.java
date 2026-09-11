package com.akylas.enforcedoze;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.annotation.RequiresApi;

/** Controls the airplane-during-Doze preference, not the current system airplane mode. */
@RequiresApi(Build.VERSION_CODES.N)
public class AirplaneTileService extends TileService {
    private SharedPreferences preferences;
    private boolean listening;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (key == null || "turnOnAirplaneInDoze".equals(key)) refresh();
    };

    @Override public void onTileAdded() {
        super.onTileAdded();
        requestListeningState(this, new ComponentName(this, AirplaneTileService.class));
    }

    @Override public void onStartListening() {
        super.onStartListening();
        if (!listening) {
            listening = true;
            preferences = PreferenceManager.getDefaultSharedPreferences(this);
            preferences.registerOnSharedPreferenceChangeListener(listener);
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
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    @Override public void onClick() {
        super.onClick();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean("turnOnAirplaneInDoze", !prefs.getBoolean("turnOnAirplaneInDoze", false)).apply();
        refresh();
        SettingsActivity.reloadSettings(this);
    }

    private void refresh() {
        if (!listening) return;
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean enabled = preferences.getBoolean("turnOnAirplaneInDoze", false);
        tile.setLabel(getString(R.string.airplane_tile_label));
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(getString(enabled ? R.string.tile_subtitle_on : R.string.tile_subtitle_off));
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
