package dev.netcastremote.protocol

data class Channel(
    val kind: String = "",
    val major: Int = 0,
    val minor: Int = 0,
    val displayMajor: Int = major,
    val displayMinor: Int = minor,
    val sourceIndex: Int = 0,
    val physicalNum: Int = 0,
    val name: String = "",
    val program: String = "",
    val audioOnly: Boolean = false,
    val inputName: String = "",
    val label: String = "",
) {
    val id: String get() = "$major.$minor.$sourceIndex.$physicalNum"
    val numberLabel: String
        get() {
            val maj = if (displayMajor != 0) displayMajor else major
            val min = if (displayMinor != 0 || minor == 0) displayMinor else minor
            return if (min == 0) "$maj" else "$maj-$min"
        }
}

data class VolumeInfo(
    val mute: Boolean = false,
    val min: Int = 0,
    val max: Int = 100,
    val level: Int = 0,
)

data class TvApp(
    val auid: String = "",
    val name: String = "",
    val category: Int = 0,
    val contentId: String = "",
    val adult: Boolean = false,
    val iconName: String = "",
)

enum class UiMode { VolCh, TouchPad, Unknown }

data class TvDescription(
    val friendlyName: String = "",
    val modelName: String = "",
    val uuid: String = "",
    val udapVersion: String = "",
    val webOs: Boolean = false,
    val server: String = "",
)

sealed class HostEvent {
    data class ChannelChanged(val channel: Channel) : HostEvent()
    data class Keyboard(val visible: Boolean, val password: Boolean) : HostEvent()
    data class Text(val state: String, val value: String) : HostEvent()
    data class ThreeD(val on: Boolean) : HostEvent()
    data class AppResult(val action: String, val detail: String) : HostEvent()
    data class AppListChanged(val action: String, val auid: String, val name: String, val category: Int) : HostEvent()
    data class Cursor(val visible: Boolean) : HostEvent()
    data object Bye : HostEvent()
    data class IpChanged(val newIp: String, val oldIp: String) : HostEvent()
    data class AppMessage(val body: String) : HostEvent()
    data class Ignored(val name: String) : HostEvent()
}

data class SsdpNotice(
    val location: String,
    val server: String,
    val searchTarget: String,
    val usn: String,
    val raw: String,
)

enum class Dialect { UDAP, ROAP }

data class HttpResult(
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
) {
    val text: String get() = body.toString(Charsets.UTF_8)
    val ok: Boolean get() = status in 200..299
}

object TextLimits {
    const val MAX_BYTES = 2048

    fun clipUtf8(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size <= MAX_BYTES) return value
        var end = MAX_BYTES
        while (end > 0 && end < bytes.size && (bytes[end].toInt() and 0xC0) == 0x80) end--
        return bytes.copyOf(end).toString(Charsets.UTF_8)
    }
}

object VolumeSteps {
    /** Keys to send so a stepped volume control lands on [target]. */
    fun toward(current: Int, target: Int, min: Int, max: Int, ceiling: Int? = null): List<KeyCode> {
        val cap = ceiling?.coerceIn(min, max) ?: max
        val goal = target.coerceIn(min, cap)
        val now = current.coerceIn(min, max)
        val delta = goal - now
        if (delta == 0) return emptyList()
        val key = if (delta > 0) KeyCode.VOLUME_UP else KeyCode.VOLUME_DOWN
        return List(kotlin.math.abs(delta).coerceAtMost(40)) { key }
    }
}

/** Collapses a burst of repeats into the newest code while one POST is in flight. */
class KeyCoalescer {
    private var busy = false
    private var queued: Int? = null

    fun submit(code: Int): Boolean {
        if (busy) {
            queued = code
            return false
        }
        busy = true
        return true
    }

    fun done(): Int? {
        val next = queued
        queued = null
        busy = next != null
        return next
    }

    fun reset() {
        busy = false
        queued = null
    }
}

fun repeatDelayMs(tick: Int): Long = when {
    tick < 3 -> 360L
    tick < 8 -> 160L
    else -> 80L
}
