plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    jvm()
    androidTarget()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":core:model"))
                api(project(":core:domain"))
                api(project(":core:math"))
                api(project(":core:documents"))
                api(project(":core:graphics"))
                api(project(":core:security"))
                api(project(":core:interchange"))
                api(project(":core:persistence"))
                api(project(":core:search"))
                api(project(":core:automation"))
                api(project(":core:notifications"))
                api(project(":core:sync-protocol"))
                api(project(":core:sync-client"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val jvmSharedMain by creating { dependsOn(commonMain) }
        val jvmMain by getting { dependsOn(jvmSharedMain) }
        val androidMain by getting { dependsOn(jvmSharedMain) }
    }
}

android {
    namespace = "com.planaday.nativeapp.core.usecases"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
