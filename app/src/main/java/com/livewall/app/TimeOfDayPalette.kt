package com.livewall.app

import androidx.annotation.StringRes
import java.time.LocalTime

/**
 * The four phases of the time-of-day option, each starting at a clock time (device-local; no
 * location is needed, so the times are fixed). [defaults] are the colors a phase has until the
 * user edits it. The declaration order is the order of the day; night runs over midnight into
 * the first phase. The enum name is stored in the settings, so renaming an entry loses the
 * user's colors for it.
 */
enum class DayPhase(@StringRes val label: Int, val startMinute: Float, val defaults: PaletteColors) {
    // Sunrise: violet-dark top, rose and amber towards the bottom.
    MORNING(R.string.phase_morning, 6 * 60f, PaletteColors(0xFF1E1030.toInt(), 0xFF5A2D4F.toInt(), 0xFFB5604F.toInt(), 0xFF140A08.toInt(), 0xFFFFC48A.toInt(), 0xFFFF8FA0.toInt(), 0xFFFFE9A8.toInt())),
    // Day: clear blue-teal, kept dark enough for the light lines and characters to read.
    DAY(R.string.phase_day, 9 * 60f, PaletteColors(0xFF0B3A52.toInt(), 0xFF14688A.toInt(), 0xFF2E9BB8.toInt(), 0xFF0A1418.toInt(), 0xFFB8F2FF.toInt(), 0xFFFFE9A0.toInt(), 0xFF9BFFD0.toInt())),
    // Sunset: plum top, burnt orange at the bottom.
    EVENING(R.string.phase_evening, 18 * 60f, PaletteColors(0xFF1A0C26.toInt(), 0xFF6B2A4A.toInt(), 0xFFC2593A.toInt(), 0xFF140808.toInt(), 0xFFFFA257.toInt(), 0xFFFF6F91.toInt(), 0xFFC59BFF.toInt())),
    // Night: deep navy with cool blue accents.
    NIGHT(R.string.phase_night, 21 * 60f, PaletteColors(0xFF050A1E.toInt(), 0xFF0A1838.toInt(), 0xFF14305A.toInt(), 0xFF080C14.toInt(), 0xFF6FA8FF.toInt(), 0xFF8C7BFF.toInt(), 0xFF5FE0E8.toInt())),
}

/**
 * Colors by time of day: each phase keeps its colors, and at a phase boundary they cross-fade
 * over [TRANSITION_MINUTES], centred on the boundary (06:00 sunrise fades from 05:45 to 06:15).
 */
object TimeOfDayPalette {

    const val TRANSITION_MINUTES = 30f

    private const val MINUTES_PER_DAY = 24 * 60f

    /** Local time of day as minutes since midnight, with seconds as the fraction. */
    fun minuteOfDayNow(): Float = LocalTime.now().toSecondOfDay() / 60f

    /** The phase that has most recently started at [minuteOfDay] (0 until 1440). */
    fun phaseAt(minuteOfDay: Float): DayPhase =
        DayPhase.entries.lastOrNull { it.startMinute <= minuteOfDay } ?: DayPhase.entries.last()

    /** The colors at [minuteOfDay]; [overrides] are the user's colors per phase, if any. */
    fun colorsAt(minuteOfDay: Float, overrides: Map<DayPhase, PaletteColors>): PaletteColors {
        fun colors(phase: DayPhase) = overrides[phase] ?: phase.defaults
        val half = TRANSITION_MINUTES / 2f
        val phases = DayPhase.entries
        for (i in phases.indices) {
            val phase = phases[i]
            val before = phases[(i + phases.size - 1) % phases.size]
            val offset = signedDistance(minuteOfDay, phase.startMinute)
            if (offset > -half && offset < half) {
                val t = (offset + half) / TRANSITION_MINUTES
                return colors(before).mix(colors(phase), t * t * (3f - 2f * t))
            }
        }
        // Not inside a fade: the phase that has started.
        return colors(phaseAt(minuteOfDay))
    }

    /** [minute] minus [boundary], wrapped into -720 until 720 so a fade can span midnight. */
    private fun signedDistance(minute: Float, boundary: Float): Float {
        var d = minute - boundary
        if (d >= MINUTES_PER_DAY / 2f) d -= MINUTES_PER_DAY
        if (d < -MINUTES_PER_DAY / 2f) d += MINUTES_PER_DAY
        return d
    }
}
