package com.eshwar.rideconnectx.domain.model

/**
 * Passwordless sign-in: the app emails a one-time link; opening it on the phone
 * finishes sign-in. The email is saved when the link is sent, so the same phone
 * finishes without asking. On another phone it is not there, so the rider types it.
 */
object EmailLinkFlow {

    sealed interface Step {
        data object NoLink : Step
        data object AskEmail : Step
        data class Complete(val email: String) : Step
    }

    /** A typed email wins over the saved one (the rider may be correcting it). */
    fun next(link: String?, savedEmail: String?, typedEmail: String?): Step = when {
        link == null -> Step.NoLink
        !typedEmail.isNullOrBlank() -> Step.Complete(typedEmail.trim())
        !savedEmail.isNullOrBlank() -> Step.Complete(savedEmail.trim())
        else -> Step.AskEmail
    }

    // Limits the app enforces itself (Firebase's own link lifetime is fixed on the free plan).
    const val LINK_LIFETIME_MS = 10 * 60_000L
    const val RESEND_COOLDOWN_MS = 60_000L
    const val MAX_REQUESTS_PER_DAY = 5
    const val REQUEST_WINDOW_MS = 24 * 60 * 60_000L

    const val LINK_EXPIRED = "This link has expired. Request a new one."
    const val SENT_NOTE = "Check your inbox. The link works for 10 minutes and only once."

    /** No send time (link requested on another phone) = cannot tell; Firebase's own limit applies. */
    fun isExpired(sentAt: Long?, now: Long): Boolean = sentAt != null && now - sentAt > LINK_LIFETIME_MS

    sealed interface SendCheck {
        data object Ok : SendCheck
        data class Wait(val seconds: Int) : SendCheck
        data class Capped(val minutes: Int) : SendCheck
    }

    /** [requests] = send times of earlier requests on this phone. Cap is checked before cooldown. */
    fun checkSend(requests: List<Long>, now: Long): SendCheck {
        val recent = requests.filter { now - it < REQUEST_WINDOW_MS }
        if (recent.size >= MAX_REQUESTS_PER_DAY) {
            val left = recent.min() + REQUEST_WINDOW_MS - now
            return SendCheck.Capped(((left + 59_999) / 60_000).toInt().coerceAtLeast(1))
        }
        val last = recent.maxOrNull() ?: return SendCheck.Ok
        val left = RESEND_COOLDOWN_MS - (now - last)
        return if (left > 0) SendCheck.Wait(((left + 999) / 1000).toInt()) else SendCheck.Ok
    }

    fun cappedMessage(minutes: Int): String {
        val h = (minutes + 59) / 60
        val wait = if (minutes < 60) "$minutes minute${if (minutes == 1) "" else "s"}" else "$h hour${if (h == 1) "" else "s"}"
        return "Too many requests today. Try again in $wait."
    }

    /** e***@gmail.com: the address is not repeated in full in screen text. */
    fun maskEmail(email: String): String {
        val e = email.trim()
        val at = e.lastIndexOf('@')
        if (at < 0) return "***"
        return (if (at > 0) e.take(1) else "") + "***" + e.substring(at)
    }

    const val TOO_MANY = "Too many tries. Wait a few minutes and try again."
    const val WRONG_EMAIL = "That email doesn't match the one the link was sent to. Type the same email."

    /** Plain words for a failed link, from Firebase's error code; null = use the general message. */
    fun message(errorCode: String?): String? = when (errorCode) {
        "ERROR_EXPIRED_ACTION_CODE" -> "This sign-in link has expired. Ask for a new one."
        "ERROR_INVALID_ACTION_CODE" -> "This sign-in link was already used or is not valid. Ask for a new one."
        "ERROR_INVALID_EMAIL" -> WRONG_EMAIL
        "ERROR_USER_DISABLED" -> "This account has been turned off."
        else -> null
    }

    const val SEND_FAILED = "Couldn't send the sign-in link. Please try again in a moment."

    const val QUOTA_BUSY = "Sign-in emails are very busy today. Please try Google sign-in or try again tomorrow."

    /** Firebase's daily email quota: shows up as an error code, or only inside the message text. */
    fun isQuotaExceeded(errorCode: String?, rawMessage: String?): Boolean =
        listOf(errorCode, rawMessage).any { it != null && it.replace(Regex("[\\s_-]"), "").contains("quotaexceeded", ignoreCase = true) }

    /** Plain words when asking for a link fails; never Firebase's raw text. */
    fun sendMessage(errorCode: String?, rawMessage: String? = null): String = when {
        isQuotaExceeded(errorCode, rawMessage) -> QUOTA_BUSY
        errorCode == "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
        errorCode == "ERROR_TOO_MANY_REQUESTS" -> TOO_MANY
        else -> SEND_FAILED
    }

    /** Only a wrong email leaves the link usable; an expired or used link is dropped. */
    fun linkStillGood(errorCode: String?): Boolean = errorCode == "ERROR_INVALID_EMAIL"
}
