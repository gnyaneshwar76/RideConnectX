package com.eshwar.rideconnectx.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.eshwar.rideconnectx.presentation.theme.Rcx

/**
 * The app's on/off switch, drawn the iOS way: a 51 x 31 pill, a white knob
 * with a soft shadow that springs across, a track that fills with colour, and
 * a knob that stretches while the finger is down. A light tick of haptics
 * confirms the change.
 */
@Composable
fun RcxSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Rcx.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val track by animateColorAsState(
        // CEO 10 Oct: the switch follows the accent / colour mix (c.blue is the accent).
        if (checked) c.blue else c.muted.copy(alpha = 0.28f),
        tween(250),
        label = "switchTrack",
    )
    val knobWidth by animateDpAsState(if (pressed) 33.dp else 27.dp, spring(stiffness = 700f), label = "switchKnobW")
    // Knob's left edge: 2dp inset; when on, it sits against the right inset.
    val knobX by animateDpAsState(
        if (checked) 51.dp - 2.dp - knobWidth else 2.dp,
        spring(dampingRatio = 0.68f, stiffness = 520f),
        label = "switchKnobX",
    )

    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .width(51.dp)
            .height(31.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null,
                onValueChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(it)
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knobX)
                .size(width = knobWidth, height = 27.dp)
                .shadow(3.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(Color.White, CircleShape)
        )
    }
}
