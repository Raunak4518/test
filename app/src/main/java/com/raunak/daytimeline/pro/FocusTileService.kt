package com.raunak.daytimeline.pro

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: one tap turns Focus mode on or off (strict timers and schedules can't be ended here). */
class FocusTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val c = FocusGuardStore(this).config
        if (FocusMode.status(c).on) FocusMode.turnOff(this) else FocusMode.turnOn(this)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val s = FocusMode.status(FocusGuardStore(this).config)
        tile.state = if (s.on && !s.onBreak) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Focus mode"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = s.label
        tile.updateTile()
    }
}
