package com.eshwar.rideconnectx.presentation.viewmodel

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.core.di.ApplicationScope
import com.eshwar.rideconnectx.core.util.CityLocator
import com.eshwar.rideconnectx.data.local.UserPreferencesStore
import com.eshwar.rideconnectx.data.local.db.EmergencyContactEntity
import com.eshwar.rideconnectx.data.repository.ContactError
import com.eshwar.rideconnectx.data.repository.SafetyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.Locale

/** What "share my location" produced — a link, or a reason there isn't one. */
sealed interface LocationShare {
    /**
     * @param accuracyMetres how tight the fix is, so the sheet can warn when it
     *        is loose rather than presenting a vague position as exact.
     */
    data class Ready(val text: String, val accuracyMetres: Int?) : LocationShare
    data object Unavailable : LocationShare
}

@HiltViewModel
class SafetyViewModel @Inject constructor(
    private val repository: SafetyRepository,
    private val locator: CityLocator,
    userPrefs: UserPreferencesStore,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    /** Named in the SOS message, so it says who needs help. */
    private val riderName: StateFlow<String> = userPrefs.session
        .map { it.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val contacts: StateFlow<List<EmergencyContactEntity>> =
        repository.contacts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val primaryContact: StateFlow<EmergencyContactEntity?> =
        repository.primaryContact.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sosEnabled: StateFlow<Boolean> =
        repository.sosEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val shareLocation: StateFlow<Boolean> =
        repository.shareLocation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val helmetReminder: StateFlow<Boolean> =
        repository.helmetReminder.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _locating = MutableStateFlow(false)
    /** True while a fix is being waited on, so the button can say so. */
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    /**
     * False when the rider granted "Approximate" instead of "Precise".
     *
     * Android 12 and later let them choose, and approximate is accurate to
     * roughly a city block — useless for sending help to. The screen offers to
     * fix it rather than quietly sharing a vague position.
     */
    val hasPreciseLocation: Boolean get() = locator.hasPreciseLocation

    /** At most three. More than that is a list nobody maintains. */
    val contactLimit: Int get() = SafetyRepository.MAX_CONTACTS

    val contactsFull: StateFlow<Boolean> = repository.contacts
        .map { it.size >= SafetyRepository.MAX_CONTACTS }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Saves a contact. Returns null on success, or why it was refused.
     *
     * On [appScope] because saving dismisses the sheet, and work that must
     * outlive a screen cannot run on that screen's scope.
     */
    suspend fun saveContact(id: Long, name: String, phone: String): ContactError? =
        appScope.async { repository.save(id, name, phone) }.await()

    fun delete(contact: EmergencyContactEntity) {
        appScope.launch { repository.delete(contact) }
    }

    fun setPrimary(id: Long) {
        appScope.launch { repository.setPrimary(id) }
    }

    fun setSosEnabled(enabled: Boolean) = appScope.launch { repository.setSosEnabled(enabled) }
    fun setShareLocation(enabled: Boolean) = appScope.launch { repository.setShareLocation(enabled) }
    fun setHelmetReminder(enabled: Boolean) = appScope.launch { repository.setHelmetReminder(enabled) }

    fun acknowledgeSafetyInfo() = appScope.launch { repository.acknowledgeSafetyInfo() }

    private var prefetch: Deferred<Location?>? = null
    private var prefetchedAt = 0L

    /**
     * Starts looking for a fix as the SOS sheet opens, so it is usually ready
     * by the tap — the share took 3-4 s from the tap (rider, 26 Sep). Only a
     * precise fix is worth it; the share itself still refuses approximate.
     */
    fun prefetchLocation() {
        if (!hasPreciseLocation || prefetch?.isActive == true) return
        prefetchedAt = System.currentTimeMillis()
        prefetch = viewModelScope.async { locator.currentLocation() }
    }

    /**
     * Builds the message SOS shares. Never invents coordinates — with no fix it
     * reports [LocationShare.Unavailable] so the rider is told rather than
     * sending a message that says nothing.
     */
    fun buildLocationMessage(onResult: (LocationShare) -> Unit) {
        viewModelScope.launch {
            _locating.value = true
            // A revoked permission or a failed lookup means "no fix", never a crash
            // or a button stuck on "locating" (CHK-25).
            val fix = try {
                fetchFix()
            } finally {
                _locating.value = false
            }
            if (fix == null) {
                onResult(LocationShare.Unavailable)
                return@launch
            }
            val who = riderName.value.ifBlank { "A rider" }
            buildResult(fix, who, onResult)
        }
    }

    private suspend fun fetchFix(): Location? {
        try {
            // The fix started when the sheet opened, if it is still recent;
            // otherwise a fresh one. Used once, so the next share looks again.
            val early = prefetch?.takeIf { System.currentTimeMillis() - prefetchedAt < PREFETCH_VALID_MS }
            prefetch = null
            return early?.await() ?: locator.currentLocation()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
    }

    private fun buildResult(fix: Location, who: String, onResult: (LocationShare) -> Unit) {
        run {
            // Locale.US, not the device locale. A comma-decimal locale (de, fr,
            // ru, pt-BR, id...) formats 48.8566 as "48,856600", and the link
            // becomes ?q=48,856600,2,352200 — four comma-separated tokens, not a
            // coordinate pair. The one message this feature exists to deliver
            // would point nowhere, and only for riders whose phone is set to
            // one of those locales.
            val lat = String.format(Locale.US, "%.6f", fix.latitude)
            val lon = String.format(Locale.US, "%.6f", fix.longitude)
            val accuracy = if (fix.hasAccuracy()) fix.accuracy.toInt() else null

            // The accuracy goes in the message. Someone reading "accurate to
            // about 500 m" knows to look around; the same link without it reads
            // as a doorstep, which is how a shared location ends up two streets
            // away with nobody realising.
            val precision = accuracy?.let { " (accurate to about $it m)" }.orEmpty()

            onResult(
                LocationShare.Ready(
                    text = "$who may need help. Location$precision: " +
                        "https://maps.google.com/?q=$lat,$lon",
                    accuracyMetres = accuracy,
                )
            )
        }
    }

    private companion object {
        /** A fix fetched earlier than this is looked for again at the tap. */
        const val PREFETCH_VALID_MS = 60_000L
    }
}
