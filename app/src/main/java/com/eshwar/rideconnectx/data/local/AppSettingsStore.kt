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
            distanceUnit = p[UNITS].toEnum(DistanceUnit.KM),
            surfaceStyle = p[SURFACE_STYLE].toEnum(SurfaceStyle.FLAT),
            autoStartNavigation = p[AUTO_START_NAV] ?: true,
            autoConnect = p[AUTO_CONNECT] ?: true,
            rideNotifications = p[RIDE_NOTIFICATIONS] ?: true,
            connectionAlerts = p[CONNECTION_ALERTS] ?: true,
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
    suspend fun setAutoStartNavigation(on: Boolean) = put(AUTO_START_NAV, on)

    suspend fun setAutoConnect(on: Boolean) = put(AUTO_CONNECT, on)

    suspend fun setRideNotifications(on: Boolean) = put(RIDE_NOTIFICATIONS, on)
    suspend fun setConnectionAlerts(on: Boolean) = put(CONNECTION_ALERTS, on)

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
