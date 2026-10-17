// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.google.services) apply false
    // Dependency vulnerability scanning. Run `./gradlew dependencyCheckAnalyze`;
    // it downloads the NVD CVE feed on first use, so it needs network and is
    // meant for CI rather than every local build.
    id("org.owasp.dependencycheck") version "10.0.4"
}


dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JSON")
    // The NVD API is rate-limited without a key. Set NVD_API_KEY in CI to avoid
    // an hours-long first sync; a free key comes from nvd.nist.gov.
    nvd { apiKey = System.getenv("NVD_API_KEY") ?: "" }
    analyzers {
        assemblyEnabled = false
        nodeAudit { enabled = false }
        nodeEnabled = false
    }
}
