package com.livewall.app

import kotlin.math.exp
import kotlin.math.min

/**
 * What the touch-aware styles show for the finger: a soft glow that follows it while it rests
 * on the screen, and a ripple that runs outwards from where it landed (also for launcher taps,
 * which arrive without a finger-down/up stream). Positions are normalized to the screen
 * (0..1, y up, like the shaders' vUv). Only touched on the render thread.
 */
internal class TouchState {

    /** Smoothed finger position: the glow trails the finger a little instead of snapping to it. */
    var x = 0.5f
        private set
    var y = 0.5f
        private set

    /** 0..1, how firmly a finger rests on the screen; eases in and out so nothing flashes. */
    var hold = 0f
        private set

    var rippleX = 0.5f
        private set
    var rippleY = 0.5f
        private set

    /**
     * Seconds since the last ripple started, in real time (not scaled by the speed setting, so
     * the reaction always feels equally direct). The shaders draw a ripple for its first
     * 5 seconds; [RIPPLE_END_S] means none is running.
     */
    var rippleAge = RIPPLE_END_S
        private set

    private var targetX = 0.5f
    private var targetY = 0.5f
    private var fingerDown = false

    fun down(nx: Float, ny: Float) {
        x = nx
        y = ny
        targetX = nx
        targetY = ny
        fingerDown = true
        startRipple(nx, ny)
    }

    fun move(nx: Float, ny: Float) {
        targetX = nx
        targetY = ny
    }

    fun up() {
        fingerDown = false
    }

    fun tap(nx: Float, ny: Float) = startRipple(nx, ny)

    /** Back to rest: no finger, no ripple. */
    fun reset() {
        fingerDown = false
        hold = 0f
        rippleAge = RIPPLE_END_S
    }

    fun step(dt: Float) {
        val follow = 1f - exp(-dt * FOLLOW_RATE)
        x += (targetX - x) * follow
        y += (targetY - y) * follow

        val rate = if (fingerDown) ATTACK_RATE else RELEASE_RATE
        hold += ((if (fingerDown) 1f else 0f) - hold) * (1f - exp(-dt * rate))
        if (!fingerDown && hold < HOLD_EPSILON) hold = 0f

        if (rippleAge < RIPPLE_END_S) rippleAge = min(rippleAge + dt, RIPPLE_END_S)
    }

    private fun startRipple(nx: Float, ny: Float) {
        rippleX = nx
        rippleY = ny
        rippleAge = 0f
    }

    private companion object {
        /** Exponential rates per second: the glow rises in about a quarter second, sinks in about a second. */
        const val ATTACK_RATE = 5f
        const val RELEASE_RATE = 1.4f
        const val FOLLOW_RATE = 9f
        const val HOLD_EPSILON = 0.002f

        const val RIPPLE_END_S = 6f
    }
}
