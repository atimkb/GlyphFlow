package com.example.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.GlyphFlowApplication
import com.example.engine.EventIngestBus

/**
 * Android System Quick Settings Tile allowing instant 1-tap arming or muting
 * of the Nothing Phone (3) GlyphFlow Automation Engine from the notification shade.
 */
class GlyphQuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        val nextEnabled = !EventIngestBus.globalEngineEnabled.value
        EventIngestBus.setGlobalEngineEnabled(nextEnabled)
        if (!nextEnabled) {
            GlyphFlowApplication.instance?.hardwareController?.clearAndCloseSession()
        } else if (!EventIngestBus.isForegroundServiceRunning.value) {
            runCatching { GlyphEngineForegroundService.startEngine(applicationContext) }
        }
        syncTileState()
    }

    private fun syncTileState() {
        val tile = qsTile ?: return
        val enabled = EventIngestBus.globalEngineEnabled.value
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "GlyphFlow"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (enabled) "25x25 Armed" else "Engine Muted"
        }
        tile.updateTile()
    }
}
