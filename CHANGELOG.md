# Changelog

All notable changes to RideConnectX. Format: Keep a Changelog. Versions follow the release tag.

## [1.0.2-tester] - 2026-10-27 (testers only)

### Added
- Navigation page: "Turn on Google Maps notifications" card with a button, shown after a failed navigation start.
- Notification-reader watchdog: if a phone stops the reader, the app asks Android to restart it.
- Ride log notes when Google changes the Maps notification layout.

### Fixed
- No fake "0 m" at the start of a route on phones without Samsung's extra distance field.
- Road alerts ("Accident-prone area ...") are no longer read as turns.
- Maps can be found on Android 11+ (package visibility).

### Known limits
- Not yet proven on a phone: the watchdog on Xiaomi, and the blank turn-distance field on a real dashboard.

## [1.0.1-tester] - 2026-10-22 (testers only)

### Added
- V-Strom / Gixxer family dashboards (BM01): connect, navigation, calls, messages, ride stats.
- Navigation page: "Allow background run" pop-up (once, after install) and a reminder while it is off.
- Connection details in About > Information.

### Fixed
- Remaining distance on Android 16 Google Maps: always the real km, counted down between Maps updates; no blank box.
- Next-turn metres keep counting with the screen off; no jumps back up.
- Calls on Samsung phones: a ringing call is no longer mistaken for an answered one.
- Arrival time read from "Arrive 5:17 pm".
- Faster reconnect when a motorcycle dashboard does not answer.

### Known limits
- Not yet proven on a ride with this build: screen-off metres, reconnect, the Samsung background page.

## [1.0.0-tester] - 2026-10 (testers only)

### Added
- Report a problem button, tester guide.

## [1.0.0] - 2026-10 (first public release)

### Added
- Live GPS rides: real-time distance, speed, average and max from the phone's GPS,
  route map, Keep / Delete for every ride, 30-day auto-delete, cloud copy of up to
  10 kept rides when signed in. GPS saver wakes at once when the phone moves.
- Email-link sign-in: get a one-tap sign-in link by email (link lives 10 minutes,
  60 s before you can ask again, 5 requests per 24 hours per phone).
- Recent sign-ins on the Profile page.

### Changed
- One app for everyone: the separate test build is gone.
- Pairing scan lists every supported dashboard, not only the first model.
- Service screen cleaned up; kilometres now follow the rides you really rode.
- Deleting a ride asks first.
- Release APK is signed with the project's own release key. Phones that had an
  earlier test build update in place and keep their data (APK Signature Scheme v3
  key rotation).

### Known limits
- UNPROVEN on a real ride with this build: GPS rides, email-link sign-in on a real
  phone, the new pairing scan.
- UNPROVEN: dashboards other than the tested 125 cc scooter; roundabout exit codes.
- Calls and messages on the dashboard: confirmed on a real bike.
- After installing 1.0.0, older test builds signed with the old debug key cannot be
  installed over it any more (Android blocks the key going back).
- Unofficial: not made by, or connected with, any vehicle manufacturer.

## [0.9.0-beta] - 2026-10 (draft, never published)

### What the app does
RideConnectX shows Google Maps turn-by-turn directions on the instrument cluster of a
125 cc scooter over Bluetooth Low Energy. It reads the Maps navigation notification
and sends the cluster the matching turn arrow and distance. It also shows phone battery
and signal, calls and messages, keeps a ride history, service reminders and safety
settings, and has an optional sign-in with a cloud profile.

### Added
- Calls and messages on the cluster.
- Notifications page, colour mixes, Glow background, Back as Home.
- Fork-in-road turn codes (ramp left, ramp right).
- Signal indicator rule for 5G.
- Motion and glass effects across the screens.
- Release build: version from the release tag, signed with the owner's own key,
  Play Integrity App Check.

### Changed
- Firebase is optional at build time; the app runs without it (no sign-in, no cloud profile).
- Release builds strip debug logging.

### Known limits
- UNPROVEN: models other than the tested 125 cc scooter.
- UNPROVEN: roundabout exit codes on real rides.
- UNPROVEN: automatic reconnect and live remaining distance (need a ride re-test).
- Calls and messages on the cluster: confirmed on a real bike.
- The signed release build has not been tested on a real phone yet.
- Unofficial: not made by, or connected with, any vehicle manufacturer.
