package dev.netcastremote.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/*
 * The remote UI started on Android, where keys vibrate and click through the View. A desktop has
 * neither, so these stand in with the same names and do nothing, which keeps the shared screens
 * nearly identical between the two apps.
 */
internal object HapticFeedbackConstants {
    const val LONG_PRESS = 0
    const val CLOCK_TICK = 1
    const val KEYBOARD_TAP = 2
    const val VIRTUAL_KEY = 3
}

internal object SoundEffectConstants {
    const val CLICK = 0
}

internal class FeedbackView {
    fun performHapticFeedback(kind: Int) = Unit

    fun playSoundEffect(kind: Int) = Unit
}

internal val LocalView = staticCompositionLocalOf { FeedbackView() }

/** PNG or JPEG bytes from the TV (app icons, screen grabs) as an image, or null if they don't decode. */
internal fun decodeImage(bytes: ByteArray): ImageBitmap? = runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

/** The text on the system clipboard, or empty. */
internal fun clipboardText(): String = runCatching {
    java.awt.Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
}.getOrDefault("")

internal fun copyToClipboard(text: String) {
    runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null) }
}

/** The system file chooser; returns the chosen file's text, or null if cancelled. */
internal fun pickTextFile(title: String): String? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return runCatching { java.io.File(dialog.directory, name).readText() }.getOrNull()
}
