package com.eshwar.rideconnectx.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds cannot be attested by Play Integrity — the vehicle is nowhere
 * near Play, and the build is sideloaded. The debug provider prints a token in
 * logcat which has to be registered once in the Firebase console, after which
 * this build is allowed through.
 *
 * The debug App Check library is a `debugImplementation`, so it does not exist
 * on the release compile classpath. That is why this lives in a source set
 * rather than behind a `BuildConfig.DEBUG` branch.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory =
    DebugAppCheckProviderFactory.getInstance()
