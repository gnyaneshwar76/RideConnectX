package com.eshwar.rideconnectx.data.local

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double) {
    /** Great-circle distance in metres. */
    fun distanceTo(o: LatLon): Double {
        val p1 = Math.toRadians(lat); val p2 = Math.toRadians(o.lat)
        val dp = p2 - p1; val dl = Math.toRadians(o.lon - lon)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * 6_371_000.0 * atan2(sqrt(a), sqrt(1 - a))
    }
}

/** Route storage: Google encoded polyline (1e-5 degrees, about 1 m) and simplification. */
object RoutePolyline {
    const val MAX_POINTS = 2000

    fun encode(points: List<LatLon>): String {
        val sb = StringBuilder()
        var pLat = 0L; var pLon = 0L
        for (p in points) {
            val la = (p.lat * 1e5).roundToLong(); val lo = (p.lon * 1e5).roundToLong()
            put(la - pLat, sb); put(lo - pLon, sb)
            pLat = la; pLon = lo
        }
        return sb.toString()
    }

    private fun put(v: Long, sb: StringBuilder) {
        var n = if (v < 0) (v shl 1).inv() else v shl 1
        while (n >= 0x20) { sb.append((((n and 0x1f) or 0x20) + 63).toInt().toChar()); n = n shr 5 }
        sb.append((n + 63).toInt().toChar())
    }

    /** Bad input gives the points read so far, never a throw. */
    fun decode(s: String): List<LatLon> {
        val out = ArrayList<LatLon>()
        var i = 0; var la = 0L; var lo = 0L
        fun next(): Long? {
            var shift = 0; var r = 0L
            while (i < s.length) {
                val b = s[i++].code - 63
                r = r or ((b and 0x1f).toLong() shl shift)
                shift += 5
                if (b < 0x20) return if (r and 1L != 0L) (r shr 1).inv() else r shr 1
                if (shift > 60) return null
            }
            return null
        }
        while (i < s.length) {
            la += next() ?: break
            lo += next() ?: break
            out += LatLon(la / 1e5, lo / 1e5)
        }
        return out
    }

    /** Douglas-Peucker; start and end are always kept. Iterative, so long rides cannot overflow the stack. */
    fun simplify(pts: List<LatLon>, toleranceM: Double): List<LatLon> {
        if (pts.size < 3) return pts
        val lat0 = Math.toRadians(pts[0].lat)
        val kx = 111_320.0 * cos(lat0); val ky = 110_574.0
        val x = DoubleArray(pts.size) { (pts[it].lon - pts[0].lon) * kx }
        val y = DoubleArray(pts.size) { (pts[it].lat - pts[0].lat) * ky }
        val keep = BooleanArray(pts.size)
        keep[0] = true; keep[pts.lastIndex] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, pts.lastIndex))
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var far = -1; var farD = toleranceM
            for (k in a + 1 until b) {
                val d = segDist(x[k], y[k], x[a], y[a], x[b], y[b])
                if (d > farD) { farD = d; far = k }
            }
            if (far >= 0) {
                keep[far] = true
                stack.addLast(intArrayOf(a, far)); stack.addLast(intArrayOf(far, b))
            }
        }
        return pts.filterIndexed { i, _ -> keep[i] }
    }

    /** 5 m tolerance; doubled until at most [MAX_POINTS] remain. */
    fun simplifyCapped(pts: List<LatLon>, startTolM: Double = 5.0): List<LatLon> {
        var tol = startTolM
        var out = simplify(pts, tol)
        while (out.size > MAX_POINTS && tol < 1e6) { tol *= 2; out = simplify(pts, tol) }
        return out
    }

    private fun segDist(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
        val t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / len2))
        val cx = ax + t * dx; val cy = ay + t * dy
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }
}
