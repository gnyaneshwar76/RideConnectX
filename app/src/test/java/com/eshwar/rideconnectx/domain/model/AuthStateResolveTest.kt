package com.eshwar.rideconnectx.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Only a finished sign-in counts as signed in (audit, 26 Sep). */
class AuthStateResolveTest {

    private val account = UserSession(name = "Asha", method = LoginMethod.GOOGLE, uid = "uidA")
    private val guest = UserSession(name = "Rocky", method = LoginMethod.GUEST)

    @Test
    fun `a finished sign-in is signed in`() {
        assertEquals(AuthState.Authenticated(account), AuthState.resolve("uidA", account))
    }

    @Test
    fun `a Firebase user without its saved session is not signed in yet`() {
        // Sign-in still running, or killed before it finished on this phone.
        assertEquals(AuthState.Unauthenticated, AuthState.resolve("uidA", UserSession()))
        assertEquals(AuthState.Unauthenticated, AuthState.resolve("uidB", account))
    }

    @Test
    fun `a guest is signed in without Firebase, and an unverified email is not`() {
        assertEquals(AuthState.Authenticated(guest), AuthState.resolve(null, guest))
        assertEquals(AuthState.Unauthenticated, AuthState.resolve(null, account))
    }
}
