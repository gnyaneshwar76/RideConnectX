package com.eshwar.rideconnectx.data.local

import com.eshwar.rideconnectx.data.local.MapsOffRules.shouldShowMapsOffCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapsOffRulesTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day
    private val failed = MapsOffState(failedEpisodes = 1)

    private fun show(
        s: MapsOffState = failed, access: Boolean = true, ride: Boolean = false, at: Long = now,
    ) = shouldShowMapsOffCard(s, access, ride, at)

    @Test fun h1_accessOff_hides() = assertFalse(show(access = false))
    @Test fun h2_noFailedEpisode_hides() = assertFalse(show(MapsOffState()))
    @Test fun h3_oneFailedEpisode_shows() = assertTrue(show())
    @Test fun h4_rideActive_hides() = assertFalse(show(ride = true))
    @Test fun h5_postSeenResetsCount_hides() =
        assertFalse(show(MapsOffState(failedEpisodes = 0, everSawMapsPost = true)))
    @Test fun h6_everSeenButFailedAgain_stillShows() =
        assertTrue(show(failed.copy(everSawMapsPost = true)))
    @Test fun h7_dismissed3DaysAgo_hides() = assertFalse(show(failed.copy(dismissedAt = now - 3 * day)))
    @Test fun h8_dismissed15DaysAgo_shows() = assertTrue(show(failed.copy(dismissedAt = now - 15 * day)))
    @Test fun h9_buttonTapped3DaysAgo_hides() = assertFalse(show(failed.copy(buttonTappedAt = now - 3 * day)))
    @Test fun h9b_buttonTapped8DaysAgo_shows() = assertTrue(show(failed.copy(buttonTappedAt = now - 8 * day)))
    @Test fun h9c_dismissExactly14Days_shows() = assertTrue(show(failed.copy(dismissedAt = now - 14 * day)))
    @Test fun h10_futureTimestamps_doNotHideForever() =
        assertTrue(show(failed.copy(dismissedAt = now + 5 * day, buttonTappedAt = now + 9 * day)))
    @Test fun h11_episodeRule() {
        val w = MapsOffRules.EPISODE_WINDOW_MS
        assertFalse(MapsOffRules.episodeFailed(1000, 1000 + w - 1, false))
        assertTrue(MapsOffRules.episodeFailed(1000, 1000 + w, false))
        assertFalse(MapsOffRules.episodeFailed(1000, 1000 + w, true))
        assertFalse(MapsOffRules.episodeFailed(0, w * 2, false))
    }
}
