package dev.netcastremote.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.netcastremote.model.AppInfo
import dev.netcastremote.model.LinkState
import dev.netcastremote.model.Screen
import dev.netcastremote.protocol.KeyCode
import dev.netcastremote.session.TvRepository
import dev.netcastremote.ui.NetCastRoot
import dev.netcastremote.ui.NetCastTheme
import dev.netcastremote.ui.decodeImage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.awt.Dimension

fun main() {
    val platform = DesktopPlatform()
    // Before any window opens: the window's class name, and the menu entry the dock matches it to.
    LinuxIntegration.nameWindows()
    LinuxIntegration.sync(runBlocking { platform.load() }.settings.appMenuEntry)
    app(platform)
}

private fun app(platform: DesktopPlatform) = application {
    // A remote shouldn't crash over one failed network call; log it and carry on.
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, error -> platform.log("Unhandled error: $error") }) }
    val repo = remember { TvRepository(platform, scope).also { it.start() } }
    val state by repo.state.collectAsState()
    val windowState = rememberWindowState(size = DpSize(1180.dp, 820.dp), position = WindowPosition(Alignment.Center))
    val keys = remember { KeyShortcuts(repo) }
    val icon = remember { javaClass.getResourceAsStream("/clicker.png")?.use { it.readBytes() }?.let(::decodeImage)?.let(::BitmapPainter) }
    // For screenshots and testing: CLICKER_SCREEN=Channels (any Screen name) opens that screen once the saved state has loaded.
    LaunchedEffect(Unit) {
        val start = System.getenv("CLICKER_SCREEN")?.let { name -> Screen.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        if (start != null) {
            repo.state.first { it.active != null || it.screen == Screen.Welcome }
            repo.open(start)
        }
    }
    // A minimized window pauses the fast polling and the watch for the TV, as a backgrounded phone app does.
    LaunchedEffect(windowState.isMinimized) { repo.setVisible(!windowState.isMinimized) }
    Window(
        onCloseRequest = {
            repo.disconnect()
            exitApplication()
        },
        state = windowState,
        title = state.active?.name?.let { "$it · ${AppInfo.NAME}" } ?: AppInfo.NAME,
        icon = icon,
        onKeyEvent = { event -> keys.handle(event, repo.state.value.screen, repo.state.value.link, repo.state.value.keyboard?.visible == true) },
    ) {
        LaunchedEffect(Unit) { window.minimumSize = Dimension(400, 600) }
        NetCastTheme(state.settings.theme, state.artSeed) {
            NetCastRoot(repo, state)
        }
    }
}

/**
 * The keyboard as a remote, on the remote screen only, and only for keys no focused control used.
 * Held keys repeat in the repository like a held button; the OS's own auto-repeat is ignored.
 */
private class KeyShortcuts(private val repo: TvRepository) {
    private val held = mutableSetOf<Key>()

    fun handle(event: KeyEvent, screen: Screen, link: LinkState, typing: Boolean): Boolean {
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false
        if (screen != Screen.Remote || typing || (link != LinkState.Online && link != LinkState.Demo)) return false
        val code = codeFor(event.key) ?: return false
        when (event.type) {
            KeyEventType.KeyDown -> if (held.add(event.key)) {
                if (code.family == KeyCode.Family.DIGIT) repo.digit(code) else repo.keyDown(code)
            }
            KeyEventType.KeyUp -> {
                held.remove(event.key)
                repo.keyUp()
            }
        }
        return true
    }

    private fun codeFor(key: Key): KeyCode? = when (key) {
        Key.DirectionUp -> KeyCode.UP
        Key.DirectionDown -> KeyCode.DOWN
        Key.DirectionLeft -> KeyCode.LEFT
        Key.DirectionRight -> KeyCode.RIGHT
        Key.Enter, Key.NumPadEnter -> KeyCode.OK
        Key.Escape, Key.Backspace -> KeyCode.BACK
        Key.H -> KeyCode.HOME
        Key.E -> KeyCode.EXIT
        Key.I -> KeyCode.INPUT
        Key.G -> KeyCode.EPG
        Key.M -> KeyCode.MUTE
        Key.Spacebar -> KeyCode.PAUSE
        Key.Equals, Key.Plus, Key.NumPadAdd -> KeyCode.VOLUME_UP
        Key.Minus, Key.NumPadSubtract -> KeyCode.VOLUME_DOWN
        Key.PageUp -> KeyCode.CHANNEL_UP
        Key.PageDown -> KeyCode.CHANNEL_DOWN
        else -> digits[key]
    }

    private val digits = mapOf(
        Key.Zero to KeyCode.NUM_0, Key.One to KeyCode.NUM_1, Key.Two to KeyCode.NUM_2, Key.Three to KeyCode.NUM_3, Key.Four to KeyCode.NUM_4,
        Key.Five to KeyCode.NUM_5, Key.Six to KeyCode.NUM_6, Key.Seven to KeyCode.NUM_7, Key.Eight to KeyCode.NUM_8, Key.Nine to KeyCode.NUM_9,
        Key.NumPad0 to KeyCode.NUM_0, Key.NumPad1 to KeyCode.NUM_1, Key.NumPad2 to KeyCode.NUM_2, Key.NumPad3 to KeyCode.NUM_3, Key.NumPad4 to KeyCode.NUM_4,
        Key.NumPad5 to KeyCode.NUM_5, Key.NumPad6 to KeyCode.NUM_6, Key.NumPad7 to KeyCode.NUM_7, Key.NumPad8 to KeyCode.NUM_8, Key.NumPad9 to KeyCode.NUM_9,
    )
}
