package com.eshwar.rideconnectx.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the wire format to packets actually captured from the vehicle.
 *
 * These two byte strings are ground truth — they were logged coming out of the
 * official app / off the scooter. Anything the encoder produces has to agree
 * with them, so a future "tidy-up" of the checksum or the field offsets cannot
 * quietly go back to being wrong.
 */
class ProtocolEngineTest {

    private companion object {
        /** Real 0x31 navigation packet. Checksum byte is 0xD9. */
        val CAPTURED_NAVIGATION = byteArrayOf(
            0xA5.toByte(), 0x31, 0x04, 0xFF.toByte(),
            0x30, 0x31, 0x30, 0x30, 0x4D,                    // "0100M"
            0x30, 0x36, 0x31, 0x33, 0x50, 0x4D,              // "0613PM"
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x30, 0x32, 0x39, 0x39, 0x4D,                    // "0299M"
            0x31, 0x31,                                      // "11"
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xD9.toByte(), 0x7F,
        )

        /** Real 0x37 telemetry packet. Checksum byte is 0x61. */
        val CAPTURED_TELEMETRY = byteArrayOf(
            0xA5.toByte(), 0x37,
            0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
            0x38, 0x39, 0x37, 0x30,
            0x30, 0x32, 0x31, 0x38,
            0x38, 0x30, 0x30, 0x33,
            0x37, 0x33, 0x39,
            0x01, 0x35, 0x00, 0x00, 0x00,
            0x61, 0x7F,
        )
    }

    // ── Checksum ───────────────────────────────────────────────────

    @Test
    fun `checksum reproduces the captured navigation packet`() {
        assertEquals(
            CAPTURED_NAVIGATION[28],
            ProtocolEngine.calculateChecksum(CAPTURED_NAVIGATION),
        )
    }

    /**
     * A different packet type, so this is what rules out a coincidence: one rule
     * has to satisfy both, and only the one's complement of the sum does.
     */
    @Test
    fun `checksum reproduces the captured telemetry packet`() {
        assertEquals(
            CAPTURED_TELEMETRY[28],
            ProtocolEngine.calculateChecksum(CAPTURED_TELEMETRY),
        )
    }

    @Test
    fun `checksum is not a plain sum`() {
        // The previous implementation. Kept as a test so the regression is loud.
        var sum = 0
        for (i in 1..27) sum += CAPTURED_NAVIGATION[i].toInt() and 0xFF
        assertFalse(
            "plain sum must not match — that was the old bug",
            (sum and 0xFF).toByte() == CAPTURED_NAVIGATION[28],
        )
    }

    // ── Navigation packet ──────────────────────────────────────────

    /**
     * The maneuver code is a raw byte at index 2 — `bytes[2] = (byte) i` in the
     * official builder. An earlier version of this encoder wrote it as two ASCII
     * digits at 23-24, which are actually the GPS/nav status characters.
     */
    @Test
    fun `maneuver code is a raw byte at index 2`() {
        val p = ProtocolEngine.buildNavigationPacket(
            clusterCode = ProtocolEngine.Maneuver.TURN_LEFT,
            distanceMetres = 299,
            clock = "0613PM",
        )

        assertEquals(ProtocolEngine.Maneuver.TURN_LEFT.toByte(), p[2])
        assertEquals(0xFF.toByte(), p[3])
    }

    @Test
    fun `status characters sit at 23 and 24, not the maneuver`() {
        val p = ProtocolEngine.buildNavigationPacket(
            clusterCode = ProtocolEngine.Maneuver.TURN_RIGHT,
            distanceMetres = 100,
            clock = "0900AM",
            gpsStatus = ProtocolEngine.GPS_DISABLED,
        )

        assertEquals(ProtocolEngine.GPS_DISABLED.code.toByte(), p[23])
        assertEquals('1'.code.toByte(), p[24])
    }

    @Test
    fun `navigation packet keeps the frame envelope`() {
        val p = ProtocolEngine.buildNavigationPacket(11, 40, "0900AM")

        assertEquals(30, p.size)
        assertEquals(0xA5.toByte(), p[0])
        assertEquals(0x31.toByte(), p[1])
        assertEquals(0x7F.toByte(), p[29])
        assertEquals(ProtocolEngine.calculateChecksum(p), p[28])
    }

    /**
     * The NEXT-TURN distance belongs at bytes 4-8, beside the arrow.
     *
     * These tests previously asserted it at 18-22, matching a builder that
     * called the 4-8 field `speed` and hardcoded it to "000". The rider
     * photographed the cluster on 19 August with the fields circled: the turn
     * distance was appearing in the destination slot and `0m` sat beside the
     * arrow for the entire 91 minute ride.
     */
    @Test
    fun `next-turn distance is zero padded to four digits at bytes 4-8`() {
        val p = ProtocolEngine.buildNavigationPacket(11, 40, "0900AM")
        assertEquals("0040M", String(p, 4, 5, Charsets.US_ASCII))
    }

    @Test
    fun `next-turn distance is clamped rather than overflowing the field`() {
        val p = ProtocolEngine.buildNavigationPacket(11, 999_999, "0900AM")
        // 999,999 m is 999 km; the field holds four characters plus the unit.
        assertEquals("0999K", String(p, 4, 5, Charsets.US_ASCII))
        assertEquals(30, p.size)
    }

    /**
     * The rider's rule, given 21 August 2026 after watching it on the cluster:
     *
     * > Under 1000 m, show metres. 1000 m and above, show kilometres.
     *
     * Metres for everything is what produced `2500m` where the dashboard should
     * read `2.5 km`, and worse, a 43 km destination that simply clamped to a
     * meaningless `9999m` until the rider came within 10 km of it.
     */
    @Test
    fun `distances under a kilometre stay in metres`() {
        assertEquals("0000M", ProtocolEngine.formatDistance(0))
        assertEquals("0040M", ProtocolEngine.formatDistance(40))
        assertEquals("0900M", ProtocolEngine.formatDistance(900))
        assertEquals("0999M", ProtocolEngine.formatDistance(999))
    }

    @Test
    fun `a kilometre and over switches to kilometres`() {
        assertEquals("01.0K", ProtocolEngine.formatDistance(1_000))
        assertEquals("02.5K", ProtocolEngine.formatDistance(2_500))
        assertEquals("03.7K", ProtocolEngine.formatDistance(3_700))
        // Past ten kilometres a decimal no longer fits four characters.
        assertEquals("0043K", ProtocolEngine.formatDistance(43_000))
    }

    /**
     * The kilometre layout has never been observed - every distance captured
     * from the official app is under 1000 m - so the alternatives are
     * switchable at runtime and testable on the cluster without a rebuild.
     */
    @Test
    fun `the kilometre style can be switched without touching metres`() {
        try {
            ProtocolEngine.kmStyle = 1
            assertEquals("0025K", ProtocolEngine.formatDistance(2_500))
            ProtocolEngine.kmStyle = 2
            assertEquals("0002K", ProtocolEngine.formatDistance(2_500))
            // Metres are confirmed behaviour and must not move with the style.
            assertEquals("0900M", ProtocolEngine.formatDistance(900))
        } finally {
            ProtocolEngine.kmStyle = 0
        }
    }

    /** Remaining-to-destination is a separate field at bytes 18-22. */
    @Test
    fun `remaining distance goes to bytes 18-22 and defaults to blank`() {
        val withRemaining = ProtocolEngine.buildNavigationPacket(
            clusterCode = 11, distanceMetres = 40, clock = "0900AM",
            remainingMetres = 8500,
        )
        // 8,500 m is 8.5 km — both distance fields follow the same rule.
        assertEquals("08.5K", String(withRemaining, 18, 5, Charsets.US_ASCII))
        // Turn distance must be unaffected by it.
        assertEquals("0040M", String(withRemaining, 4, 5, Charsets.US_ASCII))

        // Not supplied - the slot is blank (FF), not a literal 0000M.
        val without = ProtocolEngine.buildNavigationPacket(11, 40, "0900AM")
        for (i in 18..22) assertEquals(0xFF.toByte(), without[i])
    }

    // ── Profile packet ─────────────────────────────────────────────

    /**
     * Mirrors the official builder exactly. The name is *not* upper-cased — the
     * cluster does that — and bytes 22-26 carry `FF`, with a flag at 27. The
     * first hardware attempt left those as zeros and the cluster ignored it.
     */
    @Test
    fun `profile packet matches the official layout`() {
        val p = ProtocolEngine.buildProfilePacket("Gnyaneshwar")

        assertEquals(0xA5.toByte(), p[0])
        assertEquals(0x36.toByte(), p[1])
        assertEquals("Gnyaneshwar", String(p, 2, 11, Charsets.US_ASCII))

        // Name field is NUL-padded through byte 21.
        for (i in 13..21) assertEquals("byte $i should be NUL", 0.toByte(), p[i])

        // The bytes the first attempt got wrong.
        for (i in 22..26) assertEquals("byte $i should be 0xFF", 0xFF.toByte(), p[i])
        assertEquals('F'.code.toByte(), p[27])

        assertEquals(ProtocolEngine.calculateChecksum(p), p[28])
        assertEquals(0x7F.toByte(), p[29])
    }

    @Test
    fun `checksum branch follows the vehicle model`() {
        // SAS210217219 — the development Access 125. Complement branch.
        ProtocolEngine.configureForDevice("SAS210217219")
        assertTrue(ProtocolEngine.complementChecksum)

        // TFT / e-ACCESS families use the plain sum.
        ProtocolEngine.configureForDevice("SCE100000000")
        assertFalse(ProtocolEngine.complementChecksum)
        ProtocolEngine.configureForDevice("SCS010000000")
        assertFalse(ProtocolEngine.complementChecksum)

        ProtocolEngine.configureForDevice("SAS210217219")
        assertTrue(ProtocolEngine.complementChecksum)
    }

    @Test
    fun `profile packet strips characters the cluster cannot render`() {
        val p = ProtocolEngine.buildProfilePacket("Gnyaneshwar 🏍")
        assertTrue(p.all { it.toInt() and 0xFF <= 0xFF })
        assertEquals(30, p.size)
    }

    // ── Guard rails ────────────────────────────────────────────────

    private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }

    // ── 0x37 telemetry ──────────────────────────────────────────────────
    //
    // Captured from the rider's Access 125 on 4 August 2026 at 18:50. The
    // cluster was photographed in the same minute and read ODO 001602 km,
    // Trip A 198.7 km, Trip B 1079.2 km, fuel bar full. This test pins that
    // agreement — the field split was wrong for months and nothing caught it.
    private val realTelemetryFrame = byteArrayOf(
        0xA5.toByte(), 0x37,
        // "000001602001987010792"
        0x30, 0x30, 0x30, 0x30, 0x30, 0x31, 0x36, 0x30, 0x32,
        0x30, 0x30, 0x31, 0x39, 0x38, 0x37,
        0x30, 0x31, 0x30, 0x37, 0x39, 0x32,
        0x01, 0x35, 0x00, 0x00, 0x00, 0x6D, 0x7F,
    )

    @Test
    fun `telemetry decodes to the values shown on the cluster`() {
        val t = ProtocolEngine.parseTelemetry(realTelemetryFrame)

        assertNotNull(t)
        assertEquals(1602, t!!.odometerKm)
        assertEquals(198.7f, t.tripAKm, 0.001f)
        assertEquals(1079.2f, t.tripBKm, 0.001f)
        assertEquals(5, t.fuelSegments)
        assertTrue(t.isValid)
    }

    @Test
    fun `the older archived capture decodes with the same field split`() {
        // a5 37 000000897 002188 003739 01 35 ... — a genuinely different
        // reading, so a split that only fits one frame cannot pass both.
        val frame = realTelemetryFrame.copyOf()
        "000000897002188003739".forEachIndexed { i, ch -> frame[2 + i] = ch.code.toByte() }
        frame[28] = ProtocolEngine.calculateChecksum(frame)

        val t = ProtocolEngine.parseTelemetry(frame)

        assertNotNull(t)
        assertEquals(897, t!!.odometerKm)
        assertEquals(218.8f, t.tripAKm, 0.001f)
        assertEquals(373.9f, t.tripBKm, 0.001f)
    }

    // ── 0x33 heartbeat ──────────────────────────────────────────────────

    /**
     * Ground truth: a status packet logged straight out of the official app
     * app on 4 August 2026 while it was paired to this cluster.
     *
     *     A5 33 32 59 FF FF FF 33 31 30 35 31 33 38 4E 4E FF×12 4F 7F
     *           ↑  ↑           ↑  └ clock 105138 ┘  ↑  ↑
     *      batt 2  charging  signal 3          idle N N
     *
     * Two things this settled: signal really is `'3'` at full strength — our
     * encoding already matched — and bytes 14-15 rest at `'N'`, not `'Y'`.
     */
    @Test
    fun `heartbeat reproduces the official app's captured status packet`() {
        val p = ProtocolEngine.buildHeartbeatPacket(
            batteryBucket = 2,
            isCharging = true,
            signalBars = 3,
            speedKmh = 0,
            clockHHmmss = "105138",
            notificationPending = false,
            missedCallPending = false,
        )

        val official = byteArrayOf(
            0xA5.toByte(), 0x33, 0x32, 0x59,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x33, 0x31, 0x30, 0x35, 0x31, 0x33, 0x38,
            0x4E, 0x4E,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x4F, 0x7F,
        )

        assertEquals(official.toHex(), p.toHex())
    }

    @Test
    fun `heartbeat carries battery, signal, clock and the two flags`() {
        val p = ProtocolEngine.buildHeartbeatPacket(
            batteryBucket = 9,
            isCharging = true,
            signalBars = 3,
            speedKmh = 0,
            clockHHmmss = "185231",
            notificationPending = true,
            missedCallPending = false,
        )

        assertEquals(30, p.size)
        assertEquals(0xA5.toByte(), p[0])
        assertEquals(0x33.toByte(), p[1])
        assertEquals('9'.code.toByte(), p[2])
        assertEquals('Y'.code.toByte(), p[3])

        // Zero speed is blanked, not sent as "000" — that would draw a literal 0.
        for (i in 4..6) assertEquals("byte $i should be FF", 0xFF.toByte(), p[i])

        assertEquals('3'.code.toByte(), p[7])
        assertEquals("185231", String(p, 8, 6, Charsets.US_ASCII))

        assertEquals('Y'.code.toByte(), p[14]) // notification waiting
        assertEquals('N'.code.toByte(), p[15]) // no missed call
        for (i in 16..27) assertEquals("byte $i should be FF", 0xFF.toByte(), p[i])
        assertEquals(0x7F.toByte(), p[29])
    }

    /**
     * The battery field is a single ASCII digit, so nothing may overflow it.
     *
     * On the dashboard, 3 drew three bars, 4 also drew three, 5 blanked the
     * indicator; an earlier 0..9 bucket sent 8 for an 88% phone, out of range,
     * which is why the icon showed empty. Note the official app reaches a
     * *full* battery, so this field is not yet fully understood — this test
     * only pins the frame's shape, not the semantics.
     */
    @Test
    fun `battery field never exceeds what the cluster accepts`() {
        for (bucket in 0..9) {
            val p = ProtocolEngine.buildHeartbeatPacket(
                batteryBucket = bucket,
                isCharging = false,
                signalBars = 0,
                clockHHmmss = "120000",
                notificationPending = false,
                missedCallPending = false,
            )
            val sent = String(p, 2, 1, Charsets.US_ASCII).toInt()
            assertTrue("bucket $bucket sent $sent, above the cluster's max", sent <= 9)
        }
    }

    @Test
    fun `heartbeat writes a real speed when the scooter is moving`() {
        val p = ProtocolEngine.buildHeartbeatPacket(
            batteryBucket = 5,
            isCharging = false,
            signalBars = 1,
            speedKmh = 42,
            clockHHmmss = "090000",
            notificationPending = false,
            missedCallPending = true,
        )

        assertEquals("042", String(p, 4, 3, Charsets.US_ASCII))
        assertEquals('N'.code.toByte(), p[3]) // not charging
        assertEquals('Y'.code.toByte(), p[15]) // missed call waiting
    }

    @Test
    fun `a malformed frame is rejected rather than guessed at`() {
        // Non-ASCII in the digit range: better to keep the last good reading
        // than to put an invented number on the rider's dashboard.
        val garbled = realTelemetryFrame.copyOf().also { it[5] = 0xFF.toByte() }
        assertNull(ProtocolEngine.parseTelemetry(garbled))

        // Right shape, wrong packet type.
        val wrongType = realTelemetryFrame.copyOf().also { it[1] = 0x31 }
        assertNull(ProtocolEngine.parseTelemetry(wrongType))
    }

    @Test
    fun `a frame whose checksum does not match is rejected`() {
        // Still all ASCII digits, so only byte 28 can catch it: this is the
        // shape of the corruption that produced the 6,001,923 km card.
        val corrupt = realTelemetryFrame.copyOf().also { it[4] = '6'.code.toByte() }
        assertNull(ProtocolEngine.parseTelemetry(corrupt))
    }

    // ── Alert packets 0x32 / 0x34 / 0x35 ───────────────────────────────

    private fun ascii(p: ByteArray, from: Int, to: Int) =
        String(p, from, to - from, Charsets.US_ASCII).trimEnd('\u0000')

    private fun assertFramed(p: ByteArray) {
        assertEquals(30, p.size)
        assertEquals(0xA5.toByte(), p[0])
        assertEquals(0x7F.toByte(), p[29])
        assertEquals(ProtocolEngine.calculateChecksum(p), p[28])
    }

    @Test
    fun `a call goes out as the official 0x32 ringing packet`() {
        val p = ProtocolEngine.buildCallPacket("Grand Mother")
        assertFramed(p)
        assertEquals(0x32.toByte(), p[1])
        assertEquals("Grand Mother", ascii(p, 2, 22))
        assertEquals('N'.code.toByte(), p[22])
        assertEquals('1'.code.toByte(), p[23])
        for (i in 24..27) assertEquals(0xFF.toByte(), p[i])

        val wa = ProtocolEngine.buildCallPacket("+91 98765", whatsApp = true, answered = true)
        assertEquals("91 98765", ascii(wa, 2, 22))
        assertEquals('W'.code.toByte(), wa[22])
        assertEquals('2'.code.toByte(), wa[23])
    }

    @Test
    fun `a message goes out as the official 0x35 packet with the sender only`() {
        val p = ProtocolEngine.buildMessagePacket('W', "Ravi", count = 3)
        assertFramed(p)
        assertEquals(0x35.toByte(), p[1])
        assertEquals(0xFF.toByte(), p[2])
        assertEquals('Y'.code.toByte(), p[3])
        assertEquals(3.toByte(), p[4])
        assertEquals("Ravi", ascii(p, 5, 25))
        assertEquals('W'.code.toByte(), p[25])
        assertEquals(0xFF.toByte(), p[26])
        assertEquals(0xFF.toByte(), p[27])

        // Unknown type letters fall back to SMS; a 25-character name is cut at 20.
        val long = ProtocolEngine.buildMessagePacket('Q', "A".repeat(25))
        assertEquals('N'.code.toByte(), long[25])
        assertEquals("A".repeat(20), ascii(long, 5, 25))
    }

    @Test
    fun `a missed call follows the official 0x34 layout`() {
        val p = ProtocolEngine.buildMissedCallPacket(2, "Ravi", whatsApp = true)
        assertFramed(p)
        assertEquals(0x34.toByte(), p[1])
        assertEquals(0xFF.toByte(), p[2])
        assertEquals(2.toByte(), p[3])
        assertEquals("Ravi", ascii(p, 4, 24))
        assertEquals('W'.code.toByte(), p[24])
        for (i in 25..27) assertEquals(0xFF.toByte(), p[i])
    }

    @Test
    fun `count reset forms match the official app`() {
        val missed = ProtocolEngine.buildMissedCallPacket(0, "")
        assertFramed(missed)
        assertEquals(0.toByte(), missed[3])
        for (i in 4..23) assertEquals(0.toByte(), missed[i])

        val msg = ProtocolEngine.buildMessagePacket('N', "", count = 0, newAlert = false)
        assertFramed(msg)
        assertEquals('N'.code.toByte(), msg[3])
        assertEquals(0.toByte(), msg[4])
        for (i in 5..24) assertEquals(0.toByte(), msg[i])
    }

    @Test
    fun `non-English names are made safe, never garbage bytes`() {
        assertEquals("Jose", ProtocolEngine.sanitizeString("José"))
        assertEquals("", ProtocolEngine.sanitizeString("हिन्दी"))
        // Nothing printable in title or body: a generic label, not an empty name.
        val p = ProtocolEngine.buildNotificationPacket('C', "हि", "")
        assertEquals("Incoming call", ascii(p, 2, 22))
        val m = ProtocolEngine.buildNotificationPacket('N', "ह", "")
        assertEquals("New message", ascii(m, 5, 25))
    }

    @Test
    fun `body text never reaches the frame`() {
        val m = ProtocolEngine.buildNotificationPacket('W', "ह", "SECRETBODY text")
        assertEquals("New message", ascii(m, 5, 25))
        assertEquals(false, String(m, Charsets.ISO_8859_1).contains("SECRET"))
        assertEquals("Missed call", ascii(ProtocolEngine.buildMissedCallPacket(1, "हि"), 4, 24))
    }

    @Test
    fun `an unknown device name goes back to the Access checksum branch`() {
        ProtocolEngine.configureForDevice("SCE100000000")
        assertFalse(ProtocolEngine.complementChecksum)
        ProtocolEngine.configureForDevice(null)
        assertTrue(ProtocolEngine.complementChecksum)
        ProtocolEngine.configureForDevice("AS")
        assertTrue(ProtocolEngine.complementChecksum)
    }

    // ── Edge inputs (14 Oct 2026 check) ────────────────────────────────

    @Test
    fun `every builder gives 30 bytes for empty, long and odd input`() {
        val odd = "‮abć😀हि" + "x".repeat(200)
        val all = listOf(
            ProtocolEngine.buildNavigationPacket(-5, Int.MAX_VALUE, "", Int.MIN_VALUE),
            ProtocolEngine.buildNavigationPacket(999, 12345678, odd, Int.MAX_VALUE, distanceText = odd),
            ProtocolEngine.buildProfilePacket(odd),
            ProtocolEngine.buildProfilePacket(""),
            ProtocolEngine.buildCallPacket(odd),
            ProtocolEngine.buildCallPacket(""),
            ProtocolEngine.buildMissedCallPacket(Int.MAX_VALUE, odd),
            ProtocolEngine.buildMessagePacket('W', odd, Int.MIN_VALUE),
            ProtocolEngine.buildNotificationPacket('N', odd, odd),
            ProtocolEngine.buildNotificationPacket('C', "", ""),
        )
        all.forEach { assertFramed(it) }
    }

    @Test
    fun `distance field is always five characters`() {
        for (m in listOf(Int.MIN_VALUE, -1, 0, 999, 1000, 9949, 9950, 9999, 10000, 99999, Int.MAX_VALUE)) {
            assertEquals("m=$m", 5, ProtocolEngine.formatDistance(m).length)
        }
    }

    @Test
    fun `parser never throws on short, long or garbage input`() {
        assertNull(ProtocolEngine.parseTelemetry(ByteArray(0)))
        assertNull(ProtocolEngine.parseTelemetry(ByteArray(29)))
        assertNull(ProtocolEngine.parseTelemetry(ByteArray(100) { 0xFF.toByte() }))
        val rnd = java.util.Random(7)
        repeat(200) { ProtocolEngine.parseTelemetry(ByteArray(rnd.nextInt(60)).also { rnd.nextBytes(it) }) }
    }
}
