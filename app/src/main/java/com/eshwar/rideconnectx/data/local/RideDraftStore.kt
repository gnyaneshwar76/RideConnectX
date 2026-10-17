package com.eshwar.rideconnectx.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A ride in progress, kept on disk in case the app is killed before it ends.
 * [topSpeedKmh] is the max speed; [route] is the encoded polyline so far.
 */
data class RideDraft(
    val startedAt: Long, val lastMoveAt: Long, val metres: Int, val topSpeedKmh: Int,
    val route: String = "",
)

@Singleton
class RideDraftStore @Inject constructor(@ApplicationContext context: Context) {
    // v2: the v8 draft (trip-meter ride, no route) is ignored and its file removed.
    private val prefs = context.getSharedPreferences("ride_draft_v2", Context.MODE_PRIVATE)

    init { runCatching { context.deleteSharedPreferences("ride_draft") } }

    fun save(d: RideDraft) {
        prefs.edit()
            .putLong("startedAt", d.startedAt).putLong("lastMoveAt", d.lastMoveAt)
            .putInt("metres", d.metres).putInt("top", d.topSpeedKmh)
            .putString("route", d.route)
            .apply()
    }

    fun load(): RideDraft? =
        if (prefs.getInt("metres", 0) <= 0) null
        else RideDraft(
            prefs.getLong("startedAt", 0), prefs.getLong("lastMoveAt", 0),
            prefs.getInt("metres", 0), prefs.getInt("top", 0),
            prefs.getString("route", "") ?: "",
        )

    fun clear() = prefs.edit().clear().apply()
}
