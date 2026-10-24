package com.daniel.assistant.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.daniel.assistant.ui.MainActivity

/** Quick Settings tile: one tap → speak a command. */
class NabraTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = if (ListeningService.state.value.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Nabra"
            updateTile()
        }
    }

    override fun onClick() {
        if (ListeningService.state.value.running) {
            startService(Intent(this, ListeningService::class.java).setAction(ListeningService.ACTION_LISTEN_NOW))
            return
        }
        // Starting the microphone needs the app in the foreground: go through the activity.
        val i = Intent(this, MainActivity::class.java)
            .setAction(ListeningService.ACTION_LISTEN_NOW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 7, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(i)
        }
    }
}
