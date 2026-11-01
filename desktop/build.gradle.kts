import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Clicker for NetCast on Windows, macOS, and Linux. Shares :core (the TV session and models) with Android.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    // Expressive components (wide rail, split buttons, loading indicators) arrive in the 1.12 alphas.
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
}

compose.desktop {
    application {
        mainClass = "dev.netcastremote.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage, TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "clicker-for-netcast"
            packageVersion = "1.0.0"
            description = "The modern remote for LG NetCast smart TVs"
            vendor = "Clicker for NetCast contributors"
            licenseFile.set(rootProject.file("LICENSE"))
            linux {
                iconFile.set(project.file("src/main/resources/clicker.png"))
                menuGroup = "AudioVideo"
                shortcut = true
            }
        }
    }
}

// Renders docs/screenshots/desktop-*.png offscreen with the demo TV. Nothing touches a real TV or saved settings.
tasks.register<JavaExec>("screenshots") {
    group = "documentation"
    description = "Renders the README's desktop screenshots with the demo TV."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.netcastremote.desktop.ScreenshotsKt")
    args(rootProject.file("docs/screenshots").absolutePath)
}
