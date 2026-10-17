package com.eshwar.rideconnectx.data.remote

import com.eshwar.rideconnectx.domain.model.AuthError
import com.eshwar.rideconnectx.domain.model.UserSession
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guest mode: a build with no google-services.json has no FirebaseApp, so both sources get null. */
class NoFirebaseTest {
    private val firestore = FirestoreUserDataSource(null)

    @Test fun firestoreReportsOfflineWithoutThrowing() = runBlocking {
        assertNull(firestore.exists("u"))
        val r = firestore.touchLogin(UserSession(uid = "u"))
        assertTrue(r.exceptionOrNull() is IOException)
        assertTrue(firestore.fetchFavorites("u").isFailure)
        assertTrue(firestore.deleteUser("u").isFailure)
    }

    @Test fun notConfiguredMessageIsClear() {
        assertTrue(AuthError.CloudNotConfigured.message.contains("not configured"))
    }

    // CLOUD_ENABLED=false hands both sources null: no Firebase, no throw.
    @Test fun clearingCacheWithoutFirebaseIsANoOp() = runBlocking {
        firestore.clearLocalCache()
        assertNull(firestore.exists("u"))
    }
}
