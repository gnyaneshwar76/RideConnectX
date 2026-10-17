package com.eshwar.rideconnectx.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release builds attest through Play Integrity, so Firebase can tell a genuine
 * Play-installed copy of this app from a script someone wrote after unzipping
 * the APK and reading google-services.json out of it.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory =
    PlayIntegrityAppCheckProviderFactory.getInstance()
