# Privacy Policy — RideConnectX

**Last updated:** 22 November 2026
**Contact:** rideconnectx.app@gmail.com

RideConnectX ("the app") connects your phone to your scooter's instrument
cluster over Bluetooth Low Energy, so the cluster can show turn-by-turn
directions, a notification lamp and your name. This policy explains what the app
collects, where it goes, and what stays on your phone.

> This policy was written from an audit of what the code actually does. It is
> not legal advice.

---

## 1. The short version

- Most of what the app knows **never leaves your phone.**
- Nothing is sold, and nothing is shared with advertisers or data brokers.
- Notification content is read on-device and relayed to your scooter. It is
  **never** uploaded.
- The Bluetooth link to the cluster is not encrypted: caller and sender names cross the air in plain text. Only a few kinds of app are read: calls and missed calls (the phone app), the default SMS app, Google Messages, WhatsApp and Telegram (the important ones), and social, calendar, reminder and clock apps (the medium ones). Every other app is ignored: its notifications are never read, stored or sent. Of the apps that are read, only a short preview (60 characters at most) is kept, on the phone, and the list is cleared at midnight every day. Messages that look like a one-time code (OTP) or a bank or UPI text show only the sender name: the text itself is never stored, shown or sent anywhere, and history keeps only the sender name and the label "Message". None of it leaves the phone except the name sent to your scooter. A name with no Latin letters is shown as the fixed label "New message".
- Report a problem opens your mail app with a pre-filled message; nothing is sent unless you press send.
- While your scooter is connected, the app records your ride with GPS (precise
  location). The route of a finished ride is saved on your phone. If you do not
  keep the ride, it is deleted automatically 30 days after it ended. Only if you
  sign in **and tap Keep** is the ride, with its route, also saved to your
  account, for at most 10 rides. You can delete a ride at any time (section 7).
  One more exception: when you look up nearby service centres, your approximate
  position is sent to the OpenStreetMap Overpass service (see section 5).
- Guests upload nothing.
- You can **delete your account and its data from inside the app** at any time.

---

## 2. What is stored on your phone only

None of this is uploaded. Each item is tied to the account that created it and
is not shown to any other account signed in on the same phone:

| Data | Why |
|---|---|
| Emergency contacts (name and number you add) | So SOS can offer someone to call — works without internet |
| Service records | The service screens |
| Ride history (signed-in users also have a summary copy in their account, section 3) | The ride screens |
| Notification history, today only (the caller or sender name and the word "Call" or "Missed call", or for a message a short preview of at most 60 characters; deleted before midnight every day; ignored apps are never stored; for one-time-code and bank or UPI texts only the sender name and the label "Message" are kept, never the text) | The in-app notification list |
| Your per-app switches (app package names with on or off) and the list of important and medium apps that have posted a notification (package names only, never text) | The Settings list of apps that may show on the cluster |
| Paired vehicle address and name | To reconnect to your scooter |
| The email address you asked a sign-in link for, the time it was sent, and the times of your last link requests (to enforce a 10-minute link and 5 requests a day) | So opening the link on this phone finishes sign-in without typing it again |
| App settings and appearance | Your preferences |
| Ride recorder file (only if you switch **Settings → Ride testing → Ride recorder** on): each Google Maps instruction such as "Turn left onto MG Road", the turn sent to the cluster, the Maps destination text, calls and messages with no names and no text (the file never holds who called or wrote), and route start and end times. It is a file in the app's own storage folder on the phone, never uploaded, and you can delete it from the same screen. | Checking that the right turns reach the cluster |

Backup is disabled, so none of this is copied into Google cloud backup or moved
in a device-to-device transfer.

**Emergency contacts are other people's details.** Only add people who have
agreed to be your emergency contact.

## 3. What is stored in your account

If you sign in, the following is saved to Firebase (Google Cloud) under your
account, and security rules make it readable only by you:

- Your name, email address and sign-in method
- Rider name, nickname and city
- Selected vehicle model and colour
- Account dates (when the account was created and last signed in) and a default settings record (theme, units, voice guidance, auto-connect, map type, route avoidances, notifications). These defaults are created when you first sign in; the app does not currently change them.
- Profile photo, if you set one, stored at a reduced size
- Your scooter's last odometer, trip and fuel readings, saved when you sign
  out so they are there when you sign back in
- Your last 10 sign-ins: time, sign-in method (Google, email and password, or email link), phone model, Android version and app version. No IP address, no location and no device ID. You can see them in Profile → Recent sign-ins, to spot a sign-in that was not you; older ones are deleted automatically. This is kept so you can check your account's security.
- Rides you **keep** (maximum 10): start and end time, distance, duration, top
  and average speed, ride title, vehicle and the route (a list of GPS points,
  which is precise location history). Rides you do not keep are never uploaded.
  Deleting a ride in the app also deletes its cloud copy.

You can use the app as a **guest**, in which case nothing is uploaded at all.

## 4. Permissions, and exactly what each is for

| Permission | What the app does with it |
|---|---|
| **Bluetooth / nearby devices** | Find and connect to your scooter's cluster. Used for nothing else. |
| **Notification access** | Read the notification that Google Maps posts while navigating, so the next turn can be drawn on your cluster; and detect that a call or message arrived so the cluster's lamp lights. Only these groups are read: calls and missed calls, the default SMS app, Google Messages, WhatsApp and Telegram (important, shown on the cluster unless you turn the app off), and social, calendar, reminder and clock apps (medium, kept in the in-app list and shown on the cluster only if you turn that app on). All other apps are ignored: never read, stored or sent. For the apps that are read, the app uses the caller or sender name and a short preview of the message (60 characters at most). The preview is kept on the phone for the day only, and for one-time-code and bank or UPI texts only the sender name is used, never the text. **Content is processed on the phone and sent only to your scooter.** It is never uploaded. |
| **Location** | Sharing your position when *you* tap share on the SOS screen; filling in your city on your profile; while you navigate, comparing your GPS position with the next turn to count down the distance on the cluster (on the phone only, not stored); recording your ride route while the scooter is connected (saved on the phone; uploaded only for rides you keep while signed in); and finding nearby service centres (section 5). On Android 11 and older, Android also requires it for Bluetooth scanning. The app records a route only for the ride while the scooter is connected, and you decide whether to keep it. |
| **Contacts** | Only when you tap to pick an emergency contact. The app keeps that one contact's name and number. |
| **Photos** | Only when you choose a profile picture. |
| **Phone state** | Only to read your mobile signal strength, which is shown as bars on the cluster. The app does not read your calls, call log or phone number. |
| **Notifications (post)** | Show the ongoing notification Android requires while the app holds the Bluetooth link. |

The app deliberately does **not** request permission to read SMS, read your call
log, or place calls. Calls open your dialer with the number filled in; you press
the call button.

## 5. Other services, and no analytics

The app has **no analytics, no advertising and no advertising identifier.**
It uses these outside services:

- **Firebase Authentication and Cloud Firestore (Google)** store your sign-in
  and the account data in section 3, only when you sign in. Google Sign-In is
  used if you choose it. Sign-in link, verification and password-reset emails
  are sent from rideconnectx.app@gmail.com. Google's handling is described at
  <https://firebase.google.com/support/privacy>.
- **OpenStreetMap Overpass (overpass-api.de)**: when you pick a service centre,
  your position, rounded to about 1 km, and a search radius are sent to find places nearby.
  Like any web request, the service also sees your IP address. Nothing is
  linked to your account. This optional "service centres near me" lookup uses a
  service we do not operate. Map data (c) OpenStreetMap contributors, available
  under the Open Database Licence: <https://www.openstreetmap.org/copyright>.
- **Firebase App Check (Google)** confirms to Firebase that requests come from this app, not from a copy or script. It exchanges an attestation token with Google; it carries no personal data from your profile.
- **Android Geocoder**: turns coordinates into a place or city name. On most
  phones this is done by Google's location service, under Google's terms.

## 6. What is never collected

- Full message text or call content. A caller or sender name is relayed to the
  cluster, and a short preview (60 characters at most) is kept in the in-app
  notification list on your phone for that day only; nothing about notifications
  is uploaded, and notifications from ignored apps are not even read
- The history and the list of apps: they never reach Firestore, Analytics or any server
- Ride log is off by default and stays off until you switch it on.
- A history of where you have travelled outside rides: routes are recorded only while the scooter is connected, and are uploaded only for rides you keep
- Your address book as a whole
- Payment information — the app takes no payments

## 7. Your choices

- **Use it as a guest** — nothing is uploaded.
- **Sign out** — your last readings are saved to your account, and your profile
  and readings are removed from the phone.
- **Per-app switches** — Settings lists the apps that have posted; turn any of them off or on. Notifications are cleared by the app every day.
- **Delete local data** — wipes rides, notifications, service records, emergency
  contacts, the paired vehicle, cached readings, the ride log, your avatar and
  profile preferences from the phone.
- **Delete your account** — in the app: **Profile → Delete account**. After you
  confirm it is you, your cloud data (profile, photo, readings, rides), your account, and that account's data on the phone are permanently
  deleted, including your sign-in history. Without the app, request deletion at
  <https://rideconnectx-89528dd3.web.app/delete-account.html>.
- **Revoke any permission** at any time in Android Settings. The app degrades
  rather than breaking: without location, SOS tells you it cannot share a
  position instead of inventing one.

## 8. Your rights

Under India's Digital Personal Data Protection Act, 2023, and similar laws
elsewhere, you can ask to access, correct or erase your personal data, and to
withdraw consent. Most of this you can do yourself in the app; for anything
else, email the contact at the top. Requests are answered within 30 days. If
you are not satisfied, you may complain to the Data Protection Board of India.

## 9. Children

The app is not directed at children under 13 and is not intended for them. It is
a companion to a motor vehicle.

## 10. Data retention

Local data stays until you delete it, sign out, or uninstall. Account data stays
until you delete your account, and is then removed immediately.

## 11. Changes

If this policy changes materially, the app will say so before the change takes
effect. The date at the top always reflects the current version.

---

## Notice

RideConnectX is an independent application. It is not produced, endorsed,
certified or supported by any vehicle manufacturer or Google LLC. Trademarks
belong to their respective owners.

**Safety:** what the app draws on your cluster is an aid, not a substitute for
your attention to the road or to the vehicle's own instruments. Do not interact
with your phone while riding.
