# Design proposals

Nothing here is decided. The owner picks; rcx-ui builds.
Part 1 (9 Oct 2026): Notifications grouping, N38 glass options, N37 audit. Part 2 (3 Oct 2026, older): sections A to E below.

# PART 1 (9 Oct 2026)

## 1. Notifications page: grouping proposal (plainest version)

Fits the existing row look (`NotificationRow`, 18 dp card, 40 dp icon tile). Built in `NotificationsViewModel` + `NotificationsScreen`, a pure function so it can be unit tested.

- **Sections**, newest first, each with a small header row (11 sp+, muted, same style as other group titles): "Today", "Yesterday", then one header per older date ("6 Oct"). Use the phone's local date, not 24 h windows. Replace "3d ago" with the clock time ("14:05") inside Today/Yesterday, and "14:05" on older dates too (the date is in the header). Sticky headers not needed.
- **Calls vs messages**, by `kind`: calls = phone icon, green accent, label "CALL" or "MISSED CALL"; messages = chat icon, blue accent, label "MESSAGE". A small label (10 sp caps, accent colour) sits beside the title, the icon tile already carries colour. Other kinds (Service amber, Vehicle blue, Ride cyan, Safety red, System green) stay as now. Note: green and blue are already used by System and Vehicle; to make calls and messages clearly different, use a different shape of icon tile (circle for people items, rounded square for app items) rather than a new colour. That is the plainest way.
- **Collapse repeats**: same `kind` + same `title` (title = sender) + same day, becomes one row: newest text as body, a count pill "x3" beside the title, time of the newest. Unread dot if any of the group is unread. Tap marks the whole group read. Different senders, or the same sender on another day, stay separate rows.
- **Clear** and "Mark all read" stay as they are (both cover the whole list, not one section).
- **Empty state**: unchanged.

**Gap found (important for rcx-ui, rcx-data, rcx-nav).** `NotificationKind` today is only SERVICE, VEHICLE, RIDE, SAFETY, SYSTEM, and the only writer is `ServiceReminder.kt:86` (SERVICE rows). Nothing writes call or message rows into this list, and there is no sender field. So the "calls and messages" look cannot show anything until two additive changes exist:
- NEEDS: rcx-data - add `CALL` and `MESSAGE` to `NotificationKind` (stored by name, no migration; check `NavGraph.kt:93-99` `when` gets branches, they would map to null = no jump).
- NEEDS: rcx-nav - where a call or message is forwarded to the cluster, also call `NotificationRepository.notify(CALL/MESSAGE, title = sender, body = text)` (and "Missed call" for the 0x34 case). If the owner does not want a history of his messages stored on the phone, drop this and keep only service/vehicle rows: OWNER DECISION 1c below.
- Until then rcx-ui can build the grouping on the existing kinds (title + kind + day) and the call/message styling is dormant code.

### Taste options for the owner (do not block)
1a. **Section header style.** (i) plain muted text, (ii) text with a thin line, (iii) small glass pill. I would pick (i): least new look.
1b. **Group count.** (i) "x3" pill beside the title, (ii) "3 messages" line under the body, (iii) stacked-card edge under the row (Apple style). I would pick (i): clearest at a glance.
1c. **Keep message text on the phone?** (i) store sender + text, (ii) store sender and "New message" only, (iii) do not store calls and messages at all. I would pick (ii): useful list, no private message text in the database. (Privacy question, also for rcx-legal and rcx-security.)
1d. **Swipe to delete a row** (the app already has swipe-to-delete elsewhere): (i) yes, (ii) no, only Clear. I would pick (i) but it must also have a non-gesture way (long press menu) for TalkBack.

## 2. N38 glass: options (NO build until the owner picks)

Today (`theme/Glass.kt`): glass is a smoked fill (alpha 0.36 to 0.62 dark, 0.30 light) over a pre-blurred drifting colour field (`GlassLightField`), lit top highlight, specular gloss, inner rim. No live backdrop blur (removed for frame cost). Bottom sheets use real window blur (`FLAG_BLUR_BEHIND`, Android 12+). One Appearance slider (`LocalGlassIntensity`) scales blur, tint and rim together. So "less blur" and "more transparency" are partly the slider already.

| | (a) Less blur | (b) More transparency | (c) Apple liquid-glass direction |
|---|---|---|---|
| What changes | Sheet window blur radius `18+70k` px lower (cap ~40); `blur = 20.dp` token and the pre-blurred field softer (sharper colour behind cards). | Lower fill alpha: Heavy 0.62 to ~0.45, Light 0.36 to ~0.22, light theme 0.30 to ~0.18; keep rim and highlight so edges still read. | Reference https://developer.apple.com/design/ : lensing and refraction at the edge, a bright specular rim that follows the shape, content-aware tint, morph when state changes, floating controls (tab pills, buttons) with larger radii, text with vibrancy. |
| Where | `Glass.kt` tokens and `GlassSheetWindow`. Every glass screen and sheet. | `Glass.kt` tokens only (`GlassDarkHeavy`, `GlassDarkLight`, `GlassLight`). Dashboard tiles, Stats, Appearance, Notifications cards. | `Glass.kt` (new edge-lens draw: gradient rim, inner shadow, edge brightening), `glassButton`, BackHeader, bottom sheets; most screens through `cardSurface`. Real refraction needs a RuntimeShader (AGSL, Android 13+) on top of a captured backdrop. |
| Cost on phone | Cheaper: smaller blur radius is less GPU. No battery cost, may save a little. | Neutral. Risk: text contrast drops (small labels over bright colour); must re-check 4.5:1 at the floor `ScrimMin`. | Highest. Rim and gloss gradients only: low cost. Refraction shader plus backdrop capture: a real GPU cost every frame, bad for the scooter use and battery; Android 11 and below fall back to flat (as now). Do rim and highlight first, no shader. |
| New images | None. | None. | None for rim-only. A shader needs no image; a pre-rendered lens-edge texture is optional. |
| Risk | Looks less "glass", more flat tint. | Readability on light theme and bright photos. | Largest work, the look is a moving target; the app's own Figma tokens say "do not adjust by feel", so it needs a new Figma export first. |

**I would pick (b) with a floor**, plus the rim and highlight part of (c) later. Reason: it costs nothing on a phone, only changes numbers, and keeps text readable if each tier is clamped; full Apple refraction is battery heavy for a phone on a handlebar.

## 3. N37 whole-app usability audit

Method: read the code of all 23 screens; emulator (emulator-5554, 1280x2856) was running but sits on the Intro "Welcome" page, so only that page was seen (looks right: Skip and Get Started both 56 dp high, dots, text readable; screenshot `.claude/team/reports/20261009-2031-rcx-design-current.png`). Everything else is from code. NOT looked at on a screen: all other screens, TalkBack, font scale. Items from Part 2 section A that are still open are not repeated; they stay valid.

Severity: H = blocks or misleads a rider, M = real gap, L = polish.

| # | Screen | Problem | Sev | Owner |
|---|---|---|---|---|
| 1 | Notifications | Header actions "Mark all read" and "Clear" are 11 sp text with 6 dp vertical padding (about 28 dp high), under 48 dp, no role. `NotificationsScreen.kt:124-143`. Also "Clear" is destructive and sits next to the title with no spacing from "Mark all". | M | rcx-ui |
| 2 | Notifications | Rows use `relativeTime` with hard-coded English ("Just now", "5m ago") not `stringResource`; row has no combined TalkBack description (title, body, time, unread state is a dot only). `:309-319, :231` | M | rcx-ui |
| 3 | Appearance | Accent colour swatches are `clickable` circles with no label and colour is the only identity; size depends on `aspectRatio` in a row (probably ~40 dp). `AppearanceScreen.kt:335-348` | M | rcx-ui |
| 4 | Sign in | "Terms", "Privacy policy" links are 10.5 sp, no padding, about 14 dp touch height. "New here?" and "Forgot password" 12 sp, same. `SignInScreen.kt:246-263, :345-357` | H | rcx-ui |
| 5 | Sign in | Message notice (`:204`) is dismissed by tap only; ok, but errors are not announced (no live region) | L | rcx-ui |
| 6 | Profile | Edit photo button 30 dp (known, Part 2 A3); "Close" text button in photo viewer is 15 sp text + 8 dp padding, about 38 dp, and a hard-coded English "Close". `ProfileScreen.kt:491, :680-684` | M | rcx-ui |
| 7 | Statistics | Period tabs have 9 dp vertical padding with 11 dp corners: about 36 dp high; no `Role.Tab`/selected state for TalkBack. `StatisticsScreen.kt:210-216` | M | rcx-ui |
| 8 | Pairing | "Skip" row is 14 sp with 10 dp padding, about 40 dp high. Cancel exists only as Skip/back; the scan has no explicit Cancel button while scanning (only back arrow). `BlePairingScreen.kt:278-283` | M | rcx-ui |
| 9 | Create profile | Checkbox row has 4 dp vertical padding (row height from text only, under 48 dp); the checkbox itself needs `Role.Checkbox`/toggleable so TalkBack says checked. `CreateProfileScreen.kt:770-776` | M | rcx-ui |
| 10 | Navigate | Three readouts in one Row, 20 sp, fixed 32 dp dividers, no maxLines: at font scale 1.3+ "Distance" from Maps and "ETA 125 min" clip or wrap. `NavigationScreen.kt:195-206` | H | rcx-ui |
| 11 | Navigate | Turn arrow `contentDescription = null` (`:367`); new turn never spoken. Also 3 `null` descriptions in this file. | M | rcx-ui |
| 12 | All screens | Only 1 `maxLines`/`TextOverflow` in the whole of `presentation/screens` (grep count). Long names (rider name, vehicle name, Maps street, service centre names, contact names) can push layouts at large font. Needs one pass: names get `maxLines=1` + ellipsis, headers `maxLines=2`. | M | rcx-ui |
| 13 | Dashboard | 14 `clickable` with `indication = null` (`DashboardScreen.kt:238, 444, 501`): custom press feel exists, but TalkBack needs `role` and `onClickLabel` on tiles (they read as plain text). 9 sp unread badge and 8 sp OVERDUE (Part 2 A6) still open. | M | rcx-ui |
| 14 | Settings, Safety, Service | Almost no `role`/`onClickLabel` on `clickable` rows: only 2 of ~80 `clickable` usages in presentation carry a role or label (grep). TalkBack announces rows but not as buttons. One shared fix in `SettingsUi.kt` row + `CommonUi.kt` covers most. | M | rcx-ui |
| 15 | Safety | Call-contact button is 44 dp (`SafetyScreen.kt:1040`), 4 dp short; emergency action, should be 48 dp+ with a label "Call <name>". SOS hold has no non-hold alternative for TalkBack (hold is a gesture). | H | rcx-ui |
| 16 | Service | Delete (56 dp) is fine; "add" row ok. But date field opens a picker only on tap of the whole field; no typed fallback. Low. | L | rcx-ui |
| 17 | Dashboard / Notifications | "Connection alerts" and "Helmet reminder" rows will be removed this round; make sure no leftover empty section header or divider remains in Settings and Safety. | L | rcx-ui |
| 18 | Intro | Back button 48x56: fine. Skip 56 dp: fine. Dots (decorative) have no description, ok. | OK | none |
| 19 | Pairing, Stats, Service, Safety | Empty states exist (checked by grep: Service 209, Stats 187, Safety 373, Pairing nothing-found 292). Good. Error state for Sign in exists. No error state seen for Statistics load or Notifications load (flows from Room, so unlikely). | OK | none |
| 20 | Alerts feel (all) | Infinite animations ignoring Battery Saver (Part 2 A8) still open; not a usability gap but a battery one; owned by rcx-ui, already listed. | L | rcx-ui |
| 21 | Back behaviour | N41: Back from Dashboard shows Welcome again. Dead end/loop. Already in this round's order. | H | rcx-ui |
| 22 | Notification listener warning, post-notification warning | Both banners stack on Notifications page above the list on small screens; at large font the list can be pushed off. Make the list scroll with the banners (banners as first items). `NotificationsScreen.kt:149-150` | L | rcx-ui |

All rows are rcx-ui files (every screen belongs to rcx-ui), plus the two data/nav items in section 1.

NEEDS (one line each, to rcx-ui): rows 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 17, 22 as above. Highest first: 4, 10, 15, 21, then 1, 6, 7, 8, 9, 14.

NOT TESTED in part 1: every screen on the emulator except Intro, TalkBack, font scale 1.3 to 2.0, touch sizes were estimated from dp in code (not measured), contrast.

---

# PART 2 (3 Oct 2026, older, from the code only)

## A. Clear problems (sent to rcx-ui as NEEDS)

1. **Back button is 36 dp.** `components/CommonUi.kt:259` (BackHeader). Under 48 dp, on every screen with a header. Keep the 36 dp look but make the touch area 48 dp. Also add `role = Role.Button`.
2. **Dashboard bell is 36 dp.** `screens/DashboardScreen.kt:577` (HeaderIconButton). Same fix.
3. **Profile photo edit button is 30 dp.** `screens/ProfileScreen.kt:487-491`. Same fix, and it needs a label (check it has one).
4. **Navigate: the turn is tiny.** `screens/NavigationScreen.kt:358-394`. See section D. The size is a taste call, but the missing label is not: the turn arrow `contentDescription = null` (line 367) and the text has no live-region, so a screen reader never says the new turn.
5. **Decorative `null` descriptions that are not decorative.** Check these: `NotificationsScreen.kt:181,217`, `PermissionsScreen.kt:268,380` (status ticks: granted or not is only shown by colour and icon), `BlePairingScreen.kt:436`, `DashboardScreen.kt:663` (service icon, fine). The permission tick should say "Granted".
6. **Dashboard dashboard badge text 8 sp and 9 sp.** `DashboardScreen.kt:609` (unread count 9 sp), `:677` (OVERDUE / DUE SOON 8 sp), `:788,827,834,883,884` (fuel and stat labels 9 sp). 56 text styles in `presentation/` are 11 sp or smaller. 8 sp is too small even at default size. Minimum proposed: 11 sp. (Exact size is a taste call, the floor is not.)
7. **Light theme contrast.** From the hex values: `LightCyan 0095BB` on white is about 3.4:1, `LightGreen 009E74` about 3.4:1, `LightAmber C87D00` about 3.3:1. These fail 4.5:1 for small text. Used for the Speed readout, status words, OVERDUE badge. Dark theme `DarkMuted 6B7FA0` on `DarkCard 0D1526` is about 4.4:1, just under 4.5, and it is used for 9-12 sp labels. (Estimates by hand, not measured with a tool.) Fix: darker light-theme accents for text only, slightly lighter DarkMuted. See decision 4.
8. **Infinite animations that ignore Battery Saver.** `rememberInfiniteTransition` in `BlePairingScreen.kt:380` (radar), `SafetyScreen.kt:527` (SOS pulse), `CreateProfileScreen.kt:590`, `VehicleStage.kt:175` (scan sweep), `AnimatedSignInBg.kt` (aurora) do not read `LocalAmbientMotion`. The slow glows do. Motion rule 3 in MOTION-PLAN says ambient motion holds still in Battery Saver. SOS pulse may be kept on purpose.
9. **Large font size.** `NavigationScreen.kt:195-206` the 3 readouts sit in one Row with 20 sp values and fixed 32 dp dividers and no `maxLines` or weights. "Distance" text from Maps (for example "1.2 km") plus "ETA 125 min" plus "Speed 100 km/h" will wrap or clip at font scale 1.3 or more. `ManeuverCard` instruction (line 389) has no `maxLines` so long street names are fine but the 44 dp box and bar do not grow.

## B. Screens that differ from the rest

- **BackHeader `lifted`** is passed on About, Appearance, Legal, Statistics, Profile, PermissionDetails, Settings, Safety, Service. It is missing on **BlePairing, CreateProfile (Change Vehicle), GuestProfile, Navigation, Notifications**. Those headers never get the hairline or the title tuck. Navigation is a map, so that is fine. The others scroll.
- **Header title size**: always 16 sp Wordmark, but it shrinks 10% when lifted on some screens and not others (follows the point above).
- **Corner radius**: cards use 12, 14, 16, 17, 18, 20, 22 and 24 dp. Rough pattern: 22 for big hero cards (About, Appearance, Service, Profile), 18 for list cards (Stats, Notifications, Dashboard service), 20 for settings groups and empty states, 14 for form fields. Odd ones: 17 dp (`ServiceScreen.kt:773`), 24 dp (Safety SOS, Notifications empty art, Profile), 11 dp (Settings icons, Service, Statistics tab). Eight different radii on cards is more than a design system needs.
- **Type scale**: screens set `fontSize` by hand over a base style (`RcxType.BodySmall.copy(fontSize = 12.sp)`) instead of using the theme styles; Navigate's body uses 12 sp, 10 sp, 14 sp, 15 sp, 17 sp, 20 sp. No other screen shares exactly that set.
- **Glass**: `isGlass` is used by BackHeader (`glassButton`), but Dashboard's bell uses `cardSurface` and Navigate cards use plain `c.bg.copy(alpha=0.94f)` with a border, so in Glass mode Navigate does not look glass.
- **Splash / Intro / SignIn**: have their own full-bleed look, so they do not use BackHeader. Not a problem, just different.
- **Vehicle gallery, Profile found, Onboarding**: not read in detail (NOT TESTED).

## C. Motion list for the owner to pick from

Phase 1 is said to "need some fixes". From the code, these are the candidates. Tick the ones that bother you.

| # | Motion | Where | Concern |
|---|---|---|---|
| M1 | Greeting typed out letter by letter (45 ms a letter) | Dashboard, every first open | Delays reading your name; plays on each app start |
| M2 | Count-up of ODO / Trips, 1100 ms | Dashboard | Slow; you can not read the number until it stops. Same for Stats |
| M3 | Many ambient loops at once: floor glow, scooter hover, pill ping, service border, fuel low pulse | Dashboard | A lot going on at a glance, plus battery |
| M4 | Fuel bars fill one after another | Dashboard | Fuel is a safety read; the bars lie for about half a second |
| M5 | Route line draws itself (1400 ms + 200 ms delay) | Navigate | The map is decoration (fixed drawing, not the real route). Every visit |
| M6 | Pulsing blue dot "you are here" | Navigate | Fixed position, so it suggests live GPS when it is not |
| M7 | Bell swings 620 ms with 5 swings | Dashboard | Long for a bell |
| M8 | Shake 360 ms + buzz on refused input | Forms | OK, but 14 dp swing is big |
| M9 | Screen push uses a spring (about 500 ms) plus fade to 35% on the screen behind | All | Slowest common action; feels heavy when you go in and out often |
| M10 | enterRise stagger 55 ms x index | Dashboard, Settings, Service, Safety, Stats | 8 or more items means the last item arrives after 440 ms plus spring. Content is hidden while it builds |
| M11 | Flip card, tilt with the phone | Dashboard | Tilt follows the sensor all the time (battery); flip is hidden: no hint except "Tap to turn back" on the back |
| M12 | Header title shrinks 10% when lifted | Headers | Looks like a bug on short titles |

Same action, different treatment: sheets and lists use a mix of `tween(300..600)` (Dashboard pill 500, accent 600, arrow 400) and springs (`snappy`, `smooth`). `Motion.kt` has the vocabulary but Dashboard and Navigate still use raw `tween` (DashboardScreen.kt:417,722,855; NavigationScreen.kt:241,341,378). Two ambient-loop styles exist: `rememberBreath` (respects Battery Saver) and `rememberInfiniteTransition` (does not).

## D. Glance-ability while riding

Navigate screen, `NavigationScreen.kt:326-397`:
- The turn is a generic Navigation arrow, 20 dp in a 44 dp box, rotated by `arrowDegrees`. It is not the cluster's arrow shape, and a 20 dp icon is small for a handlebar-mounted phone.
- "IN 200 M" is 10 sp mono, the instruction is 14 sp. The distance is the most important number and is the smallest text on the card.
- The big numbers (20 sp ETA, Distance, Speed) are in the bottom sheet, not on the card.
- The card is on a map drawing that is fixed, not real.
- Bar along the bottom (3 dp) shows the turn getting nearer, good.

Dashboard: the main numbers are 22 sp Wordmark with 9 sp labels and 10 sp units. Fine for a parked look, not for riding.

Note: the cluster itself shows the turn while riding, so the phone screen may be a parked screen. The owner knows how the phone is used.

## E. OWNER DECISION

1. **Turn card size on Navigate.**
   - (a) Small, as now.
   - (b) Large turn arrow (about 72 dp) and distance (about 36 sp), instruction below at 18 sp.
   - (c) Full-width top banner, the arrow and distance fill it, bottom sheet unchanged.
   I would pick (b): the distance is what you read first and it is the smallest thing now.
2. **Card corner radius.** Options: (a) keep 8 radii; (b) three sizes only: 12 (chips, icons), 18 (cards), 24 (hero and sheets); (c) two sizes: 14 and 22. I would pick (b); it keeps the current look and removes the odd 11, 17 and 20 dp values.
3. **Motion cuts** (from section C). Options: (a) leave all; (b) cut the slow ones: typed greeting (M1), count-up to 600 ms (M2), fuel bars instant (M4), screen push to about 300 ms (M9), stagger capped at 6 items (M10); (c) (b) plus remove the fake route and dot on Navigate (M5, M6) and show a plain card. I would pick (b); the rest is fine and the owner liked the feel. M5/M6 go with the Navigate redesign in decision 1.
4. **Light theme colours.** Options: (a) leave; (b) darken cyan, green and amber only when used as text (new "text" shades), keep the bright ones for fills; (c) darken everywhere. I would pick (b); the look stays and small text becomes readable in sun, which is where a rider uses it.
5. **Ambient motion on Dashboard.** Options: (a) leave the five loops; (b) keep floor glow and connected ping, drop hover, service border and fuel pulse; (c) all loops off once the scooter is connected and moving. I would pick (b) for battery and calm.
6. **Minimum text size.** Options: (a) leave 8-9 sp labels; (b) raise everything to 11 sp; (c) 10 sp. I would pick (b).

## NOT TESTED

Real screens, contrast with a tool, font scale 1.3 to 2.0, TalkBack, dark and light side by side, glass mode, rcx-test screenshots (none for design were there when checked).

## Built 10 Oct 2026 (rcx-manager note)
Colour mixes (12), button styles (Solid / Glass / Outline) and glass option (b) with floors and lower sheet blur are built by rcx-ui (emulator-checked, not on the phone). N37 rows 3,5,6,11,12,13,14,16,20,22 are done or proven. Still open from this doc: SOS non-hold alternative and items 1-6 of the list above (turn card size, radius, motion cuts, light text colours, ambient motion, text size). Owner decisions are in docs/HANDOFF.md.

---

# PART 3 (14 Oct 2026): check of what changed since 10 Oct

Read-only pass by rcx-design (code and the 13 Oct screenshots `20261013-rcx-test-s4.png`, `s7.png`). No emulator run; rcx-test does that. Not measured: contrast, real TalkBack, font scale 1.3 to 2.0.

## What is good now (checked in code)
- Notifications: Clear asks first (confirm dialog); header actions are 48 dp; rows are Role.Button with a state description for unread; delete is also a TalkBack custom action (`NotificationsScreen.kt` ~line 232), so the swipe has a non-gesture path. Call and message rows show a label ("MESSAGE", "MISSED CALL", "x4" count read as "4 times").
- Settings rows: the toggle row is one Role.Switch with a merged name (`SettingsUi.kt:99`), link rows are Role.Button.
- Safety SOS: hold has a non-hold path ("Send SOS" custom click, `SafetyScreen.kt:572`). Call-contact button is 48 dp with a role (`SafetyScreen.kt:1046`). Part 1 item 15 is partly done (the hold gap is covered).
- Removed rows: "Connection alerts" and "Helmet reminder" are not gone from Safety (helmet row still there, see NEEDS below); no leftover empty header found.

## NEEDS (per owner, one line each)
- NEEDS: rcx-ui - SafetyScreen.kt:629, :820, :1292 - three `RcxSwitch` rows where the title is a sibling Text, so TalkBack reads "switch, off" with no name. Use the `SettingsToggleRow` pattern (toggleable on the row with Role.Switch, `clearAndSetSemantics` on the switch). Code-based; confirm on TalkBack.
- NEEDS: rcx-ui - PermissionsScreen.kt:320-323 (ServiceOffRow button) and :450-462 (PermissionCard button) - 40 dp high, no Role.Button. Make 48 dp and add role.
- NEEDS: rcx-ui - BlePairingScreen.kt ~472-475 (SystemWarning button, 42 dp, no role) - 48 dp and Role.Button.
- NEEDS: rcx-ui - BlePairingScreen.kt ~556-560 (DeviceRow "Connect", padding 9 dp, about 34 dp high) - add defaultMinSize 48 dp. Also every row says "Connect" with no vehicle name: add a semantics label "Connect to <name>".
- NEEDS: rcx-ui - components/SettingsUi.kt ~146 (SettingsLinkRow padding 10 dp, title-only row about 40 dp) - add a 48 dp minimum height.
- NEEDS: rcx-ui - NotificationsScreen.kt:224 - `onClickLabel = "Open"` is hard-coded English; use stringResource (same for "Connect" and the Permissions button labels "Enable", "Granted", "Open Settings", "REQUIRED", "Permanently denied..." which are hard-coded too).
- NEEDS: rcx-ui - NotificationsScreen.kt ~255-275 (NotificationRow title line) - title, kind label, count pill and unread dot share one Row with no maxLines. At font 1.3+ a long sender name pushes the label and count off the card. Give the title maxLines = 2 and let the label wrap below it.

## OWNER DECISION
1. **Clear dialog confirm colour.** The screen shot `s7` shows "Cancel" and "Clear all" both blue, while the header "Clear" is red. Options: (a) leave both blue; (b) "Clear all" red, Cancel stays blue; (c) both as now but dialog title says it cannot be undone (already does). I would pick (b): the one action that deletes data should look like it.
2. **Small text on Notifications.** Kind labels ("MESSAGE", "MISSED CALL") and times are 10 sp; body 12 sp. Options: (a) leave; (b) 11 sp floor for every row label (same floor as Part 2 item 6); (c) 12 sp for labels, 13 sp body. I would pick (b): the label is the only thing that tells a call from a message at a glance.
3. **Icon shape for call and message rows.** Already built: circle for people, rounded square for apps. Options: (a) keep; (b) add a small glyph badge; (c) colour only. I would pick (a): no new look needed.

## Not checked (say so)
- Contrast of the red "Clear" and "Permanently denied" text (11 sp muted on a red tint) and of the blue header actions. Estimated only; not measured.
- Permissions screen background-run row (`PermissionDetailsScreen.kt` ~line 178) only read from code; the button there was not measured.
- Radar and scan loops still use `rememberInfiniteTransition` and ignore Battery Saver (Part 2 item 8). Not re-checked this round.
- Dark and light theme on the emulator; font scale; TalkBack; rotation.
