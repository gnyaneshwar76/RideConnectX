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

    companion object {
        /** Exact by definition: 1 mile = 1.609344 km. */
        private const val MILES_PER_KM = 1f / 1.609344f

        /** Used until the rider chooses: miles in US, GB, Liberia, Myanmar; km elsewhere. */
        fun defaultForRegion(country: String?): DistanceUnit =
            if (country?.uppercase() in setOf("US", "GB", "LR", "MM")) MILES else KM
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Theme"),
    DARK("Dark Theme"),
}

/**
 * The accent the whole app is tinted with: one of three calm palettes, chosen by the rider.
 * Saved by [name]. Any other saved value (the old Cyan, Magenta, Lime...) reads as [DEFAULT].
 */
enum class AccentColor(val label: String, val argb: Long) {
    CALM_BLUE("Calm blue", 0xFF3B6FD4),
    SOFT_TEAL("Soft teal", 0xFF2A8C7E),
    SOFT_INDIGO("Soft indigo", 0xFF5B5FC7),
    ;

    companion object {
        val DEFAULT = SOFT_TEAL

        /** The data layer still says AccentColor.BLUE as its fallback; it now means the default. */
        val BLUE = DEFAULT

        /** Saved name to palette; the default on missing or old values. */
        fun fromName(name: String?): AccentColor = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
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
    val accentColor: AccentColor = AccentColor.DEFAULT,
    val fontSize: FontSize = FontSize.MEDIUM,
    val surfaceStyle: SurfaceStyle = SurfaceStyle.FLAT,
    /** null = no mix chosen: accent colour only. */
    val colourMix: ColourMix? = null,
    val buttonStyle: ButtonStyle = ButtonStyle.SOLID,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.PHOTO,
)
