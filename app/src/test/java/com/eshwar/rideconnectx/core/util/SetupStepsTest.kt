package com.eshwar.rideconnectx.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Setup asks for one thing at a time, in order, and never asks twice (N1). */
class SetupStepsTest {

    @Test
    fun `walks the steps one at a time, in order`() {
        var p = SetupProgress()
        assertEquals(SetupStep.NOTIFICATIONS, SetupSteps.next(p))
        p = p.copy(notificationsAnswered = true)
        assertEquals(SetupStep.BLUETOOTH, SetupSteps.next(p))
        p = p.copy(bluetoothAnswered = true, bluetoothGranted = true)
        assertEquals(SetupStep.BLUETOOTH_ON, SetupSteps.next(p))
        p = p.copy(bluetoothPrompted = true)
        assertEquals(SetupStep.LOCATION, SetupSteps.next(p))
        p = p.copy(locationAnswered = true, locationGranted = true)
        assertEquals(SetupStep.LOCATION_ON, SetupSteps.next(p))
        p = p.copy(locationPrompted = true)
        assertEquals(SetupStep.DONE, SetupSteps.next(p))
    }

    @Test
    fun `a refusal moves on instead of asking again`() {
        val p = SetupProgress(notificationsAnswered = true, bluetoothAnswered = true)
        // Refused Bluetooth: no radio prompt, straight on to Location.
        assertEquals(SetupStep.LOCATION, SetupSteps.next(p))
        assertEquals(SetupStep.DONE, SetupSteps.next(p.copy(locationAnswered = true)))
    }

    @Test
    fun `radios already on are not prompted`() {
        val p = SetupProgress(
            notificationsAnswered = true,
            bluetoothGranted = true, bluetoothOn = true,
            locationGranted = true, locationOn = true,
        )
        assertEquals(SetupStep.DONE, SetupSteps.next(p))
    }
}
