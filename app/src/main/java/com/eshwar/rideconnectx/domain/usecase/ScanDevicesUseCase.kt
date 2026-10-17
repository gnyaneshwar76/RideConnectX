package com.eshwar.rideconnectx.domain.usecase

import com.eshwar.rideconnectx.domain.model.BleDevice
import com.eshwar.rideconnectx.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScanDevicesUseCase @Inject constructor(
    private val repository: BleRepository
) {
    operator fun invoke(): Flow<List<BleDevice>> = repository.scanDevices()
}
