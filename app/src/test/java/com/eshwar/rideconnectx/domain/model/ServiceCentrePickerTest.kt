package com.eshwar.rideconnectx.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Service-centre picker: labels, fallback area and ordering (N14). */
class ServiceCentrePickerTest {

    @Test
    fun `nearby centres read as Name - Area, two-wheelers only`() {
        val labels = ServiceCentrePicker.nearbyLabels(
            listOf(
                OsmPlace("Bike Service", suburb = "Dammaiguda", shop = "motorcycle"),
                OsmPlace("Sai Motors", city = "Hyderabad", shop = "motorcycle_repair"),
                OsmPlace("Bike Point", shop = "motorcycle"),
                OsmPlace("Speed Bike", shop = "motorcycle", derivedArea = "Nagaram"),
                OsmPlace("", shop = "motorcycle"), // unnamed - useless in a picker
                OsmPlace("Bike Service", suburb = "Dammaiguda", shop = "motorcycle"), // node + building
                // Cars, not scooters (rider, 28 Sep).
                OsmPlace("Maruti Arena", brand = "Maruti", shop = "car"),
                OsmPlace("NEXA Kapra", brand = "Other"),
                OsmPlace("Bike Workshop", shop = "car_repair"),
                OsmPlace("Bike Arena ECIL", brand = "Other"),
            ),
            riderArea = "Kapra",
        )
        assertEquals(
            listOf(
                "Bike Service – Dammaiguda", "Sai Motors – Hyderabad",
                "Bike Point – Kapra", "Speed Bike – Nagaram",
            ),
            labels,
        )
    }

    @Test
    fun `past centres come first, once, filtered by what is typed`() {
        val past = listOf("Sai Bike", "dammaiguda – bike service")
        val nearby = listOf("Dammaiguda – Bike Service", "Kapra – Bike Point")
        assertEquals(
            listOf("Sai Bike", "dammaiguda – bike service", "Kapra – Bike Point"),
            ServiceCentrePicker.options(past, nearby, typed = ""),
        )
        assertEquals(listOf("Kapra – Bike Point"), ServiceCentrePicker.options(past, nearby, typed = "kapra"))
    }

    @Test
    fun `the query asks around the rider with a US-formatted point`() {
        val q = ServiceCentrePicker.overpassQuery(17.49123, 78.57456)
        assertTrue(q.contains("(around:10800,17.49,78.57)"))
        assertTrue(q.startsWith("[out:json]"))
    }
}
