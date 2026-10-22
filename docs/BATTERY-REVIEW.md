# Battery and speed review (read-only check, 3 Oct 2026)

By rcx-battery. Code reading only. No emulator, no adb, no gradle in this run.
Paths are under `app/src/main/java/com/eshwar/rideconnectx/`.

## UPDATE 13 Oct 2026 (second pass, code reading only, no emulator, no adb, no gradle)

Paths under `app/src/main/java/com/eshwar/rideconnectx/`. Nothing here was measured; every line is a code line.

### Status of the 3 Oct findings (the list below is the old text)

- Old HIGH 1-3 (GPS after swipe, link lost, stale route): FIXED. `NavigationRelay.kt:97-117` turns GPS and HIGH priority off on link loss (`onLink` -> `idleLink`); `:283-290` the 90 s watchdog calls `idleLink()` too; `stopGps()` cancels the 250 ms tick job (`:207-214`).
- Old HIGH 4 (3 s watcher forever): FIXED (CHK-15). `LinkRules.kt:36-42` `decide`: connected -> wait on the state flow, dormant or user-disconnected -> `wake.receive()` (no timer), no address or auto-connect off -> one 30 s recheck. A timer runs only while a connect is really being tried (3 s -> 15 s, `:45`).
- Old MEDIUM 5 (33 ms loop in background): FIXED. `Motion.kt:155-171` `rememberBreath` stops the loop when `rememberIsVisible()` is false (below STARTED) and holds at 0.5 in Battery Saver.
- Old MEDIUM 6 (endless transitions ignore Battery Saver): FIXED for all 5 named surfaces. `rememberAmbientTransition` returns null in Battery Saver (`Motion.kt:191-192`); used in `AnimatedSignInBg.kt:129`, `VehicleStage.kt:177`, `BlePairingScreen.kt:383`, `CreateProfileScreen.kt:594`, `SafetyScreen.kt:529`, `Motion.kt:447`. They still run at the display rate, not 30 fps, but only while the activity is started (see LOW N1).
- Old MEDIUM 7 (CityLocator listener): FIXED. `CityLocator.kt:158-162` removes the listener inside the callback before resuming; `:172` removes it on cancel; the failure path removes it too. Geocoder runs on `Dispatchers.IO` (`:195`).
- Old MEDIUM 8 (250 ms GPS): unchanged, still an owner choice (`NavigationRelay.kt:64,186-188`). Only while linked and a route is active.
- Old LOW 9-11: not rechecked.

### The 1 s alert loop (BleRepositoryImpl.kt:197-262, LinkRules.kt AlertLoop)

- Call ringing: `callRinging.combine(_connectionState)` + `collectLatest` (`:202-219`). One burst = 3 writes 200 ms apart, then `delay(600)`, so 1 burst per second, 3 writes per second, max `CALL_MAX_BURSTS = 30` = 90 writes in 30 s, then the loop returns and nothing runs until `callRinging` changes. Cost estimate from the code: about 4 coroutine wake-ups per second for 30 s, one extra heartbeat write every 5 s. Each write is about 30 bytes (one radio event on a link that is already open). It only runs while the phone is ringing, so the CPU is awake for the ring anyway. No measurement.
- Ends on every path I read: answer, end, reject -> `onCallEnded` / `clearRinging` set `callRinging` to null or the next call -> `collectLatest` cancels the loop (mid-delay or mid-write; `sendPacket` waits at most `WRITE_WAIT_MS` 3 s, `:659`). Link drop -> `combine` emits null -> cancelled. Listener gone -> `clearAllRinging` (`MapsNotificationListener.kt:237`). Sticky ringing state -> `ringMaxMs` 90 s (`ClusterAlerts.kt:79`) and the 30-burst cap, whichever first. `onEnd` runs in `finally`, so the ride log line is written on cancel too.
- No wake lock anywhere (grep `WakeLock` in app code: none). No second job: `collectLatest` never runs two loops. The message loop (`:231-245`) is also `collectLatest`, 3 bursts = 9 writes, cancelled by the next alert.
- If the link is gone but the state still says Connected, `sendPacket` returns false at once (`:650-656`), so a burst costs no radio and the loop still ends at the cap. GOOD.
- Not measured: real period when each write takes 50-100 ms (burst longer than the 400 ms the loop assumes, so period 1.1-1.3 s, harmless); whether Doze delays `delay(600)` with the screen off (a ringing phone keeps the CPU up, so unlikely).

### Other items checked

- Foreground service: `onTaskRemoved` -> `closeGatt`, `repository.shutdown()`, `stopForeground`, `stopSelf` (`BleForegroundService.kt:642-649`); `shutdown()` writes `closed_by_rider` with `commit()` and cancels link, profile and heartbeat jobs (`BleRepositoryImpl.kt:613-635`). `START_NOT_STICKY` unchanged. GOOD.
- Heartbeat: one loop, 5 s idle, 1 s only for the 3 clear beats (`LinkRules.kt:76-91`), exits after 3 failed writes (`:769-773`). The new 12 s setup watchdog (`BleForegroundService.kt` `setupWatchdog`) is one `postDelayed`, removed on success and on close. GOOD.
- Back as Home: `moveTaskToBack(true)` (`MainActivity.kt` BackHandler); the activity stops, `rememberIsVisible()` goes false, the Glow loop and the other breath loops stop. The service and link stay by design. GOOD by code.
- Glow background: `GlowField` uses `rememberBreath(39_000)` (`Glass.kt:404-410`), so about 30 redraws per second of 3 radial gradients over the whole screen, only while visible and not in Battery Saver; frozen at the midpoint in Battery Saver. Redraw cost per frame not measured.
- Ride log: the file is written only if debug or the Ride recorder setting is on (`RideLog.kt:63-66`), so the smooth build writes nothing by default. Cap 3 MB, then the file is deleted and restarts (`:208-214`). Each line is a separate `appendText` (open, write, close) plus `f.length()` and `getExternalFilesDir`; `gpsCount` goes about 4 times a second during a route and each alert burst adds a `diag` line. It is called from the relay/BLE coroutines, not a Compose thread. LOW.
- Notification listener: per Maps frame (about 1 per second) it renders the large icon into a 32x32 bitmap on the listener thread (`MapsNotificationListener.kt:176-189`). Small. LOW.
- WorkManager: unchanged, one daily job. GOOD.
- Size: `app/build/outputs/apk/smooth/app-smooth.apk` is 8,547,863 bytes (8.15 MiB, file date 13 Oct 14:22); 3 Oct build was 8.46 MB. About the same. Not rebuilt.

### New findings

LOW N1. Ambient transitions (aurora, radar, SOS pulse, stage scan) run at the display refresh rate (60-120 Hz), not 30 fps. Proof: `rememberAmbientTransition` wraps `rememberInfiniteTransition` (`Motion.kt:191-192`). Only while the screen is on and the app started. NEEDS: rcx-ui - `Motion.kt:191` - optional; drive these from `rememberBreath` where the look allows. Owner decision on the look.
LOW N2. `Log.d(TAG, "Data Packet-Main ${packet.toHexString()}")` for every write (`BleForegroundService.kt:384`): 90 string builds in a 30 s ring. Proof: the line. NEEDS: rcx-ble - `BleForegroundService.kt:384` - guard with `BuildConfig.DEBUG` or drop it.
LOW N3. `RideLog.append` reopens the file on every line (`RideLog.kt:210-213`). NEEDS: rcx-nav - `RideLog.kt:208` - only if the recorder is left on for a whole ride; a buffered writer would do.

No HIGH and no MEDIUM finding in this pass.

### Could not measure (no emulator, no adb this round)

Real wake-ups and CPU of the alert loop; radio time of 90 writes; Glow frame cost (`dumpsys gfxinfo`); location requests after a swipe (`dumpsys location`); sensor listeners (`dumpsys sensorservice`). The commands are in "NOT TESTED" below.

---

# Old text of the 3 Oct 2026 review (kept for history; see the status list above)

## HIGH (drains while the app is closed or idle)

1. GPS 4 times a second is not stopped when the app is swiped away mid-route.
   - Proof: `BleRepositoryImpl.shutdown()` (line 474-497) drops BLE but never calls `NavigationRelay.stop()`. `onTaskRemoved` in `BleForegroundService.kt:588` also only closes BLE. Only `stop()` (`NavigationRelay.kt:290-302`) calls `stopGps()` and `setLowLatency(false)`.
   - The Maps listener keeps the process alive, and every Maps update calls `onManeuver` (`NavigationRelay.kt:206-214`), which turns HIGH priority and `startGps()` back on. `onManeuver` does not check the BLE link or the "closed by rider" flag.
   - Cost: fused HIGH_ACCURACY at 250 ms (`NavigationRelay.kt:147`) plus a 250 ms tick job (`:159-164`) for as long as Maps navigates, with nothing to send to.
   - NEEDS: rcx-nav - `NavigationRelay.kt:206` - do not start GPS or tick when the link is down or the rider closed the app; stop the relay when the app is swiped away. (rcx-ble owns `BleRepositoryImpl.kt:474`: a hook so `shutdown()` can tell the relay.)
2. GPS keeps running when the BLE link is lost.
   - Proof: nothing in `NavigationRelay.kt` reads the connection state to call `stop()`. `_clusterLinked` is only set false in `stop()` (`:297`).
   - NEEDS: rcx-nav - `NavigationRelay.kt:206-230` - stop GPS and the tick after the link has been down for some seconds. rcx-ble to expose a link-lost signal if one is missing.
3. Stale-route clear leaves GPS and HIGH priority on.
   - Proof: `armWatchdog` (`NavigationRelay.kt:245-251`) calls `clearCluster()` only, not `stop()`, when Maps goes silent for 90 s (`STALE_CLEAR_MS`, line 59). State stays Active, so GPS 250 ms and ticks continue, and `onTick` (`:178-182`) keeps relaying.
   - NEEDS: rcx-nav - `NavigationRelay.kt:250` - after clearing, also `stopGps()` and `setLowLatency(false)`, and restart them on the next Maps update.
4. Reconnect watcher wakes every 3 s forever, even when idle.
   - Proof: `BleRepositoryImpl.kt:117-126`: when `userDisconnected`, `dormant` or auto-connect is off, the loop does `continue` with `delayMs` still 3 s (`RECONNECT_MIN_MS`, line 640). It also reads DataStore (`appSettings.settings.first()`) every pass. The loop is never cancelled, and the process lives on while the Maps listener is bound.
   - NEEDS: rcx-ble - `BleRepositoryImpl.kt:125` - wait on a flow or sleep long (e.g. 60 s) while dormant or user-disconnected; cancel the watcher in `shutdown()`.

## MEDIUM

5. The ambient animation loop keeps ticking while the app is in the background.
   - Proof: `Motion.kt:153-161` is a `LaunchedEffect` with `while(true){ ... delay(33) }`. `delay` does not wait for the frame clock or the lifecycle, so it keeps waking about 30 times a second with the screen off or the app in recents (until the composition is destroyed). Used by `GlassLightField` (`Glass.kt:310`), dashboard, navigation, `VehicleStage` and `floaty` screens.
   - NEEDS: rcx-ui - `Motion.kt:153` - wrap the loop in `repeatOnLifecycle(STARTED)` (or use `withFrameNanos` plus a 33 ms gap).
6. The endless animations (`rememberInfiniteTransition`) ignore Battery Saver and run at the full display rate (60-120 Hz), not 30 fps.
   - Proof: sign-in aurora `AnimatedSignInBg.kt:127-210` (about 5 animated values); radar `BlePairingScreen.kt:380-392`; SOS pulse `SafetyScreen.kt:527-531`; locate pulse `CreateProfileScreen.kt:590-594`; scan sweep `VehicleStage.kt:175`; running border `Motion.kt:400`. None read `LocalAmbientMotion` (grep: only `rememberBreath`, `rememberTilt`, `MainActivity.kt:168`). `MainActivity.kt:91-101` asks for the highest refresh rate when not in Battery Saver, so these can run at 120 fps.
   - They stop when the activity stops (the frame clock pauses), so this is not an idle drain.
   - NEEDS: rcx-ui - the files above - use the throttled `rememberBreath`, or gate them with `LocalAmbientMotion`, so Battery Saver freezes them. Owner to say whether the SOS pulse should stay live in Battery Saver.
7. `CityLocator.freshFix` leaves a raw location listener behind when a fix arrives.
   - Proof: `CityLocator.kt:166-171`: `removeUpdates` is only in `invokeOnCancellation`. After a normal `cont.resume(location)` the listener (interval 0 ms, GPS provider) is not removed. The `withTimeoutOrNull` (`:145`) only cancels on a timeout.
   - NEEDS: rcx-manager (owner of `core/util` is not listed in OWNERS.md; give it to rcx-data or rcx-nav) - `core/util/CityLocator.kt:171` - remove the listener in a `finally` after resume.
8. HIGH accuracy GPS at 250 ms is aggressive even in a normal route.
   - Proof: `NavigationRelay.kt:147-149`. Worth measuring on a ride (see OWNER TEST).
   - NEEDS: rcx-nav - decide whether 500 ms or 1 s keeps the countdown smooth enough; owner choice.

## LOW

9. Tilt sensor: GOOD, one nit. Registered only between ON_RESUME and ON_PAUSE and only when ambient motion is on (`Motion.kt:262-273`), one shared listener (`:221`), 33 ms rate (`:226`, about 30 Hz), unregistered at the last release (`:230`). Off in Battery Saver. Nit: the 33 000 us value is only a hint; every event writes a mutable state on the main thread (`:242`). NEEDS: rcx-ui - `Motion.kt:226` - optional: add a max report latency so events batch.
10. `floaty()` comment says "still in Battery Saver" (`Motion.kt:181`), but it uses `rememberBreath`, which holds at the midpoint in Battery Saver (`:154`). Comment is wrong, not the behaviour. NEEDS: rcx-ui - `Motion.kt:181`.
11. `BlePairingScreen` radar: the rings use an infinite transition even when `active` is false (only the drawn values are fixed, `:397-398`). NEEDS: rcx-ui - `BlePairingScreen.kt:380` - create the transition only while `active`.

## GOOD

- Foreground service: `START_NOT_STICKY` (`BleForegroundService.kt:643`), `onTaskRemoved` closes GATT, unbinds and stops (`:588-596`), `onDestroy` closes GATT and clears handler callbacks (`:571-578`). No wake locks in app code (grep: no `WakeLock`).
- Connection priority: HIGH only from `setLowLatency(true)` on route start, BALANCED again in `stop()` (`NavigationRelay.kt:301`, `BleForegroundService.kt:346-352`). It stays on HIGH only through the gaps in HIGH items 1-3.
- "Closed by rider" memory (`BleRepositoryImpl.kt:68-93`) stops the reconnect watcher from restarting the link after a swipe or force stop.
- WorkManager: one daily periodic job (`ServiceReminder.kt:56-59`). Low cost.
- `GlassLightField` and `VehicleStage` use the throttled clock. The Battery Saver listener is registered and unregistered (`MainActivity.kt:108-118`).
- Size: `app-smooth.apk` is 8.46 MB (built 1 Oct 2026), minify and shrink resources on (`app/build.gradle.kts:73-74`). `res/` is 1.3 MB; only two images are over 100 KB (`img_welcome_hero.webp` 142 KB, `img_vehicle_vstrom_sx.webp` 107 KB, both `drawable-nodpi`).

## EMULATOR

Not checked - the emulator was in use by rcx-test and this job forbade adb and gradle.

## NOT TESTED (run later, emulator only)

- `adb -s <emulator> shell dumpsys sensorservice` with the dashboard open, then after Home: expect no RideConnectX listener after Home.
- `adb -s <emulator> shell dumpsys location` during a simulated route, then after swiping the app away: expect no request after the swipe (HIGH item 1 predicts one stays).
- `adb -s <emulator> shell dumpsys gfxinfo com.gnyaneshwar.rideconnectx framestats` on the sign-in screen: frames per second.
- `adb -s <emulator> shell dumpsys batterystats com.gnyaneshwar.rideconnectx` after 10 minutes in the background: wakeups and CPU of the 3 s watcher (item 4) and the 33 ms loop (item 5).
- `adb -s <emulator> shell dumpsys jobscheduler` for the WorkManager job.

## OWNER TEST (real phone and scooter)

1. Start a Maps route, confirm the cluster shows turns, swipe the app away. Keep Maps navigating 2 minutes. Settings, Location, App location usage: RideConnectX should NOT show as using location.
2. Same, but switch the scooter off mid-route. After a minute the location use should stop.
3. After a ride, Settings, Battery, RideConnectX: background use should be near zero with the app closed overnight.
4. Turn Battery Saver on and open the sign-in and pairing screens: motion should freeze or slow.

## GPS ride tracking 16 Oct

Code reading (RideTracker, GpsRideAccumulator, RideRepository workers, RideConnectXApp, BLE service stop, NavigationRelay GPS).

- HIGH - `RideTracker.kt:311,324` - GPS runs at 1 s high accuracy for the whole BLE link, not only while the scooter is on. The link can stay Connected with the scooter off (`BleRepositoryImpl.kt:466`, CHK-14). Proof: `startUpdates()` is called on every connected second. Owner: rcx-data.
- MEDIUM - `RideTracker.kt:306-310` - the 1 s loop runs for the whole process, even when disconnected (`if (!connected) continue`). Idle wakeups. Owner: rcx-data.
- LOW - `RideTracker.kt:148,257` - `draft()` re-encodes the whole route on the main thread every 15 s (the callback runs on the main looper, `:333`). The route is not capped in memory (`GpsRideAccumulator.kt:127-128`); the cap is only at save (`RoutePolyline.kt:95`). Not measured. Owner: rcx-data.
- LOW - `RideTracker.kt:259` - `LiveRide.route` is copied and compared on every emission, but no UI reads it (no `.route` in `StatisticsScreen.kt`). Owner: rcx-data.
- LOW - `NavigationRelay.kt:167` - during navigation two HIGH-accuracy GPS requests run (this 250 ms one and `RideTracker` 1 s). Owner: rcx-nav.
- LOW (not battery) - `RideTracker.kt:281-282` - the recovered draft is cleared even when `recordRide` failed. Owner: rcx-data.
- GOOD - `RideTracker.kt:299` stops GPS on disconnect; the 60 s grace (`:301`) holds no GPS. Battery Saver 3 s / 5 m at `:324-326`, re-requested by the receiver at `:264-268`.
- GOOD - swipe-away: `BleForegroundService.kt:642-649` calls `shutdown()`, which sets Idle (`BleRepositoryImpl.kt:640-656`) and `setClosed(true)` (`:641`). The `:299` stop then runs. Live flow emits about 1/s, not per frame (`:259,312`).
- GOOD - `BleForegroundService.kt:401` HIGH only while a route runs (`NavigationRelay.kt:102`), back to BALANCED in `idleLink()` and `stop()` (`NavigationRelay.kt:109-113,339-349`).
- GOOD - `RideRepository.kt:204-205` daily purge (KEEP); `:213-217` upload is unique, network-constrained, retry with backoff (`:289`), no tight loop.
- Measured: `adb -s emulator-5554 shell dumpsys location` at 20:02 on 16 Oct showed 0 lines for `com.gnyaneshwar.rideconnectx`. The app was not confirmed running, so this is weak evidence.
- NEEDS: rcx-data - RideTracker.kt:311,324 - run GPS only while the scooter is on (telemetry valid), not for the whole link.
- NEEDS: rcx-data - RideTracker.kt:306-310 - stop the 1 s loop when disconnected; wait on the connection state.
- NEEDS: rcx-data - RideTracker.kt:148,257,259 and RideTracker.kt:281-282 - cap or stream the route; draft load/clear.
- NEEDS: rcx-nav - NavigationRelay.kt:167 - reuse the ride's fixes, or stop the second request while a ride runs.
