package com.eshwar.rideconnectx.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType

/**
 * The app rule for action buttons: pinned at the bottom of the screen. Exactly two
 * buttons sit side by side in ONE row (equal width, 48 dp high). They stack instead
 * when the font scale is 1.3 or more, when a label would wrap, or for 1 or 3+ items.
 * Put the page content in a `Modifier.weight(1f)` scroller above this. Order: main
 * action first (left), red last. Use [PrimaryButton] and [DangerButton] with
 * `Modifier.fillMaxWidth()`. Set [sideBySide] false when a child is not a button.
 */
@Composable
fun BottomActions(
    modifier: Modifier = Modifier,
    sideBySide: Boolean = true,
    horizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    val bigFont = LocalDensity.current.fontScale >= 1.3f
    Layout(
        content = content,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
    ) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val w = constraints.maxWidth
        val free = Constraints(minWidth = w, maxWidth = w, maxHeight = Constraints.Infinity)
        // Decide first with intrinsic heights (no measure), then measure each child exactly once.
        val half = (w - gap) / 2
        val row = rowFits(sideBySide, bigFont, measurables.size) {
            measurables.all { it.minIntrinsicHeight(half) <= 52.dp.roundToPx() }
        }
        val items = measurables.map {
            it.measure(if (row) Constraints.fixedWidth(half).copy(maxHeight = Constraints.Infinity) else free)
        }
        if (row) {
            val h = items.maxOf { it.height }
            layout(w, h) {
                items.forEachIndexed { i, p -> p.placeRelative(i * (items[0].width + gap), (h - p.height) / 2) }
            }
        } else {
            val h = items.sumOf { it.height } + gap * (items.size - 1).coerceAtLeast(0)
            layout(w, h) {
                var y = 0
                items.forEach { it.placeRelative(0, y); y += it.height + gap }
            }
        }
    }
}

/** Side by side only for exactly 2 buttons, normal font, and when no label wraps. Pure, for tests. */
internal fun rowFits(sideBySide: Boolean, bigFont: Boolean, count: Int, noWrap: () -> Boolean): Boolean =
    sideBySide && !bigFont && count == 2 && noWrap()

/** Red button for an action that removes or forgets something. Same size as [PrimaryButton]. */
@Composable
fun DangerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.6f)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(c.red.copy(alpha = 0.094f))
            .border(1.5.dp, c.red.copy(alpha = 0.31f), shape)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = RcxType.Button.copy(fontWeight = FontWeight.SemiBold),
            color = c.red,
            textAlign = TextAlign.Center,
        )
    }
}
