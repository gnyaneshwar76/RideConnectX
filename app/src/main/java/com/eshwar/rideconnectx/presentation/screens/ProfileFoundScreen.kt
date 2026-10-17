package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.theme.cardFill
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.RiderAvatar
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.viewmodel.ProfileFoundViewModel

/**
 * "Add your guest data to this account?"
 *
 * Shown when a guest signs into an account that already has a profile. The
 * guest's rides, service records and emergency contacts used to be merged into
 * it without a word (rider, 26 Sep). Nothing moves until the rider answers:
 * add them to this account, or go back and choose another one.
 */
@Composable
fun ProfileFoundScreen(
    onAdded: (profileDone: Boolean) -> Unit,
    onChooseAnother: () -> Unit,
    vm: ProfileFoundViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val q by vm.question.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (c.isDark) listOf(Color(0xFF070D1B), Color(0xFF0C1428))
                    else listOf(Color(0xFFE4EEFF), Color(0xFFEEF2FF))
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    tint = c.blue,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Add your guest data to this account?",
                    style = RcxType.Wordmark.copy(fontSize = 20.sp),
                    color = c.text,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "This account already has a rider profile. Adding moves the rides, " +
                    "service records and emergency contacts you saved as " +
                    "${q.guestName.ifBlank { "a guest" }} into it, and keeps the " +
                    "account's own profile. Or choose another account.",
                style = RcxType.Body.copy(fontSize = 14.sp),
                color = c.muted,
            )

            Spacer(Modifier.height(24.dp))

            // ── The account being signed into ────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .cardFill()
                    .border(1.dp, c.blue.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RiderAvatar(name = q.accountName, size = 52.dp, accent = c.blue)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        q.accountName.ifBlank { "Your account" },
                        style = RcxType.Wordmark.copy(fontSize = 18.sp),
                        color = c.text,
                    )
                    if (q.accountEmail.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            q.accountEmail,
                            style = RcxType.Body.copy(fontSize = 13.sp),
                            color = c.muted,
                        )
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = RcxType.Body.copy(fontSize = 13.sp), color = c.red)
            }

            Spacer(Modifier.height(28.dp))

            PrimaryButton(
                label = if (busy) "Adding…" else "Add to this account",
                onClick = { vm.add(onAdded) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))

            TextButton(
                onClick = { vm.chooseAnother(onChooseAnother) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Choose another account",
                    style = RcxType.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    color = c.muted,
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Your guest data stays on this phone until you decide.",
                style = RcxType.Body.copy(fontSize = 12.sp),
                color = c.muted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
