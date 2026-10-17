package com.eshwar.rideconnectx.core.util

import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Resolves the rider's city for the profile step.
 *
 * Deliberately best-effort: the city is a convenience on a form the rider can
 * also type into, so every failure path returns null rather than throwing. The
 * caller decides what to say about it.
 */
@Singleton
class CityLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "RCX-Location"
        const val FIX_TIMEOUT_MS = 20_000L

        /** A cached fix older than this is not trusted for an emergency. */
        const val FRESH_ENOUGH_MS = 60_000L

        /** Metres. Roughly a house, rather than a street. */
        const val GOOD_ACCURACY_M = 15f
    }

    val hasPermission: Boolean
        get() = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /** City name, or null if it could not be determined. */
    @SuppressLint("MissingPermission")
    suspend fun currentCity(): String? {
        if (!hasPermission) {
            Log.d(TAG, "No location permission")
            return null
        }

        val location = lastKnown() ?: freshFix() ?: run {
            Log.d(TAG, "No location fix available")
            return null
        }

        return geocode(location)
    }

    /** True when the rider granted *precise* location, not just approximate. */
    val hasPreciseLocation: Boolean
        get() = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * A fix good enough to send someone to, for the Safety screen.
     *
     * **This is not [currentCity]'s "any fix will do".** A cached reading can be
     * an hour old and half a kilometre out — which is exactly what happened when
     * a shared location landed two streets away. So:
     *
     * 1. A cached fix is only accepted when it is **recent and accurate**.
     * 2. Otherwise the GPS is asked for a fresh one, and the *best* fix seen
     *    during the wait is kept rather than the first.
     * 3. Whatever comes back carries its accuracy, so the message can say how
     *    precise it is instead of implying a metre.
     *
     * Approximate-only permission caps accuracy at roughly a city block no
     * matter what — see [hasPreciseLocation].
     */
    suspend fun currentLocation(): Location? {
        if (!hasPermission) return null

        val cached = lastKnown()
        if (cached != null && cached.isGoodEnough()) return cached

        // Prefer a fresh fix; fall back to the cached one only if nothing better
        // arrives, since a stale position beats no position in an emergency.
        return bestFreshFix() ?: cached
    }

    /** Recent and tight enough to send help to. */
    private fun Location.isGoodEnough(): Boolean {
        val ageMs = System.currentTimeMillis() - time
        return ageMs < FRESH_ENOUGH_MS && hasAccuracy() && accuracy <= GOOD_ACCURACY_M
    }

    /**
     * One fresh fix from the fused provider (GPS + Wi-Fi + cell), which answers
     * in a second or two where raw GPS took up to [FIX_TIMEOUT_MS] — and the
     * old listener's timeout threw away the best fix it had seen, so a slow
     * lock returned nothing at all (rider, 24 Sep: "taking so much time").
     * GPS still works with no signal or data.
     */
    @SuppressLint("MissingPermission")
    private suspend fun bestFreshFix(): Location? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(FIX_TIMEOUT_MS)
            .setMaxUpdateAgeMillis(FRESH_ENOUGH_MS)
            .build()
        return runCatching {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(request, null)
                .await()
        }.onFailure { Log.w(TAG, "Fused location failed", it) }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        // Newest of whatever the providers already have, so the common case
        // costs nothing and needs no GPS lock.
        return manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(): Location? = withTimeoutOrNull(FIX_TIMEOUT_MS) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withTimeoutOrNull null

        suspendCancellableCoroutine { cont ->
            val provider = when {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }

            if (provider == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            lateinit var listener: android.location.LocationListener
            listener = android.location.LocationListener { location ->
                // One fix is enough: stop the provider now. Only cancellation used to
                // remove it, so a delivered fix left GPS requesting every 0 ms.
                runCatching { manager.removeUpdates(listener) }
                if (cont.isActive) cont.resume(location)
            }

            runCatching {
                manager.requestLocationUpdates(provider, 0L, 0f, listener)
            }.onFailure {
                Log.w(TAG, "requestLocationUpdates failed", it)
                runCatching { manager.removeUpdates(listener) }
                if (cont.isActive) cont.resume(null)
            }

            cont.invokeOnCancellation { manager.removeUpdates(listener) }
        }
    }

    private suspend fun geocode(location: Location): String? =
        addresses(location, 1).firstOrNull()
            ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            ?.also { Log.d(TAG, "Resolved city: $it") }

    private suspend fun addresses(location: Location, max: Int): List<android.location.Address> =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext emptyList()
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // The blocking overload is deprecated on 33+; this is the sanctioned
                // callback form, bridged back to a suspend result.
                withTimeoutOrNull(8_000L) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(location.latitude, location.longitude, max) {
                            if (cont.isActive) cont.resume(it)
                        }
                    }
                }.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                runCatching {
                    geocoder.getFromLocation(location.latitude, location.longitude, max)
                }.getOrNull().orEmpty()
            }
        }

    /** Any recent fix, for "what is near me" rather than for sending help (N14). */
    @SuppressLint("MissingPermission")
    suspend fun roughLocation(): Location? {
        if (!hasPermission) return null
        return lastKnown() ?: withTimeoutOrNull(5_000L) { freshFix() }
    }

    /** "Hyderabad – Dammaiguda" for the areas around [location]; empty when unknown. */
    suspend fun areaNames(location: Location): List<String> =
        addresses(location, 5).mapNotNull { a ->
            val city = a.locality ?: a.subAdminArea
            val area = a.subLocality ?: a.thoroughfare
            when {
                city != null && area != null -> "$city – $area"
                else -> city ?: area
            }
        }.distinct()

    /** The area at a point — a centre OpenStreetMap gave no suburb for (N14). */
    suspend fun areaAt(lat: Double, lon: Double): String =
        areaOf(Location("osm").apply { latitude = lat; longitude = lon })

    /** The rider's own area, for labelling a centre OpenStreetMap gave no area for. */
    suspend fun areaOf(location: Location): String =
        addresses(location, 1).firstOrNull()?.let { it.subLocality ?: it.locality }.orEmpty()
}
