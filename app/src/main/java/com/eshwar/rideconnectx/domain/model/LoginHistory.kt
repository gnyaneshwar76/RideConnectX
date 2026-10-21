package com.eshwar.rideconnectx.domain.model

/** One row of Profile -> Recent sign-ins (users/{uid}/logins). No IP, no location, no device id. */
data class LoginEntry(
    val timeMillis: Long,
    val method: String,
    val device: String,
    val android: String,
    val app: String,
)

/** The shape of a sign-in record, and the keep-newest-10 rule. firestore.rules allows exactly [FIELDS]. */
object LoginHistory {
    const val KEEP = 10
    val FIELDS = setOf("time", "method", "device", "android", "app")
    val METHODS = setOf("google", "password", "emailLink")

    fun methodKey(method: LoginMethod): String? = when (method) {
        LoginMethod.GOOGLE -> "google"
        LoginMethod.EMAIL -> "password"
        LoginMethod.EMAIL_LINK -> "emailLink"
        else -> null
    }

    /** Every field except `time`, which the data source sets to the server time. */
    fun record(
        method: String,
        manufacturer: String,
        model: String,
        androidRelease: String,
        appVersion: String,
    ): Map<String, String> {
        // "Google Pixel 9" and not "Google Google Pixel 9".
        val device = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        return mapOf(
            "method" to method,
            "device" to device.trim().take(60),
            "android" to androidRelease.take(20),
            "app" to appVersion.take(30),
        )
    }

    /** Ids to delete, given every id newest first. */
    fun overflow(idsNewestFirst: List<String>): List<String> = idsNewestFirst.drop(KEEP)

    fun methodLabel(key: String): String = when (key) {
        "google" -> "Google"
        "password" -> "Email and password"
        "emailLink" -> "Email link"
        else -> key
    }
}
