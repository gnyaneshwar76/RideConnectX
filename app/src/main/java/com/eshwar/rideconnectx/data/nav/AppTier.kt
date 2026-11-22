package com.eshwar.rideconnectx.data.nav

import android.app.Notification

/** How much a phone app's notifications matter to a rider (plan 20261122, shared contract). */
enum class AppTier { IMPORTANT, MEDIUM, IGNORED }

/**
 * The one tier rule. Important: calls, missed calls, the default SMS app, Google
 * Messages, WhatsApp (+ Business), Telegram; ON for the cluster by default.
 * Medium: social, calendar, reminders, clock/alarms; in history, cluster only
 * when the rider switched the app ON. Everything else is ignored: never parsed,
 * stored, logged or sent.
 */
object AppTiers {

    val IMPORTANT_APPS = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.google.android.apps.messaging",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram", // Telegram X
        "com.samsung.android.dialer", "com.samsung.android.messaging",
    )

    // shortcut: a fixed list of common apps; add a package here when a rider reports one missing.
    val MEDIUM_APPS = setOf(
        // Social
        "com.instagram.android", "com.facebook.katana", "com.facebook.lite", "com.facebook.orca",
        "com.twitter.android", "com.snapchat.android", "com.linkedin.android", "com.reddit.frontpage",
        "com.discord", "org.thoughtcrime.securesms",
        // Calendar and reminders
        "com.google.android.calendar", "com.samsung.android.calendar", "com.android.calendar",
        "com.coloros.calendar", "com.oneplus.calendar", "com.google.android.keep",
        "com.google.android.apps.tasks", "com.samsung.android.app.reminder",
        // Clock and alarms
        "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage",
        "com.coloros.alarmclock", "com.oneplus.deskclock",
    )

    private val MEDIUM_CATEGORIES = setOf(
        Notification.CATEGORY_EVENT,
        Notification.CATEGORY_REMINDER,
        Notification.CATEGORY_ALARM,
    )

    /**
     * [trustedCall]: a call or missed-call notification from a believed call
     * source. [isSystemApp] is asked only for an unlisted app posting an
     * event/alarm/reminder (OEM clock and calendar apps have many names).
     */
    fun tierOf(
        pkg: String,
        trustedCall: Boolean,
        defaultSms: String?,
        category: String? = null,
        isSystemApp: () -> Boolean = { false },
    ): AppTier = when {
        trustedCall || pkg in IMPORTANT_APPS || pkg == defaultSms -> AppTier.IMPORTANT
        pkg in MEDIUM_APPS -> AppTier.MEDIUM
        category in MEDIUM_CATEGORIES && isSystemApp() -> AppTier.MEDIUM
        else -> AppTier.IGNORED
    }

    /** What happens to one notification. */
    data class Route(val store: Boolean, val cluster: Boolean)

    /** [switch] = the rider's per-app switch, null when never set (tier default: Important ON, Medium OFF). */
    fun route(tier: AppTier, switch: Boolean?): Route = when (tier) {
        AppTier.IGNORED -> Route(store = false, cluster = false)
        else -> Route(store = true, cluster = switch ?: (tier == AppTier.IMPORTANT))
    }

    private val OTP_WORD = Regex(
        "\\b(otp|one[ -]?time|verification|verify|passcode|password|pin|cvv|cvc|do not share|don'?t share|never share|" +
            "(security|login|auth(entication)?|access|confirmation|reset)\\s+code|(use|your)\\s+code|code\\s+is)\\b",
        RegexOption.IGNORE_CASE,
    )
    // 4-8 digits, or two groups of 3 ("123 456", "123-456").
    private val OTP_DIGITS = Regex("(?<!\\d)(\\d{4,8}|\\d{3}[ -]\\d{3})(?!\\d)")

    /**
     * A one-time code: its text is never shown on the dashboard or stored, even from an Important app.
     * Needs a code word AND a number, so "Please verify the plan" stays.
     * shortcut: a rider's own "my pin 1234" also loses its preview (false positive, safe side).
     */
    fun looksLikeOtp(text: String): Boolean = OTP_WORD.containsMatchIn(text) && OTP_DIGITS.containsMatchIn(text)

    private val BANK_STRONG = Regex(
        "(?<![a-z])(debited|credited|a/c|acct|txn|refno|ref\\s?no|neft|imps|rtgs|balance)(?![a-z])",
        RegexOption.IGNORE_CASE,
    )
    private val BANK_UPI = Regex("(?<![a-z])upi(?![a-z])", RegexOption.IGNORE_CASE)
    private val AMOUNT = Regex("(rs\\.?|inr|₹)\\s?\\d", RegexOption.IGNORE_CASE)

    /**
     * Bank / UPI / transaction text. Rule: an amount (Rs, INR, rupee sign + digit) with one
     * strong bank word, or two different bank words (UPI counts only as the second word).
     * Trade-off: "Rs 500 for dinner?" stays; a friend's "Rs 500 balance pending" or
     * "pay Rs 500 a/c" loses its preview (false positive); a bank text with no amount and one
     * bank word slips through (false negative). Both chosen to keep ordinary chat.
     */
    fun looksLikeBankText(text: String): Boolean {
        val strong = BANK_STRONG.findAll(text).map { it.value.lowercase().replace(Regex("\\s"), "") }.toSet()
        val upi = BANK_UPI.containsMatchIn(text)
        return (strong.isNotEmpty() && AMOUNT.containsMatchIn(text)) || strong.size + (if (upi) 1 else 0) >= 2
    }

    /** One-time codes and bank texts: shown and stored as the sender name only ([messageView]). */
    fun isSensitive(text: String): Boolean = looksLikeOtp(text) || looksLikeBankText(text)

    /** History body for a code or bank text: a fixed label, never any of its words. */
    const val SENSITIVE_LABEL = "Message"

    /** What one message may show: [who] for cluster and history, [clusterText] for the alert, [historyBody] for the list. */
    data class MessageView(val who: String, val clusterText: String, val historyBody: String)

    /**
     * Owner, 22 Nov 2026: a code or bank text is not dropped; it keeps only the sender
     * name (cluster and history) with [SENSITIVE_LABEL]. A sender name that itself looks
     * like a code or bank text is blanked too. Normal messages keep the [preview].
     */
    fun messageView(who: String, body: String): MessageView =
        if (isSensitive("$who $body")) {
            MessageView(if (isSensitive(who)) "" else who, "", SENSITIVE_LABEL)
        } else {
            MessageView(who, body, preview(body))
        }

    /** Longest sender name kept in history. */
    const val SENDER_CHARS = 40

    fun sender(who: String): String = cleanStoredText(who).take(SENDER_CHARS).trimEnd()

    /** History keeps at most this many characters of a message. */
    const val PREVIEW_CHARS = 60

    /** Cleaned, one line, cut to [PREVIEW_CHARS]; blank becomes "New message". */
    fun preview(text: String): String {
        val s = cleanStoredText(text).replace(Regex("\\s+"), " ")
        return when {
            s.isEmpty() -> "New message"
            s.length <= PREVIEW_CHARS -> s
            else -> s.take(PREVIEW_CHARS - 1).trimEnd() + "…"
        }
    }
}
