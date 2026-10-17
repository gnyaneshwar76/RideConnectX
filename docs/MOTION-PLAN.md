# RideConnectX — Motion plan

Branch: `motion/app-motion`, built on `backup/app-before-motion-2026-09-29`.
If anything here feels wrong on the phone, that backup branch is the untouched app.

Rules every motion follows:

- **It explains something.** Where a screen came from, that a value changed, that a tap landed, that input was refused. No motion only for decoration.
- **One vocabulary.** All timings and helpers live in `presentation/theme/Motion.kt`: `enterRise` (stagger in), `pressScale`, `shake`, `rememberBreath` (slow glow), `animatedCount` (count-up), `rememberShakeKey`.
- **Plays once per visit.** Entrances do not replay on scroll or recomposition.
- **Honours "Remove animations".** Compose scales every animation by the phone's animator setting, so it drops to instant.

## Phase 1: built on this branch (126/126 tests)

| Screen | Motion | Why |
|---|---|---|
| **All screens** | New screen slides in from the right; the one behind drifts back and dims. Back reverses it. Leaving the splash only fades. | You always know where you came from. |
| **Dashboard** | Sections rise in one after another. | The screen builds instead of popping. |
| | Scooter rolls onto its stage the first time. | The vehicle is the hero. |
| | Floor light breathes; swells and warms when connected. | Connected is felt, not just read. |
| | Light sweeps across the scooter while scanning or connecting. | Shows work in progress. |
| | Connected pill fades green and a ring pings from its dot. | Live link at a glance. |
| | ODO / Trip A / Trip B count up to the new reading. | A fresh reading is noticed. |
| | Fuel bars fill left to right, one after another. | Like the gauge sweeping on ignition. |
| | Pair tile morphs to Connected (colour + live dot pops in). | State change is smooth. |
| | Quick-action tiles press in and spring back. | Taps feel physical. (existing, kept) |
| | Overdue service card slowly breathes its red border. | Noticed without shouting. |
| **Navigate** | Route line draws itself from you to the destination. | Sets the scene. |
| | Your position dot pulses. | "You are here". |
| | Maneuver card drops in; each new turn slides up into it. | The next action is what moves. |
| | Relay dot fades between states and pings while sending to the cluster. | Shows the cluster link is live. |
| **Pairing** | Found scooters rise into the list as they are found. | No sudden jumps. |
| | Connect → spinner → tick, each swapping in with a pop. | Clear progress. |
| **Stats** | Sections rise in; period tab colour glides. | |
| | Distance bars grow one after another whenever the period changes. | Numbers feel earned. |
| | Ride rows rise in; added/removed rows animate. | |
| **Service** | Hero, tasks, status and history rise in; added/deleted records slide/fold. | |
| **Safety** | Sections rise in; contacts animate in/out. SOS pulse (existing, kept). | |
| **Notifications** | Rows rise in one after another; read/removed rows animate. | |
| **Profile** | Cards rise in; scooter hovers slowly and glows when connected. | On display, alive. |
| | Photo viewer: picture zooms up from the avatar size. | Not a jump cut. |
| **Settings** | Groups rise in. Switches animate (Material, existing). | |
| **Forms** | Refused input (N18 name rules, contact fields, profile names) shakes once. | Tells you "no" without a popup. |
| | Primary buttons press in and spring back. | |
| **Intro / onboarding** | Page dots stretch and glide between pages. | The pill travels. |

## Phase 2: built 1 Oct 2026 (135/135 tests)

| Screen | Motion |
|---|---|
| Splash | A band of light crosses the wordmark once. |
| Intro pager | Parallax: the picture travels slower than its page. |
| Permissions | Granted: the tick pops in and the card border turns green. |
| Vehicle picker (profile setup) | Chosen model holds its size, the others step back and dim; the paint dissolves when a swatch is picked. |
| Dashboard | Unread badge pops onto the bell; the bell swings once when something new arrives. |
| Navigate | Arrival card with a tick that draws itself. |
| Service | The save button shows "Saved" with a drawn tick, then the sheet closes. |
| Empty states | Artwork bobs slowly (still in Battery Saver). |
| Appearance | Light/dark and Flat/Glass switches dissolve instead of snapping. |
| Haptics | Short buzz on connect, on refused input, and on SOS. |

Not built: SOS hold-to-send. The SOS button already opens a confirmation
sheet, so a hold would only slow down an emergency.

## Ideas on hold

- Vehicle card flips to show details on the back.
- Three-stage animations (working, done, result) for SOS, pairing and save.
- Live preview of the rider's name as the cluster will show it.

## Background: Photo or Glow (10 Oct 2026)

- Appearance > Background: Photo (default, today's look) or Glow.
- Glow: three large soft lights in the colour mix or the accent, one slow sweep about every 78 s (`GlowField` in `theme/Glass.kt`, on `rememberBreath`: ~30 fps, stops off screen, still in Battery Saver). Replaces the backdrop photo and pools in Flat and Glass; the scooter picture and pairing photo stay. Navigate has no backdrop, so it stays calm.
- Peak alpha per light: `glowAlpha` (0.18 dark, 0.16 light at full intensity); `ColourMathTest.glowKeepsThemeTextReadable` checks 4.5:1 with all three overlapping.
