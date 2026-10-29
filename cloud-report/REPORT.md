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
