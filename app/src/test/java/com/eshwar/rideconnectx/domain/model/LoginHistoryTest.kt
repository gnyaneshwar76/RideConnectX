package com.eshwar.rideconnectx.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginHistoryTest {

    @Test fun `keeps the newest 10 and deletes the rest`() {
        val ids = (1..13).map { "n$it" }               // newest first
        assertEquals(listOf("n11", "n12", "n13"), LoginHistory.overflow(ids))
        assertEquals(emptyList<String>(), LoginHistory.overflow(ids.take(10)))
        assertEquals(emptyList<String>(), LoginHistory.overflow(emptyList()))
    }

    @Test fun `record holds only the allowed fields, no ip or location`() {
        val r = LoginHistory.record("google", "Google", "Pixel 10 Pro", "16", "1.0.0")
        // The data source adds "time"; firestore.rules allows exactly FIELDS.
        assertEquals(LoginHistory.FIELDS, r.keys + "time")
        assertEquals("Google Pixel 10 Pro", r["device"])
        assertEquals("Xiaomi 14", LoginHistory.record("google", "Xiaomi", "Xiaomi 14", "15", "1")["device"]) // maker not repeated
        assertEquals("16", r["android"])
        assertEquals("1.0.0", r["app"])
    }

    @Test fun `record fits the rule size limits`() {
        val r = LoginHistory.record("password", "M".repeat(50), "X".repeat(50), "1".repeat(40), "v".repeat(40))
        assertTrue(r.getValue("device").length <= 60)
        assertTrue(r.getValue("android").length <= 20)
        assertTrue(r.getValue("app").length <= 30)
        assertEquals("Samsung SM-S911B", LoginHistory.record("emailLink", "Samsung", "SM-S911B", "15", "1")["device"])
    }

    @Test fun `method keys match the rules list`() {
        assertEquals("google", LoginHistory.methodKey(LoginMethod.GOOGLE))
        assertEquals("password", LoginHistory.methodKey(LoginMethod.EMAIL))
        assertEquals("emailLink", LoginHistory.methodKey(LoginMethod.EMAIL_LINK))
        assertNull(LoginHistory.methodKey(LoginMethod.GUEST))
        assertTrue(LoginMethod.entries.mapNotNull(LoginHistory::methodKey).all { it in LoginHistory.METHODS })
    }
}
