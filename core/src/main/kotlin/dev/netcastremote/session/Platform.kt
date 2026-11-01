package dev.netcastremote.session

import dev.netcastremote.model.LinkState
import dev.netcastremote.model.Persisted
import java.io.File
import java.net.InetAddress

/**
 * What the shared TV session needs from the device it runs on. The Android app and the desktop app
 * each provide one; everything else in [TvRepository] is plain Kotlin.
 */
interface Platform {
    /** App icons and the screen preview are cached here. */
    val cacheDir: File

    suspend fun load(): Persisted

    suspend fun save(value: Persisted)

    fun log(line: String)

    /** Null when this device can reach a TV on the LAN; otherwise the reason it can't (no Wi-Fi, a VPN). */
    fun networkIssue(): LinkState?

    /** This network's directed broadcast address, for Wake-on-LAN and B-SEARCH. */
    fun subnetBroadcast(): InetAddress?

    /** Held while searching, on systems that filter multicast to save power. Close it when done. */
    fun multicastLock(): AutoCloseable?

    /** Calls back with this device's IPv4 address whenever it changes, so the TV can be told. */
    fun watchAddress(onChange: (String?) -> Unit)

    /** A main color from a TV screen grab, to tint the theme; null when it can't be read. */
    fun colorSeed(jpeg: ByteArray): Int?

    /**
     * Schedules [fire] for [atMs] (wall clock). Returns false when the system won't allow it, such as
     * Android without the exact-alarm permission. Platforms that wake through their own receiver may
     * ignore [fire] and deliver the matching command instead.
     */
    fun scheduleReminder(title: String, id: Int, atMs: Long, fire: () -> Unit): Boolean

    fun scheduleSleep(atMs: Long, fire: () -> Unit): Boolean

    fun cancelSleep()

    /** Tells a paired watch what's on. A no-op where there is no watch. */
    fun pushNowPlaying(channel: String, program: String) = Unit
}

/** The directed broadcast address of an IPv4 interface, such as 192.168.1.255 for 192.168.1.20/24. */
fun directedBroadcast(address: InetAddress, prefixLength: Int): InetAddress? {
    if (address !is java.net.Inet4Address || prefixLength !in 1..30) return null
    val ip = address.address.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
    val broadcast = ip or (-1 ushr prefixLength)
    return InetAddress.getByAddress(ByteArray(4) { (broadcast ushr (24 - it * 8)).toByte() })
}
