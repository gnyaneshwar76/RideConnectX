package com.eshwar.rideconnectx.domain.model

enum class OnboardingStatus {
    NOT_STARTED,
    WELCOME_COMPLETED,
    PERMISSION_GRANTED,
    PAIRING_COMPLETED,
    FINISHED
}

/** Rules for the automatic first open: a guest made by the app, and the model read from the Bluetooth name. */
object AutoSetup {
    /** Valid under [GuestNameRules]; the rider can rename it on Profile. */
    const val GUEST_NAME = "Rider"

    /** Make a guest only when nobody is signed in; a Google or guest session is never replaced. */
    fun needsGuest(local: UserSession): Boolean = !local.isSignedIn

    /**
     * Catalogue id to save for a connected scooter, or null (save nothing, ask nothing)
     * when a model is already saved or the name is not a known one.
     */
    fun vehicleIdFor(deviceName: String?, savedVehicleId: String?): String? {
        if (VehicleCatalog.byId(savedVehicleId) != null) return null
        if (com.eshwar.rideconnectx.domain.ProtocolEngine.profileFor(deviceName).model.isEmpty()) return null
        return VehicleCatalog.byDeviceName(deviceName)?.id
    }
}
