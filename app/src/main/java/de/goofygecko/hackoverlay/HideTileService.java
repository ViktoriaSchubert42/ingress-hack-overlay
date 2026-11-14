package de.goofygecko.hackoverlay;

import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile that hides or shows the buttons, e.g. right before taking a screenshot. */
public class HideTileService extends TileService {

    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        SharedPreferences prefs = Prefs.get(this);
        // A running overlay reacts to the change straight away.
        prefs.edit().putBoolean(Prefs.HIDDEN, !Prefs.hidden(prefs)).apply();
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean hidden = Prefs.hidden(Prefs.get(this));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_notif));
        tile.setLabel("Hack buttons");
        tile.setState(hidden ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(hidden ? "Hidden" : "Shown");
        tile.updateTile();
    }
}
