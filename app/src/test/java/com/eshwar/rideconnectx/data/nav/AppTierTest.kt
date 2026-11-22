package com.eshwar.rideconnectx.data.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTierTest {
    private fun tier(pkg: String, call: Boolean = false, sms: String? = null, category: String? = null, system: Boolean = false) =
        AppTiers.tierOf(pkg, call, sms, category) { system }

    @Test fun importantGroup() {
        assertEquals(AppTier.IMPORTANT, tier("com.whatsapp"))
        assertEquals(AppTier.IMPORTANT, tier("com.whatsapp.w4b"))
        assertEquals(AppTier.IMPORTANT, tier("com.google.android.apps.messaging"))
        assertEquals(AppTier.IMPORTANT, tier("org.telegram.messenger"))
        assertEquals(AppTier.IMPORTANT, tier("com.samsung.android.messaging", sms = "com.samsung.android.messaging"))
        assertEquals(AppTier.IMPORTANT, tier("com.google.android.dialer", call = true))
    }

    @Test fun samsungDialerAndMessagesAreImportant() {
        listOf("com.samsung.android.dialer", "com.samsung.android.messaging").forEach { assertEquals(AppTier.IMPORTANT, tier(it)) }
    }

    @Test fun mediumGroup() {
        assertEquals(AppTier.MEDIUM, tier("com.instagram.android"))
        assertEquals(AppTier.MEDIUM, tier("com.google.android.calendar"))
        assertEquals(AppTier.MEDIUM, tier("com.google.android.deskclock"))
        // An OEM clock with an unlisted name, but a system app posting an alarm.
        assertEquals(AppTier.MEDIUM, tier("com.oem.clock", category = "alarm", system = true))
    }

    @Test fun unknownAndListedJunkAreIgnored() {
        assertEquals(AppTier.IGNORED, tier("com.example.shop"))
        assertEquals(AppTier.IGNORED, tier("net.one97.paytm"))
        assertEquals(AppTier.IGNORED, tier("com.google.android.gm"))
        // A store app setting "reminder" is not a system app: still ignored.
        assertEquals(AppTier.IGNORED, tier("com.example.shop", category = "reminder", system = false))
        // The dialer's non-call posts (voicemail, etc.) are not messages.
        assertEquals(AppTier.IGNORED, tier("com.google.android.dialer", call = false))
    }

    @Test fun ignoredAppIsNeverStoredOrSent() {
        val r = AppTiers.route(AppTier.IGNORED, switch = true)
        assertFalse(r.store)
        assertFalse(r.cluster)
    }

    @Test fun tierDefaultsAndSwitches() {
        assertEquals(AppTiers.Route(store = true, cluster = true), AppTiers.route(AppTier.IMPORTANT, null))
        assertEquals(AppTiers.Route(store = true, cluster = false), AppTiers.route(AppTier.MEDIUM, null))
        assertEquals(AppTiers.Route(store = true, cluster = true), AppTiers.route(AppTier.MEDIUM, true))
    }

    @Test fun switchOffBlocksTheCluster() {
        val r = AppTiers.route(AppTier.IMPORTANT, switch = false)
        assertTrue(r.store)
        assertFalse(r.cluster)
    }

    @Test fun otpKeepsOnlyTheSenderName() {
        assertTrue(AppTiers.looksLikeOtp("AX-BANK 482913 is your OTP for login"))
        assertTrue(AppTiers.looksLikeOtp("Your verification code is 7731"))
        assertFalse(AppTiers.looksLikeOtp("Reached home, call me at 6"))
        assertFalse(AppTiers.looksLikeOtp("Please verify the plan"))
        val v = AppTiers.messageView("AX-HDFCBK", "482913 is your OTP for login. Do not share")
        assertEquals(AppTiers.MessageView("AX-HDFCBK", "", AppTiers.SENSITIVE_LABEL), v)
    }

    @Test fun sensitiveSenderNameIsBlankedToo() {
        // The code sits in the title: nothing of it may survive.
        assertEquals(AppTiers.MessageView("", "", AppTiers.SENSITIVE_LABEL), AppTiers.messageView("Your OTP is 482913", ""))
    }

    @Test fun previewIsShortAndOneLine() {
        assertEquals("New message", AppTiers.preview("  "))
        assertEquals("On my way home", AppTiers.preview("On my way\n home"))
        val long = AppTiers.preview("x".repeat(200))
        assertEquals(AppTiers.PREVIEW_CHARS, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test fun otpVariants() {
        listOf("Use code 123456", "Your code is 4829", "Your PIN is 5521", "CVV 123 for card 4111", "Code 123 456 do not share",
            "G-123-456 is your Google verification code", "Your one-time password is 884210", "Do not share 9921")
            .forEach { assertTrue(it, AppTiers.looksLikeOtp(it)) }
        listOf("Your parcel arrives at 5", "Use the code in the app", "Lunch at 1 30?", "Call 98765 43210").forEach { assertFalse(it, AppTiers.looksLikeOtp(it)) }
    }

    @Test fun bankTextsKeepOnlyTheSenderName() {
        listOf("Rs.500.00 debited from A/c XX1234 on 22-11. Bal Rs 2,000", "INR 1,200 credited to your acct", "UPI txn of Rs 50 to Ravi, Ref No 4455",
            "Your a/c balance is low", "Sent via UPI, refno 99")
            .forEach {
                assertTrue(it, AppTiers.looksLikeBankText(it))
                assertEquals(it, AppTiers.MessageView("JD-SBIUPI", "", AppTiers.SENSITIVE_LABEL), AppTiers.messageView("JD-SBIUPI", it))
            }
        // Not dropped any more: an Important app still stores it and sends it to the cluster.
        assertEquals(AppTiers.Route(store = true, cluster = true), AppTiers.route(AppTier.IMPORTANT, null))
    }

    @Test fun ordinaryTextsStay() {
        listOf("Rs 500 for dinner?", "Send me on UPI", "Rs 100 please", "Balance the bike", "Credited you in the report")
            .forEach {
                assertFalse(it, AppTiers.isSensitive(it))
                assertEquals(AppTiers.MessageView("Ravi", it, it), AppTiers.messageView("Ravi", it))
            }
    }

    @Test fun senderIsCapped() {
        assertEquals(40, AppTiers.sender("n".repeat(100)).length)
        assertEquals("Bob", AppTiers.sender("  Bob "))
    }

    @Test fun previewKeepsAllWords() {
        assertEquals("Your parcel is at the gate", AppTiers.preview("Your parcel is at the gate"))
    }
}
