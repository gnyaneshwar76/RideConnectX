import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
}

/*
 * The google-services plugin hard-fails if google-services.json is absent, which
 * would block every build until Firebase is registered. Applying it conditionally
 * keeps the project buildable now and switches Firebase on automatically the
 * moment the file is dropped into app/.
 */
val firebaseConfig = file("google-services.json")
if (firebaseConfig.exists()) {
    apply(plugin = "com.google.gms.google-services")
    logger.lifecycle("Firebase: google-services.json found — Firebase enabled.")
} else {
    logger.lifecycle("Firebase: google-services.json missing — running without Firebase.")
}

/*
 * Release signing, same conditional trick as Firebase above. The keystore and its
 * passwords live in keystore.properties, which git ignores and nobody but the
 * rider ever sees. Until that file exists, release builds stay unsigned and the
 * build still passes. Expected keys: storeFile, storePassword, keyAlias, keyPassword.
 */
val keystoreProps = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

/*
 * Version from the release tag: ./gradlew assembleRelease -PreleaseTag=v0.9.0-beta
 * versionName = tag without "v" ("0.9.0-beta").
 * versionCode = (major*10000 + minor*100 + patch) * 10 + suffix rank, so a later
 * version always has a higher code and a pre-release sorts below its final:
 * none 9, rc 7, beta 5, alpha 3, any other suffix 1. v0.9.0-beta = 9005.
 * Without a tag: versionName "0.9.0", versionCode from -PversionCode or 1 (as before).
 */
val releaseTag = (project.findProperty("releaseTag") as String?) ?: System.getenv("RELEASE_TAG")
val tagMatch = releaseTag?.let { Regex("""v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.]+))?""").matchEntire(it.trim()) }
if (releaseTag != null && tagMatch == null) {
    throw GradleException("releaseTag '$releaseTag' is not like v1.2.3 or v1.2.3-beta")
}
val tagVersionName = tagMatch?.let { m ->
    m.groupValues.let { g -> "${g[1]}.${g[2]}.${g[3]}" + if (g[4].isNotEmpty()) "-${g[4]}" else "" }
}
val tagVersionCode = tagMatch?.let { m ->
    val (ma, mi, pa) = m.groupValues.drop(1).take(3).map { it.toInt() }
    val rank = when (m.groupValues[4].substringBefore('.').lowercase()) {
        "" -> 9; "rc" -> 7; "beta" -> 5; "alpha" -> 3; else -> 1
    }
    (ma * 10000 + mi * 100 + pa) * 10 + rank
}

android {
    namespace = "com.eshwar.rideconnectx"
    compileSdk = 35

    defaultConfig {
        // Must match the package registered in Firebase; the Kotlin namespace
        // above is separate and does not need to change.
        applicationId = "com.gnyaneshwar.rideconnectx"
        minSdk = 28
        targetSdk = 35
        // Play needs a higher number each upload: ./gradlew bundleRelease -PversionCode=2
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        if (tagVersionCode != null) versionCode = tagVersionCode
        versionName = tagVersionName ?: "0.9.0"
        buildConfigField("boolean", "CLOUD_ENABLED", "true")
        buildConfigField("boolean", "RIDE_LOG_DEFAULT_ON", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "RIDE_LOG_DEFAULT_ON", "true")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // R8 on: shrinks, and strips android.util.Log via proguard-rules.pro.
            // Without it every Log.d survived into release — BLE packets in hex,
            // caller names, message senders and the Firebase uid, all readable
            // over ADB by anyone holding the phone.
            //
            // NOT YET SMOKE-TESTED ON HARDWARE. Obfuscation breaks at runtime,
            // not at compile time. Install a release build and exercise sign-in,
            // the BLE link and a real turn before trusting it.
            isMinifyEnabled = true
            isShrinkResources = true

            // An unsigned release APK is not installable and cannot be uploaded
            // to Play, but the build used to succeed anyway and say nothing —
            // you only found out at the store. Fail loudly instead.
            if (keystoreProps == null) {
                tasks.matching { it.name == "packageRelease" || it.name == "bundleRelease" || it.name == "packageReleaseBundle" }.configureEach {
                    doFirst {
                        throw GradleException(
                            "keystore.properties is missing, so this release would be UNSIGNED " +
                                "and cannot be installed or uploaded to Play. Create it at the " +
                                "project root with storeFile, storePassword, keyAlias, keyPassword. " +
                                "For an unsigned test build use assembleDebug or assembleSmooth."
                        )
                    }
                }
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Release speed for testing on your own phone: R8-optimised and not
        // debuggable (debug builds run Compose several times slower, so they
        // are no measure of smoothness), but signed with the debug key so it
        // installs over the debug app and Google sign-in keeps working.
        create("smooth") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // Screen 23 (About) reads the version and build number from BuildConfig
        // rather than hardcoding them, so they can never drift out of date.
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Release APK is named RideConnectX-v<version>.apk and gets a .sha256 beside it.
// Only the release variant; debug and smooth keep their normal names.
@Suppress("DEPRECATION")
android.applicationVariants.all {
    if (buildType.name == "release") {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "RideConnectX-v${versionName}.apk"
        }
    }
}
tasks.matching { it.name == "packageRelease" }.configureEach {
    doLast {
        outputs.files.files.flatMap { f -> f.walkTopDown().filter { it.extension == "apk" }.toList() }.forEach { apk ->
            val sum = MessageDigest.getInstance("SHA-256").digest(apk.readBytes())
                .joinToString("") { "%02x".format(it) }
            File(apk.parentFile, apk.name + ".sha256").writeText("$sum  ${apk.name}" + System.lineSeparator())
        }
    }
}

hilt {
    enableAggregatingTask = true
}

dependencies {
    // CVE-2024-7254 (L1): Firebase BoM 33.7.0 resolves protobuf-javalite 3.25.1.
    // Constraint instead of a BoM bump so nothing else moves; remove when the BoM ships >= 3.25.5.
    constraints {
        listOf("protobuf-javalite", "protobuf-java", "protobuf-kotlin-lite").forEach {
            implementation("com.google.protobuf:$it:3.25.5") { because("CVE-2024-7254") }
        }
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Daily service-reminder check while the app is closed (N9).
    implementation(libs.androidx.work.runtime.ktx)

    // Navigation & Hilt
    implementation(libs.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Room — ride history for Statistics
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Firebase — Auth (email/password, Google, phone) and Firestore
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // App Check — proves to Firebase that a request came from THIS app, not from
    // a script someone built after pulling google-services.json out of the APK.
    // Rules stop cross-user reads; they do not stop quota abuse. This does.
    implementation(libs.firebase.appcheck)
    debugImplementation(libs.firebase.appcheck.debug)
    "smoothImplementation"(libs.firebase.appcheck.debug)
    implementation(libs.coroutines.play.services)

    // The in-app "Turn on location?" dialog. Without it the only route is
    // the system Location settings page, which the rider has to navigate.
    implementation(libs.gms.location)

    // ponytail: no Haze code is left, but it pulls Compose 1.8.0 over the BOM's
    // 1.7.6 and the app was tuned on that. Bump the Compose BOM, then delete this.
    implementation(libs.haze)

    // Credential Manager, for Google Sign-In
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
