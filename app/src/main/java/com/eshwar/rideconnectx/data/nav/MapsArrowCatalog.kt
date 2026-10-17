package com.eshwar.rideconnectx.data.nav

import com.eshwar.rideconnectx.domain.ProtocolEngine.Maneuver as M

/**
 * Every manoeuvre arrow Google Maps ships, and the cluster code each one means.
 *
 * ### Why this exists
 *
 * Maps does not put the turn direction in the notification text. It writes the
 * road being turned *onto* and counts the distance down, so
 * `"Dammaiguda Rd  400 m"` is a turn with no left or right in the words. The
 * direction is carried only by the arrow bitmap in the large-icon slot. On the
 * 19 August 2026 ride that cost 1,643 of 1,873 frames, all sent as STRAIGHT.
 *
 * ### Where the list came from
 *
 * `aapt2 dump resources` over Maps 26.33.02, pulled off the phone with adb on
 * 20 August 2026 - not guessed, and not limited to whatever manoeuvres a driven
 * route happened to contain. A simulated route yielded five of these; the
 * catalogue has all of them.
 *
 * Names rather than resource ids, because ids shift on every Maps release and
 * names do not. They are resolved at runtime by [ArrowMatcher].
 *
 * ### Where the codes came from
 *
 * The rider's own calibration, each photographed on their Access 125. See the
 * locked table in `docs/RideConnectX-Knowledge-Base.md`. Nothing here invents a
 * cluster code.
 *
 * ### Roundabouts send the EXIT DIRECTION, not a ring
 *
 * Changed 21 August 2026 after the rider watched it on the cluster.
 *
 * All 42 roundabout variants used to collapse to the generic [M.ROUNDABOUT],
 * on the reasoning that showing "roundabout" was honest and the exit was
 * polish. Ridden, that is wrong: 61 frames of the 21 August run were
 * `roundabout_enter_and_exit_cw_slight_left` - Maps knew the exit - and the
 * cluster drew a bare ring that says nothing about which way to go.
 *
 * So the exit direction in the drawable's name now drives the code, using the
 * cluster's own exit-specific roundabout icons at **20-26**: `..._slight_left`
 * sends [M.ROUNDABOUT_EXIT_SLIGHT_LEFT] (22), `..._normal_right` sends
 * [M.ROUNDABOUT_EXIT_RIGHT] (25), and so on. The rider gets the ring *and* the
 * exit, which is what Maps was telling us all along.
 *
 * Codes 20-26 are **predicted from the APK's icon geometry**, with 23 confirmed
 * on hardware. That decode was 8 of 8 correct in the 18 August sweep, so the
 * prediction is credible - but it is a prediction, and the ride log marks these
 * frames PREDICTED ONLY so a wrong icon is obvious on reading it back.
 *
 * A bare `maneuver_roundabout_*` with no exit in its name still sends the
 * generic ring, which is honest: Maps did not state an exit.
 *
 * Ramp variants collapse into plain turns because Maps draws them identically -
 * `maneuver_on_ramp_normal_right` measures distance zero from
 * `maneuver_turn_normal_right`.
 */
object MapsArrowCatalog {

    const val MAPS_PACKAGE = "com.google.android.apps.maps"

    /** Drawable name to the cluster code it should draw. */
    val CODES: Map<String, Int> = mapOf(
        "arrive_left" to M.DESTINATION,
        "ic_arrive_left" to M.DESTINATION,
        "ic_arrive_right" to M.DESTINATION,
        "maneuver_depart" to M.STRAIGHT,
        "maneuver_destination" to M.DESTINATION,
        "maneuver_destination_left" to M.DESTINATION,
        "maneuver_destination_right" to M.DESTINATION,
        "maneuver_destination_straight" to M.DESTINATION,
        "maneuver_fork_left" to M.KEEP_LEFT,
        "maneuver_fork_right" to M.KEEP_RIGHT,
        "maneuver_keep_left" to M.KEEP_LEFT,
        "maneuver_keep_right" to M.KEEP_RIGHT,
        "maneuver_merge" to M.STRAIGHT,
        "maneuver_merge_left" to M.KEEP_LEFT,
        "maneuver_merge_right" to M.KEEP_RIGHT,
        "maneuver_name_change" to M.STRAIGHT,
        "maneuver_off_ramp_keep_left" to M.KEEP_LEFT,
        "maneuver_off_ramp_keep_right" to M.KEEP_RIGHT,
        "maneuver_off_ramp_normal_left" to M.TURN_LEFT,
        "maneuver_off_ramp_normal_right" to M.TURN_RIGHT,
        "maneuver_off_ramp_sharp_left" to M.SHARP_LEFT,
        "maneuver_off_ramp_sharp_right" to M.SHARP_RIGHT,
        "maneuver_off_ramp_slight_left" to M.RAMP_FORK_LEFT,
        "maneuver_off_ramp_slight_right" to M.RAMP_FORK_RIGHT,
        "maneuver_off_ramp_u_turn_left" to M.U_TURN,
        "maneuver_off_ramp_u_turn_right" to M.U_TURN,
        "maneuver_on_ramp_keep_left" to M.KEEP_LEFT,
        "maneuver_on_ramp_keep_right" to M.KEEP_RIGHT,
        "maneuver_on_ramp_normal_left" to M.TURN_LEFT,
        "maneuver_on_ramp_normal_right" to M.TURN_RIGHT,
        "maneuver_on_ramp_sharp_left" to M.SHARP_LEFT,
        "maneuver_on_ramp_sharp_right" to M.SHARP_RIGHT,
        "maneuver_on_ramp_slight_left" to M.SLIGHT_LEFT,
        "maneuver_on_ramp_slight_right" to M.SLIGHT_RIGHT,
        "maneuver_on_ramp_u_turn_left" to M.U_TURN,
        "maneuver_on_ramp_u_turn_right" to M.U_TURN,
        "maneuver_roundabout_enter_and_exit_ccw" to M.ROUNDABOUT,
        "maneuver_roundabout_enter_and_exit_ccw_normal_left" to M.ROUNDABOUT_EXIT_LEFT,
        "maneuver_roundabout_enter_and_exit_ccw_normal_right" to M.ROUNDABOUT_EXIT_RIGHT,
        "maneuver_roundabout_enter_and_exit_ccw_sharp_left" to M.ROUNDABOUT_EXIT_SHARP_LEFT,
        "maneuver_roundabout_enter_and_exit_ccw_sharp_right" to M.ROUNDABOUT_EXIT_SHARP_RIGHT,
        "maneuver_roundabout_enter_and_exit_ccw_slight_left" to M.ROUNDABOUT_EXIT_SLIGHT_LEFT,
        "maneuver_roundabout_enter_and_exit_ccw_slight_right" to M.ROUNDABOUT_EXIT_SLIGHT_RIGHT,
        "maneuver_roundabout_enter_and_exit_ccw_straight" to M.ROUNDABOUT_EXIT_STRAIGHT,
        "maneuver_roundabout_enter_and_exit_ccw_u_turn" to M.U_TURN,
        "maneuver_roundabout_enter_and_exit_cw" to M.ROUNDABOUT,
        "maneuver_roundabout_enter_and_exit_cw_normal_left" to M.ROUNDABOUT_EXIT_LEFT,
        "maneuver_roundabout_enter_and_exit_cw_normal_right" to M.ROUNDABOUT_EXIT_RIGHT,
        "maneuver_roundabout_enter_and_exit_cw_sharp_left" to M.ROUNDABOUT_EXIT_SHARP_LEFT,
        "maneuver_roundabout_enter_and_exit_cw_sharp_right" to M.ROUNDABOUT_EXIT_SHARP_RIGHT,
        "maneuver_roundabout_enter_and_exit_cw_slight_left" to M.ROUNDABOUT_EXIT_SLIGHT_LEFT,
        "maneuver_roundabout_enter_and_exit_cw_slight_right" to M.ROUNDABOUT_EXIT_SLIGHT_RIGHT,
        "maneuver_roundabout_enter_and_exit_cw_straight" to M.ROUNDABOUT_EXIT_STRAIGHT,
        "maneuver_roundabout_enter_and_exit_cw_u_turn" to M.U_TURN,
        "maneuver_roundabout_enter_ccw" to M.ROUNDABOUT,
        "maneuver_roundabout_enter_cw" to M.ROUNDABOUT,
        "maneuver_roundabout_exit_ccw" to M.ROUNDABOUT,
        "maneuver_roundabout_exit_cw" to M.ROUNDABOUT,
        "maneuver_straight" to M.STRAIGHT,
        "maneuver_turn_normal_left" to M.TURN_LEFT,
        "maneuver_turn_normal_right" to M.TURN_RIGHT,
        "maneuver_turn_sharp_left" to M.SHARP_LEFT,
        "maneuver_turn_sharp_right" to M.SHARP_RIGHT,
        "maneuver_turn_slight_left" to M.SLIGHT_LEFT,
        "maneuver_turn_slight_right" to M.SLIGHT_RIGHT,
        "maneuver_u_turn_left" to M.U_TURN,
        "maneuver_u_turn_right" to M.U_TURN,
    )

    val NAMES: List<String> get() = CODES.keys.toList()
}
