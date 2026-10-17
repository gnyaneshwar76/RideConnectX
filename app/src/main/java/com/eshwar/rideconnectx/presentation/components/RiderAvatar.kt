package com.eshwar.rideconnectx.presentation.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eshwar.rideconnectx.core.util.ProfilePhotoStore
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The rider's picture, wherever the rider appears.
 *
 * This used to live as a private helper inside the Profile screen, which is
 * exactly why the picture showed up there and nowhere else — not on the
 * dashboard greeting, not on the Settings account card, not on Account Found.
 * One composable, used by all of them.
 *
 * Falls back to the rider's initial, then to a person glyph, so the circle is
 * never empty.
 */
@Composable
fun RiderAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    accent: Color? = null,
    ring: Boolean = true,
    /**
     * Bump to force a re-read after the file changes. The path never changes,
     * and the modification-time key alone can miss a save made in the same
     * second as the previous one.
     */
    version: Int = 0,
) {
    val c = Rcx.colors
    val tint = accent ?: c.blue
    val photo = rememberRiderAvatar(version)

    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.20f), tint.copy(alpha = 0.04f))
                )
            )
            .then(
                if (ring) Modifier.border(1.5.dp, tint.copy(alpha = 0.35f), CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            photo != null -> Image(
                bitmap = photo,
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )

            name.isNotBlank() -> Text(
                name.trim().first().uppercase(),
                // Scales with the circle so one component serves a 32dp row
                // icon and a 92dp profile header without a size argument.
                style = RcxType.Wordmark.copy(fontSize = (size.value * 0.38f).sp),
                color = tint,
            )

            else -> Icon(
                Icons.Filled.Person, null,
                Modifier.size(size * 0.42f), tint = c.muted,
            )
        }
    }
}

/**
 * Decodes the saved avatar off the main thread.
 *
 * The project has no image-loading library and the file is already a 1080px
 * square, so a plain decode is cheaper than adding one.
 *
 * Keyed on the file's modification time as well as its path: the path never
 * changes, so without it a freshly cropped photo would keep drawing the
 * previous one until the process restarted.
 */
@Composable
fun rememberRiderAvatar(version: Int = 0): ImageBitmap? {
    val context = LocalContext.current
    val store = remember { ProfilePhotoStore(context) }
    val stamp = store.photoFile.let { if (it.exists()) it.lastModified() else 0L }
    val uri: Uri? = remember(stamp, version) { store.existingPhotoUri }

    var image by remember(uri, stamp, version) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uri, stamp, version) {
        image = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return image
}
