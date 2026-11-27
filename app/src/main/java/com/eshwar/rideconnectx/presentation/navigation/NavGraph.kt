package com.eshwar.rideconnectx.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.layout.fillMaxSize
import com.eshwar.rideconnectx.presentation.components.Dock
import com.eshwar.rideconnectx.presentation.components.DockRoutes
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import com.eshwar.rideconnectx.presentation.screens.AlertsScreen
import com.eshwar.rideconnectx.presentation.screens.DiagnosticsScreen
import com.eshwar.rideconnectx.presentation.screens.AppearanceScreen
import com.eshwar.rideconnectx.presentation.screens.BlePairingScreen
import com.eshwar.rideconnectx.presentation.screens.CreateProfileScreen
import com.eshwar.rideconnectx.presentation.screens.DashboardScreen
import com.eshwar.rideconnectx.presentation.screens.MyScooterScreen
import com.eshwar.rideconnectx.presentation.screens.LegalScreen
import com.eshwar.rideconnectx.presentation.screens.PrivacyTermsScreen
import com.eshwar.rideconnectx.presentation.screens.NavigationScreen
import com.eshwar.rideconnectx.presentation.screens.NotificationsScreen
import com.eshwar.rideconnectx.presentation.screens.PermissionDetailsScreen
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
    /** The one-page welcome with a Start button (first open only). */
    const val INTRO = "intro"
    const val SIGN_IN = "signin"
    const val TERMS = "terms"
    const val PRIVACY = "privacy"

    /** Terms and Privacy on a single page — what the profile step links to. */
    const val LEGAL = "legal"
    /** "Add your guest data to this account?" — a guest signing into an account with a profile. */
    const val PROFILE_FOUND = "profile_found"
    /** The Create Profile form, only the vehicle part of it. */
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
    /** Picture, details, Connect and Forget for the paired scooter; opened from Settings. */
    const val MY_SCOOTER = "my_scooter"
    const val ALERTS = "alerts"
    const val DIAGNOSTICS = "diagnostics"
    const val APPEARANCE = "appearance"
    const val ABOUT = "about"
    const val PRIVACY_TERMS = "privacy_terms"

    /**
     * The reference page, reached from Settings. Permissions are asked
     * when needed (Bluetooth on pairing and so on), not on a setup page.
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
    Routes.SPLASH, Routes.INTRO, Routes.SIGN_IN, Routes.TERMS, Routes.PRIVACY,
    Routes.LEGAL, Routes.PROFILE_FOUND,
)

/** True when both routes are dock tabs: the dock switches with a fade, not a slide. Pure, so it is unit-tested. */
internal fun isTabSwitch(from: String?, to: String?): Boolean =
    from != null && to != null && from != to && from in DockRoutes && to in DockRoutes

/**
 * The first tap always works, also while a screen transition runs (the old
 * "must be RESUMED" guard swallowed taps on slow frames). Double fire is stopped
 * differently: go() uses launchSingleTop and a same-route check, Back is
 * debounced (a second Back from the screen that is still fading out would pop
 * two screens).
 */
private var lastBackMs = 0L

private fun NavController.safeBack() {
    val now = android.os.SystemClock.uptimeMillis()
    if (now - lastBackMs < 350) return
    lastBackMs = now
    popBackStack()
}

/** Sign In after a sign-out: Welcome sits underneath, so Back has somewhere to go. */
private fun NavController.goSignInFresh() {
    navigate(Routes.INTRO) { popUpTo(0) { inclusive = true } }
    navigate(Routes.SIGN_IN)
}

private fun NavController.safeGo(route: String) {
    if (currentDestination?.route != route) navigate(route) { launchSingleTop = true }
}

/** Dock tab: one entry per tab, Back from any tab lands on Home (N49 keeps Home as the exit). */
private fun NavController.goTab(route: String) {
    if (currentDestination?.route != route) {
        navigate(route) {
            launchSingleTop = true
            popUpTo(Routes.DASHBOARD)
        }
    }
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

    // Deeper screens (service, safety, profile, ride detail...): a spring slide,
    // the old screen drifting back and dimming; Back reverses it. Switching
    // between two dock tabs is only a calm short fade, so the dock never seems
    // to move the whole page. Leaving the splash only fades. Springs follow the
    // phone's animation scale, so with animations off they land at once.
    val spec = RcxMotion.smooth<androidx.compose.ui.unit.IntOffset>()
    val fadeSpring = RcxMotion.smooth<Float>()
    val tabFade = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
    val fade = tween<Float>(RcxMotion.SCREEN_MS)
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = {
            if (initialState.destination.route == Routes.SPLASH) fadeIn(tween(500))
            else if (isTabSwitch(initialState.destination.route, targetState.destination.route)) fadeIn(tabFade)
            else slideInHorizontally(spec) { it } + fadeIn(fadeSpring, initialAlpha = 0.6f)
        },
        exitTransition = {
            if (initialState.destination.route == Routes.SPLASH) fadeOut(tween(300))
            else if (isTabSwitch(initialState.destination.route, targetState.destination.route)) fadeOut(tabFade)
            else slideOutHorizontally(spec) { -it / 4 } + fadeOut(fadeSpring, targetAlpha = 0.35f)
        },
        popEnterTransition = {
            if (isTabSwitch(initialState.destination.route, targetState.destination.route)) fadeIn(tabFade)
            else slideInHorizontally(spec) { -it / 4 } + fadeIn(fadeSpring, initialAlpha = 0.35f)
        },
        popExitTransition = {
            if (isTabSwitch(initialState.destination.route, targetState.destination.route)) fadeOut(tabFade)
            else slideOutHorizontally(spec) { it } + fadeOut(fadeSpring, targetAlpha = 0.6f)
        },
    ) {

        composable(Routes.SPLASH) {
            val authVm: AuthViewModel = hiltViewModel()
            val authState by authVm.authState.collectAsStateWithLifecycle()

            val scope = rememberCoroutineScope()

            SplashScreen(
                onFinished = {
                    // A guest or account already on the phone goes straight to Home.
                    scope.launch {
                        val signedIn = authState is AuthState.Authenticated
                        if (!signedIn) authVm.dropUnfinishedSignIn()
                        // A sign-in link opened the app: straight to Sign In, which finishes it.
                        val destination = if (!signedIn && authVm.emailLinkWaiting) Routes.SIGN_IN
                            else SetupGate.launchRoute(signedIn, authVm.isGuestMergePending())
                        navController.navigate(destination) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.INTRO) {
            val authVm: AuthViewModel = hiltViewModel()
            val scope = rememberCoroutineScope()
            // Start makes the guest "Rider", then Home; Back from Home leaves the app.
            IntroScreen(
                onStart = {
                    scope.launch {
                        authVm.ensureGuest()
                        if (navController.currentDestination?.route == Routes.INTRO) navController.navigate(Routes.DASHBOARD) { popUpTo(0) { inclusive = true } }
                    }
                },
                // Back from Sign In pops to Welcome; success is handled by the SIGN_IN route.
                onSignIn = { navController.safeGo(Routes.SIGN_IN) },
            )
        }

        composable(Routes.SIGN_IN) {
            val authVm: AuthViewModel = hiltViewModel()
            val authState by authVm.authState.collectAsStateWithLifecycle()
            val signingIn by remember { authVm.ui.map { it.isBusy } }.collectAsStateWithLifecycle(false)
            val scope = rememberCoroutineScope()

            // Sign-in success is observed rather than pushed, so Google, email
            // and guest all converge on one navigation path. It waits for the
            // sign-in to *finish*, not just for Firebase: the account's profile
            // is restored after Firebase reports signed in, and routing before
            // that sent a returning rider to Create Profile (rider, 26 Sep).
            LaunchedEffect(authState, signingIn) {
                if (authState is AuthState.Authenticated && !signingIn) {
                    authVm.ensureGuest()
                    navController.navigate(SetupGate.launchRoute(signedIn = true, guestMergePending = authVm.isGuestMergePending())) {
                        // The whole intro, not just this screen. Popping only
                        // Sign In left Welcome and the three onboarding pages
                        // underneath, so Back from Permissions walked a
                        // signed-in rider straight back into the tour.
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            SignInScreen(
                onGuest = {
                    scope.launch {
                        authVm.ensureGuest()
                        navController.navigate(Routes.DASHBOARD) { popUpTo(0) { inclusive = true } }
                    }
                },
                onTerms = { navController.safeGo(Routes.TERMS) },
                onPrivacy = { navController.safeGo(Routes.PRIVACY) },
                onBack = {
                    if (navController.previousBackStackEntry != null) navController.safeBack()
                    else navController.navigate(Routes.INTRO) { popUpTo(0) { inclusive = true } }
                },
                vm = authVm,
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

        composable(Routes.PROFILE_FOUND) {
            ProfileFoundScreen(
                onAdded = { _ ->
                    // Setup resumes if the account's own profile is unfinished.
                    navController.navigate(SetupGate.launchRoute(signedIn = true)) {
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
                onProfile = { navController.safeGo(Routes.PROFILE) },
                onMyScooter = { navController.safeGo(Routes.MY_SCOOTER) },
                onService = { navController.safeGo(Routes.SERVICE) },
                onAlerts = { navController.safeGo(Routes.ALERTS) },
                onAppearance = { navController.safeGo(Routes.APPEARANCE) },
                onPermissions = { navController.safeGo(Routes.PERMISSION_DETAILS) },
                onPrivacy = { navController.safeGo(Routes.PRIVACY_TERMS) },
                onAbout = { navController.safeGo(Routes.ABOUT) },
                onDiagnostics = { navController.safeGo(Routes.DIAGNOSTICS) },
            )
        }

        composable(Routes.ALERTS) {
            AlertsScreen(onBack = { navController.safeBack() })
        }

        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(onBack = { navController.safeBack() })
        }

        composable(Routes.MY_SCOOTER) {
            MyScooterScreen(
                onBack = { navController.safeBack() },
                onPairVehicle = { navController.safeGo(Routes.BLE) },
            )
        }

        composable(Routes.PERMISSION_DETAILS) {
            PermissionDetailsScreen(onBack = { navController.safeBack() })
        }

        composable(Routes.APPEARANCE) {
            AppearanceScreen(onBack = { navController.safeBack() })
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.safeBack() })
        }

        composable(Routes.PRIVACY_TERMS) {
            PrivacyTermsScreen(
                onBack = { navController.safeBack() },
                onPrivacy = { navController.safeGo(Routes.PRIVACY) },
                onTerms = { navController.safeGo(Routes.TERMS) },
                onSignedOut = { navController.goSignInFresh() },
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.safeBack() },
                onChangeVehicle = { navController.safeGo(Routes.CHANGE_VEHICLE) },
                onSignedOut = {
                    // Everything behind the account goes with it, so the whole
                    // back stack is cleared rather than left to be popped into.
                    navController.goSignInFresh()
                },
            )
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = currentRoute in DockRoutes && currentRoute !in ENTRY_ROUTES,
        modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
        enter = fadeIn(fade),
        exit = fadeOut(fade),
    ) {
        Dock(selectedRoute = currentRoute, onSelect = { navController.goTab(it) })
    }
    }
}
