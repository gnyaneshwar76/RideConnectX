package com.eshwar.rideconnectx.data.repository

import com.eshwar.rideconnectx.data.local.db.CloudState
import com.eshwar.rideconnectx.data.local.db.KeepState
import com.eshwar.rideconnectx.data.local.db.RideDao
import com.eshwar.rideconnectx.data.local.db.RideEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class RideSyncTest {
    private val uid = "u1"
    private val rows = mutableListOf<RideEntity>()
    private var lostAnswers = 0

    /** Only the queries syncRides uses; the rest of the DAO is never called. */
    private val dao: RideDao = Proxy.newProxyInstance(
        RideDao::class.java.classLoader, arrayOf(RideDao::class.java),
    ) { _, m, a ->
        when (m.name) {
            "tombstones" -> rows.filter { it.keepState == KeepState.DELETED && it.cloudState == CloudState.UPLOADED }
            "toUpload" -> rows.filter { it.keepState == KeepState.KEPT && it.cloudState == CloudState.NOT_UPLOADED }.sortedBy { it.endedAt }
            "deleteById" -> rows.removeAll { it.id == a[0] as Long }
            "reopenPhoneOnly" -> rows.replaceAll { if (it.cloudState == CloudState.PHONE_ONLY) it.copy(cloudState = CloudState.NOT_UPLOADED) else it }
            "setCloud" -> rows.replaceAll { if (it.id == a[0] as Long) it.copy(cloudState = a[1] as Int, cloudSlot = a[2] as String?) else it }
            else -> error("unexpected ${m.name}")
        }.let { if (it is Boolean || it is Unit) null else it }
    } as RideDao

    private val cloudSlots = mutableMapOf<String, Long>()
    private val cloud = object : RideCloud {
        override suspend fun fetchRideSlots(uid: String) = Result.success(cloudSlots.toMap())
        override suspend fun createRideInSlot(uid: String, slot: String, ride: Map<String, Any>): Result<Unit> {
            cloudSlots[slot] = ride["startedAt"] as Long
            return if (lostAnswers-- > 0) Result.failure(java.io.IOException("lost")) else Result.success(Unit)
        }
        override suspend fun deleteRideSlot(uid: String, slot: String): Result<Unit> { cloudSlots.remove(slot); return Result.success(Unit) }
    }

    private fun kept(id: Long) = RideEntity(id = id, ownerId = uid, startedAt = id * 1000, endedAt = id * 1000 + 500, distanceMeters = 1000, durationMillis = 500, keepState = KeepState.KEPT)
    private fun sync() = runBlocking { syncRides(dao, cloud, uid) }
    private fun row(id: Long) = rows.first { it.id == id }

    @Test fun slotsLostAnswerFullCloudAndFreedSlot() {
        // lowest free slot
        cloudSlots["r0"] = 1L; cloudSlots["r2"] = 2L
        rows += kept(10)
        assertTrue(sync())
        assertEquals("r1", row(10).cloudSlot)

        // lost answer: the doc was written, the app saw a failure; next sync finds it by startedAt
        rows += kept(11); lostAnswers = 1
        assertTrue(sync())
        assertEquals(1, cloudSlots.values.count { it == 11_000L })   // no duplicate
        assertEquals(CloudState.UPLOADED, row(11).cloudState)
        assertEquals(cloudSlots.filterValues { it == 11_000L }.keys.single(), row(11).cloudSlot)

        // fill the cloud to 10, the next kept ride stays on the phone
        for (i in 3..9) cloudSlots["r$i"] = 100L + i
        assertEquals(10, cloudSlots.size)
        rows += kept(12)
        assertTrue(sync())
        assertEquals(CloudState.PHONE_ONLY, row(12).cloudState)

        // the rider deletes an uploaded ride: its slot frees and the phone-only ride uploads
        val freed = row(10).cloudSlot!!
        rows.replaceAll { if (it.id == 10L) it.copy(keepState = KeepState.DELETED) else it }
        assertTrue(sync())
        assertEquals(CloudState.UPLOADED, row(12).cloudState)
        assertEquals(freed, row(12).cloudSlot)
    }
}
