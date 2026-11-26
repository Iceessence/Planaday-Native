import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)
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
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(compose.desktop.uiTestJUnit4)
}

tasks.test {
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}

compose.desktop {
    application {
        mainClass = "com.planaday.nativeapp.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Planaday Native"
            packageVersion = "1.0.0"
            description = "Planaday Native — Offline-First Personal & Laboratory Productivity OS"
            vendor = "Planaday Native"
            modules(
                "java.desktop",
                "java.instrument",
                "java.logging",
                "java.management",
                "java.naming",
                "java.net.http",
                "java.sql",
                "java.xml",
                "jdk.crypto.ec",
                "jdk.unsupported",
            )
            windows {
                menuGroup = "Planaday Native"
                perUserInstall = true
                // Fresh independent MSI Upgrade UUID (Owner Decision O-004: distinct from any legacy installer)
                upgradeUuid = "9B7E4E52-3C8A-4D7E-A156-7F41C9E2B604"
                shortcut = true
                dirChooser = true
            }
        }
    }
}
