package com.eshwar.rideconnectx.domain.repository

import android.app.Activity
import com.eshwar.rideconnectx.domain.model.AuthResult
import com.eshwar.rideconnectx.domain.model.AuthState
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    /**
     * Single source of truth for who is signed in. Starts as
     * [AuthState.Loading] while Firebase restores the persisted session.
     */
    val authState: Flow<AuthState>

    val isGoogleSignInAvailable: Boolean

    /** False when cloud is off (CLOUD_ENABLED=false): the UI hides every sign-in and cloud button. */
    val cloudEnabled: Boolean

    suspend fun signInWithGoogle(activity: Activity): AuthResult

    suspend fun signUpWithEmail(email: String, password: String, name: String): AuthResult

    suspend fun signInWithEmail(email: String, password: String): AuthResult

    suspend fun sendPasswordReset(email: String): AuthResult

    /** True while a sign-in link that opened the app waits to be finished on Sign In. */
    val emailLinkWaiting: kotlinx.coroutines.flow.StateFlow<Boolean>

    /** Called with every link the app is opened with; true when it is a sign-in link (it is then held). */
    fun offerEmailLink(link: String): Boolean

    /** Emails a one-time sign-in link and saves [email] on this phone for when it opens. */
    suspend fun sendSignInLink(email: String): AuthResult

    /**
     * Finishes the held link. [email] null uses the address saved when the link
     * was sent; with none saved (another phone) it fails with NeedLinkEmail.
     */
    suspend fun completeEmailLink(email: String? = null): AuthResult

    /** Profile -> Recent sign-ins, newest first (at most 10). */
    suspend fun recentSignIns(): Result<List<com.eshwar.rideconnectx.domain.model.LoginEntry>>

    /** Local-only session; never reaches Firebase or Firestore. */
    suspend fun continueAsGuest(name: String): AuthResult

    /**
     * Ends the session.
     *
     * [keepLocalProfile] is the "move this profile to another account" flow —
     * the only case where the rider name, nickname, city, vehicle, colour and
     * photo are meant to survive and be written into the next account. The
     * default clears them, so sign-out is an account boundary and a new call
     * site cannot reopen the leak by forgetting a flag.
     */
    suspend fun signOut(keepLocalProfile: Boolean = false)

    /**
     * Answers "Add your guest data to this account?", asked when a guest signs
     * into an account that already has a profile. Nothing is merged before this.
     */
    suspend fun addGuestDataToAccount(): AuthResult

    /**
     * The other answer: leave this account, keeping the guest data on the phone
     * and ready for the account chosen next.
     */
    suspend fun declineGuestMerge()

    /**
     * Setup was left unfinished (app killed, or Back → Exit): sign out and clear
     * everything it started, so the next launch begins cleanly. Writes nothing
     * to the cloud.
     */
    suspend fun resetUnfinishedSetup()

    /**
     * Signs Firebase out when a sign-in was killed before it finished on the
     * phone: the account is not signed in here, but Firebase still holds it.
     */
    suspend fun dropUnfinishedSignIn()

    /**
     * Deletes the account for good: confirm identity (Google picker, or the
     * email account's [password]), then cloud data, then the account, then
     * this account's data on the phone.
     */
    suspend fun deleteAccount(activity: android.app.Activity, password: String? = null): AuthResult
}
