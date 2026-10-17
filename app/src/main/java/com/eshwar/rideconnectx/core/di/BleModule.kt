package com.eshwar.rideconnectx.core.di

import android.os.Build
import com.eshwar.rideconnectx.BuildConfig
import com.eshwar.rideconnectx.data.repository.BleRepositoryImpl
import com.eshwar.rideconnectx.data.repository.BleRepositorySimulatorImpl
import com.eshwar.rideconnectx.domain.repository.BleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BleModule {

    /**
     * The real scooter link everywhere except a debug build on the Android
     * emulator, which has no Bluetooth: there a simulated Access 125 connects
     * and sends example readings, so screens and motion can be reviewed
     * without the vehicle. A phone always gets the real one.
     */
    @Provides
    @Singleton
    fun provideBleRepository(
        real: Provider<BleRepositoryImpl>,
        simulator: Provider<BleRepositorySimulatorImpl>,
    ): BleRepository = if (BuildConfig.DEBUG && isEmulator()) simulator.get() else real.get()

    private fun isEmulator(): Boolean =
        Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" ||
            Build.PRODUCT.startsWith("sdk") || Build.FINGERPRINT.contains("generic")
}
