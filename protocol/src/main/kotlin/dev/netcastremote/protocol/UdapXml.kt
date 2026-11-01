package dev.netcastremote.protocol

object UdapXml {
    const val USER_AGENT = "Android/16 UDAP/2.0 NetCastRemote/1.0"
    private const val DECL = """<?xml version="1.0" encoding="utf-8"?>"""

    fun showKey(): String = DECL + """<envelope><api type="pairing"><name>showKey</name></api></envelope>"""

    fun hello(key: String, eventPort: Int): String =
        DECL + """<envelope><api type="pairing"><name>hello</name><value>${escape(key)}</value><port>$eventPort</port></api></envelope>"""

    fun byebye(eventPort: Int): String =
        DECL + """<envelope><api type="pairing"><name>byebye</name><port>$eventPort</port></api></envelope>"""

    fun update(newIp: String, oldIp: String): String =
        DECL + """<envelope><api type="pairing"><name>update</name><value>${escape(newIp)}</value><port>${escape(oldIp)}</port></api></envelope>"""

    fun command(name: String, params: Map<String, String>): String = envelope("command", name, params)

    fun event(name: String, params: Map<String, String>): String = envelope("event", name, params)

    fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                '\n' -> append("&#10;")
                '\r' -> append("&#13;")
                else -> append(ch)
            }
        }
    }

    private fun envelope(type: String, name: String, params: Map<String, String>): String = buildString {
        append(DECL)
        append("""<envelope><api type="$type"><name>${escape(name)}</name>""")
        params.forEach { (key, value) ->
            append("<").append(key).append(">")
            append(escape(value))
            append("</").append(key).append(">")
        }
        append("</api></envelope>")
    }
}

object RoapXml {
    fun authKeyRequest(): String =
        """<?xml version="1.0" encoding="utf-8"?><auth><type>AuthKeyReq</type></auth>"""

    fun authRequest(key: String): String =
        """<?xml version="1.0" encoding="utf-8"?><auth><type>AuthReq</type><value>${UdapXml.escape(key)}</value></auth>"""

    fun key(session: String, code: Int): String =
        """<?xml version="1.0" encoding="utf-8"?><command><session>${UdapXml.escape(session)}</session><type>HandleKeyInput</type><value>$code</value></command>"""
}

object UdapParse {
    fun tag(xml: String, name: String): String? {
        val match = Regex("<$name\\b[^>]*>([\\s\\S]*?)</$name>", RegexOption.IGNORE_CASE).find(xml) ?: return null
        return unescape(match.groupValues[1].trim())
    }

    fun blocks(xml: String, name: String): List<String> =
        Regex("<$name\\b[^>]*>([\\s\\S]*?)</$name>", RegexOption.IGNORE_CASE)
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()

    fun unescape(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#10;", "\n")
        .replace("&#13;", "\r")
        .replace("&amp;", "&")

    fun channel(xml: String): Channel? {
        if (tag(xml, "chname") == null && tag(xml, "major") == null && tag(xml, "displayMajor") == null) return null
        val major = tag(xml, "major")?.toIntOrNull() ?: 0
        val minor = tag(xml, "minor")?.toIntOrNull() ?: 0
        return Channel(
            kind = tag(xml, "chtype").orEmpty(),
            major = major,
            minor = minor,
            displayMajor = tag(xml, "displayMajor")?.toIntOrNull() ?: major,
            displayMinor = tag(xml, "displayMinor")?.toIntOrNull() ?: minor,
            sourceIndex = tag(xml, "sourceIndex")?.toIntOrNull() ?: 0,
            physicalNum = tag(xml, "physicalNum")?.toIntOrNull() ?: 0,
            name = tag(xml, "chname").orEmpty(),
            program = tag(xml, "progName").orEmpty(),
            audioOnly = tag(xml, "audioCh") == "1",
            inputName = tag(xml, "inputSourceName").orEmpty(),
            label = tag(xml, "labelName").orEmpty(),
        )
    }

    fun channelList(xml: String): List<Channel> {
        val chunks = blocks(xml, "data").ifEmpty { blocks(xml, "channel") }
        return chunks.mapNotNull { channel(it) }
    }

    fun volume(xml: String): VolumeInfo = VolumeInfo(
        mute = tag(xml, "mute").equals("true", ignoreCase = true),
        min = tag(xml, "minLevel")?.toIntOrNull() ?: 0,
        max = tag(xml, "maxLevel")?.toIntOrNull() ?: 100,
        level = tag(xml, "level")?.toIntOrNull() ?: 0,
    )

    fun uiMode(xml: String): UiMode = when (tag(xml, "mode")?.lowercase()) {
        "volch" -> UiMode.VolCh
        "touchpad" -> UiMode.TouchPad
        else -> UiMode.Unknown
    }

    fun is3d(xml: String): Boolean = tag(xml, "is3D").equals("true", ignoreCase = true) ||
        tag(xml, "value").equals("true", ignoreCase = true) && xml.contains("3d", ignoreCase = true)

    fun apps(xml: String): List<TvApp> = blocks(xml, "data").mapNotNull { block ->
        val auid = tag(block, "auid") ?: return@mapNotNull null
        TvApp(
            auid = auid,
            name = tag(block, "name").orEmpty(),
            category = tag(block, "type")?.toIntOrNull() ?: 0,
            contentId = tag(block, "cpid").orEmpty(),
            adult = tag(block, "adult").equals("true", ignoreCase = true),
            iconName = tag(block, "icon_name").orEmpty(),
        )
    }

    fun appCount(xml: String): Int = tag(xml, "number")?.toIntOrNull() ?: apps(xml).size

    fun description(xml: String, server: String = ""): TvDescription {
        val udn = tag(xml, "UDN").orEmpty().removePrefix("uuid:")
        val blob = xml + server
        return TvDescription(
            friendlyName = tag(xml, "friendlyName").orEmpty(),
            modelName = tag(xml, "modelName").orEmpty(),
            uuid = udn,
            udapVersion = tag(xml, "modelNumber").orEmpty(),
            webOs = blob.contains("webOS", ignoreCase = true) || blob.contains("Web0S"),
            server = server,
        )
    }

    fun session(xml: String): String? = tag(xml, "session")

    fun event(path: String, body: String): HostEvent {
        if (path.contains("apptoapp", ignoreCase = true)) return HostEvent.AppMessage(body)
        val name = tag(body, "name").orEmpty()
        return when (name) {
            "ChannelChanged" -> HostEvent.ChannelChanged(channel(body) ?: Channel())
            "KeyboardVisible" -> HostEvent.Keyboard(
                visible = tag(body, "value").equals("true", ignoreCase = true),
                password = tag(body, "mode").equals("password", ignoreCase = true),
            )
            "TextEdited" -> HostEvent.Text(tag(body, "state").orEmpty(), tag(body, "value").orEmpty())
            "3DMode" -> HostEvent.ThreeD(tag(body, "value").equals("true", ignoreCase = true))
            "Mobilehome_App_Errstate" -> HostEvent.AppResult(tag(body, "action").orEmpty(), tag(body, "detail").orEmpty())
            "Mobilehome_App_Change" -> HostEvent.AppListChanged(
                action = tag(body, "action").orEmpty(),
                auid = tag(body, "auid").orEmpty(),
                name = tag(body, "appname") ?: tag(body, "name").orEmpty(),
                category = tag(body, "apptype")?.toIntOrNull() ?: 0,
            )
            "CursorVisible" -> HostEvent.Cursor(tag(body, "value").equals("true", ignoreCase = true))
            "byebye" -> HostEvent.Bye
            "update" -> HostEvent.IpChanged(tag(body, "value").orEmpty(), tag(body, "port").orEmpty())
            else -> if (name.isEmpty() && body.contains("byebye")) HostEvent.Bye else HostEvent.Ignored(name)
        }
    }
}

object Ssdp {
    val searchTargets = listOf(
        "udap:rootservice",
        "urn:schemas-udap:service:netrcu:1",
        "urn:schemas-udap:service:smartText:1",
        "urn:schemas-udap:service:mobilehome:1",
        "urn:schemas-udap:service:AppToApp:1",
    )

    /** UDAP 2.0's B-SEARCH goes to a broadcast address on this port, for routers that drop multicast. */
    const val BROADCAST_PORT = 1990

    fun request(searchTarget: String, broadcast: Boolean = false): String = buildString {
        append(if (broadcast) "B-SEARCH * HTTP/1.1\r\n" else "M-SEARCH * HTTP/1.1\r\n")
        append(if (broadcast) "HOST: 255.255.255.255:$BROADCAST_PORT\r\n" else "HOST: 239.255.255.250:1900\r\n")
        append("MAN: \"ssdp:discover\"\r\n")
        append("MX: 2\r\n")
        append("ST: ").append(searchTarget).append("\r\n")
        append("USER-AGENT: ").append(UdapXml.USER_AGENT).append("\r\n")
        append("\r\n")
    }

    fun parse(raw: String): SsdpNotice? {
        if (!raw.contains("HTTP/1.1", ignoreCase = true) && !raw.contains("NOTIFY", ignoreCase = true)) return null
        val headers = linkedMapOf<String, String>()
        raw.lineSequence().forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        val location = headers["location"] ?: return null
        return SsdpNotice(
            location = location,
            server = headers["server"].orEmpty(),
            searchTarget = headers["st"].orEmpty(),
            usn = headers["usn"].orEmpty(),
            raw = raw,
        )
    }

    fun interesting(notice: SsdpNotice): Boolean {
        val blob = (notice.server + notice.searchTarget + notice.usn + notice.location).lowercase()
        return blob.contains("udap") || blob.contains("netcast") || blob.contains("lge") ||
            blob.contains("lgsmarttv") || blob.contains("webos") || blob.contains("roku").not() && blob.contains("lg")
    }
}
