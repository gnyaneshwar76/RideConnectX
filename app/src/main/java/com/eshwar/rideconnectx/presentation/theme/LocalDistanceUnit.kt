package com.eshwar.rideconnectx.presentation.theme

import androidx.compose.runtime.compositionLocalOf
import com.eshwar.rideconnectx.domain.model.DistanceUnit

/**
 * The units distances are displayed in, from Screen 20 (Settings).
 *
 * A composition local rather than a parameter threaded through every card:
 * almost every distance in the app is drawn several composables deep, and the
 * setting is read-only to all of them. Screens that show distances provide it
 * from their own ViewModel; the default keeps previews and any screen that has
 * not opted in on kilometres, which is what the vehicle actually reports.
 *
 * Read it once at the top of a composable — `val unit = LocalDistanceUnit.current`
 * — so the plain `unit.format(km)` can also be called from ordinary lambdas.
 */
val LocalDistanceUnit = compositionLocalOf { DistanceUnit.KM }
