plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.planaday.nativeapp.android"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        // Fresh independent Android application ID (Owner Decision O-004: coexists cleanly alongside legacy installations)
        applicationId = "com.planaday.nativeapp"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0-alpha.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:math"))
    implementation(project(":core:documents"))
    implementation(project(":core:graphics"))
    implementation(project(":core:security"))
    implementation(project(":core:interchange"))
    implementation(project(":core:persistence"))
    implementation(project(":core:search"))
    implementation(project(":core:automation"))
    implementation(project(":core:notifications"))
    implementation(project(":core:sync-protocol"))
    implementation(project(":core:sync-client"))
    implementation(project(":core:usecases"))
    implementation(project(":core:design-tokens"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(compose.material3)
    implementation(compose.ui)
}
