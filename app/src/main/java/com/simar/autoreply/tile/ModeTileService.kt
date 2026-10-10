package com.simar.autoreply.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.simar.autoreply.data.AppStore
import com.simar.autoreply.logic.ModeState

/** Quick Settings tile: tap to switch the last used mode on/off without opening the app. */
class ModeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        AppStore.init(this)
        refresh()
    }

    override fun onClick() {
        super.onClick()
        AppStore.init(this)
        ModeState.toggle(this)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val active = ModeState.current()
        tile.state = if (active != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = active?.mode?.name ?: "Auto Reply"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                active == null -> "Off"
                active.until != null -> "Until ${ModeState.formatUntil(this, active.until)}"
                else -> "On"
            }
        }
        tile.updateTile()
    }
}
