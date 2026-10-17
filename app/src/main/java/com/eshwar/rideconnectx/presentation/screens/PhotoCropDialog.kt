package com.eshwar.rideconnectx.presentation.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import kotlin.math.max
import kotlin.math.min

/**
 * Lets the rider frame their own avatar.
 *
 * A plain centre-crop is a guess about what matters in a photo — it keeps the
 * middle, which is wrong for most pictures of people. This shows the circle the
 * avatar will actually be, and lets them drag and pinch until the right part is
 * inside it. What they see in the circle is exactly what gets saved.
 *
 * @param source the picked image, in whatever format the phone produced
 * @param onConfirm receives the transform: zoom, pan (preview px) and the
 *        circle's diameter in px, which is everything the cropper needs
 */
@Composable
fun PhotoCropDialog(
    source: Uri,
    decode: suspend (Uri) -> Bitmap?,
    onCancel: () -> Unit,
    onConfirm: (scale: Float, offsetX: Float, offsetY: Float, viewportPx: Int) -> Unit,
) {
    val c = Rcx.colors

    var image by remember(source) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(source) { mutableStateOf(false) }

    var scale by remember(source) { mutableFloatStateOf(1f) }
    var offsetX by remember(source) { mutableFloatStateOf(0f) }
    var offsetY by remember(source) { mutableFloatStateOf(0f) }
    var viewportPx by remember { mutableStateOf(0) }

    LaunchedEffect(source) {
        val bmp = decode(source)
        if (bmp == null) failed = true else image = bmp.asImageBitmap()
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF07090F)),
        ) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(28.dp))
                Text("Position your photo", style = RcxType.Wordmark.copy(fontSize = 20.sp), color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Drag to move, pinch to zoom. Whatever sits inside the circle is your picture.",
                    style = RcxType.BodySmall.copy(fontSize = 13.sp),
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        failed -> Text(
                            "That image couldn't be opened. Try a different one.",
                            style = RcxType.Body.copy(fontSize = 14.sp),
                            color = c.amber,
                            textAlign = TextAlign.Center,
                        )

                        image == null -> CircularProgressIndicator(color = c.blue)

                        else -> {
                            val bmp = image!!
                            val density = LocalDensity.current
                            val circle = with(density) { 300.dp.toPx() }.toInt()

                            Box(
                                Modifier
                                    .size(with(density) { circle.toDp() })
                                    .onSizeChanged { viewportPx = min(it.width, it.height) }
                                    .pointerInput(source) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            // Never let it shrink past the circle,
                                            // or there would be empty space inside
                                            // the avatar.
                                            scale = (scale * zoom).coerceIn(1f, 6f)
                                            offsetX += pan.x
                                            offsetY += pan.y

                                            val limit = bound(bmp, scale, viewportPx)
                                            offsetX = offsetX.coerceIn(-limit.width, limit.width)
                                            offsetY = offsetY.coerceIn(-limit.height, limit.height)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = bmp,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offsetX
                                            translationY = offsetY
                                        },
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                )

                                // Everything outside the circle is dimmed, so the
                                // framing is unambiguous.
                                // BlendMode.Clear only punches a hole when the
                                // canvas has its own compositing layer, hence
                                // the alpha nudge — without it the dim covers
                                // the circle too and nothing is visible.
                                androidx.compose.foundation.Canvas(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = 0.99f }
                                ) {
                                    drawCircleMask(c.blue)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PrimaryButton(
                        label = "Cancel",
                        onClick = onCancel,
                        secondary = true,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        label = "Use photo",
                        onClick = { onConfirm(scale, offsetX, offsetY, viewportPx) },
                        enabled = image != null && viewportPx > 0,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * How far the image may be panned before its edge would enter the circle.
 * Keeps the avatar full-bleed no matter how the rider drags it.
 */
private fun bound(bitmap: ImageBitmap, scale: Float, viewportPx: Int): Size {
    if (viewportPx == 0) return Size.Zero

    val fit = min(
        viewportPx.toFloat() / bitmap.width,
        viewportPx.toFloat() / bitmap.height,
    )
    val drawnW = bitmap.width * fit * scale
    val drawnH = bitmap.height * fit * scale

    return Size(
        max(0f, (drawnW - viewportPx) / 2f),
        max(0f, (drawnH - viewportPx) / 2f),
    )
}

/** Dims everything outside the avatar circle and rings it. */
private fun DrawScope.drawCircleMask(ring: Color) {
    val radius = size.minDimension / 2f
    val centre = Offset(size.width / 2f, size.height / 2f)

    drawRect(Color.Black.copy(alpha = 0.55f))
    drawCircle(Color.Transparent, radius, centre, blendMode = BlendMode.Clear)
    drawCircle(ring, radius, centre, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
}
