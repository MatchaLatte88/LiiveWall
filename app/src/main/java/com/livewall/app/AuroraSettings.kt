package com.livewall.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.annotation.StringRes
import kotlin.math.roundToInt

/**
 * The seven colors every style draws with: [deep], [mid] and [shallow] form the background
 * gradient (top, 30 %, bottom), [glow] is a soft additive lift in the lower middle, and
 * [primary], [secondary], [tertiary] are the accents the animated styles draw with.
 */
data class PaletteColors(
    val deep: Int,
    val mid: Int,
    val shallow: Int,
    val glow: Int,
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
) {
    /**
     * Replaces the background from a single color: [color] is the light end, the darker steps and
     * the glow are derived from it, so one color picker is enough for the whole gradient.
     */
    fun withBackground(color: Int) = copy(
        deep = scaled(color, 0.35f),
        mid = scaled(color, 0.65f),
        shallow = color,
        glow = scaled(color, 0.12f),
    )

    /** Channel-wise blend from these colors ([t] = 0) to [other] ([t] = 1). */
    fun mix(other: PaletteColors, t: Float) = PaletteColors(
        deep = lerp(deep, other.deep, t),
        mid = lerp(mid, other.mid, t),
        shallow = lerp(shallow, other.shallow, t),
        glow = lerp(glow, other.glow, t),
        primary = lerp(primary, other.primary, t),
        secondary = lerp(secondary, other.secondary, t),
        tertiary = lerp(tertiary, other.tertiary, t),
    )

    private fun lerp(from: Int, to: Int, t: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).roundToInt().coerceIn(0, 255),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).roundToInt().coerceIn(0, 255),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).roundToInt().coerceIn(0, 255),
    )

    private fun scaled(argb: Int, factor: Float): Int = Color.rgb(
        (Color.red(argb) * factor).roundToInt().coerceIn(0, 255),
        (Color.green(argb) * factor).roundToInt().coerceIn(0, 255),
        (Color.blue(argb) * factor).roundToInt().coerceIn(0, 255),
    )
}

enum class AuroraPalette(
    @StringRes val label: Int,
    val deep: Int,
    val mid: Int,
    val shallow: Int,
    val glow: Int,
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
) {
    // deep/mid/shallow/glow of DEEP_SEA are measured from the reference screenshot.
    DEEP_SEA(R.string.palette_deep_sea, 0xFF0A2A33.toInt(), 0xFF0F4547.toInt(), 0xFF236B5F.toInt(), 0xFF0C100D.toInt(), 0xFF59F2B3.toInt(), 0xFF40A6F2.toInt(), 0xFFB48CFF.toInt()),
    POLAR(R.string.palette_polar, 0xFF080F29.toInt(), 0xFF0F253C.toInt(), 0xFF1A4759.toInt(), 0xFF0A1010.toInt(), 0xFF66FF99.toInt(), 0xFFA666F2.toInt(), 0xFF66E0FF.toInt()),
    LAGOON(R.string.palette_lagoon, 0xFF051A33.toInt(), 0xFF083A57.toInt(), 0xFF0D6B8C.toInt(), 0xFF061014.toInt(), 0xFF4DE6F2.toInt(), 0xFF8CBFFF.toInt(), 0xFF7BF5C0.toInt()),
    SAKURA(R.string.palette_sakura, 0xFF1A0A24.toInt(), 0xFF361639.toInt(), 0xFF612959.toInt(), 0xFF140A10.toInt(), 0xFFFF8CC0.toInt(), 0xFFB380FF.toInt(), 0xFFFFB38A.toInt()),
    // Warm amber measured from the honeycomb reference: dark brown edges, golden core.
    HONEY(R.string.palette_honey, 0xFF2A1F13.toInt(), 0xFF7A5420.toInt(), 0xFFB8853A.toInt(), 0xFF0D0A05.toInt(), 0xFFFFD78C.toInt(), 0xFFE8A24A.toInt(), 0xFFF08A6B.toInt()),

    // Near-black gray with dim gray accents, measured from the katakana reference recording.
    GRAPHITE(R.string.palette_graphite, 0xFF141414.toInt(), 0xFF141414.toInt(), 0xFF141414.toInt(), 0xFF000000.toInt(), 0xFF5A5A5A.toInt(), 0xFF484848.toInt(), 0xFF6E6E6E.toInt()),

    /** The user's own colors live in [AuroraSettings.custom]; the values here are only a fallback. */
    CUSTOM(R.string.palette_custom, 0xFF0A2A33.toInt(), 0xFF0F4547.toInt(), 0xFF236B5F.toInt(), 0xFF0C100D.toInt(), 0xFF59F2B3.toInt(), 0xFF40A6F2.toInt(), 0xFFB48CFF.toInt());

    val colors: PaletteColors get() = PaletteColors(deep, mid, shallow, glow, primary, secondary, tertiary)
}

/**
 * [shader] is the file name in assets/shaders. A style that is not [animated] is drawn once and
 * then costs nothing. The has* flags say which sliders make sense for the style.
 * [accents] is how many accent colors the style draws with; with the multicolor switch on it
 * uses [multicolorAccents] (more colors, spread across the picture).
 * The declaration order is the order of the chips in the picker; it is chosen so they fill
 * even rows on a phone.
 */
enum class WallpaperStyle(
    @StringRes val label: Int,
    val shader: String,
    val animated: Boolean,
    val hasSpeed: Boolean,
    val hasIntensity: Boolean,
    val hasParticles: Boolean,
    val accents: Int,
    val multicolorAccents: Int,
    /** The style can tint its areas (not only its lines). */
    val hasFill: Boolean = false,
    /** The style can react to touch (see [AuroraSettings.touchResponse]). */
    val hasTouch: Boolean = false,
    /** Text-like styles need full resolution to stay legible; the performance mode cannot go below it. */
    val minRenderScale: Float = 0f,
    /** The style draws characters and lets the user pick the [GlyphSet]. */
    val hasGlyphSets: Boolean = false,
    /** The style can take its colors from the time of day (see [AuroraSettings.timeOfDay]). */
    val hasTimeOfDay: Boolean = false,
) {
    GRADIENT(R.string.style_gradient, "gradient", animated = false, hasSpeed = false, hasIntensity = false, hasParticles = false, accents = 0, multicolorAccents = 0),
    AURORA(R.string.style_aurora, "aurora", animated = true, hasSpeed = true, hasIntensity = true, hasParticles = true, accents = 2, multicolorAccents = 3),
    WAVES(R.string.style_waves, "waves", animated = true, hasSpeed = true, hasIntensity = true, hasParticles = false, accents = 2, multicolorAccents = 3),
    BOKEH(R.string.style_bokeh, "bokeh", animated = true, hasSpeed = true, hasIntensity = true, hasParticles = true, accents = 2, multicolorAccents = 3),
    MIST(R.string.style_mist, "mist", animated = true, hasSpeed = true, hasIntensity = true, hasParticles = false, accents = 2, multicolorAccents = 3),
    CAUSTICS(R.string.style_caustics, "caustics", animated = true, hasSpeed = true, hasIntensity = true, hasParticles = false, accents = 1, multicolorAccents = 3),
    CONTOURS(R.string.style_contours, "contours", animated = true, hasSpeed = true, hasIntensity = true, hasParticles = false, accents = 1, multicolorAccents = 3, hasFill = true, hasTouch = true, hasTimeOfDay = true),
    HONEYCOMB(R.string.style_honeycomb, "honeycomb", animated = true, hasSpeed = true, hasIntensity = true, hasParticles = true, accents = 2, multicolorAccents = 3, hasFill = true, hasTouch = true, hasTimeOfDay = true),
    DUNES(R.string.style_dunes, "dunes", animated = true, hasSpeed = true, hasIntensity = true, hasParticles = false, accents = 1, multicolorAccents = 3),
    RAYS(R.string.style_rays, "rays", animated = true, hasSpeed = true, hasIntensity = true, hasParticles = true, accents = 1, multicolorAccents = 3),
    INK(R.string.style_ink, "ink", animated = true, hasSpeed = true, hasIntensity = true, hasParticles = false, accents = 2, multicolorAccents = 3),
    MOIRE(R.string.style_moire, "moire", animated = true, hasSpeed = true, hasIntensity = true, hasParticles = false, accents = 2, multicolorAccents = 3),
    KATAKANA(R.string.style_katakana, "matrix", animated = true, hasSpeed = true, hasIntensity = true, hasParticles = false, accents = 1, multicolorAccents = 1, minRenderScale = 1f, hasGlyphSets = true, hasTimeOfDay = true);

    val hasMulticolor: Boolean get() = multicolorAccents > accents
}

/**
 * The characters the Matrix style draws with, at most [GlyphAtlas.CAPACITY] per set.
 * The enum name is stored in the settings, so renaming an entry loses the user's choice.
 */
enum class GlyphSet(@StringRes val label: Int, val chars: String) {
    KATAKANA(R.string.glyphs_katakana, "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲンガギグゲゴザジズゼゾダヂヅデドバビブベボパピプペポァィゥェォッャュョー"),
    HIRAGANA(R.string.glyphs_hiragana, "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをんがぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽぁぃぅぇぉっゃゅょー"),
    HANGUL(R.string.glyphs_hangul, "가나다라마바사아자차카타파하각간갈감갑강개거건걸검고곤골공과관광구국군그근글금기김내너네노논누느단달담대더도동두드들디란람래러려로루르리만말맘매머먼메모문물미온올"),
    // The 37 bopomofo letters and the four tone marks.
    ZHUYIN(R.string.glyphs_zhuyin, "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦㄧㄨㄩˊˇˋ˙"),
}

/**
 * The battery lever. [fps] caps the frame rate, [renderScale] is the share of the screen
 * resolution the shader runs at (the result is upscaled, which is invisible on soft gradients).
 */
enum class PerformanceMode(@StringRes val label: Int, val fps: Int, val renderScale: Float) {
    ECO(R.string.mode_eco, 20, 0.35f),
    BALANCED(R.string.mode_balanced, 30, 0.5f),
    SMOOTH(R.string.mode_smooth, 60, 0.75f),
}

data class AuroraSettings(
    val style: WallpaperStyle = WallpaperStyle.AURORA,
    val palette: AuroraPalette = AuroraPalette.DEEP_SEA,
    /** Colors of the CUSTOM palette; null until the user edits a color. */
    val custom: PaletteColors? = null,
    val multicolor: Boolean = false,
    /** Tint the areas between the lines too (only styles with [WallpaperStyle.hasFill]). */
    val fillAreas: Boolean = false,
    /** Let the style react to touch (only styles with [WallpaperStyle.hasTouch]). */
    val touchResponse: Boolean = false,
    /** Take the colors from the time of day instead of the palette (only styles with [WallpaperStyle.hasTimeOfDay]). */
    val timeOfDay: Boolean = false,
    /** The user's colors per phase of the day; a phase that is not in here has its [DayPhase.defaults]. */
    val timeColors: Map<DayPhase, PaletteColors> = emptyMap(),
    val speed: Float = 1f,
    val intensity: Float = 1f,
    val particles: Float = 0.5f,
    val mode: PerformanceMode = PerformanceMode.BALANCED,
    /** Characters of the Matrix style (only styles with [WallpaperStyle.hasGlyphSets]). */
    val glyphSet: GlyphSet = GlyphSet.KATAKANA,
) {
    /** The colors in effect: the chosen palette, or the custom set. */
    val colors: PaletteColors
        get() = if (palette == AuroraPalette.CUSTOM) custom ?: AuroraPalette.CUSTOM.colors else palette.colors

    /** True when the colors follow the clock: the option is on and the style has it. */
    val timeOfDayActive: Boolean
        get() = timeOfDay && style.hasTimeOfDay

    /** The colors in effect at [minuteOfDay] (see [TimeOfDayPalette.minuteOfDayNow]). */
    fun colorsAt(minuteOfDay: Float): PaletteColors =
        if (timeOfDayActive) TimeOfDayPalette.colorsAt(minuteOfDay, timeColors) else colors

    /** The colors a phase has now: the user's, or its defaults. */
    fun phaseColors(phase: DayPhase): PaletteColors = timeColors[phase] ?: phase.defaults

    /** 1 when the multicolor look is on and the style has one, else 0 (passed to the shaders). */
    val multicolorAmount: Float
        get() = if (multicolor && style.hasMulticolor) 1f else 0f

    /** 1 when the areas are tinted and the style supports it, else 0 (passed to the shaders). */
    val fillAmount: Float
        get() = if (fillAreas && style.hasFill) 1f else 0f

    /** True when the wallpaper should listen for touches: the option is on and the style has it. */
    val touchActive: Boolean
        get() = touchResponse && style.hasTouch

    companion object {
        val SPEED_RANGE = 0.3f..2f
        val INTENSITY_RANGE = 0.3f..1.5f
        val PARTICLES_RANGE = 0f..1f
    }
}

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("aurora", Context.MODE_PRIVATE)

    fun load(): AuroraSettings {
        val d = AuroraSettings()
        return AuroraSettings(
            style = prefs.getString(KEY_STYLE, null)?.let(WallpaperStyle::valueOf) ?: d.style,
            palette = prefs.getString(KEY_PALETTE, null)?.let(AuroraPalette::valueOf) ?: d.palette,
            custom = loadCustom(),
            multicolor = prefs.getBoolean(KEY_MULTICOLOR, d.multicolor),
            fillAreas = prefs.getBoolean(KEY_FILL_AREAS, d.fillAreas),
            touchResponse = prefs.getBoolean(KEY_TOUCH_RESPONSE, d.touchResponse),
            timeOfDay = prefs.getBoolean(KEY_TIME_OF_DAY, d.timeOfDay),
            timeColors = DayPhase.entries.mapNotNull { phase ->
                loadColors(phaseKeyPrefix(phase))?.let { phase to it }
            }.toMap(),
            speed = prefs.getFloat(KEY_SPEED, d.speed).coerceIn(AuroraSettings.SPEED_RANGE),
            intensity = prefs.getFloat(KEY_INTENSITY, d.intensity).coerceIn(AuroraSettings.INTENSITY_RANGE),
            particles = prefs.getFloat(KEY_PARTICLES, d.particles).coerceIn(AuroraSettings.PARTICLES_RANGE),
            mode = prefs.getString(KEY_MODE, null)?.let(PerformanceMode::valueOf) ?: d.mode,
            glyphSet = prefs.getString(KEY_GLYPH_SET, null)?.let(GlyphSet::valueOf) ?: d.glyphSet,
        )
    }

    fun save(s: AuroraSettings) {
        val edit = prefs.edit()
            .putString(KEY_STYLE, s.style.name)
            .putString(KEY_PALETTE, s.palette.name)
            .putBoolean(KEY_MULTICOLOR, s.multicolor)
            .putBoolean(KEY_FILL_AREAS, s.fillAreas)
            .putBoolean(KEY_TOUCH_RESPONSE, s.touchResponse)
            .putBoolean(KEY_TIME_OF_DAY, s.timeOfDay)
            .putFloat(KEY_SPEED, s.speed)
            .putFloat(KEY_INTENSITY, s.intensity)
            .putFloat(KEY_PARTICLES, s.particles)
            .putString(KEY_MODE, s.mode.name)
            .putString(KEY_GLYPH_SET, s.glyphSet.name)
        val c = s.custom
        if (c != null) {
            edit.putInt(KEY_C_DEEP, c.deep).putInt(KEY_C_MID, c.mid).putInt(KEY_C_SHALLOW, c.shallow)
                .putInt(KEY_C_GLOW, c.glow).putInt(KEY_C_PRIMARY, c.primary)
                .putInt(KEY_C_SECONDARY, c.secondary).putInt(KEY_C_TERTIARY, c.tertiary)
        }
        // A phase without user colors must lose its keys, or a reset would come back on the next load.
        for (phase in DayPhase.entries) {
            val prefix = phaseKeyPrefix(phase)
            val colors = s.timeColors[phase]
            if (colors != null) putColors(edit, prefix, colors) else removeColors(edit, prefix)
        }
        edit.apply()
    }

    private fun phaseKeyPrefix(phase: DayPhase) = "tod_${phase.name.lowercase()}"

    private fun putColors(edit: SharedPreferences.Editor, prefix: String, c: PaletteColors) {
        edit.putInt("${prefix}_deep", c.deep).putInt("${prefix}_mid", c.mid).putInt("${prefix}_shallow", c.shallow)
            .putInt("${prefix}_glow", c.glow).putInt("${prefix}_primary", c.primary)
            .putInt("${prefix}_secondary", c.secondary).putInt("${prefix}_tertiary", c.tertiary)
    }

    private fun removeColors(edit: SharedPreferences.Editor, prefix: String) {
        for (part in COLOR_PARTS) edit.remove("${prefix}_$part")
    }

    private fun loadColors(prefix: String): PaletteColors? {
        if (!prefs.contains("${prefix}_shallow")) return null
        return PaletteColors(
            deep = prefs.getInt("${prefix}_deep", 0),
            mid = prefs.getInt("${prefix}_mid", 0),
            shallow = prefs.getInt("${prefix}_shallow", 0),
            glow = prefs.getInt("${prefix}_glow", 0),
            primary = prefs.getInt("${prefix}_primary", 0),
            secondary = prefs.getInt("${prefix}_secondary", 0),
            tertiary = prefs.getInt("${prefix}_tertiary", 0),
        )
    }

    private fun loadCustom(): PaletteColors? {
        if (!prefs.contains(KEY_C_SHALLOW)) return null
        return PaletteColors(
            deep = prefs.getInt(KEY_C_DEEP, 0),
            mid = prefs.getInt(KEY_C_MID, 0),
            shallow = prefs.getInt(KEY_C_SHALLOW, 0),
            glow = prefs.getInt(KEY_C_GLOW, 0),
            primary = prefs.getInt(KEY_C_PRIMARY, 0),
            secondary = prefs.getInt(KEY_C_SECONDARY, 0),
            tertiary = prefs.getInt(KEY_C_TERTIARY, 0),
        )
    }

    /** SharedPreferences keeps listeners weakly: the caller must hold on to [listener]. */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object {
        const val KEY_STYLE = "style"
        const val KEY_PALETTE = "palette"
        const val KEY_MULTICOLOR = "multicolor"
        const val KEY_FILL_AREAS = "fill_areas"
        const val KEY_TOUCH_RESPONSE = "touch_response"
        const val KEY_TIME_OF_DAY = "time_of_day"
        val COLOR_PARTS = listOf("deep", "mid", "shallow", "glow", "primary", "secondary", "tertiary")
        const val KEY_SPEED = "speed"
        const val KEY_INTENSITY = "intensity"
        const val KEY_PARTICLES = "particles"
        const val KEY_MODE = "mode"
        const val KEY_GLYPH_SET = "glyph_set"
        const val KEY_C_DEEP = "custom_deep"
        const val KEY_C_MID = "custom_mid"
        const val KEY_C_SHALLOW = "custom_shallow"
        const val KEY_C_GLOW = "custom_glow"
        const val KEY_C_PRIMARY = "custom_primary"
        const val KEY_C_SECONDARY = "custom_secondary"
        const val KEY_C_TERTIARY = "custom_tertiary"
    }
}
