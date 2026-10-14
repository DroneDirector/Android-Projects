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
        // RootEncoder (live streaming) is published on JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ProCamera"
include(":app")
