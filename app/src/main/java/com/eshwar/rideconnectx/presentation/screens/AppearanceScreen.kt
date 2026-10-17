package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.theme.innerFill
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.theme.cardSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.domain.model.AccentColor
import com.eshwar.rideconnectx.domain.model.ButtonStyle
import com.eshwar.rideconnectx.domain.model.ColourMix
import com.eshwar.rideconnectx.presentation.theme.soft
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.text.style.TextOverflow
import com.eshwar.rideconnectx.domain.model.FontSize
import com.eshwar.rideconnectx.domain.model.ThemeMode
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.SettingsChoiceRow
import com.eshwar.rideconnectx.presentation.components.SettingsDivider
import com.eshwar.rideconnectx.presentation.components.SettingsGroup
import androidx.compose.material.icons.filled.Layers
import com.eshwar.rideconnectx.domain.model.SurfaceStyle
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.viewmodel.AppearanceViewModel

/**
 * Screen 22 — Appearance.
 *
 * Theme, accent colour and text size, all applied instantly with no restart:
 * the choice is written to DataStore, `MainActivity` observes it, and the whole
 * tree recomposes. The accent replaces the brand blue in the colour tokens, so
 * every screen in the app follows it.
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    vm: AppearanceViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val fontSize by vm.fontSize.collectAsStateWithLifecycle()
    val accent by vm.accentColor.collectAsStateWithLifecycle()
    val surfaceStyle by vm.surfaceStyle.collectAsStateWithLifecycle()
    val intensity by vm.glassIntensity.collectAsStateWithLifecycle()
    val colourMix by vm.colourMix.collectAsStateWithLifecycle()
    val buttonStyle by vm.buttonStyle.collectAsStateWithLifecycle()
    val backgroundStyle by vm.backgroundStyle.collectAsStateWithLifecycle()

    ScreenBackdrop(accent = c.cyan) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val headerList = androidx.compose.foundation.lazy.rememberLazyListState()
            BackHeader(title = stringResource(R.string.settings_appearance), onBack = onBack, lifted = headerList.canScrollBackward)

            LazyColumn(
                state = headerList,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item { AppearancePreviewCard() }

                item {
                    SettingsGroup(stringResource(R.string.appearance_theme), Icons.Filled.Palette) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            if (index > 0) SettingsDivider()
                            SettingsChoiceRow(
                                title = mode.label,
                                subtitle = when (mode) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.appearance_follow_phone)
                                    ThemeMode.LIGHT -> stringResource(R.string.appearance_always_light)
                                    ThemeMode.DARK -> stringResource(R.string.appearance_always_dark)
                                },
                                icon = when (mode) {
                                    ThemeMode.SYSTEM -> Icons.Filled.PhoneAndroid
                                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                                    ThemeMode.DARK -> Icons.Filled.DarkMode
                                },
                                selected = theme == mode,
                                onSelect = { vm.setThemeMode(mode) },
                            )
                        }
                    }
                }

                item {
                    SettingsGroup(stringResource(R.string.appearance_text_size), Icons.Filled.FormatSize) {
                        FontSize.entries.forEachIndexed { index, size ->
                            if (index > 0) SettingsDivider()
                            SettingsChoiceRow(
                                title = size.label,
                                subtitle = if (size == FontSize.MEDIUM) stringResource(R.string.appearance_default) else "",
                                selected = fontSize == size,
                                onSelect = { vm.setFontSize(size) },
                            )
                        }
                    }
                }

                item {
                    SettingsGroup(
                        stringResource(R.string.appearance_surface),
                        Icons.Filled.Layers,
                    ) {
                        SurfaceStyle.entries.forEachIndexed { index, style ->
                            if (index > 0) SettingsDivider()
                            SettingsChoiceRow(
                                title = style.label,
                                subtitle = when (style) {
                                    SurfaceStyle.FLAT ->
                                        stringResource(R.string.appearance_surface_flat_sub)
                                    SurfaceStyle.GLASS ->
                                        stringResource(R.string.appearance_surface_glass_sub)
                                },
                                selected = surfaceStyle == style,
                                onSelect = { vm.setSurfaceStyle(style) },
                            )
                        }
                        // Only meaningful for glass, so it appears with it.
                        AnimatedVisibility(surfaceStyle == SurfaceStyle.GLASS) {
                            GlassIntensityRow(intensity, vm::previewGlassIntensity, vm::setGlassIntensity)
                        }
                    }
                }

                // Plan b job 1: Photo (default) or Glow behind the screens.
                item {
                    SettingsGroup("Background", Icons.Filled.Palette) {
                        com.eshwar.rideconnectx.domain.model.BackgroundStyle.entries.forEachIndexed { index, style ->
                            if (index > 0) SettingsDivider()
                            SettingsChoiceRow(
                                title = if (style == com.eshwar.rideconnectx.domain.model.BackgroundStyle.PHOTO) "Photo" else "Glow",
                                subtitle = if (style == com.eshwar.rideconnectx.domain.model.BackgroundStyle.PHOTO)
                                    "A soft picture behind each screen"
                                else "Three soft lights in your colours, drifting slowly. Still in Battery Saver",
                                selected = backgroundStyle == style,
                                onSelect = { vm.setBackgroundStyle(style) },
                            )
                        }
                    }
                }

                item {
                    AccentColourSection(
                        selected = accent,
                        // An accent tap is a single colour again, so it clears the mix.
                        onSelect = { vm.setAccentColor(it); vm.setColourMix(null) },
                        mixChosen = colourMix != null,
                    )
                }

                item { ColourMixSection(selected = colourMix, onSelect = { vm.setColourMix(it) }) }

                item {
                    SettingsGroup(stringResource(R.string.appearance_button_style), Icons.Filled.Palette) {
                        ButtonStyle.entries.forEachIndexed { index, style ->
                            if (index > 0) SettingsDivider()
                            SettingsChoiceRow(
                                title = when (style) {
                                    ButtonStyle.SOLID -> stringResource(R.string.appearance_button_solid)
                                    ButtonStyle.GLASS -> stringResource(R.string.appearance_button_glass)
                                    ButtonStyle.OUTLINE -> stringResource(R.string.appearance_button_outline)
                                },
                                subtitle = when (style) {
                                    ButtonStyle.SOLID -> stringResource(R.string.appearance_button_solid_sub)
                                    ButtonStyle.GLASS -> stringResource(R.string.appearance_button_glass_sub)
                                    ButtonStyle.OUTLINE -> stringResource(R.string.appearance_button_outline_sub)
                                },
                                selected = buttonStyle == style,
                                onSelect = { vm.setButtonStyle(style) },
                            )
                        }
                    }
                }

                item {
                    Text(
                        // The design asks for accessibility scaling to be
                        // preserved, and this is how it behaves in practice.
                        "Text size multiplies your phone's own accessibility setting " +
                            "rather than replacing it.",
                        style = RcxType.BodySmall.copy(fontSize = 12.sp),
                        color = c.muted,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
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
            Text("Glass intensity", style = RcxType.Label.copy(fontSize = 14.sp), color = c.text, modifier = Modifier.weight(1f))
            Text(
                when {
                    local < 0.34f -> "Subtle"
                    local < 0.67f -> "Balanced"
                    else -> "Strong"
                },
                style = RcxType.Mono.copy(fontSize = 11.sp),
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
                inactiveTrackColor = c.muted.copy(alpha = 0.25f),
            ),
        )
    }
}

/**
 * Live preview — the same tokens every other screen uses, so what is shown here
 * is literally what the app will look like.
 */
@Composable
private fun AppearancePreviewCard() {
    val c = Rcx.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardSurface(shape)
            .padding(18.dp),
    ) {
        Text(stringResource(R.string.appearance_preview), style = RcxType.MonoTiny, color = c.muted)
        Spacer(Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(c.blue, c.cyan))),
            )
            Column(Modifier.weight(1f)) {
                Text("RideConnectX", style = RcxType.Wordmark.copy(fontSize = 17.sp), color = c.text)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.appearance_preview_body),
                    style = RcxType.BodySmall.copy(fontSize = 13.sp),
                    color = c.muted,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PreviewTile("ODO", "1,602 km", c.cyan, Modifier.weight(1f))
            PreviewTile("STATUS", "Connected", c.green, Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        // The rider's colour mix and button style, exactly as every main button draws it.
        com.eshwar.rideconnectx.presentation.components.PrimaryButton(
            stringResource(R.string.appearance_preview_button), onClick = {}, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PreviewTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .innerFill()
            .border(1.dp, c.border, shape)
            .padding(12.dp),
    ) {
        Text(label, style = RcxType.MonoTiny, color = c.muted)
        Spacer(Modifier.height(6.dp))
        Text(value, style = RcxType.Mono.copy(fontSize = 13.sp), color = accent)
    }
}

/**
 * Accent colour — a real picker now.
 *
 * Every swatch re-tints the whole app the instant it is tapped: the chosen
 * accent replaces the brand blue in the colour tokens, so every screen, border
 * and glow follows. Each accent carries a dark and a light value, because a hue
 * that reads on the navy background is usually too pale on the light one.
 */
@Composable
private fun AccentColourSection(
    selected: AccentColor,
    onSelect: (AccentColor) -> Unit,
    mixChosen: Boolean = false,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(20.dp)
    val dark = c.isDark

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 2.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Palette, null, Modifier.size(15.dp), tint = c.blue)
            Text(stringResource(R.string.appearance_accent), style = RcxType.Section.copy(fontSize = 16.sp), color = c.text)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .cardSurface(shape)
                .padding(16.dp),
        ) {
            // Two rows of four, so every swatch stays a comfortable target.
            AccentColor.entries.chunked(4).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { accent ->
                        val swatch = Color(if (dark) accent.darkArgb else accent.lightArgb)
                        val active = accent == selected && !mixChosen
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (active) 3.dp else 1.dp,
                                    color = if (active) c.text else c.border,
                                    shape = CircleShape,
                                )
                                .semantics { this.selected = active; contentDescription = accent.label }
                                .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClickLabel = accent.label) { onSelect(accent) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (active) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                    // Keeps a short final row aligned with the one above it.
                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                }
            }

            Text(
                "${selected.label} — applied everywhere, straight away. " +
                    "Blue is the RideConnectX brand colour.",
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = c.muted,
            )
        }
    }
}

/**
 * Colour mix: 12 gradients of 2 to 4 colours, each softened toward slate (the
 * owner asked for soft, not bright). The chosen mix fills every main button
 * and its first colour becomes the accent.
 */
@Composable
private fun ColourMixSection(
    selected: ColourMix?,
    onSelect: (ColourMix) -> Unit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 2.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Palette, null, Modifier.size(15.dp), tint = c.blue)
            Text(stringResource(R.string.appearance_colour_mix), style = RcxType.Section.copy(fontSize = 16.sp), color = c.text)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .cardSurface(shape)
                .padding(16.dp),
        ) {
            ColourMix.entries.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { mix ->
                        val active = mix == selected
                        Column(
                            Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .semantics { this.selected = active }
                                .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClickLabel = mix.label) { onSelect(mix) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(mix.colours.map { Color(soft(it)) }))
                                    .border(
                                        width = if (active) 3.dp else 1.dp,
                                        color = if (active) c.text else c.border,
                                        shape = RoundedCornerShape(12.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                // Soft fills are light: black reads on all 12 (see ColourMathTest).
                                if (active) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = Color.Black)
                            }
                            Text(
                                mix.label,
                                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                                color = if (active) c.text else c.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            Text(
                if (selected == null) stringResource(R.string.appearance_colour_mix_none)
                else stringResource(R.string.appearance_colour_mix_on, selected.label),
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = c.muted,
            )
        }
    }
}
