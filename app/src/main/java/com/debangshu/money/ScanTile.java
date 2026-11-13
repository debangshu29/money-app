package com.debangshu.money;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick-settings tile: pull down the notification shade → Pay & note. */
public class ScanTile extends TileService {

    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(Tile.STATE_INACTIVE);
        t.updateTile();
    }

    @Override
    public void onClick() { open(this, new Intent(this, MainActivity.class).putExtra("pay", true), 41); }

    @SuppressWarnings("deprecation")
    static void open(TileService s, Intent i, int req) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= 34) {
            s.startActivityAndCollapse(PendingIntent.getActivity(s, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            s.startActivityAndCollapse(i);
        }
    }
}
