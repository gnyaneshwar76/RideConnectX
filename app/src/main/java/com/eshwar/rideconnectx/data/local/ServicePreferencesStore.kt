package com.eshwar.rideconnectx.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.servicePrefs: DataStore<Preferences> by preferencesDataStore(
    name = "service_prefs",
    // A corrupt file reads as empty (defaults) instead of crashing every launch.
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { androidx.datastore.preferences.core.emptyPreferences() },
)

/**
 * The rider's service reminder settings, plus the last odometer reading the app
 * ever saw.
 *
 * The odometer is cached here because the Service screen has to say how far the
 * next service is *away*, and the scooter only tells us the reading while it is
 * connected. Without a cache the screen would go blank the moment the rider
 * walks away from the vehicle — which is exactly when they look at it.
 *
 * Separate from [UserPreferencesStore] and [SessionDataStore] for the same
 * reason those are separate from each other: clearing one must not clear another.
 */
@Singleton
class ServicePreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * The maker's own schedule for the Access family: every 3,000 km or 3 months,
     * whichever comes first. The rider can change both.
     */
    val defaultIntervalKm = 3_000
    val defaultIntervalDays = 90

    val remindersEnabled: Flow<Boolean> =
        context.servicePrefs.data.map { it[REMINDERS_ON] ?: true }

    val intervalKm: Flow<Int> =
        context.servicePrefs.data.map { it[INTERVAL_KM] ?: defaultIntervalKm }

    val intervalDays: Flow<Int> =
        context.servicePrefs.data.map { it[INTERVAL_DAYS] ?: defaultIntervalDays }

    /** 0 means "never seen one" — the screen treats that as unknown, not as zero km. */
    val lastKnownOdometerKm: Flow<Int> =
        context.servicePrefs.data.map { it[LAST_ODO_KM] ?: 0 }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.servicePrefs.edit { it[REMINDERS_ON] = enabled }
    }

    suspend fun setIntervalKm(km: Int) {
        context.servicePrefs.edit { it[INTERVAL_KM] = km }
    }

    suspend fun setIntervalDays(days: Int) {
        context.servicePrefs.edit { it[INTERVAL_DAYS] = days }
    }

    /**
     * Only ever moves forward. A garbled frame that decodes low must not rewrite
     * a good high reading, and the odometer on a real vehicle cannot go down.
     *
     * Forward-only alone is not enough: a single corrupt frame that decodes
     * *high* sticks for good, and then rejects the rider's own real service
     * records as [ServiceRepository] OdometerTooLow. One such value already
     * reached this cache — 6,001,923 km against a real 2,248 (docs/HANDOFF.md).
     * The parser checks the byte-28 checksum, but one byte passes a garbled
     * frame 1 time in 128, so the guard stays here, where every source of a
     * reading passes through.
     */
    suspend fun recordOdometer(km: Int) {
        if (km <= 0) return
        context.servicePrefs.edit { prefs ->
            val known = prefs[LAST_ODO_KM] ?: 0
            if (isPlausibleOdometer(known, km) || refused.believe(km)) prefs[LAST_ODO_KM] = km
        }
    }

    /** Refused readings that agree with each other; see [RefusedStreak]. */
    private val refused = RefusedStreak()

    /** Last (state, city) chosen in the service-centre picker (N14). */
    val centrePlace: Flow<Pair<String, String>> = context.servicePrefs.data.map {
        it[CENTRE_STATE].orEmpty() to it[CENTRE_CITY].orEmpty()
    }

    suspend fun setCentrePlace(state: String, city: String) {
        context.servicePrefs.edit {
            it[CENTRE_STATE] = state
            it[CENTRE_CITY] = city
        }
    }

    /** The last service reminder raised for [owner] — see `ServiceReminderRule`. */
    suspend fun lastReminder(owner: String, channel: String = ""): String =
        context.servicePrefs.data.first()[reminderKey(owner, channel)].orEmpty()

    suspend fun setLastReminder(owner: String, key: String, channel: String = "") {
        context.servicePrefs.edit { it[reminderKey(owner, channel)] = key }
    }

    /** One record per channel (in-app, phone), so each is sent exactly once. */
    private fun reminderKey(owner: String, channel: String) =
        stringPreferencesKey("service_reminded_$owner" + if (channel.isEmpty()) "" else "_$channel")

    /** Forgets the cached reading, so a bad one is not permanent. */
    suspend fun clearOdometer() {
        context.servicePrefs.edit { it.remove(LAST_ODO_KM) }
    }

    /**
     * Stops a poisoned cache from refusing the real reading for good.
     *
     * A garbage frame that decoded high (but under the plausible ceiling) makes
     * every real, lower reading "implausible". Garbage does not repeat, but a
     * real odometer does: [THRESHOLD] refused readings in a row that never go
     * down and each rise by at most a few km are the truth, so the cache takes
     * them even though they are lower than what it holds.
     */
    class RefusedStreak {
        private var last = 0
        private var count = 0

        /** Call for a reading the forward-only rule refused. True once believed. */
        @Synchronized
        fun believe(km: Int): Boolean {
            if (km in last..(last + 5) && last > 0) count++ else count = 1
            last = km
            if (count < THRESHOLD) return false
            count = 0; last = 0
            return true
        }

        companion object { const val THRESHOLD = 3 }
    }

    companion object {
        private val REMINDERS_ON = booleanPreferencesKey("service_reminders_enabled")
        private val INTERVAL_KM = intPreferencesKey("service_interval_km")
        private val INTERVAL_DAYS = intPreferencesKey("service_interval_days")
        private val LAST_ODO_KM = intPreferencesKey("last_known_odometer_km")
        private val CENTRE_STATE = stringPreferencesKey("centre_picker_state")
        private val CENTRE_CITY = stringPreferencesKey("centre_picker_city")

        /** No Access 125 reaches this; the wire format allows up to 999,999,999. */
        const val MAX_PLAUSIBLE_ODO_KM = 200_000

        /**
         * A rider can be away from the app for a season, so the step is generous.
         * It only has to be smaller than the garbage a flipped digit produces.
         */
        const val MAX_ODO_JUMP_KM = 5_000

        /** Pure so it can be tested without DataStore. */
        fun isPlausibleOdometer(known: Int, km: Int): Boolean {
            if (km <= 0 || km > MAX_PLAUSIBLE_ODO_KM) return false
            // A cache already poisoned before this guard existed is replaced
            // rather than defended, so the stuck card heals on the next ride.
            if (known > MAX_PLAUSIBLE_ODO_KM) return true
            return km > known && (known == 0 || km - known <= MAX_ODO_JUMP_KM)
        }
    }
}
