package com.eshwar.rideconnectx.data.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScooterCandidateFilterTest {
    @Test fun acceptsTwelveCharClusterNames() {
        for (n in listOf("SAS210217219", "SBS010000000", "SCE100000000", "SCS010000000", "SBM010000000"))
            assertTrue(n, ScooterCandidateFilter.isClusterNameCandidate(n))
    }

    @Test fun rejectsWrongLengthAndOtherNames() {
        for (n in listOf("SAS21021721", "SAS2102172190", "Galaxy Watch4", "XYZ123456789", ""))
            assertFalse(n, ScooterCandidateFilter.isClusterNameCandidate(n))
        assertFalse(ScooterCandidateFilter.isClusterNameCandidate(null))
    }

    @Test fun usesScanRecordNameWhenCachedNameIsNull() {
        val n = ScooterCandidateFilter.pickName("SBS010000000", null)
        assertTrue(ScooterCandidateFilter.isClusterNameCandidate(n))
        org.junit.Assert.assertEquals("SBS010000000", n)
        org.junit.Assert.assertEquals("cached", ScooterCandidateFilter.pickName(null, "cached"))
    }
}
