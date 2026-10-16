package com.livewall.app

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

class MainActivity : ComponentActivity() {

    private lateinit var store: SettingsStore
    private var wallpaperActive by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = SettingsStore(this)
        setContent {
            LiveWallTheme {
                var settings by remember { mutableStateOf(store.load()) }
                SettingsScreen(
                    settings = settings,
                    wallpaperActive = wallpaperActive,
                    onSettingsChange = {
                        settings = it
                        store.save(it)
                    },
                    onSetWallpaper = ::requestWallpaper,
                    onPreviewTouch = ::sendTapToWallpaper,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        wallpaperActive = isOwnWallpaperActive()
    }

    private fun isOwnWallpaperActive(): Boolean {
        val info = WallpaperManager.getInstance(this).wallpaperInfo ?: return false
        return info.packageName == packageName && info.serviceName == AuroraWallpaperService::class.java.name
    }

    /** Tells the wallpaper behind the window about a tap, the same way a launcher does. */
    private fun sendTapToWallpaper(position: Offset) {
        if (!wallpaperActive) return
        WallpaperManager.getInstance(this).sendWallpaperCommand(
            window.decorView.windowToken, WallpaperManager.COMMAND_TAP, position.x.toInt(), position.y.toInt(), 0, null,
        )
    }

    private fun requestWallpaper() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, AuroraWallpaperService::class.java),
        )
        startActivity(intent)
    }
}
