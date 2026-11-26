import java.time.Instant
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

val generatedBuildMetaDir = layout.buildDirectory.dir("generated/source/planadayBuildMeta/commonMain/kotlin")

val generateBuildMetaTask = tasks.register("generatePlanadayBuildMeta") {
    val outDir = generatedBuildMetaDir
    outputs.dir(outDir)
    outputs.upToDateWhen { false }
    doLast {
        fun runGit(vararg args: String): String = try {
            val proc = ProcessBuilder(listOf("git") + args.toList())
                .directory(rootProject.projectDir)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            if (proc.waitFor() == 0) out else ""
        } catch (_: Throwable) {
            ""
        }
        val shortSha = runGit("rev-parse", "--short=12", "HEAD").ifEmpty { "unknown-commit" }
        val fullSha = runGit("rev-parse", "HEAD").ifEmpty { "unknown-commit" }
        val dirty = runGit("status", "--porcelain").isNotEmpty()
        val effectiveSha = if (dirty) "$shortSha-dirty" else shortSha
        val builtAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val target = outDir.get().file("com/planaday/nativeapp/core/model/GeneratedBuildMeta.kt").asFile
        target.parentFile.mkdirs()
        target.writeText(
            """
            package com.planaday.nativeapp.core.model

            /** Auto-generated from live Git HEAD during Gradle build (Update 2 §36). */
            object GeneratedBuildMeta {
                const val GIT_COMMIT_SHA: String = "$effectiveSha"
                const val GIT_FULL_SHA: String = "$fullSha"
                const val GIT_WORKTREE_DIRTY: Boolean = $dirty
                const val BUILD_TIMESTAMP_UTC: String = "$builtAt"
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )
    }
}

kotlin {
    jvmToolchain(21)
    jvm()
    androidTarget()

    sourceSets {
        commonMain {
            kotlin.srcDir(generatedBuildMetaDir)
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.configureEach {
    if (name.startsWith("compile") && name.contains("Kotlin")) {
        dependsOn(generateBuildMetaTask)
    }
}

android {
    namespace = "com.planaday.nativeapp.core.model"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
