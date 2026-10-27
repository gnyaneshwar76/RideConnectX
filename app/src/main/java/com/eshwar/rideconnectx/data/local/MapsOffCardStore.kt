package com.eshwar.rideconnectx.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eshwar.rideconnectx.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Saved facts for the "Maps notifications look off" card. Times are wall-clock millis, 0 = never. */
data class MapsOffState(
    val failedEpisodes: Int = 0,
    /** True once this install has seen any Maps post. Not used to hide; for the card wording. */
    val everSawMapsPost: Boolean = false,
    val dismissedAt: Long = 0L,
    val buttonTappedAt: Long = 0L,
)

/** The card rules. Pure, so they are unit-tested. */
object MapsOffRules {
    const val EPISODE_WINDOW_MS = 60_000L
    const val DISMISS_HIDE_MS = 14L * 24 * 60 * 60 * 1000
    const val BUTTON_HIDE_MS = 7L * 24 * 60 * 60 * 1000

    /** One failed episode: Maps was launched from our app and [EPISODE_WINDOW_MS] passed with no Maps post. */
    fun episodeFailed(launchedAt: Long, now: Long, postSeenSinceLaunch: Boolean): Boolean =
        !postSeenSinceLaunch && launchedAt > 0L && now - launchedAt >= EPISODE_WINDOW_MS

    /** A stored time in the future (clock set back) counts as never, so it cannot hide the card for ever. */
    private fun sane(t: Long, now: Long) = if (t > now) 0L else t

    private fun hiddenBy(t: Long, now: Long, forMs: Long): Boolean {
        val s = sane(t, now)
        return s > 0L && now - s < forMs
    }

    fun shouldShowMapsOffCard(
        state: MapsOffState,
        notificationAccessGranted: Boolean,
        rideActive: Boolean,
        now: Long,
    ): Boolean =
        notificationAccessGranted && !rideActive && state.failedEpisodes >= 1 &&
            !hiddenBy(state.dismissedAt, now, DISMISS_HIDE_MS) &&
            !hiddenBy(state.buttonTappedAt, now, BUTTON_HIDE_MS)
}

private val Context.mapsOffCard: DataStore<Preferences> by preferencesDataStore(
    name = "maps_off_card",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class MapsOffCardStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val failedKey = intPreferencesKey("failed_episodes")
    private val seenKey = booleanPreferencesKey("ever_saw_post")
    private val dismissedKey = longPreferencesKey("dismissed_at")
    private val tappedKey = longPreferencesKey("button_tapped_at")

    val state: Flow<MapsOffState> = context.mapsOffCard.data.map {
        MapsOffState(it[failedKey] ?: 0, it[seenKey] ?: false, it[dismissedKey] ?: 0L, it[tappedKey] ?: 0L)
    }

    suspend fun addFailedEpisode() { context.mapsOffCard.edit { it[failedKey] = (it[failedKey] ?: 0) + 1 } }

    /** Any Maps post: the card hides at once. Dismiss and tap times stay, so an old dismissal still counts. */
    suspend fun onPostSeen() { context.mapsOffCard.edit { it[seenKey] = true; it[failedKey] = 0 } }

    suspend fun dismiss(now: Long) { context.mapsOffCard.edit { it[dismissedKey] = now } }

    suspend fun buttonTapped(now: Long) { context.mapsOffCard.edit { it[tappedKey] = now } }
}

/**
 * Watches one "Maps launched from our app" episode. Runs on the application scope so it survives
 * the rider leaving the Navigation page. Not tested (needs a clock and DataStore); the rules above are.
 */
@Singleton
class MapsOffTracker @Inject constructor(
    private val store: MapsOffCardStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null
    @Volatile private var recorded = false

    /** Call when our app opens Google Maps. */
    fun onMapsLaunched() {
        job?.cancel()
        recorded = false
        job = scope.launch {
            delay(MapsOffRules.EPISODE_WINDOW_MS)
            store.addFailedEpisode()
        }
    }

    /** Call for every Maps-package post. Writes once per launch, not per frame. */
    fun onMapsPost() {
        job?.cancel()
        if (recorded) return
        recorded = true
        scope.launch { store.onPostSeen() }
    }
}
