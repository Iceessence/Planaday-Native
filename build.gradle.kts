plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

allprojects {
    group = "com.planaday.nativeapp"
    version = "1.0.0-alpha.1"
}

tasks.register<Exec>("verifyGreenfieldAndTraceability") {
    group = "verification"
    description = "Verify zero legacy files/hashes/identities and complete 652/56/50/152/28 traceability."
    commandLine("py", "${rootProject.projectDir}/scripts/verify_greenfield_and_traceability.py")
}
