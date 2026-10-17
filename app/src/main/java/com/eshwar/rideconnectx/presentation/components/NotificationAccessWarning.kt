package com.eshwar.rideconnectx.presentation.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.eshwar.rideconnectx.core.util.rememberNotificationAccess
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType

/**
 * Shown on the Notifications screen, and only while access is off.
 * Without it the cluster gets no turns, call lamps or message lamps, and the
 * Notifications screen used to just say "all caught up" (rider, 24 Sep).
 */
@Composable
fun NotificationAccessWarning(modifier: Modifier = Modifier) {
    val access = rememberNotificationAccess()
    if (access.granted) return
    WarningCard(
        title = "Notification access is off",
        body = "Turns, call and message alerts can't reach your scooter. Tap to turn it on.",
        onClick = access.openSettings,
        modifier = modifier,
    )
}

/**
 * The app's own notifications (POST_NOTIFICATIONS, Android 13+) — separate from
 * the access above. Off, service reminders and ride summaries silently never
 * appear (rider, 26 Sep). Tapping asks again while Android still allows it;
 * once it won't show the dialog, it opens the app's notification settings.
 */
@Composable
fun PostNotificationsWarning(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val activity = LocalActivity.current ?: return
    val permission = Manifest.permission.POST_NOTIFICATIONS
    fun isGranted() =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(isGranted()) }

    // Re-read on every return: it can be switched off in Settings meanwhile.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = isGranted() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val openSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { granted = isGranted() }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        // Refused with no rationale left = Android answered without asking.
        if (!ok && !activity.shouldShowRequestPermissionRationale(permission)) {
            runCatching {
                openSettings.launch(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                )
            }
        }
    }

    if (granted) return
    WarningCard(
        title = "Notifications are off",
        body = "Service reminders and ride summaries won't appear. Tap to allow them.",
        onClick = { request.launch(permission) },
        modifier = modifier,
    )
}

@Composable
private fun WarningCard(title: String, body: String, onClick: () -> Unit, modifier: Modifier) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.amber.copy(alpha = 0.10f))
            .border(1.dp, c.amber.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(title, style = RcxType.BodySmall, color = c.amber)
        Spacer(Modifier.height(4.dp))
        Text(body, style = RcxType.BodySmall, color = c.text)
    }
}
