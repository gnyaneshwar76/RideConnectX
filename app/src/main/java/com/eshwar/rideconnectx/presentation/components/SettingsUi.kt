package com.eshwar.rideconnectx.presentation.components

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.selection.toggleable

import com.eshwar.rideconnectx.presentation.theme.cardSurface
import com.eshwar.rideconnectx.presentation.theme.enterRise
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType

/**
 * The grouped-card settings vocabulary from the design, shared by Settings,
 * Appearance, Profile and About so all four look like one screen family.
 *
 * The design's rule: settings are grouped into cards, rows are 12dp apart, and
 * a toggle saves the moment it moves.
 */

/** A titled group of rows, drawn as one rounded card. */
@Composable
fun SettingsGroup(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().enterRise()) {
        Row(
            Modifier.padding(start = 2.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(15.dp), tint = c.blue)
            Text(title, style = RcxType.Section.copy(fontSize = 16.sp), color = c.text, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .cardSurface(shape)
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

/** A row with a switch. [enabled] false dims it and blocks the toggle. */
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Rcx.colors
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            // N37 row 14: title, subtitle and switch state read as one item.
            // The whole row is the switch, so TalkBack says "<title>, <subtitle>, on, switch"
            // (the inner switch's own toggleable would otherwise be a second, unlabeled stop).
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = RcxType.Label.copy(fontSize = 14.sp), color = c.text)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted)
            }
        }
        RcxSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * A row that leads somewhere, or reports a value.
 *
 * @param value trailing text, e.g. the current units. Omit for a plain link.
 * @param chevron false for rows that only report — a chevron on something
 *        that does not open is a lie about what a tap will do.
 */
@Composable
fun SettingsLinkRow(
    title: String,
    subtitle: String = "",
    value: String = "",
    modifier: Modifier = Modifier,
    accent: Color? = null,
    enabled: Boolean = true,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = Rcx.colors
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .then(
                if (enabled && onClick != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick) else Modifier
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = RcxType.Label.copy(fontSize = 14.sp),
                color = accent ?: c.text,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted)
            }
        }
        if (value.isNotBlank()) {
            Text(value, style = RcxType.Mono.copy(fontSize = 12.sp), color = c.cyan)
        }
        if (chevron && onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(15.dp),
                tint = c.muted.copy(alpha = 0.44f),
            )
        }
    }
}

/** Hairline between rows inside a group. */
@Composable
fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(Rcx.colors.border)
    )
}

/**
 * A radio-style choice, used by Appearance for theme and font size.
 * The design calls for exactly one selection per group.
 */
@Composable
fun SettingsChoiceRow(
    title: String,
    subtitle: String = "",
    icon: ImageVector? = null,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Rcx.colors
    Row(
        modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background((if (selected) c.blue else c.muted).copy(alpha = 0.094f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(16.dp), tint = if (selected) c.blue else c.muted)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = RcxType.Label.copy(fontSize = 14.sp), color = c.text)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted)
            }
        }
        // Radio drawn by hand so it carries the RCX blue rather than M3's.
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) c.blue.copy(alpha = 0.13f) else Color.Transparent)
                .border(
                    1.5.dp,
                    if (selected) c.blue else c.muted.copy(alpha = 0.4f),
                    RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(c.blue))
            }
        }
    }
}
