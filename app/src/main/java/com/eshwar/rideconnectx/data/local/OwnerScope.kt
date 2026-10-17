package com.eshwar.rideconnectx.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who the rows on this phone belong to.
 *
 * One database file is shared by everyone who signs in on the device, so every
 * query has to say whose rows it wants. Before this existed, the second person
 * to sign in saw the first person's emergency contact, rides, notifications and
 * service history — the contact their SOS screen offered to call was not theirs.
 *
 * The owner is the Firebase account id while signed in, and [GUEST] otherwise.
 * Guest rows are real and usable, but they are their own island: they are not
 * visible to any signed-in account, and no account's rows are visible to a
 * guest. On sign-in, guest rows are claimed once by the account that signs in.
 */
@Singleton
class OwnerScope @Inject constructor(
    private val prefs: UserPreferencesStore,
) {
    /**
     * The owner every query and write should use.
     *
     * Derived from the session rather than cached: an owner that outlives a
     * sign-out is exactly the bug this column exists to prevent.
     */
    val current: Flow<String> = prefs.session.map { it.uid.ifBlank { GUEST } }

    suspend fun currentId(): String = current.first()

    /** True while nobody is signed in, so the UI can offer to save to an account. */
    val isGuest: Flow<Boolean> = current.map { it == GUEST }

    companion object {
        /**
         * Rows belonging to nobody signed in.
         *
         * A literal rather than an empty string, so a row written with a
         * forgotten owner is obvious in the table rather than silently matching
         * every unauthenticated read.
         */
        const val GUEST = "guest"

        /**
         * A form object that has not been saved yet.
         *
         * Screens build a draft entity to bind their fields to, and that draft
         * has no owner — the repository stamps the real one on the way to the
         * database. Naming it rather than defaulting to an empty string keeps
         * the compiler pointing at every real write site, and makes a draft
         * that somehow reached the table obvious instead of invisible.
         */
        const val DRAFT = "__draft__"
    }
}
