package com.akylas.enforcedoze;

import android.os.Build;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

/** This tile selects the screen-off option; it does not toggle the device radio immediately. */
@RequiresApi(24)
public class AirplaneTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        render();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        render();
    }

    @Override
    public void onClick() {
        super.onClick();
        android.content.SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
        p.edit()
                .putBoolean("turnOnAirplaneInDoze", !p.getBoolean("turnOnAirplaneInDoze", false))
                .apply();
        SettingsActivity.reloadSettings(this);
        render();
    }

    private void render() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean enabled =
                PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean("turnOnAirplaneInDoze", false);
        tile.setLabel("Airplane in Doze");
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(enabled ? "Option on" : "Option off");
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
