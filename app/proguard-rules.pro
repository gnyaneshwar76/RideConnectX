# ── RideConnectX release rules ────────────────────────────────────────────────
# R8 is enabled for release. The app is Compose + Hilt + Firebase + Room, and
# each of those ships its own consumer rules, so this file only covers what is
# specific to us or known to need help.

# Kotlin metadata and coroutines internals that reflection touches.
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# Firebase/Firestore maps documents onto these by reflection, so field names
# must survive. CloudProfile is what restores a rider's account.
-keepclassmembers class com.eshwar.rideconnectx.data.remote.** {
    <init>();
    <fields>;
}
-keepclassmembers class com.eshwar.rideconnectx.domain.model.** {
    <init>();
    <fields>;
}


# Enum valueOf() is used by the DataStore settings (`toEnum`), which looks the
# constant up by name — obfuscating the names breaks every stored preference.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# The debug broadcast receiver used for cluster sweeps is referenced only by
# name from adb, never from code. Debug build only: no class exists in this package in src/main.
-keep class com.eshwar.rideconnectx.debug.** { *; }

# Hilt entry point. (The BLE service is kept by the manifest rules AGP adds.)
-keep interface com.eshwar.rideconnectx.core.di.** { *; }

# --- Strip debug logging from release builds ---
# Log.d/v/i carry BLE packet hex, caller names, message senders and the account
# id. They are useful during development and have no business in a shipped APK.
# Warnings and errors are kept so a real fault is still diagnosable.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# --- Keep what reflection and serialization need ---
# Firestore builds these back from documents by field name; obfuscating them
# silently empties the rider's profile rather than failing loudly.

# Room, Hilt and Firebase ship their own consumer rules; these cover the
# app's own types that cross those boundaries.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Harder to read after decompiling: flatten our packages into one unnamed package
# and hide source file names. Line numbers stay so crash reports still map back.
-repackageclasses ''
-allowaccessmodification
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
