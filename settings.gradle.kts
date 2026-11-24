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

rootProject.name = "Dastyar"

include(
    ":app",
    ":core:ui",
    ":core:data",
    ":core:security",
    ":core:network",
    ":feature:chat",
    ":feature:agent",
    ":feature:providers",
    ":feature:settings",
    ":feature:tools",
    ":feature:voice",
)
