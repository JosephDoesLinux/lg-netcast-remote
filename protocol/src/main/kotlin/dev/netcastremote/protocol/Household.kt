package dev.netcastremote.protocol

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class MatterCluster { OnOff, LevelControl, Channel, KeypadInput, MediaPlayback }

data class MatterInvoke(
    val cluster: MatterCluster,
    val command: String,
    val args: Map<String, String> = emptyMap(),
)

interface MatterSink {
    fun power(turnOn: Boolean?)
    fun volumeTo(level: Int)
    fun mute()
    fun channelUp()
    fun channelDown()
    fun tune(number: String?, name: String?)
    fun key(code: Int)
    fun transport(command: String)
    fun launchApp(name: String)
    fun runMacro(name: String)
}

object MatterClusters {
    fun invoke(command: MatterInvoke, sink: MatterSink): String {
        when (command.cluster) {
            MatterCluster.OnOff -> when (command.command.lowercase()) {
                "on" -> sink.power(true)
                "off" -> sink.power(false)
                else -> sink.power(null)
            }
            MatterCluster.LevelControl -> {
                val level = command.args["level"]?.toIntOrNull() ?: return "missing level"
                sink.volumeTo(level)
            }
            MatterCluster.Channel -> when (command.command.lowercase()) {
                "up" -> sink.channelUp()
                "down" -> sink.channelDown()
                else -> sink.tune(command.args["number"], command.args["name"])
            }
            MatterCluster.KeypadInput -> {
                val code = command.args["code"]?.toIntOrNull()
                    ?: KeyCode.fromName(command.args["key"].orEmpty())?.code
                    ?: return "missing key"
                sink.key(code)
            }
            MatterCluster.MediaPlayback -> when (command.command.lowercase()) {
                "launch" -> sink.launchApp(command.args["name"].orEmpty())
                "macro" -> sink.runMacro(command.args["name"].orEmpty())
                else -> sink.transport(command.command)
            }
        }
        return "ok"
    }

    fun parseLine(line: String): MatterInvoke? {
        val parts = line.trim().split(' ', limit = 3)
        if (parts.size < 2) return null
        val cluster = MatterCluster.entries.firstOrNull { it.name.equals(parts[0], true) } ?: return null
        val args = linkedMapOf<String, String>()
        parts.getOrNull(2)?.split(' ')?.forEach { token ->
            val idx = token.indexOf('=')
            if (idx > 0) args[token.substring(0, idx)] = token.substring(idx + 1)
        }
        return MatterInvoke(cluster, parts[1], args)
    }
}

data class Macro(
    val id: String,
    val name: String,
    val tvId: String,
    val steps: List<MacroStep>,
)

data class MacroStep(
    val op: String,
    val a: String = "",
    val b: String = "",
    val c: String = "",
    val d: String = "",
    val ms: Long = 0,
)

data class Programme(
    val channel: String,
    val title: String,
    val startEpochMs: Long,
    val stopEpochMs: Long,
)

object XmlTv {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun parse(xml: String): List<Programme> = Regex(
        """<programme\b([^>]*)>([\s\S]*?)</programme>""",
        RegexOption.IGNORE_CASE,
    ).findAll(xml).mapNotNull { match ->
        val attrs = match.groupValues[1]
        val body = match.groupValues[2]
        val start = attr(attrs, "start")?.let(::parseStamp) ?: return@mapNotNull null
        val stop = attr(attrs, "stop")?.let(::parseStamp) ?: (start + 30 * 60_000)
        val title = UdapParse.tag(body, "title").orEmpty()
        Programme(attr(attrs, "channel").orEmpty(), title, start, stop)
    }.toList()

    private fun attr(blob: String, name: String): String? =
        Regex("""$name\s*=\s*"([^"]*)"""").find(blob)?.groupValues?.get(1)

    fun parseStamp(value: String): Long? {
        val digits = value.trim().take(14)
        if (digits.length < 14) return null
        return try {
            LocalDateTime.parse(digits, stamp).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}

object Dlna {
    fun setUri(uri: String, title: String): String = soap(
        "SetAVTransportURI",
        """<InstanceID>0</InstanceID><CurrentURI>${UdapXml.escape(uri)}</CurrentURI><CurrentURIMetaData>${UdapXml.escape(didl(uri, title))}</CurrentURIMetaData>""",
    )

    fun play(): String = soap("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")

    fun stop(): String = soap("Stop", "<InstanceID>0</InstanceID>")

    fun controlUrl(descriptionXml: String): String? {
        val service = UdapParse.blocks(descriptionXml, "service").firstOrNull { block ->
            block.contains("AVTransport", ignoreCase = true)
        } ?: return null
        return UdapParse.tag(service, "controlURL")
    }

    private fun didl(uri: String, title: String): String =
        """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"><item id="0" parentID="-1" restricted="1"><dc:title xmlns:dc="http://purl.org/dc/elements/1.1/">${UdapXml.escape(title)}</dc:title><res>${UdapXml.escape(uri)}</res></item></DIDL-Lite>"""

    private fun soap(action: String, inner: String): String =
        """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            """<s:Body><u:$action xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">$inner</u:$action></s:Body></s:Envelope>"""
}

object WakeOnLan {
    /** "a1-b2-c3-d4-e5-f6", "A1B2C3D4E5F6", or "a1:b2:c3:d4:e5:f6" as "A1:B2:C3:D4:E5:F6"; null when it isn't six octets. */
    fun normalize(raw: String): String? {
        val hex = raw.filterNot { it in ":-. " }
        if (hex.length != 12 || hex.any { it.digitToIntOrNull(16) == null }) return null
        return hex.uppercase().chunked(2).joinToString(":")
    }

    fun packet(mac: String): ByteArray {
        val parts = requireNotNull(normalize(mac)) { "MAC needs 6 octets" }.split(':').map { it.toInt(16).toByte() }
        return ByteArray(6 + 16 * 6).also { buf ->
            for (i in 0 until 6) buf[i] = 0xFF.toByte()
            for (repeat in 0 until 16) {
                parts.forEachIndexed { index, byte -> buf[6 + repeat * 6 + index] = byte }
            }
        }
    }
}

object AppResultText {
    fun explain(action: String, detail: String): String {
        val verb = if (action.equals("Terminate", true)) "close" else "start"
        return when (detail.uppercase()) {
            "OK" -> if (verb == "close") "App closed." else "App started."
            "BUSY" -> "The TV is busy with that app."
            else -> "The TV could not $verb this app. The NetCast store has been gone for years, so this tile may never open."
        }
    }
}
