package dev.netcastremote.desktop

import dev.netcastremote.model.LinkState
import dev.netcastremote.model.Persisted
import dev.netcastremote.session.Platform
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Timer
import java.util.TimerTask
import javax.imageio.ImageIO
import kotlin.concurrent.thread

/**
 * The desktop side of the shared session. State is one JSON file in the usual per-user folder for
 * each OS; timers run in-process, so reminders and the sleep timer need the app to stay open.
 */
class DesktopPlatform(
    private val configDir: File = defaultConfigDir(),
    cacheDir: File = defaultCacheDir(),
) : Platform {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val timer = Timer("clicker-timers", true)
    private var sleep: TimerTask? = null
    private val stateFile = File(configDir, "state.json")

    override val cacheDir: File = cacheDir.also { it.mkdirs() }

    override suspend fun load(): Persisted {
        if (!stateFile.exists()) return Persisted()
        return runCatching { json.decodeFromString(Persisted.serializer(), stateFile.readText()) }.getOrDefault(Persisted())
    }

    override suspend fun save(value: Persisted) {
        stateFile.parentFile.mkdirs()
        // Write then rename, so a crash mid-write never leaves half a file.
        val temp = File(stateFile.parentFile, "state.json.tmp")
        temp.writeText(json.encodeToString(Persisted.serializer(), value))
        if (!temp.renameTo(stateFile)) {
            stateFile.delete()
            temp.renameTo(stateFile)
        }
    }

    override fun log(line: String) {
        println("${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))} $line")
    }

    /** A desktop is usually wired or on Wi-Fi; only no network at all is worth reporting. */
    override fun networkIssue(): LinkState? = if (lanAddresses().isEmpty()) LinkState.WrongWifi else null

    override fun subnetBroadcast(): InetAddress? = lanAddresses().firstNotNullOfOrNull { it.broadcast }

    override fun multicastLock(): AutoCloseable? = null

    /** Desktops have no network callback to listen to, so look every half minute. */
    override fun watchAddress(onChange: (String?) -> Unit) {
        thread(isDaemon = true, name = "clicker-address") {
            var last: String? = null
            while (true) {
                val now = lanAddresses().firstOrNull()?.address?.hostAddress
                if (now != last) {
                    last = now
                    onChange(now)
                }
                Thread.sleep(30_000)
            }
        }
    }

    /** The average of the most colorful pixels, which is close enough to Android's vibrant swatch for a tint. */
    override fun colorSeed(jpeg: ByteArray): Int? {
        val image = runCatching { ImageIO.read(ByteArrayInputStream(jpeg)) }.getOrNull() ?: return null
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        val step = maxOf(1, minOf(image.width, image.height) / 48)
        for (y in 0 until image.height step step) for (x in 0 until image.width step step) {
            val rgb = image.getRGB(x, y)
            val pr = rgb shr 16 and 0xFF
            val pg = rgb shr 8 and 0xFF
            val pb = rgb and 0xFF
            if (maxOf(pr, pg, pb) - minOf(pr, pg, pb) < 48) continue
            r += pr
            g += pg
            b += pb
            n++
        }
        if (n == 0L) return null
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    override fun scheduleReminder(title: String, id: Int, atMs: Long, fire: () -> Unit): Boolean {
        timer.schedule(task(fire), maxOf(0L, atMs - System.currentTimeMillis()))
        return true
    }

    override fun scheduleSleep(atMs: Long, fire: () -> Unit): Boolean {
        sleep?.cancel()
        sleep = task(fire).also { timer.schedule(it, maxOf(0L, atMs - System.currentTimeMillis())) }
        return true
    }

    override fun cancelSleep() {
        sleep?.cancel()
        sleep = null
    }

    private fun task(fire: () -> Unit) = object : TimerTask() {
        override fun run() = fire()
    }

    /** IPv4 addresses on interfaces that are up and not loopback or virtual, LAN ones first. */
    private fun lanAddresses() = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.name.startsWith("docker") && !it.name.startsWith("veth") }
            .flatMap { it.interfaceAddresses }
            .filter { it.address is Inet4Address }
            .sortedByDescending { it.address.isSiteLocalAddress }
    }.getOrDefault(emptyList())

    companion object {
        private const val APP = "clicker-for-netcast"
        private val os = System.getProperty("os.name").lowercase()
        private val home = System.getProperty("user.home")

        fun defaultConfigDir(): File = when {
            "win" in os -> File(System.getenv("APPDATA") ?: home, APP)
            "mac" in os -> File(home, "Library/Application Support/$APP")
            else -> File(System.getenv("XDG_CONFIG_HOME") ?: "$home/.config", APP)
        }

        fun defaultCacheDir(): File = when {
            "win" in os -> File(System.getenv("LOCALAPPDATA") ?: home, "$APP/cache")
            "mac" in os -> File(home, "Library/Caches/$APP")
            else -> File(System.getenv("XDG_CACHE_HOME") ?: "$home/.cache", APP)
        }
    }
}
