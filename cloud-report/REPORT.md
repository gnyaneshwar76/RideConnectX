# Cloud Worker 2 report

Plain notes, newest job last. Every branch was tested with `./gradlew testDebugUnitTest` before it was pushed: 0 failures, no existing test edited.

## C1 - `cloud/maps-real-samples`
- New file `app/src/test/.../data/nav/MapsRealSamplesTest.kt`: 13 tests from the 12 real Maps 26.42 samples.
- 2 marked `@Ignore` because today's parser is wrong: sample 6 (empty title + "Turn right") and sample 11b (non-breaking space in "1.4 km"). Both are fixed in C3. Un-ignore them after `cloud/nav-fixes` is merged.

## C2 - `cloud/ui-smooth`
Only `presentation/` changed. The aim was no visual change.
- `rememberBreath(active = ...)`: the ambient loops stop while nothing shows them.
- SOS and locate pulses are created only when shown, and are read in `graphicsLayer`.
- Sign-in orbs use an offset lambda and the canvas uses `drawWithCache`. Radar rings use `graphicsLayer`.
- Statistics reads the per-second value inside its item. About uses `collectAsStateWithLifecycle`.
- Stable keys on the Legal, Permission and Vehicle lists.
- Risk: not yet checked on a phone.

## C3 - `cloud/nav-fixes`
1. An empty title is accepted when the text is a turn ("Turn right"). The distance is then unknown.
2. Distance patterns accept U+00A0 and U+202F.
3. Turn keywords are read only before " onto / toward / via / to stay ".
4. Maps icon work runs off the main thread on one ordered lane. The icon is loaded once and the arrow catalogue is built on connect.
5. Relay ticks every 500 ms, skips ticks whose metres did not change, and formats the ETA clock once per maneuver. Packet bytes are unchanged.
6. RideLog uses a per-thread time formatter and writes one entry at a time.
- Risks: the dashboard updates at most twice a second from GPS. If Maps gives no clock, the fallback ETA no longer moves at a minute change within one maneuver.

## C4 - `cloud/nav-turn-keep` (built on `cloud/nav-fixes`; it needs the C3 parser fixes)
- `keepsTurnRule` has a new `freshNewTurn`. A post naming a different recognised turn is shown, not hidden behind the old one. Example: sample 6 "Turn right" with no distance, after sample 5 "40 m Turn left", used to keep LEFT 40 m.
- A plain left/right with no turn verb in front ("Neredmet - Left Bank Rd") is treated as a road name, so the result is STRAIGHT with source FALLBACK. The arrow icon still wins.
- 6 new tests in `TurnKeepRealSamplesTest.kt`. All 391 tests pass.
- Risk: a Maps wording that gives a direction with no verb in front ("Left onto X") would now read as straight unless the icon says otherwise. No real capture shows that wording.

## C5 - `cloud/perf-screen-off` (from `Ride_Connect_X`)
Fixed (smallest changes):
- `MapsNotificationListener.relayClusterAlert`: the trusted-dialer check (default dialer + package flags, two system lookups on the main thread) ran twice per call notification. It now runs once.
- `NavigationScreen`: the 30 s clock for the Maps-off card ran while the app was in the background. It now runs only while the app is on screen, and updates at once when the app comes back.

Checked, no change needed:
- Relay ticker and Maps listener: already fixed in C3 (500 ms tick, unchanged ticks skipped, icon work off main).
- BLE write loop (`BleRepositoryImpl`): runs on IO. The reconnect watcher sleeps instead of polling. The heartbeat runs only while linked, and the protocol needs it.
- `RideTracker` 1 s ticker: runs only while linked, and publishes only when something collects it.
- Room/DataStore in composables: no `runBlocking` or blocking reads. Pull-to-refresh calls suspend `checkOnce()`, which is main-safe. Statistics' day timer is `WhileSubscribed`. Only About used `collectAsState`, and that is fixed in C2.
- Remaining `SharingStarted.Eagerly` (theme, auth, odometer): small DataStore flows with no timers. Left as they are.
- 376 tests pass.

## C6 - `cloud/more-tests` (built on `cloud/nav-fixes`; it tests the C3 parser fixes)
New file `MoreCoverageTest.kt`, 14 tests, all pass (399 total on the branch):
- ProtocolEngine: frame envelope and checksum; real 0 remaining is `0000M`, unknown is FF x5; distance text such as `----M` is always 5 characters; the same input gives identical bytes. Metres only, so the km display style is not touched.
- Parser: empty title (a turn only when the text is a turn); U+00A0 and U+202F; km/m/mi/ft and decimal commas; `Arrive` in 12 h and 24 h; remaining = progressMax - progress (3646 m -> 3.6 km, 999 m, 1.0 km, 0 m; nothing when progress is past the end or negative).
- Relay: carryTurn keeps the old arrow and distance and takes the new ETA and remaining; the "0 m" repeat keeps the turn; UnknownTurnHold reset; RemainingRule holds 3 s, then sends unknown.
- The `freshNewTurn` case is tested on the C4 branch, where it exists.

## Merge order
`cloud/nav-fixes`, then `cloud/nav-turn-keep` and `cloud/more-tests` (both are built on it), then `cloud/ui-smooth`, `cloud/perf-screen-off` and `cloud/maps-real-samples` (un-ignore its 2 tests after nav-fixes is in).

---
# Round 2 (after 1.0.3-tester; branches from Ride_Connect_X 893915d)

## C4b nav - `cloud/nav-fixes-2` (0846446)
Changes:
1. `MapsNotificationListener.kt` `ROAD_NAME_START`: `(?:^|\s)` before onto/toward/towards/via/to stay. "toward Right Side Colony" is now STRAIGHT, where it was RIGHT. "Ontario Rd" is untouched.
2. `onListenerDisconnected`: `relay.stop()` now runs through `mapsLane` (then on main), behind any queued frames, the same as a Maps removal.
3. ETA: a new `EtaClockCache`. Maps' own clock is formatted once per maneuver. A computed clock (now + minutes, or the plain clock) is redone every minute. The clock is part of the new `FrameKey`, so a GPS tick with unchanged metres still sends when the ETA minute changes.
4. `keepsTurnRule(freshHasTurn)`: a distance-less post that names a turn (from its text or icon) is shown as itself. It is held 3 s, then sent with the distance blank, and is not swapped for the old turn. Only a post with no direction ("Head east on ...") keeps the old turn.
5. Tests: both `@Ignore` removed from `MapsRealSamplesTest` (now 13/13 pass), and sample 7 is `assertNull`. New `NavFixes2Test` (8): relay skip, ETA cache (fixed / per minute / new maneuver), lane order (200 tasks, in order, never two at once), road names starting with onto/toward, and turn carry with real samples.

Tests: `testDebugUnitTest` 406 pass, 0 fail. The existing T5 `keepsTurnRule` test is unchanged and passes, because the new parameter has a default.

Risks:
- After a distance-less "Turn left" that repeats the same turn, the cluster no longer keeps counting the old metres. It holds the frame 3 s, then shows the arrow with a blank distance until Maps gives one again. This is what "no carry without a real signal" asks for, but it is a visible change.
- Listener disconnect now stops the relay a moment later, because it is asynchronous.

Not checked: on a phone or a ride; the ETA field on the cluster at a minute change.

## C4c UI - `cloud/eta-tile` (4fd0490)
Changes: `NavigationScreen.kt` ETA tile now uses the new `etaTileText(etaMinutes, etaClock)`. It shows "14 min" when Maps gives minutes. Otherwise it shows the arrival clock read from the cluster field ("0848PM" becomes "8:48 pm"). If neither is there, or the clock cannot be read, it shows "—".
Tests: new `EtaTileTextTest` (4), including the real Android 16/17 "Arrive" line read through the parser. 402 pass, 0 fail.
Risks: the tile text is in English and lowercase ("8:48 pm"). It does not follow the phone's 24-hour setting.
Not checked: on a phone.

## C5 perf - `cloud/perf` (11d6999)
Changes (smallest fixes):
- `relayClusterAlert`: the trusted-dialer check (two system lookups, main thread) now runs once per call notification instead of twice. Carried over from the unmerged `cloud/perf-screen-off`.
- `NavigationScreen` Maps-off card: the 30 s clock now runs only while the app is on screen, and updates at once on return. Also carried over.
- `logAlert`: the ride-log file write for each call or message moved off the main thread, onto the serial Maps lane, so lines keep their order.

Checked, no change needed:
- Relay ticker: 500 ms and runs only while counting; unchanged ticks are skipped (C3, and the ETA key in C4b).
- Maps listener: frames are already off main (C3).
- BLE write loop: IO dispatcher. The reconnect watcher sleeps on a channel or a timeout, not polling. The heartbeat runs only while linked, and the protocol needs it.
- `RideTracker` 1 s ticker: runs only while linked, and publishes only with collectors.
- Composables: no `runBlocking` and no blocking Room/DataStore reads. Refresh actions call suspend `checkOnce()`. Statistics flows are `WhileSubscribed`. All `collectAsState` now have lifecycle (C2).
- `SharingStarted.Eagerly` (theme, auth, odometer): small DataStore flows with no timers. Left as they are.

Tests: 398 pass, 0 fail. These are timing changes, so there is no new unit test.
Risks: alert lines now reach the ride log a moment later.
Not checked: battery measurement on a phone with the screen off.

## C6 tests - `cloud/tests` (b56ea62)
Changes (tests only, no app code):
- `MoreCoverageTest` (14), carried over from the unmerged `cloud/more-tests`:
  - ProtocolEngine: envelope/checksum, `0000M` vs FF x5, distance text, identical bytes.
  - Parser: empty title, U+00A0/U+202F, km/m/mi/ft, Arrive 12/24 h, remaining = progressMax - progress (and past the end / negative).
  - Relay: carryTurn, UnknownTurnHold, RemainingRule.
- New `Android17SamplesTest` (4) with the two real Android 17 frames:
  - "70 m · Turn left", 0/5849: LEFT, 70 m, remaining 5.8 km, 0137AM, no minutes.
  - "1.4 km · Turn right at Madina Cir to stay on NH 44", 650/4297: RIGHT, 1400 m, remaining 3.6 km, 0132AM.
  - The same two with non-breaking spaces.
  - Sample 1 run through to the cluster frame: code byte, "0070M", checksum.

Tests: 416 pass, 0 fail.
Risks: none to app behaviour.
Not checked: the km display style (only metres asserted, because kmStyle is shared global state).
