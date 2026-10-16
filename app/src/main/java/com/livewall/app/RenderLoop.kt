package com.livewall.app

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.Surface
import java.util.concurrent.CountDownLatch
import kotlin.random.Random

/**
 * Owns the render thread, the EGL objects and the frame pacing.
 *
 * Battery rules, all enforced here:
 *  - no frame is produced while the wallpaper is not visible,
 *  - a style that is not animated is drawn once per change and then the loop stays idle,
 *  - frames are capped to the mode's fps and scheduled with a delay, so the CPU is not woken
 *    on every vsync,
 *  - Battery Saver forces the cheapest mode,
 *  - GL resources live exactly as long as the window surface.
 *
 * Public methods may be called from any thread; they hand work to the render thread.
 */
internal class RenderLoop(private val context: Context) {

    private val thread = HandlerThread("aurora-gl").also { it.start() }
    private val handler = Handler(thread.looper)

    // Everything below is only touched on the render thread.
    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }
    private val frameCallback = Choreographer.FrameCallback { onFrame(it) }

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var gl: StyleGl? = null

    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var visible = false
    private var frameScheduled = false
    private var settings = AuroraSettings()
    private var powerSave = false
    private var animTime = 0f
    private var lastFrameNanos = 0L
    private val touch = TouchState()

    fun surfaceCreated(newSurface: Surface) {
        handler.post {
            surface = newSurface
            createEgl(newSurface)
            resumeFrames()
        }
    }

    fun surfaceChanged(newWidth: Int, newHeight: Int) {
        handler.post {
            width = newWidth
            height = newHeight
            resumeFrames()
        }
    }

    /** Blocks until the surface is gone from the render thread, so it is safe to let it die. */
    fun surfaceDestroyed() {
        val done = CountDownLatch(1)
        handler.post {
            try {
                pauseFrames()
                destroyEgl()
                surface = null
            } finally {
                done.countDown()
            }
        }
        done.await()
    }

    fun setVisible(isVisible: Boolean) {
        handler.post {
            visible = isVisible
            touch.reset()
            if (isVisible) {
                // A different spot in the animation after every wake-up, and a small time value.
                animTime = Random.nextFloat() * 600f
                lastFrameNanos = 0L
                resumeFrames()
            } else {
                pauseFrames()
            }
        }
    }

    /** A finger touched down at ([x], [y]) in surface pixels. */
    fun touchDown(x: Float, y: Float) = handler.post { normalized(x, y) { nx, ny -> touch.down(nx, ny) } }

    fun touchMove(x: Float, y: Float) = handler.post { normalized(x, y) { nx, ny -> touch.move(nx, ny) } }

    fun touchUp() = handler.post { touch.up() }

    /** A tap that reached the wallpaper as a launcher command, without a finger-down/up stream. */
    fun tap(x: Float, y: Float) = handler.post { normalized(x, y) { nx, ny -> touch.tap(nx, ny) } }

    /** Surface pixels (y down) to the shaders' 0..1 space (y up); render thread only. */
    private inline fun normalized(x: Float, y: Float, use: (Float, Float) -> Unit) {
        if (width > 0 && height > 0) use(x / width, 1f - y / height)
    }

    fun update(newSettings: AuroraSettings, batterySaver: Boolean) {
        handler.post {
            settings = newSettings
            powerSave = batterySaver
            // An idle (static) loop has to be woken for the change to show; a running one
            // picks the new settings up on its next frame.
            if (!frameScheduled) resumeFrames()
        }
    }

    fun release() {
        handler.post {
            pauseFrames()
            destroyEgl()
            thread.quitSafely()
        }
    }

    private fun resumeFrames() {
        if (!visible || eglSurface == EGL14.EGL_NO_SURFACE || width <= 0 || height <= 0) return
        choreographer.removeFrameCallback(frameCallback)
        choreographer.postFrameCallback(frameCallback)
        frameScheduled = true
    }

    private fun pauseFrames() {
        choreographer.removeFrameCallback(frameCallback)
        frameScheduled = false
        lastFrameNanos = 0L
    }

    private fun onFrame(frameTimeNanos: Long) {
        frameScheduled = false
        val renderer = gl
        if (!visible || renderer == null || eglSurface == EGL14.EGL_NO_SURFACE) return

        val workStart = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f
        else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0f, MAX_FRAME_DELTA_S)
        lastFrameNanos = frameTimeNanos
        animTime += dt * settings.speed
        if (settings.touchActive) touch.step(dt) else touch.reset()

        val mode = if (powerSave) PerformanceMode.ECO else settings.mode
        // The clock is read every frame, so a time-of-day fade moves on while the wallpaper is visible.
        val colors = settings.colorsAt(TimeOfDayPalette.minuteOfDayNow())
        renderer.draw(width, height, animTime, settings, colors, maxOf(mode.renderScale, settings.style.minRenderScale), touch)
        checkEgl(EGL14.eglSwapBuffers(display, eglSurface), "eglSwapBuffers")

        // A static style is on screen now and stays there: no further frames until a change.
        if (!settings.style.animated) return

        val workMs = (System.nanoTime() - workStart) / 1e6f
        val delayMs = (1000f / mode.fps - workMs - VSYNC_SLACK_MS).toLong()
        if (delayMs > 0) {
            choreographer.postFrameCallbackDelayed(frameCallback, delayMs)
        } else {
            choreographer.postFrameCallback(frameCallback)
        }
        frameScheduled = true
    }

    private fun loadShader(name: String): String =
        context.assets.open("shaders/$name.frag").bufferedReader().use { it.readText() }

    private fun createEgl(target: Surface) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val version = IntArray(2)
        checkEgl(EGL14.eglInitialize(display, version, 0, version, 1), "eglInitialize")

        // No alpha, depth or stencil: nothing here needs them and they cost bandwidth.
        val configAttributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 0,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        checkEgl(EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, count, 0), "eglChooseConfig")
        check(count[0] > 0) { "No matching EGL config" }
        val config = configs[0]!!

        eglContext = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed: 0x${EGL14.eglGetError().toString(16)}" }

        eglSurface = EGL14.eglCreateWindowSurface(display, config, target, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) {
            "eglCreateWindowSurface failed: 0x${EGL14.eglGetError().toString(16)}"
        }
        checkEgl(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext), "eglMakeCurrent")
        checkEgl(EGL14.eglSwapInterval(display, 1), "eglSwapInterval")

        gl = StyleGl(::loadShader).also { it.init() }
    }

    private fun destroyEgl() {
        gl?.release()
        gl = null
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    private fun checkEgl(ok: Boolean, what: String) {
        check(ok) { "$what failed: EGL error 0x${EGL14.eglGetError().toString(16)}" }
    }

    private companion object {
        /** After a pause the clock must not jump: one frame never advances time by more than this. */
        const val MAX_FRAME_DELTA_S = 0.1f

        /** Wake up a bit before the target time so the frame lands on the right vsync. */
        const val VSYNC_SLACK_MS = 6f
    }
}
