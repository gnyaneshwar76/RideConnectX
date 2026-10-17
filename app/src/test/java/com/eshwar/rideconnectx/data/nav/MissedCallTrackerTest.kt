package com.eshwar.rideconnectx.data.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MissedCallTrackerTest {
    @Test fun `ringing then gone is missed once`() {
        val t = MissedCallTracker()
        t.onRinging("k", 0); t.onRinging("k", 1_000)
        assertEquals(1, t.onEnded("k", 8_000))
        assertNull(t.onEnded("k", 9_000))
    }
    @Test fun `ringing then answered is not missed`() {
        val t = MissedCallTracker()
        t.onRinging("k", 0); t.onAnswered("k")
        assertNull(t.onEnded("k", 20_000))
    }
    @Test fun `quick decline is not missed and count grows`() {
        val t = MissedCallTracker()
        t.onRinging("a", 0)
        assertNull(t.onEnded("a", 1_000))
        t.onRinging("b", 0); assertEquals(1, t.onEnded("b", 9_000))
        t.onRinging("c", 0); assertEquals(2, t.onEnded("c", 9_000))
    }
    @Test fun `two calls at once - ring length is measured to removal`() {
        val t = MissedCallTracker()
        t.onRinging("a", 0); t.onRinging("b", 0)
        // both removed 1 s after ringing began: rejected, even if decided later
        assertEquals(ClusterAlerts.CallEnd.REJECTED, t.resolve("a", 1_000))
        assertEquals(ClusterAlerts.CallEnd.REJECTED, t.resolve("b", 1_000))
        assertNull(t.onEnded("a", 20_000))
    }
    @Test fun `answered call is never missed and ringing state follows`() {
        val t = MissedCallTracker()
        t.onRinging("a", 0); t.onAnswered("a")
        assertEquals(ClusterAlerts.CallEnd.ANSWERED, t.resolve("a", 30_000))
        ClusterAlerts.reset()
        ClusterAlerts.onCallRinging("x", false, 0); ClusterAlerts.onCallRinging("y", false, 1)
        ClusterAlerts.onCallEnded("y", ClusterAlerts.CallEnd.ANSWERED)
        assertEquals("x", ClusterAlerts.callRinging.value?.caller)
        ClusterAlerts.onCallEnded("x", ClusterAlerts.CallEnd.MISSED)
        assertNull(ClusterAlerts.callRinging.value)
    }
    @Test fun `ring then missed post counts one and stops ringing`() {
        ClusterAlerts.reset()
        val t = MissedCallTracker()
        ClusterAlerts.onCallRinging("Mum", false, 0)
        t.onRinging("k", 0)
        ClusterAlerts.clearRinging("Mum")
        assertNull(ClusterAlerts.callRinging.value)
        assertEquals(true, t.onMissedPost("k", 10_000))
        assertEquals(false, t.onMissedPost("k", 12_000))
    }
    @Test fun `ring then answered counts zero`() {
        val t = MissedCallTracker()
        t.onRinging("k", 0); t.onAnswered("k")
        assertEquals(false, t.onMissedPost("k", 10_000))
        assertNull(t.resolve("k", 10_000))
    }
    @Test fun `missed post then removal resolve counts one not two`() {
        val t = MissedCallTracker()
        t.onRinging("k", 0)
        assertEquals(true, t.onMissedPost("k", 8_000))
        assertNull(t.resolve("k", 8_100))
        // other order: removal first, then the missed post
        t.onRinging("j", 0)
        assertEquals(ClusterAlerts.CallEnd.MISSED, t.resolve("j", 8_000))
        assertEquals(false, t.onMissedPost("j", 9_000))
    }
    @Test fun `bidi and plain caller give the same key`() {
        val plain = cleanStoredText("Mum")
        val bidi = cleanStoredText("⁨Mum⁩")
        assertEquals(plain, bidi)
        val t = MissedCallTracker()
        t.onRinging("call|p|$bidi", 0)
        assertEquals(true, t.onMissedPost("call|p|$plain", 8_000))
        assertEquals(false, t.onMissedPost("call|p|$bidi", 9_000))
    }
    @Test fun `counts and resetCounts`() {
        ClusterAlerts.reset()
        ClusterAlerts.onNotification('W', "a", ""); ClusterAlerts.onNotification('W', "b", "")
        assertEquals(2, ClusterAlerts.unreadCount.value)
        ClusterAlerts.onCallMissed(1, "c")
        assertEquals(1, ClusterAlerts.missedCount.value)
        ClusterAlerts.resetCounts()
        assertEquals(0, ClusterAlerts.unreadCount.value); assertEquals(0, ClusterAlerts.missedCount.value)
    }
}
