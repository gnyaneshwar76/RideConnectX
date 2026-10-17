package com.eshwar.rideconnectx.data.repository

import android.util.Log
import com.eshwar.rideconnectx.core.util.CityLocator
import com.eshwar.rideconnectx.domain.model.OsmPlace
import com.eshwar.rideconnectx.domain.model.ServiceCentrePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nearby two-wheeler service centres for the service-centre picker
 * (N14), from OpenStreetMap's free Overpass API — no Places billing.
 *
 * Usage policy: one request per time the field opens, the answer cached for
 * [CACHE_MS], an identifying User-Agent, and a 5 s limit. Any failure is quiet:
 * the picker falls back to area names from the phone's Geocoder, then to the
 * rider's own past centres.
 */
@Singleton
class NearbyServiceCentres @Inject constructor(
    private val locator: CityLocator,
) {
    private var cached: List<String> = emptyList()
    private var cachedAt = 0L

    suspend fun find(): List<String> {
        if (cached.isNotEmpty() && System.currentTimeMillis() - cachedAt < CACHE_MS) return cached
        val here = locator.roughLocation() ?: return emptyList()
        val places = withTimeoutOrNull(TIMEOUT_MS) { fetch(here.latitude, here.longitude) }.orEmpty()
            .filter(ServiceCentrePicker::isTwoWheeler)
            .take(ServiceCentrePicker.MAX_NEARBY)
        val result = if (places.isNotEmpty()) {
            ServiceCentrePicker.nearbyLabels(withAreas(places), locator.areaOf(here))
        } else {
            locator.areaNames(here)
        }
        if (result.isNotEmpty()) {
            cached = result
            cachedAt = System.currentTimeMillis()
        }
        return result
    }

    /** No suburb tag: ask the Geocoder where the place is (N14), all at once, 5 s at most. */
    private suspend fun withAreas(places: List<OsmPlace>): List<OsmPlace> = coroutineScope {
        places.map { p ->
            async {
                if (p.suburb.isNotBlank() || p.lat == null || p.lon == null) return@async p
                val area = withTimeoutOrNull(TIMEOUT_MS) { locator.areaAt(p.lat, p.lon) }.orEmpty()
                p.copy(derivedArea = area)
            }
        }.awaitAll()
    }

    private suspend fun fetch(lat: Double, lon: Double): List<OsmPlace> = withContext(Dispatchers.IO) {
        runCatching {
            val query = URLEncoder.encode(ServiceCentrePicker.overpassQuery(lat, lon), "UTF-8")
            val conn = URL("$ENDPOINT?data=$query").openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS.toInt()
            conn.readTimeout = TIMEOUT_MS.toInt()
            conn.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (conn.responseCode != 200) return@runCatching emptyList()
                val elements = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    .optJSONArray("elements") ?: return@runCatching emptyList()
                (0 until elements.length()).mapNotNull { i ->
                    val el = elements.optJSONObject(i) ?: return@mapNotNull null
                    val tags = el.optJSONObject("tags") ?: return@mapNotNull null
                    // Nodes carry lat/lon; ways and relations a "center".
                    val point = el.optJSONObject("center") ?: el
                    OsmPlace(
                        name = tags.optString("name"),
                        suburb = tags.optString("addr:suburb"),
                        city = tags.optString("addr:city"),
                        shop = tags.optString("shop"),
                        brand = tags.optString("brand"),
                        lat = point.optDouble("lat").takeIf { !it.isNaN() },
                        lon = point.optDouble("lon").takeIf { !it.isNaN() },
                    )
                }
            } finally {
                conn.disconnect()
            }
        }.onFailure { Log.w(TAG, "Overpass lookup failed", it) }.getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "RCX-Centres"
        const val ENDPOINT = "https://overpass-api.de/api/interpreter"
        const val USER_AGENT = "RideConnectX/1.0 (Android; service-centre picker)"
        const val TIMEOUT_MS = 5_000L
        const val CACHE_MS = 30 * 60_000L
    }
}
