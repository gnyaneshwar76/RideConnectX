package com.eshwar.rideconnectx.domain.usecase

import com.eshwar.rideconnectx.domain.repository.BleRepository
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ConnectToDeviceUseCase @Inject constructor(
    private val repository: BleRepository
) {
    operator fun invoke(address: String): Flow<ConnectionState> = repository.connect(address)
}
