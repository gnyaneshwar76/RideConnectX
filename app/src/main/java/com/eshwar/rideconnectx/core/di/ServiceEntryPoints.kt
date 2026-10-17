package com.eshwar.rideconnectx.core.di

import com.eshwar.rideconnectx.data.repository.NavigationRelay
import com.eshwar.rideconnectx.domain.repository.BleRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Dependencies for the app's Services, fetched rather than field-injected.
 *
 * `@AndroidEntryPoint` field injection into a Service makes Hilt's Java
 * compile step read the Kotlin metadata of the class, which the pinned Hilt
 * version cannot parse for Kotlin 2.2. Pulling the dependency through an
 * entry point sidesteps that without moving anyone's dependency versions —
 * the object graph and the lifetimes are identical either way.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceEntryPoint {
    fun bleRepository(): BleRepository
    fun navigationRelay(): NavigationRelay
    fun rideLog(): com.eshwar.rideconnectx.data.nav.RideLog
    fun phoneStatusProvider(): com.eshwar.rideconnectx.data.ble.PhoneStatusProvider
    fun serviceReminder(): com.eshwar.rideconnectx.data.repository.ServiceReminder
    fun connectionAlerts(): com.eshwar.rideconnectx.data.repository.ConnectionAlerts
    fun rideTracker(): com.eshwar.rideconnectx.data.repository.RideTracker
}
