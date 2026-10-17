# Building RideConnectX

## You need
- JDK 17 (Android Studio's bundled JBR works).
- Android SDK with platform 35 (Android Studio installs it). Gradle finds it from
  `ANDROID_HOME` or `local.properties` (`sdk.dir=...`, auto-written by Android Studio).
- An internet connection for the first Gradle sync.

## Debug build (works with no private files)
```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
Debug uses a simulated Access 125 scooter, so no hardware is needed.
Without `app/google-services.json` the build still passes and the app runs
without Firebase (no sign-in or cloud profile; BLE, navigation and local data work).

## Add your own Firebase (optional)
1. Create a project at https://console.firebase.google.com.
2. Add an Android app with package `com.gnyaneshwar.rideconnectx`.
3. Enable Authentication (Email/Password, Google) and Firestore. Deploy `firestore.rules`.
4. Download `google-services.json` into `app/`. It is git-ignored; never commit it.
5. Add your debug SHA-1 in the console if you use Google sign-in.

## Build types
| Task | Use |
|---|---|
| `assembleDebug` | Emulator or dev phone, simulated scooter, debug-signed |
| `assembleSmooth` | R8-optimised, debug-signed, for your own phone |
| `assembleRelease -PreleaseTag=v0.9.0-beta` | Signed release, RideConnectX-v0.9.0-beta.apk + .sha256; needs your keystore; versionCode = (maj*10000+min*100+patch)*10 + rank (none 9, rc 7, beta 5, alpha 3) |

## Release signing
The app never ships a key. Create `keystore.properties` in the repo root
(git-ignored):
```
storeFile=path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```
Without it, `assembleRelease`/`bundleRelease` fail with a clear message.
Generate a key with `keytool -genkeypair -v -keystore your.jks -alias rcx -keyalg RSA -keysize 2048 -validity 10000`.
Keep the keystore and passwords outside the repo and back them up.
