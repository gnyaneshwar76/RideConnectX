package com.eshwar.rideconnectx.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the read side of the cloud profile.
 *
 * The write side always worked; nothing ever read it back, so reinstalling and
 * signing in with the same account still demanded a fresh profile. These pin
 * the key names and the fallbacks so that cannot quietly happen again.
 */
class CloudProfileTest {

    /** The shape `createUser` / `updateScooter` / `updateProfileFields` produce. */
    private fun document(
        riderName: String? = "Eshwar P",
        name: String? = "Eshwar Pochana",
        location: String? = "Secunderabad",
        vehicleId: String? = "access125",
        colorId: String? = "stellar_blue",
    ) = mapOf<String, Any?>(
        "profile" to buildMap {
            riderName?.let { put("riderName", it) }
            name?.let { put("name", it) }
            location?.let { put("location", it) }
            put("email", "rider@example.com")
            put("loginMethod", "GOOGLE")
        },
        "scooter" to buildMap {
            vehicleId?.let { put("vehicleId", it) }
            colorId?.let { put("colorId", it) }
            put("vehicleName", "Access 125")
        },
        "settings" to mapOf("theme" to "system"),
    )

    @Test
    fun `a full document restores every field`() {
        val p = CloudProfile.from(document())

        assertEquals("Eshwar P", p.riderName)
        assertEquals("Secunderabad", p.location)
        assertEquals("access125", p.vehicleId)
        assertEquals("stellar_blue", p.colorId)
        assertTrue(p.isComplete)
    }

    /**
     * `riderName` is what the rider typed and what the cluster greets them by.
     * The provider's `name` is often a full legal name and is only a fallback.
     */
    @Test
    fun `the rider's own name wins over the login provider's`() {
        assertEquals("Eshwar P", CloudProfile.from(document()).riderName)
        assertEquals(
            "Eshwar Pochana",
            CloudProfile.from(document(riderName = null)).riderName,
        )
    }

    @Test
    fun `a blank rider name falls through to the provider name`() {
        val doc = mapOf<String, Any?>(
            "profile" to mapOf("riderName" to "   ", "name" to "Eshwar Pochana"),
        )
        assertEquals("Eshwar Pochana", CloudProfile.from(doc).riderName)
    }

    /**
     * A name with no vehicle must not skip setup: the dashboard renders the
     * chosen vehicle in its header and artwork, so the rider would arrive at a
     * broken-looking screen.
     */
    @Test
    fun `a name without a vehicle is not complete`() {
        val p = CloudProfile.from(document(vehicleId = null, colorId = null))

        assertEquals("Eshwar P", p.riderName)
        assertNull(p.vehicleId)
        assertFalse(p.isComplete)
    }

    @Test
    fun `a vehicle without a name is not complete`() {
        val p = CloudProfile.from(document(riderName = null, name = null))

        assertNull(p.riderName)
        assertEquals("access125", p.vehicleId)
        assertFalse(p.isComplete)
    }

    /** A first sign-in writes `scooter` as an empty map. */
    @Test
    fun `a freshly created account has nothing to restore`() {
        val doc = mapOf<String, Any?>(
            "profile" to mapOf("name" to "New Rider", "email" to "new@example.com"),
            "scooter" to emptyMap<String, Any?>(),
        )
        val p = CloudProfile.from(doc)

        assertEquals("New Rider", p.riderName)
        assertNull(p.vehicleId)
        assertFalse(p.isComplete)
    }

    @Test
    fun `a missing or malformed document is handled rather than thrown`() {
        assertFalse(CloudProfile.from(null).isComplete)
        assertFalse(CloudProfile.from(emptyMap()).isComplete)

        // Wrong types where maps are expected — a half-written document must
        // send the rider to Create Profile, not crash them out of signing in.
        val junk = mapOf<String, Any?>("profile" to "not a map", "scooter" to 42)
        val p = CloudProfile.from(junk)
        assertNull(p.riderName)
        assertNull(p.vehicleId)
        assertFalse(p.isComplete)
    }

    /** A colour is optional — the vehicle still restores without one. */
    @Test
    fun `a vehicle with no colour still restores`() {
        val p = CloudProfile.from(document(colorId = null))

        assertEquals("access125", p.vehicleId)
        assertNull(p.colorId)
        assertTrue(p.isComplete)
    }
}
