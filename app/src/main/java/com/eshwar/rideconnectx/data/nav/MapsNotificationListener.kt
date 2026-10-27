package com.eshwar.rideconnectx.data.nav

import com.eshwar.rideconnectx.data.local.db.NotificationKind
import android.app.Notification
import android.content.pm.ApplicationInfo
import android.telecom.TelecomManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.eshwar.rideconnectx.core.di.ServiceEntryPoint
import com.eshwar.rideconnectx.data.repository.NavigationRelay
import com.eshwar.rideconnectx.domain.ProtocolEngine
import com.eshwar.rideconnectx.domain.model.NavManeuver
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads Google Maps' ongoing navigation notification and feeds each instruction
 * into [NavigationRelay].
 *
 * Maps has no public API for live turn-by-turn, and its notification is the only
 * supported surface that carries the instruction, the distance to the turn and
 * the remaining ETA. That is what the rider already sees on their lock screen,
 * so nothing here is scraped that they are not already being shown.
 *
 * The notification's wording is not a contract — Google changes it — so parsing
 * is deliberately forgiving: an unrecognised phrase yields a maneuver with a
 * generic icon rather than dropping the update.
 */
class MapsNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "RCX-Nav"

        /**
         * The running listener, so a debug broadcast can reach it.
         *
         * A notification listener is started by the system and cannot be bound
         * to from a receiver, and the arrow catalogue dump has to run inside
         * this service because that is where the rendering code lives.
         */
        @Volatile
        var instance: MapsNotificationListener? = null
            private set

        /** Maps and Maps Go both post the same shaped navigation notification. */
        val MAPS_PACKAGES = setOf(
            "com.google.android.apps.maps",
            "com.google.android.apps.mapslite",
        )

        /**
         * Apps whose notifications light the cluster's message lamp.
         *
         * The cluster has one indicator, not a per-app inbox, so this only has
         * to answer "is there something waiting". SMS is included because the
         * official app treats WhatsApp and SMS the same way.
         */
        val MESSAGE_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.google.android.apps.messaging",
        )

        /** Notification categories that mean a call rather than a message. */
        private val CALL_CATEGORIES = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_MISSED_CALL,
        )

        /**
         * Telephony packages whose call category is believed even when they are
         * not the current default dialer — the in-call UI and the telecom
         * service post under their own names on several OEM builds.
         *
         * A name in this set is **not** sufficient on its own. `com.android.*`
         * is a plain application id that nothing reserves: on a retail phone
         * that ships `com.google.android.dialer`, any app can be built with
         * `applicationId "com.android.dialer"`, install without conflict, and
         * inherit this trust. Membership here is only checked together with the
         * system flag — see [isTrustedCallSource].
         */
        private val TELECOM_PACKAGES = setOf(
            "com.android.server.telecom",
            "com.android.dialer",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
        )

        /**
         * Apps that place calls the rider cares about and are already trusted
         * for messages. Without these, restricting calls to telephony silently
         * drops every VoIP call — which is most calls, for many riders.
         */
        private val VOIP_CALL_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
            "com.google.android.apps.tachyon",
        )
    }

    /** Fetched rather than field-injected — see [ServiceEntryPoint]. */
    private val relay: NavigationRelay
        get() = EntryPointAccessors
            .fromApplication(applicationContext, ServiceEntryPoint::class.java)
            .navigationRelay()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Identifies the manoeuvre arrow. Built lazily on first navigation. */
    private val matcher by lazy { ArrowMatcher(applicationContext) }

    init { instance = this }

    /** One alert per distinct change of a notification (N31). */
    private val alertDedup = AlertDedup()
    private val missedTracker = MissedCallTracker()
    private val pendingCallClear = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    private fun callKey(sbn: StatusBarNotification) =
        "call|${sbn.packageName}|${callTitle(sbn)}"

    /** Caller as one stable string: the dialer wraps it in direction marks on the first post only. */
    private fun callTitle(sbn: StatusBarNotification) =
        cleanStoredText(sbn.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())

    /**
     * Maps' own resources, held open so the icon name lookup is not a package
     * manager round-trip several times a second for a whole ride.
     */
    private var mapsResources: android.content.res.Resources? = null

    /**
     * Turns a drawable id from another app into its resource name.
     *
     * The id itself is meaningless across Maps versions - it is an index into
     * their resource table and shifts on every release. The *name* does not, so
     * that is what gets recorded and what any future mapping is keyed on.
     *
     * Best-effort: an app can ship stripped resource names, and a lookup that
     * fails must never interrupt navigation.
     */
    /**
     * A picture reduced to two fingerprints.
     *
     * [exact] is every pixel's alpha, and [shape] is a coarse 16x16 on/off
     * grid. Two of them because an exact hash is only useful if Maps redraws
     * the arrow identically every time - and if it anti-aliases, or overlays a
     * countdown ring, the exact hash changes every frame and says nothing. The
     * coarse grid survives that, because a left arrow still covers the same
     * squares. Whichever proves stable across a route is the one we key on;
     * recording both means one run answers that rather than two.
     */
    private data class IconSig(val exact: String, val shape: String, val note: String)

    private fun iconSignature(icon: android.graphics.drawable.Icon?): IconSig {
        if (icon == null) return IconSig("", "", "none")

        val type = runCatching { icon.type }.getOrNull()
        if (type == android.graphics.drawable.Icon.TYPE_RESOURCE) {
            val id = runCatching { icon.resId }.getOrDefault(0)
            val pkg = runCatching { icon.resPackage }.getOrNull().orEmpty()
            val name = resolveIconName(pkg.ifBlank { MAPS_PACKAGES.first() }, id)
            return IconSig(name.ifBlank { "res$id" }, name.ifBlank { "res$id" }, "resource $name")
        }

        return runCatching {
            val drawable = icon.loadDrawable(this)
                ?: return@runCatching IconSig("", "", "unrenderable")
            val n = 32
            val bmp = android.graphics.Bitmap.createBitmap(
                n, n, android.graphics.Bitmap.Config.ARGB_8888,
            )
            android.graphics.Canvas(bmp).also {
                drawable.setBounds(0, 0, n, n)
                drawable.draw(it)
            }
            val px = IntArray(n * n)
            bmp.getPixels(px, 0, n, 0, 0, n, n)
            bmp.recycle()

            // Alpha only. Maps tints the arrow white on a coloured card, so the
            // shape lives in the alpha channel and the colour is noise.
            var fine = 17
            for (p in px) fine = fine * 31 + (p ushr 24)

            // Coarse: 16x16 cells, each on if its 2x2 block is mostly opaque.
            val bits = StringBuilder()
            for (cy in 0 until 16) for (cx in 0 until 16) {
                var sum = 0
                for (dy in 0..1) for (dx in 0..1) {
                    sum += px[(cy * 2 + dy) * n + (cx * 2 + dx)] ushr 24
                }
                bits.append(if (sum / 4 > 128) '1' else '0')
            }
            val shape = bits.toString().chunked(4)
                .joinToString("") { Integer.toHexString(it.toInt(2)) }

            IconSig(Integer.toHexString(fine), shape, "bitmap ${icon.type}")
        }.getOrDefault(IconSig("", "", "sig-failed"))
    }

    /**
     * Whether the display is on, for the stall investigation.
     *
     * `isInteractive` rather than the deprecated `isScreenOn`: it reports the
     * state that governs whether apps are allowed to keep updating, which is
     * the thing under suspicion.
     */
    private fun isScreenOn(): Boolean = runCatching {
        (getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager)
            .isInteractive
    }.getOrDefault(true)

    private fun resolveIconName(pkg: String, resId: Int): String {
        if (resId == 0) return ""
        return runCatching {
            val res = mapsResources
                ?: packageManager.getResourcesForApplication(pkg).also { mapsResources = it }
            res.getResourceEntryName(resId)
        }.getOrDefault("")
    }

    /** The listener is gone, so no removal will ever arrive: nothing may stay "ringing". */
    private fun endAllCalls() {
        pendingCallClear.values.forEach { it.cancel() }
        pendingCallClear.clear()
        ClusterAlerts.clearAllRinging()
    }

    override fun onDestroy() {
        endAllCalls()
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in MAPS_PACKAGES) {
            relayClusterAlert(sbn)
            return
        }

        // Any Maps post proves Maps notifications are on (the "look off" card hides).
        runCatching {
            EntryPointAccessors.fromApplication(applicationContext, NavHistoryEntryPoint::class.java)
                .mapsOffTracker().onMapsPost()
        }

        val extras = sbn.notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()

        // One compact line rather than a dump of every field. The full dump was
        // used on 6 August to establish the layout — title is the distance, text
        // is the instruction, subText the journey — and it is recorded in the
        // knowledge base. Keeping the three fields that matter is enough to spot
        // a future Maps release moving them again, without flooding logcat
        // several times a second for a whole ride.
        // The maneuver ICON, which is where the direction actually lives.
        //
        // The 19 August ride proved the text does not carry it. Maps puts the
        // road you are turning ONTO in `text` and counts the distance down to
        // the turn - "Neredmet - Sainikpur X Rd  400 m" means "in 400 m turn
        // onto Neredmet Rd", with no left or right anywhere in the words. Only
        // 5 of ~40 turns that ride said "Turn left"/"Turn right" outright, so
        // 1,719 of 1,982 frames went out as STRAIGHT (code 40) - which the
        // rider saw on the dashboard, and which code 40 is photographed to draw.
        //
        // The arrow the rider sees in the notification shade is the small icon,
        // so its resource name is the direction. Recorded, not yet acted on:
        // the ids are Maps' own and have to be observed before they can be
        // mapped, the same rule the cluster codes were established under.
        // The LARGE icon is the manoeuvre arrow - the one the rider can see in
        // their own screenshot of the shade, drawn on the right of the card.
        // The small icon is Maps' logo and is the same on every frame; reading
        // only that is what made two rides report a single generic name.
        val arrow = runCatching {
            iconSignature(extras.getParcelable(Notification.EXTRA_LARGE_ICON))
        }.getOrDefault(IconSig("", "", "failed"))

        // Identify the arrow against Maps' own catalogue. This is what supplies
        // the direction on the ~88% of frames whose text carries none.
        val arrowMatch = runCatching {
            val icon = extras.getParcelable<android.graphics.drawable.Icon>(
                Notification.EXTRA_LARGE_ICON,
            )
            icon?.loadDrawable(this)?.let { matcher.match(it) }
        }.getOrNull()

        // The log shows the identified name where it used to show a bare
        // fingerprint, so a wrong arrow is readable without decoding hex.
        val iconName = arrowMatch?.let { "${it.name} (±${it.distance})" }
            ?: arrow.shape.ifBlank { "" }
        val iconId = runCatching { sbn.notification.smallIcon?.resId ?: 0 }.getOrDefault(0)

        Log.d(TAG, "RAW title='$title' | text='$text' | subText='$subText' | icon=$iconName ($iconId)")
        // Also to the ride log - logcat does not survive a 90 minute ride, and
        // the 19 August ride showed the arrival case is only visible here.
        runCatching {
            EntryPointAccessors
                .fromApplication(applicationContext, ServiceEntryPoint::class.java)
                .rideLog()
                .raw(title, text, subText, iconName, iconId,
                    "${extras.getInt(Notification.EXTRA_PROGRESS, 0)}/${extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)}")
        }

        // Format-change watch: key names and value types only, never values (privacy).
        val shape = extras.keySet().sorted().joinToString(",") { k ->
            @Suppress("DEPRECATION")
            val type = extras.get(k)?.javaClass?.simpleName
            "$k:$type"
        }
        if (frameWatch.shape(shape)) diagLog("MAPS SHAPE NEW: $shape")

        // Android 16 "live update" Maps (ProgressStyle, Samsung S23 FE, 22 Oct 2026): see
        // MapsNotificationParser.liveUpdateFields. Old-layout frames come back unchanged.
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val live = MapsNotificationParser.liveUpdateFields(
            title, text, subText,
            progress = extras.getInt(Notification.EXTRA_PROGRESS, 0),
            progressMax = progressMax,
            chipTexts = listOf(
                "android.ongoingActivityNoti.primaryInfo",
                "android.ongoingActivityNoti.chipExpandedText",
                "android.shortCriticalText",
            ).map { extras.getCharSequence(it)?.toString().orEmpty() },
        )
        val navTitle = live?.title ?: title
        val navText = live?.text ?: text
        val navSubText = live?.subText ?: subText

        if (MapsNotificationParser.isStarting(title)) return
        if (navTitle.isBlank() && navText.isBlank()) {
            frameWatch.parsed(false)?.let { n -> diagLog("MAPS UNREADABLE: $n frames in a row; empty title and text") }
            return
        }

        // Maps is working out a new route (rider left the planned one). The
        // cluster has no reroute icon, so the relay shows a "working" frame
        // until the next real instruction arrives.
        if (MapsNotificationParser.isReroute("$navTitle $navText")) {
            Log.d(TAG, "Reroute: title='$navTitle' text='$navText'")
            relay.submitReroute("$navTitle | $navText")
            return
        }

        val parsed = MapsNotificationParser.parse(
            navTitle, navText, navSubText, iconName, arrowMatch?.code,
            turnUnknownOk = live != null && live.title.isBlank(),
        )
        frameWatch.parsed(parsed != null)?.let { n ->
            diagLog("MAPS UNREADABLE: $n frames in a row; titleBlank=${navTitle.isBlank()} textBlank=${navText.isBlank()} progressMax>0=${progressMax > 0}")
        }
        if (parsed == null) {
            Log.v(TAG, "Maps notification ignored: title='$title' text='$text'")
            return
        }
        // Read here rather than in the relay: the rider's report that the screen
        // being off stalls navigation has to be measured at the moment Maps
        // posts, not whenever the packet happens to be built.
        val maneuver = parsed.copy(screenOn = isScreenOn(), postedAt = sbn.postTime)

        Log.d(TAG, "Maneuver: ${maneuver.instruction} in ${maneuver.distanceToTurn}")
        // Queued, not launched: one coroutine per notification let an older frame
        // overtake a newer one on the way to the scooter (the 30 Sep ride log has
        // two frames written at once). The relay sends in order, newest wins.
        relay.submit(maneuver)
    }

    /**
     * Lights the cluster's message or missed-call lamp for a non-Maps
     * notification.
     *
     * Only WhatsApp/SMS and call notifications count. Ongoing notifications are
     * skipped — a music player or a running download is not something the rider
     * needs flagged mid-ride, and it would keep the lamp permanently lit.
     */
    private fun relayClusterAlert(sbn: StatusBarNotification) {
        val category = sbn.notification?.category
        val isCall = category in CALL_CATEGORIES

        // Every non-Maps notification is logged at verbose, because when a lamp
        // does not light the first question is always 'did the app even see it',
        // and without this there is no way to tell a missed notification from a
        // rejected one. The rider hit exactly that on 18 August.
        Log.v(
            TAG,
            "alert? pkg=${sbn.packageName} category=$category ongoing=${sbn.isOngoing} isCall=$isCall",
        )

        // Ongoing notifications are skipped for MESSAGES only. A music player or
        // a running download must not pin the lamp on.
        //
        // Calls are deliberately exempt: an *incoming* call posts an ongoing
        // notification while it rings, so the old blanket `if (sbn.isOngoing)
        // return` at the top of this function threw away every incoming call
        // before the category was even looked at. That is why the call lamp
        // never lit during the 18 August test.
        // The 30 Sep ride: a friend's call never showed on the cluster, and
        // nothing recorded whether the app even saw it.
        if (isCall && !isTrustedCallSource(sbn.packageName)) {
            logAlert("CALL notification from ${sbn.packageName} IGNORED (not a trusted dialer)")
        }
        when {
            isCall && isTrustedCallSource(sbn.packageName) -> {
                val who = callTitle(sbn)
                if (category == Notification.CATEGORY_MISSED_CALL) {
                    // Not ringing any more: a stale ring entry would repeat 0x32 forever.
                    ClusterAlerts.clearRinging(who)
                    ClusterAlerts.clearMissedCall()
                    if (missedTracker.onMissedPost(callKey(sbn), System.currentTimeMillis())) {
                        ClusterAlerts.onCallMissed(
                            (ClusterAlerts.missedCount.value + 1).coerceAtMost(99), who,
                            sbn.packageName.startsWith("com.whatsapp"),
                        )
                        recordHistory(NotificationKind.CALL, who, "Missed call")
                    }
                    return
                }
                // An answered call is still a call-category notification
                // (CallStyle ongoing = 2): do not light the lamp for it. Phone
                // state would need READ_PHONE_STATE, so the notification stays
                // the source (CHK-32).
                // Samsung's in-call UI posts a RINGING call as callType 2 too ("Incoming call",
                // hang-up action only); only the ringing one carries a full-screen intent
                // (seen on a Samsung S23 FE, 22 Oct 2026).
                val callType = sbn.notification.extras?.getInt("android.callType", 0) ?: 0
                if (isAnsweredCall(callType, sbn.notification.fullScreenIntent != null)) {
                    missedTracker.onAnswered(callKey(sbn))
                    ClusterAlerts.onCallEnded(who, ClusterAlerts.CallEnd.ANSWERED)
                    return
                }
                missedTracker.onRinging(callKey(sbn), System.currentTimeMillis())
                // Keyed by caller, not sbn.key: the dialer re-posts under new ids.
                pendingCallClear.remove(callKey(sbn))?.cancel()
                if (!alertDedup.shouldSend(callKey(sbn), "$category|$who", System.currentTimeMillis(), isCall = true)) return
                Log.d(TAG, "Call from ${sbn.packageName} who='$who' - flagging cluster")
                logAlert("CALL from $who (${sbn.packageName}) - seen (see ALERT WRITTEN or ALERT DROPPED for delivery)")
                ClusterAlerts.onCallRinging(who, sbn.packageName.startsWith("com.whatsapp"))
                ClusterAlerts.onMissedCall(title = who)
                recordHistory(NotificationKind.CALL, who, "Call")
            }
            sbn.isOngoing -> return
            // WhatsApp and Messages post a summary beside each chat: counting both doubled the unread count.
            isGroupSummary(sbn.notification?.flags ?: 0) -> return
            sbn.packageName in MESSAGE_PACKAGES -> {
                val extras = sbn.notification.extras
                val who = cleanStoredText(extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
                val body = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                // 'W' WhatsApp, 'N' SMS - the identifiers the official app uses
                // in the 0x06 packet.
                val app = if (sbn.packageName.startsWith("com.whatsapp")) 'W' else 'N'
                if (!alertDedup.shouldSend(sbn.key, "$who|$body", System.currentTimeMillis(), isCall = false)) return
                Log.d(TAG, "Message from ${sbn.packageName} who='$who' - flagging cluster")
                logAlert("MESSAGE from $who (${sbn.packageName}) - seen (see ALERT WRITTEN or ALERT DROPPED for delivery)")
                ClusterAlerts.onNotification(appIdentifier = app, title = who, text = body)
                recordHistory(NotificationKind.MESSAGE, who, "New message")
            }
        }
    }

    /**
     * Whether a call-category notification is believed.
     *
     * Android lets *any* app set [Notification.CATEGORY_CALL], and the call
     * branch above passes the notification's own title straight to the cluster
     * as a caller name. Without this check any installed app could print 26
     * characters of its choosing on the dashboard and light the missed-call
     * lamp — the message branch has always had an allowlist, the call branch
     * had none.
     *
     * The default dialer is read fresh each time rather than cached: the rider
     * can change it, and a stale answer here silently drops real calls.
     */
    private fun isTrustedCallSource(pkg: String): Boolean {
        // The current default dialer is the rider's own choice, recorded by the
        // OS. This is the primary rule.
        val dialer = runCatching {
            getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()
        if (pkg == dialer) return true

        // A telephony name is believed only if the OS also says that package is
        // part of the system image. Checking the name alone would let any app
        // claim `com.android.dialer` on a phone that does not already ship it.
        if (pkg in TELECOM_PACKAGES && isSystemPackage(pkg)) return true

        if (pkg in VOIP_CALL_PACKAGES || pkg in MESSAGE_PACKAGES) return true

        Log.d(TAG, "Ignoring call-category notification from untrusted pkg=$pkg")
        return false
    }

    /** One row on the Notifications page. Sender and a fixed body only, never the message text (privacy). */
    private fun recordHistory(kind: NotificationKind, who: String, body: String) {
        scope.launch {
            runCatching {
                EntryPointAccessors.fromApplication(applicationContext, NavHistoryEntryPoint::class.java)
                    .notificationRepository().notify(kind, cleanStoredText(who).ifBlank { "Unknown" }, cleanStoredText(body))
            }
        }
    }

    private val frameWatch = MapsFrameWatch()

    private fun diagLog(line: String) {
        runCatching {
            EntryPointAccessors.fromApplication(applicationContext, ServiceEntryPoint::class.java)
                .rideLog().diag(line)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        ListenerWatchdog.onConnected(this)
        // Needs a <queries> entry for Maps on Android 11+ (rcx-build); null = not visible or not installed.
        val ver = MAPS_PACKAGES.joinToString { p ->
            "$p=" + (runCatching { packageManager.getPackageInfo(p, 0).versionName }.getOrNull() ?: "unknown")
        }
        diagLog("MAPS VERSION: $ver")
    }

    private fun logAlert(line: String) {
        runCatching {
            EntryPointAccessors.fromApplication(applicationContext, ServiceEntryPoint::class.java)
                .rideLog().alert(line)
        }
    }

    /** True only for a package on the system image or a system-signed update. */
    private fun isSystemPackage(pkg: String): Boolean = runCatching {
        val flags = packageManager.getApplicationInfo(pkg, 0).flags
        (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
    }.getOrDefault(false)
    /**
     * Logs why one of the app's own notifications was removed (N13: reminders
     * vanished into history). REASON_ERROR (4) means SystemUI could not draw
     * it; REASON_APP_CANCEL (8) means this app cancelled it.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (sbn.packageName == packageName) Log.d(TAG, "Own notification id=${sbn.id} removed, reason=$reason")
        super.onNotificationRemoved(sbn, rankingMap, reason)
    }

    override fun onListenerDisconnected() {
        // Nobody is reading Maps any more: stop GPS and blank the cluster.
        ListenerWatchdog.onDisconnected(this)
        relay.stop()
        endAllCalls()
        super.onListenerDisconnected()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName !in MAPS_PACKAGES) {
            // The call ended (ringing notification gone): end its lamp now.
            // A missed-call notification is posted after this, so it re-lights.
            val category = sbn.notification?.category
            if (category == Notification.CATEGORY_CALL) {
                // Wait: a cancel + re-post of a still-ringing call must not blank the lamp.
                val k = callKey(sbn)
                val who = callTitle(sbn)
                val whatsApp = sbn.packageName.startsWith("com.whatsapp")
                val endedAt = System.currentTimeMillis()
                alertDedup.forget(k)
                pendingCallClear.remove(k)?.cancel()
                pendingCallClear[k] = scope.launch {
                    kotlinx.coroutines.delay(3_000)
                    pendingCallClear.remove(k)
                    ClusterAlerts.clearMissedCall()
                    // Decided after the 3 s wait, so a cancel + re-post burst never reaches here twice.
                    // Ring length is measured to the removal, not to the end of the 3 s wait.
                    val outcome = missedTracker.resolve(k, endedAt)
                    // Always end the ring entry, even when the tracker never saw this key.
                    ClusterAlerts.onCallEnded(who, outcome ?: ClusterAlerts.CallEnd.REJECTED)
                    if (outcome == ClusterAlerts.CallEnd.MISSED) {
                        ClusterAlerts.onCallMissed((ClusterAlerts.missedCount.value + 1).coerceAtMost(99), who, whatsApp)
                        recordHistory(NotificationKind.CALL, who, "Missed call")
                    }
                }
            } else if (category == Notification.CATEGORY_MISSED_CALL && isTrustedCallSource(sbn.packageName)) {
                // Official m9116f: missed-call notification gone -> count 0 and a 0x34 reset.
                ClusterAlerts.onMissedNotificationRemoved()
            } else if (sbn.packageName in MESSAGE_PACKAGES && !sbn.isOngoing) {
                // Official m9120l: recount the phone's active message notifications.
                val wa = sbn.packageName.startsWith("com.whatsapp")
                val active = runCatching { activeNotifications }.getOrNull().orEmpty()
                    .filter { it.key != sbn.key && it.packageName in MESSAGE_PACKAGES && !it.isOngoing && !isGroupSummary(it.notification?.flags ?: 0) }
                val own = active.count { it.packageName.startsWith("com.whatsapp") == wa }
                ClusterAlerts.onMessagesRecounted(if (wa) 'W' else 'N', own, active.size)
            }
            return
        }
        // Maps clearing its notification means the route ended or was cancelled.
        Log.d(TAG, "Maps navigation notification removed — stopping relay")
        relay.stop()
    }
}

/**
 * Turns the text of a Maps navigation notification into a [NavManeuver].
 *
 * Kept separate from the service so it can be reasoned about — and tested —
 * without an Android notification in hand.
 */
object MapsNotificationParser {

    /** "400 m", "1.2 km", "250 ft", "0.5 mi" — the distance to the next turn. */
    private val DISTANCE = Regex(
        """(\d+(?:[.,]\d+)?)\s*(km|m|mi|ft|yd)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** "12 min", "1 hr 5 min" — remaining time. */
    private val ETA_MINUTES = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)
    /** "11:07 am ETA" / "14:53 ETA" — the arrival clock Maps shows. */
    private val ETA_CLOCK = Regex("""(\d{1,2}):(\d{2})[\s  ]*([ap]\.?m\.?)?[\s  ]*ETA""", RegexOption.IGNORE_CASE)
    /** Android 16 live-update Maps writes "Arrive 5:01 pm" instead of "5:01 pm ETA"; same groups. */
    private val ARRIVE_CLOCK = Regex("""Arriv\w*[\s  ]+(\d{1,2}):(\d{2})[\s  ]*([ap]\.?m\.?)?""", RegexOption.IGNORE_CASE)
    private val ETA_HOURS = Regex("""(\d+)\s*(?:hr|hour)""", RegexOption.IGNORE_CASE)

    /**
     * Ordered longest-phrase-first: "slight left" has to be tested before
     * "left", or every slight turn would be reported as a plain one.
     */
    // `[\s-]+` rather than `\s+` throughout: the qualifier and the direction
    // are sometimes joined by a hyphen rather than a space, and losing the
    // qualifier turns a slight turn into a hard one on the cluster.
    private val MANEUVERS: List<Pair<Regex, Int>> = listOf(
        Regex("""u[- ]?turn""", RegexOption.IGNORE_CASE) to ProtocolEngine.Maneuver.U_TURN,
        Regex("""sharp[\s-]+left""", RegexOption.IGNORE_CASE) to ProtocolEngine.Maneuver.SHARP_LEFT,
        Regex("""sharp[\s-]+right""", RegexOption.IGNORE_CASE) to ProtocolEngine.Maneuver.SHARP_RIGHT,
        Regex("""slight[\s-]+left""", RegexOption.IGNORE_CASE) to ProtocolEngine.Maneuver.SLIGHT_LEFT,
        Regex("""slight[\s-]+right""", RegexOption.IGNORE_CASE) to ProtocolEngine.Maneuver.SLIGHT_RIGHT,
        Regex("""keep[\s-]+left""", RegexOption.IGNORE_CASE) to ProtocolEngine.Maneuver.KEEP_LEFT,
        Regex("""keep[\s-]+right""", RegexOption.IGNORE_CASE) to ProtocolEngine.Maneuver.KEEP_RIGHT,
        Regex("""roundabout|rotary""", RegexOption.IGNORE_CASE) to ProtocolEngine.Maneuver.ROUNDABOUT,
        Regex("""merge""", RegexOption.IGNORE_CASE) to ProtocolEngine.Maneuver.MERGE,
        Regex("""arriv|destination""", RegexOption.IGNORE_CASE) to ProtocolEngine.Maneuver.DESTINATION,
        Regex("""\bleft\b""", RegexOption.IGNORE_CASE) to ProtocolEngine.Maneuver.TURN_LEFT,
        Regex("""\bright\b""", RegexOption.IGNORE_CASE) to ProtocolEngine.Maneuver.TURN_RIGHT,
    )

    /**
     * Captured from Google Maps on the rider's phone, 6 August 2026:
     *
     * ```
     * title   = "0 m"                                 ← distance to the turn
     * text    = "Head north"                          ← the instruction
     * subText = "1 hr 12 min · 38 km · 1:38 am ETA"   ← duration, remaining, ETA
     * ```
     *
     * This is the **opposite** of what the code assumed for months: it read the
     * title as the instruction, so it searched "350 m" for the word "left" and
     * of course never found one. A whole ride relayed nothing but straight-ahead
     * because of it, and the ETA was read from the wrong field too.
     *
     * Field roles are now fixed to what the device actually sends. `bigText`,
     * `infoText`, `summaryText`, `titleBig` and `tickerText` were all null, so
     * there is nothing else to fall back on.
     */
    /**
     * Reads a maneuver out of an icon's resource name.
     *
     * Maps names its drawables after what they draw - `ic_maneuver_turn_left`,
     * `ic_maneuver_roundabout_right` and so on - so the same phrase table that
     * reads the text reads these too, once the underscores are spaces. That is
     * why this is a normalisation and not a second hand-written table: one
     * table cannot drift out of step with itself.
     *
     * Returns null when the name matches nothing, which is also the answer for
     * a Maps build that ships stripped resource names.
     */
    fun maneuverFromIconName(iconName: String): Int? {
        if (iconName.isBlank()) return null
        val words = iconName.replace('_', ' ').replace('-', ' ')
        return MANEUVERS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(words) }?.second
    }

    /**
     * Maps' wording while it recalculates. ponytail: English phrases only,
     * collected from the ride log's RAW lines; add a phrase when a ride shows
     * another one.
     */
    fun isReroute(s: String): Boolean {
        val t = s.lowercase()
        return REROUTE_PHRASES.any { it in t }
    }

    /** "Starting navigation..." (N99): no turn yet, so no frame; the first "Head ..." post starts the route. */
    fun isStarting(s: String): Boolean = s.trim().lowercase().startsWith("starting navigation")

    private val REROUTE_PHRASES = listOf(
        "rerouting", "re-routing", "recalculating", "finding a new route",
        "finding a better route", "new route", "off route",
    )

    /**
     * Metres left of the whole journey, read from Maps' subText.
     *
     * `"0 min · 40 m · 6:12 pm ETA"` -> 40. The remaining figure is always the
     * last distance in that line, which is what tells us the rider has arrived.
     */
    private fun remainingMetres(subText: String): Int? {
        val last = DISTANCE.findAll(subText).map { it.value }.lastOrNull() ?: return null
        return distanceToMetres(last)
    }

    /** The three fields as the old layout would have carried them. */
    data class LiveFields(val title: String, val text: String, val subText: String)

    private val DISTANCE_ONLY = Regex("""\s*\d+(?:[.,]\d+)?\s*(?:km|m|mi|ft|yd)\s*""", RegexOption.IGNORE_CASE)
    private val DISTANCE_DOT_TEXT = Regex(
        """\s*(\d+(?:[.,]\d+)?[\s  ]*(?:km|m|mi|ft|yd))[\s  ]*[·•][\s  ]*(.+)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Android 16 live-update Maps: text is blank and the title is the instruction, either
     * "Turn left" or "120 m · Turn left"; progressMax - progress is the metres left of the
     * route (25846 for a 26 km route). Returns the old layout (title = distance, text =
     * instruction, subText ends with the remaining km), or null when this is not a live-update frame.
     * Seen in the owner's ride log 4 (Samsung, Android 16). No distance anywhere (first frame,
     * "Head north", Xiaomi) gives title "" = unknown, never "0 m"; see [parseLive].
     * A chip is trusted only as a pure distance; the third key (shortCriticalText) also not
     * as a bare "m", which could be minutes. Guess: no capture shows Google doing that.
     */
    fun liveUpdateFields(
        title: String, text: String, subText: String,
        progress: Int, progressMax: Int, chipTexts: List<String> = emptyList(),
    ): LiveFields? {
        if (text.isNotBlank() || title.isBlank() || DISTANCE_ONLY.matches(title)) return null
        val split = DISTANCE_DOT_TEXT.matchEntire(title)
        if (progressMax <= 0 && split == null) return null
        val distance = split?.groupValues?.get(1)
            ?: chipTexts.withIndex().firstOrNull { (i, c) ->
                DISTANCE_ONLY.matches(c) && (i != SHORT_CRITICAL_INDEX || !BARE_METRES.matches(c))
            }?.value?.trim()
            ?: ""
        val left = (progressMax - progress).coerceAtLeast(0)
        val remaining = when {
            progressMax <= 0 || progress < 0 || progress > progressMax -> ""
            left >= 1000 -> "%.1f km".format(java.util.Locale.US, left / 1000f)
            else -> "$left m"
        }
        return LiveFields(
            title = distance,
            text = split?.groupValues?.get(2)?.trim() ?: title,
            subText = listOf(subText.trim(), remaining).filter { it.isNotBlank() }.joinToString(" · "),
        )
    }

    private const val SHORT_CRITICAL_INDEX = 2
    private val BARE_METRES = Regex("""\s*\d+(?:[.,]\d+)?\s*m\s*""", RegexOption.IGNORE_CASE)

    /** Parses a live-update frame; an unknown turn distance stays unknown ("" in distanceToTurn). */
    fun parseLive(f: LiveFields, iconName: String = "", iconCode: Int? = null): NavManeuver? =
        parse(f.title, f.text, f.subText, iconName, iconCode, turnUnknownOk = f.title.isBlank())

    /** "400 m" / "1,2 km" / "0.5 mi" / "50 ft" to metres; null when there is no distance. */
    internal fun distanceToMetres(text: String): Int? {
        val m = DISTANCE.find(text) ?: return null
        val value = m.groupValues[1].replace(',', '.').toFloatOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "km" -> value * 1000
            "mi" -> value * 1609.34f
            "ft" -> value * 0.3048f
            "yd" -> value * 0.9144f
            else -> value
        }.toInt()
    }

    /**
     * How close the destination has to be before a distance-less frame counts
     * as arrival rather than a notification we simply cannot read.
     */
    private const val ARRIVAL_METRES = 60

    fun parse(
        title: String,
        text: String,
        subText: String = "",
        iconName: String = "",
        iconCode: Int? = null,
        turnUnknownOk: Boolean = false,
    ): NavManeuver? {
        // An alert ("Accident-prone area for the next 500 m") has words in the title and no text:
        // not a turn. Real frames have the instruction in text, or a distance-only title.
        if (text.isBlank() && !DISTANCE_ONLY.matches(title)) return null

        // The instruction lives in `text`. Keeping title in the haystack costs
        // nothing and covers a Maps version that swaps them back.
        val haystack = "$text $title"

        // Maps posts other notifications too (traffic, offers, "you're offline").
        // A navigation one always states a distance to the next turn.
        val distance = DISTANCE.find(title)?.value
            ?: DISTANCE.find(text)?.value
            // No distance anywhere, but the destination is metres away: this is
            // the arrival frame. Maps drops the next-turn distance because there
            // is no next turn, and puts the destination's NAME in the text -
            // "SRI DEVI RESIDENCY", "Ramani Nilayam". No regex can match an
            // arbitrary place name, which is why arrival went undetected for
            // every ride until 20 August; the remaining distance identifies it
            // without needing to.
            //
            // Maps ships `maneuver_destination` but does not switch the
            // notification to it - it keeps the previous arrow - so the picture
            // cannot be used here either.
            ?: run {
                val left = remainingMetres(subText)
                if (left != null && left <= ARRIVAL_METRES && text.isNotBlank()) {
                    return NavManeuver(
                        maneuverId = ProtocolEngine.Maneuver.DESTINATION,
                        instruction = text.trim(),
                        distanceToTurn = "$left m",
                        remainingDistance = "$left m",
                        phraseRecognised = true,
                        iconName = iconName,
                        codeSource = NavManeuver.Source.ICON,
                    )
                }
                null
            }
            ?: (if (turnUnknownOk && text.isNotBlank()) "" else return null)

        val matched = MANEUVERS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(haystack) }
            ?.second

        // The ARROW wins when it was identified, and the text is the fallback.
        //
        // That order is the opposite of what it was, and it is deliberate. The
        // arrow is what Maps actually draws for the rider, and it distinguishes
        // slight from normal from sharp, keep from fork, and every roundabout -
        // distinctions the text usually omits. The text only ever carries a
        // direction on about 12% of frames, and on those two the arrow agreed.
        //
        // A match is only accepted inside ArrowMatcher.MAX_DISTANCE, so an arrow
        // Maps has newly introduced falls through to the text rather than being
        // forced onto the nearest old shape. If neither speaks, STRAIGHT - the
        // same behaviour as before, so this can never be worse than it was.
        val maneuverId = iconCode
            ?: matched
            ?: ProtocolEngine.Maneuver.STRAIGHT
        val codeSource = when {
            iconCode != null -> NavManeuver.Source.ICON
            matched != null -> NavManeuver.Source.TEXT
            else -> NavManeuver.Source.FALLBACK
        }

        // Never the distance: "350 m" told the rider nothing and made the ride
        // log unreadable.
        val instruction = text.trim().ifBlank { title.trim() }

        // "1 hr 12 min · 38 km · 1:38 am ETA" — everything about the rest of the
        // journey is in subText, so that is where both of these come from.
        val journey = subText.ifBlank { text }

        return NavManeuver(
            maneuverId = maneuverId,
            instruction = instruction,
            distanceToTurn = distance.trim(),
            etaMinutes = parseEtaMinutes(journey),
            etaClock = parseEtaClock(journey),
            remainingDistance = DISTANCE.findAll(journey).map { it.value }.lastOrNull()
                ?.trim().orEmpty(),
            phraseRecognised = matched != null,
            iconName = iconName,
            iconManeuverId = iconCode,
            codeSource = codeSource,
        )
    }

    /**
     * The 30 Sep ride: working the clock out as now + minutes put the cluster a
     * minute off Maps on a quarter of frames, so Maps' own figure is used.
     */
    internal fun parseEtaClock(source: String): String? {
        val m = ETA_CLOCK.find(source) ?: ARRIVE_CLOCK.find(source) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2]
        val ampm = m.groupValues[3].replace(".", "").uppercase().ifBlank {
            (if (hour >= 12) "PM" else "AM").also { hour = (hour + 11) % 12 + 1 }
        }
        return "%02d%s%s".format(hour, minute, ampm)
    }

    private fun parseEtaMinutes(source: String): Int? {
        val hours = ETA_HOURS.find(source)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minutes = ETA_MINUTES.find(source)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = hours * 60 + minutes
        return total.takeIf { it > 0 }
    }
}

/** CallStyle ongoing (2) is an answered call, unless it carries a full-screen intent: Samsung's ringing one does. */
internal fun isAnsweredCall(callType: Int, hasFullScreenIntent: Boolean): Boolean =
    callType == 2 && !hasFullScreenIntent

/** Notification.FLAG_GROUP_SUMMARY: the bundle header, not a message of its own. */
internal fun isGroupSummary(flags: Int): Boolean = flags and Notification.FLAG_GROUP_SUMMARY != 0

/** Own entry point so this stays inside data/nav (ServiceEntryPoint is in core/di). */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface NavHistoryEntryPoint {
    fun notificationRepository(): com.eshwar.rideconnectx.data.repository.NotificationRepository
    fun mapsOffTracker(): com.eshwar.rideconnectx.data.local.MapsOffTracker
}

/**
 * Spots a changed Maps notification layout (extras key/type set) and a run of frames that cannot
 * be read. Pure, so it is tested without Android. Both answers are "log once".
 */
internal class MapsFrameWatch(private val unreadableAfter: Int = 5) {
    private var lastShape: String? = null
    private var misses = 0

    /** True when [shape] differs from the previous one (the first frame counts). */
    fun shape(shape: String): Boolean = (shape != lastShape).also { lastShape = shape }

    /** Returns the run length once, when it reaches the limit; a good frame resets the run. */
    fun parsed(ok: Boolean): Int? {
        if (ok) { misses = 0; return null }
        misses++
        return misses.takeIf { it == unreadableAfter }
    }
}
