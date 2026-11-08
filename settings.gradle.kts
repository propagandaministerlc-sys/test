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

rootProject.name = "UltraGrid"
// The Windows build passes -PdesktopOnly so it does not need the Android SDK.
if (!providers.gradleProperty("desktopOnly").isPresent) {
    include(":app")
    include(":hearing")
}
include(":desktop")
