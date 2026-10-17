package com.eshwar.rideconnectx.domain.model

import com.eshwar.rideconnectx.domain.ProtocolEngine

/**
 * One turn-by-turn instruction, as shown in-app and relayed to the cluster.
 *
 * [maneuverId] is a [ProtocolEngine.Maneuver] constant, so a maneuver can be
 * encoded into a packet without any further lookup.
 */
data class NavManeuver(
    val maneuverId: Int,
    /** Human-readable instruction, e.g. "Turn left onto MG Road". */
    val instruction: String,
    /** Distance to the maneuver, e.g. "200 m". */
    val distanceToTurn: String,
    /** Remaining trip figures, blank while unknown. */
    val etaMinutes: Int? = null,
    /** Maps' own arrival time as the cluster wants it, "1107AM"; null if absent. */
    val etaClock: String? = null,
    val remainingDistance: String = "",
    /**
     * False when no manoeuvre phrase matched and the code fell back to STRAIGHT.
     *
     * Without this a genuine "continue on NH163" and a phrasing the regexes did
     * not recognise are indistinguishable in the log - both read as code 40. The
     * 19 August ride sent 1,719 straights and there was no way to tell how many
     * were real. An unrecognised phrase silently becoming straight is the same
     * trap that made unfamiliar wording blank the cluster in August.
     */
    val phraseRecognised: Boolean = true,

    /**
     * The maneuver icon's resource name in Maps, e.g. `ic_maneuver_turn_left`.
     *
     * The 19 August ride established that the direction is **not in the words**.
     * Maps writes the road being turned ONTO and counts down to it, so
     * "Neredmet - Sainikpur X Rd  400 m" is a turn with no left or right
     * anywhere in the text. The arrow the rider sees in the notification shade
     * is this icon, which makes its name the only carrier of the direction for
     * the 87% of frames that went out as STRAIGHT that day.
     */
    val iconName: String = "",

    /**
     * What the icon name says the maneuver is, or null when it matched nothing.
     *
     * Kept separate from [maneuverId] so the two can be compared in the ride
     * log. If they ever disagree the log says so, rather than one silently
     * winning and the mismatch being invisible - the failure mode that let
     * 1,719 wrong straights through unnoticed for a whole ride.
     */
    val iconManeuverId: Int? = null,

    /** Which field the code was taken from, for the log. */
    val codeSource: Source = Source.TEXT,

    /**
     * Whether the phone screen was on when Maps posted this.
     *
     * The rider reported navigation appearing to stall with the screen off, and
     * the morning ride was dead for 66% of its length - 365 stalls, the longest
     * 82 seconds. Recording the screen state against each update is what turns
     * that from a suspicion into a measurement.
     */
    val screenOn: Boolean = true,

    /** When Maps posted this (sbn.postTime), to measure our own delay. 0 = unknown. */
    val postedAt: Long = 0L,
) {
    enum class Source {
        /** A manoeuvre phrase was found in the notification text. */
        TEXT,

        /** The text said nothing; the icon name supplied the direction. */
        ICON,

        /** Neither said anything - straight ahead, which may be wrong. */
        FALLBACK,
    }
}

/**
 * State of the current navigation session.
 *
 * Instructions originate in Google Maps: the rider starts navigation there, and
 * a notification listener feeds the maneuvers back into RideConnectX. Until that
 * listener is wired up — or when no route is running — this is [Inactive] and
 * the screen shows its empty state rather than invented directions.
 */
sealed interface NavState {
    object Inactive : NavState
    object AwaitingMaps : NavState
    data class Active(val maneuver: NavManeuver) : NavState
}
