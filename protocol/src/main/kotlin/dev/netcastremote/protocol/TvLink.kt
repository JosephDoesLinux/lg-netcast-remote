package dev.netcastremote.protocol

import java.net.URLEncoder

interface TvLink : AutoCloseable {
    val dialect: Dialect
    fun requestPairingKey(): Int
    fun hello(key: String, eventPort: Int): Int
    fun byebye(eventPort: Int): Int
    fun updateIp(newIp: String, oldIp: String): Int
    fun command(name: String, params: Map<String, String>, keepAlive: Boolean = false): Int
    fun event(name: String, params: Map<String, String>): Int
    fun query(target: String, params: Map<String, String> = emptyMap()): String
    fun queryBytes(target: String, params: Map<String, String> = emptyMap()): ByteArray
    fun appToApp(auid: String, action: String, body: String = ""): String
    fun appStatus(auid: String): String
    fun appAuid(name: String): String
}

class SocketTvLink(
    private val host: String,
    private val port: Int,
    override val dialect: Dialect,
    private val http: RawHttpClient = RawHttpClient(),
) : TvLink {
    var session: String = ""
        private set

    override fun requestPairingKey(): Int = when (dialect) {
        Dialect.UDAP -> post("/udap/api/pairing", UdapXml.showKey()).status
        Dialect.ROAP -> post("/roap/api/auth", RoapXml.authKeyRequest()).also { captureSession(it) }.status
    }

    override fun hello(key: String, eventPort: Int): Int = when (dialect) {
        Dialect.UDAP -> post("/udap/api/pairing", UdapXml.hello(key.filter { it.isDigit() }, eventPort)).status
        Dialect.ROAP -> post("/roap/api/auth", RoapXml.authRequest(key.filter { it.isDigit() })).also { captureSession(it) }.status
    }

    override fun byebye(eventPort: Int): Int = when (dialect) {
        Dialect.UDAP -> post("/udap/api/pairing", UdapXml.byebye(eventPort)).status
        Dialect.ROAP -> 200
    }

    override fun updateIp(newIp: String, oldIp: String): Int = when (dialect) {
        Dialect.UDAP -> post("/udap/api/pairing", UdapXml.update(newIp, oldIp)).status
        Dialect.ROAP -> 200
    }

    override fun command(name: String, params: Map<String, String>, keepAlive: Boolean): Int = when (dialect) {
        Dialect.UDAP -> post("/udap/api/command", UdapXml.command(name, params), keepAlive).status
        Dialect.ROAP -> {
            val code = params["value"]?.toIntOrNull()
            if (name == "HandleKeyInput" && code != null) {
                post("/roap/api/command", RoapXml.key(session, code), keepAlive).status
            } else {
                501
            }
        }
    }

    override fun event(name: String, params: Map<String, String>): Int = when (dialect) {
        Dialect.UDAP -> post("/udap/api/event", UdapXml.event(name, params)).status
        Dialect.ROAP -> 501
    }

    override fun query(target: String, params: Map<String, String>): String {
        if (dialect != Dialect.UDAP) return ""
        return get(queryPath(target, params)).text
    }

    override fun queryBytes(target: String, params: Map<String, String>): ByteArray {
        if (dialect != Dialect.UDAP) return ByteArray(0)
        return get(queryPath(target, params)).body
    }

    override fun appToApp(auid: String, action: String, body: String): String {
        if (dialect != Dialect.UDAP) return ""
        val path = "/udap/api/apptoapp/command/${encode(auid)}/$action"
        val type = if (action == "send") "text/plain; charset=utf-8" else "text/xml; charset=utf-8"
        return http.post(host, port, path, body.toByteArray(Charsets.UTF_8), type, false).text
    }

    override fun appStatus(auid: String): String {
        if (dialect != Dialect.UDAP) return "NONE"
        return http.get(host, port, "/udap/api/apptoapp/data/${encode(auid)}/status").text.trim()
    }

    override fun appAuid(name: String): String {
        if (dialect != Dialect.UDAP) return ""
        return http.get(host, port, "/udap/api/apptoapp/data/${encode(name)}").text.trim()
    }

    override fun close() = http.close()

    private fun post(path: String, xml: String, keepAlive: Boolean = false): HttpResult =
        http.post(host, port, path, xml.toByteArray(Charsets.UTF_8), "text/xml; charset=utf-8", keepAlive)

    private fun get(path: String): HttpResult = http.get(host, port, path)

    private fun captureSession(result: HttpResult) {
        UdapParse.session(result.text)?.let { session = it }
    }

    private fun queryPath(target: String, params: Map<String, String>): String {
        val extra = params.entries.joinToString("") { (k, v) -> "&${encode(k)}=${encode(v)}" }
        return "/udap/api/data?target=${encode(target)}$extra"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}

fun probeDialect(http: RawHttpClient, host: String, port: Int): Dialect {
    val udap = try {
        http.get(host, port, "/udap/api/data?target=volume_info")
    } catch (_: Exception) {
        HttpResult(0)
    }
    if (udap.status == 200 || udap.status == 401 || udap.status == 400) return Dialect.UDAP
    val roap = try {
        http.get(host, port, "/roap/api/auth")
    } catch (_: Exception) {
        HttpResult(0)
    }
    if (roap.status in 200..499 && udap.status == 404) return Dialect.ROAP
    if (udap.status == 404 && roap.status != 0) return Dialect.ROAP
    return Dialect.UDAP
}

class DemoTvLink : TvLink {
    override val dialect: Dialect = Dialect.UDAP
    private val channels = mutableListOf(
        Channel("terrestrial", 7, 1, 7, 1, 1, 7, "BBC One", "News", false, "Antenna", "Antenna"),
        Channel("terrestrial", 11, 1, 11, 1, 2, 11, "MBC", "Evening", false, "Antenna", "Antenna"),
        Channel("cable", 24, 0, 24, 0, 3, 24, "Film", "Late movie", false, "Antenna", "Antenna"),
        Channel("terrestrial", 88, 1, 88, 1, 4, 88, "Radio 1", "Morning show", true, "Antenna", "Antenna"),
    )
    private var index = 0
    private var volume = VolumeInfo(mute = false, min = 0, max = 100, level = 18)
    private var mode = UiMode.VolCh
    private var threeD = false
    private var keyboard = false
    private var text = ""
    private val apps = listOf(
        TvApp("00000001", "Browser", 3, "browser", false, "browser.png"),
        TvApp("00000002", "Live TV", 3, "livetv", false, "livetv.png"),
        TvApp("00000003", "Gallery", 3, "gallery", false, "gallery.png"),
        TvApp("0000DEAD", "Old store app", 2, "store", true, "store.png"),
    )
    var lastCommand: String = ""
        private set

    override fun requestPairingKey(): Int = 200
    override fun hello(key: String, eventPort: Int): Int = if (key.filter { it.isDigit() }.length == 6 || key == "demo") 200 else 401
    override fun byebye(eventPort: Int): Int = 200
    override fun updateIp(newIp: String, oldIp: String): Int = 200

    override fun command(name: String, params: Map<String, String>, keepAlive: Boolean): Int {
        lastCommand = name
        when (name) {
            "HandleKeyInput" -> applyKey(params["value"]?.toIntOrNull() ?: return 400)
            "HandleChannelChange" -> {
                val major = params["major"]?.toIntOrNull()
                val found = channels.indexOfFirst { it.major == major }
                if (found >= 0) index = found
                mode = UiMode.VolCh
            }
            "HandleTouchMove", "HandleTouchClick", "HandleTouchWheel" -> mode = UiMode.TouchPad
            "AppExecute" -> mode = UiMode.TouchPad
            "AppTerminate" -> mode = UiMode.VolCh
        }
        return 200
    }

    override fun event(name: String, params: Map<String, String>): Int {
        if (name == "TextEdited") text = params["value"].orEmpty()
        if (name == "CallStateChanged" && params["value"] == "ringon") volume = volume.copy(mute = true)
        if (name == "CallStateChanged" && params["value"] == "ringoff") volume = volume.copy(mute = false)
        return 200
    }

    override fun query(target: String, params: Map<String, String>): String = when (target) {
        "cur_channel" -> channelXml(channels[index])
        "channel_list" -> "<envelope><dataList>" + channels.joinToString("") { "<data>${channelInner(it)}</data>" } + "</dataList></envelope>"
        "volume_info" -> "<envelope><mute>${volume.mute}</mute><minLevel>${volume.min}</minLevel><maxLevel>${volume.max}</maxLevel><level>${volume.level}</level></envelope>"
        "context_ui" -> "<envelope><mode>${if (mode == UiMode.TouchPad) "TouchPad" else "VolCh"}</mode></envelope>"
        "is_3d" -> "<envelope><is3D>$threeD</is3D></envelope>"
        "applist_get" -> "<envelope>" + apps.joinToString("") {
            "<data><auid>${it.auid}</auid><name>${it.name}</name><type>${it.category}</type><cpid>${it.contentId}</cpid><adult>${it.adult}</adult><icon_name>${it.iconName}</icon_name></data>"
        } + "</envelope>"
        "appnum_get" -> "<envelope><type>1</type><number>${apps.size}</number></envelope>"
        else -> ""
    }

    override fun queryBytes(target: String, params: Map<String, String>): ByteArray =
        if (target == "screen_image" || target == "appicon_get") TINY_JPEG else query(target, params).toByteArray()

    override fun appToApp(auid: String, action: String, body: String): String = "OK"
    override fun appStatus(auid: String): String = if (mode == UiMode.TouchPad) "RUN" else "NONE"
    override fun appAuid(name: String): String = apps.firstOrNull { it.name.equals(name, true) }?.auid.orEmpty()
    override fun close() = Unit

    private fun applyKey(code: Int) {
        when (code) {
            KeyCode.VOLUME_UP.code -> volume = volume.copy(level = (volume.level + 1).coerceAtMost(volume.max), mute = false)
            KeyCode.VOLUME_DOWN.code -> volume = volume.copy(level = (volume.level - 1).coerceAtLeast(volume.min))
            KeyCode.MUTE.code -> volume = volume.copy(mute = !volume.mute)
            KeyCode.CHANNEL_UP.code -> index = (index + 1) % channels.size
            KeyCode.CHANNEL_DOWN.code -> index = (index - 1 + channels.size) % channels.size
            KeyCode.THREE_D.code -> threeD = !threeD
            KeyCode.INPUT.code, KeyCode.MY_APPS.code, KeyCode.NETCAST.code, KeyCode.HOME.code -> mode = UiMode.TouchPad
            KeyCode.LIVE_TV.code, KeyCode.EXIT.code -> mode = UiMode.VolCh
            else -> Unit
        }
    }

    private fun channelXml(channel: Channel) = "<envelope>${channelInner(channel)}</envelope>"

    private fun channelInner(channel: Channel) = buildString {
        append("<chtype>${channel.kind}</chtype>")
        append("<major>${channel.major}</major><minor>${channel.minor}</minor>")
        append("<displayMajor>${channel.displayMajor}</displayMajor><displayMinor>${channel.displayMinor}</displayMinor>")
        append("<sourceIndex>${channel.sourceIndex}</sourceIndex><physicalNum>${channel.physicalNum}</physicalNum>")
        append("<chname>${channel.name}</chname><progName>${channel.program}</progName>")
        append("<audioCh>${if (channel.audioOnly) 1 else 0}</audioCh>")
        append("<inputSourceName>${channel.inputName}</inputSourceName><labelName>${channel.label}</labelName>")
    }

    companion object {
        val TINY_JPEG: ByteArray = "ffd8ffe000104a46494600010100000100010000ffdb004300080606070605080707070909080a0c140d0c0b0b0c1912130f141d1a1f1e1d1a1c1c20242e2720222c231c1c2837292c30313434341f27393d38323c2e333432ffc0000b080001000101011100ffc40014100100000000000000000000000000000000ffda00080001000100003f00fbffd9"
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
