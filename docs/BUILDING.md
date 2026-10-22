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
| `assembleRelease` | R8 + resource shrink, not debuggable, logs stripped, no debug hooks, Play Integrity App Check. Gives an UNSIGNED `RideConnectX-v<version>-unsigned.apk`; sign it with the script below |

The version is set in `app/build.gradle.kts` (`versionName`, `versionCode`). Raise
`versionCode` by 1 for every APK that must install over the previous one.

## Release signing (owner)
The release key is the owner's own keystore, kept outside the repo. Its password is
never written in any file. Gradle does not sign the release; the script does.

Run from the repo root, then type the key password when asked:
```
powershell -ExecutionPolicy Bypass -File tools\build-release.ps1
```
Output: `app/build/outputs/apk/release/RideConnectX-v<version>.apk` and
`RideConnectX-v<version>.apk.sha256` (the name follows `versionName`). For the tester
build (`versionName` 1.0.0-tester, versionCode 3) that is `RideConnectX-v1.0.0-tester.apk`.
The published 1.0.0 APK (versionCode 2) is not rebuilt by this. The script prints the signer certificate at the end.

What the script does:
- Runs `assembleRelease`, then `apksigner` from the newest Android build-tools.
- Asks for the password with a hidden prompt, puts it in an environment variable of
  its own process only after Gradle is done, and clears it at the end, also on errors.
- Options: `-KeyStore <path>` (default `..\RideConnectX-keys\rideconnectx-release.jks`
  next to the repo folder), `-KeyAlias` (default `rideconnectx`).

### Key rotation (why the old phone keeps its data)
Builds up to 0.9.0 were signed with the Android debug key
(`~/.android/debug.keystore`, alias `androiddebugkey`). Android only updates an app in
place if the new APK is signed by the same key, or by a key the old key has approved.
The script uses APK Signature Scheme v3 key rotation:
- On the first run it creates `rideconnectx-lineage.bin` next to the keystore with
  `apksigner rotate` (debug key -> release key). The file holds only public
  certificates and signatures, no private key. It stays out of git.
- Every APK is signed by both keys with `--lineage` and `--rotation-min-sdk-version 28`,
  so every supported Android version (9+) sees the release key.
- A phone with the debug-signed 0.9.0 updates with no uninstall and keeps its data.
  A fresh install just sees the release key. Proven on the emulator with a throwaway key.
- After that update, a debug-signed build (debug, smooth) can no longer be installed
  over it: Android does not let the key go back. Install release builds only, or
  uninstall first (this deletes the app data).
- The debug keystore on this laptop is needed to sign. If it is lost, the lineage file
  still lets new APKs update phones that already have 1.0.0, but not older ones.

### Back up the key
Copy these to a USB stick (and a second place), never to git or a cloud chat:
- `rideconnectx-release.jks`
- `rideconnectx-lineage.bin`
- `~/.android/debug.keystore`
Write the password on paper and keep it apart from the stick. If the release key or
its password is lost, no future version can update the installed app.

### Never commit
`keystore.properties`, `*.jks`, `*.keystore`, `google-services.json`. The build does
not read `keystore.properties` any more; do not create one with a password in it.

### Your own fork
Make your own key with
`keytool -genkeypair -v -keystore your.jks -alias yourname -keyalg RSA -keysize 2048 -validity 10000`
and run the script with `-KeyStore your.jks -KeyAlias yourname`. If you have no
debug-signed installs to carry over, the debug signer in the lineage does no harm.
