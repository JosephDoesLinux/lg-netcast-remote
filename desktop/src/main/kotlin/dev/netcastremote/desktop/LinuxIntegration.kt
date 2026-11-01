package dev.netcastremote.desktop

import dev.netcastremote.model.AppInfo
import java.awt.Toolkit
import java.io.File

/**
 * Linux desktops (GNOME, COSMIC, KDE) show a window's icon and name by matching its WM_CLASS to an
 * installed .desktop file. An AppImage isn't installed, so when Clicker runs as one it writes its own
 * menu entry and icon under ~/.local/share, pointing at wherever the AppImage lives now. Turning off
 * "Show in the app menu" removes both. Packages install their own entry, and a development run
 * leaves the system alone.
 */
object LinuxIntegration {
    const val WM_CLASS = "clicker-for-netcast"

    private val linux = System.getProperty("os.name").lowercase().contains("linux")
    private val dataHome = File(System.getenv("XDG_DATA_HOME") ?: "${System.getProperty("user.home")}/.local/share")
    private val entry = File(dataHome, "applications/$WM_CLASS.desktop")
    private val icon = File(dataHome, "icons/hicolor/512x512/apps/$WM_CLASS.png")

    /** Running from an AppImage on Linux, the only case that needs a menu entry written. */
    val available: Boolean = linux && System.getenv("APPIMAGE") != null

    /**
     * Names the app's windows "clicker-for-netcast" instead of the main class. Must run before the first
     * window opens; needs `--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED`, which the build passes.
     */
    fun nameWindows() {
        if (!linux) return
        runCatching {
            val toolkit = Toolkit.getDefaultToolkit()
            toolkit.javaClass.getDeclaredField("awtAppClassName").apply { isAccessible = true }.set(toolkit, WM_CLASS)
        }
    }

    fun sync(enabled: Boolean) {
        if (!available) return
        runCatching { if (enabled) install(System.getenv("APPIMAGE")) else remove() }
    }

    private fun install(appImage: String) {
        val png = LinuxIntegration::class.java.getResourceAsStream("/clicker.png")?.use { it.readBytes() } ?: return
        if (!icon.exists() || !icon.readBytes().contentEquals(png)) {
            icon.parentFile.mkdirs()
            icon.writeBytes(png)
        }
        val text = """
            [Desktop Entry]
            Type=Application
            Name=${AppInfo.NAME}
            GenericName=TV Remote
            Comment=${AppInfo.TAGLINE}
            Exec=${quote(appImage)}
            Icon=${icon.absolutePath}
            Categories=AudioVideo;Video;TV;
            Keywords=LG;NetCast;TV;remote;
            Terminal=false
            StartupWMClass=$WM_CLASS
            X-AppImage-Version=${AppInfo.VERSION}
        """.trimIndent() + "\n"
        // Rewritten only when something changed, such as the AppImage being moved or updated.
        if (!entry.exists() || entry.readText() != text) {
            entry.parentFile.mkdirs()
            entry.writeText(text)
        }
    }

    private fun remove() {
        entry.delete()
        icon.delete()
    }

    /** Desktop-entry quoting for a path that may hold spaces. */
    private fun quote(path: String) = "\"" + path.replace(Regex("""(["`$\\])"""), """\\\\$1""") + "\""
}
