package com.livewall.app

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val PanelColor = Color(0xEB0A222A)

private enum class SettingsTab(@StringRes val label: Int) {
    STYLE(R.string.tab_style),
    COLORS(R.string.tab_colors),
    CUSTOM(R.string.tab_custom),
    ADJUST(R.string.tab_adjust),
    BATTERY(R.string.tab_battery),
}

@Composable
internal fun LiveWallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF59F2B3),
            onPrimary = Color(0xFF00241A),
            secondary = Color(0xFF40A6F2),
            surface = Color(0xFF0A222A),
            onSurface = Color(0xFFE3F4F0),
            onSurfaceVariant = Color(0xFF9DBDB6),
        ),
        content = content,
    )
}

/**
 * Everything above the panel is intentionally empty: the activity theme shows the live
 * wallpaper there, so every change can be judged on the real thing. Tapping that area closes
 * the open tab, which leaves only the slim tab bar over the preview. Touches there never reach
 * the wallpaper by themselves, so each one is also reported to [onPreviewTouch].
 */
@Composable
internal fun SettingsScreen(
    settings: AuroraSettings,
    wallpaperActive: Boolean,
    onSettingsChange: (AuroraSettings) -> Unit,
    onSetWallpaper: () -> Unit,
    onPreviewTouch: (Offset) -> Unit,
) {
    val currentOnPreviewTouch by rememberUpdatedState(onPreviewTouch)
    var open by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(SettingsTab.STYLE) }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    open = false
                }
                // observes only: nothing is consumed, so the click above still works
                .pointerInput(Unit) {
                    awaitEachGesture {
                        currentOnPreviewTouch(awaitFirstDown(requireUnconsumed = false).position)
                    }
                },
        )
        Surface(
            color = PanelColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.navigationBarsPadding()) {
                AnimatedVisibility(visible = open) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
                    ) {
                        when (tab) {
                            SettingsTab.STYLE -> StyleTab(settings, onSettingsChange)
                            SettingsTab.COLORS -> ColorsTab(settings, onSettingsChange)
                            SettingsTab.CUSTOM -> CustomTab(settings, onSettingsChange)
                            SettingsTab.ADJUST -> AdjustTab(settings, onSettingsChange)
                            SettingsTab.BATTERY -> BatteryTab(settings, onSettingsChange)
                        }
                    }
                }
                if (!wallpaperActive) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.not_active_hint),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onSetWallpaper) { Text(stringResource(R.string.set_wallpaper)) }
                    }
                }
                TabBar(selected = if (open) tab else null) { clicked ->
                    if (open && tab == clicked) {
                        open = false
                    } else {
                        tab = clicked
                        open = true
                    }
                }
            }
        }
    }
}

@Composable
private fun TabBar(selected: SettingsTab?, onTab: (SettingsTab) -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp)) {
        SettingsTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = isSelected, role = Role.Tab) { onTab(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(tab.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isSelected) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .width(28.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleTab(settings: AuroraSettings, onChange: (AuroraSettings) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WallpaperStyle.entries.forEach { style ->
            FilterChip(
                selected = style == settings.style,
                onClick = { onChange(settings.copy(style = style)) },
                label = { Text(stringResource(style.label)) },
            )
        }
    }
}

@Composable
private fun ColorsTab(settings: AuroraSettings, onChange: (AuroraSettings) -> Unit) {
    if (settings.timeOfDayActive) {
        TimeOfDayNote()
        return
    }
    Row(Modifier.fillMaxWidth()) {
        AuroraPalette.entries.forEach { palette ->
            val isCustom = palette == AuroraPalette.CUSTOM
            // The custom swatch shows the user's colors; until there are any it shows what is active.
            val colors = if (isCustom) settings.custom ?: settings.colors else palette.colors
            PaletteSwatch(
                modifier = Modifier.weight(1f),
                label = stringResource(palette.label),
                colors = colors,
                selected = palette == settings.palette,
                onClick = {
                    onChange(
                        if (isCustom) settings.copy(palette = palette, custom = settings.custom ?: settings.colors)
                        else settings.copy(palette = palette),
                    )
                },
            )
        }
    }
}

/**
 * Background plus the accent colors the current style uses; editing switches to the custom set.
 * With time-of-day colors on, the colors of one phase of the day are edited instead.
 */
@Composable
private fun CustomTab(settings: AuroraSettings, onChange: (AuroraSettings) -> Unit) {
    val style = settings.style
    val timeOfDay = settings.timeOfDayActive
    // Starts on the phase that is on screen right now, so the first edit is visible.
    var phase by rememberSaveable { mutableStateOf(TimeOfDayPalette.phaseAt(TimeOfDayPalette.minuteOfDayNow())) }
    val colors = if (timeOfDay) settings.phaseColors(phase) else settings.colors
    val usedAccents = if (settings.multicolor && style.hasMulticolor) style.multicolorAccents else style.accents
    var slot by rememberSaveable { mutableIntStateOf(0) }
    // The style may have changed since the slot was picked.
    val selected = if (slot <= usedAccents) slot else 0
    val accentColors = intArrayOf(colors.primary, colors.secondary, colors.tertiary)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (timeOfDay) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayPhase.entries.forEach { p ->
                    FilterChip(
                        selected = p == phase,
                        onClick = { phase = p },
                        label = { Text(stringResource(p.label), maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorSlot(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.color_background),
                color = colors.shallow,
                selected = selected == 0,
                enabled = true,
            ) { slot = 0 }
            for (i in 1..style.multicolorAccents) {
                ColorSlot(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.color_accent, i),
                    color = accentColors[i - 1],
                    selected = selected == i,
                    enabled = i <= usedAccents,
                ) { slot = i }
            }
            // keep the slots the same width regardless of how many a style offers
            repeat(3 - style.multicolorAccents) { Spacer(Modifier.weight(1f)) }
        }

        ColorPicker(
            color = if (selected == 0) colors.shallow else accentColors[selected - 1],
            resetKey = listOf(selected, if (timeOfDay) phase else null),
            onColor = { picked ->
                val edited = when (selected) {
                    0 -> colors.withBackground(picked)
                    1 -> colors.copy(primary = picked)
                    2 -> colors.copy(secondary = picked)
                    else -> colors.copy(tertiary = picked)
                }
                onChange(
                    if (timeOfDay) settings.copy(timeColors = settings.timeColors + (phase to edited))
                    else settings.copy(palette = AuroraPalette.CUSTOM, custom = edited),
                )
            },
        )

        if (timeOfDay && phase in settings.timeColors) {
            TextButton(onClick = { onChange(settings.copy(timeColors = settings.timeColors - phase)) }) {
                Text(stringResource(R.string.phase_reset))
            }
        }

        val hint = when {
            timeOfDay -> R.string.custom_time_of_day_hint
            !style.animated -> R.string.custom_static_hint
            style.hasMulticolor && !settings.multicolor -> R.string.custom_multicolor_hint
            else -> null
        }
        if (hint != null) {
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdjustTab(settings: AuroraSettings, onChange: (AuroraSettings) -> Unit) {
    val style = settings.style
    if (!style.animated) {
        Text(
            stringResource(R.string.static_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column {
        if (style.hasGlyphSets) {
            Text(stringResource(R.string.glyph_set), style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlyphSet.entries.forEach { set ->
                    FilterChip(
                        selected = set == settings.glyphSet,
                        onClick = { onChange(settings.copy(glyphSet = set)) },
                        label = { Text(stringResource(set.label)) },
                    )
                }
            }
        }
        if (style.hasSpeed) {
            CompactSlider(
                label = stringResource(R.string.speed),
                value = settings.speed,
                range = AuroraSettings.SPEED_RANGE,
                valueText = "%.1f×".format(settings.speed),
                onChange = { onChange(settings.copy(speed = it)) },
            )
        }
        if (style.hasIntensity) {
            CompactSlider(
                label = stringResource(R.string.intensity),
                value = settings.intensity,
                range = AuroraSettings.INTENSITY_RANGE,
                valueText = "${(settings.intensity * 100).roundToInt()} %",
                onChange = { onChange(settings.copy(intensity = it)) },
            )
        }
        if (style.hasParticles) {
            CompactSlider(
                label = stringResource(R.string.particles),
                value = settings.particles,
                range = AuroraSettings.PARTICLES_RANGE,
                valueText = "${(settings.particles * 100).roundToInt()} %",
                onChange = { onChange(settings.copy(particles = it)) },
            )
        }
        if (style.hasMulticolor) {
            SwitchRow(stringResource(R.string.multicolor), settings.multicolor) {
                onChange(settings.copy(multicolor = it))
            }
        }
        if (style.hasFill) {
            SwitchRow(stringResource(R.string.fill_areas), settings.fillAreas) {
                onChange(settings.copy(fillAreas = it))
            }
        }
        if (style.hasTouch) {
            SwitchRow(stringResource(R.string.touch_response), settings.touchResponse) {
                onChange(settings.copy(touchResponse = it))
            }
        }
        if (style.hasTimeOfDay) {
            SwitchRow(stringResource(R.string.time_of_day), settings.timeOfDay) {
                onChange(settings.copy(timeOfDay = it))
            }
        }
    }
}

/** Shown instead of the palette pickers while the colors follow the clock. */
@Composable
private fun TimeOfDayNote() {
    Text(
        stringResource(R.string.time_of_day_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatteryTab(settings: AuroraSettings, onChange: (AuroraSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PerformanceMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = settings.mode == mode,
                    onClick = { onChange(settings.copy(mode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, PerformanceMode.entries.size),
                ) {
                    Text(stringResource(mode.label))
                }
            }
        }
        Text(
            if (settings.style.animated) {
                stringResource(
                    R.string.mode_detail,
                    settings.mode.fps,
                    (settings.mode.renderScale * 100).roundToInt(),
                )
            } else {
                stringResource(R.string.static_note)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            stringResource(R.string.battery_saver_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CompactSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(84.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(
            valueText,
            Modifier.width(52.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaletteSwatch(
    modifier: Modifier,
    label: String,
    colors: PaletteColors,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(colors.deep), Color(colors.shallow))))
                .border(
                    BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Color(colors.primary) else Color(0x40FFFFFF)),
                    CircleShape,
                ),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ColorSlot(
    modifier: Modifier,
    label: String,
    color: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.35f)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(
                    BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color(0x40FFFFFF)),
                    CircleShape,
                ),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * Hue bar plus saturation and brightness sliders. [resetKey] changes when a different color is
 * being edited; the hue is kept locally because it is lost in the color value at zero saturation.
 */
@Composable
private fun ColorPicker(color: Int, resetKey: Any, onColor: (Int) -> Unit) {
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
    var hue by remember(resetKey) { mutableFloatStateOf(hsv[0]) }
    val saturation = hsv[1]
    val brightness = hsv[2]

    fun emit(h: Float, s: Float, v: Float) =
        onColor(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HueBar(hue = hue, label = stringResource(R.string.color_hue)) { h ->
            hue = h
            emit(h, saturation, brightness)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledSlider(Modifier.weight(1f), stringResource(R.string.color_saturation), saturation) {
                emit(hue, it, brightness)
            }
            LabeledSlider(Modifier.weight(1f), stringResource(R.string.color_brightness), brightness) {
                emit(hue, saturation, it)
            }
        }
    }
}

@Composable
private fun LabeledSlider(modifier: Modifier, label: String, value: Float, onChange: (Float) -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f)
    }
}

@Composable
private fun HueBar(hue: Float, label: String, onHue: (Float) -> Unit) {
    val stops = remember { (0..6).map { Color.hsv(it * 60f, 1f, 1f) } }
    val currentOnHue by rememberUpdatedState(onHue)
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.horizontalGradient(stops))
                .pointerInput(Unit) {
                    detectTapGestures { currentOnHue((it.x / size.width * 360f).coerceIn(0f, 360f)) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        currentOnHue((change.position.x / size.width * 360f).coerceIn(0f, 360f))
                    }
                }
                .drawBehind {
                    val r = size.height / 2f - 2.dp.toPx()
                    val x = (hue / 360f * size.width).coerceIn(r + 2.dp.toPx(), size.width - r - 2.dp.toPx())
                    drawCircle(Color.White, radius = r, center = Offset(x, size.height / 2f), style = Stroke(3.dp.toPx()))
                },
        )
    }
}
