package com.eshwar.rideconnectx.domain.model

/** What sign-in does with the rider profile already on the phone. */
enum class HandoverAction {
    /** It is this account's own profile. */
    KEEP,

    /** Someone else's, or left behind: wipe it before this account's comes down. */
    CLEAR,

    /** The rider asked for it to follow them, and the account has none of its own. */
    CARRY,

    /**
     * A guest's data meets an account that already has a profile: ask the
     * rider before merging, and let them pick another account instead.
     */
    ASK,
}

/**
 * The local profile (name, nickname, city, vehicle, paint, photo) belongs to
 * one owner, and only ever moves to another when the rider asked for that.
 *
 * Before this, whatever profile was on the phone was merge-written into any
 * account that signed in without one of its own — a guest's name ("rocky bhai")
 * turned up in a different person's Google account setup (rider, 26 Sep).
 */
object ProfileHandover {
    /**
     * @param localOwner who the local profile belongs to; blank when unknown.
     * @param carry the rider chose "Save to an account" / "Move to another account".
     * @param accountHasProfile the account already holds a complete profile.
     * @param localIsGuest the local profile belongs to a guest.
     * @param localComplete the local profile finished setup. An unfinished one
     *   is never carried: it would be a half-made profile in the new account.
     */
    fun decide(
        localOwner: String,
        uid: String,
        carry: Boolean,
        accountHasProfile: Boolean,
        localIsGuest: Boolean = false,
        localComplete: Boolean = true,
    ): HandoverAction =
        when {
            localOwner == uid -> HandoverAction.KEEP
            carry && !accountHasProfile -> if (localComplete) HandoverAction.CARRY else HandoverAction.CLEAR
            // Never a silent switch (rider, 26 Sep).
            carry && localIsGuest -> HandoverAction.ASK
            // An account's own profile always wins over a carried one.
            else -> HandoverAction.CLEAR
        }
}
