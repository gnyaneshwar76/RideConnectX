# Changelog

All notable changes to RideConnectX. Format: Keep a Changelog. Versions follow the release tag.

## [0.9.0-beta] - 2026-10 (draft, not yet published)

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
