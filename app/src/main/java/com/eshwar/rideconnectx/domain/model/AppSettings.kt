package com.eshwar.rideconnectx.domain.model

/**
 * Distance units.
 *
 * Display only — the scooter always reports kilometres, every value is *stored*
 * in kilometres, and only the presentation changes. Converting on the way into
 * storage would corrupt the data the moment the rider changed their mind.
 */
enum class DistanceUnit(val label: String, val short: String) {
    KM("Kilometres", "km"),
    MILES("Miles", "mi");

    /** Kilometres → whatever this unit counts in. */
    fun fromKm(km: Int): Int = when (this) {
        KM -> km
        MILES -> Math.round(km * MILES_PER_KM).toInt()
    }

    /** Speed label for this unit. */
    val speed: String get() = if (this == KM) "km/h" else "mph"

    /** Same, for readings with decimals (trip meters, ride distances). */
    fun fromKm(km: Float): Float = if (this == KM) km else km * MILES_PER_KM

    /** "3,000 km" / "1,864 mi" — grouped, because these run to five digits. */
    fun format(km: Int): String = "%,d %s".format(fromKm(km), short)

    private companion object {
        /** Exact by definition: 1 mile = 1.609344 km. */
        const val MILES_PER_KM = 1f / 1.609344f
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Theme"),
    DARK("Dark Theme"),
}

/**
 * The accent the whole app is tinted with.
 *
 * Stored as an ARGB value per theme rather than a single colour: a hue that
 * reads well on the dark navy background is often too pale on the light one, so
 * each accent carries both. Blue is the RideConnectX brand and the default.
 */
enum class AccentColor(
    val label: String,
    val darkArgb: Long,
    val lightArgb: Long,
) {
    BLUE("Blue", 0xFF2B7FFF, 0xFF2B7FFF),
    CYAN("Cyan", 0xFF00C2E0, 0xFF0095BB),
    MINT("Mint", 0xFF00D9A3, 0xFF009E74),
    VIOLET("Violet", 0xFF9B6BFF, 0xFF7A45E6),
    MAGENTA("Magenta", 0xFFFF5FA8, 0xFFD62A7C),
    AMBER("Amber", 0xFFFFB547, 0xFFC87D00),
    CORAL("Coral", 0xFFFF7A5A, 0xFFD9482A),
    LIME("Lime", 0xFF9BE564, 0xFF5C9A1F),
}

/**
 * Text scale. Applied by multiplying the density's font scale, so it stacks
 * with the rider's Android accessibility setting rather than overriding it.
 */
/**
 * Flat cards or frosted glass.
 *
 * Offered as a choice because the rider asked for one: glass is striking but
 * not to everyone's taste, and on Android 11 and below it cannot really blur
 * anyway. Flat stays the default so nothing changes for anyone who never opens
 * this setting.
 */
enum class SurfaceStyle(val label: String) {
    FLAT("Flat"),
    GLASS("Glass"),
}

/**
 * Colour mix for buttons and accent: 2 to 4 raw colours, softened by the UI.
 * No mix chosen (null in [AppSettings.colourMix]) = accent colour only, as before.
 */
enum class ColourMix(val label: String, val colours: List<Long>) {
    AURORA("Aurora", listOf(0xFF00D9A3, 0xFF00C2E0, 0xFF2B7FFF)),
    NEON("Neon", listOf(0xFF00C2E0, 0xFF9B6BFF, 0xFFFF5FA8)),
    SUNSET("Sunset", listOf(0xFFFFB547, 0xFFFF7A5A, 0xFFFF5FA8)),
    OCEAN("Ocean", listOf(0xFF2B7FFF, 0xFF00C2E0, 0xFF9BE564)),
    ROYAL("Royal", listOf(0xFF9B6BFF, 0xFF2B7FFF, 0xFF00C2E0)),
    CANDY("Candy", listOf(0xFFFF5FA8, 0xFF9B6BFF, 0xFF2B7FFF)),
    FOREST("Forest", listOf(0xFF9BE564, 0xFF00D9A3, 0xFF00C2E0)),
    EMBER("Ember", listOf(0xFFFFB547, 0xFFFF7A5A)),
    FIRE_AND_ICE("Fire & Ice", listOf(0xFFFF7A5A, 0xFFFF5FA8, 0xFF9B6BFF, 0xFF00C2E0)),
    TROPIC("Tropic", listOf(0xFF9BE564, 0xFF00C2E0, 0xFFFF5FA8)),
    GOLD_MINT("Gold Mint", listOf(0xFFFFB547, 0xFF9BE564, 0xFF00D9A3)),
    ICE("Ice", listOf(0xFFE4ECFF, 0xFF00C2E0, 0xFF2B7FFF));

    companion object {
        /** Stored name to mix; null (none chosen) on missing or unknown. */
        fun fromName(name: String?): ColourMix? = entries.firstOrNull { it.name == name }
    }
}

/** App background: real pictures (PHOTO, today's look) or soft moving lights (GLOW). */
enum class BackgroundStyle {
    PHOTO, GLOW;

    companion object {
        fun fromName(name: String?): BackgroundStyle = entries.firstOrNull { it.name == name } ?: PHOTO
    }
}

/** How filled buttons look. SOLID keeps today's look. */
enum class ButtonStyle {
    SOLID, GLASS, OUTLINE;

    companion object {
        fun fromName(name: String?): ButtonStyle = entries.firstOrNull { it.name == name } ?: SOLID
    }
}

enum class FontSize(val label: String, val scale: Float) {
    SMALL("Small", 0.9f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.15f),
}

/**
 * The whole of Screen 20 and Screen 22 in one value.
 *
 * Deliberately absent: **service reminders**, which live in
 * `ServicePreferencesStore` because Screen 18 owns them — two switches writing
 * two different keys would let the Settings row and the Service row disagree.
 *
 * Also absent, and removed on the rider's instruction after reviewing the app:
 * the whole Navigation section (voice guidance, traffic, tolls, highways, map
 * type) — Google Maps owns every one of those and setting them here did
 * nothing; the "default navigation app" row, since Maps is the only app whose
 * notification the relay can read; marketing notifications, which the app does
 * not send; and "background connection", which is a permission rather than a
 * preference and now lives on the permissions screen.
 */
data class AppSettings(
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val autoStartNavigation: Boolean = true,

    /**
     * One switch, not two. "Auto connect" and "auto reconnect" described the
     * same behaviour to the rider and there was no case where wanting one and
     * not the other made sense.
     */
    val autoConnect: Boolean = true,

    val rideNotifications: Boolean = true,
    val connectionAlerts: Boolean = true,

    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.BLUE,
    val fontSize: FontSize = FontSize.MEDIUM,
    val surfaceStyle: SurfaceStyle = SurfaceStyle.FLAT,
    /** null = no mix chosen: accent colour only. */
    val colourMix: ColourMix? = null,
    val buttonStyle: ButtonStyle = ButtonStyle.SOLID,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.PHOTO,
)
