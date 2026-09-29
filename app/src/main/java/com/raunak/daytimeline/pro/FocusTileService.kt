package com.raunak.daytimeline.pro

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile: tap to start a Focus Guard block for the first session preset, tap again to end
 * it (not allowed in Locked mode, so the tile can't be used to escape).
 */
class FocusTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val store = FocusGuardStore(this)
        val c = store.config
        val now = System.currentTimeMillis()
        if (FocusGuardEngine.sessionActive(c, now)) {
            if (!c.lockedMode) store.update { it.copy(sessionUntil = 0) }
        } else {
            val minutes = c.sessionPresets.firstOrNull() ?: 25
            store.update { it.copy(sessionUntil = now + minutes * 60_000L) }
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val c = FocusGuardStore(this).config
        val now = System.currentTimeMillis()
        val active = FocusGuardEngine.sessionActive(c, now)
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Focus block"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (active) "${((c.sessionUntil - now) / 60_000L) + 1} min left" + (if (c.lockedMode) " · locked" else "") else "${c.sessionPresets.firstOrNull() ?: 25} min"
        tile.updateTile()
    }
}
