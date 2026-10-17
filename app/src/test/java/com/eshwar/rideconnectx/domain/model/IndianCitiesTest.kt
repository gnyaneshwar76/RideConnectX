package com.eshwar.rideconnectx.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** City picker: prefix matches first, and typed names map to the list's spelling (N15). */
class IndianCitiesTest {
    @Test
    fun `prefix matches come before inner matches`() {
        assertEquals("Hyderabad", IndianCities.search("hyd").first())
        assertEquals(listOf("Jabalpur", "Jaipur", "Jalandhar"), IndianCities.search("ja").take(3))
        // Nothing starts with "nagar"; the ones containing it still come up.
        assertEquals("Ahmednagar", IndianCities.search("nagar").first())
    }

    @Test
    fun `every city belongs to exactly one state`() {
        val cities = IndianCities.byState.values.flatten()
        assertEquals(cities.size, cities.toSet().size)
        assertEquals("Telangana", IndianCities.stateOf("hyderabad"))
        assertEquals(listOf("Hyderabad", "Karimnagar", "Nizamabad", "Secunderabad", "Warangal"), IndianCities.citiesIn("Telangana"))
        assertNull(IndianCities.stateOf("Atlantis"))
    }

    @Test
    fun `a typed city resolves to the list's spelling`() {
        assertEquals("Hyderabad", IndianCities.find(" hyderabad "))
        assertNull(IndianCities.find("Atlantis"))
    }
}
