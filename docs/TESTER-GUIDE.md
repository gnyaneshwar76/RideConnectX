# RideConnectX tester guide (version 1.0.0-tester)

Thank you for testing. This guide is short and simple. Do the steps in order.
At any step, if you do not see what the guide says, tap **Report a problem**
(see "How to report a problem" at the end). Please do not skip a problem.

RideConnectX shows Google Maps turn arrows, calls and messages on your bike's Bluetooth dashboard.
It is a hobby app. It is not made by, or connected with, any vehicle maker.

You need: an Android phone (Android 9 or newer), Google Maps, and a bike with a
Bluetooth dashboard. You can test most of the app without the bike.

## 1. Download and install

1. On your phone, open the GitHub page: https://github.com/gnyaneshwar76/Ride_Connect_X/releases
2. Find the release called **1.0.0-tester**. Tap the file `RideConnectX-v1.0.0-tester.apk` to download it.
3. Android may say the file can harm your phone, or ask you to allow installs from your browser or file manager. Allow it for this one time. This is normal for apps outside the Play Store.
4. Open the downloaded file and tap **Install**.
5. You should see the app "RideConnectX" in your app list. If not, email the owner (see the end of this guide).

Already have RideConnectX on the phone?
- You do not need to uninstall. This tester build is signed with the same release key as 1.0.0. It installs over 1.0.0. An old debug-signed 0.9.0 also updates in place and keeps its data (key rotation).
- Only a debug or "smooth" test build made by the developer cannot be installed over a release build. If Android says "App not installed", uninstall RideConnectX first, then install again. Uninstalling deletes the app's data on the phone.

## 2. First start and permissions

1. Open RideConnectX. You see a few intro pages. Swipe or tap to go on.
2. Tap **Continue as Guest** (you can sign in later, see step 12).
3. Type a name (for example "Alex") and continue. Nothing else is needed.
4. The Permissions page opens. Android asks for each permission. Tap **Enable** and then **Allow**. What each one is for:
   - **Bluetooth**: to talk to your bike's dashboard.
   - **Location**: Android needs it to find Bluetooth devices. The app also uses it to record your rides.
   - **Notifications**: to show the "connected" notice, service reminders and ride summaries.
   - **Phone status**: to read your signal strength so the dashboard can show the bars. If you say no, the dashboard shows no bars.
   - **Contacts** (asked later, only when needed): to pick an emergency contact, and to show a caller's name instead of a number. Contacts stay on your phone.
   - **Photos and media** (asked later): only to set your profile picture. You can allow only selected photos.
5. Also turn on Bluetooth and location on the phone if the page says they are off. Tap the button it shows.
6. You should see a green or "Granted" mark for each one. If one says "Permanently denied", tap **Open Settings** and allow it there.
7. The permission to read notifications is special and is asked separately: see step 5.
8. If this page does not behave as written: tap Report a problem.

## 3. Pair the bike's Bluetooth dashboard

1. Switch the bike on. Keep the phone close to the dashboard.
2. In the app, go to the Pair screen and start the scan.
3. You should see your dashboard in the list. Tap it.
4. Your phone may ask to pair. Accept. You should see "Connected" in the app and a connected icon on the dashboard.
5. Turn the bike off and on again. The app should connect again by itself.
6. If the dashboard is not listed, or does not connect: tap Report a problem. Tell us the bike model.

## 4. Turn arrows with Google Maps

1. In RideConnectX, make sure it is connected (step 3).
2. Open Google Maps. Choose a place and tap **Start** for a driving route (or the bike mode if you have it).
2a. If the Navigation page shows the card "Google Maps notifications look off", tap **Turn on** and enable the Maps notifications.
3. Go back to RideConnectX once to check it says it sees the route.
4. The dashboard should show the next turn arrow and the distance. It changes at each turn.
5. At the end of the route the dashboard should show the arrival sign.
6. If the arrow is wrong, missing, or late: tap Report a problem. Write what Maps showed and what the dashboard showed. A photo of the dashboard helps.

## 5. Calls and messages on the dashboard

1. Allow **notification reading** for RideConnectX. The app sends you to Android settings. Switch RideConnectX on. This lets it read the Google Maps notification and call and message alerts.
   If Notification access is greyed out or blocked, you installed the app from a file. On Android 13 and newer, go to Settings > Apps > RideConnectX > App info, tap the three dots (top right), tap Allow restricted settings, then go back and turn Notification access on.
2. Ask a friend to call you. The dashboard should show the caller's name or number.
3. Ask a friend to send you a message. The dashboard should show the sender's name. The message text is never sent to the dashboard and is not stored.
4. Open the **Notifications** page in the app. You should see the call or message names there (on your phone only).
5. If nothing shows: tap Report a problem.

## 6. Live ride and saved rides

1. Turn on location. Start moving (walk, cycle, ride or drive as a passenger). Open the **Riding now** card.
2. You should see distance, time, speed, average speed and max speed. The average should never be higher than the max.
3. Stop. The ride is saved. Open the ride in the ride list. You should see a route map and the numbers.
4. **Keep and Delete.** Every ride shows **Keep ride** and **Delete**. Delete asks "Delete this ride?" first.
5. **30-day rule.** A ride you do not keep is deleted by itself 30 days after it ended. A ride you keep stays.
6. Guest mode keeps rides on the phone only. If you sign in, kept rides are copied to your account (up to 10).
7. Wrong distance, speed, or no ride saved? Tap Report a problem.

## 7. Service and fuel

1. Open the **Service** screen.
2. Add a service record: centre name, date and odometer reading. Save it.
3. You should see it in the list, and the next service counted from your latest record.
4. On the **Dashboard**, check the **FUEL** bar. It shows **E**, **F** or **RESERVE**, and it moves when the bike's fuel reading changes. There is no fuel entry to type in.
5. Delete a record. It asks first ("Delete this record?").
6. Wrong or missing data? Tap Report a problem.
7. Note: the service-centre lookup sends an approximate position (about 1 km) to a map service to find centres near you.

## 8. Safety contacts

1. Open the **Safety** screen.
2. Add an emergency contact: a name (2 to 30 letters) and a phone number (10 to 15 digits). You can pick from your contacts if you allow the permission.
3. You should see the contact saved on the screen.
4. Do NOT test the emergency button with a real contact unless you warned them first.
5. Anything odd? Tap Report a problem.

## 9. Appearance (themes)

1. Open **Settings**, then **Appearance**.
2. Change the **Theme** (follow phone, light, dark), **Text size**, **Accent colour**, **Colour mix**, **Button style** and **Surface style**.
3. You should see the preview and the whole app change at once.
4. Is any text hard to read or a button cut off? Tap Report a problem and say which theme.

## 10. Sign in with Google

1. Open the sign-in page (from Settings or Profile if you are a guest).
2. Tap **Continue with Google** and choose your Google account.
3. You should see your name and picture on the Profile page.
4. If it fails, tap Report a problem.

## 11. Sign in with an email link

1. On the sign-in page tap **Continue with Email**, then **Email me a sign-in link**. Type your email.
2. Open the mail on the same phone. Tap the link. The app should open and sign you in. (This has NOT been proven on a real phone yet, so your result matters a lot.)
3. Limits:
   - The link works for **10 minutes** and **one time only**.
   - After you ask for a link, wait **60 seconds** before you ask again.
   - You can ask for at most **5 links in 24 hours on one phone**.
   - If the mail service is overloaded you see: "Sign-in emails are very busy today. Please try Google sign-in or try again tomorrow."
4. Mail not arriving? Check the spam folder. Then tap Report a problem.

## 12. Recent sign-ins

1. Open **Profile**.
2. Find **Recent sign-ins**. You should see your last sign-ins (up to 10): time, method, phone model and Android version. No IP address and no location.
3. If it is empty or wrong, tap Report a problem.

## 13. Delete your account

1. Open **Profile** and tap **Delete account**.
2. A popup asks you to confirm. Read it, then confirm.
3. If you signed in with an **email link**, the app first asks you to sign in again, then you delete. This is for your safety.
4. You should be signed out. Your cloud profile and cloud rides are removed.
5. Anything else happens? Tap Report a problem.

## 14. Uninstall

1. Press and hold the RideConnectX icon, then tap **Uninstall** (or use Settings > Apps).
2. This removes the app and its data on the phone.
3. Also unpair the bike from the phone's Bluetooth settings if you want.

## If Android blocks Notification access

On Android 13 and newer, an app installed from a file (not from the Play Store) cannot get Notification access at first: the switch is greyed out, or Android says "Restricted setting".

1. Open **Settings > Apps > RideConnectX**.
2. Tap the **three dots** at the top right.
3. Tap **Allow restricted settings**. Confirm with your PIN or fingerprint if asked.
4. Go back to RideConnectX and turn on Notification access again.
5. RideConnectX shows these steps itself ("Switch greyed out?") with a button that opens the app info page.
6. No three dots or no such option? Your phone may name it differently. Tap Report a problem and say what you see.

## Play Protect warning when installing

Google Play Protect may say the app is unknown or was blocked.

1. Tap **More details**.
2. Tap **Install anyway**.
3. **Do not switch Play Protect off.** It protects your phone from other apps; RideConnectX does not need it off.

## How to report a problem

1. Open RideConnectX. Go to **About**, then **Information**. Tap **Report a problem**.
2. Your mail app opens with a message already filled in (app version, phone model, Android version).
3. Write what you did, what you saw, and what you expected. Add screenshots or a photo of the dashboard.
4. Send it to **rideconnectx.app@gmail.com**.
5. If no mail app opens, the app shows the address. Write to it yourself.
6. Your ride logs and screenshots go **only to the owner, by email**. They are not posted anywhere.

## Known issues / not yet proven

- Only one bike model is proven on real hardware. Other models are in the code but unproven. Your result is useful, good or bad.
- The email link has not been tapped on a real phone yet. Cloud sign-in limits and the "emails are busy" message are only checked in tests.
- The real mail compose screen for Report a problem has not been seen on a real phone.
- Phone signal bars on the dashboard are not fully proven. They may look different from your phone.
- Roundabout exit arrows are unconfirmed.
- GPS rides, and the pairing scan that lists every supported dashboard, are not yet proven on a real ride.
- The Bluetooth link to the dashboard is not encrypted. Caller and sender names travel in plain text over Bluetooth while paired. Message text is never sent.
- The app is unofficial. It is not made by any vehicle maker.

Version of this guide: matches 1.0.0-tester. Facts not checked on a real tester phone: the exact button names on the Pair screen, where the sign-in page opens from, the exact wording of the Delete account popup, and whether the tester release is already public on GitHub.
