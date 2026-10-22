package com.eshwar.rideconnectx.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.eshwar.rideconnectx.BuildConfig
import com.eshwar.rideconnectx.R

const val REPORT_ADDRESS = "rideconnectx.app@gmail.com"

fun reportSubject(version: String) = "RideConnectX $version report"

/** Takes only three strings on purpose: no account, location or ride data can reach the body. */
fun reportBody(version: String, model: String, os: String) =
    "App version: $version\nPhone: $model\nAndroid: $os\n\n" +
        "What I did:\n\n\nWhat I expected:\n\n\nWhat happened:\n\n\nScreenshot attached? (yes/no)\n"

/** Opens the mail app, pre-filled. Nothing is sent until the user presses send. */
fun openReportMail(context: Context) {
    val v = BuildConfig.VERSION_NAME
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_ADDRESS))
        putExtra(Intent.EXTRA_SUBJECT, reportSubject(v))
        putExtra(Intent.EXTRA_TEXT, reportBody(v, android.os.Build.MODEL, android.os.Build.VERSION.RELEASE))
    }
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.report_no_mail, REPORT_ADDRESS), Toast.LENGTH_LONG).show()
    }
}
