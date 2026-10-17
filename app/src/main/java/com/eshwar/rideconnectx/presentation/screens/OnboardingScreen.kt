package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.components.ObArt

/**
 * Screens 03–05 — the onboarding content, matching `OB_DATA` in the design.
 *
 * The screen itself is [IntroScreen]: Welcome and these three pages are one
 * swipeable pager rather than four destinations, so all that survives here is
 * the copy and which illustration goes with it.
 */
enum class OnboardingStep(
    val step: Int,
    val art: ObArt,
    val title: String,
    val desc: String,
) {
    Navigation(
        step = 0,
        art = ObArt.Nav,
        title = "Turn-by-Turn Navigation",
        desc = "Real-time navigation delivered to your vehicle's display. Eyes on road — always.",
    ),
    Bluetooth(
        step = 1,
        art = ObArt.Ble,
        title = "Bluetooth Connectivity",
        desc = "Seamless BLE 5.0 pairing. Real-time data bridge between your phone and digital cluster.",
    ),
    Intelligence(
        step = 2,
        art = ObArt.Stats,
        title = "Ride Intelligence",
        desc = "Live stats, fuel tracking, trip history and diagnostics — all in one premium interface.",
    ),
}
