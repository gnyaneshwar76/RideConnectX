package com.eshwar.rideconnectx.presentation.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.eshwar.rideconnectx.core.util.rememberPermissionsController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.eshwar.rideconnectx.presentation.theme.RcxMotion
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.data.local.db.NotificationKind
import com.eshwar.rideconnectx.domain.model.AuthState
import com.eshwar.rideconnectx.domain.model.LegalDoc
import com.eshwar.rideconnectx.presentation.screens.AboutScreen
import com.eshwar.rideconnectx.presentation.screens.AppearanceScreen
import com.eshwar.rideconnectx.presentation.screens.BlePairingScreen
import com.eshwar.rideconnectx.presentation.screens.CreateProfileScreen
import com.eshwar.rideconnectx.presentation.screens.DashboardScreen
import com.eshwar.rideconnectx.presentation.screens.GuestProfileScreen
import com.eshwar.rideconnectx.presentation.screens.LegalScreen
import com.eshwar.rideconnectx.presentation.screens.NavigationScreen
import com.eshwar.rideconnectx.presentation.screens.NotificationsScreen
import com.eshwar.rideconnectx.presentation.screens.PermissionDetailsScreen
import com.eshwar.rideconnectx.presentation.screens.PermissionsScreen
import com.eshwar.rideconnectx.presentation.screens.ProfileFoundScreen
import com.eshwar.rideconnectx.presentation.screens.ProfileScreen
import com.eshwar.rideconnectx.presentation.screens.SafetyScreen
import com.eshwar.rideconnectx.presentation.screens.ServiceScreen
import com.eshwar.rideconnectx.presentation.screens.SettingsScreen
import com.eshwar.rideconnectx.presentation.screens.SignInScreen
import com.eshwar.rideconnectx.presentation.screens.SplashScreen
import com.eshwar.rideconnectx.presentation.screens.StatisticsScreen
import com.eshwar.rideconnectx.presentation.screens.VehicleGalleryScreen
import com.eshwar.rideconnectx.presentation.screens.IntroScreen
import com.eshwar.rideconnectx.presentation.viewmodel.AuthViewModel
import com.eshwar.rideconnectx.presentation.viewmodel.ProfileFormMode

/** Route names mirror the `ScreenId` union in the design. */
object Routes {
    const val SPLASH = "splash"
    /** Welcome plus the three onboarding pages — one swipeable pager. */
    const val INTRO = "intro"
    const val SIGN_IN = "signin"
    const val GUEST_PROFILE = "guestCreate"
    const val TERMS = "terms"
    const val PRIVACY = "privacy"

    /** Terms and Privacy on a single page — what the profile step links to. */
    const val LEGAL = "legal"
    /** "Add your guest data to this account?" — a guest signing into an account with a profile. */
    const val PROFILE_FOUND = "profile_found"
    const val PERMISSIONS = "perms"
    const val VEHICLE = "vehicle"

    /** The same form as [VEHICLE], but only the vehicle part of it. */
    const val CHANGE_VEHICLE = "vehicle_change"
    const val DASHBOARD = "dash"
    const val BLE = "ble"
    const val NAVIGATION = "nav"
    const val STATS = "stats"
    const val RIDE = "ride/{id}"
    fun ride(id: Long) = "ride/$id"
    const val NOTIFICATIONS = "notifs"
    const val SERVICE = "service"
    const val SAFETY = "safety"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val APPEARANCE = "appearance"
    const val ABOUT = "about"

    /**
     * The reference page, reached from Settings. Distinct from [PERMISSIONS],
     * which is the one-time setup step that fires the dialogs automatically.
     */
    const val PERMISSION_DETAILS = "permission_details"
}

/** Where a notification of [kind] leads — the in-app list and a tapped phone notification alike. */
private fun routeFor(kind: NotificationKind): String? = when (kind) {
    NotificationKind.RIDE -> Routes.STATS
    NotificationKind.VEHICLE -> Routes.BLE
    NotificationKind.SERVICE -> Routes.SERVICE
    NotificationKind.SAFETY -> Routes.SAFETY
    // System notices refer to the app itself, so there is nowhere to push;
    // they stay on the list.
    NotificationKind.SYSTEM -> null
    // Calls and messages are a record only; nothing to open.
    NotificationKind.CALL, NotificationKind.MESSAGE -> null
}

/** Screens a rider is still getting into the app on; a notification waits past them. */
private val ENTRY_ROUTES = setOf(
    Routes.SPLASH, Routes.INTRO, Routes.SIGN_IN, Routes.GUEST_PROFILE, Routes.TERMS, Routes.PRIVACY,
    Routes.LEGAL, Routes.PROFILE_FOUND, Routes.PERMISSIONS, Routes.VEHICLE,
)

/**
 * A second tap during the screen transition is ignored (CHK-27): only the
 * screen that is fully on top may navigate. Without it a double tap on Back
 * popped two screens and a double tap on a card stacked the same screen twice.
 */
private fun NavController.onTop() =
    currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED

private fun NavController.safeBack() { if (onTop()) popBackStack() }

private fun NavController.safeGo(route: String) {
    if (onTop()) navigate(route) { launchSingleTop = true }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    /** A tapped phone notification's [NotificationKind] name (N13). */
    openKind: String? = null,
    onKindOpened: () -> Unit = {},
) {
    // Opened once the rider is past splash and setup, so a tap from the shade
    // lands on e.g. Service with the Dashboard behind it.
    val current by navController.currentBackStackEntryAsState()
    val currentRoute = current?.destination?.route
    LaunchedEffect(openKind, currentRoute) {
        if (openKind == null || currentRoute == null || currentRoute in ENTRY_ROUTES) return@LaunchedEffect
        onKindOpened()
        runCatching { NotificationKind.valueOf(openKind) }.getOrNull()
            ?.let(::routeFor)
            ?.takeIf { it != currentRoute }
            ?.let { navController.navigate(it) }
    }

    // Push: the new screen slides in from the right while the old one drifts
    // back and dims; Back reverses it, so the rider always sees where they came
    // from. Leaving the splash only fades — nothing is "behind" it.
    val spec = RcxMotion.smooth<androidx.compose.ui.unit.IntOffset>()
    val fade = tween<Float>(RcxMotion.SCREEN_MS)
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = {
            if (initialState.destination.route == Routes.SPLASH) fadeIn(tween(500))
            else slideInHorizontally(spec) { it } + fadeIn(fade, initialAlpha = 0.6f)
        },
        exitTransition = {
            if (initialState.destination.route == Routes.SPLASH) fadeOut(tween(300))
            else slideOutHorizontally(spec) { -it / 4 } + fadeOut(fade, targetAlpha = 0.35f)
        },
        popEnterTransition = { slideInHorizontally(spec) { -it / 4 } + fadeIn(fade, initialAlpha = 0.35f) },
        popExitTransition = { slideOutHorizontally(spec) { it } + fadeOut(fade, targetAlpha = 0.6f) },
    ) {

        composable(Routes.SPLASH) {
            val authVm: AuthViewModel = hiltViewModel()
            val authState by authVm.authState.collectAsStateWithLifecycle()

            val scope = rememberCoroutineScope()

            SplashScreen(
                onFinished = {
                    // Auto-login: an already-authenticated user never sees the
                    // sign-in flow. Loading resolves before the animation ends,
                    // so there is no extra wait. Unfinished setup starts over.
                    scope.launch {
                        val signedIn = authState is AuthState.Authenticated
                        val profileDone = authVm.isProfileCompleted()
                        val mergePending = authVm.isGuestMergePending()
                        when {
                            SetupGate.mustReset(signedIn, profileDone, mergePending) -> authVm.resetUnfinishedSetup()
                            !signedIn -> authVm.dropUnfinishedSignIn()
                        }
                        // A sign-in link opened the app: straight to Sign In, which finishes it.
                        val destination = if (!signedIn && authVm.emailLinkWaiting) Routes.SIGN_IN
                            else SetupGate.launchRoute(signedIn, profileDone, mergePending)
                        navController.navigate(destination) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.INTRO) {
            IntroScreen(
                onFinished = { navController.safeGo(Routes.SIGN_IN) },
                onSkip = { navController.safeGo(Routes.SIGN_IN) },
            )
        }

        composable(Routes.SIGN_IN) {
            val authVm: AuthViewModel = hiltViewModel()
            val authState by authVm.authState.collectAsStateWithLifecycle()
            val signingIn by remember { authVm.ui.map { it.isBusy } }.collectAsStateWithLifecycle(false)

            // Sign-in success is observed rather than pushed, so Google, email
            // and guest all converge on one navigation path. It waits for the
            // sign-in to *finish*, not just for Firebase: the account's profile
            // is restored after Firebase reports signed in, and routing before
            // that sent a returning rider to Create Profile (rider, 26 Sep).
            LaunchedEffect(authState, signingIn) {
                if (authState is AuthState.Authenticated && !signingIn) {
                    navController.navigate(Routes.PERMISSIONS) {
                        // The whole intro, not just this screen. Popping only
                        // Sign In left Welcome and the three onboarding pages
                        // underneath, so Back from Permissions walked a
                        // signed-in rider straight back into the tour.
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            SignInScreen(
                onGuest = { navController.safeGo(Routes.GUEST_PROFILE) },
                onTerms = { navController.safeGo(Routes.TERMS) },
                onPrivacy = { navController.safeGo(Routes.PRIVACY) },
                vm = authVm,
            )
        }

        composable(Routes.GUEST_PROFILE) {
            GuestProfileScreen(
                onBack = { navController.safeBack() },
                onContinue = {
                    navController.navigate(Routes.PERMISSIONS) {
                        // Sign-in is done; don't let Back return into it, or
                        // into the onboarding tour sitting underneath it.
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.TERMS) {
            LegalScreen(doc = LegalDoc.TERMS, onBack = { navController.safeBack() })
        }

        composable(Routes.PRIVACY) {
            LegalScreen(doc = LegalDoc.PRIVACY, onBack = { navController.safeBack() })
        }

        composable(Routes.LEGAL) {
            LegalScreen(doc = LegalDoc.BOTH, onBack = { navController.safeBack() })
        }

        composable(Routes.PERMISSIONS) {
            val authVm: AuthViewModel = hiltViewModel()
            val perms = rememberPermissionsController()

            val scope = rememberCoroutineScope()
            // A returning rider goes straight to the dashboard. A guest who
            // signed into an account that already has a profile is asked first
            // whether to add their guest data to it (rider, 26 Sep).
            suspend fun next(done: Boolean) {
                when (val route = SetupGate.afterPermissions(done, authVm.isGuestMergePending())) {
                    Routes.VEHICLE -> navController.navigate(route)
                    else -> navController.navigate(route) { popUpTo(0) { inclusive = true } }
                }
            }
            // Nothing to ask for: don't show the setup screen at all.
            LaunchedEffect(Unit) {
                if (perms.allRequiredGranted) next(authVm.isProfileCompleted())
            }

            // The stored value, not the StateFlow's `false` before it loads.
            PermissionsScreen(onContinue = { scope.launch { next(authVm.isProfileCompleted()) } })
        }

        composable(Routes.PROFILE_FOUND) {
            ProfileFoundScreen(
                onAdded = { profileDone ->
                    // Setup resumes if the account's own profile is unfinished.
                    navController.navigate(SetupGate.launchRoute(signedIn = true, profileDone = profileDone)) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onChooseAnother = {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // Create Profile — name, city, vehicle, paint and consent. Runs once,
        // ahead of the dashboard, because the dashboard renders the chosen
        // vehicle and greets the rider by name.
        composable(Routes.VEHICLE) {
            // Back here asks before leaving; see CreateProfileScreen.
            val authVm: AuthViewModel = hiltViewModel()
            val activity = LocalActivity.current
            val scope = rememberCoroutineScope()
            CreateProfileScreen(
                onExitSetup = {
                    scope.launch {
                        authVm.resetUnfinishedSetup()
                        activity?.finish()
                    }
                },
                onContinue = {
                    navController.navigate(Routes.DASHBOARD) {
                        // The whole setup run, not just this step. Popping only
                        // this screen left Permissions underneath, so Back from
                        // the Dashboard dropped the rider into a setup screen
                        // they had already finished.
                        popUpTo(0) { inclusive = true }
                    }
                },
                onLegal = { navController.safeGo(Routes.LEGAL) },
            )
        }

        // Changing the vehicle later reuses the same form with everything the
        // rider already settled — name, city, photo, consent — left out.
        composable(Routes.CHANGE_VEHICLE) {
            CreateProfileScreen(
                mode = ProfileFormMode.CHANGE_VEHICLE,
                onBack = { navController.safeBack() },
                onContinue = { navController.safeBack() },
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigate = { navController.safeGo(Routes.NAVIGATION) },
                onPairVehicle = { navController.safeGo(Routes.BLE) },
                onStatistics = { navController.safeGo(Routes.STATS) },
                onNotifications = { navController.safeGo(Routes.NOTIFICATIONS) },
                onService = { navController.safeGo(Routes.SERVICE) },
                onSafety = { navController.safeGo(Routes.SAFETY) },
                onSettings = { navController.safeGo(Routes.SETTINGS) },
                onProfile = { navController.safeGo(Routes.PROFILE) },
            )
        }

        composable(Routes.BLE) {
            BlePairingScreen(
                onBack = { navController.safeBack() },
                // N41: go back to the Dashboard already underneath. Re-navigating to
                // it with popUpTo(inclusive) emptied the stack and let Back reach Welcome.
                onPaired = {
                    if (!navController.popBackStack(Routes.DASHBOARD, inclusive = false)) {
                        navController.navigate(Routes.DASHBOARD) { popUpTo(0) { inclusive = true } }
                    }
                },
                onSkip = { navController.safeBack() },
            )
        }

        composable(Routes.NAVIGATION) {
            NavigationScreen(onBack = { navController.safeBack() })
        }
        composable(Routes.STATS) {
            StatisticsScreen(
                onBack = { navController.safeBack() },
                onOpenRide = { navController.safeGo(Routes.ride(it)) },
            )
        }
        composable(
            Routes.RIDE,
            arguments = listOf(androidx.navigation.navArgument("id") { type = androidx.navigation.NavType.LongType }),
        ) {
            com.eshwar.rideconnectx.presentation.screens.RideDetailScreen(onBack = { navController.safeBack() })
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                onBack = { navController.safeBack() },
                // The screen stays destination-agnostic; routing lives here.
                onOpen = { kind -> routeFor(kind)?.let { navController.navigate(it) } },
            )
        }

        composable(Routes.SERVICE) {
            ServiceScreen(onBack = { navController.safeBack() })
        }

        composable(Routes.SAFETY) {
            SafetyScreen(onBack = { navController.safeBack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.safeBack() },
                onAppearance = { navController.safeGo(Routes.APPEARANCE) },
                onAbout = { navController.safeGo(Routes.ABOUT) },
                onPermissions = { navController.safeGo(Routes.PERMISSION_DETAILS) },
                onPrivacy = { navController.safeGo(Routes.PRIVACY) },
                onTerms = { navController.safeGo(Routes.TERMS) },
                onPairVehicle = { navController.safeGo(Routes.BLE) },
                onProfile = { navController.safeGo(Routes.PROFILE) },
            )
        }

        composable(Routes.PERMISSION_DETAILS) {
            PermissionDetailsScreen(onBack = { navController.safeBack() })
        }

        composable(Routes.APPEARANCE) {
            AppearanceScreen(onBack = { navController.safeBack() })
        }

        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.safeBack() },
                onPrivacy = { navController.safeGo(Routes.PRIVACY) },
                onTerms = { navController.safeGo(Routes.TERMS) },
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.safeBack() },
                onChangeVehicle = { navController.safeGo(Routes.CHANGE_VEHICLE) },
                onSignedOut = {
                    // Everything behind the account goes with it, so the whole
                    // back stack is cleared rather than left to be popped into.
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
