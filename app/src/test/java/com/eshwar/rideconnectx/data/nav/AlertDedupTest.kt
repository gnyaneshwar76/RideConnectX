package com.eshwar.rideconnectx.data.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertNull

class AlertDedupTest {
    @Test
    fun `a re-posted call goes once, a changed call again, a new ring after the call ended`() {
        val d = AlertDedup()
        assertTrue(d.shouldSend("k", "call|Amma", 0, isCall = true))
        assertFalse(d.shouldSend("k", "call|Amma", 1_000, isCall = true))
        assertFalse(d.shouldSend("k", "call|Amma", 120_000, isCall = true))   // still ringing
        assertTrue(d.shouldSend("k", "call|Ravi", 121_000, isCall = true))    // state changed
        d.forget("k", 125_000)
        assertFalse(d.shouldSend("k", "call|Ravi", 128_000, isCall = true))   // cancel + re-post burst
        assertTrue(d.shouldSend("k", "call|Ravi", 134_000, isCall = true))    // a new ring
    }

    @Test
    fun `a message repeat is dropped in the window and allowed after it`() {
        val d = AlertDedup(windowMs = 30_000)
        assertTrue(d.shouldSend("m", "Ravi|ok", 0, isCall = false))
        assertFalse(d.shouldSend("m", "Ravi|ok", 5_000, isCall = false))
        assertTrue(d.shouldSend("m", "Ravi|ok again", 6_000, isCall = false))
        assertTrue(d.shouldSend("m", "Ravi|ok again", 40_000, isCall = false))
    }

    @Test
    fun `message removed or chat read recounts N then 0 with no blink and byte 14 flag`() {
        ClusterAlerts.reset()
        repeat(3) { ClusterAlerts.onNotification('W', "Asha", "hi") }
        ClusterAlerts.onMessagesRecounted('W', 1, 1)
        var a = ClusterAlerts.alerts.replayCache.last()
        org.junit.Assert.assertEquals(1, ClusterAlerts.unreadCount.value)
        org.junit.Assert.assertFalse(a.newAlert)
        org.junit.Assert.assertEquals(1, a.unreadCount)
        org.junit.Assert.assertFalse(a.bothZero)
        ClusterAlerts.onMessagesRecounted('W', 0, 0)
        a = ClusterAlerts.alerts.replayCache.last()
        org.junit.Assert.assertEquals(0, ClusterAlerts.unreadCount.value)
        org.junit.Assert.assertEquals(0, a.unreadCount)
        org.junit.Assert.assertTrue(a.bothZero)
        org.junit.Assert.assertFalse(ClusterAlerts.notificationPending.value)
    }

    @Test
    fun `recount to zero is not bothZero while missed calls remain`() {
        ClusterAlerts.reset()
        ClusterAlerts.onNotification('N', "Bank", "otp")
        ClusterAlerts.onCallMissed(2, "Ravi")
        ClusterAlerts.onMessagesRecounted('N', 0, 0)
        org.junit.Assert.assertFalse(ClusterAlerts.alerts.replayCache.last().bothZero)
    }

    @Test
    fun `missed notification removed zeroes the count and fires the 0x34 reset`() = kotlinx.coroutines.runBlocking {
        ClusterAlerts.reset()
        ClusterAlerts.onCallMissed(2, "Ravi")
        ClusterAlerts.onMissedCall("Ravi")
        val fired = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            ClusterAlerts.missedReset.first()
        }
        ClusterAlerts.onMissedNotificationRemoved()
        fired.await()
        org.junit.Assert.assertEquals(0, ClusterAlerts.missedCount.value)
        org.junit.Assert.assertFalse(ClusterAlerts.missedCallPending.value)
    }

    @Test
    fun `disconnect reset zeroes counts silently`() = kotlinx.coroutines.runBlocking {
        ClusterAlerts.reset()
        ClusterAlerts.onNotification('N', "A", "b")
        ClusterAlerts.onCallMissed(1, "Ravi")
        val n = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            kotlinx.coroutines.withTimeoutOrNull(100) {
                kotlinx.coroutines.flow.merge(ClusterAlerts.missedReset, ClusterAlerts.countsReset).first()
            }
        }
        ClusterAlerts.reset()
        org.junit.Assert.assertNull(n.await())
        org.junit.Assert.assertEquals(0, ClusterAlerts.missedCount.value)
        org.junit.Assert.assertEquals(0, ClusterAlerts.unreadCount.value)
    }

    @Test
    fun `Clear button still resets everything and fires countsReset`() = kotlinx.coroutines.runBlocking {
        ClusterAlerts.reset()
        ClusterAlerts.onNotification('N', "A", "b")
        ClusterAlerts.onCallMissed(3, "Ravi")
        val fired = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            ClusterAlerts.countsReset.first()
        }
        ClusterAlerts.resetCounts()
        fired.await()
        org.junit.Assert.assertEquals(0, ClusterAlerts.missedCount.value)
        org.junit.Assert.assertEquals(0, ClusterAlerts.unreadCount.value)
    }

    @Test
    fun `clearAllRinging leaves nothing ringing`() {
        ClusterAlerts.reset()
        ClusterAlerts.onCallRinging("Asha", false)
        ClusterAlerts.onCallRinging("Ravi", true)
        ClusterAlerts.clearAllRinging()
        org.junit.Assert.assertNull(ClusterAlerts.callRinging.value)
    }
}
