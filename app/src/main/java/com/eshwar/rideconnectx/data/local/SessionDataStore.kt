package com.eshwar.rideconnectx.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ride_session",
    // A corrupt file reads as empty (defaults) instead of crashing every launch.
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { androidx.datastore.preferences.core.emptyPreferences() },
)

@Singleton
class SessionDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        private val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
        private val LAST_CONNECTED_TIMESTAMP = longPreferencesKey("last_connected_timestamp")
        private val IS_NAV_ACTIVE = booleanPreferencesKey("is_nav_active")
        private val NAV_DESTINATION = stringPreferencesKey("nav_destination")
        private val ONBOARDING_STATUS = stringPreferencesKey("onboarding_status")
        /** The cluster connected to *before* the current one — see [previousClusterName]. */
        private val PREVIOUS_CLUSTER_NAME = stringPreferencesKey("previous_cluster_name")

        // Last good telemetry, so the dashboard still has something true to
         // show with the scooter out of range or the app just reopened.
        private val TELE_ODO = intPreferencesKey("tele_odometer_km")
        private val TELE_TRIP_A = floatPreferencesKey("tele_trip_a_km")
        private val TELE_TRIP_B = floatPreferencesKey("tele_trip_b_km")
        private val TELE_FUEL = intPreferencesKey("tele_fuel_segments")
        private val TELE_AT = longPreferencesKey("tele_captured_at")
        private val TELE_BATT = intPreferencesKey("tele_battery_percent")

        private val PENDING_UID = stringPreferencesKey("pending_readings_uid")
        private val PENDING_ODO = intPreferencesKey("pending_odometer_km")
        private val PENDING_TRIP_A = floatPreferencesKey("pending_trip_a_km")
        private val PENDING_TRIP_B = floatPreferencesKey("pending_trip_b_km")
        private val PENDING_FUEL = intPreferencesKey("pending_fuel_segments")
        private val PENDING_AT = longPreferencesKey("pending_captured_at")
    }

    /**
     * The cluster this phone connected to on the previous session.
     *
     * Byte 27 of the profile packet is chosen by comparing this with the
     * cluster being connected to now — the same `prev_cluster` check the
     * official app makes. It decides whether the dashboard greets the rider or
     * merely reports a connection, so it has to survive a restart.
     */
    val previousClusterName: Flow<String?> = context.dataStore.data.map { it[PREVIOUS_CLUSTER_NAME] }

    suspend fun rememberCluster(name: String) {
        context.dataStore.edit { it[PREVIOUS_CLUSTER_NAME] = name }
    }

    /**
     * The last telemetry frame the cluster sent, and when.
     *
     * The dashboard used to blank every reading the moment the link dropped, and
     * started empty on each launch, so the rider saw "-" for the odometer they
     * had just been looking at. Reported 18 August: "when I disconnect the
     * scooty it is gone".
     *
     * Kept as the *last known* values with a timestamp rather than pretending
     * they are live - the screen can then say how old they are instead of
     * implying a connection that is not there.
     */
    data class CachedTelemetry(
        val odometerKm: Int,
        val tripAKm: Float,
        val tripBKm: Float,
        val fuelSegments: Int,
        val capturedAt: Long,
        /** e-ACCESS battery percent; null on petrol models and in values stored before this field. */
        val batteryPercent: Int? = null,
    )

    val cachedTelemetry: Flow<CachedTelemetry?> = context.dataStore.data.map { p ->
        val odo = p[TELE_ODO] ?: return@map null
        CachedTelemetry(
            odometerKm = odo,
            tripAKm = p[TELE_TRIP_A] ?: 0f,
            tripBKm = p[TELE_TRIP_B] ?: 0f,
            fuelSegments = p[TELE_FUEL] ?: 0,
            capturedAt = p[TELE_AT] ?: 0L,
            batteryPercent = p[TELE_BATT],
        )
    }

    suspend fun cacheTelemetry(
        odometerKm: Int,
        tripAKm: Float,
        tripBKm: Float,
        fuelSegments: Int,
        capturedAt: Long = System.currentTimeMillis(),
        batteryPercent: Int? = null,
    ) {
        context.dataStore.edit {
            it[TELE_ODO] = odometerKm
            it[TELE_TRIP_A] = tripAKm
            it[TELE_TRIP_B] = tripBKm
            it[TELE_FUEL] = fuelSegments
            it[TELE_AT] = capturedAt
            if (batteryPercent != null) it[TELE_BATT] = batteryPercent else it.remove(TELE_BATT)
        }
    }

    /**
     * Readings from a sign-out that could not reach the cloud (offline), held
     * for [uid] until that account signs in again - the cloud refuses uploads
     * once signed out. Never shown to any other account.
     *
     * ponytail: one slot - a second offline sign-out overwrites the first. Fine
     * because the scooter re-sends true values on connect; a per-uid list if
     * shared phones make that matter.
     */
    val pendingReadings: Flow<Pair<String, CachedTelemetry>?> = context.dataStore.data.map { p ->
        val uid = p[PENDING_UID] ?: return@map null
        uid to CachedTelemetry(
            odometerKm = p[PENDING_ODO] ?: return@map null,
            tripAKm = p[PENDING_TRIP_A] ?: 0f,
            tripBKm = p[PENDING_TRIP_B] ?: 0f,
            fuelSegments = p[PENDING_FUEL] ?: 0,
            capturedAt = p[PENDING_AT] ?: 0L,
        )
    }

    suspend fun savePendingReadings(uid: String, c: CachedTelemetry) {
        context.dataStore.edit {
            it[PENDING_UID] = uid
            it[PENDING_ODO] = c.odometerKm
            it[PENDING_TRIP_A] = c.tripAKm
            it[PENDING_TRIP_B] = c.tripBKm
            it[PENDING_FUEL] = c.fuelSegments
            it[PENDING_AT] = c.capturedAt
        }
    }

    suspend fun clearPendingReadings() {
        context.dataStore.edit {
            listOf(PENDING_UID, PENDING_ODO, PENDING_TRIP_A, PENDING_TRIP_B, PENDING_FUEL, PENDING_AT)
                .forEach { k -> it.remove(k) }
        }
    }

    /**
     * Forgets the last readings. Called on sign-out: they belong to the account
     * that saw them, and must not leak to the next account signed in on the
     * same phone.
     */
    suspend fun clearTelemetry() {
        context.dataStore.edit {
            it.remove(TELE_ODO)
            it.remove(TELE_TRIP_A)
            it.remove(TELE_TRIP_B)
            it.remove(TELE_FUEL)
            it.remove(TELE_AT)
            it.remove(TELE_BATT)
        }
    }

    val lastDeviceAddress: Flow<String?> = context.dataStore.data.map { it[LAST_DEVICE_ADDRESS] }
    val lastDeviceName: Flow<String?> = context.dataStore.data.map { it[LAST_DEVICE_NAME] }
    val onboardingStatus: Flow<String> = context.dataStore.data.map { 
        it[ONBOARDING_STATUS] ?: "NOT_STARTED" 
    }

    suspend fun saveOnboardingStatus(status: String) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_STATUS] = status
        }
    }

    suspend fun saveSession(address: String, name: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_DEVICE_ADDRESS] = address
            preferences[LAST_DEVICE_NAME] = name
            preferences[LAST_CONNECTED_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(LAST_DEVICE_ADDRESS)
            preferences.remove(LAST_DEVICE_NAME)
            preferences.remove(LAST_CONNECTED_TIMESTAMP)
        }
    }
    
    suspend fun saveNavState(isActive: Boolean, destination: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[IS_NAV_ACTIVE] = isActive
            destination?.let { preferences[NAV_DESTINATION] = it }
        }
    }
    
    val isNavActive: Flow<Boolean> = context.dataStore.data.map { it[IS_NAV_ACTIVE] ?: false }
    val navDestination: Flow<String?> = context.dataStore.data.map { it[NAV_DESTINATION] }
}
