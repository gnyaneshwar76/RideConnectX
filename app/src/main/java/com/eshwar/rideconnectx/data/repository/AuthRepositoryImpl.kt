package com.eshwar.rideconnectx.data.repository

import android.app.Activity
import android.content.Context
import com.eshwar.rideconnectx.data.local.ServicePreferencesStore
import com.eshwar.rideconnectx.data.local.SessionDataStore
import com.eshwar.rideconnectx.data.local.UserPreferencesStore
import com.eshwar.rideconnectx.data.local.OwnerScope.Companion.GUEST
import com.eshwar.rideconnectx.data.remote.FirebaseAuthDataSource
import com.eshwar.rideconnectx.data.remote.FirestoreUserDataSource
import com.eshwar.rideconnectx.domain.model.AuthError
import com.eshwar.rideconnectx.domain.model.AuthResult
import com.eshwar.rideconnectx.domain.model.AuthState
import com.eshwar.rideconnectx.domain.model.CloudProfile
import com.eshwar.rideconnectx.domain.model.HandoverAction
import com.eshwar.rideconnectx.domain.model.ProfileHandover
import com.eshwar.rideconnectx.domain.model.GuestNameRules
import com.eshwar.rideconnectx.domain.model.LoginMethod
import com.eshwar.rideconnectx.domain.model.EmailLinkFlow
import com.eshwar.rideconnectx.domain.model.LoginEntry
import com.eshwar.rideconnectx.domain.model.LoginHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.eshwar.rideconnectx.domain.model.SyncOutcome
import com.eshwar.rideconnectx.domain.model.UserSession
import com.eshwar.rideconnectx.core.di.ApplicationScope
import com.eshwar.rideconnectx.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remote: FirebaseAuthDataSource,
    private val firestore: FirestoreUserDataSource,
    private val prefs: UserPreferencesStore,
    private val photoStore: com.eshwar.rideconnectx.core.util.ProfilePhotoStore,
    private val sessionStore: SessionDataStore,
    private val servicePrefs: ServicePreferencesStore,
    private val database: com.eshwar.rideconnectx.data.local.db.RideDatabase,
    @ApplicationScope private val appScope: CoroutineScope,
    private val rides: RideRepository,
    private val linkStore: com.eshwar.rideconnectx.data.remote.EmailLinkStore,
) : AuthRepository {

    private companion object {
        const val TAG = "RCX-Auth"
        const val LAST_READINGS = "lastReadings"
        const val SAVE_TIMEOUT_MS = 5_000L

        /** Every Room table carrying an ownerId column (DB v8). */
        val OWNED_TABLES = listOf(
            "emergency_contacts", "notifications", "rides", "service_records",
            "service_tasks", "fuel_samples", "refuels",
        )
    }

    /**
     * Firebase drives the state for cloud accounts; DataStore covers guests,
     * who have no Firebase user. Combining the two means one stream answers
     * "is anyone signed in", online or off.
     */
    override val authState: Flow<AuthState> =
        combine(
            remote.authStateChanges.onStart { emit(remote.currentUser) },
            prefs.session,
        ) { firebaseUser, localSession ->
            // An email account counts only once its address is verified;
            // Google accounts always are.
            val verifiedUid = firebaseUser?.takeIf { user ->
                user.isEmailVerified || user.providerData.none { it.providerId == "password" }
            }?.uid
            AuthState.resolve(verifiedUid, localSession)
        }.onStart { emit(AuthState.Loading) }

    override val isGoogleSignInAvailable: Boolean get() = remote.isGoogleSignInAvailable

    override val cloudEnabled: Boolean get() = com.eshwar.rideconnectx.BuildConfig.CLOUD_ENABLED && remote.isConfigured

    /**
     * Google sign-in, with [persist] on [appScope] for the reason below.
     *
     * The Activity itself has to be handed to the caller's thread, so only the
     * persistence is moved. Google used to get away with running persist on the
     * caller's scope because it was two quick writes; once [persist] also read
     * the profile back down, the extra round trip gave the navigation time to
     * clear the AuthViewModel mid-write and the Firestore write died with
     * `JobCancellationException`.
     */
    override suspend fun signInWithGoogle(activity: Activity): AuthResult {
        val result = remote.signInWithGoogle(activity)
        return appScope.async { result.persist() }.await()
    }

    /**
     * Runs on [appScope] rather than the caller's scope.
     *
     * `createUserWithEmailAndPassword` signs the user in the moment it returns,
     * which flips [authState] and navigates away from Sign In — clearing the
     * AuthViewModel and cancelling its viewModelScope. The email path then has
     * one more suspension point than Google does (`updateProfile`), so it was
     * reliably being cancelled there, before [persist] ever ran and before
     * Firestore was ever told about the account. Hence: Google users in the
     * database, email users not.
     */
    override suspend fun signUpWithEmail(email: String, password: String, name: String): AuthResult =
        appScope.async { remote.signUpWithEmail(email, password, name).persist() }.await()

    override suspend fun signInWithEmail(email: String, password: String): AuthResult =
        appScope.async { remote.signInWithEmail(email, password).persist() }.await()

    override suspend fun sendPasswordReset(email: String): AuthResult =
        remote.sendPasswordReset(email)

    /** In memory only: if the app is killed, tapping the link again brings it back. */
    private val pendingLink = MutableStateFlow<String?>(null)
    override val emailLinkWaiting: StateFlow<Boolean> =
        pendingLink.map { it != null }.stateIn(appScope, SharingStarted.Eagerly, false)

    override fun offerEmailLink(link: String): Boolean =
        remote.isSignInLink(link).also { if (it) pendingLink.value = link }

    override suspend fun sendSignInLink(email: String): AuthResult {
        val now = System.currentTimeMillis()
        when (val check = EmailLinkFlow.checkSend(linkStore.requests(), now)) {
            EmailLinkFlow.SendCheck.Ok -> Unit
            is EmailLinkFlow.SendCheck.Wait ->
                return AuthResult.Failure(AuthError.Unknown("Wait ${check.seconds} seconds before asking for another link."))
            is EmailLinkFlow.SendCheck.Capped ->
                return AuthResult.Failure(AuthError.Unknown(EmailLinkFlow.cappedMessage(check.minutes)))
        }
        val result = remote.sendSignInLink(email)
        if (result is AuthResult.Success) linkStore.markSent(email.trim(), now)
        return result
    }

    override suspend fun completeEmailLink(email: String?): AuthResult = appScope.async {
        val link = pendingLink.value
        if (link != null && EmailLinkFlow.isExpired(linkStore.sentAt(), System.currentTimeMillis())) {
            pendingLink.value = null
            linkStore.clear()
            return@async AuthResult.Failure(AuthError.Unknown(EmailLinkFlow.LINK_EXPIRED))
        }
        when (val step = EmailLinkFlow.next(link, linkStore.get(), email)) {
            EmailLinkFlow.Step.NoLink -> AuthResult.Failure(AuthError.Unknown("Open the sign-in link from your email again."))
            EmailLinkFlow.Step.AskEmail -> AuthResult.Failure(AuthError.NeedLinkEmail)
            is EmailLinkFlow.Step.Complete -> {
                val result = remote.signInWithEmailLink(step.email, link!!)
                val error = (result as? AuthResult.Failure)?.error
                if (result is AuthResult.Success || (error is AuthError.LinkFailed && !error.linkStillGood)) {
                    pendingLink.value = null
                    linkStore.clear()
                }
                result.persist()
            }
        }
    }.await()

    override suspend fun recentSignIns(): Result<List<LoginEntry>> {
        val uid = prefs.session.first().uid
        if (uid.isEmpty()) return Result.success(emptyList())
        return firestore.fetchLogins(uid)
    }

    /** Fire and forget: the sign-in never waits on, or fails because of, its history record. */
    private fun recordLogin(user: UserSession) {
        val method = LoginHistory.methodKey(user.method) ?: return
        appScope.launch {
            firestore.recordLogin(
                user.uid,
                LoginHistory.record(
                    method, android.os.Build.MANUFACTURER, android.os.Build.MODEL,
                    android.os.Build.VERSION.RELEASE, com.eshwar.rideconnectx.BuildConfig.VERSION_NAME,
                ),
            ).onFailure { Log.w(TAG, "Sign-in record not saved", it) }
        }
    }

    override suspend fun continueAsGuest(name: String): AuthResult {
        val clean = name.trim()
        GuestNameRules.validate(clean)?.let {
            return AuthResult.Failure(AuthError.InvalidCredentials(it))
        }
        val session = UserSession(name = clean, method = LoginMethod.GUEST)
        // A guest never inherits an account's profile; only a guest's own
        // (one that went to sign in and came back) is kept.
        if (prefs.profileOwner.first() != GUEST) clearLocalProfile()
        prefs.setProfileOwner(GUEST)
        prefs.saveSession(session)
        // A guest types their name on the screen before Create Profile, and
        // Create Profile then asked for it again with an empty field — because
        // the name only went into the session, not into the rider name the rest
        // of the app reads. It is the same name; store it once.
        prefs.saveRiderName(clean)
        return AuthResult.Success(session)
    }

    /** On [appScope]: leaving the screen must not cancel it half way (Firebase out, profile still on the phone). */
    override suspend fun signOut(keepLocalProfile: Boolean) =
        appScope.async { signOutSteps(keepLocalProfile) }.await()

    private suspend fun signOutSteps(keepLocalProfile: Boolean) {
        val session = prefs.session.first()
        // The last readings go up to the account that saw them, then off the
        // phone. Capped at a few seconds so offline never blocks sign-out; if
        // the cloud does not confirm, they are held for this account only and
        // uploaded at its next sign-in. Guests have no cloud; theirs are erased.
        if (!session.isGuest && session.uid.isNotEmpty()) {
            sessionStore.cachedTelemetry.first()?.let { c ->
                val saved = withTimeoutOrNull(SAVE_TIMEOUT_MS) {
                    uploadReadings(session.uid, c)
                } ?: false
                if (!saved) {
                    Log.w(TAG, "Last readings not confirmed by cloud - held for next sign-in")
                    sessionStore.savePendingReadings(session.uid, c)
                }
            }
        }
        clearReadings()

        remote.signOut(context)
        firestore.clearLocalCache()
        prefs.clearSession()
        // The boundary lives here, not in a ViewModel: `AuthViewModel.signOut`
        // reaches this same method, and a profile left behind is merge-written
        // into the *next* uid's document by `carryLocalProfileToCloud`.
        if (!keepLocalProfile) {
            clearLocalProfile()
        } else {
            // Owned by whoever is leaving, and marked to follow them.
            prefs.setProfileOwner(prefs.profileOwner.first().ifBlank { session.uid.ifBlank { GUEST } }, carry = true)
        }
    }

    override suspend fun resetUnfinishedSetup() = appScope.async {
        Log.d(TAG, "Unfinished setup reset")
        remote.signOut(context)
        prefs.clearSession()
        prefs.setGuestMergePending(false)
        clearReadings()
        clearLocalProfile()
    }.await()

    override suspend fun dropUnfinishedSignIn() {
        val user = remote.currentUser ?: return
        if (prefs.session.first().uid == user.uid) return
        Log.d(TAG, "Sign-in killed before it finished - Firebase signed out")
        remote.signOut(context)
    }

    private suspend fun clearLocalProfile() {
        prefs.clearProfile()
        photoStore.clear()
    }

    /**
     * Order matters, and each step stops the rest on failure:
     *  1. Confirm identity. Firebase refuses to delete a stale sign-in, and the
     *     old code deleted the cloud data first - so that refusal left an
     *     account with its data gone but the account itself still there.
     *  2. Cloud data (needs the account to still exist - rules check the uid).
     *  3. The account.
     *  4. This account's data on the phone - other accounts' rows are kept.
     * Runs on [appScope]: the screen navigates away as the account vanishes.
     */
    override suspend fun deleteAccount(activity: Activity, password: String?): AuthResult =
        appScope.async { deleteAccountSteps(activity, password) }.await()

    private suspend fun deleteAccountSteps(activity: Activity, password: String?): AuthResult {
        val session = prefs.session.first()
        if (session.isGuest || session.uid.isEmpty()) {
            return AuthResult.Failure(AuthError.Unknown("Guests have no account to delete."))
        }
        val uid = session.uid

        val proof = when (session.method) {
            LoginMethod.EMAIL -> remote.reauthenticateWithPassword(password.orEmpty())
            // No password to re-check: Firebase accepts a sign-in from the last few minutes.
            LoginMethod.EMAIL_LINK ->
                if (remote.isSignInRecent()) AuthResult.Success(session) else AuthResult.Failure(AuthError.SignInTooOld)
            else -> remote.reauthenticateWithGoogle(activity)
        }
        if (proof is AuthResult.Failure) return proof

        firestore.deleteUser(uid).onFailure {
            Log.e(TAG, "Cloud delete failed", it)
            return AuthResult.Failure(AuthError.Unknown("Couldn't delete your cloud data. Check your internet and try again."))
        }
        val result = remote.deleteAccount()
        if (result is AuthResult.Failure) return result

        database.openHelper.writableDatabase.apply {
            beginTransaction()
            try {
                OWNED_TABLES.forEach { execSQL("DELETE FROM $it WHERE ownerId = ?", arrayOf(uid)) }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        sessionStore.clearPendingReadings()
        remote.signOut(context)
        firestore.clearLocalCache()
        prefs.clearSession()
        clearReadings()
        // Deleting the account is a stronger statement than signing out, so the
        // local profile and avatar go too. Leaving them meant the next account
        // signed in on this phone inherited the deleted rider's name, city,
        // vehicle and face.
        clearLocalProfile()
        return result
    }

    /**
     * Mirrors a successful sign-in into DataStore for offline reads, then
     * creates or refreshes the Firestore document.
     *
     * A Firestore failure does not fail the sign-in — the user is authenticated
     * either way — but it is surfaced so the UI can warn that sync is behind.
     */
    private suspend fun AuthResult.persist(): AuthResult {
        if (this !is AuthResult.Success) return this

        // Recorded here rather than in the ViewModel so it shares this scope and
        // cannot be lost to the same cancellation that used to drop the
        // Firestore write. The Sign In screen states that continuing is consent.
        prefs.acceptTerms()
        prefs.acceptPrivacy()

        // Unknown is not "new": treating a failed read as a first sign-in
        // overwrote a returning rider's cloud profile with defaults. And the
        // account's profile must come down before setup is decided, or a rider
        // with a profile is asked to make one again — so a failed read undoes
        // the sign-in rather than leaving it half done.
        val isReturning = firestore.exists(user.uid) ?: return abandonSignIn()
        Log.d(TAG, "persist method=${user.method} returning=$isReturning")

        val cloudData =
            if (isReturning) firestore.fetchUser(user.uid).getOrElse { return abandonSignIn() } else null
        val synced =
            if (isReturning) firestore.touchLogin(user) else firestore.createUser(user)

        // The whole point of signing in with an account: a returning rider gets
        // their profile back instead of being asked to build it again.
        //
        // And the mirror of it: a rider who asked to take their profile to an
        // account with none of its own — a guest signing up, or someone moving
        // to a different email — takes it with them. Anything else on the phone
        // belongs to someone else and is cleared, never carried.
        val localOwner = prefs.profileOwner.first()
        val action = ProfileHandover.decide(
            localOwner = localOwner,
            uid = user.uid,
            carry = prefs.profileCarry.first(),
            accountHasProfile = CloudProfile.from(cloudData).isComplete,
            localIsGuest = localOwner == GUEST,
            localComplete = prefs.profileCompleted.first(),
        )
        Log.d(TAG, "profile handover $action (local owner ${localOwner.ifBlank { "unknown" }})")
        when (action) {
            HandoverAction.KEEP -> if (isReturning) restoreFromCloud(user.uid, cloudData)
            HandoverAction.CLEAR -> {
                clearLocalProfile()
                if (isReturning) restoreFromCloud(user.uid, cloudData)
            }
            HandoverAction.CARRY -> {
                carryLocalProfileToCloud(user.uid)
                // Only a guest's rows are unowned; an account's stay its own.
                if (localOwner == GUEST) claimGuestRows(user.uid)
            }
            // Nothing moves until the rider answers; see addGuestDataToAccount.
            HandoverAction.ASK -> Unit
        }
        // Set either way, so a question left by a sign-in that never finished
        // cannot be put to a different account.
        prefs.setGuestMergePending(action == HandoverAction.ASK)
        if (action != HandoverAction.ASK) {
            prefs.setProfileOwner(user.uid)
            flushPendingReadings(user.uid)
        }
        // Last: saving the session is what makes the account signed in on this
        // phone (AuthState.resolve), so nothing routes on a half-done sign-in.
        prefs.saveSession(user)
        rides.scheduleUpload()   // kept rides go up once the session exists
        recordLogin(user)

        // The session is saved, so the rider IS signed in. Never turn that into a
        // Failure: the screen would show "couldn't sync" over a working account.
        // The outcome rides on Success for the screen to word as it likes.
        val outcome = SyncOutcome.of(synced.exceptionOrNull())
        synced.exceptionOrNull()?.let { Log.w(TAG, "Profile write not confirmed ($outcome)", it) }
        return (this as AuthResult.Success).copy(sync = outcome)
    }

    private suspend fun abandonSignIn(): AuthResult {
        Log.e(TAG, "Account unreachable - sign-in undone")
        remote.signOut(context)
        prefs.clearSession()
        return AuthResult.Failure(AuthError.Unknown("Couldn't reach your account. Check your internet and try again."))
    }

    override suspend fun addGuestDataToAccount(): AuthResult = appScope.async {
        val session = prefs.session.first()
        val data = firestore.fetchUser(session.uid).getOrElse {
            Log.e(TAG, "Guest merge: account read failed", it)
            return@async AuthResult.Failure(AuthError.Unknown("Couldn't reach your account. Check your internet and try again."))
        }
        // The account's profile wins; the guest's rides, service records and
        // contacts join it.
        clearLocalProfile()
        restoreFromCloud(session.uid, data)
        claimGuestRows(session.uid)
        prefs.setProfileOwner(session.uid)
        prefs.setGuestMergePending(false)
        flushPendingReadings(session.uid)
        rides.scheduleUpload()
        AuthResult.Success(session)
    }.await()

    override suspend fun declineGuestMerge() {
        remote.signOut(context)
        prefs.clearSession()
        prefs.setGuestMergePending(false)
        // The guest profile and its "take me with you" mark stay for the next account.
    }

    /**
     * Pulls a returning rider's account back down onto this device.
     *
     * Without this, reinstalling and signing in with the same Google account
     * still landed on Create Profile every time — the cloud copy existed and was
     * being written, but nothing ever read it back, so the phone started blank.
     *
     * Restores the rider's name and city, the chosen vehicle and colourway, and
     * the settings and preferences maps. Profile setup is then marked done, so
     * the rider goes straight to the dashboard.
     *
     * Deliberately forgiving: a missing or partial document leaves the rider on
     * Create Profile rather than dropping them onto a dashboard with no vehicle.
     */
    private suspend fun restoreFromCloud(uid: String, data: Map<String, Any>?) {
        if (data == null) {
            Log.e(TAG, "Cloud restore failed")
            return
        }

        // Only a finished profile comes down. A partial one — a name with no
        // vehicle, e.g. a guest's name merge-written before N4 — kept putting
        // "rocky bhai" back into another account's Create Profile (rider,
        // 26 Sep). Finishing setup overwrites it in the cloud.
        val cloud = CloudProfile.from(data).takeIf { it.isComplete } ?: CloudProfile()

        cloud.riderName?.let { prefs.saveRiderName(it) }
        cloud.nickname?.let { prefs.saveRiderNickname(it) }
        cloud.location?.let { prefs.saveRiderLocation(it) }
        cloud.vehicleId?.let { prefs.saveVehicle(it) }
        cloud.colorId?.let { prefs.saveColor(it) }
        // The picture is part of the profile, so it comes back with it. Skipped
        // silently when the device already has one — see `restoreFromCloud`.
        cloud.photoBase64?.let { photoStore.restoreFromCloud(it) }
        if (cloud.isComplete) prefs.setProfileCompleted(true)
        restoreLastReadings(data)

        Log.d(
            TAG,
            "Cloud restore users/$uid name=${cloud.riderName != null} " +
                "vehicle=${cloud.vehicleId != null} colour=${cloud.colorId != null} " +
                "photo=${cloud.photoBase64 != null} profileComplete=${cloud.isComplete}",
        )
    }

    /**
     * Takes the profile already on this device up to an account with none of
     * its own — only when the rider asked for it (see [ProfileHandover]).
     *
     * This is what makes "guest, then sign up" and "move to another email" keep
     * the rider's work. `clearSession()` deliberately leaves the rider name,
     * nickname, city, vehicle, paint and photo alone — only the account
     * identity goes — so at this point everything is still here and just needs
     * an owner.
     *
     * Writes nothing when there is nothing to write, so a genuinely new rider
     * does not get an empty document full of blank fields.
     */
    private suspend fun carryLocalProfileToCloud(uid: String) {
        val riderName = prefs.riderName.first()
        val nickname = prefs.riderNickname.first()
        val location = prefs.riderLocation.first()
        val vehicleId = prefs.selectedVehicleId.first().orEmpty()
        val colorId = prefs.selectedColorId.first().orEmpty()

        val profile = buildMap<String, Any> {
            if (riderName.isNotBlank()) put("riderName", riderName)
            if (nickname.isNotBlank()) put("nickname", nickname)
            if (location.isNotBlank()) put("location", location)
            photoStore.encodeForCloud()?.let { put("photoBase64", it) }
        }

        if (profile.isEmpty() && vehicleId.isBlank()) {
            Log.d(TAG, "New account, nothing local to carry over")
            return
        }

        if (profile.isNotEmpty()) firestore.updateProfileFields(uid, profile)
        if (vehicleId.isNotBlank()) {
            firestore.updateScooter(
                uid,
                mapOf("vehicleId" to vehicleId, "colorId" to colorId),
            )
        }

        Log.d(
            TAG,
            "Carried local profile to users/$uid " +
                "name=${riderName.isNotBlank()} vehicle=${vehicleId.isNotBlank()}",
        )
    }

    /**
     * Hands everything saved as a guest to the account that just signed in.
     *
     * OwnerScope promised this and nothing did it: a guest's emergency contact
     * stayed owned by "guest" and vanished from the account's Safety screen.
     * `UPDATE OR IGNORE` skips a contact the account already has (the unique
     * phone index); that duplicate stays with the guest rather than failing.
     */
    private fun claimGuestRows(uid: String) {
        database.openHelper.writableDatabase.apply {
            beginTransaction()
            try {
                OWNED_TABLES.forEach {
                    execSQL("UPDATE OR IGNORE $it SET ownerId = ? WHERE ownerId = ?", arrayOf(uid, GUEST))
                }
                // The account may already have an SOS contact; keep only its oldest primary.
                execSQL(
                    "UPDATE emergency_contacts SET isPrimary = 0 WHERE ownerId = ? AND isPrimary = 1 " +
                        "AND id <> (SELECT id FROM emergency_contacts WHERE ownerId = ? AND isPrimary = 1 " +
                        "ORDER BY createdAt ASC LIMIT 1)",
                    arrayOf(uid, uid),
                )
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
    }

    /**
     * True only once the cloud confirms. One document, merge-written: the cloud
     * saves it whole or not at all, and a repeat overwrites rather than
     * duplicates - so retrying is always safe.
     */
    private suspend fun uploadReadings(uid: String, c: SessionDataStore.CachedTelemetry): Boolean =
        firestore.updateScooter(
            uid,
            mapOf(
                LAST_READINGS to mapOf(
                    "odometerKm" to c.odometerKm,
                    "tripAKm" to c.tripAKm.toDouble(),
                    "tripBKm" to c.tripBKm.toDouble(),
                    "fuelSegments" to c.fuelSegments,
                    "capturedAt" to c.capturedAt,
                )
            ),
        ).isSuccess

    /**
     * Readings held from an offline sign-out of [uid]. They are newer than the
     * cloud copy, so they go on screen first; the held copy is deleted only
     * after the cloud confirms the upload. Signed in, Firestore itself waits
     * for the network, so this simply completes when the internet is back.
     */
    private fun flushPendingReadings(uid: String) = appScope.launch {
        val (owner, c) = sessionStore.pendingReadings.first() ?: return@launch
        if (owner != uid) return@launch // someone else's - stays hidden
        sessionStore.cacheTelemetry(c.odometerKm, c.tripAKm, c.tripBKm, c.fuelSegments, c.capturedAt)
        if (uploadReadings(uid, c)) sessionStore.clearPendingReadings()
    }

    /** Readings and the service screen's odometer floor both belong to the account. */
    private suspend fun clearReadings() {
        sessionStore.clearTelemetry()
        servicePrefs.clearOdometer()
    }

    /** The account's own last readings, saved by [signOut]. Absent = dashboard shows "—". */
    private suspend fun restoreLastReadings(data: Map<String, Any>?) {
        val r = (data?.get("scooter") as? Map<*, *>)?.get(LAST_READINGS) as? Map<*, *> ?: return
        val odo = (r["odometerKm"] as? Number)?.toInt() ?: return
        if (!ServicePreferencesStore.isPlausibleOdometer(0, odo)) return
        sessionStore.cacheTelemetry(
            odometerKm = odo,
            tripAKm = (r["tripAKm"] as? Number)?.toFloat() ?: 0f,
            tripBKm = (r["tripBKm"] as? Number)?.toFloat() ?: 0f,
            fuelSegments = ((r["fuelSegments"] as? Number)?.toInt() ?: 0).coerceIn(0, 5),
            capturedAt = (r["capturedAt"] as? Number)?.toLong() ?: 0L,
        )
    }
}
