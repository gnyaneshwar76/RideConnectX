package com.eshwar.rideconnectx.domain

import com.eshwar.rideconnectx.domain.model.VehicleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleCatalogTest {
    private fun n(code: String) = VehicleCatalog.byDeviceName("S${code}0217219")?.name

    @Test fun officialCodesMapToModels() {
        assertEquals("Access", n("AS21"))
        assertEquals("Access 125", n("AS01"))
        assertEquals("Access 125", n("AS02"))
        assertEquals("Burgman Street", n("AS11"))
        assertEquals("Burgman Street", n("AS12"))
        assertEquals("Burgman Street", n("BS11"))
        assertEquals("Burgman Street", n("BS51"))
        assertEquals("Burgman Street 125EX", n("BS31"))
        assertEquals("Avenis 125", n("BS22"))
        assertEquals("e-ACCESS", VehicleCatalog.byDeviceName("SCE000000000")?.name)
        assertEquals("Access-TFT Edition", VehicleCatalog.byDeviceName("SCS000000000")?.name)
        assertEquals("Burgman Street-TFT Edition", VehicleCatalog.byDeviceName("SCS100000000")?.name)
        assertEquals("V-Strom SX", n("BM01"))
        assertEquals("Gixxer SF", n("BM11"))
        assertEquals("Gixxer SF 250", n("BM21"))
    }

    @Test fun electricSubtitleHasNoCc() {
        val e = VehicleCatalog.byDeviceName("SCE000000000")!!
        assertEquals(e.trim, e.subtitle)
        assertEquals("Ride Connect Electric", e.subtitle)
    }

    @Test fun subtitleOfEveryModelIsPinned() {
        val expected = mapOf(
            "access_125" to "Ride Connect TFT Edition · 124cc",
            "burgman_street_125ex" to "Ride Connect TFT Edition · 124cc",
            "access" to "Ride Connect · 124cc",
            "access_tft" to "Ride Connect TFT Edition · 124cc",
            "e_access" to "Ride Connect Electric",
            "burgman_street" to "Ride Connect · 124cc",
            "burgman_street_tft" to "Ride Connect TFT Edition · 124cc",
            "avenis_125" to "Ride Connect Edition · 124cc",
            "gixxer_sf_250" to "Ride Connect · 249cc",
            "gixxer_250" to "Ride Connect · 249cc",
            "gixxer_sf_155" to "Ride Connect · 155cc",
            "gixxer_155" to "Ride Connect · 155cc",
            "vstrom_sx_250" to "Ride Connect · 249cc",
        )
        assertEquals(13, expected.size)
        expected.forEach { (id, subtitle) ->
            assertEquals(subtitle, VehicleCatalog.byId(id)!!.subtitle)
        }
    }

    @Test fun unknownIsSafe() {
        assertNull(VehicleCatalog.byDeviceName("SXX000000000"))
        assertNull(VehicleCatalog.byDeviceName(null))
        assertEquals("Access 125", VehicleCatalog.forDeviceName("junk").name)
    }

    @Test fun existingSavedIdsStillLoad() {
        listOf("access_125", "burgman_street_125ex", "avenis_125", "gixxer_sf_250",
            "gixxer_250", "gixxer_sf_155", "gixxer_155", "vstrom_sx_250").forEach {
            assertNotNull(it, VehicleCatalog.byId(it))
        }
        assertEquals("Burgman Street 125EX", VehicleCatalog.byId("burgman_street_125ex")?.name)
        assertEquals(13, VehicleCatalog.all.size)
        assertEquals(VehicleCatalog.all.size, VehicleCatalog.all.map { it.id }.toSet().size)
    }
}
