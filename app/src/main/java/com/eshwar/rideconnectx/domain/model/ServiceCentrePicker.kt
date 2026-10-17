package com.eshwar.rideconnectx.domain.model

import java.util.Locale

/** One place OpenStreetMap returned: its name, tags and whatever address parts it carries. */
data class OsmPlace(
    val name: String,
    val suburb: String = "",
    val city: String = "",
    val shop: String = "",
    val brand: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    /** Worked out from [lat]/[lon] by the phone's Geocoder when [suburb] is missing. */
    val derivedArea: String = "",
)

/**
 * The service-centre picker's rules (N14). Pure, so they are testable without
 * a device or the network; fetching lives in `NearbyServiceCentres`.
 *
 * Nearby centres come from OpenStreetMap's free Overpass API, not the paid
 * Places API (R2 still waits on billing).
 */
object ServiceCentrePicker {
    const val RADIUS_M = 10_000
    const val MAX_NEARBY = 20

    /** Coordinates leave the phone rounded to 2 decimals (about 1 km); the radius grows by the worst rounding error (~0.005 deg each way = ~790 m). */
    const val ROUND_PAD_M = 800

    /** Two-wheeler shops and repairers, around a point. */
    fun overpassQuery(lat: Double, lon: Double, radiusM: Int = RADIUS_M): String {
        val around = String.format(Locale.US, "(around:%d,%.2f,%.2f)", radiusM + ROUND_PAD_M, lat, lon)
        return "[out:json][timeout:5];(" +
            "nwr[\"shop\"=\"motorcycle\"]$around;" +
            "nwr[\"shop\"=\"motorcycle_repair\"]$around;" +
            "nwr[\"craft\"=\"motorcycle_repair\"]$around;" +
            ");out center tags $MAX_NEARBY;"
    }

    /**
     * Two-wheelers only: car showrooms (OpenStreetMap shop=car / car_repair,
     * named Maruti or NEXA) are dropped (rider, 28 Sep).
     */
    fun isTwoWheeler(p: OsmPlace): Boolean {
        val text = "${p.name} ${p.brand}".lowercase()
        if (p.shop == "car" || p.shop == "car_repair") return false
        // Arena is a car-showroom brand.
        if ("maruti" in text || "nexa" in text || "arena" in text) return false
        return p.shop == "motorcycle" || p.shop == "motorcycle_repair"
    }

    /** "Bike Point – Dammaiguda"; just the name when no area is known. */
    fun label(area: String, name: String): String =
        if (area.isBlank()) name.trim() else "${name.trim()} – ${area.trim()}"

    /**
     * Named places only, labelled with their own suburb or city, else the
     * rider's area. Duplicates (the same shop as a node and a building) once.
     */
    fun nearbyLabels(places: List<OsmPlace>, riderArea: String): List<String> =
        places.filter { it.name.isNotBlank() && isTwoWheeler(it) }
            .map { label(it.suburb.ifBlank { it.derivedArea }.ifBlank { it.city }.ifBlank { riderArea }, it.name) }
            .distinctBy { it.lowercase() }
            .take(MAX_NEARBY)

    /** Past centres first, then nearby; one of each, filtered by what is typed. */
    fun options(past: List<String>, nearby: List<String>, typed: String): List<String> {
        val q = typed.trim()
        return (past + nearby)
            .filter { it.isNotBlank() }
            .distinctBy { it.trim().lowercase() }
            .filter { q.isEmpty() || it.contains(q, ignoreCase = true) }
            .filterNot { it.equals(q, ignoreCase = true) }
    }
}
