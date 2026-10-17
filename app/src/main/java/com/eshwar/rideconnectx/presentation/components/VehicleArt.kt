package com.eshwar.rideconnectx.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.domain.model.Vehicle
import com.eshwar.rideconnectx.domain.model.VehicleColor
import kotlin.math.min

/**
 * Original line art for each supported model.
 *
 * Drawn from scratch rather than sourced, so there is no third-party imagery in
 * the app. Each silhouette is proportioned to its real counterpart — wheelbase,
 * seat height, fairing and stance — so a Burgman reads as a maxi-scooter and a
 * V-Strom reads as an adventure bike.
 *
 * Everything is drawn in a 200 × 100 space and scaled to fit, so the art is
 * resolution-independent and recolours to the selected paint.
 */
enum class VehicleArtStyle { ClassicScooter, MaxiScooter, SportScooter, NakedBike, FairedBike, AdventureBike }

/** Which silhouette belongs to which model. */
val Vehicle.artStyle: VehicleArtStyle
    get() = when (id) {
        "access_125" -> VehicleArtStyle.ClassicScooter
        "burgman_street_125ex" -> VehicleArtStyle.MaxiScooter
        "avenis_125" -> VehicleArtStyle.SportScooter
        "gixxer_250", "gixxer_155" -> VehicleArtStyle.NakedBike
        "gixxer_sf_250", "gixxer_sf_155" -> VehicleArtStyle.FairedBike
        "vstrom_sx_250" -> VehicleArtStyle.AdventureBike
        else -> VehicleArtStyle.ClassicScooter
    }

private const val ART_W = 200f
private const val ART_H = 100f

private class Art(canvas: Size) {
    val s = min(canvas.width / ART_W, canvas.height / ART_H)
    val dx = (canvas.width - ART_W * s) / 2f
    val dy = (canvas.height - ART_H * s) / 2f
    fun x(v: Float) = dx + v * s
    fun y(v: Float) = dy + v * s
    fun len(v: Float) = v * s
    fun pt(px: Float, py: Float) = Offset(x(px), y(py))
}

/**
 * The photograph for each model, or null where there is not one yet.
 *
 * Studio side profiles, background removed and a contact shadow added — see
 * `tools/images/prepare_vehicles.py`. They are `drawable-nodpi` because they
 * are already sized for display; letting Android rescale them per density
 * would cost memory and gain nothing.
 */
private val Vehicle.photoRes: Int?
    get() = when (id) {
        "access_125" -> R.drawable.img_vehicle_access_125
        "burgman_street_125ex" -> R.drawable.img_vehicle_burgman_street
        "avenis_125" -> R.drawable.img_vehicle_avenis_125
        "gixxer_250" -> R.drawable.img_vehicle_gixxer_250
        "gixxer_155" -> R.drawable.img_vehicle_gixxer_155
        "gixxer_sf_250" -> R.drawable.img_vehicle_gixxer_sf_250
        "gixxer_sf_155" -> R.drawable.img_vehicle_gixxer_sf_155
        "vstrom_sx_250" -> R.drawable.img_vehicle_vstrom_sx
        else -> null
    }

/**
 * The photograph for a specific paint, if one has been generated.
 *
 * Looked up by name — `img_vehicle_<vehicleId>_<colorId>` — so adding a
 * colourway is dropping a WebP into `drawable-nodpi` with the right file name
 * and nothing else. Missing variants fall back to the base photograph, so the
 * set can be filled in one vehicle at a time without the app ever showing a
 * gap. See `docs/Vehicle-Colourway-Prompts.md` for the generation prompts.
 *
 * `getIdentifier` is the only way to resolve a name assembled at runtime. It is
 * remembered per (vehicle, colour) so the lookup happens once per selection,
 * not once per frame.
 */
@Composable
private fun colourwayPhoto(vehicle: Vehicle, colorway: VehicleColor?): Int? {
    val base = vehicle.photoRes
    if (base == null || colorway == null) return base

    val context = LocalContext.current
    return remember(vehicle.id, colorway.id) {
        val name = "img_vehicle_${vehicle.id}_${colorway.id}"
        val found = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (found != 0) found else base
    }
}

/**
 * Shows [vehicle] — the photograph where one exists, the line art otherwise.
 *
 * **A photograph carries its own paint.** Where a per-colour photograph exists
 * it is used ([colourwayPhoto]); where it does not, the base photograph is
 * shown untinted. Running a colour filter over a photo of a real vehicle tints
 * the tyres, the chrome and the shadow along with the bodywork and looks worse
 * than showing it honestly, so the chosen paint drives everything *around* the
 * vehicle instead — the stage light behind it, the card tint, the borders.
 *
 * Models without a photo fall back to the original line art, which does
 * recolour. Nothing breaks while the set is incomplete.
 *
 * @param body the selected paint colour; used by the line art only.
 * @param colorway the selected paint, used to find a matching photograph.
 */
@Composable
fun VehicleArtwork(
    vehicle: Vehicle,
    body: Color,
    outline: Color,
    modifier: Modifier = Modifier,
    colorway: VehicleColor? = null,
) {
    val photo = colourwayPhoto(vehicle, colorway)
    if (photo != null) {
        Image(
            painter = painterResource(photo),
            contentDescription = vehicle.name,
            // Fit, never crop: a cropped vehicle looks like a mistake, and
            // these are wider than most of the boxes they sit in.
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
        return
    }

    Canvas(modifier) {
        val a = Art(size)
        when (vehicle.artStyle) {
            VehicleArtStyle.ClassicScooter -> classicScooter(a, body, outline)
            VehicleArtStyle.MaxiScooter -> maxiScooter(a, body, outline)
            VehicleArtStyle.SportScooter -> sportScooter(a, body, outline)
            VehicleArtStyle.NakedBike -> nakedBike(a, body, outline)
            VehicleArtStyle.FairedBike -> fairedBike(a, body, outline)
            VehicleArtStyle.AdventureBike -> adventureBike(a, body, outline)
        }
    }
}

// ── Shared parts ───────────────────────────────────────────────────

private fun DrawScope.wheel(a: Art, cx: Float, cy: Float, r: Float, outline: Color, spokes: Boolean = false) {
    drawCircle(outline, a.len(r), a.pt(cx, cy), style = Stroke(a.len(2.4f)))
    drawCircle(outline.copy(alpha = 0.35f), a.len(r * 0.34f), a.pt(cx, cy))
    if (spokes) {
        // Wire-spoke hint for the adventure model.
        listOf(0f, 45f, 90f, 135f).forEach { deg ->
            val rad = Math.toRadians(deg.toDouble())
            val ox = (Math.cos(rad) * r * 0.8f).toFloat()
            val oy = (Math.sin(rad) * r * 0.8f).toFloat()
            drawLine(
                outline.copy(alpha = 0.4f),
                a.pt(cx - ox, cy - oy), a.pt(cx + ox, cy + oy),
                strokeWidth = a.len(1f),
            )
        }
    }
}

private fun DrawScope.panel(a: Art, build: Path.() -> Unit, body: Color, outline: Color, width: Float = 2.4f) {
    val p = Path().apply(build)
    drawPath(p, body.copy(alpha = 0.55f))
    drawPath(p, outline, style = Stroke(a.len(width), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.stroke(a: Art, build: Path.() -> Unit, outline: Color, width: Float = 2.4f) {
    drawPath(
        Path().apply(build), outline,
        style = Stroke(a.len(width), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

// ── Access 125 — classic step-through scooter ──────────────────────

private fun DrawScope.classicScooter(a: Art, body: Color, outline: Color) {
    wheel(a, 42f, 76f, 17f, outline)
    wheel(a, 158f, 76f, 17f, outline)

    // Leg shield + flat floorboard + rear body: the step-through signature.
    panel(a, {
        moveTo(a.x(34f), a.y(62f))
        lineTo(a.x(36f), a.y(30f)); quadraticBezierTo(a.x(37f), a.y(24f), a.x(45f), a.y(24f))
        lineTo(a.x(56f), a.y(24f)); lineTo(a.x(60f), a.y(62f))
        lineTo(a.x(112f), a.y(62f))
        lineTo(a.x(112f), a.y(50f)); quadraticBezierTo(a.x(116f), a.y(38f), a.x(132f), a.y(38f))
        lineTo(a.x(170f), a.y(38f)); quadraticBezierTo(a.x(182f), a.y(39f), a.x(182f), a.y(50f))
        lineTo(a.x(182f), a.y(64f)); lineTo(a.x(120f), a.y(64f))
        lineTo(a.x(60f), a.y(70f)); lineTo(a.x(34f), a.y(62f))
        close()
    }, body, outline)

    // Seat
    panel(a, {
        moveTo(a.x(116f), a.y(38f)); lineTo(a.x(172f), a.y(38f))
        quadraticBezierTo(a.x(178f), a.y(32f), a.x(168f), a.y(31f))
        lineTo(a.x(126f), a.y(31f)); quadraticBezierTo(a.x(116f), a.y(32f), a.x(116f), a.y(38f))
        close()
    }, outline, outline, 1.6f)

    // Bars, mirror stalk, headlight
    stroke(a, { moveTo(a.x(44f), a.y(24f)); lineTo(a.x(40f), a.y(12f)) }, outline)
    stroke(a, { moveTo(a.x(30f), a.y(10f)); lineTo(a.x(54f), a.y(10f)) }, outline, 2f)
    drawCircle(outline, a.len(5f), a.pt(38f, 32f))
}

// ── Burgman Street 125EX — maxi-scooter ────────────────────────────

private fun DrawScope.maxiScooter(a: Art, body: Color, outline: Color) {
    wheel(a, 44f, 76f, 16f, outline)
    wheel(a, 158f, 76f, 16f, outline)

    // Taller front mask and screen, longer body — the maxi look.
    panel(a, {
        moveTo(a.x(32f), a.y(60f))
        lineTo(a.x(34f), a.y(26f)); quadraticBezierTo(a.x(35f), a.y(16f), a.x(46f), a.y(16f))
        lineTo(a.x(58f), a.y(18f)); lineTo(a.x(64f), a.y(60f))
        lineTo(a.x(110f), a.y(60f))
        lineTo(a.x(110f), a.y(46f)); quadraticBezierTo(a.x(114f), a.y(34f), a.x(132f), a.y(34f))
        lineTo(a.x(172f), a.y(34f)); quadraticBezierTo(a.x(184f), a.y(35f), a.x(184f), a.y(48f))
        lineTo(a.x(184f), a.y(64f)); lineTo(a.x(120f), a.y(66f))
        lineTo(a.x(64f), a.y(70f)); lineTo(a.x(32f), a.y(60f))
        close()
    }, body, outline)

    // Windscreen
    stroke(a, {
        moveTo(a.x(40f), a.y(16f))
        quadraticBezierTo(a.x(44f), a.y(2f), a.x(58f), a.y(4f))
    }, outline, 2f)

    // Split seat with backrest step
    panel(a, {
        moveTo(a.x(114f), a.y(34f)); lineTo(a.x(176f), a.y(34f))
        quadraticBezierTo(a.x(182f), a.y(26f), a.x(170f), a.y(25f))
        lineTo(a.x(146f), a.y(25f)); lineTo(a.x(140f), a.y(28f))
        lineTo(a.x(124f), a.y(28f)); quadraticBezierTo(a.x(114f), a.y(29f), a.x(114f), a.y(34f))
        close()
    }, outline, outline, 1.6f)

    drawCircle(outline, a.len(5.5f), a.pt(37f, 30f))
}

// ── Avenis 125 — sporty, angular scooter ───────────────────────────

private fun DrawScope.sportScooter(a: Art, body: Color, outline: Color) {
    wheel(a, 42f, 76f, 16f, outline)
    wheel(a, 158f, 76f, 16f, outline)

    // Sharper, wedge-shaped panels and an upswept tail.
    panel(a, {
        moveTo(a.x(30f), a.y(58f))
        lineTo(a.x(36f), a.y(28f)); lineTo(a.x(48f), a.y(20f)); lineTo(a.x(58f), a.y(24f))
        lineTo(a.x(62f), a.y(60f)); lineTo(a.x(110f), a.y(60f))
        lineTo(a.x(112f), a.y(46f)); lineTo(a.x(132f), a.y(34f))
        lineTo(a.x(176f), a.y(30f)); lineTo(a.x(188f), a.y(40f))
        lineTo(a.x(180f), a.y(60f)); lineTo(a.x(118f), a.y(66f))
        lineTo(a.x(62f), a.y(70f)); lineTo(a.x(30f), a.y(58f))
        close()
    }, body, outline)

    // Angular headlight cluster
    stroke(a, {
        moveTo(a.x(32f), a.y(34f)); lineTo(a.x(46f), a.y(28f)); lineTo(a.x(44f), a.y(38f)); close()
    }, outline, 2f)

    stroke(a, { moveTo(a.x(46f), a.y(20f)); lineTo(a.x(42f), a.y(10f)) }, outline)
    stroke(a, { moveTo(a.x(30f), a.y(9f)); lineTo(a.x(56f), a.y(9f)) }, outline, 2f)
}

// ── Gixxer / Gixxer 250 — naked street bike ────────────────────────

private fun DrawScope.nakedBike(a: Art, body: Color, outline: Color) {
    wheel(a, 40f, 74f, 19f, outline)
    wheel(a, 160f, 74f, 19f, outline)

    // Exposed tank and trellis-style frame line: the naked signature.
    panel(a, {
        moveTo(a.x(78f), a.y(46f))
        quadraticBezierTo(a.x(86f), a.y(30f), a.x(108f), a.y(30f))
        lineTo(a.x(124f), a.y(32f)); lineTo(a.x(126f), a.y(44f))
        lineTo(a.x(96f), a.y(52f)); close()
    }, body, outline)

    // Tail unit, kicked up
    panel(a, {
        moveTo(a.x(126f), a.y(38f)); lineTo(a.x(168f), a.y(28f))
        lineTo(a.x(176f), a.y(34f)); lineTo(a.x(132f), a.y(46f)); close()
    }, body, outline, 2f)

    // Frame + swingarm + forks
    stroke(a, { moveTo(a.x(96f), a.y(52f)); lineTo(a.x(126f), a.y(46f)); lineTo(a.x(160f), a.y(74f)) }, outline, 2f)
    stroke(a, { moveTo(a.x(60f), a.y(30f)); lineTo(a.x(40f), a.y(74f)) }, outline, 2.6f)
    stroke(a, { moveTo(a.x(78f), a.y(46f)); lineTo(a.x(62f), a.y(34f)) }, outline, 2f)

    // Engine block
    panel(a, {
        moveTo(a.x(84f), a.y(52f)); lineTo(a.x(116f), a.y(50f))
        lineTo(a.x(120f), a.y(64f)); lineTo(a.x(86f), a.y(66f)); close()
    }, outline, outline, 1.6f)

    // Round headlight + bars
    drawCircle(outline, a.len(7f), a.pt(58f, 28f))
    stroke(a, { moveTo(a.x(48f), a.y(20f)); lineTo(a.x(74f), a.y(20f)) }, outline, 2f)
}

// ── Gixxer SF — fully faired sportbike ─────────────────────────────

private fun DrawScope.fairedBike(a: Art, body: Color, outline: Color) {
    wheel(a, 40f, 74f, 19f, outline)
    wheel(a, 160f, 74f, 19f, outline)

    // Full fairing sweeping from nose to tail — one continuous shell.
    panel(a, {
        moveTo(a.x(48f), a.y(34f))
        quadraticBezierTo(a.x(56f), a.y(20f), a.x(76f), a.y(24f))
        lineTo(a.x(104f), a.y(28f)); lineTo(a.x(126f), a.y(30f))
        lineTo(a.x(168f), a.y(22f)); lineTo(a.x(178f), a.y(28f))
        lineTo(a.x(134f), a.y(42f)); lineTo(a.x(124f), a.y(56f))
        lineTo(a.x(92f), a.y(62f)); lineTo(a.x(72f), a.y(56f))
        lineTo(a.x(52f), a.y(52f)); close()
    }, body, outline)

    // Screen and nose vent
    stroke(a, { moveTo(a.x(52f), a.y(24f)); quadraticBezierTo(a.x(58f), a.y(12f), a.x(72f), a.y(16f)) }, outline, 2f)
    stroke(a, { moveTo(a.x(50f), a.y(38f)); lineTo(a.x(66f), a.y(36f)) }, outline, 1.8f)

    // Swingarm + forks
    stroke(a, { moveTo(a.x(120f), a.y(58f)); lineTo(a.x(160f), a.y(74f)) }, outline, 2.4f)
    stroke(a, { moveTo(a.x(56f), a.y(40f)); lineTo(a.x(40f), a.y(74f)) }, outline, 2.6f)
}

// ── V-Strom SX — adventure tourer ──────────────────────────────────

private fun DrawScope.adventureBike(a: Art, body: Color, outline: Color) {
    // Taller stance, longer travel, wire-spoke look.
    wheel(a, 38f, 72f, 21f, outline, spokes = true)
    wheel(a, 162f, 72f, 21f, outline, spokes = true)

    // Big tank with tall shoulders
    panel(a, {
        moveTo(a.x(76f), a.y(42f))
        quadraticBezierTo(a.x(84f), a.y(22f), a.x(108f), a.y(22f))
        lineTo(a.x(126f), a.y(26f)); lineTo(a.x(128f), a.y(40f))
        lineTo(a.x(94f), a.y(50f)); close()
    }, body, outline)

    // Rally tail
    panel(a, {
        moveTo(a.x(128f), a.y(34f)); lineTo(a.x(172f), a.y(24f))
        lineTo(a.x(180f), a.y(31f)); lineTo(a.x(134f), a.y(44f)); close()
    }, body, outline, 2f)

    // The beak — the V-Strom's defining feature
    stroke(a, {
        moveTo(a.x(58f), a.y(34f))
        quadraticBezierTo(a.x(44f), a.y(38f), a.x(34f), a.y(46f))
    }, outline, 2.6f)

    // Tall screen
    stroke(a, {
        moveTo(a.x(58f), a.y(24f))
        quadraticBezierTo(a.x(56f), a.y(6f), a.x(70f), a.y(4f))
    }, outline, 2f)

    // Long-travel forks + swingarm
    stroke(a, { moveTo(a.x(60f), a.y(26f)); lineTo(a.x(38f), a.y(72f)) }, outline, 2.8f)
    stroke(a, { moveTo(a.x(122f), a.y(54f)); lineTo(a.x(162f), a.y(72f)) }, outline, 2.4f)

    panel(a, {
        moveTo(a.x(84f), a.y(50f)); lineTo(a.x(118f), a.y(48f))
        lineTo(a.x(122f), a.y(62f)); lineTo(a.x(86f), a.y(64f)); close()
    }, outline, outline, 1.6f)

    drawCircle(outline, a.len(6f), a.pt(60f, 30f))
}
