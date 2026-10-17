package com.eshwar.rideconnectx.presentation.navigation

/**
 * Where the app opens to. Pure, so the rule is testable without a device.
 *
 * Setup is never skipped and never left half done: a rider signed in with
 * setup unfinished when the app last closed is signed out and starts again
 * (see [mustReset]). Splash used to send anyone signed in to the dashboard,
 * which landed a half-made account there; then setup resumed, but the rider
 * wants a clean start instead (rider, 26 Sep).
 */
object SetupGate {
    /** Signed in, setup unfinished, and no question waiting: reset on launch. */
    fun mustReset(signedIn: Boolean, profileDone: Boolean, guestMergePending: Boolean = false): Boolean =
        signedIn && !profileDone && !guestMergePending

    fun launchRoute(signedIn: Boolean, profileDone: Boolean, guestMergePending: Boolean = false): String = when {
        !signedIn -> Routes.INTRO
        // The guest-merge question is answered before anything else.
        guestMergePending -> Routes.PROFILE_FOUND
        profileDone -> Routes.DASHBOARD
        // Reset by the caller (mustReset): start over.
        else -> Routes.INTRO
    }

    /** After the Permissions step. */
    fun afterPermissions(profileDone: Boolean, guestMergePending: Boolean): String = when {
        guestMergePending -> Routes.PROFILE_FOUND
        profileDone -> Routes.DASHBOARD
        else -> Routes.VEHICLE
    }
}
