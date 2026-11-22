package com.eshwar.rideconnectx.presentation.navigation

/**
 * Where the app opens to. Pure, so the rule is testable without a device.
 *
 * First open is two steps: the Welcome page, then Home. A guest ("Rider") is
 * made by Start, so there is no sign-in wall, no tour, no Create Profile and no
 * Permissions page (22 Nov 2026). Every later open goes straight to Home.
 */
object SetupGate {
    fun launchRoute(signedIn: Boolean, guestMergePending: Boolean = false): String = when {
        !signedIn -> Routes.INTRO
        // The guest-merge question is answered before anything else.
        guestMergePending -> Routes.PROFILE_FOUND
        else -> Routes.DASHBOARD
    }
}
