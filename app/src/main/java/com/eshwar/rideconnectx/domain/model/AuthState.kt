package com.eshwar.rideconnectx.domain.model

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: UserSession) : AuthState

    companion object {
        /**
         * An account counts as signed in only once sign-in has finished on this
         * phone — its session is saved last, after the profile is settled. A
         * Firebase user alone (sign-in still running, or killed halfway) used
         * to count, which let a half-finished sign-in resume into setup with
         * nothing restored and rows saved as a guest's.
         *
         * @param firebaseUid the Firebase user, if its email is verified
         *   (Google accounts always are); null otherwise.
         */
        fun resolve(firebaseUid: String?, local: UserSession): AuthState = when {
            firebaseUid != null && local.uid == firebaseUid && !local.isGuest -> Authenticated(local)
            local.isGuest -> Authenticated(local)
            else -> Unauthenticated
        }
    }
}

sealed interface AuthError {
    data object NoInternet : AuthError
    data object Cancelled : AuthError
    data object NoCredentialAvailable : AuthError
    data object WebClientIdMissing : AuthError
    /** Build has no google-services.json: guest mode only. */
    data object CloudNotConfigured : AuthError
    data class InvalidCredentials(val detail: String) : AuthError
    data class AccountExists(val detail: String) : AuthError
    data class WeakPassword(val detail: String) : AuthError
    data class TokenFailure(val detail: String) : AuthError
    data class CloudSyncFailure(val detail: String) : AuthError
    /** Email account whose address was never confirmed; a link has been (re)sent. */
    data class EmailNotVerified(val email: String) : AuthError
    data class Unknown(val detail: String) : AuthError
    /** Firebase rate limit (sign-in tries or emails sent). */
    data object TooManyTries : AuthError
    /** A sign-in link that failed; [linkStillGood] = retry with another email. */
    data class LinkFailed(val detail: String, val linkStillGood: Boolean) : AuthError
    /** A link opened on a phone that did not ask for it: type the email to finish. */
    data object NeedLinkEmail : AuthError
    /** Email-link accounts have no password, so deleting needs a sign-in from the last 5 minutes; the rider is sent to sign in again (the app sends no email). */
    data object SignInTooOld : AuthError

    val message: String
        get() = when (this) {
            NoInternet -> "No internet connection. Check your network and try again."
            Cancelled -> "Sign-in cancelled."
            // Also what Credential Manager answers when this build's signing
            // fingerprint isn't registered in Firebase, so don't claim the
            // phone has no accounts.
            NoCredentialAvailable -> "Couldn't find a Google account for this app. Check Settings → Accounts, or use email for now."
            WebClientIdMissing ->
                "Google Sign-In isn't configured. Re-download google-services.json after enabling Google in Firebase Console."
            CloudNotConfigured -> "Cloud sign-in is not configured in this build. You can continue as a guest."
            is InvalidCredentials -> detail
            is AccountExists -> detail
            is WeakPassword -> detail
            is TokenFailure -> "Couldn't verify your Google account. $detail"
            is CloudSyncFailure -> "Signed in, but your data couldn't sync. $detail"
            is EmailNotVerified ->
                "We've sent a verification link to ${EmailLinkFlow.maskEmail(email)}. Open it, then sign in. Not your address? Sign up again with the right one."
            is Unknown -> detail
            TooManyTries -> EmailLinkFlow.TOO_MANY
            is LinkFailed -> detail
            NeedLinkEmail -> "To finish signing in, type the email you asked the link for."
            SignInTooOld ->
                "For your safety, sign in again, then delete your account."
        }
}

/**
 * How the cloud profile write went after a sign-in that itself succeeded.
 * The rider is signed in in all three cases; this only says whether the cloud
 * copy is up to date.
 */
enum class SyncOutcome {
    /** Cloud confirmed (or nothing needed writing). */
    SYNCED,

    /** No connection or timed out. The write stays queued and goes up later. */
    OFFLINE,

    /** The cloud refused or errored for another reason. */
    FAILED;

    companion object {
        /** A null [error] is success; an [java.io.IOException] means no connection. */
        fun of(error: Throwable?): SyncOutcome = when (error) {
            null -> SYNCED
            is java.io.IOException -> OFFLINE
            else -> FAILED
        }
    }
}

sealed interface AuthResult {
    /** [sync] is SYNCED for everything except a sign-in whose cloud write did not confirm. */
    data class Success(val user: UserSession, val sync: SyncOutcome = SyncOutcome.SYNCED) : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}
