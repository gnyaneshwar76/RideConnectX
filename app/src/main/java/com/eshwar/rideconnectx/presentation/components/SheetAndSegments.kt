package com.eshwar.rideconnectx.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eshwar.rideconnectx.presentation.theme.GlassSheetWindow
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.theme.sheetEnter

/**
 * Body of a bottom sheet that never hides its buttons: [content] scrolls (rotation,
 * big font, keyboard), [buttons] stay pinned under it, two buttons side by side, as
 * [BottomActions]. Use inside a `ModalBottomSheet`.
 */
@Composable
fun SheetScaffold(
    buttons: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.sheetEnter()) {
        GlassSheetWindow()
        Column(Modifier.padding(horizontal = 24.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
            BottomActions(Modifier.padding(bottom = 20.dp), horizontalPadding = 0.dp, content = buttons)
        }
    }
}

/** One choice out of a few, side by side (Settings pages). 48 dp tall, selected one tinted and bold. */
@Composable
fun SegmentedRow(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .border(1.dp, c.border, shape)
            .selectableGroup(),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (on) c.blue.copy(alpha = 0.18f) else Color.Transparent)
                    .selectable(selected = on, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(i) })
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = RcxType.Label.copy(fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                    color = if (on) c.blue else c.text,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
