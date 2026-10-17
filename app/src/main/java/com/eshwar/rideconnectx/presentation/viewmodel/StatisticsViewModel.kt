package com.eshwar.rideconnectx.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.data.local.db.RideBucket
import com.eshwar.rideconnectx.data.local.db.RideEntity
import com.eshwar.rideconnectx.data.local.db.RideTotals
import com.eshwar.rideconnectx.data.repository.RideRepository
import com.eshwar.rideconnectx.data.repository.StatsPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val repo: RideRepository,
    private val tracker: com.eshwar.rideconnectx.data.repository.RideTracker,
    owner: com.eshwar.rideconnectx.data.local.OwnerScope,
) : ViewModel() {

    private val _period = MutableStateFlow(StatsPeriod.WEEK)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    // The ranges start at midnight, so a new day must re-query (Today / This Week
    // went stale after midnight). Polls once a minute only while the screen collects.
    private val today = flow {
        while (true) { emit(LocalDate.now()); delay(60_000) }
    }.distinctUntilChanged()
    private val window = combine(_period, today) { p, _ -> p }

    // Each stream re-subscribes when the filter or the day changes.
    val totals: StateFlow<RideTotals> = window
        .flatMapLatest { repo.observeTotals(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RideTotals(0, 0, 0L, 0.0))

    val rides: StateFlow<List<RideEntity>> = window
        .flatMapLatest { repo.observeRides(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val buckets: StateFlow<List<RideBucket>> = window
        .flatMapLatest { repo.observeBuckets(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val live = tracker.live
    val locationOff = tracker.locationOff
    val isGuest: StateFlow<Boolean> = owner.isGuest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setPeriod(p: StatsPeriod) { _period.value = p }
}
