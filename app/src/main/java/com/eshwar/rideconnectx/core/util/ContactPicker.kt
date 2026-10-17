package com.eshwar.rideconnectx.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** A name and number lifted out of the phone's contact list. */
data class PickedContact(val name: String, val phone: String)

/**
 * Opens the system contact picker and returns the chosen name and number.
 *
 * **No `READ_CONTACTS` permission is needed.** Picking with `ACTION_PICK` on
 * `Phone.CONTENT_URI` hands back a URI for the single row the rider chose, and
 * the system grants temporary read access to that row only. Asking for the
 * whole address book to add one emergency contact would be far more than the
 * job requires.
 *
 * Typing a number by hand still works; this exists because nobody should have
 * to, which is the complaint that prompted it.
 */
@Composable
fun rememberContactPicker(onPicked: (PickedContact) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onPicked)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        context.readContact(uri)?.let(callback)
    }

    return remember {
        {
            runCatching {
                launcher.launch(
                    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                )
            }
        }
    }
}

/**
 * Reads the picked row. Returns null rather than throwing on anything odd — a
 * contact with no number, a revoked grant, an OEM picker that hands back
 * something unexpected.
 */
private fun Context.readContact(uri: Uri): PickedContact? = runCatching {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
    )
    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val name = cursor.getString(0).orEmpty().trim()
        val phone = cursor.getString(1).orEmpty().trim()
        if (phone.isEmpty()) null else PickedContact(name, phone)
    }
}.getOrNull()
