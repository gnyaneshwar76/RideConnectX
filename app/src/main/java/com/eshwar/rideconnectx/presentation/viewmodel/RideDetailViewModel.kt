package com.eshwar.rideconnectx.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.core.di.ApplicationScope
import com.eshwar.rideconnectx.data.local.OwnerScope
import com.eshwar.rideconnectx.data.local.db.RideEntity
import com.eshwar.rideconnectx.data.repository.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RideDetailViewModel @Inject constructor(
    state: SavedStateHandle,
    private val repo: RideRepository,
    owner: OwnerScope,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val id: Long = state["id"] ?: -1L

    val ride: StateFlow<RideEntity?> = repo.observeRide(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isGuest: StateFlow<Boolean> = owner.isGuest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // Outlives the screen: Delete pops back at once, the write must still finish.
    fun keep() { appScope.launch { repo.keep(id) } }
    fun delete() { appScope.launch { repo.delete(id) } }
}
