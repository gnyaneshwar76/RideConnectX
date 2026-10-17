package com.eshwar.rideconnectx.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerGuardTest {
    @Test
    fun `a one-frame spike never reaches the dashboard, a real change does`() {
        val g = OdometerGuard()
        assertTrue(g.accept(0, 12846))          // first reading
        assertTrue(g.accept(12846, 12847))      // normal riding
        assertFalse(g.accept(12847, 300512))    // over the plausible limit (30 Sep)
        assertFalse(g.accept(12847, 99999))     // garbled but plausible: held
        assertTrue(g.accept(12847, 12847))      // next good frame
        assertFalse(g.accept(12847, 13500))     // real jump after riding without the app...
        assertTrue(g.accept(12847, 13500))      // ...shown once the next frame repeats it
        assertFalse(g.accept(13500, 13400))     // going backwards needs confirming too
    }
}
