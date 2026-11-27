package com.eshwar.rideconnectx.data.local

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.datastore.preferences.core.floatPreferencesKey
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eshwar.rideconnectx.domain.model.AccentColor
import com.eshwar.rideconnectx.domain.model.BackgroundStyle
import com.eshwar.rideconnectx.domain.model.ButtonStyle
import com.eshwar.rideconnectx.domain.model.ColourMix
import com.eshwar.rideconnectx.domain.model.SurfaceStyle
import com.eshwar.rideconnectx.domain.model.AppSettings
import com.eshwar.rideconnectx.domain.model.DistanceUnit
import com.eshwar.rideconnectx.domain.model.FontSize
import com.eshwar.rideconnectx.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettings: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings",
    // A corrupt file reads as empty (defaults) instead of crashing every launch.
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { androidx.datastore.preferences.core.emptyPreferences() },
)

/**
 * Everything Screen 20 (Settings) and Screen 22 (Appearance) persist.
 *
 * One store, because the two screens edit one set of preferences and the design
 * has Appearance opening *from* Settings. Every write lands immediately — the
 * design's rule is that a toggle saves the moment it moves.
 *
 * Enums are stored by name so adding a case never renumbers the existing ones,
 * and an unreadable value falls back to the default rather than throwing.
 */
@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        // General
        val UNITS = stringPreferencesKey("distance_unit")
        val SURFACE_STYLE = stringPreferencesKey("surface_style")
        val GLASS_INTENSITY = floatPreferencesKey("glass_intensity")
        val RIDE_RECORDER = booleanPreferencesKey("ride_recorder")
        val GPS_COUNTDOWN = booleanPreferencesKey("gps_countdown")
        val AUTO_START_NAV = booleanPreferencesKey("auto_start_navigation")

        // Bluetooth
        val AUTO_CONNECT = booleanPreferencesKey("ble_auto_connect")

        // Notifications
        val RIDE_NOTIFICATIONS = booleanPreferencesKey("notify_rides")
        val CONNECTION_ALERTS = booleanPreferencesKey("notify_connection")

        const val APP_SWITCH_PREFIX = "notif_app_"
        val SEEN_PACKAGES = androidx.datastore.preferences.core.stringSetPreferencesKey("notif_seen_packages")

        // Alerts on the scooter (switches and a choice only; no notification text is stored)
        val ALERTS_CLUSTER = booleanPreferencesKey("alerts_cluster")
        val ALERTS_IMPORTANT = booleanPreferencesKey("alerts_important")
        val ALERTS_MEDIUM = booleanPreferencesKey("alerts_medium")
        val ALERTS_NAME_ONLY = booleanPreferencesKey("alerts_name_only")

        // Appearance
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent_color")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val COLOUR_MIX = stringPreferencesKey("colour_mix")
        val BUTTON_STYLE = stringPreferencesKey("button_style")
        val BACKGROUND_STYLE = stringPreferencesKey("background_style")
    }

    val settings: Flow<AppSettings> = context.appSettings.data.map { p ->
        AppSettings(
            // Never chosen: follow the phone region. A saved choice wins.
            distanceUnit = p[UNITS].toEnum(DistanceUnit.defaultForRegion(java.util.Locale.getDefault().country)),
            surfaceStyle = p[SURFACE_STYLE].toEnum(SurfaceStyle.FLAT),
            // Always on, whatever was saved before (switches removed).
            autoStartNavigation = true,
            autoConnect = true,
            rideNotifications = p[RIDE_NOTIFICATIONS] ?: true,
            connectionAlerts = p[CONNECTION_ALERTS] ?: true,
            alertsCluster = p[ALERTS_CLUSTER] ?: true,
            alertsImportant = p[ALERTS_IMPORTANT] ?: true,
            alertsMedium = p[ALERTS_MEDIUM] ?: false,
            alertsNameOnly = p[ALERTS_NAME_ONLY] ?: false,
            themeMode = p[THEME_MODE].toEnum(ThemeMode.SYSTEM),
            accentColor = p[ACCENT].toEnum(AccentColor.BLUE),
            fontSize = p[FONT_SIZE].toEnum(FontSize.MEDIUM),
            colourMix = ColourMix.fromName(p[COLOUR_MIX]),
            buttonStyle = ButtonStyle.fromName(p[BUTTON_STYLE]),
            backgroundStyle = BackgroundStyle.fromName(p[BACKGROUND_STYLE]),
        )
    }

    /** Read on their own by the theme, which exists before any screen does. */
    val themeMode: Flow<ThemeMode> =
        context.appSettings.data.map { it[THEME_MODE].toEnum(ThemeMode.SYSTEM) }

    val accentColor: Flow<AccentColor> =
        context.appSettings.data.map { it[ACCENT].toEnum(AccentColor.BLUE) }

    /** null = no mix chosen. */
    val colourMix: Flow<ColourMix?> =
        context.appSettings.data.map { ColourMix.fromName(it[COLOUR_MIX]) }

    val buttonStyle: Flow<ButtonStyle> =
        context.appSettings.data.map { ButtonStyle.fromName(it[BUTTON_STYLE]) }

    val backgroundStyle: Flow<BackgroundStyle> =
        context.appSettings.data.map { BackgroundStyle.fromName(it[BACKGROUND_STYLE]) }

    val fontSize: Flow<FontSize> =
        context.appSettings.data.map { it[FONT_SIZE].toEnum(FontSize.MEDIUM) }

    suspend fun setDistanceUnit(unit: DistanceUnit) = put(UNITS, unit.name)

    suspend fun setSurfaceStyle(style: SurfaceStyle) = put(SURFACE_STYLE, style.name)

    /** Ride recorder for navigation testing; default from BuildConfig.RIDE_LOG_DEFAULT_ON (off in smooth/release); a stored choice always wins. */
    val rideRecorder: Flow<Boolean> = context.appSettings.data.map { it[RIDE_RECORDER] ?: com.eshwar.rideconnectx.BuildConfig.RIDE_LOG_DEFAULT_ON }

    suspend fun setRideRecorder(on: Boolean) = put(RIDE_RECORDER, on)

    /** Live metre countdown from the phone's GPS between Maps updates; on by default. */
    val gpsCountdown: Flow<Boolean> = context.appSettings.data.map { it[GPS_COUNTDOWN] ?: true }

    suspend fun setGpsCountdown(on: Boolean) = put(GPS_COUNTDOWN, on)

    /** The slider's value while it is being dragged; null when not dragging. */
    private val draggingIntensity = MutableStateFlow<Float?>(null)

    /**
     * Glass strength, 0 (barely there) to 1 (strongest). Follows the slider
     * live while it is dragged, so the whole app changes under the finger.
     */
    val glassIntensity: Flow<Float> = combine(
        context.appSettings.data.map { (it[GLASS_INTENSITY] ?: DEFAULT_GLASS_INTENSITY).coerceIn(0f, 1f) },
        draggingIntensity,
    ) { saved, dragging -> dragging ?: saved }

    /** Live preview while dragging; not written to disk. */
    fun previewGlassIntensity(value: Float) { draggingIntensity.value = value.coerceIn(0f, 1f) }

    suspend fun setGlassIntensity(value: Float) {
        put(GLASS_INTENSITY, value.coerceIn(0f, 1f))
        draggingIntensity.value = null
    }
    /**
     * Per-app notification switch (package -> on/off). Missing = the tier default
     * (Important ON, Medium OFF), which the caller applies. Package names only.
     */
    val appSwitches: Flow<Map<String, Boolean>> = context.appSettings.data.map { p ->
        p.asMap().entries.mapNotNull { (k, v) ->
            if (k.name.startsWith(APP_SWITCH_PREFIX) && v is Boolean) k.name.removePrefix(APP_SWITCH_PREFIX) to v else null
        }.toMap()
    }

    suspend fun setAppSwitch(pkg: String, on: Boolean) = put(booleanPreferencesKey(APP_SWITCH_PREFIX + pkg), on)

    /** Important/Medium packages that have posted. Package names only; ignored apps are never added. */
    val seenPackages: Flow<Set<String>> = context.appSettings.data.map { it[SEEN_PACKAGES].orEmpty() }

    suspend fun addSeenPackage(pkg: String) {
        context.appSettings.edit { it[SEEN_PACKAGES] = it[SEEN_PACKAGES].orEmpty() + pkg }
    }

    suspend fun setAutoStartNavigation(on: Boolean) = put(AUTO_START_NAV, on)

    suspend fun setAutoConnect(on: Boolean) = put(AUTO_CONNECT, on)

    suspend fun setRideNotifications(on: Boolean) = put(RIDE_NOTIFICATIONS, on)
    suspend fun setConnectionAlerts(on: Boolean) = put(CONNECTION_ALERTS, on)

    val alertsCluster: Flow<Boolean> = context.appSettings.data.map { it[ALERTS_CLUSTER] ?: true }
    val alertsImportant: Flow<Boolean> = context.appSettings.data.map { it[ALERTS_IMPORTANT] ?: true }
    val alertsMedium: Flow<Boolean> = context.appSettings.data.map { it[ALERTS_MEDIUM] ?: false }
    val alertsNameOnly: Flow<Boolean> = context.appSettings.data.map { it[ALERTS_NAME_ONLY] ?: false }
    suspend fun setAlertsCluster(on: Boolean) = put(ALERTS_CLUSTER, on)
    suspend fun setAlertsImportant(on: Boolean) = put(ALERTS_IMPORTANT, on)
    suspend fun setAlertsMedium(on: Boolean) = put(ALERTS_MEDIUM, on)
    suspend fun setAlertsNameOnly(on: Boolean) = put(ALERTS_NAME_ONLY, on)

    suspend fun setThemeMode(mode: ThemeMode) = put(THEME_MODE, mode.name)
    suspend fun setAccentColor(accent: AccentColor) = put(ACCENT, accent.name)
    /** Pass null to clear the mix (back to accent only). */
    suspend fun setColourMix(mix: ColourMix?) {
        context.appSettings.edit { if (mix == null) it.remove(COLOUR_MIX) else it[COLOUR_MIX] = mix.name }
    }
    suspend fun setButtonStyle(style: ButtonStyle) = put(BUTTON_STYLE, style.name)
    suspend fun setBackgroundStyle(style: BackgroundStyle) = put(BACKGROUND_STYLE, style.name)
    suspend fun setFontSize(size: FontSize) = put(FONT_SIZE, size.name)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.appSettings.edit { it[key] = value }
    }
}

const val DEFAULT_GLASS_INTENSITY = 0.6f

/** Stored name → enum, falling back to [fallback] on anything unrecognised. */
private inline fun <reified E : Enum<E>> String?.toEnum(fallback: E): E =
    this?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: fallback
