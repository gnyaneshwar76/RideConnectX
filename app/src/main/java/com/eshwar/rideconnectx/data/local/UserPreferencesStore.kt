package com.eshwar.rideconnectx.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eshwar.rideconnectx.domain.model.LoginMethod
import com.eshwar.rideconnectx.domain.model.UserSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefs: DataStore<Preferences> by preferencesDataStore(
    name = "user_prefs",
    // A corrupt file reads as empty (defaults) instead of crashing every launch.
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { androidx.datastore.preferences.core.emptyPreferences() },
)

/**
 * Everything about *who* is using the app: identity, legal acceptance and
 * onboarding progress.
 *
 * Deliberately separate from [SessionDataStore], which tracks the *vehicle*
 * connection. Clearing one should never clear the other.
 */
@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        val NAME = stringPreferencesKey("user_name")
        val EMAIL = stringPreferencesKey("user_email")
        val METHOD = stringPreferencesKey("login_method")
        val UID = stringPreferencesKey("user_uid")
        val PHOTO_URL = stringPreferencesKey("user_photo_url")

        val TERMS_ACCEPTED = booleanPreferencesKey("terms_accepted")
        val TERMS_VERSION = stringPreferencesKey("terms_version")
        val TERMS_AT = longPreferencesKey("terms_accepted_at")

        val PRIVACY_ACCEPTED = booleanPreferencesKey("privacy_accepted")
        val PRIVACY_VERSION = stringPreferencesKey("privacy_version")
        val PRIVACY_AT = longPreferencesKey("privacy_accepted_at")

        val PERMISSIONS_DONE = booleanPreferencesKey("permissions_completed")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")

        val VEHICLE_ID = stringPreferencesKey("selected_vehicle_id")
        val COLOR_ID = stringPreferencesKey("selected_color_id")

        val RIDER_NAME = stringPreferencesKey("rider_name")

        /**
         * The short name the dashboard greets the rider by.
         *
         * Separate from [RIDER_NAME] because the two have different jobs: the
         * full name is what the cluster prints and what the account is under,
         * and it is routinely too long for the dashboard header — "Gnyaneshwar
         * .P" already overflows it. The rider picks something that fits.
         */
        val RIDER_NICKNAME = stringPreferencesKey("rider_nickname")
        val RIDER_LOCATION = stringPreferencesKey("rider_location")
        val PROFILE_DONE = booleanPreferencesKey("profile_completed")
        /** A guest signed into an account with a profile and has not yet said whether to merge. */
        val GUEST_MERGE_ASK = booleanPreferencesKey("guest_merge_ask")

        /** Whose profile the rider fields above are: a uid, or `OwnerScope.GUEST`. */
        val PROFILE_OWNER = stringPreferencesKey("profile_owner")

        /** Set by "Save to / Move to another account": the next account takes this profile. */
        val PROFILE_CARRY = booleanPreferencesKey("profile_carry")
    }

    /** Version stamped on acceptance, so a future revision can re-prompt. */
    val legalVersion: String = "1.0"

    /**
     * The uid is part of this: without it `UserSession.syncsToCloud` is always
     * false, which silently skips every cloud write that reads the session from
     * here, and makes the auth-state comparison against the Firebase user never
     * match.
     */
    val session: Flow<UserSession> = context.userPrefs.data.map { p ->
        UserSession(
            name = p[NAME].orEmpty(),
            email = p[EMAIL].orEmpty(),
            method = runCatching { LoginMethod.valueOf(p[METHOD] ?: "NONE") }
                .getOrDefault(LoginMethod.NONE),
            uid = p[UID].orEmpty(),
            photoUrl = p[PHOTO_URL].orEmpty(),
        )
    }

    val termsAccepted: Flow<Boolean> = context.userPrefs.data.map { it[TERMS_ACCEPTED] ?: false }
    val privacyAccepted: Flow<Boolean> = context.userPrefs.data.map { it[PRIVACY_ACCEPTED] ?: false }
    val permissionsCompleted: Flow<Boolean> = context.userPrefs.data.map { it[PERMISSIONS_DONE] ?: false }
    val onboardingCompleted: Flow<Boolean> = context.userPrefs.data.map { it[ONBOARDING_DONE] ?: false }

    suspend fun saveSession(session: UserSession) {
        context.userPrefs.edit {
            it[NAME] = session.name
            it[EMAIL] = session.email
            it[METHOD] = session.method.name
            it[UID] = session.uid
            it[PHOTO_URL] = session.photoUrl
        }
    }

    suspend fun clearSession() {
        context.userPrefs.edit {
            it.remove(NAME); it.remove(EMAIL); it.remove(METHOD)
            it.remove(UID); it.remove(PHOTO_URL)
        }
    }

    /**
     * Clears the rider profile that outlives the account session.
     *
     * [clearSession] removes only the account identity. Everything the *cluster*
     * knows the rider by — name, nickname, city, vehicle and colour — used to
     * survive a sign-out, and `AuthRepositoryImpl.persist` then merge-wrote it
     * into the next uid's Firestore document for any account with no document
     * of its own. The carry-over is deliberate for guest-to-sign-up and for an
     * email change; neither of those is a sign-out, so clearing here leaves
     * them working.
     */
    suspend fun clearProfile() {
        context.userPrefs.edit {
            it.remove(RIDER_NAME); it.remove(RIDER_NICKNAME); it.remove(RIDER_LOCATION)
            it.remove(VEHICLE_ID); it.remove(COLOR_ID); it.remove(PROFILE_DONE)
            it.remove(PROFILE_OWNER); it.remove(PROFILE_CARRY)
        }
    }

    /**
     * Who the local profile belongs to. Blank for a profile saved before this
     * existed, which is treated as nobody's — see `ProfileHandover`.
     */
    val profileOwner: Flow<String> = context.userPrefs.data.map { it[PROFILE_OWNER].orEmpty() }
    val profileCarry: Flow<Boolean> = context.userPrefs.data.map { it[PROFILE_CARRY] ?: false }

    suspend fun setProfileOwner(owner: String, carry: Boolean = false) = context.userPrefs.edit {
        it[PROFILE_OWNER] = owner
        it[PROFILE_CARRY] = carry
    }

    suspend fun acceptTerms() = context.userPrefs.edit {
        it[TERMS_ACCEPTED] = true
        it[TERMS_VERSION] = legalVersion
        it[TERMS_AT] = System.currentTimeMillis()
    }

    suspend fun acceptPrivacy() = context.userPrefs.edit {
        it[PRIVACY_ACCEPTED] = true
        it[PRIVACY_VERSION] = legalVersion
        it[PRIVACY_AT] = System.currentTimeMillis()
    }

    suspend fun setPermissionsCompleted(done: Boolean) = context.userPrefs.edit {
        it[PERMISSIONS_DONE] = done
    }

    suspend fun setOnboardingCompleted(done: Boolean) = context.userPrefs.edit {
        it[ONBOARDING_DONE] = done
    }

    // ── Vehicle selection ──────────────────────────────────────────

    val selectedVehicleId: Flow<String?> = context.userPrefs.data.map { it[VEHICLE_ID] }
    val selectedColorId: Flow<String?> = context.userPrefs.data.map { it[COLOR_ID] }

    suspend fun saveVehicle(vehicleId: String) = context.userPrefs.edit {
        it[VEHICLE_ID] = vehicleId
        // A colour belongs to one model, so changing model invalidates it.
        it.remove(COLOR_ID)
    }

    suspend fun saveColor(colorId: String) = context.userPrefs.edit {
        it[COLOR_ID] = colorId
    }

    // ── Rider profile ──────────────────────────────────────────────
    //
    // Captured on the Create Profile step, which runs once between sign-in and
    // the dashboard. The name is what the cluster greets the rider with, so it
    // is kept here rather than derived from the account — a Google display name
    // is often a full legal name, which reads badly on a small display.

    val riderName: Flow<String> = context.userPrefs.data.map { it[RIDER_NAME].orEmpty() }
    val riderLocation: Flow<String> = context.userPrefs.data.map { it[RIDER_LOCATION].orEmpty() }
    val riderNickname: Flow<String> = context.userPrefs.data.map { it[RIDER_NICKNAME].orEmpty() }
    val profileCompleted: Flow<Boolean> = context.userPrefs.data.map { it[PROFILE_DONE] ?: false }

    suspend fun saveRiderName(name: String) = context.userPrefs.edit {
        it[RIDER_NAME] = name.trim()
    }

    suspend fun saveRiderNickname(nickname: String) = context.userPrefs.edit {
        it[RIDER_NICKNAME] = nickname.trim()
    }

    suspend fun saveRiderLocation(location: String) = context.userPrefs.edit {
        it[RIDER_LOCATION] = location.trim()
    }

    suspend fun setProfileCompleted(done: Boolean) = context.userPrefs.edit {
        it[PROFILE_DONE] = done
    }

    /**
     * Kept on disk, not in memory, so leaving the app at the question brings
     * the rider back to it rather than past it.
     */
    val guestMergePending: Flow<Boolean> = context.userPrefs.data.map { it[GUEST_MERGE_ASK] ?: false }
    suspend fun setGuestMergePending(pending: Boolean) = context.userPrefs.edit { it[GUEST_MERGE_ASK] = pending }
}
