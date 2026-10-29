# Messages from Cloud Worker 2 to the General Manager

The laptop was off, so session-to-session messages cannot reach the GM. They are kept here instead, newest last.
The full report is in `cloud-report/REPORT.md` on branch `cloud/report`.

---

## 1. Round 1 (C1, C2, C3) - sent 29 Oct 2026

All done and pushed. Each job is on its own branch made from Ride_Connect_X. Nothing was pushed to Ride_Connect_X, and no PRs were opened. Every push passed `./gradlew testDebugUnitTest` with 0 failures. No existing test was edited.

- **C1** `cloud/maps-real-samples`: `MapsRealSamplesTest.kt`, 13 tests from the 12 real Maps 26.42 samples. 2 were `@Ignore` (empty title + "Turn right"; U+00A0 distance), because the parser was wrong. Fixed in C3; un-ignored in C4b.
- **C2** `cloud/ui-smooth`: only `presentation/` changed, with no visual change intended.
  - The breath loop is gated.
  - Pulses are created only when shown.
  - Sign-in orbs use an offset lambda and the canvas uses drawWithCache.
  - Statistics reads its live value inside its item.
  - About uses collectAsStateWithLifecycle.
  - Lists have stable keys.
- **C3** `cloud/nav-fixes`, one commit per step:
  1. An empty title with turn text is accepted.
  2. Non-breaking spaces in distances.
  3. Turn keywords are read only before onto/toward/via/to stay.
  4. Icon work runs off main on one ordered lane.
  5. The relay ticks at 500 ms, skips unchanged ticks, and caches the ETA clock.
  6. RideLog uses a ThreadLocal formatter and @Synchronized writes.

## 2. Jobs C4, C5, C6 (first set) - sent 29 Oct 2026

- **C4** `cloud/nav-turn-keep` (7be08e9, on top of nav-fixes): a new turn is not hidden behind the old one, and a bare left/right with no verb is a road name. 391 tests pass. Replaced by C4b.
- **C5** `cloud/perf-screen-off` (31d2287): one dialer check per call; the Navigation 30 s clock runs only on screen. 376 tests pass. Carried into `cloud/perf`.
- **C6** `cloud/more-tests` (2fde47e, on top of nav-fixes): 14 more tests. 399 tests pass. Carried into `cloud/tests`.

## 3. Round 2 (after 1.0.3-tester, from Ride_Connect_X 893915d) - finished 29 Oct 2026

Every branch passed `testDebugUnitTest` with 0 failures before its push.

| Job | Branch | Commit | Tests |
|---|---|---|---|
| C4b nav | cloud/nav-fixes-2 | 0846446 | 406 pass |
| C4c ETA tile | cloud/eta-tile | 4fd0490 | 402 pass |
| C5 perf | cloud/perf | 11d6999 | 398 pass |
| C6 tests | cloud/tests | b56ea62 | 416 pass |
| C7 restricted guide | cloud/restricted-settings-guide (from eta-tile) | ae5006c | 404 pass |
| C8 docs | cloud/docs-1-0-4 | ac505d9 | 398 pass |

- **C4b**:
  - The road-name cut has `(?:^|\s)`.
  - onListenerDisconnected stops the relay through mapsLane.
  - EtaClockCache: Maps' clock is cached per maneuver, a computed one is redone every minute, and the clock is part of the GPS skip key.
  - A distance-less turn is not carried.
  - MapsRealSamplesTest: both @Ignore removed, sample 7 is assertNull.
  - New tests: relay skip, ETA cache, lane order, onto/toward at the start.
- **C4c**: the ETA tile shows "14 min", else "8:48 pm" from etaClock, else "—".
- **C5**:
  - One dialer check per call.
  - The Maps-off clock runs only on screen.
  - The alert ride-log write moved off main.
  - The rest was checked and listed in the report.
- **C6**: MoreCoverageTest (14) plus Android17SamplesTest (4) with both real Android 17 samples.
- **C7**: the "Switch greyed out?" dialog (SDK >= 33, installer not com.android.vending, access still off), with an Open app info button. 8 strings, 2 tests.
- **C8**: a draft CHANGELOG [1.0.4-tester], plus tester-guide sections for restricted settings and Play Protect (Install anyway, do not switch it off).

**Merge order:** nav-fixes-2, tests, perf, restricted-settings-guide (it contains eta-tile), docs-1-0-4.

**Risks:**
- After a distance-less "Turn left" the dashboard shows a blank distance until Maps gives one.
- The restricted-settings menu path varies by phone.
- `cloud/perf` and `cloud/nav-fixes-2` both edit `MapsNotificationListener.kt`, in different functions. Check the merge.
- Nothing has been tested on a phone.

This branch is temporary: delete it once read.

## Merge check (all round 2 branches together)
On a local scratch branch (not pushed), I merged `cloud/nav-fixes-2`, `cloud/tests`, `cloud/perf`, `cloud/restricted-settings-guide` and `cloud/docs-1-0-4` into Ride_Connect_X 893915d, in that order. All five merged with no conflicts, and `testDebugUnitTest` passed 430 of 430 tests with 0 failures. The `MapsNotificationListener.kt` overlap I flagged does not conflict.
