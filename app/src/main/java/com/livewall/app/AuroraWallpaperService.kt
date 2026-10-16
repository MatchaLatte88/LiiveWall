package com.livewall.app

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat

class AuroraWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = AuroraEngine()

    private inner class AuroraEngine : Engine() {

        private val store = SettingsStore(this@AuroraWallpaperService)
        private val powerManager = getSystemService(PowerManager::class.java)
        private lateinit var loop: RenderLoop
        private var lastColors: PaletteColors? = null
        private var touchEnabled = false

        private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            applySettings()
        }
        private val powerSaveReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = applySettings()
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            loop = RenderLoop(applicationContext)
            store.registerListener(settingsListener)
            ContextCompat.registerReceiver(
                this@AuroraWallpaperService,
                powerSaveReceiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            applySettings()
        }

        override fun onDestroy() {
            store.unregisterListener(settingsListener)
            unregisterReceiver(powerSaveReceiver)
            loop.release()
            super.onDestroy()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            loop.surfaceCreated(holder.surface)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            loop.surfaceChanged(width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            loop.surfaceDestroyed()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            loop.setVisible(visible)
            // With time-of-day colors the system's color extraction goes stale while the screen is off.
            if (visible) notifyIfColorsChanged(store.load())
        }

        // Raw touches only reach the wallpaper where the window on top lets them through ...
        override fun onTouchEvent(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> loop.touchDown(event.x, event.y)
                MotionEvent.ACTION_MOVE -> loop.touchMove(event.x, event.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> loop.touchUp()
            }
        }

        // ... while a launcher reports a tap on its empty home screen as a wallpaper command.
        override fun onCommand(
            action: String?,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultRequested: Boolean,
        ): Bundle? {
            if (touchEnabled && action == WallpaperManager.COMMAND_TAP) loop.tap(x.toFloat(), y.toFloat())
            return null
        }

        override fun onComputeColors(): WallpaperColors {
            val colors = store.load().colorsAt(TimeOfDayPalette.minuteOfDayNow())
            return WallpaperColors(
                Color.valueOf(colors.primary),
                Color.valueOf(colors.secondary),
                Color.valueOf(colors.deep),
            )
        }

        private fun applySettings() {
            val settings = store.load()
            loop.update(settings, powerManager.isPowerSaveMode)
            if (settings.touchActive != touchEnabled) {
                touchEnabled = settings.touchActive
                setTouchEventsEnabled(touchEnabled)
            }
            notifyIfColorsChanged(settings)
        }

        private fun notifyIfColorsChanged(settings: AuroraSettings) {
            val colors = settings.colorsAt(TimeOfDayPalette.minuteOfDayNow())
            if (colors != lastColors) {
                lastColors = colors
                notifyColorsChanged()
            }
        }
    }
}
