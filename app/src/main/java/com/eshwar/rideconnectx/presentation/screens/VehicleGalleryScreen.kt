package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.theme.cardFill
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eshwar.rideconnectx.domain.model.Vehicle
import com.eshwar.rideconnectx.domain.model.VehicleCatalog
import com.eshwar.rideconnectx.presentation.components.VehicleArtwork
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType

/**
 * Preview of the supported line-up and its artwork.
 *
 * Screens 11 and 12 are being designed in Figma, so this stands in meanwhile —
 * it exercises the real catalogue and the real vehicle art on device.
 */
@Composable
fun VehicleGalleryScreen(onContinue: () -> Unit = {}) {
    val c = Rcx.colors

    ScreenBackdrop(photo = R.drawable.img_welcome_hero, accent = c.blue) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Text("Select Your Vehicle", style = RcxType.Wordmark.copy(fontSize = 20.sp), color = c.text)
                    Text(
                        "${VehicleCatalog.all.size} Ride Connect models",
                        style = RcxType.BodySmall.copy(fontSize = 12.sp),
                        color = c.muted,
                    )
                }
            }

            item { SectionLabel("SCOOTERS") }
            items(VehicleCatalog.scooters, key = { it.id }) { VehicleCard(it) }

            item { SectionLabel("MOTORCYCLES") }
            items(VehicleCatalog.motorcycles, key = { it.id }) { VehicleCard(it) }

            item {
                com.eshwar.rideconnectx.presentation.components.PrimaryButton(
                    label = "Continue to Dashboard",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = RcxType.MonoTiny.copy(fontSize = 10.sp),
        color = Rcx.colors.muted,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun VehicleCard(vehicle: Vehicle) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(20.dp)
    // Preview each model in its first factory colour.
    val paint = vehicle.colors.first()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardFill()
            .border(1.5.dp, c.border, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(vehicle.name, style = RcxType.Label.copy(fontSize = 15.sp), color = c.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (vehicle.tag.isNotEmpty()) {
                        Text(
                            vehicle.tag.uppercase(),
                            style = RcxType.Mono.copy(fontSize = 9.sp),
                            color = vehicle.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(vehicle.accent.copy(alpha = 0.10f))
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(vehicle.subtitle, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted)
            }
        }

        VehicleArtwork(
            vehicle = vehicle,
            body = paint.primary,
            outline = vehicle.accent,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(vertical = 8.dp),
        )

        // Factory paint swatches; dual-tone shades are split down the middle.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vehicle.colors.forEach { colour ->
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (colour.secondary == null) Brush.linearGradient(listOf(colour.primary, colour.primary))
                            else Brush.linearGradient(
                                0f to colour.primary, 0.5f to colour.primary,
                                0.5f to colour.secondary, 1f to colour.secondary,
                            )
                        )
                        .border(1.dp, c.muted.copy(alpha = 0.35f), CircleShape)
                )
            }
        }
    }
}
