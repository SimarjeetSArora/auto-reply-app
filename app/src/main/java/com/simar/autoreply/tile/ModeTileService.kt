package com.simar.autoreply.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.simar.autoreply.data.AppStore

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
        if (AppStore.activeMode() != null) {
            AppStore.setActiveMode(null)
        } else {
            val modes = AppStore.modes.value
            val target = modes.find { it.id == AppStore.settings.value.lastModeId } ?: modes.firstOrNull()
            AppStore.setActiveMode(target?.id)
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val active = AppStore.activeMode()
        tile.state = if (active != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = active?.name ?: "Auto Reply"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active != null) "On" else "Off"
        }
        tile.updateTile()
    }
}
