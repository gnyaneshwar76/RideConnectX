package com.eshwar.rideconnectx.data.repository

import android.util.Log
import com.eshwar.rideconnectx.core.di.ApplicationScope
import com.eshwar.rideconnectx.core.util.ProfilePhotoStore
import com.eshwar.rideconnectx.data.local.UserPreferencesStore
import com.eshwar.rideconnectx.data.remote.FirestoreUserDataSource
import com.eshwar.rideconnectx.domain.model.Vehicle
import com.eshwar.rideconnectx.domain.model.VehicleCatalog
import com.eshwar.rideconnectx.domain.model.VehicleColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** The rider's chosen model and paint, resolved from stored ids. */
data class VehicleSelection(
    val vehicle: Vehicle? = null,
    val color: VehicleColor? = null,
) {
    val isComplete: Boolean get() = vehicle != null && color != null
}

/**
 * Owns the supported line-up and the rider's choice.
 *
 * The catalogue itself is static ([VehicleCatalog]) — no network needed — while
 * the selection lives in DataStore so it survives restarts.
 */
@Singleton
class VehicleRepository @Inject constructor(
    private val prefs: UserPreferencesStore,
    private val firestore: FirestoreUserDataSource,
    private val photoStore: ProfilePhotoStore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    val catalog: VehicleCatalog get() = VehicleCatalog

    val selectedVehicle: Flow<Vehicle?> =
        prefs.selectedVehicleId.map { VehicleCatalog.byId(it) }

    val selection: Flow<VehicleSelection> =
        combine(prefs.selectedVehicleId, prefs.selectedColorId) { vId, cId ->
            VehicleSelection(
                vehicle = VehicleCatalog.byId(vId),
                color = VehicleCatalog.colorById(vId, cId),
            )
        }

    suspend fun selectVehicle(vehicle: Vehicle) = prefs.saveVehicle(vehicle.id)

    suspend fun selectColor(color: VehicleColor) = prefs.saveColor(color.id)

    /**
     * Mirrors the finished profile to Firestore so it follows the rider to a new
     * device. Guests have no uid and are skipped — their profile stays local.
     *
     * **Everything** runs on [appScope], including the two reads.
     *
     * They used to run on the caller's scope, and `saveProfile` navigates away
     * the instant it returns — which clears the ViewModel and cancels that
     * scope. If either read was still suspended at that moment the write was
     * never even queued, and the cloud kept the *previous* profile while the
     * device showed the new one. Renaming a profile appeared not to save.
     *
     * This is the third place the same mistake appeared, after email sign-up and
     * Google sign-in: work that must outlive the screen cannot start on the
     * screen's scope.
     */
    fun syncSelectionToCloud(riderName: String, location: String) {
        appScope.launch {
            val session = prefs.session.first()
            if (!session.syncsToCloud) {
                Log.d(TAG, "Guest or no uid — profile stays local")
                return@launch
            }

            val current = selection.first()

            firestore.updateScooter(
                session.uid,
                mapOf(
                    "vehicleId" to current.vehicle?.id,
                    "vehicleName" to current.vehicle?.name,
                    "colorId" to current.color?.id,
                    "colorName" to current.color?.name,
                ),
            ).fold(
                onSuccess = { Log.d(TAG, "Scooter synced: ${current.vehicle?.name}") },
                onFailure = { Log.e(TAG, "Scooter sync failed", it) },
            )

            // The picture travels with the account too, so signing in on
            // another phone brings the rider's face back along with their name.
            // Null when there is no picture — and null is *not* written, or
            // saving a profile from a device where the photo had not been
            // restored yet would wipe it from the account.
            val fields = buildMap<String, Any> {
                put("riderName", riderName)
                put("nickname", prefs.riderNickname.first())
                put("location", location)
                photoStore.encodeForCloud()?.let { put("photoBase64", it) }
            }

            firestore.updateProfileFields(session.uid, fields).fold(
                onSuccess = {
                    Log.d(
                        TAG,
                        "Profile synced: riderName='$riderName' " +
                            "photo=${fields.containsKey("photoBase64")}",
                    )
                },
                onFailure = { Log.e(TAG, "Profile sync failed", it) },
            )
        }
    }

    /** A removed picture; blank reads back as "no photo" (see CloudProfile). */
    fun clearPhotoInCloud() {
        appScope.launch {
            val session = prefs.session.first()
            if (!session.syncsToCloud) return@launch
            firestore.updateProfileFields(session.uid, mapOf("photoBase64" to "")).fold(
                onSuccess = { Log.d(TAG, "Avatar removed from cloud") },
                onFailure = { Log.e(TAG, "Avatar removal sync failed", it) },
            )
        }
    }

    /**
     * Sends just the picture, for when that is the only thing that changed.
     *
     * Deliberately takes no arguments: a caller changing an avatar has no
     * business restating the name and city, and passing them stale would
     * overwrite the real ones. Firestore merges the single field.
     */
    fun syncPhotoToCloud() {
        appScope.launch {
            val session = prefs.session.first()
            if (!session.syncsToCloud) return@launch
            val encoded = photoStore.encodeForCloud() ?: return@launch
            firestore.updateProfileFields(session.uid, mapOf("photoBase64" to encoded)).fold(
                onSuccess = { Log.d(TAG, "Avatar synced") },
                onFailure = { Log.e(TAG, "Avatar sync failed", it) },
            )
        }
    }

    private companion object { const val TAG = "RCX-Vehicle" }
}
