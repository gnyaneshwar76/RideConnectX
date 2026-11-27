package com.eshwar.rideconnectx.presentation.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.domain.model.AccentColor
import com.eshwar.rideconnectx.domain.model.BackgroundStyle
import com.eshwar.rideconnectx.domain.model.ButtonStyle
import com.eshwar.rideconnectx.domain.model.DistanceUnit
import com.eshwar.rideconnectx.domain.model.FontSize
import com.eshwar.rideconnectx.domain.model.SurfaceStyle
import com.eshwar.rideconnectx.domain.model.ThemeMode
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.SegmentedRow
import com.eshwar.rideconnectx.presentation.components.SettingsDivider
import com.eshwar.rideconnectx.presentation.components.SettingsGroup
import com.eshwar.rideconnectx.presentation.components.SettingsLinkRow
import com.eshwar.rideconnectx.presentation.components.SettingsToggleRow
import com.eshwar.rideconnectx.presentation.components.contentWidth
import com.eshwar.rideconnectx.presentation.components.safeDrawingCentred
import com.eshwar.rideconnectx.presentation.theme.GlowField
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.theme.cardSurface
import com.eshwar.rideconnectx.presentation.theme.innerFill
import com.eshwar.rideconnectx.presentation.viewmodel.AppearanceViewModel
import com.eshwar.rideconnectx.presentation.viewmodel.SettingsViewModel

/**
 * Screen 22 - Appearance and units.
 *
 * One live mini-Home pinned at the top (portrait) or on the left (landscape); the
 * options scroll beside or below it. Every choice is written to DataStore and the
 * whole app, the preview included, follows at once: the preview draws with the
 * app's own tokens.
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    vm: AppearanceViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val unit by vm.distanceUnit.collectAsStateWithLifecycle()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val listState = rememberLazyListState()

    ScreenBackdrop(accent = c.cyan) {
        Column(
            Modifier
                .fillMaxHeight()
                .safeDrawingCentred()
                .contentWidth()
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            BackHeader(
                title = stringResource(R.string.settings_appearance_units),
                onBack = onBack,
                lifted = listState.canScrollBackward,
            )
            if (landscape) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, end = 12.dp, bottom = 16.dp),
                    ) { AppearancePreviewCard(vm, unit) }
                    AppearanceOptions(vm, listState, Modifier.weight(0.55f).fillMaxHeight(), 12.dp, 24.dp)
                }
            } else {
                // Scrolls inside its own cap so a very large font cannot push the options off screen.
                Column(
                    Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                ) { AppearancePreviewCard(vm, unit) }
                AppearanceOptions(vm, listState, Modifier.weight(1f).fillMaxWidth(), 24.dp, 24.dp)
            }
        }
    }
}

@Composable
private fun AppearanceOptions(
    vm: AppearanceViewModel,
    listState: LazyListState,
    modifier: Modifier,
    start: Dp,
    end: Dp,
) {
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val fontSize by vm.fontSize.collectAsStateWithLifecycle()
    val accent by vm.accentColor.collectAsStateWithLifecycle()
    val surfaceStyle by vm.surfaceStyle.collectAsStateWithLifecycle()
    val intensity by vm.glassIntensity.collectAsStateWithLifecycle()
    val buttonStyle by vm.buttonStyle.collectAsStateWithLifecycle()
    val backgroundStyle by vm.backgroundStyle.collectAsStateWithLifecycle()
    val unit by vm.distanceUnit.collectAsStateWithLifecycle()
    val settingsVm: SettingsViewModel = hiltViewModel()
    var advanced by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)

    LazyColumn(
        modifier,
        state = listState,
        contentPadding = PaddingValues(start = start, end = end, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(shape).cardSurface(shape).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OptionRow(stringResource(R.string.appearance_mode)) {
                    val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
                    SegmentedRow(
                        options = listOf(
                            stringResource(R.string.appearance_mode_light),
                            stringResource(R.string.appearance_mode_dark),
                            stringResource(R.string.appearance_mode_phone),
                        ),
                        selected = modes.indexOf(theme),
                        onSelect = { vm.setThemeMode(modes[it]) },
                    )
                }
                OptionRow(stringResource(R.string.appearance_colour), accent.label) {
                    // Also clears any mix saved by an older version; the theme ignores mixes.
                    ColourDots(accent) { vm.setAccentColor(it); vm.setColourMix(null) }
                }
                OptionRow(stringResource(R.string.settings_units)) {
                    val units = DistanceUnit.entries
                    SegmentedRow(
                        options = units.map { it.short },
                        selected = unit.ordinal,
                        onSelect = { settingsVm.setDistanceUnit(units[it]) },
                    )
                }
                OptionRow(stringResource(R.string.appearance_text_size)) {
                    val sizes = FontSize.entries
                    SegmentedRow(
                        options = listOf(
                            stringResource(R.string.appearance_size_s),
                            stringResource(R.string.appearance_size_m),
                            stringResource(R.string.appearance_size_l),
                        ),
                        selected = sizes.indexOf(fontSize),
                        onSelect = { vm.setFontSize(sizes[it]) },
                    )
                }
            }
        }

        item {
            SettingsGroup(stringResource(R.string.appearance_advanced), Icons.Filled.Tune) {
                SettingsLinkRow(
                    title = stringResource(R.string.appearance_advanced_title),
                    subtitle = stringResource(R.string.appearance_advanced_sub),
                    value = stringResource(if (advanced) R.string.appearance_hide else R.string.appearance_show),
                    chevron = false,
                    onClick = { advanced = !advanced },
                )
                if (advanced) {
                    SettingsToggleRow(
                        title = stringResource(R.string.appearance_glass_cards),
                        subtitle = "",
                        checked = surfaceStyle == SurfaceStyle.GLASS,
                        onCheckedChange = { vm.setSurfaceStyle(if (it) SurfaceStyle.GLASS else SurfaceStyle.FLAT) },
                    )
                    // Only meaningful for glass, so it appears with it.
                    AnimatedVisibility(surfaceStyle == SurfaceStyle.GLASS) {
                        GlassIntensityRow(intensity, vm::previewGlassIntensity, vm::setGlassIntensity)
                    }
                    SettingsToggleRow(
                        title = stringResource(R.string.appearance_glass_buttons),
                        subtitle = "",
                        checked = buttonStyle == ButtonStyle.GLASS,
                        onCheckedChange = { vm.setButtonStyle(if (it) ButtonStyle.GLASS else ButtonStyle.SOLID) },
                    )
                    OptionRow(stringResource(R.string.appearance_background)) {
                        val styles = BackgroundStyle.entries
                        SegmentedRow(
                            options = listOf(
                                stringResource(R.string.appearance_bg_photo),
                                stringResource(R.string.appearance_bg_glow),
                            ),
                            selected = styles.indexOf(backgroundStyle),
                            onSelect = { vm.setBackgroundStyle(styles[it]) },
                        )
                    }
                }
            }
        }
    }
}

/** A title (and the chosen value, right) over one full-width control. */
@Composable
private fun OptionRow(title: String, value: String? = null, content: @Composable () -> Unit) {
    val c = Rcx.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = RcxType.Label.copy(fontSize = 14.sp), color = c.text, modifier = Modifier.weight(1f))
            if (value != null) Text(value, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted)
        }
        content()
    }
}

/** The palettes as one row of 48 dp dots: ring on the chosen one, name read by the screen reader. */
@Composable
private fun ColourDots(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    val c = Rcx.colors
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AccentColor.entries.forEach { accent ->
            val active = accent == selected
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(accent) })
                    .semantics { contentDescription = accent.label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(44.dp).then(if (active) Modifier.border(2.dp, c.text, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(accent.argb)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = Color.White)
                    }
                }
            }
        }
    }
}

/** Slider for how strong the glass is: blur, tint, rim and background light. */
@Composable
private fun GlassIntensityRow(value: Float, onPreview: (Float) -> Unit, onChange: (Float) -> Unit) {
    val c = Rcx.colors
    // The app follows the finger live (onPreview, memory only); the value is
    // saved once on release, so storage is not written every frame.
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        SettingsDivider()
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.appearance_glass_intensity), style = RcxType.Label.copy(fontSize = 14.sp), color = c.text, modifier = Modifier.weight(1f))
            Text(
                stringResource(
                    when {
                        local < 0.34f -> R.string.appearance_glass_subtle
                        local < 0.67f -> R.string.appearance_glass_balanced
                        else -> R.string.appearance_glass_strong
                    }
                ),
                style = RcxType.Mono.copy(fontSize = 12.sp),
                color = c.muted,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it; onPreview(it) },
            onValueChangeFinished = { onChange(local) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = c.blue,
                inactiveTrackColor = c.muted.copy(alpha = 0.4f),
            ),
        )
    }
}

/** Mini Home: hero strip, 2x2 tiles, one button. Decorative, so one line for the screen reader. */
@Composable
private fun AppearancePreviewCard(vm: AppearanceViewModel, unit: DistanceUnit) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(22.dp)
    val bg by vm.backgroundStyle.collectAsStateWithLifecycle()
    val desc = stringResource(R.string.appearance_preview_desc)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardSurface(shape)
            .padding(12.dp)
            .clearAndSetSemantics { contentDescription = desc },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val heroShape = RoundedCornerShape(12.dp)
        Box(Modifier.fillMaxWidth().height(88.dp).clip(heroShape).background(c.bg)) {
            if (bg == BackgroundStyle.GLOW) {
                GlowField()
            } else {
                Image(
                    painterResource(R.drawable.img_vehicle_access_125), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit, // N139: whole scooter, not a strip
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewTile(stringResource(R.string.dash_odo), unit.format(1602), Modifier.weight(1f))
            PreviewTile(stringResource(R.string.dash_fuel), "4/5", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewTile(stringResource(R.string.dash_trip_a), "%.1f %s".format(unit.fromKm(12.4f), unit.short), Modifier.weight(1f))
            PreviewTile(stringResource(R.string.dash_trip_b), "%.1f %s".format(unit.fromKm(48.0f), unit.short), Modifier.weight(1f))
        }
        // The rider's colour and button style, exactly as every main button draws it.
        PrimaryButton(stringResource(R.string.appearance_preview_button), onClick = {}, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PreviewTile(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .innerFill()
            .border(1.dp, c.border, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, style = RcxType.MonoTiny.copy(fontSize = 12.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = RcxType.Mono.copy(fontSize = 14.sp), color = c.text, maxLines = 1)
    }
}
