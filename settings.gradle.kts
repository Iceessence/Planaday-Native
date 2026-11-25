pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Planaday-Native"

include(
    ":core:model",
    ":core:domain",
    ":core:math",
    ":core:documents",
    ":core:graphics",
    ":core:security",
    ":core:interchange",
    ":core:persistence",
    ":core:search",
    ":core:automation",
    ":core:notifications",
    ":core:sync-protocol",
    ":core:sync-client",
    ":core:usecases",
    ":core:design-tokens",
    ":server:app",
    ":apps:desktop",
    ":apps:android",
    ":tests:integration",
)
