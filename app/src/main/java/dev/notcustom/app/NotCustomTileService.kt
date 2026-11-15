package dev.notcustom.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.notcustom.app.core.AnimScales
import dev.notcustom.app.core.Presets
import dev.notcustom.app.core.ShizukuManager
import dev.notcustom.app.core.Snapshot
import dev.notcustom.app.core.Tweaks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotCustomTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "NotCustom"
            state = if (ShizukuManager.hasPermission()) {
                Tile.STATE_INACTIVE
            } else {
                Tile.STATE_UNAVAILABLE
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (!ShizukuManager.hasPermission()) {
            openApp()
            return
        }
        val snapshot = Presets.load(applicationContext)
        if (snapshot == null) {
            openApp()
            return
        }
        scope.launch {
            runCatching { apply(snapshot) }
        }
    }

    private suspend fun apply(snapshot: Snapshot) {
        Tweaks.setAnimScales(
            AnimScales(snapshot.window, snapshot.transition, snapshot.animator)
        )
        Tweaks.setFontScale(snapshot.fontScale)
        if (snapshot.density == null) {
            Tweaks.resetDensity()
        } else {
            Tweaks.setDensity(snapshot.density)
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
