package com.eshwar.rideconnectx.core.util

/** One thing the setup screen asks for. The order here is the order asked. */
enum class SetupStep { NOTIFICATIONS, BLUETOOTH, BLUETOOTH_ON, LOCATION, LOCATION_ON, DONE }

/** Where the rider has got to on the setup screen. "Answered" means asked this run, allowed or not. */
data class SetupProgress(
    val notificationsAnswered: Boolean = false,
    val bluetoothGranted: Boolean = false,
    val bluetoothAnswered: Boolean = false,
    val bluetoothOn: Boolean = false,
    val bluetoothPrompted: Boolean = false,
    val locationGranted: Boolean = false,
    val locationAnswered: Boolean = false,
    val locationOn: Boolean = false,
    val locationPrompted: Boolean = false,
)

/**
 * Setup asks for exactly one thing at a time.
 *
 * It used to fire every permission dialog back to back and then both "turn it
 * on" prompts together, so the rider saw a stack of system dialogs with no idea
 * which was which (rider, 26 Sep). Now each step comes up by itself once the
 * one before it is answered, and a step is never asked twice in one run.
 */
object SetupSteps {
    fun next(p: SetupProgress): SetupStep = when {
        !p.notificationsAnswered -> SetupStep.NOTIFICATIONS
        !p.bluetoothGranted && !p.bluetoothAnswered -> SetupStep.BLUETOOTH
        // The radio prompt needs the permission on Android 12+, so it only
        // follows a grant.
        p.bluetoothGranted && !p.bluetoothOn && !p.bluetoothPrompted -> SetupStep.BLUETOOTH_ON
        !p.locationGranted && !p.locationAnswered -> SetupStep.LOCATION
        p.locationGranted && !p.locationOn && !p.locationPrompted -> SetupStep.LOCATION_ON
        else -> SetupStep.DONE
    }
}
