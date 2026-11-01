package dev.netcastremote.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test
    fun pairingTemplatesMatchTheSpecLengths() {
        assertEquals(105, UdapXml.showKey().toByteArray(Charsets.UTF_8).size)
        assertEquals(141, UdapXml.hello("166350", 8080).toByteArray(Charsets.UTF_8).size)
        assertEquals(121, UdapXml.byebye(8080).toByteArray(Charsets.UTF_8).size)
        assertTrue(UdapXml.USER_AGENT.contains("UDAP/2.0"))
    }

    @Test
    fun xmlEscapesAndClipsText() {
        val xml = UdapXml.event("TextEdited", mapOf("state" to "Editing", "value" to "a<b>&"))
        assertTrue(xml.contains("a&lt;b&gt;&amp;"))
        val big = "é".repeat(2000)
        assertTrue(TextLimits.clipUtf8(big).toByteArray().size <= TextLimits.MAX_BYTES)
    }

    @Test
    fun parsesChannelVolumeAppsAndEvents() {
        val list = UdapParse.channelList(
            "<dataList><data><chtype>terrestrial</chtype><major>11</major><minor>1</minor>" +
                "<displayMajor>11</displayMajor><displayMinor>1</displayMinor><sourceIndex>2</sourceIndex>" +
                "<physicalNum>11</physicalNum><chname>MBC</chname><progName>News</progName><audioCh>0</audioCh>" +
                "<inputSourceName>Antenna</inputSourceName><labelName>Antenna</labelName></data></dataList>",
        )
        assertEquals("11-1", list.single().numberLabel)
        assertEquals("MBC", list.single().name)
        val volume = UdapParse.volume("<mute>true</mute><minLevel>0</minLevel><maxLevel>100</maxLevel><level>12</level>")
        assertTrue(volume.mute)
        assertEquals(12, volume.level)
        val apps = UdapParse.apps("<data><auid>AB</auid><name>Browser</name><type>3</type><cpid>b</cpid><adult>true</adult><icon_name>b.png</icon_name></data>")
        assertTrue(apps.single().adult)
        val event = UdapParse.event("/udap/api/event", "<name>KeyboardVisible</name><value>true</value><mode>password</mode>")
        assertEquals(HostEvent.Keyboard(true, true), event)
        assertTrue(UdapParse.is3d("<is3D>true</is3D>"))
        assertEquals(UiMode.TouchPad, UdapParse.uiMode("<mode>TouchPad</mode>"))
    }

    @Test
    fun localTvAcceptsPairingAndAKey() {
        val server = MiniHttpServer { request ->
            val body = request.body.toString(Charsets.UTF_8)
            val status = when {
                request.path == "/udap/api/pairing" && body.contains("showKey") -> 200
                request.path == "/udap/api/pairing" && body.contains(">hello<") && body.contains("513296") -> 200
                request.path == "/udap/api/pairing" && body.contains(">hello<") -> 401
                request.path == "/udap/api/command" && body.contains("HandleKeyInput") -> 200
                request.path.startsWith("/udap/api/data") -> 200
                else -> 400
            }
            val payload = if (request.path.contains("volume_info")) {
                "<level>4</level><mute>false</mute><minLevel>0</minLevel><maxLevel>100</maxLevel>"
            } else {
                ""
            }
            Outgoing(status, body = payload.toByteArray())
        }
        server.start()
        try {
            val link = SocketTvLink("127.0.0.1", server.port, Dialect.UDAP)
            assertEquals(200, link.requestPairingKey())
            assertEquals(401, link.hello("000000", server.port))
            assertEquals(200, link.hello("51 32 96", server.port))
            val started = System.nanoTime()
            assertEquals(200, link.command("HandleKeyInput", mapOf("value" to "24"), keepAlive = true))
            assertEquals(200, link.command("HandleKeyInput", mapOf("value" to "24"), keepAlive = true))
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue("volume posts took ${elapsedMs}ms", elapsedMs < 2_000)
            assertEquals(4, UdapParse.volume(link.query("volume_info")).level)
            link.close()
        } finally {
            server.close()
        }
    }

    @Test
    fun eventServerReceivesBye() {
        var seen = ""
        val server = MiniHttpServer { request ->
            seen = request.path + " " + request.body.toString(Charsets.UTF_8)
            Outgoing(200)
        }
        server.start()
        try {
            RawHttpClient().use { client ->
                val xml = UdapXml.byebye(server.port)
                val result = client.post("127.0.0.1", server.port, "/udap/api/pairing", xml.toByteArray(), "text/xml; charset=utf-8", false)
                assertEquals(200, result.status)
            }
            Thread.sleep(50)
            assertTrue(seen.contains("byebye"))
            assertEquals(HostEvent.Bye, UdapParse.event("/udap/api/pairing", UdapXml.byebye(1)))
        } finally {
            server.close()
        }
    }

    @Test
    fun volumeStepsStayInsideTheKidsCeiling() {
        val steps = VolumeSteps.toward(current = 10, target = 40, min = 0, max = 100, ceiling = 15)
        assertEquals(5, steps.size)
        assertTrue(steps.all { it == KeyCode.VOLUME_UP })
        assertTrue(VolumeSteps.toward(3, 3, 0, 100).isEmpty())
    }

    @Test
    fun coalescerKeepsOnlyTheNewestRepeat() {
        val gate = KeyCoalescer()
        assertTrue(gate.submit(24))
        assertFalse(gate.submit(24))
        assertFalse(gate.submit(25))
        assertEquals(25, gate.done())
        assertEquals(null, gate.done())
    }

    @Test
    fun matterAndGuideAndDlna() {
        val log = mutableListOf<String>()
        val sink = object : MatterSink {
            override fun power(turnOn: Boolean?) { log += "power:$turnOn" }
            override fun volumeTo(level: Int) { log += "vol:$level" }
            override fun mute() { log += "mute" }
            override fun channelUp() { log += "up" }
            override fun channelDown() {}
            override fun tune(number: String?, name: String?) { log += "tune:$number:$name" }
            override fun key(code: Int) { log += "key:$code" }
            override fun transport(command: String) { log += "tr:$command" }
            override fun launchApp(name: String) {}
            override fun runMacro(name: String) { log += "macro:$name" }
        }
        MatterClusters.invoke(MatterInvoke(MatterCluster.OnOff, "Off"), sink)
        MatterClusters.invoke(MatterClusters.parseLine("LevelControl MoveToLevel level=18")!!, sink)
        MatterClusters.invoke(MatterInvoke(MatterCluster.KeypadInput, "SendKey", mapOf("key" to "MUTE")), sink)
        MatterClusters.invoke(MatterInvoke(MatterCluster.MediaPlayback, "macro", mapOf("name" to "Movie")), sink)
        assertEquals(listOf("power:false", "vol:18", "key:26", "macro:Movie"), log)

        val guide = XmlTv.parse(
            """<tv><programme start="20261031190000 +0000" stop="20261031200000 +0000" channel="mbc"><title>News &amp; Weather</title></programme></tv>""",
        )
        assertEquals("News & Weather", guide.single().title)
        assertTrue(guide.single().startEpochMs > 0)

        val soap = Dlna.setUri("http://10.0.0.5/clip.mp4", "Clip")
        assertTrue(soap.contains("SetAVTransportURI"))
        assertTrue(soap.contains("clip.mp4"))
        val control = Dlna.controlUrl(
            "<service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/upnp/control/AVTransport</controlURL></service>",
        )
        assertEquals("/upnp/control/AVTransport", control)
        assertEquals(102, WakeOnLan.packet("00:11:22:33:44:55").size)
    }

    @Test
    fun broadcastSearchFollowsSpec() {
        val request = Ssdp.request("udap:rootservice", broadcast = true)
        assertTrue(request.startsWith("B-SEARCH * HTTP/1.1\r\n"))
        assertTrue(request.contains("HOST: 255.255.255.255:1990\r\n"))
        assertTrue(Ssdp.request("udap:rootservice").startsWith("M-SEARCH"))
    }

    @Test
    fun macAddressesNormalize() {
        listOf("a1:b2:c3:d4:e5:f6", "A1-B2-C3-D4-E5-F6", "a1b2c3d4e5f6", " a1b2.c3d4.e5f6 ").forEach {
            assertEquals("A1:B2:C3:D4:E5:F6", WakeOnLan.normalize(it))
        }
        assertEquals(null, WakeOnLan.normalize("a1:b2:c3:d4:e5"))
        assertEquals(null, WakeOnLan.normalize("a1:b2:c3:d4:e5:fg"))
        assertEquals(0xA1.toByte(), WakeOnLan.packet("a1-b2-c3-d4-e5-f6")[6])
    }

    @Test
    fun ssdpNoticeAndDemoTv() {
        val raw = "HTTP/1.1 200 OK\r\nLOCATION: http://192.168.1.20:8080/udap.xml\r\nSERVER: Linux UDAP/2.0 LGSmartTV\r\nST: udap:rootservice\r\nUSN: uuid:abc\r\n\r\n"
        val notice = Ssdp.parse(raw)!!
        assertEquals("http://192.168.1.20:8080/udap.xml", notice.location)
        assertTrue(Ssdp.interesting(notice))
        assertTrue(UdapParse.description("<friendlyName>Lounge</friendlyName><modelName>LM7600</modelName><UDN>uuid:abc</UDN>", notice.server).uuid == "abc")
        assertTrue(UdapParse.description("<modelName>OLED</modelName>", "WebOS/1.0").webOs)

        val demo = DemoTvLink()
        assertEquals(200, demo.hello("000000", 9))
        demo.command("HandleKeyInput", mapOf("value" to KeyCode.VOLUME_UP.code.toString()))
        assertEquals(19, UdapParse.volume(demo.query("volume_info")).level)
        assertEquals(4, UdapParse.channelList(demo.query("channel_list")).size)
        assertTrue(demo.queryBytes("screen_image").copyOf(2).contentEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        assertEquals("The TV could not start this app. The NetCast store has been gone for years, so this tile may never open.", AppResultText.explain("Execute", "ERROR"))
    }

    @Test
    fun everySpecKeyHasAUniqueCode() {
        assertEquals(KeyCode.entries.size, KeyCode.entries.map { it.code }.toSet().size)
        assertEquals(listOf(KeyCode.RED, KeyCode.GREEN, KeyCode.YELLOW, KeyCode.BLUE), KeyCode.colorOrder)
        assertEquals(31, KeyCode.RED.code)
        assertEquals(29, KeyCode.BLUE.code)
    }

    @Test
    fun onlySteppingKeysRepeat() {
        assertTrue(KeyCode.VOLUME_DOWN.repeats)
        assertTrue(KeyCode.CHANNEL_UP.repeats)
        assertTrue(KeyCode.LEFT.repeats)
        assertFalse(KeyCode.MUTE.repeats)
        assertFalse(KeyCode.BACK.repeats)
        assertFalse(KeyCode.FLASHBACK.repeats)
        assertFalse(KeyCode.OK.repeats)
    }
}
