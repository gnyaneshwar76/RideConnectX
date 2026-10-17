package com.eshwar.rideconnectx.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.safetyPrefs: DataStore<Preferences> by preferencesDataStore(
    name = "safety_prefs",
    // A corrupt file reads as empty (defaults) instead of crashing every launch.
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { androidx.datastore.preferences.core.emptyPreferences() },
)

/** The rider's safety settings. Local only — nothing here needs a network. */
@Singleton
class SafetyPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        val SOS_ENABLED = booleanPreferencesKey("sos_enabled")
        val SHARE_LOCATION = booleanPreferencesKey("sos_share_location")
        val HELMET_REMINDER = booleanPreferencesKey("helmet_reminder")
        val ACKNOWLEDGED_AT = longPreferencesKey("safety_acknowledged_at")
    }

    val sosEnabled: Flow<Boolean> = context.safetyPrefs.data.map { it[SOS_ENABLED] ?: true }

    val shareLocation: Flow<Boolean> = context.safetyPrefs.data.map { it[SHARE_LOCATION] ?: true }

    val helmetReminder: Flow<Boolean> = context.safetyPrefs.data.map { it[HELMET_REMINDER] ?: true }

    /** When the rider last read the safety information. 0 = never. */
    val acknowledgedAt: Flow<Long> = context.safetyPrefs.data.map { it[ACKNOWLEDGED_AT] ?: 0L }

    suspend fun setSosEnabled(enabled: Boolean) {
        context.safetyPrefs.edit { it[SOS_ENABLED] = enabled }
    }

    suspend fun setShareLocation(enabled: Boolean) {
        context.safetyPrefs.edit { it[SHARE_LOCATION] = enabled }
    }

    suspend fun setHelmetReminder(enabled: Boolean) {
        context.safetyPrefs.edit { it[HELMET_REMINDER] = enabled }
    }

    suspend fun acknowledgeNow() {
        context.safetyPrefs.edit { it[ACKNOWLEDGED_AT] = System.currentTimeMillis() }
    }
}
