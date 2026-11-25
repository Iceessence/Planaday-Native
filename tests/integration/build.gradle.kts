plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(project(":core:model"))
    testImplementation(project(":core:domain"))
    testImplementation(project(":core:math"))
    testImplementation(project(":core:documents"))
    testImplementation(project(":core:graphics"))
    testImplementation(project(":core:security"))
    testImplementation(project(":core:interchange"))
    testImplementation(project(":core:persistence"))
    testImplementation(project(":core:search"))
    testImplementation(project(":core:automation"))
    testImplementation(project(":core:notifications"))
    testImplementation(project(":core:sync-protocol"))
    testImplementation(project(":core:sync-client"))
    testImplementation(project(":core:usecases"))
    testImplementation(project(":core:design-tokens"))
    testImplementation(project(":server:app"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.sqlite.bundled)
    testImplementation(libs.sqlite.jdbc)
}

tasks.test {
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}
