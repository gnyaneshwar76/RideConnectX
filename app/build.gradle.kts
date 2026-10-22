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

android {
    namespace = "com.eshwar.rideconnectx"
    compileSdk = 35

    defaultConfig {
        // Must match the package registered in Firebase; the Kotlin namespace
        // above is separate and does not need to change.
        applicationId = "com.gnyaneshwar.rideconnectx"
        minSdk = 28
        targetSdk = 35
        // Raise versionCode by 1 for every APK that must install over the last one.
        // 3 = 1.0.0-tester, 4 = 1.0.1-tester (testers only). The next real release must be higher.
        versionCode = 4
        versionName = "1.0.1-tester"
        buildConfigField("boolean", "CLOUD_ENABLED", "true")
        buildConfigField("boolean", "RIDE_LOG_DEFAULT_ON", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "RIDE_LOG_DEFAULT_ON", "true")
        }
        release {
            // No signingConfig on purpose: Gradle makes an UNSIGNED APK and
            // tools/build-release.ps1 signs it with apksigner (release key plus the
            // v3 rotation lineage from the debug key, which Gradle cannot do).
            // The key password is typed by the owner and never stored on disk.
            //
            // R8 on: shrinks, and strips android.util.Log via proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
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

// Unsigned release APK: RideConnectX-v<version>-unsigned.apk. tools/build-release.ps1
// signs it into RideConnectX-v<version>.apk and writes the .sha256 beside it.
@Suppress("DEPRECATION")
android.applicationVariants.all {
    if (buildType.name == "release") {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "RideConnectX-v${versionName}-unsigned.apk"
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
        // Email-link sign-in needs ActionCodeSettings.setLinkDomain (23.2.0+): Dynamic Links are shut down.
        implementation("com.google.firebase:firebase-auth:23.2.1") { because("email link on Hosting domain") }
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
