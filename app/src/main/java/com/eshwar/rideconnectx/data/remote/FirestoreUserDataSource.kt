package com.eshwar.rideconnectx.data.remote

import com.eshwar.rideconnectx.domain.model.UserSession
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud copy of everything that should follow a rider between devices.
 *
 * Schema:
 *   users/{uid}
 *     profile      { name, email, photoUrl, loginMethod, createdAt, lastLoginAt }
 *     settings     { theme, units, voiceGuidance, autoConnect }
 *     scooter      { vehicleId, colorId, lastDeviceAddress, lastConnectedAt }
 *     preferences  { mapType, avoidTolls, avoidHighways, notifications }
 *   users/{uid}/favorites/{placeId}
 *   users/{uid}/rides/{r0..r9}   (kept GPS rides, max 10)
 *
 * Profile fields live in maps on the user document rather than sub-documents so
 * a single read restores the whole account. Favorites and rides grow unbounded,
 * so they are subcollections.
 */
@Singleton
class FirestoreUserDataSource @Inject constructor(
    /** Null when the build has no google-services.json: every call then reports offline. */
    private val dbOrNull: FirebaseFirestore?,
) : com.eshwar.rideconnectx.data.repository.RideCloud {
    @Volatile private var current: FirebaseFirestore? = dbOrNull
    private val db: FirebaseFirestore
        get() = current ?: throw CloudNotConfiguredException()

    /**
     * Erases the offline copy of the account (sign-out, account delete): terminate,
     * then clearPersistence, then a fresh instance so later calls still work.
     * Never throws; the cache is best effort.
     */
    suspend fun clearLocalCache() {
        val old = current ?: return
        runCatching {
            old.terminate().await()
            old.clearPersistence().await()
        }
        current = runCatching { com.eshwar.rideconnectx.core.di.FirebaseModule.newFirestore() }.getOrNull()
    }

    private companion object {
        // Writes wait at most 8 s (see awaitBounded): Firestore queues them offline.
        const val USERS = "users"
        const val FAVORITES = "favorites"
        const val RIDES = "rides"

        const val PROFILE = "profile"
        const val SETTINGS = "settings"
        const val SCOOTER = "scooter"
        const val PREFERENCES = "preferences"
        val RIDE_SLOTS = (0 until 10).map { "r$it" }
    }

    private fun userDoc(uid: String) = db.collection(USERS).document(uid)

    /** Null when the read failed (offline), which is not the same as "no account". */
    suspend fun exists(uid: String): Boolean? =
        runCatching { userDoc(uid).get().awaitBounded().exists() }.getOrNull()

    /** Called on first sign-in. Seeds defaults without overwriting anything later. */
    suspend fun createUser(session: UserSession): Result<Unit> = runCatching {
        userDoc(session.uid).set(
            mapOf(
                PROFILE to mapOf(
                    "name" to session.name,
                    "email" to session.email,
                    "photoUrl" to session.photoUrl,
                    "loginMethod" to session.method.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                ),
                SETTINGS to mapOf(
                    "theme" to "system",
                    "units" to "metric",
                    "voiceGuidance" to true,
                    "autoConnect" to true,
                ),
                SCOOTER to emptyMap<String, Any>(),
                PREFERENCES to mapOf(
                    "mapType" to "normal",
                    "avoidTolls" to false,
                    "avoidHighways" to false,
                    "notifications" to true,
                ),
            )
        ).awaitBounded()
    }

    /** Called on every subsequent sign-in. Merges, so nothing is lost. */
    suspend fun touchLogin(session: UserSession): Result<Unit> = runCatching {
        userDoc(session.uid).set(
            mapOf(
                PROFILE to mapOf(
                    "name" to session.name,
                    "email" to session.email,
                    "photoUrl" to session.photoUrl,
                    "loginMethod" to session.method.name,
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                )
            ),
            SetOptions.merge(),
        ).awaitBounded()
    }

    /** One read that restores the whole account after a fresh install. */
    suspend fun fetchUser(uid: String): Result<Map<String, Any>?> = runCatching {
        userDoc(uid).get().awaitBounded().data
    }

    /** Merges individual profile fields, e.g. the rider name and city. */
    suspend fun updateProfileFields(uid: String, fields: Map<String, Any?>): Result<Unit> =
        runCatching {
            userDoc(uid).set(mapOf(PROFILE to fields), SetOptions.merge()).awaitBounded()
        }

    suspend fun updateSettings(uid: String, settings: Map<String, Any>): Result<Unit> =
        runCatching {
            userDoc(uid).set(mapOf(SETTINGS to settings), SetOptions.merge()).awaitBounded()
        }

    suspend fun updateScooter(uid: String, scooter: Map<String, Any?>): Result<Unit> =
        runCatching {
            userDoc(uid).set(mapOf(SCOOTER to scooter), SetOptions.merge()).awaitBounded()
        }

    suspend fun updatePreferences(uid: String, preferences: Map<String, Any>): Result<Unit> =
        runCatching {
            userDoc(uid).set(mapOf(PREFERENCES to preferences), SetOptions.merge()).awaitBounded()
        }

    suspend fun addFavorite(uid: String, placeId: String, place: Map<String, Any>): Result<Unit> =
        runCatching {
            userDoc(uid).collection(FAVORITES).document(placeId).set(place).awaitBounded()
        }

    suspend fun removeFavorite(uid: String, placeId: String): Result<Unit> = runCatching {
        userDoc(uid).collection(FAVORITES).document(placeId).delete().awaitBounded()
    }

    suspend fun fetchFavorites(uid: String): Result<List<Map<String, Any>>> = runCatching {
        userDoc(uid).collection(FAVORITES).get().awaitBounded().documents.mapNotNull { it.data }
    }

    // Ride cloud: fixed doc ids r0..r9 (the rules refuse any other id, so max 10 rides).
    // With no FirebaseApp, db throws inside runCatching and each call soft-fails.
    override suspend fun fetchRideSlots(uid: String): Result<Map<String, Long>> = runCatching {
        userDoc(uid).collection(RIDES)
            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), RIDE_SLOTS)
            .get().awaitBounded().documents
            .mapNotNull { d -> d.getLong("startedAt")?.let { d.id to it } }.toMap()
    }

    override suspend fun createRideInSlot(uid: String, slot: String, ride: Map<String, Any>): Result<Unit> =
        runCatching {
            require(slot in RIDE_SLOTS)
            userDoc(uid).collection(RIDES).document(slot).set(ride).awaitBounded()
        }

    override suspend fun deleteRideSlot(uid: String, slot: String): Result<Unit> = runCatching {
        require(slot in RIDE_SLOTS)
        userDoc(uid).collection(RIDES).document(slot).delete().awaitBounded()
    }

    /**
     * Deletes the account's cloud data. Firestore does not cascade: deleting
     * users/{uid} alone left favorites and rides behind, orphaned but still
     * stored. Subcollections go first, in batches under Firestore's 500-write
     * limit, then the document itself. Safe to repeat if interrupted.
     */
    suspend fun deleteUser(uid: String): Result<Unit> = runCatching {
        for (sub in listOf(FAVORITES, RIDES)) {
            userDoc(uid).collection(sub).get().awaitBounded().documents.chunked(400).forEach { chunk ->
                db.batch().apply { chunk.forEach { delete(it.reference) } }.commit().awaitBounded()
            }
        }
        userDoc(uid).delete().awaitBounded()
    }
}

private class Boxed<T>(val value: T)

/**
 * await() that gives up after 8 s and reports "no connection" as an
 * [IOException], so callers can tell offline from a real refusal.
 * The result is boxed: a write is a Task<Void> whose result is null, and a
 * missing document's data is null; neither is a timeout.
 */
internal suspend fun <T> Task<T>.awaitBounded(waitMs: Long = 8_000L): T {
    try {
        val box = withTimeoutOrNull(waitMs) { Boxed(await()) } ?: throw IOException("Timed out waiting for the cloud")
        return box.value
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            e.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
        ) throw IOException(e)
        throw e
    }
}
