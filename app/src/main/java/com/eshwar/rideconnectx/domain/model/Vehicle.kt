package com.eshwar.rideconnectx.domain.model

import androidx.compose.ui.graphics.Color

/** Vehicle Selection is split into these two sections. */
enum class VehicleCategory(val label: String) {
    SCOOTER("Scooters"),
    MOTORCYCLE("Motorcycles"),
}

/**
 * A factory paint option.
 *
 * Names are the maker official ones. [primary] is the main body colour and
 * [secondary] is set only for dual-tone schemes, where the swatch is split.
 *
 * The hex values are close visual approximations of each named shade — the maker
 * does not publish sRGB values — so they are for UI swatches, not colour matching.
 */
data class VehicleColor(
    val id: String,
    val name: String,
    val primary: Color,
    val secondary: Color? = null,
) {
    val isDualTone: Boolean get() = secondary != null
}

/**
 * A two-wheeler that ships with the Bluetooth digital console and
 * supports the cluster protocol.
 */
data class Vehicle(
    val id: String,
    val name: String,
    /** Trim / console line, e.g. "Ride Connect TFT Edition". */
    val trim: String,
    val category: VehicleCategory,
    val displacementCc: Int,
    /**
     * Fuel tank capacity in litres, from the maker's published specs.
     *
     * Needed because the cluster transmits no fuel economy and no litres - only
     * a five-segment bar. With a tank size, a Trip B reset at each fill and the
     * litres the rider actually put in, mileage becomes arithmetic
     * (km / litres) instead of the inference the fuel-bar heuristic has to do.
     *
     * Also gives the range estimate something real to work from.
     */
    val fuelCapacityLitres: Float,
    /** Optional badge shown on the card, e.g. "Popular". */
    val tag: String = "",
    /** Accent colour used for the card's artwork and highlights. */
    val accent: Color,
    val colors: List<VehicleColor>,
    /** Advertised-name codes (characters 1..4, e.g. "AS21"; prefixes allowed, e.g. "CS0"). */
    val nameCodes: List<String> = emptyList(),
) {
    /** Trim without a trailing " · 155" style number, so the cc figure is not shown twice. */
    val subtitle: String get() =
        if (displacementCc > 0) "${trim.replace(TRAILING_NUMBER, "")} · ${displacementCc}cc" else trim
}

private val TRAILING_NUMBER = Regex(" · \\d+$")

/**
 * The supported vehicle line-up.
 *
 * Sourced from the maker's 2026 range — the models whose digital
 * console carries the cluster protocol (turn-by-turn navigation, call/SMS/WhatsApp
 * alerts, phone battery and network on the cluster).
 */
object VehicleCatalog {

    // ── Shared shades ──────────────────────────────────────────────
    private val pearlGraceWhite = Color(0xFFF2EFE9)
    private val glassSparkleBlack = Color(0xFF16161C)
    private val matBlackNo2 = Color(0xFF1F2124)
    private val pearlGlacierWhite = Color(0xFFF5F3EF)
    private val platinumSilverNo2 = Color(0xFFA8ADB2)

    val scooters = listOf(
        Vehicle(
            id = "access_125",
            name = "Access 125",
            trim = "Ride Connect TFT Edition",
            category = VehicleCategory.SCOOTER,
            displacementCc = 124,
            fuelCapacityLitres = 5.3f,
            tag = "Popular",
            accent = Color(0xFF2B7FFF),
            nameCodes = listOf("AS01", "AS02"),
            colors = listOf(
                VehicleColor("acc_aqua", "Pearl Mat Aqua Silver", Color(0xFF9FB4B8)),
                VehicleColor("acc_ice", "Solid Ice Green", Color(0xFFBFE0D2)),
                VehicleColor("acc_beige", "Pearl Shiny Beige", Color(0xFFD9C9AE)),
                VehicleColor("acc_white", "Pearl Grace White", pearlGraceWhite),
                VehicleColor("acc_black", "Metallic Mat Black No. 2", matBlackNo2),
                VehicleColor("acc_stellar", "Metallic Mat Stellar Blue", Color(0xFF2C4C7C)),
                VehicleColor(
                    "acc_sonoma", "Metallic Sonoma Red / Pearl Mirage White",
                    Color(0xFFA3242C), Color(0xFFF0EDE5),
                ),
            ),
        ),
        Vehicle(
            id = "burgman_street_125ex",
            name = "Burgman Street 125EX",
            trim = "Ride Connect TFT Edition",
            category = VehicleCategory.SCOOTER,
            displacementCc = 124,
            fuelCapacityLitres = 5.5f,
            tag = "Premium",
            accent = Color(0xFF00D9A3),
            nameCodes = listOf("BS31", "BS32"),
            colors = listOf(
                VehicleColor("bur_navy", "Metallic Mat Navy Blue", Color(0xFF2A3A54)),
                VehicleColor("bur_white", "Pearl Grace White", pearlGraceWhite),
                VehicleColor("bur_black", "Glossy Sparkle Black", glassSparkleBlack),
            ),
        ),
        Vehicle(
            id = "access",
            name = "Access",
            trim = "Ride Connect",
            category = VehicleCategory.SCOOTER,
            displacementCc = 124,
            fuelCapacityLitres = 5.3f,
            accent = Color(0xFF2B7FFF),
            nameCodes = listOf("AS21"),
            // Placeholder shades: official paint list not collected yet.
            colors = listOf(
                VehicleColor("acs_white", "Pearl Grace White", pearlGraceWhite),
                VehicleColor("acs_black", "Glossy Sparkle Black", glassSparkleBlack),
            ),
        ),
        Vehicle(
            id = "access_tft",
            name = "Access-TFT Edition",
            trim = "Ride Connect TFT Edition",
            category = VehicleCategory.SCOOTER,
            displacementCc = 124,
            fuelCapacityLitres = 5.3f,
            accent = Color(0xFF2B7FFF),
            nameCodes = listOf("CS0"),
            // Placeholder shades: official paint list not collected yet.
            colors = listOf(
                VehicleColor("acstft_white", "Pearl Grace White", pearlGraceWhite),
                VehicleColor("acstft_black", "Glossy Sparkle Black", glassSparkleBlack),
            ),
        ),
        Vehicle(
            id = "e_access",
            name = "e-ACCESS",
            trim = "Ride Connect Electric",
            category = VehicleCategory.SCOOTER,
            displacementCc = 0,
            fuelCapacityLitres = 0.0f,
            accent = Color(0xFF00D9A3),
            nameCodes = listOf("CE"),
            // Placeholder shades: official paint list not collected yet.
            colors = listOf(
                VehicleColor("eacs_white", "Pearl Grace White", pearlGraceWhite),
                VehicleColor("eacs_black", "Glossy Sparkle Black", glassSparkleBlack),
            ),
        ),
        Vehicle(
            id = "burgman_street",
            name = "Burgman Street",
            trim = "Ride Connect",
            category = VehicleCategory.SCOOTER,
            displacementCc = 124,
            fuelCapacityLitres = 5.5f,
            accent = Color(0xFF00D9A3),
            nameCodes = listOf("AS11", "AS12", "BS11", "BS12", "BS51"),
            // Placeholder shades: official paint list not collected yet.
            colors = listOf(
                VehicleColor("bst_white", "Pearl Grace White", pearlGraceWhite),
                VehicleColor("bst_black", "Glossy Sparkle Black", glassSparkleBlack),
            ),
        ),
        Vehicle(
            id = "burgman_street_tft",
            name = "Burgman Street-TFT Edition",
            trim = "Ride Connect TFT Edition",
            category = VehicleCategory.SCOOTER,
            displacementCc = 124,
            fuelCapacityLitres = 5.5f,
            accent = Color(0xFF00D9A3),
            nameCodes = listOf("CS1"),
            // Placeholder shades: official paint list not collected yet.
            colors = listOf(
                VehicleColor("bsttft_white", "Pearl Grace White", pearlGraceWhite),
                VehicleColor("bsttft_black", "Glossy Sparkle Black", glassSparkleBlack),
            ),
        ),
        Vehicle(
            id = "avenis_125",
            name = "Avenis 125",
            trim = "Ride Connect Edition",
            category = VehicleCategory.SCOOTER,
            displacementCc = 124,
            fuelCapacityLitres = 5.2f,
            tag = "Sporty",
            accent = Color(0xFFFFB547),
            nameCodes = listOf("BS21", "BS22"),
            colors = listOf(
                VehicleColor(
                    "ave_yellow", "Champion Yellow No. 2 / Glossy Sparkle Black",
                    Color(0xFFF4C518), glassSparkleBlack,
                ),
                VehicleColor(
                    "ave_red", "Glossy Sparkle Black / Pearl Mira Red",
                    glassSparkleBlack, Color(0xFFC0392B),
                ),
                VehicleColor(
                    "ave_white", "Glossy Sparkle Black / Pearl Glacier White",
                    glassSparkleBlack, pearlGlacierWhite,
                ),
                VehicleColor("ave_black", "Glossy Sparkle Black", glassSparkleBlack),
            ),
        ),
    )

    val motorcycles = listOf(
        Vehicle(
            id = "gixxer_sf_250",
            name = "Gixxer SF 250",
            trim = "Ride Connect",
            category = VehicleCategory.MOTORCYCLE,
            displacementCc = 249,
            fuelCapacityLitres = 12.0f,
            tag = "Sport",
            accent = Color(0xFFFF5A6A),
            nameCodes = listOf("BM21", "BM22"),
            colors = listOf(
                VehicleColor("gsf250_black", "Glass Sparkle Black", glassSparkleBlack),
                VehicleColor(
                    "gsf250_white", "Pearl Glacier White / Metallic Mat Platinum Silver No. 2",
                    pearlGlacierWhite, platinumSilverNo2,
                ),
            ),
        ),
        Vehicle(
            id = "gixxer_250",
            name = "Gixxer 250",
            trim = "Ride Connect",
            category = VehicleCategory.MOTORCYCLE,
            displacementCc = 249,
            fuelCapacityLitres = 12.0f,
            accent = Color(0xFF00D4FF),
            nameCodes = listOf("BM21", "BM22"),
            colors = listOf(
                VehicleColor(
                    "g250_white", "Pearl Glacier White / Metallic Mat Platinum Silver No. 2",
                    pearlGlacierWhite, platinumSilverNo2,
                ),
                VehicleColor(
                    "g250_triton", "Metallic Triton Blue / Glass Sparkle Black",
                    Color(0xFF1A4A8A), glassSparkleBlack,
                ),
                VehicleColor("g250_black", "Glass Sparkle Black", glassSparkleBlack),
            ),
        ),
        Vehicle(
            id = "gixxer_sf_155",
            name = "Gixxer SF",
            trim = "Ride Connect · 155",
            category = VehicleCategory.MOTORCYCLE,
            displacementCc = 155,
            fuelCapacityLitres = 12.0f,
            tag = "Faired",
            accent = Color(0xFF2B7FFF),
            nameCodes = listOf("BM11"),
            colors = listOf(
                VehicleColor(
                    "gsf155_white", "Pearl Glacier White / Metallic Mat Platinum Silver No. 2",
                    pearlGlacierWhite, platinumSilverNo2,
                ),
                VehicleColor(
                    "gsf155_blue", "Metallic Mat Stellar Blue / Glass Sparkle Black",
                    Color(0xFF2C4C7C), glassSparkleBlack,
                ),
                VehicleColor("gsf155_black", "Glass Sparkle Black", glassSparkleBlack),
            ),
        ),
        Vehicle(
            id = "gixxer_155",
            name = "Gixxer",
            trim = "Ride Connect · 155",
            category = VehicleCategory.MOTORCYCLE,
            displacementCc = 155,
            fuelCapacityLitres = 12.0f,
            accent = Color(0xFF00D9A3),
            nameCodes = listOf("BM11"),
            colors = listOf(
                VehicleColor(
                    "g155_blue", "Metallic Mat Stellar Blue / Glass Sparkle Black",
                    Color(0xFF2C4C7C), glassSparkleBlack,
                ),
                VehicleColor("g155_black", "Glass Sparkle Black", glassSparkleBlack),
                VehicleColor(
                    "g155_red", "Metallic Sonoma Red / Glass Sparkle Black",
                    Color(0xFFA3242C), glassSparkleBlack,
                ),
            ),
        ),
        Vehicle(
            id = "vstrom_sx_250",
            name = "V-Strom SX",
            trim = "Ride Connect · 250",
            category = VehicleCategory.MOTORCYCLE,
            displacementCc = 249,
            fuelCapacityLitres = 12.0f,
            tag = "Adventure",
            accent = Color(0xFFFFB547),
            nameCodes = listOf("BM01"),
            colors = listOf(
                VehicleColor("vsx_yellow", "Metallic Mat Steel Green", Color(0xFF5A6650)),
                VehicleColor("vsx_black", "Glass Sparkle Black", glassSparkleBlack),
                VehicleColor("vsx_grey", "Metallic Mat Black No. 2", matBlackNo2),
            ),
        ),
    )

    val all: List<Vehicle> = scooters + motorcycles

    fun byId(id: String?): Vehicle? = all.firstOrNull { it.id == id }

    fun colorById(vehicleId: String?, colorId: String?): VehicleColor? =
        byId(vehicleId)?.colors?.firstOrNull { it.id == colorId }

    fun byCategory(category: VehicleCategory) =
        if (category == VehicleCategory.SCOOTER) scooters else motorcycles

    /**
     * Model for an advertised name (`SAS210217219` -> `AS21`), or null when unknown.
     * Same list as the picker. Longest matching code wins.
     */
    fun byDeviceName(name: String?): Vehicle? {
        val code = name?.trim()?.uppercase()?.drop(1)?.take(4)?.takeIf { it.length >= 2 } ?: return null
        return all.flatMap { v -> v.nameCodes.map { it to v } }
            .filter { code.startsWith(it.first) }
            .maxByOrNull { it.first.length }?.second
    }

    /** Unknown names fall back to the Access 125, like the protocol engine does. */
    fun forDeviceName(name: String?): Vehicle = byDeviceName(name) ?: all.first { it.id == "access_125" }

    /** Case-insensitive search across model name and trim. */
    fun search(query: String): List<Vehicle> {
        val q = query.trim()
        if (q.isEmpty()) return all
        return all.filter {
            it.name.contains(q, ignoreCase = true) || it.trim.contains(q, ignoreCase = true)
        }
    }
}
