package dev.netcastremote.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.netcastremote.model.Screen
import dev.netcastremote.session.TvRepository
import dev.netcastremote.ui.NetCastRoot
import dev.netcastremote.ui.NetCastTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Renders the README's desktop screenshots offscreen, with the demo TV and a throwaway settings folder,
 * so nothing touches a real TV or the user's saved state. Run with `./gradlew :desktop:screenshots`.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "docs/screenshots").apply { mkdirs() }
    shot(out, "desktop-welcome", Screen.Welcome, theme = "dark", demo = false)
    shot(out, "desktop-remote", Screen.Remote, theme = "dark")
    shot(out, "desktop-remote-light", Screen.Remote, theme = "light")
    shot(out, "desktop-channels", Screen.Channels, theme = "dark")
    shot(out, "desktop-apps", Screen.Apps, theme = "light")
    shot(out, "desktop-settings", Screen.Settings, theme = "dark")
    shot(out, "desktop-about", Screen.About, theme = "dark")
}

private fun shot(out: File, name: String, screen: Screen, theme: String, demo: Boolean = true) = runBlocking {
    val temp = createTempDirectory("clicker-shot").toFile()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repo = TvRepository(DesktopPlatform(File(temp, "config"), File(temp, "cache")), scope)
    repo.start()
    repo.state.first { it.screen != Screen.Discover || it.active != null }
    if (demo) {
        repo.useDemo(persist = false)
        // The demo TV answers instantly, but its channel and app lists load on another thread.
        repo.state.first { it.channels.isNotEmpty() && it.apps.isNotEmpty() }
    }
    repo.open(screen)
    delay(300)
    repo.clearMessage()
    // 1280 x 800 dp at 1.5x, a common laptop window.
    ImageComposeScene(width = 1920, height = 1200, density = Density(1.5f)) {
        val state by repo.state.collectAsState()
        NetCastTheme(theme, null) { NetCastRoot(repo, state) }
    }.use { scene ->
        // Step through two seconds of frames so entrance animations settle.
        var image = scene.render(0)
        for (frame in 1..60) image = scene.render(frame * 33_333_333L)
        File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
    scope.cancel()
    temp.deleteRecursively()
    println("wrote $name.png")
}
