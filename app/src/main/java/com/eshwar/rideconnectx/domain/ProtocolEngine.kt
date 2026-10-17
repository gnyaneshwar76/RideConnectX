package com.eshwar.rideconnectx.domain

import java.util.Locale

/**
 * Single source of truth for the the cluster protocol Protocol.
 * Frame: [0xA5] (1 byte) | Payload (27 bytes) | Checksum (1 byte) | [0x7F] (1 byte)
 * Total: 30 bytes
 */
object ProtocolEngine {
    private const val START_BYTE = 0xA5.toByte()
    private const val END_BYTE = 0x7F.toByte()
    private const val PACKET_LENGTH = 30

    fun buildPacket(type: Int, payload: ByteArray): ByteArray {
        val frame = ByteArray(PACKET_LENGTH)
        frame[0] = START_BYTE
        
        // Byte 1 is typically the packet type/ID
        frame[1] = type.toByte()
        
        // Fill payload starting from Byte 2
        payload.copyInto(frame, destinationOffset = 2, endIndex = minOf(payload.size, 26))
        
        // Calculate Checksum at Byte 28
        frame[28] = calculateChecksum(frame)
        frame[29] = END_BYTE
        
        return frame
    }

    /**
     * Byte 28 — one's complement of the low byte of the sum of bytes 1..27.
     *
     * Recovered by solving against two captured packets of different types
     * (0x31 navigation → 0xD9, 0x37 telemetry → 0x61). Of every plain sum, XOR
     * and CRC-8 variant swept over every plausible byte range, this was the only
     * rule that reproduced both. See `ProtocolEngineTest`, which pins it to those
     * captures so a regression here cannot pass silently.
     *
     * It was previously a plain sum, which matches neither capture.
     */
    fun calculateChecksum(frame: ByteArray): Byte {
        var sum = 0
        for (i in 1..27) {
            sum += frame[i].toInt() and 0xFF
        }
        return if (complementChecksum) {
            (sum.inv() and 0xFF).toByte()
        } else {
            (sum and 0xFF).toByte()
        }
    }

    /** The connected vehicle's profile, set by [configureForDevice] before the first send. */
    @Volatile
    var profile: VehicleProfile = profileFor(null)
        private set

    /** Which checksum branch the connected vehicle expects (see [VehicleProfile]). */
    val complementChecksum: Boolean get() = profile.complementChecksum

    /**
     * Picks the vehicle profile from the advertised device name. Call at
     * connect, before the first packet. An unknown or placeholder name goes
     * back to the Access profile rather than keep the last vehicle's.
     */
    fun configureForDevice(deviceName: String?) {
        profile = profileFor(deviceName)
    }

    /**
     * Scan-list rule of the official app: a 12-character name containing
     * `SA`, `SB` or `SC`. Case-sensitive, as the official compare is.
     */
    fun isClusterName(name: String?): Boolean =
        name != null && name.length == 12 &&
            (name.contains("SA") || name.contains("SB") || name.contains("SC"))

    /**
     * The model from name characters 1-4 (`SAS210217219` -> `AS21`), in the
     * official app's order. Names it does not list keep today's behaviour:
     * `?C E|S..` plain sum, everything else the Access complement.
     */
    fun profileFor(deviceName: String?): VehicleProfile {
        val n = deviceName.orEmpty()
        if (n.length < 5) return VehicleProfile("", "", ClusterFamily.UNKNOWN, null)
        val c = n.substring(1, 5)
        fun p(model: String, family: ClusterFamily, altLaneIcons: Boolean?) =
            VehicleProfile(c, model, family, altLaneIcons)
        return when {
            c.startsWith("CE") -> p("e-ACCESS", ClusterFamily.ELECTRIC, true)
            c.startsWith("CS0") -> p("Access-TFT Edition", ClusterFamily.TFT, true)
            c.startsWith("CS1") -> p("Burgman Street-TFT Edition", ClusterFamily.TFT, true)
            c == "AS21" -> p("Access", ClusterFamily.ACCESS_LCD, true)
            c == "AS01" || c == "AS02" -> p("Access 125", ClusterFamily.ACCESS_LCD, false)
            c == "AS11" || c == "AS12" -> p("Burgman Street", ClusterFamily.ACCESS_LCD, false)
            c == "BS21" || c == "BS22" -> p("Avenis", ClusterFamily.SCOOTER_LCD, false)
            c == "BS31" || c == "BS32" -> p("Burgman Street EX", ClusterFamily.SCOOTER_LCD, false)
            c == "BS11" || c == "BS12" -> p("Burgman Street", ClusterFamily.SCOOTER_LCD, false)
            // shortcut: the 74/58 rule names "SBS51" but its meaning is unproven; null until a capture.
            c == "BS51" -> p("Burgman Street", ClusterFamily.SCOOTER_LCD, null)
            c == "BM01" -> p("V-STROM SX", ClusterFamily.MOTORCYCLE, false)
            c == "BM11" -> p("GIXXER / GIXXER SF", ClusterFamily.MOTORCYCLE, false)
            c == "BM21" || c == "BM22" -> p("GIXXER 250 / SF 250", ClusterFamily.MOTORCYCLE, false)
            c[0] == 'C' && (c[1] == 'E' || c[1] == 'S') -> p("", ClusterFamily.UNKNOWN_SUM, null)
            else -> p("", ClusterFamily.UNKNOWN, null)
        }
    }

    /**
     * Makes text safe for the cluster, which draws printable ASCII only.
     *
     * Accented Latin letters lose the accent ("Jose" for the accented form).
     * Anything with no ASCII form (Hindi, Telugu, Arabic, CJK, emoji) is
     * dropped, never sent as raw bytes. A name made only of such characters
     * therefore comes out empty; [buildNotificationPacket] then sends a generic
     * label so the rider still sees that something arrived. Never throws.
     */
    fun sanitizeString(input: String): String =
        java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
            .filter { it.code in 32..126 }
            .replace(Regex(" {2,}"), " ")
            .trim()

    /**
     * Builds the 0x31 navigation packet in the layout observed on the wire.
     *
     * Every field is ASCII, not binary — that is what the captures show:
     *
     *     0      A5
     *     1      31
     *     2-3    flags            04 FF
     *     4-8    trip metadata    "0100M"
     *     9-14   clock            "0613PM"
     *     15-17  spacer           FF FF FF
     *     18-22  distance to turn "0299M"
     *     23-24  maneuver code    "11"
     *     25-27  spacer           FF FF FF
     *     28     checksum
     *     29     7F
     *
     * The previous implementation wrote a binary maneuver id at byte 2 and the
     * distance as free text after it, under packet type 7. Neither matches
     * anything the cluster was ever seen to receive.
     *
     * @param clusterCode the two-digit ASCII wire code — see [ClusterManeuver]
     * @param distanceMetres distance to the maneuver, rendered as `NNNNM`
     * @param clock 6-char `HHMMAM`/`HHMMPM`
     */
    /**
     * How kilometres are written into a distance field. **Unverified.**
     *
     * The field is four characters plus a unit, and every distance ever
     * captured from the official app is under 1000 m - so no capture
     * shows how it writes kilometres, and the layout below is a considered
     * guess rather than a fact. Exposed as a variable so the alternatives can
     * be tried on the cluster with a broadcast instead of a rebuild:
     *
     * ```
     * adb shell "am broadcast ... -a com.eshwar.rideconnectx.TEST_KMSTYLE --ei style 1"
     * ```
     */
    @Volatile
    var kmStyle: Int = 0

    /**
     * A distance for the cluster's four-character field plus unit character.
     *
     * The rider's rule, given 21 August after riding it:
     *
     * > Under 1000 m, show metres. 1000 m and above, show kilometres.
     *
     * Metres were being sent for everything, so the dashboard read `2500m`
     * where it should read `2.5 km`, and the remaining-distance field simply
     * clamped: a 43 km destination showed a meaningless `9999m` until the rider
     * came within 10 km of it.
     *
     * The metres branch is confirmed - it is what the official app sends and
     * what the cluster has always drawn correctly. The kilometre branch is not;
     * see [kmStyle].
     */
    fun formatDistance(metres: Int): String {
        val m = metres.coerceAtLeast(0)
        if (m < 1000) return m.toString().padStart(4, '0') + "M"

        val km = m / 1000f
        return when (kmStyle) {
            // "02.5K" — a decimal point, which is how the value reads to a
            // human and what the dashboard's dot-matrix row can render.
            0 -> if (km < 10f) String.format(Locale.US, "%04.1f", km) + "K"
            else km.toInt().coerceAtMost(9999).toString().padStart(4, '0') + "K"

            // "0025K" — tenths of a kilometre with no point, in case the field
            // is digits-only and the cluster places the decimal itself.
            1 -> (m / 100).coerceAtMost(9999).toString().padStart(4, '0') + "K"

            // "0002K" — whole kilometres only, the plainest reading.
            else -> km.toInt().coerceAtMost(9999).toString().padStart(4, '0') + "K"
        }
    }

    fun buildNavigationPacket(
        clusterCode: Int,
        /**
         * Metres to the NEXT TURN. Goes to bytes 4-8, the field the cluster
         * prints beside the arrow.
         */
        distanceMetres: Int,
        clock: String,
        /**
         * Metres remaining to the DESTINATION. Goes to bytes 18-22, the field
         * printed beside the clock. 0 leaves it blank.
         */
        remainingMetres: Int = 0,
        gpsStatus: Char = GPS_OK,
        navActive: Char = '1',
        /**
         * Five ASCII characters to put in the turn-distance field instead of
         * [distanceMetres] - the reroute frame's "----". Unverified: whether
         * the cluster draws non-digits there is exactly what a ride settles.
         */
        distanceText: String? = null,
    ): ByteArray {
        val frame = ByteArray(PACKET_LENGTH)
        frame[0] = START_BYTE
        frame[1] = PacketType.NAVIGATION.toByte()

        // The maneuver code is a RAW BYTE here — not two ASCII digits further
        // along the frame, which is what an earlier note claimed and what this
        // builder originally did. `bytes[2] = (byte) i` in the official builder.
        frame[2] = clusterCode.toByte()
        frame[3] = 0xFF.toByte()

        // Bytes 4-8 are the NEXT-TURN distance, not speed.
        //
        // This parameter was called `speed` and hardcoded to "000", which is why
        // the cluster printed a permanent `0m` beside the arrow for the whole of
        // the 19 August ride. The rider photographed and annotated it: the field
        // carries an `m` unit, and speed would be km/h - it was simply
        // mislabelled. The turn distance was going to bytes 18-22 instead, so it
        // appeared in the destination-remaining slot at the bottom.
        putAscii(frame, 4, distanceText?.padEnd(5)?.take(5) ?: formatDistance(distanceMetres))
        putAscii(frame, 9, clock.padEnd(6).take(6))
        // TFT and e-ACCESS clusters: the official app blanks a leading-zero hour.
        if (profile.tft && frame[9] == '0'.code.toByte()) frame[9] = ' '.code.toByte()

        frame[15] = 0xFF.toByte(); frame[16] = 0xFF.toByte(); frame[17] = 0xFF.toByte()

        // Bytes 18-22: distance remaining to the destination, printed beside the
        // clock. Four digits then the unit character, same encoding as 4-8.
        // Unknown (0 or less) is left blank (FF, as the official app blanks
        // speed and clock) instead of a literal "0000M".
        // Unverified on the cluster: see the Knowledge Base.
        if (remainingMetres <= 0) {
            for (i in 18..22) frame[i] = 0xFF.toByte()
        } else {
            putAscii(frame, 18, formatDistance(remainingMetres))
        }

        // 23 and 24 are the status characters, NOT the maneuver:
        // '0' = airplane mode, '4' = GPS off, otherwise normal.
        frame[23] = gpsStatus.code.toByte()
        frame[24] = navActive.code.toByte()

        frame[25] = 0xFF.toByte(); frame[26] = 0xFF.toByte(); frame[27] = 0xFF.toByte()

        frame[28] = calculateChecksum(frame)
        frame[29] = END_BYTE
        return frame
    }

    const val GPS_OK = '1'
    const val GPS_AIRPLANE_MODE = '0'
    const val GPS_DISABLED = '4'

    /**
     * Builds the 0x36 profile packet — the rider's name, which is what makes the
     * cluster print `WELCOME <name>`.
     *
     * Ported from the official builder, which is:
     *
     *     str = "?6" + name padded with NULs + "\0\0\0\0\0"
     *     bytes[0] = 0xA5
     *     bytes[22..26] = 0xFF
     *     bytes[27] = flag ? 'F' : 'R'
     *     bytes[28] = checksum
     *     bytes[29] = 0x7F
     *
     * The first attempt against real hardware left bytes 22-27 as zeros, which
     * is almost certainly why the cluster ignored it. Those `FF` bytes and the
     * trailing flag are not padding — the official app writes them every time.
     *
     * The name is NUL-padded, not space-padded, and is **not** upper-cased by
     * the official app; the cluster does that itself.
     */
    fun buildProfilePacket(riderName: String, flag: Char = PROFILE_FLAG_DEFAULT): ByteArray {
        val frame = ByteArray(PACKET_LENGTH)
        frame[0] = START_BYTE
        frame[1] = PacketType.PROFILE.toByte()

        // Name occupies bytes 2..21 — twenty characters, NUL-padded.
        val name = sanitizeString(riderName).take(20).toByteArray(Charsets.US_ASCII)
        name.copyInto(frame, destinationOffset = 2, endIndex = name.size)

        for (i in 22..26) frame[i] = 0xFF.toByte()
        frame[27] = flag.code.toByte()

        frame[28] = calculateChecksum(frame)
        frame[29] = END_BYTE
        return frame
    }

    /**
     * Byte 27 of the profile packet — **not a constant**.
     *
     * It says whether this is the same cluster the phone connected to last
     * time. From `C5028d`:
     *
     * ```java
     * if (prefs.getString("prev_cluster","").equals(currentClusterName))
     *      C3055K.f9294s = false;   // bytes[27] = 82 = 'R'
     * else C3055K.f9294s = true;    // bytes[27] = 70 = 'F'
     * ```
     *
     * Hardcoding `'F'` told the cluster "brand new vehicle" on every single
     * connect, and it answered `<NAME> CONNECTED`. The rider reconnects to the
     * same scooter daily, so the official app is sending `'R'` — the greeting
     * path that prints WELCOME.
     */
    const val PROFILE_FLAG_NEW_CLUSTER = 'F'
    const val PROFILE_FLAG_SAME_CLUSTER = 'R'

    @Deprecated(
        "Byte 27 depends on whether the cluster is the same as last time.",
        ReplaceWith("PROFILE_FLAG_NEW_CLUSTER"),
    )
    const val PROFILE_FLAG_DEFAULT = PROFILE_FLAG_NEW_CLUSTER
    const val PROFILE_FLAG_ALT = PROFILE_FLAG_SAME_CLUSTER

    /** Byte 27 for [deviceName], given the cluster connected to last time. */
    fun profileFlagFor(deviceName: String?, previousDeviceName: String?): Char =
        if (!deviceName.isNullOrBlank() && deviceName == previousDeviceName) {
            PROFILE_FLAG_SAME_CLUSTER
        } else {
            PROFILE_FLAG_NEW_CLUSTER
        }

    /**
     * Build Packet 0x33: the periodic status/heartbeat.
     *
     * This is what puts the phone's signal bars and the notification and
     * missed-call indicators on the cluster. Layout recovered from `C4956y.run()`:
     *
     * ```java
     * str = "?3" + f17334G + f17343P + f17336I + f17342O + "0000000000000000";
     * // f17343P = String.format("%03d", speed)
     * // f17336I = "0".."3" from SignalStrength.getLevel()
     * // f17342O = HHmmss
     * ```
     *
     * | Bytes | Meaning                                             |
     * |-------|-----------------------------------------------------|
     * | 2-3   | phone battery bucket + charging flag (see below)     |
     * | 4-6   | speed, `%03d`; all `FF` when zero                    |
     * | 7     | signal bars, `'0'`..`'3'`; `0` on no SIM/airplane    |
     * | 8-13  | clock, `HHmmss`; all `FF` when `"000000"`            |
     * | 14    | notification flag — `'N'` present, `'Y'` cleared     |
     * | 15    | missed-call flag — same convention                   |
     * | 16-27 | `FF`                                                 |
     *
     * Bytes 2-3 are **not fully established**. The field is two characters, a
     * digit then `Y`/`N`; the app's initial value is `"1N"` and a live capture
     * held `"0Y"`, which reads as a battery bucket plus a charging flag. That
     * is the shape being sent, and it is the one thing here still to confirm by
     * watching the cluster.
     */
    fun buildHeartbeatPacket(
        batteryBucket: Int,
        isCharging: Boolean,
        signalBars: Int,
        speedKmh: Int = 0,
        clockHHmmss: String,
        notificationPending: Boolean,
        missedCallPending: Boolean,
    ): ByteArray {
        val frame = ByteArray(PACKET_LENGTH)
        frame[0] = START_BYTE
        frame[1] = PacketType.HEARTBEAT.toByte()

        putAscii(frame, 2, batteryBucket.coerceIn(0, 9).toString())
        frame[3] = (if (isCharging) 'Y' else 'N').code.toByte()

        val speed = speedKmh.coerceIn(0, 999)
        if (speed == 0) {
            // The official app blanks the speed field rather than sending "000",
            // which would draw a literal zero on the dashboard.
            for (i in 4..6) frame[i] = 0xFF.toByte()
        } else {
            putAscii(frame, 4, "%03d".format(speed))
        }

        // Both '3' and '4' draw three bars from our packet, so 4 buys nothing —
        // but the official app drives FOUR bars on the same cluster, so this
        // field is not fully understood. Do not widen the range on a hunch;
        // capture the official app's bytes first. See the knowledge base.
        putAscii(frame, 7, signalBars.coerceIn(0, 3).toString())

        val clock = clockHHmmss.filter { it.isDigit() }.padStart(6, '0').take(6)
        if (clock == "000000") {
            for (i in 8..13) frame[i] = 0xFF.toByte()
        } else {
            putAscii(frame, 8, clock)
        }

        frame[14] = (if (notificationPending) FLAG_PRESENT else FLAG_CLEARED).code.toByte()
        frame[15] = (if (missedCallPending) FLAG_PRESENT else FLAG_CLEARED).code.toByte()

        // NOTE: the official app's other branch does *not* blanket this range.
        // It writes a weather code at 21, temperature at 22 and 1 at 23, and
        // only fills 16-20 and 24-27 with FF. Filling all of 16-27 erases the
        // weather the cluster would otherwise show. Left as-is deliberately
        // until the real bytes are captured — see the knowledge base.
        for (i in 16..27) frame[i] = 0xFF.toByte()

        frame[28] = calculateChecksum(frame)
        frame[29] = END_BYTE
        return frame
    }

    /**
     * `'Y'` (89) = there is something for the cluster to show.
     *
     * Captured from the official app: its idle status packets carry `'N' 'N'`
     * at bytes 14-15. `m8617s()` holds `89` for three heartbeats after an alert
     * arrives and then drops back to `78`, so `'Y'` is the alert and `'N'` is
     * the resting state.
     *
     * This was inverted here until 4 August 2026, which meant every idle packet
     * announced a permanent alert and a real message cleared it instead.
     */
    const val FLAG_PRESENT = 'Y'
    /** `'N'` (78) = nothing pending; the cluster's resting state. */
    const val FLAG_CLEARED = 'N'

    private fun putAscii(frame: ByteArray, offset: Int, value: String) {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        bytes.copyInto(frame, destinationOffset = offset, endIndex = bytes.size)
    }

    /**
     * ASCII wire codes for the maneuver field at bytes 23-24.
     *
     * Only [STRAIGHT] is confirmed — it is the one seen repeatedly in live logs.
     * The rest were asserted in earlier notes but never observed against a known
     * dashboard state, so they are marked and must not be transmitted until a
     * capture of a real left/right/U-turn/arrival confirms them. Sending a
     * guessed code writes unverified bytes to vehicle firmware.
     */
    object ClusterManeuver {
        /** Confirmed from live capture. */
        const val STRAIGHT = "11"

        /** Seen live, meaning not established. */
        const val UNKNOWN_21 = "21"

        // Unverified — see the doc comment above.
        const val UNVERIFIED_LEFT = "02"
        const val UNVERIFIED_RIGHT = "03"
        const val UNVERIFIED_U_TURN = "05"
        const val UNVERIFIED_DESTINATION = "00"

        /** True only for codes proven against a real dashboard. */
        fun isConfirmed(code: String) = code == STRAIGHT
    }

    /**
     * The alert packet that puts a NAME on the cluster: a ringing call for
     * [appIdentifier] 'C', otherwise a message ('W' WhatsApp, 'N' SMS, 'X' calendar).
     *
     * Until 9 Oct 2026 this built a type 0x06 frame. The official app has no
     * 0x06 packet at all (N26): calls go as `?2` (0x32) and messages as `?5`
     * (0x35) - see [buildCallPacket] and [buildMessagePacket]. The name, never
     * the message body (which is never sent), is what the official app sends, 20 characters at most.
     *
     * [title] is whoever it is from. For a call the dialer has already resolved
     * the contact name (or the bare number), so no READ_CONTACTS is needed.
     */
    @Suppress("UNUSED_PARAMETER")
    fun buildNotificationPacket(appIdentifier: Char, title: String, message: String = ""): ByteArray =
        // [message] is ignored: private body text is never sent to the cluster.
        if (appIdentifier == 'C') buildCallPacket(title) else buildMessagePacket(appIdentifier, title)

    /** Official name field: 20 ASCII characters, '+' dropped from numbers, NUL padded. */
    private fun putName(frame: ByteArray, offset: Int, name: String, label: String) {
        // A non-empty name with no printable ASCII (e.g. Hindi) gets a fixed label, never other text.
        val shown = if (name.isNotEmpty() && sanitizeString(name).isEmpty()) label else name
        val bytes = sanitizeString(shown).replace("+", "").take(20).toByteArray(Charsets.US_ASCII)
        bytes.copyInto(frame, destinationOffset = offset)
    }

    private fun seal(frame: ByteArray): ByteArray {
        frame[0] = START_BYTE
        frame[28] = calculateChecksum(frame)
        frame[29] = END_BYTE
        return frame
    }

    /**
     * `?2` incoming call, from `CallReceiverBroadcast.m8664d` (official app):
     * bytes 2-21 caller, 22 'N' phone call / 'W' WhatsApp call, 23 '1' ringing /
     * '2' answered, 24-27 FF. The official app writes it 3 times, 200 ms apart.
     */
    fun buildCallPacket(caller: String, whatsApp: Boolean = false, answered: Boolean = false): ByteArray {
        val frame = ByteArray(PACKET_LENGTH)
        frame[1] = PacketType.CALL.toByte()
        putName(frame, 2, caller, "Incoming call")
        frame[22] = (if (whatsApp) 'W' else 'N').code.toByte()
        frame[23] = (if (answered) '2' else '1').code.toByte()
        for (i in 24..27) frame[i] = 0xFF.toByte()
        return seal(frame)
    }

    /**
     * `?4` missed call, from `CallReceiverBroadcast.m8668e`: byte 2 FF, 3 the
     * missed-call count (binary), 4-23 caller, 24 'N' / 'W', 25-27 FF.
     * The official app sends it when a ringing call ends unanswered.
     */
    fun buildMissedCallPacket(count: Int, caller: String, whatsApp: Boolean = false): ByteArray {
        val frame = ByteArray(PACKET_LENGTH)
        frame[1] = PacketType.MISSED_CALL.toByte()
        frame[2] = 0xFF.toByte()
        frame[3] = count.coerceIn(0, 99).toByte()
        putName(frame, 4, caller, "Missed call")
        frame[24] = (if (whatsApp) 'W' else 'N').code.toByte()
        for (i in 25..27) frame[i] = 0xFF.toByte()
        return seal(frame)
    }

    /**
     * `?5` message, from `NotificationService.m9121o`: byte 2 FF, 3 'Y' new
     * alert ('N' on a recount), 4 unread count (binary), 5-24 sender,
     * 25 type 'N' SMS / 'W' WhatsApp / 'X' calendar, 26-27 FF.
     */
    fun buildMessagePacket(type: Char, sender: String, count: Int = 1, newAlert: Boolean = true): ByteArray {
        val frame = ByteArray(PACKET_LENGTH)
        frame[1] = PacketType.MESSAGE.toByte()
        frame[2] = 0xFF.toByte()
        frame[3] = (if (newAlert) 'Y' else 'N').code.toByte()
        frame[4] = count.coerceIn(0, 99).toByte()
        putName(frame, 5, sender, "New message")
        frame[25] = (if (type == 'W' || type == 'X') type else 'N').code.toByte()
        frame[26] = 0xFF.toByte()
        frame[27] = 0xFF.toByte()
        return seal(frame)
    }
    
    /**
     * Parse an incoming 0x37 telemetry frame.
     *
     * Layout, confirmed 4 August 2026 against a live Access 125 by comparing a
     * captured frame with the cluster readings photographed at the same minute:
     *
     * ```
     * a5 37 | 000001602 | 001987 | 010792 | 01 | 35 | 00 00 00 | 6d 7f
     *          odo=1602    A=198.7  B=1079.2      fuel
     * ```
     *
     * | Bytes | Meaning                                   |
     * |-------|-------------------------------------------|
     * | 2-10  | odometer, 9 ASCII digits, whole km        |
     * | 11-16 | Trip A, 6 ASCII digits, tenths of a km    |
     * | 17-22 | Trip B, 6 ASCII digits, tenths of a km    |
     * | 23    | 0x01, meaning not established             |
     * | 24    | fuel bar as an ASCII digit, '0'..'5'      |
     * | 25-27 | zero padding                              |
     *
     * Returns null for anything that is not a well-formed telemetry frame, so a
     * malformed packet leaves the last good reading standing rather than
     * blanking the dashboard.
     */
    fun parseTelemetry(data: ByteArray): com.eshwar.rideconnectx.domain.model.ScooterTelemetry? {
        if (data.size < 30 || data[0] != START_BYTE || data[29] != END_BYTE) return null
        if (data[1] != PacketType.TELEMETRY.toByte()) return null

        // Byte 28 must match. Either branch is accepted: telemetry can arrive
        // before `configureForDevice` has read the vehicle name, and a garbled
        // frame matches neither. An unchecked frame is how 6,001,923 km got in.
        var sum = 0
        for (i in 1..27) sum += data[i].toInt() and 0xFF
        val checksum = data[28].toInt() and 0xFF
        if (checksum != (sum and 0xFF) && checksum != (sum.inv() and 0xFF)) return null

        // Every field is ASCII. A frame with anything else in the digit range is
        // not a shape we understand, and guessing at it would put invented
        // numbers on the rider's dashboard.
        val digits = String(data, 2, 21, Charsets.US_ASCII)
        if (!digits.all { it.isDigit() }) return null

        val fuelChar = data[24].toInt().toChar()
        val fuelSegments = if (fuelChar.isDigit()) fuelChar.digitToInt().coerceIn(0, 5) else 0

        return com.eshwar.rideconnectx.domain.model.ScooterTelemetry(
            odometerKm = digits.substring(0, 9).toInt(),
            tripAKm = digits.substring(9, 15).toInt() / 10f,
            tripBKm = digits.substring(15, 21).toInt() / 10f,
            fuelSegments = fuelSegments,
            fuelLevel = fuelSegments / 5f,
            isValid = true,
            // e-ACCESS only: byte 24 is battery percent + 64 (official parser; no capture yet).
            batteryPercent = if (profile.family == ClusterFamily.ELECTRIC) {
                ((data[24].toInt() and 0xFF) - 64).takeIf { it in 0..100 }
            } else null,
        )
    }

    /**
     * Packet types, as observed on the wire.
     *
     * The earlier values (navigation = 7, greeting = 36 decimal) were guesses.
     * The captures show navigation is 0x31 and profile is 0x36 *hex*.
     */
    object PacketType {
        /** Not an official packet type; nothing sends it since 9 Oct 2026 (N26). */
        const val NOTIFICATION = 0x06
        /** `?2` incoming call (official CallReceiverBroadcast.m8664d). */
        const val CALL = 0x32
        /** `?4` missed call (official CallReceiverBroadcast.m8668e). */
        const val MISSED_CALL = 0x34
        /** `?5` message (official NotificationService.m9121o). */
        const val MESSAGE = 0x35
        /** Navigation / turn-by-turn. Confirmed from logged packets. */
        const val NAVIGATION = 0x31
        /** Heartbeat. Carries the notification and missed-call flags. */
        const val HEARTBEAT = 0x33
        /** Rider profile — the source of the cluster's WELCOME line. */
        const val PROFILE = 0x36
        /** Scooter → phone telemetry. */
        const val TELEMETRY = 0x37
    }
    
    /**
     * Cluster maneuver codes, **re-calibrated on hardware 11 August 2026**.
     *
     * 🔒 **LOCKED by the rider.** Every value below was photographed on the
     * vehicle. Do not change one unless the rider asks for a re-calibration —
     * not from the decompiled app, not from the Mappls id table, not from a
     * pattern that looks more symmetrical.
     *
     * **Only 31..45 have been swept.** The destination flag, merge, ramp and
     * lane-guidance icons are almost certainly at codes below 31 and remain
     * unknown; 46..53 are reported blank but only by a sweep since discredited.
     *
     * Every value here was established by sending one code at a time to the
     * rider's Access 125 (`SAS210217219`) and photographing what the dashboard
     * drew. Raw log in `tools/cluster-codes.csv`.
     *
     * This replaces the 4 August table, which was wrong in almost every entry.
     * That sweep collected answers in batches while codes were being sent
     * rapidly, and the mismatches went unnoticed because the app was never
     * actually sending turns — a separate bug meant every maneuver came out as
     * straight ahead. The two faults hid each other for a week.
     *
     * How wrong it was: `40` is straight, not slight-right. `41` is slight
     * right, not a U-turn. `39` is the U-turn. `44` is keep-left, not
     * slight-left. Only `43` (ferry) and `45` (roundabout) survived.
     */
    object Maneuver {
        // ── Confirmed by photograph, 11 August 2026 ──────────────────
        /** Y-fork, left branch. */
        const val KEEP_LEFT = 31
        /** Y-fork, right branch. */
        const val KEEP_RIGHT = 32
        /** Up arrow through a crossroads. */
        const val CROSSROADS = 33
        /** Hairpin curving left. */
        const val SHARP_LEFT = 34
        /**
         * Curved road bending right. Was TURN_RIGHT until 11 Sep 2026: the rider
         * rode it and it reads as a bend in the road, not the junction turn
         * Maps draws. Kept for reference; plain right turns now send
         * [JUNCTION_RIGHT].
         */
        const val CURVED_ROAD_RIGHT = 35
        /** Hairpin curving right. */
        const val SHARP_RIGHT = 36
        /** Curved road bending left — see [CURVED_ROAD_RIGHT]. */
        const val CURVED_ROAD_LEFT = 37
        const val U_TURN = 39
        const val STRAIGHT = 40
        /** Plain diagonal arrow, up and to the right. */
        const val SLIGHT_RIGHT = 41
        /** A second, horizontal right arrow, distinct from [TURN_RIGHT]. */
        const val TURN_RIGHT_FLAT = 42
        const val FERRY = 43
        /** Arrow branching left past two lane bars. */
        const val KEEP_LEFT_LANES = 44
        const val ROUNDABOUT = 45

        /**
         * No plain up-left diagonal exists in 31..45 — the mirror of
         * [SLIGHT_RIGHT] appears not to be drawn by this cluster. Falls back to
         * a full left turn: the direction is right and the angle merely
         * overstated, which is far safer than an arrow the cluster ignores.
         */
        // -- Confirmed by photograph, 18 August 2026 -------------------
        // The sweep finally went below 31 and found two more families. Each
        // code was held on the cluster alone and photographed, with the
        // distance field set equal to the code so every screen labelled its
        // own number.

        /** Bare diagonal up-left. The true mirror of [SLIGHT_RIGHT]. */
        const val SLIGHT_LEFT_DIAGONAL = 19
        /** Bare flat arrow, straight left. */
        const val LEFT_FLAT = 18
        /** Bare diagonal down-left. */
        const val DOWN_LEFT = 17
        /** Bare arrow straight down, 'go back'. Not the curved [U_TURN]. */
        const val BACK = 16
        /** Bare diagonal down-right. */
        const val DOWN_RIGHT = 15

        // 1-9 is road-shaped: a shaft bending at a junction, the way Google
        // Maps draws its own icons.
        /** Junction turn left. */
        const val JUNCTION_LEFT = 1

        /**
         * Plain left/right turns, 11 Sep 2026.
         *
         * The Dammaiguda loop test showed [CURVED_ROAD_LEFT]/[CURVED_ROAD_RIGHT]
         * bending the correct way but drawn as a curve in the road. The rider
         * wants "straight, then curve to the side" — the junction glyph 1/4,
         * photographed 18 Aug. To confirm on hardware: hold 1 and 4 before the
         * next route run.
         */
        const val TURN_LEFT = 1
        const val TURN_RIGHT = 4
        /** Junction slight left. */
        const val JUNCTION_SLIGHT_LEFT = 2
        /** Curving road bending left. */
        const val CURVE_LEFT = 3
        /** Junction turn right. */
        const val JUNCTION_RIGHT = 4
        /** Hairpin right: shaft rises, head turns back down-right. */
        const val HAIRPIN_RIGHT = 5
        /** Curving road bending right. */
        const val CURVE_RIGHT = 6
        /** A second U-turn glyph: up, over the top, back down. */
        const val U_TURN_LOOP = 7

        /**
         * Slight left is code 19, CONFIRMED ON HARDWARE 18 August 2026.
         *
         * It was [TURN_LEFT] because the 31..45 sweep found no up-left diagonal
         * and concluded the cluster could not draw one. It can - the icon lives
         * below 31, which no sweep had ever covered. Until now every ride drew a
         * full curved left for a slight left: right direction, wrong angle.
         */
        /* ── Roundabouts by exit bearing, cluster 20-26 ──────────────────
         *
         * The cluster draws a ring with the exit arrow at a different bearing
         * for each of these. Predicted from the APK's icon geometry
         * (`docs/Vendor-APK-Reference.md`), with **23 confirmed on hardware**
         * in the 18 August sweep - the sweep that got 8 of 8 predictions right
         * and so established that the geometry decode is trustworthy.
         *
         * Used because the rider rode the alternative on 21 August: collapsing
         * every roundabout to the bare [ROUNDABOUT] ring tells you nothing
         * about which way to leave it, and 61 frames of that run were
         * roundabouts whose exit Maps knew perfectly well.
         *
         * There is a second set at 47-52, most likely the anticlockwise
         * variant for right-hand-drive countries. Unswept, and India rides
         * clockwise, so both directions map here for now.
         */
        const val ROUNDABOUT_EXIT_SHARP_LEFT = 20   // left-down
        const val ROUNDABOUT_EXIT_LEFT = 21         // left-middle
        const val ROUNDABOUT_EXIT_SLIGHT_LEFT = 22  // left-up
        const val ROUNDABOUT_EXIT_STRAIGHT = 23     // centre-up — CONFIRMED
        const val ROUNDABOUT_EXIT_SLIGHT_RIGHT = 24 // right-up
        const val ROUNDABOUT_EXIT_RIGHT = 25        // right-middle
        const val ROUNDABOUT_EXIT_SHARP_RIGHT = 26  // right-down

        const val SLIGHT_LEFT = SLIGHT_LEFT_DIAGONAL

        // N33, owner yes 10 Oct 2026: the official ramp/fork icons, for Maps off_ramp_slight_*.
        // Inferred from the APK, not yet photographed on the cluster.
        const val RAMP_FORK_LEFT = 27
        const val RAMP_FORK_RIGHT = 28

        // Not observed. Mapped to the nearest confirmed icon rather than to a
        // code that draws a blank — a slightly-wrong arrow beats no arrow at
        // all mid-junction.
        const val MERGE = STRAIGHT
        /**
         * The destination marker is code 9 - a ring with a filled centre,
         * confirmed 18 August 2026.
         *
         * It was [STRAIGHT] because it could not be found, and it could not be
         * found because the search was for a chequered flag (what the rider
         * described seeing in the official app). The cluster draws a bullseye.
         */
        const val DESTINATION = 9

        /** Codes at or above this drew nothing on the cluster. */
        const val FIRST_BLANK = 46

        /** Lowest code confirmed to draw anything. */
        /**
         * Lowest code confirmed to draw anything - NOW 1, NOT 31.
         *
         * This was the trap: while it read 31, [drawable] silently rewrote every
         * code below it to [STRAIGHT]. Setting [SLIGHT_LEFT] to 19 or
         * [DESTINATION] to 9 would have done nothing at all - the packet would
         * still have gone out as 40.
         */
        const val FIRST_DRAWABLE = 1

        /** Anything the cluster ignores becomes a straight-ahead arrow. */
        fun drawable(code: Int): Int =
            if (code in FIRST_DRAWABLE until FIRST_BLANK) code else STRAIGHT

        /**
         * Plain-English name for a code, for the ride log.
         *
         * A bare number tells the rider nothing while they are comparing the
         * log against what the dashboard drew — the whole point of the log is
         * that a mismatch should be obvious at a glance.
         */
        /**
         * How much we actually know about a code.
         *
         * Printed into the ride log next to every manoeuvre so a missing arrow
         * can be read correctly: a PREDICTED code drawing nothing is expected,
         * a PHOTOGRAPHED one drawing nothing is a real fault.
         */
        enum class Confidence { PHOTOGRAPHED, PREDICTED, BELIEVED_BLANK }

        /** Codes seen on the rider's own cluster and photographed. */
        private val PHOTOGRAPHED = setOf(
            1, 2, 3, 4, 5, 6, 7, 8, 9,          // junction family, 18 Aug
            15, 16, 17, 18, 19, 23,             // bearings + roundabout, 18 Aug
            31, 32, 33, 34, 35, 36, 37, 38,     // locked set, 11 Aug
            39, 40, 41, 42, 43, 44, 45,
        )

        fun confidence(code: Int): Confidence = when {
            code in PHOTOGRAPHED -> Confidence.PHOTOGRAPHED
            code in 46..53 -> Confidence.BELIEVED_BLANK
            else -> Confidence.PREDICTED
        }

        /**
         * Plain-English name for every code the cluster might be sent.
         *
         * Covers the full 1..58 range rather than only the confirmed ones. The
         * rider's point, 19 August: if an arrow does not appear, the log must
         * still say what was attempted - otherwise there is no way to tell a
         * wrong code from a code that was never going to draw.
         *
         * Names for unphotographed codes come from the APK icon-geometry decode
         * in `Vendor-APK-Reference.md` and are guesses. [confidence] says which.
         */
        fun label(code: Int): String = when (code) {
            // -- junction family, photographed 18 August --
            1 -> "LEFT (junction)"
            2 -> "SLIGHT LEFT (junction)"
            3 -> "CURVING LEFT"
            4 -> "RIGHT (junction)"
            5 -> "SHARP RIGHT (hairpin)"
            6 -> "CURVING RIGHT"
            7 -> "U-TURN (loop)"
            8 -> "STRAIGHT (junction set)"
            9 -> "DESTINATION (bullseye)"
            // -- never swept --
            10 -> "destination flag?"
            11 -> "left at crossroads?"
            12 -> "right at crossroads?"
            13 -> "destination on left?"
            14 -> "destination on right?"
            // -- bearings, photographed 18 August --
            15 -> "DOWN-RIGHT (bearing)"
            16 -> "BACK / DOWN (bearing)"
            17 -> "DOWN-LEFT (bearing)"
            18 -> "LEFT (flat bearing)"
            19 -> "SLIGHT LEFT (up-left diagonal)"
            // -- roundabouts by exit; only 23 photographed --
            20 -> "roundabout exit left-down?"
            21 -> "roundabout exit left?"
            22 -> "roundabout exit left-up?"
            23 -> "ROUNDABOUT (exit straight)"
            24 -> "roundabout exit right-up?"
            25 -> "roundabout exit right?"
            26 -> "roundabout exit right-down?"
            // -- ramps / forks, never swept --
            27 -> "ramp or fork left-up?"
            28 -> "ramp or fork right-up?"
            29 -> "ramp or fork left-down?"
            30 -> "ramp or fork right-down?"
            // -- locked set, photographed 11 August --
            KEEP_LEFT -> "KEEP LEFT (fork)"
            KEEP_RIGHT -> "KEEP RIGHT (fork)"
            CROSSROADS -> "CROSSROADS"
            SHARP_LEFT -> "SHARP LEFT"
            TURN_RIGHT -> "RIGHT"
            SHARP_RIGHT -> "SHARP RIGHT"
            TURN_LEFT -> "LEFT"
            38 -> "LEFT (lane bar)"
            U_TURN -> "U-TURN"
            STRAIGHT -> "STRAIGHT"
            SLIGHT_RIGHT -> "SLIGHT RIGHT"
            TURN_RIGHT_FLAT -> "RIGHT (flat arrow)"
            FERRY -> "FERRY"
            KEEP_LEFT_LANES -> "KEEP LEFT (lanes)"
            ROUNDABOUT -> "ROUNDABOUT"
            // -- believed blank, but only from the discredited 4 Aug sweep --
            in 46..53 -> "believed BLANK (unverified)"
            in 54..58 -> "WEATHER ICON"
            else -> "UNKNOWN($code)"
        }
    }
}

/**
 * Cluster family, as the official app groups models by BLE name.
 * [complementChecksum] also picks the 0x33 layout: the sum families have a
 * weather layout at bytes 21-23, which we do not send yet (unproven).
 */
enum class ClusterFamily(val complementChecksum: Boolean, val tft: Boolean) {
    /** Access, Access 125, Burgman Street (AS..). The development vehicle. */
    ACCESS_LCD(true, false),
    /** Avenis, Burgman Street EX, Burgman Street (BS..). */
    SCOOTER_LCD(false, false),
    /** V-Strom SX, Gixxer family (BM..). */
    MOTORCYCLE(false, false),
    /** Access / Burgman Street TFT editions (CS0., CS1.). */
    TFT(false, true),
    /** e-ACCESS (CE..). */
    ELECTRIC(false, true),
    /** A name the official app does not list: today's Access behaviour. */
    UNKNOWN(true, false),
    /** An unlisted `?CE`/`?CS` name: today's plain-sum behaviour. */
    UNKNOWN_SUM(false, false),
}

/**
 * What differs per model, picked once at connect by [ProtocolEngine.configureForDevice].
 *
 * @param nameCode name characters 1-4, e.g. `AS21`
 * @param model the official model name, empty when unknown
 * @param altLaneIcons the official app's icon choice for its maneuver ids 74
 *   and 58 (see [officialLaneCode]); null where the source is unclear
 */
data class VehicleProfile(
    val nameCode: String,
    val model: String,
    val family: ClusterFamily,
    val altLaneIcons: Boolean?,
) {
    val complementChecksum: Boolean get() = family.complementChecksum

    /** TFT and e-ACCESS: 0x31 byte 9 blank for a leading-zero hour. */
    val tft: Boolean get() = family.tft

    /**
     * Cluster code the official app sends for its maneuver id 74 or 58 on this
     * model; null for any other id or an unproven model (keep today's code).
     * Not used yet: wiring it is rcx-nav's job and needs the owner's yes for the Access.
     */
    fun officialLaneCode(maneuverId: Int): Int? {
        val alt = altLaneIcons ?: return null
        return when (maneuverId) {
            74 -> if (alt) 38 else 44
            58 -> if (alt) 44 else 46
            else -> null
        }
    }
}
