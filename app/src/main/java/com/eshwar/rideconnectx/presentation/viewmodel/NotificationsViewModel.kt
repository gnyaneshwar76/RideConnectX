package com.eshwar.rideconnectx.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.data.local.db.NotificationEntity
import com.eshwar.rideconnectx.data.nav.ClusterAlerts
import com.eshwar.rideconnectx.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository,
) : ViewModel() {

    val notifications: StateFlow<List<NotificationEntity>> =
        repository.notifications.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    /** Grouped by day, repeats collapsed; see [groupNotifications]. */
    val sections: StateFlow<List<DaySection>> =
        notifications.map { groupNotifications(it) }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val unreadCount: StateFlow<Int> =
        repository.unreadCount.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            0,
        )

    fun markRead(id: Long) = viewModelScope.launch { repository.markRead(id) }

    fun markRead(ids: List<Long>) = viewModelScope.launch { ids.forEach { repository.markRead(it) } }

    fun markAllRead() = viewModelScope.launch { repository.markAllRead() }

    fun delete(ids: List<Long>) = viewModelScope.launch { repository.delete(ids) }

    fun clearAll() {
        // In-memory and instant: the cluster goes back to 0 missed, 0 messages.
        ClusterAlerts.resetCounts()
        viewModelScope.launch { repository.clearAll() }
    }
}
