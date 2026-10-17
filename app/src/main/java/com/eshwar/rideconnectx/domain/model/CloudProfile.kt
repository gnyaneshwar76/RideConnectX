package com.eshwar.rideconnectx.domain.model

/**
 * The parts of a Firestore user document that belong back on the device.
 *
 * Kept as a plain parser so the key names and fallbacks can be tested without a
 * Firestore instance — that is exactly where this went wrong before, with the
 * document being written correctly and never read back.
 */
data class CloudProfile(
    val riderName: String? = null,
    val nickname: String? = null,
    val location: String? = null,
    val vehicleId: String? = null,
    val colorId: String? = null,
    /** Base64 JPEG of the rider's picture — see `ProfilePhotoStore.encodeForCloud`. */
    val photoBase64: String? = null,
) {
    /**
     * Whether there is enough here to skip profile setup.
     *
     * A name alone is not enough: the dashboard renders the chosen vehicle in
     * its header and artwork, so a rider restored without one would land on a
     * broken-looking dashboard.
     */
    val isComplete: Boolean get() = !riderName.isNullOrBlank() && !vehicleId.isNullOrBlank()

    companion object {
        /**
         * Reads a `users/{uid}` document.
         *
         * Anything missing or of the wrong type comes back null rather than
         * throwing — a half-written document should send the rider to Create
         * Profile, not crash them out of signing in.
         */
        fun from(data: Map<String, Any?>?): CloudProfile {
            if (data == null) return CloudProfile()

            @Suppress("UNCHECKED_CAST")
            fun section(key: String) = data[key] as? Map<String, Any?> ?: emptyMap()

            val profile = section("profile")
            val scooter = section("scooter")

            fun text(map: Map<String, Any?>, key: String) =
                (map[key] as? String)?.takeIf { it.isNotBlank() }

            return CloudProfile(
                // `riderName` is what the rider typed and what the cluster
                // greets them by. `name` is whatever the login provider handed
                // over, so it is only a fallback.
                riderName = text(profile, "riderName") ?: text(profile, "name"),
                nickname = text(profile, "nickname"),
                location = text(profile, "location"),
                vehicleId = text(scooter, "vehicleId"),
                colorId = text(scooter, "colorId"),
                photoBase64 = text(profile, "photoBase64"),
            )
        }
    }
}
