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

rootProject.name = "lg-netcast-remote"
include(":protocol", ":core", ":desktop")

// The Android modules need an Android SDK. Without one, the desktop app still builds with just a JDK.
val androidSdk = listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").any { System.getenv(it) != null } ||
    file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
if (androidSdk) include(":app", ":wear", ":benchmark")
