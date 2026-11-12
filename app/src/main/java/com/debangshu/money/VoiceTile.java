package com.debangshu.money;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick-settings tile: say a cash spend. */
public class VoiceTile extends TileService {

    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(Tile.STATE_INACTIVE);
        t.updateTile();
    }

    @Override
    public void onClick() { ScanTile.open(this, new Intent(this, VoiceActivity.class), 42); }
}
