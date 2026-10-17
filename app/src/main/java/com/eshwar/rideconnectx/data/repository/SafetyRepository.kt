package com.eshwar.rideconnectx.data.repository

import com.eshwar.rideconnectx.data.local.OwnerScope
import com.eshwar.rideconnectx.domain.model.FieldRules
import com.eshwar.rideconnectx.data.local.SafetyPreferencesStore
import com.eshwar.rideconnectx.data.local.db.EmergencyContactDao
import com.eshwar.rideconnectx.data.local.db.EmergencyContactEntity
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

/** Why a contact was refused, so the form can say so rather than just failing. */
sealed interface ContactError {
    data object NameMissing : ContactError
    data object PhoneInvalid : ContactError
    data class Duplicate(val existingName: String) : ContactError
    /** The list is capped — see [SafetyRepository.MAX_CONTACTS]. */
    data class LimitReached(val limit: Int) : ContactError
}

/**
 * Emergency contacts and safety settings.
 *
 * Local only, by design: this screen has to work at the roadside with no
 * signal, so nothing it needs may live behind a network call.
 */
@Singleton
class SafetyRepository @Inject constructor(
    private val dao: EmergencyContactDao,
    private val prefs: SafetyPreferencesStore,
    private val owner: OwnerScope,
) {
    // flatMapLatest, not a one-off read: when the rider signs out or a
    // different account signs in, the list must re-query for the new owner
    // rather than keep serving the previous one's contacts.
    @OptIn(ExperimentalCoroutinesApi::class)
    val contacts: Flow<List<EmergencyContactEntity>> =
        owner.current.flatMapLatest { dao.observeAll(it) }

    /** Who SOS reaches for. Null when the rider has added nobody. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val primaryContact: Flow<EmergencyContactEntity?> =
        owner.current.flatMapLatest { dao.observePrimary(it) }

    val sosEnabled: Flow<Boolean> = prefs.sosEnabled
    val shareLocation: Flow<Boolean> = prefs.shareLocation
    val helmetReminder: Flow<Boolean> = prefs.helmetReminder

    /**
     * Adds or updates a contact. Returns null on success, or why it was refused.
     *
     * The first contact ever added becomes primary automatically — a contact
     * list where SOS has nobody to call would be worse than useless.
     */
    private val saveLock = Mutex()

    suspend fun save(id: Long, name: String, phone: String): ContactError? =
        // One save at a time: a double tap otherwise passes the duplicate
        // check twice and the second insert hits the unique index.
        saveLock.withLock { saveLocked(id, name, phone) }

    private suspend fun saveLocked(
        id: Long,
        name: String,
        phone: String,
    ): ContactError? {
        val ownerId = owner.currentId()
        val cleanName = name.trim()
        if (!FieldRules.isValidContactName(cleanName)) return ContactError.NameMissing

        val normalized = normalize(phone)
        if (!isValidPhone(normalized)) return ContactError.PhoneInvalid

        dao.findByPhone(ownerId, normalized)?.let { existing ->
            if (existing.id != id) return ContactError.Duplicate(existing.name)
        }

        // Capped deliberately. Under stress nobody scrolls a list, and a
        // contact list that is never pruned goes stale.
        if (id == 0L && dao.count(ownerId) >= MAX_CONTACTS) {
            return ContactError.LimitReached(MAX_CONTACTS)
        }

        if (id == 0L) {
            try {
              dao.insert(
                EmergencyContactEntity(
                    ownerId = ownerId,
                    name = cleanName,
                    phone = sanitizePhone(phone),
                    normalizedPhone = normalized,
                    // The first contact ever added becomes primary; SOS with
                    // nobody to call would be worse than useless.
                    isPrimary = dao.count(ownerId) == 0,
                )
              )
            } catch (e: SQLiteConstraintException) {
                // Backstop for the unique index: report it, never crash.
                return ContactError.Duplicate(dao.findByPhone(ownerId, normalized)?.name.orEmpty())
            }
        } else {
            // An edit changes the name and number, never who is primary.
            val existing = dao.findById(ownerId, id) ?: return null
            try {
                dao.update(
                    existing.copy(
                        name = cleanName,
                        phone = sanitizePhone(phone),
                        normalizedPhone = normalized,
                    )
                )
            } catch (e: SQLiteConstraintException) {
                return ContactError.Duplicate(dao.findByPhone(ownerId, normalized)?.name.orEmpty())
            }
        }
        return null
    }

    /**
     * Deletes, then hands primary to whoever is left — otherwise removing the
     * primary contact would silently leave SOS with nobody to call.
     */
    suspend fun delete(contact: EmergencyContactEntity) {
        val ownerId = owner.currentId()
        dao.delete(contact)
        if (contact.isPrimary) {
            dao.firstRemaining(ownerId)?.let { dao.setPrimary(ownerId, it.id) }
        }
    }

    suspend fun setPrimary(id: Long) = dao.setPrimary(owner.currentId(), id)

    suspend fun setSosEnabled(enabled: Boolean) = prefs.setSosEnabled(enabled)
    suspend fun setShareLocation(enabled: Boolean) = prefs.setShareLocation(enabled)
    suspend fun setHelmetReminder(enabled: Boolean) = prefs.setHelmetReminder(enabled)
    suspend fun acknowledgeSafetyInfo() = prefs.acknowledgeNow()

    companion object {
        /**
         * The most emergency contacts the app keeps.
         *
         * Three is the number a rider can actually keep current, and the SOS
         * sheet stays readable at a glance — which is the whole point of it.
         */
        const val MAX_CONTACTS = 3

        /** Digits with an optional leading `+`. Spaces, dashes and brackets go. */
        fun normalize(phone: String): String {
            val trimmed = phone.trim()
            val digits = trimmed.filter(Char::isDigit)
            return if (trimmed.startsWith("+")) "+$digits" else digits
        }

        /**
         * Length only, deliberately. Numbers run 7-15 digits worldwide (E.164
         * caps at 15) and anything stricter would reject somebody's real number
         * — the worst possible failure on an emergency-contact form.
         */
        fun isValidPhone(normalized: String): Boolean = FieldRules.isValidPhone(normalized)

        /**
         * Strips anything that is not part of a dialable number.
         *
         * The number typed into the form is already filtered by the field
         * itself, but one arriving from the system contact picker is not — and
         * if the rider never edits that field, the raw address-book value is
         * what gets saved and later handed to the dialer as a `tel:` URI. A row
         * carrying DTMF pause-and-wait characters (`,` `;` `p` `w`) would put
         * them in the dialer field of an *emergency* call.
         *
         * Applied here rather than in the screen, so every caller gets it.
         */
        fun sanitizePhone(raw: String): String = FieldRules.phoneInput(raw)
    }
}
