package dev.netcastremote.session

import dev.netcastremote.model.ChannelDto
import dev.netcastremote.model.Diagnostics
import dev.netcastremote.model.DiscoveredTv
import dev.netcastremote.model.KeyboardUi
import dev.netcastremote.model.LinkState
import dev.netcastremote.model.Persisted
import dev.netcastremote.model.SavedTv
import dev.netcastremote.model.Screen
import dev.netcastremote.model.nowTitle
import dev.netcastremote.model.statusLine
import dev.netcastremote.model.Settings
import dev.netcastremote.model.UiState
import dev.netcastremote.model.toChannel
import dev.netcastremote.model.toDto
import dev.netcastremote.model.toMacro
import dev.netcastremote.model.toProgramme
import dev.netcastremote.protocol.AppResultText
import dev.netcastremote.protocol.Channel
import dev.netcastremote.protocol.DemoTvLink
import dev.netcastremote.protocol.Dialect
import dev.netcastremote.protocol.HostEvent
import dev.netcastremote.protocol.Incoming
import dev.netcastremote.protocol.KeyCoalescer
import dev.netcastremote.protocol.KeyCode
import dev.netcastremote.protocol.Macro
import dev.netcastremote.protocol.MacroStep
import dev.netcastremote.protocol.MatterInvoke
import dev.netcastremote.protocol.MatterSink
import dev.netcastremote.protocol.MiniHttpServer
import dev.netcastremote.protocol.Outgoing
import dev.netcastremote.protocol.Programme
import dev.netcastremote.protocol.RawHttpClient
import dev.netcastremote.protocol.SocketTvLink
import dev.netcastremote.protocol.Ssdp
import dev.netcastremote.protocol.TextLimits
import dev.netcastremote.protocol.TvApp
import dev.netcastremote.protocol.TvLink
import dev.netcastremote.protocol.UdapParse
import dev.netcastremote.protocol.UiMode
import dev.netcastremote.protocol.VolumeInfo
import dev.netcastremote.protocol.VolumeSteps
import dev.netcastremote.protocol.WakeOnLan
import dev.netcastremote.protocol.XmlTv
import dev.netcastremote.protocol.probeDialect
import dev.netcastremote.protocol.repeatDelayMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Monotonic milliseconds, for timeouts. */
private fun now(): Long = System.nanoTime() / 1_000_000

class TvRepository(
    private val platform: Platform,
    private val scope: CoroutineScope,
) : MatterSink {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val coalescer = KeyCoalescer()
    private var disk = Persisted()
    private var link: TvLink? = null
    private var events: MiniHttpServer? = null
    private var pollJob: Job? = null
    private var repeatJob: Job? = null
    private var digitJob: Job? = null
    private var volumeJob: Job? = null
    private var wakeJob: Job? = null
    private var watchJob: Job? = null
    private val watched = setOf(LinkState.Failed, LinkState.Asleep)
    private var saveJob: Job? = null
    private var lastIp: String? = null
    private var lastPreview = 0L
    private var ringing = false
    private var ducked = false
    var onForeground: ((Boolean) -> Unit)? = null
    var onWidgets: (() -> Unit)? = null
    var previewBytes: ByteArray? = null
        private set

    fun start() {
        scope.launch {
            try {
                disk = platform.load()
                _state.update { it.hydrate(disk) }
                val active = disk.tvs.find { it.id == disk.activeId }
                when {
                    disk.settings.demo -> useDemo(persist = false)
                    active != null && active.pairingKey.isNotBlank() -> {
                        // A paired set opens on the remote; an unreachable one shows its offline card there, not the search page.
                        _state.update { it.copy(screen = Screen.Remote) }
                        connect(active, showCode = false)
                    }
                    active != null -> _state.update { it.copy(screen = Screen.Discover, link = LinkState.Idle) }
                    disk.tvs.isEmpty() && !disk.settings.onboarded -> _state.update { it.copy(screen = Screen.Welcome) }
                    else -> _state.update { it.copy(screen = Screen.Discover) }
                }
            } finally {
                if (!loaded.isCompleted) loaded.complete(Unit)
            }
        }
        scope.launch {
            _state.map { it.link to it.uiVisible }.distinctUntilChanged().collect { (now, visible) ->
                if (visible && now in watched) watchForTv()
            }
        }
        platform.watchAddress { ip ->
            val previous = lastIp
            lastIp = ip
            if (previous != null && ip != null && previous != ip && _state.value.link == LinkState.Online) {
                scope.launch(Dispatchers.IO) {
                    runCatching { link?.updateIp(ip, previous) }
                    log("IP $previous -> $ip")
                }
            }
        }
    }

    fun setVisible(visible: Boolean) {
        _state.update { it.copy(uiVisible = visible) }
        onForeground?.invoke(wantsService())
    }

    fun setPocket(near: Boolean) = _state.update { it.copy(pocketNear = near) }

    fun open(screen: Screen) = _state.update { it.copy(screen = screen) }

    /** Ends the welcome tour: straight into a search, or into the demo TV. */
    fun finishOnboarding(demo: Boolean) {
        updateSettings { copy(onboarded = true) }
        if (demo) useDemo() else search()
    }

    fun search() {
        scope.launch(Dispatchers.IO) {
            val issue = networkIssue()
            if (issue != null) {
                _state.update { it.copy(link = issue, searching = false, screen = Screen.Discover) }
                return@launch
            }
            _state.update { it.copy(searching = true, link = LinkState.Searching, screen = Screen.Discover) }
            val lock = multicast()
            try {
                val notices = searchLan()
                val cards = notices.mapNotNull { describe(it) }.distinctBy { it.id }
                val onlyWebOs = cards.isNotEmpty() && cards.all { it.webOs }
                _state.update {
                    it.copy(
                        searching = false,
                        discovered = cards,
                        link = when {
                            onlyWebOs -> LinkState.WebOs
                            cards.isEmpty() -> LinkState.Isolated
                            else -> LinkState.Found
                        },
                        message = if (onlyWebOs) "This set speaks webOS, not NetCast UDAP." else it.message,
                        diagnostics = it.diagnostics.copy(ssdp = notices.firstOrNull()?.raw.orEmpty()),
                    )
                }
                log("SSDP ${notices.size} replies, ${cards.size} TVs")
            } finally {
                lock?.close()
            }
        }
    }

    fun choose(card: DiscoveredTv) {
        if (card.webOs) {
            _state.update { it.copy(link = LinkState.WebOs, message = "A 2014-or-later webOS set uses a different protocol. This remote stops here.") }
            return
        }
        scope.launch(Dispatchers.IO) {
            val dialect = runCatching { probeDialect(RawHttpClient(), card.host, card.port) }.getOrDefault(Dialect.UDAP)
            val existing = disk.tvs.find { it.id == card.id }
            val tv = (existing ?: SavedTv(id = card.id, name = card.name.ifBlank { "LG TV" }, host = card.host, port = card.port, uuid = card.uuid))
                .copy(host = card.host, port = card.port, model = card.model.ifBlank { existing?.model.orEmpty() }, dialect = dialect.name, name = existing?.name ?: card.name.ifBlank { "LG TV" })
            if (tv.pairingKey.isNotBlank()) connect(tv, showCode = false) else connect(tv, showCode = true)
        }
    }

    fun manual(host: String, port: Int) {
        val clean = host.trim()
        if (clean.isEmpty() || port !in 1..65535) return
        choose(DiscoveredTv("$clean:$port", clean, clean, port, "", false, "$clean:$port"))
    }

    fun submitCode(code: String) {
        val tv = _state.value.active ?: return
        scope.launch(Dispatchers.IO) {
            val digits = code.filter { it.isDigit() }
            val status = runCatching { link?.hello(digits, ensureEvents()) ?: 0 }.getOrDefault(0)
            noteHttp("hello", status)
            when (status) {
                200 -> finishPair(tv.copy(pairingKey = digits, pairedAt = System.currentTimeMillis()))
                401 -> _state.update { it.copy(message = "That code was not valid. The TV keeps the prompt up for about a minute.") }
                503 -> _state.update { it.copy(link = LinkState.TooMany, message = "This TV already has as many controllers as it allows.") }
                else -> _state.update { it.copy(message = "Pairing failed ($status).") }
            }
        }
    }

    fun useDemo(persist: Boolean = true) {
        closeLink()
        link = DemoTvLink()
        val tv = SavedTv(id = "demo", name = "Demo TV", room = "Demo", host = "demo", pairingKey = "demo", dialect = "UDAP")
        disk = disk.copy(settings = disk.settings.copy(demo = true), activeId = "demo", tvs = listOf(tv) + disk.tvs.filter { it.id != "demo" })
        _state.update { it.copy(link = LinkState.Demo, active = tv, settings = disk.settings, message = "Demo TV. Nothing is sent to a real set.", screen = Screen.Remote) }
        refreshAll()
        if (persist) save()
        startPoll()
        onForeground?.invoke(true)
    }

    fun leaveDemo() {
        closeLink()
        disk = disk.copy(settings = disk.settings.copy(demo = false), activeId = disk.tvs.firstOrNull { it.id != "demo" }?.id)
        _state.update { it.copy(link = LinkState.Idle, settings = disk.settings, screen = Screen.Discover, active = disk.tvs.find { it.id == disk.activeId }) }
        save()
    }

    fun activate(id: String) {
        val tv = disk.tvs.find { it.id == id } ?: return
        cancelWake()
        disk = disk.copy(activeId = id)
        _state.update { it.hydrate(disk) }
        save()
        if (tv.pairingKey.isNotBlank()) scope.launch(Dispatchers.IO) { connect(tv, showCode = false) }
    }

    fun forget(id: String) {
        if (_state.value.active?.id == id) disconnect()
        disk = disk.copy(tvs = disk.tvs.filter { it.id != id }, activeId = disk.activeId.takeIf { it != id })
        _state.update { it.hydrate(disk) }
        save()
    }

    fun rePair() {
        val tv = _state.value.active ?: return
        scope.launch(Dispatchers.IO) { connect(tv.copy(pairingKey = ""), showCode = true) }
    }

    fun reconnect() {
        val tv = _state.value.active ?: return
        scope.launch(Dispatchers.IO) { connect(tv, showCode = tv.pairingKey.isBlank()) }
    }

    /**
     * Turns the TV on: Wake-on-LAN, then a reachability probe every 2 s until its UDAP port opens and a hello can land.
     * No power key here, because a TV that is actually on but briefly unreachable would toggle off.
     */
    fun wakeTv() {
        val tv = _state.value.active ?: return
        if (_state.value.link == LinkState.Online || _state.value.link == LinkState.Demo) return
        val mac = tv.mac
        wakeJob?.cancel()
        if (mac.isBlank()) {
            scope.launch(Dispatchers.IO) {
                if (reachable(tv.host, tv.port)) connect(tv, showCode = false)
                else _state.update { it.copy(message = "Turn the TV on with its own remote. This app connects as soon as it's on.") }
            }
            return
        }
        _state.update { it.copy(waking = true) }
        wakeJob = scope.launch(Dispatchers.IO) {
            val self = coroutineContext[Job]
            try {
                // Probes to a sleeping host take their full timeout, so bound the wait by the clock, not the attempt count.
                // Repeat the packet early in case one is lost.
                val started = now()
                var attempt = 0
                while (now() - started < 40_000) {
                    if (attempt in setOf(0, 1, 3, 6)) sendWake(mac, tv.host)
                    attempt++
                    if (reachable(tv.host, tv.port)) {
                        log("wake: ${tv.host} answered after %.1f s".format((now() - started) / 1000.0))
                        connect(tv, showCode = false)
                        return@launch
                    }
                    delay(2_000)
                }
                log("wake: no answer from ${tv.host} in 40 s")
                _state.update { it.copy(message = "${tv.name} didn't wake. NetCast sets usually only turn on with their own remote.") }
            } finally {
                // A newer wake owns the flag once it has replaced this one.
                if (wakeJob === self) _state.update { it.copy(waking = false) }
            }
        }
    }

    /**
     * NetCast sets drop off the network in standby, so the phone can't turn them on. Instead, while the
     * remote is on screen and the TV is off, probe its port every few seconds and connect the moment
     * someone turns it on with its own remote.
     */
    private fun watchForTv() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3_000)
                val s = _state.value
                val tv = s.active
                if (tv == null || tv.pairingKey.isBlank() || !s.uiVisible || s.link !in watched) break
                if (s.waking) continue
                if (reachable(tv.host, tv.port)) {
                    log("watch: ${tv.host} is back")
                    connect(tv, showCode = false)
                }
            }
        }
    }

    fun cancelWake() {
        wakeJob?.cancel()
        wakeJob = null
        _state.update { it.copy(waking = false) }
    }

    fun clearMessage() = _state.update { it.copy(message = "") }

    /** The TV's PNG for an app, cached on disk. Null when the TV has none or is unreachable. */
    suspend fun appIcon(app: TvApp): ByteArray? = withContext(Dispatchers.IO) {
        val file = platform.cacheDir.resolve("app-icons/${app.auid.filter { it.isLetterOrDigit() }}.png")
        if (file.exists()) return@withContext file.readBytes()
        val bytes = runCatching { link?.queryBytes("appicon_get", mapOf("auid" to app.auid, "appname" to app.name)) }.getOrNull()
        val png = bytes?.takeIf { it.size > 8 && it[1] == 'P'.code.toByte() && it[2] == 'N'.code.toByte() } ?: return@withContext null
        file.parentFile?.mkdirs()
        file.writeBytes(png)
        png
    }

    fun disconnect() {
        cancelWake()
        val port = events?.port ?: 0
        scope.launch(Dispatchers.IO) { runCatching { link?.byebye(port) } }
        closeLink()
        _state.update { it.copy(link = LinkState.Idle, message = "Disconnected.") }
        onForeground?.invoke(false)
    }

    fun rename(name: String, room: String) {
        val tv = _state.value.active ?: return
        val updated = tv.copy(name = name.ifBlank { tv.name }, room = room.ifBlank { tv.room })
        upsert(updated)
        _state.update { it.copy(active = updated) }
        save()
    }

    /** Blank clears it. Callers validate with [WakeOnLan.normalize] first. */
    fun setMac(mac: String) {
        val tv = _state.value.active ?: return
        val updated = tv.copy(mac = WakeOnLan.normalize(mac).orEmpty())
        upsert(updated)
        _state.update { it.copy(active = updated) }
        save()
    }

    fun updateSettings(block: Settings.() -> Settings) {
        val next = _state.value.settings.block()
        disk = disk.copy(settings = next)
        _state.update { it.copy(settings = next) }
        save()
        onForeground?.invoke(wantsService())
    }

    fun keyDown(code: KeyCode) {
        if (_state.value.pocketNear) return
        if (_state.value.settings.kidsMode && code == KeyCode.POWER) return
        if (code == KeyCode.POWER && _state.value.link == LinkState.Asleep) wake()
        if (_state.value.recordingMacro) appendStep(MacroStep("key", code.name))
        repeatJob?.cancel()
        repeatJob = scope.launch(Dispatchers.IO) {
            sendCode(code.code, code.repeats)
            if (!code.repeats) return@launch
            var tick = 0
            while (isActive) {
                delay(repeatDelayMs(tick++))
                sendCode(code.code, true)
            }
        }
    }

    fun keyUp() {
        repeatJob?.cancel()
        repeatJob = null
    }

    fun keyTap(code: KeyCode) {
        if (_state.value.pocketNear) return
        if (_state.value.settings.kidsMode && code == KeyCode.POWER) return
        if (_state.value.recordingMacro) appendStep(MacroStep("key", code.name))
        scope.launch(Dispatchers.IO) { sendCode(code.code, false) }
    }

    fun digit(code: KeyCode) {
        val label = if (code == KeyCode.DASH) "-" else code.label
        val next = (_state.value.ghostDigits + label).take(8)
        _state.update { it.copy(ghostDigits = next) }
        digitJob?.cancel()
        digitJob = scope.launch {
            delay(2_500)
            commitDigits()
        }
    }

    fun backspaceDigits() {
        _state.update { it.copy(ghostDigits = it.ghostDigits.dropLast(1)) }
    }

    fun commitDigits() {
        digitJob?.cancel()
        val text = _state.value.ghostDigits
        _state.update { it.copy(ghostDigits = "") }
        if (text.isBlank()) return
        val match = _state.value.channels.firstOrNull { it.numberLabel == text }
        if (match != null) tune(match) else scope.launch(Dispatchers.IO) {
            text.forEach { ch ->
                val key = when (ch) {
                    '-' -> KeyCode.DASH
                    in '0'..'9' -> KeyCode.entries.first { it.family == KeyCode.Family.DIGIT && it.label == ch.toString() }
                    else -> return@forEach
                }
                sendCode(key.code, true)
            }
        }
    }

    fun favoriteSlot(index: Int) {
        val id = _state.value.favorites.getOrNull(index) ?: return
        _state.value.channels.firstOrNull { it.id == id }?.let { tune(it) }
    }

    /** Latest target wins: a new level cancels the steps still going toward the old one. */
    fun setVolumeLevel(target: Int) {
        _state.update { it.copy(optimisticVolume = target) }
        volumeJob?.cancel()
        volumeJob = scope.launch(Dispatchers.IO) { stepVolume(target) }
    }

    fun tune(channel: Channel) {
        if (_state.value.settings.kidsMode && _state.value.kidsChannels.isNotEmpty() && channel.id !in _state.value.kidsChannels) return
        scope.launch(Dispatchers.IO) {
            val status = link?.command(
                "HandleChannelChange",
                mapOf(
                    "major" to channel.major.toString(),
                    "minor" to channel.minor.toString(),
                    "sourceIndex" to channel.sourceIndex.toString(),
                    "physicalNum" to channel.physicalNum.toString(),
                ),
            ) ?: return@launch
            noteHttp("tune", status)
            if (status == 200) rememberChannel(channel)
        }
    }

    fun toggleFavorite(channel: Channel) {
        val id = tvId()
        val now = _state.value.favorites.toMutableList()
        if (!now.remove(channel.id)) now.add(channel.id)
        disk = disk.copy(favorites = disk.favorites + (id to now))
        _state.update { it.copy(favorites = now) }
        save()
        onWidgets?.invoke()
    }

    fun nickname(channel: Channel, name: String) {
        val map = _state.value.nicknames.toMutableMap()
        if (name.isBlank()) map.remove(channel.id) else map[channel.id] = name
        disk = disk.copy(nicknames = disk.nicknames + (tvId() to map))
        _state.update { it.copy(nicknames = map) }
        save()
    }

    fun setLogo(channelId: String, path: String) {
        val map = _state.value.logos.toMutableMap()
        map[channelId] = path
        disk = disk.copy(logos = disk.logos + (tvId() to map))
        _state.update { it.copy(logos = map) }
        save()
    }

    fun setInputName(label: String, name: String) {
        val map = _state.value.inputNames.toMutableMap()
        if (name.isBlank()) map.remove(label) else map[label] = name
        disk = disk.copy(inputNames = disk.inputNames + (tvId() to map))
        _state.update { it.copy(inputNames = map) }
        save()
    }

    fun bindInputMacro(label: String, macroId: String) {
        val map = _state.value.inputMacros.toMutableMap()
        if (macroId.isBlank()) map.remove(label) else map[label] = macroId
        disk = disk.copy(inputMacros = disk.inputMacros + (tvId() to map))
        _state.update { it.copy(inputMacros = map) }
        save()
    }

    fun playInput(label: String) {
        val id = _state.value.inputMacros[label] ?: return
        disk.macros.map { it.toMacro() }.firstOrNull { it.id == id }?.let { runMacro(it) }
    }

    fun refreshChannels() = scope.launch(Dispatchers.IO) { loadChannels() }

    fun refreshApps() = scope.launch(Dispatchers.IO) { loadApps() }

    fun launchApp(app: TvApp) {
        scope.launch(Dispatchers.IO) {
            val status = link?.command("AppExecute", mapOf("auid" to app.auid, "appname" to app.name, "contentId" to app.contentId)) ?: return@launch
            noteHttp("launch", status)
            val recent = (listOf(app.auid) + _state.value.recentApps.filter { it != app.auid }).take(12)
            disk = disk.copy(recentApps = disk.recentApps + (tvId() to recent))
            _state.update { it.copy(recentApps = recent) }
            save()
        }
    }

    fun quitApp(app: TvApp) {
        scope.launch(Dispatchers.IO) {
            link?.command("AppTerminate", mapOf("auid" to app.auid, "appname" to app.name))
        }
    }

    fun toggleFavoriteApp(app: TvApp) {
        val now = _state.value.favoriteApps.toMutableList()
        if (!now.remove(app.auid)) now.add(app.auid)
        disk = disk.copy(favoriteApps = disk.favoriteApps + (tvId() to now))
        _state.update { it.copy(favoriteApps = now) }
        save()
        onWidgets?.invoke()
    }

    fun pointer(show: Boolean) {
        scope.launch(Dispatchers.IO) {
            link?.event("CursorVisible", mapOf("value" to show.toString(), "mode" to "auto"))
        }
    }

    fun pointerMove(dx: Float, dy: Float) {
        val settings = _state.value.settings
        val flip = if (settings.leftHandedPointer) -1f else 1f
        val x = curve(dx * flip, settings.pointerSensitivity)
        val y = curve(dy, settings.pointerSensitivity)
        if (x == 0 && y == 0) return
        scope.launch(Dispatchers.IO) {
            link?.command("HandleTouchMove", mapOf("x" to x.toString(), "y" to y.toString()), true)
        }
    }

    fun pointerClick() {
        scope.launch(Dispatchers.IO) { link?.command("HandleTouchClick", emptyMap()) }
    }

    fun pointerWheel(up: Boolean) {
        scope.launch(Dispatchers.IO) {
            link?.command("HandleTouchWheel", mapOf("value" to if (up) "up" else "down"))
        }
    }

    fun drag(active: Boolean) {
        scope.launch(Dispatchers.IO) { link?.event("DragMode", mapOf("value" to active.toString())) }
    }

    fun editText(editState: String, value: String) {
        val clipped = TextLimits.clipUtf8(value)
        _state.update { it.copy(keyboard = it.keyboard?.copy(text = clipped, dirty = editState != "EditStart")) }
        scope.launch(Dispatchers.IO) {
            link?.event("TextEdited", mapOf("state" to editState, "value" to clipped))
        }
    }

    fun closeKeyboard() {
        val text = _state.value.keyboard?.text.orEmpty()
        scope.launch(Dispatchers.IO) { link?.event("TextEdited", mapOf("state" to "EditEnd", "value" to text)) }
        _state.update { it.copy(keyboard = null) }
    }

    fun shareText(value: String) {
        val keyboard = _state.value.keyboard
        if (keyboard?.visible == true) editText("Editing", keyboard.text + value)
        else _state.update { it.copy(pendingShare = value, message = "Focus a text field on the TV, then the shared text is typed there.") }
    }

    fun consumeShare() {
        val pending = _state.value.pendingShare ?: return
        val keyboard = _state.value.keyboard ?: return
        _state.update { it.copy(pendingShare = null) }
        editText("Editing", keyboard.text + pending)
    }

    fun toggleRecord() = _state.update { it.copy(recordingMacro = !it.recordingMacro) }

    fun saveMacro(name: String) {
        if (name.isBlank()) return
        val macro = Macro(
            id = "m" + System.currentTimeMillis(),
            name = name,
            tvId = tvId(),
            steps = _state.value.macros.lastOrNull { it.id == "recording" }?.steps.orEmpty(),
        )
        // Steps recorded live into a draft stored in state via recordingSteps
        val draft = recording
        val saved = macro.copy(steps = draft)
        recording = emptyList()
        disk = disk.copy(macros = disk.macros + saved.toDto())
        _state.update { it.copy(macros = disk.macros.map { m -> m.toMacro() }, recordingMacro = false) }
        save()
    }

    fun deleteMacro(id: String) {
        disk = disk.copy(macros = disk.macros.filter { it.id != id })
        _state.update { it.copy(macros = disk.macros.map { it.toMacro() }) }
        save()
    }

    fun runMacro(macro: Macro) {
        scope.launch(Dispatchers.IO) {
            macro.steps.forEach { step ->
                when (step.op) {
                    "key" -> KeyCode.fromName(step.a)?.let { sendCode(it.code, true) }
                    "wait" -> delay(step.ms.coerceIn(0, 30_000))
                    "volume" -> stepVolume(step.a.toIntOrNull() ?: return@forEach)
                    "tune" -> link?.command(
                        "HandleChannelChange",
                        mapOf("major" to step.a, "minor" to step.b, "sourceIndex" to step.c, "physicalNum" to step.d),
                    )
                }
            }
        }
    }

    fun runMacroNamed(name: String) {
        _state.value.macros.firstOrNull { it.name.equals(name, true) && (it.tvId == tvId() || it.tvId.isBlank()) }?.let { runMacro(it) }
            ?: _state.update { it.copy(message = "No macro named $name.") }
    }

    fun importGuide(xml: String) {
        val items = XmlTv.parse(xml)
        disk = disk.copy(guide = items.map { it.toDto() })
        _state.update { it.copy(guide = items, message = "${items.size} programmes imported. This guide is phone data.") }
        save()
    }

    fun remind(programme: Programme) {
        val whenMs = programme.startEpochMs - 5 * 60_000
        val fire = { _state.update { it.copy(message = "${programme.title} starts in 5 minutes.") } }
        if (platform.scheduleReminder(programme.title, programme.startEpochMs.toInt(), whenMs, fire)) {
            _state.update { it.copy(message = "Reminder set for ${programme.title}.") }
        } else {
            _state.update { it.copy(message = "Allow exact alarms to remind you when a programme is about to start.") }
        }
    }

    fun scheduleSleep(minutes: Int) {
        if (!platform.scheduleSleep(System.currentTimeMillis() + minutes * 60_000L) { command("sleep") }) {
            _state.update { it.copy(message = "Allow exact alarms, then set the sleep timer again.") }
            return
        }
        _state.update { it.copy(message = "Power goes off in $minutes minutes.") }
        onForeground?.invoke(true)
    }

    fun cancelSleep() {
        platform.cancelSleep()
        _state.update { it.copy(message = "Sleep timer cancelled.") }
    }

    fun grabPreview() = scope.launch(Dispatchers.IO) { grab() }

    fun setLayout(keys: List<String>) {
        disk = disk.copy(layouts = disk.layouts + (tvId() to keys))
        _state.update { it.copy(customLayout = keys) }
        save()
    }

    fun setKidsChannels(ids: List<String>) {
        disk = disk.copy(kidsChannels = disk.kidsChannels + (tvId() to ids))
        _state.update { it.copy(kidsChannels = ids) }
        save()
    }

    fun setPin(pin: String) {
        val salt = java.util.UUID.randomUUID().toString()
        val hash = hashPin(salt, pin)
        updateSettings { copy(pinSalt = salt, pinHash = hash) }
    }

    fun checkPin(pin: String): Boolean {
        val settings = _state.value.settings
        if (settings.pinHash.isBlank()) return false
        return hashPin(settings.pinSalt, pin) == settings.pinHash
    }

    fun command(raw: String) {
        when (raw.substringBefore(':')) {
            "power" -> power(null)
            "mute" -> keyTap(KeyCode.MUTE)
            "volume_up" -> keyTap(KeyCode.VOLUME_UP)
            "volume_down" -> keyTap(KeyCode.VOLUME_DOWN)
            "channel_up" -> keyTap(KeyCode.CHANNEL_UP)
            "channel_down" -> keyTap(KeyCode.CHANNEL_DOWN)
            "key" -> raw.substringAfter(':').toIntOrNull()?.let { code -> KeyCode.fromCode(code)?.let { keyTap(it) } }
            "tune" -> {
                val number = raw.substringAfter(':')
                val found = _state.value.channels.firstOrNull { it.numberLabel == number || it.name.equals(number, true) }
                if (found != null) tune(found) else {
                    _state.update { it.copy(ghostDigits = number) }
                    commitDigits()
                }
            }
            "launch" -> {
                val name = raw.substringAfter(':')
                _state.value.apps.firstOrNull { it.name.equals(name, true) }?.let { launchApp(it) }
                    ?: _state.update { it.copy(message = "No TV app named $name.") }
            }
            "macro" -> runMacroNamed(raw.substringAfter(':'))
            "volume" -> raw.substringAfter(':').toIntOrNull()?.let { setVolumeLevel(it) }
        }
    }

    fun matter(command: MatterInvoke): String = dev.netcastremote.protocol.MatterClusters.invoke(command, this)

    /** On when it can't be reached, off when it can. Waits out a connect in flight so a cold start doesn't guess wrong. */
    override fun power(turnOn: Boolean?) {
        scope.launch {
            repeat(130) { if (_state.value.link == LinkState.Connecting) delay(100) }
            val online = _state.value.link == LinkState.Online || _state.value.link == LinkState.Demo
            when {
                online && turnOn != true -> keyTap(KeyCode.POWER)
                !online && turnOn != false -> wakeTv()
            }
        }
    }

    override fun volumeTo(level: Int) = setVolumeLevel(level)
    override fun mute() = keyTap(KeyCode.MUTE)
    override fun channelUp() = keyTap(KeyCode.CHANNEL_UP)
    override fun channelDown() = keyTap(KeyCode.CHANNEL_DOWN)
    override fun tune(number: String?, name: String?) {
        val channels = _state.value.channels
        val found = channels.firstOrNull { name != null && (it.name.equals(name, true) || _state.value.nicknames[it.id].equals(name, true)) }
            ?: channels.firstOrNull { number != null && it.numberLabel == number }
        if (found != null) tune(found) else if (number != null) command("tune:$number")
    }

    override fun key(code: Int) { KeyCode.fromCode(code)?.let { keyTap(it) } }
    override fun transport(command: String) {
        val key = when (command.lowercase()) {
            "play" -> KeyCode.PLAY
            "pause" -> KeyCode.PAUSE
            "stop" -> KeyCode.STOP
            "next" -> KeyCode.SKIP_FORWARD
            "previous" -> KeyCode.SKIP_BACK
            else -> null
        }
        key?.let { keyTap(it) }
    }

    override fun launchApp(name: String) = command("launch:$name")
    override fun runMacro(name: String) = runMacroNamed(name)

    fun setRinging(on: Boolean) {
        val settings = _state.value.settings
        if (!settings.muteOnCall && !settings.muteOnVoip) return
        if (on == ringing) return
        ringing = on
        scope.launch(Dispatchers.IO) {
            link?.event("CallStateChanged", mapOf("value" to if (on) "ringon" else "ringoff"))
            log(if (on) "call ringon" else "call ringoff")
        }
    }

    fun duck(on: Boolean) {
        if (!_state.value.settings.duckOnMedia || on == ducked) return
        ducked = on
        scope.launch(Dispatchers.IO) {
            repeat(6) { sendCode(if (on) KeyCode.VOLUME_DOWN.code else KeyCode.VOLUME_UP.code, true) }
        }
    }

    fun flipMute() {
        keyTap(KeyCode.MUTE)
        _state.update { it.copy(undoMute = true, message = "Muted by flip.") }
    }

    fun undoFlip() {
        if (_state.value.undoMute) keyTap(KeyCode.MUTE)
        _state.update { it.copy(undoMute = false, message = "") }
    }

    fun pingSidecar(appName: String) {
        scope.launch(Dispatchers.IO) {
            val auid = link?.appAuid(appName).orEmpty()
            if (auid.isBlank()) {
                _state.update { it.copy(message = "No TV app named $appName. Sideload the sidecar, then try again.") }
                return@launch
            }
            val status = link?.appStatus(auid).orEmpty()
            link?.appToApp(auid, "send", "ping")
            _state.update { it.copy(message = "$appName is $status.") }
        }
    }

    fun exportLog(): String = _state.value.logs.joinToString("\n")

    fun exportJson(passphrase: CharArray): ByteArray {
        val raw = kotlinx.serialization.json.Json.encodeToString(Persisted.serializer(), disk).toByteArray()
        return Crypto.seal(passphrase, raw)
    }

    fun importJson(bytes: ByteArray, passphrase: CharArray) {
        val raw = Crypto.open(passphrase, bytes)
        disk = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(Persisted.serializer(), raw.toString(Charsets.UTF_8))
        _state.update { it.hydrate(disk).copy(message = "Backup restored on this profile.") }
        save()
    }

    fun widgetSnapshot(): String {
        val s = _state.value
        val channel = s.channel
        return buildString {
            append("{\"name\":").append(json(s.active?.name ?: "TV"))
            append(",\"channel\":").append(json(if (channel == null) "" else nowTitle(s)))
            append(",\"status\":").append(json(statusLine(s)))
            append(",\"program\":").append(json(channel?.program.orEmpty()))
            append(",\"number\":").append(json(channel?.numberLabel.orEmpty()))
            append(",\"volume\":").append(s.optimisticVolume ?: s.volume.level)
            append(",\"mute\":").append(s.volume.mute)
            append(",\"online\":").append(s.link == LinkState.Online || s.link == LinkState.Demo)
            append(",\"asleep\":").append(s.link == LinkState.Asleep)
            append(",\"favorites\":[")
            append(s.favorites.take(4).joinToString(",") { id ->
                val ch = s.channels.firstOrNull { it.id == id }
                json(ch?.let { s.nicknames[it.id] ?: it.numberLabel } ?: id)
            })
            append("],\"apps\":[")
            append(s.favoriteApps.take(4).joinToString(",") { id -> json(s.apps.firstOrNull { it.auid == id }?.name ?: id) })
            append("]}")
        }
    }

    fun handleAlarm(kind: String?, title: String?) {
        when (kind) {
            "sleep" -> keyTap(KeyCode.POWER)
            "guide" -> _state.update { it.copy(message = title ?: "A programme is starting.") }
        }
    }

    fun wantsService(): Boolean {
        val s = _state.value
        val live = s.link == LinkState.Online || s.link == LinkState.Demo || s.link == LinkState.Pairing
        if (!live) return false
        if (s.uiVisible) return true
        if (s.settings.backgroundSession || s.settings.volumeRockerBackground || s.settings.matterBridge) return true
        return false
    }

    private var recording: List<MacroStep> = emptyList()
    private val loaded = kotlinx.coroutines.CompletableDeferred<Unit>()

    suspend fun ensureSession() {
        loaded.await()
        if (_state.value.link == LinkState.Online || _state.value.link == LinkState.Demo) return
        // A connect already in flight can take the full 4 s connect plus 8 s read timeout; wait for it rather than racing it.
        repeat(130) {
            val now = _state.value.link
            if (now == LinkState.Online || now == LinkState.Demo || now == LinkState.Pairing) return
            if (now == LinkState.Failed || now == LinkState.WebOs || now == LinkState.TooMany) return
            if (it >= 30 && now != LinkState.Connecting) return@repeat
            delay(100)
        }
        if (_state.value.link == LinkState.Online || _state.value.link == LinkState.Demo || _state.value.link == LinkState.Pairing) return
        if (_state.value.link == LinkState.Connecting) return
        val tv = disk.tvs.find { it.id == disk.activeId } ?: return
        if (tv.pairingKey.isNotBlank()) connect(tv, showCode = false)
    }

    private fun appendStep(step: MacroStep) {
        recording = recording + step
    }

    // Main-safe: start() and ensureSession() call this from the main scope, and hello is blocking socket I/O.
    private suspend fun connect(tv: SavedTv, showCode: Boolean): Unit = withContext(Dispatchers.IO) {
        closeLink()
        val opened = openLink(tv)
        link = opened
        _state.update { it.copy(active = tv, link = LinkState.Connecting, screen = if (showCode) Screen.Discover else it.screen) }
        val status = runCatching {
            if (showCode || tv.pairingKey.isBlank()) opened.requestPairingKey() else opened.hello(tv.pairingKey, ensureEvents())
        }.getOrElse { error ->
            noteHttp("connect", 0)
            log("connect failed: ${error.javaClass.simpleName} ${error.message.orEmpty()}")
            // No snackbar: the remote screen explains the state, and it repeats on every launch while the TV is off.
            _state.update { state -> state.copy(link = LinkState.Failed) }
            return@withContext
        }
        noteHttp(if (showCode || tv.pairingKey.isBlank()) "showKey" else "hello", status)
        when {
            status == 200 && tv.pairingKey.isNotBlank() && !showCode -> finishPair(tv)
            status == 200 -> _state.update {
                it.copy(link = LinkState.Pairing, message = "Type the code shown on the TV. It is grouped in pairs. Spaces are fine.")
            }
            status == 401 && !showCode -> connect(tv.copy(pairingKey = ""), showCode = true)
            status == 503 -> _state.update { it.copy(link = LinkState.TooMany, message = "Too many controllers are already paired to this TV.") }
            else -> _state.update { it.copy(link = LinkState.Failed, message = "Could not reach the TV ($status).") }
        }
        onForeground?.invoke(true)
    }

    private fun finishPair(tv: SavedTv) {
        val saved = tv.copy(lastSeen = System.currentTimeMillis(), pairedAt = tv.pairedAt.takeIf { it > 0 } ?: System.currentTimeMillis())
        upsert(saved)
        disk = disk.copy(activeId = saved.id, settings = disk.settings.copy(demo = false))
        _state.update {
            it.copy(
                link = LinkState.Online,
                active = saved,
                settings = disk.settings,
                screen = Screen.Remote,
                message = "",
            ).hydrateLists(disk, saved.id)
        }
        save()
        refreshAll()
        startPoll()
        onForeground?.invoke(true)
        log("online ${saved.name}")
    }

    private fun refreshAll() {
        scope.launch(Dispatchers.IO) {
            refreshLight()
            loadChannels()
            loadApps()
        }
    }

    private fun refreshLight() {
        val current = runCatching { UdapParse.channel(link?.query("cur_channel").orEmpty()) }.getOrNull()
        val volume = runCatching { UdapParse.volume(link?.query("volume_info").orEmpty()) }.getOrNull()
        val mode = runCatching { UdapParse.uiMode(link?.query("context_ui").orEmpty()) }.getOrNull()
        val three = runCatching { UdapParse.is3d(link?.query("is_3d").orEmpty()) }.getOrNull()
        if (current != null) rememberChannel(current, persistHistory = false)
        _state.update {
            it.copy(
                volume = volume ?: it.volume,
                optimisticVolume = null,
                uiMode = mode ?: it.uiMode,
                threeD = three ?: it.threeD,
            )
        }
        current?.let { pushWear(it) }
        onWidgets?.invoke()
    }

    private fun loadChannels() {
        _state.update { it.copy(loadingChannels = true) }
        val result = runCatching { link?.query("channel_list") ?: error("no session") }
        val reply = result.getOrDefault("")
        val list = runCatching { UdapParse.channelList(reply) }.getOrDefault(emptyList())
        log("channels ${list.size}")
        if (list.isEmpty()) log("channel_list: ${reply.take(240).replace('\n', ' ').ifBlank { "(no reply)" }}")
        if (list.isNotEmpty()) {
            disk = disk.copy(channels = disk.channels + (tvId() to list.map { it.toDto() }))
            _state.update { it.copy(channels = list, loadingChannels = false, channelsFailed = false) }
            save()
        } else {
            _state.update { it.copy(loadingChannels = false, channelsFailed = result.isFailure) }
        }
    }

    private fun loadApps() {
        _state.update { it.copy(loadingApps = true) }
        // A query that returns at all, even empty, counts as an answer; only exceptions mean the fetch failed.
        var answered = false
        fun ask(target: String, params: Map<String, String>): String =
            runCatching { link?.query(target, params) ?: error("no session") }.onSuccess { answered = true }.getOrDefault("")
        // Some sets list nothing under type 1 (all) but do under 2 (Premium) and 3 (My Apps), so ask for each.
        val found = linkedMapOf<String, TvApp>()
        var count = 0
        for (type in listOf("1", "2", "3")) {
            val listed = runCatching { UdapParse.appCount(ask("appnum_get", mapOf("type" to type))) }.getOrDefault(0)
            if (type == "1") count = listed
            // Sets differ on paging: some answer index 0 / number 0 with everything, others want a 1-based page.
            val pages = listOf("0" to "0", "1" to "$listed", "0" to "$listed", "1" to "1024").distinct().filter { (index, number) -> number != "0" || index == "0" }
            var reply = ""
            for ((index, number) in pages) {
                reply = ask("applist_get", mapOf("type" to type, "index" to index, "number" to number))
                val page = runCatching { UdapParse.apps(reply) }.getOrDefault(emptyList())
                page.forEach { app -> found.putIfAbsent(app.auid, app) }
                if (page.isNotEmpty()) break
            }
            log("apps type $type: $listed listed, ${found.size} so far")
            if (found.isEmpty()) log("applist_get type $type: ${reply.take(160).replace('\n', ' ').ifBlank { "(no reply)" }}")
        }
        // A nameless entry (this set lists one under Premium) has no icon and can't be told apart, so skip it.
        val apps = found.values.filter { it.name.isNotBlank() }
        found.values.filter { it.name.isBlank() }.forEach { log("apps: skipped nameless ${it.auid}") }
        count = apps.size
        // A failed fetch keeps whatever list was already showing.
        _state.update { it.copy(apps = if (answered) apps else it.apps, appCount = count, loadingApps = false, appsFailed = !answered) }
        onWidgets?.invoke()
    }

    private suspend fun stepVolume(target: Int) {
        val volume = runCatching { UdapParse.volume(link?.query("volume_info").orEmpty()) }.getOrNull() ?: _state.value.volume
        val ceiling = _state.value.settings.kidsVolumeCap.takeIf { _state.value.settings.kidsMode }
        val steps = VolumeSteps.toward(volume.level, target, volume.min, volume.max, ceiling)
        _state.update { it.copy(optimisticVolume = target.coerceIn(volume.min, ceiling ?: volume.max), volume = volume) }
        steps.forEach {
            yield()
            sendCode(it.code, true)
        }
        val after = runCatching { UdapParse.volume(link?.query("volume_info").orEmpty()) }.getOrNull()
        if (after != null) _state.update { it.copy(volume = after, optimisticVolume = null) }
        onWidgets?.invoke()
    }

    private fun sendCode(code: Int, keepAlive: Boolean) {
        if (!coalescer.submit(code)) return
        var next: Int? = code
        try {
            while (next != null) {
                val sending = next
                val status = link?.command("HandleKeyInput", mapOf("value" to sending.toString()), keepAlive) ?: 0
                noteHttp("key", status)
                if (status == 401) {
                    _state.update { it.copy(link = LinkState.Pairing, message = "The TV asked to pair again.") }
                    break
                }
                if (status == 503) {
                    _state.update { it.copy(link = LinkState.TooMany) }
                    break
                }
                if (status != 200 && status != 501) {
                    _state.update { it.copy(message = "The TV returned $status. Try again.") }
                }
                if (status == 200 && link is DemoTvLink) refreshLight()
                next = coalescer.done()
            }
        } catch (ex: Exception) {
            coalescer.reset()
            _state.update { it.copy(link = if (it.link == LinkState.Online) LinkState.Asleep else it.link, message = "The TV stopped answering.") }
            log("send failed ${ex.message}")
        }
    }

    private fun startPoll() {
        pollJob?.cancel()
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val visible = _state.value.uiVisible
                val reachable = _state.value.diagnostics.reachable
                delay(if (visible && !reachable) 4_000 else 12_000)
                if (_state.value.link != LinkState.Online && _state.value.link != LinkState.Demo) break
                runCatching { refreshLight() }
                val settings = _state.value.settings
                if (settings.previewEnabled && visible && System.currentTimeMillis() - lastPreview > settings.previewSeconds * 1000L) {
                    runCatching { grab() }
                }
            }
        }
    }

    private fun grab() {
        val bytes = link?.queryBytes("screen_image") ?: return
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte()) return
        previewBytes = bytes
        lastPreview = System.currentTimeMillis()
        if (!_state.value.settings.securePreview) {
            platform.cacheDir.resolve("preview.jpg").writeBytes(bytes)
        }
        _state.update { it.copy(previewVersion = it.previewVersion + 1, artSeed = seedOf(bytes)) }
    }

    private fun onIncoming(request: Incoming) {
        val event = UdapParse.event(request.path, request.body.toString(Charsets.UTF_8))
        _state.update { it.copy(diagnostics = it.diagnostics.copy(reachable = true)) }
        when (event) {
            is HostEvent.ChannelChanged -> rememberChannel(event.channel)
            is HostEvent.Keyboard -> {
                val pending = _state.value.pendingShare
                _state.update {
                    it.copy(
                        keyboard = if (event.visible) KeyboardUi(true, event.password, it.keyboard?.text.orEmpty()) else null,
                        screen = if (event.visible) Screen.Remote else it.screen,
                    )
                }
                if (!event.visible) closeKeyboard()
                if (event.visible && pending != null) consumeShare()
            }
            is HostEvent.Text -> if (_state.value.keyboard?.dirty != true) {
                _state.update { it.copy(keyboard = it.keyboard?.copy(text = event.value) ?: KeyboardUi(true, false, event.value)) }
            }
            is HostEvent.ThreeD -> _state.update { it.copy(threeD = event.on) }
            is HostEvent.AppResult -> {
                val message = AppResultText.explain(event.action, event.detail)
                if (event.detail.equals("ERROR", true) || event.detail.equals("BUSY", true)) {
                    val dead = _state.value.deadApps.toMutableMap()
                    _state.value.recentApps.firstOrNull()?.let { dead[it] = event.detail }
                    disk = disk.copy(deadApps = disk.deadApps + (tvId() to dead))
                    _state.update { it.copy(deadApps = dead, message = message) }
                    save()
                } else {
                    _state.update { it.copy(message = message) }
                }
            }
            is HostEvent.AppListChanged -> loadApps()
            is HostEvent.Cursor -> if (event.visible && _state.value.settings.autoLayout) {
                _state.update { it.copy(uiMode = UiMode.TouchPad) }
            }
            HostEvent.Bye -> {
                closeLink()
                _state.update { it.copy(link = LinkState.Asleep, message = "The TV ended the session.") }
                onForeground?.invoke(false)
            }
            is HostEvent.IpChanged -> log("TV IP ${event.oldIp} -> ${event.newIp}")
            is HostEvent.AppMessage -> _state.update { it.copy(message = "TV app: ${event.body.take(180)}") }
            is HostEvent.Ignored -> Unit
        }
    }

    private fun rememberChannel(channel: Channel, persistHistory: Boolean = true) {
        val recent = (listOf(channel.id) + _state.value.recents.filter { it != channel.id }).take(15)
        val zap = (listOf(channel.id) + _state.value.zap.filter { it != channel.id }).take(30)
        if (persistHistory) {
            disk = disk.copy(recents = disk.recents + (tvId() to recent), zap = disk.zap + (tvId() to zap))
            save()
        }
        _state.update { it.copy(channel = channel, recents = recent, zap = zap) }
        pushWear(channel)
        onWidgets?.invoke()
    }

    private fun ensureEvents(): Int {
        events?.let { return it.port }
        val server = MiniHttpServer { incoming ->
            scope.launch { onIncoming(incoming) }
            Outgoing(200)
        }
        server.start()
        events = server
        _state.update { it.copy(diagnostics = it.diagnostics.copy(eventPort = server.port)) }
        return server.port
    }

    private fun openLink(tv: SavedTv): TvLink {
        if (tv.id == "demo") return DemoTvLink()
        val dialect = if (tv.dialect.equals("ROAP", true)) Dialect.ROAP else Dialect.UDAP
        return SocketTvLink(tv.host, tv.port, dialect)
    }

    private fun closeLink() {
        pollJob?.cancel()
        val current = link
        link = null
        scope.launch(Dispatchers.IO) { runCatching { current?.close() } }
        events?.close()
        events = null
        coalescer.reset()
    }

    private fun upsert(tv: SavedTv) {
        disk = disk.copy(tvs = listOf(tv) + disk.tvs.filter { it.id != tv.id }, activeId = tv.id)
    }

    private fun save() {
        saveJob?.cancel()
        val snapshot = disk
        saveJob = scope.launch(Dispatchers.IO) { platform.save(snapshot) }
    }

    private fun tvId(): String = _state.value.active?.id ?: disk.activeId ?: "none"

    private fun noteHttp(action: String, status: Int) {
        _state.update { it.copy(diagnostics = it.diagnostics.copy(http = "$action $status")) }
        log("$action $status")
    }

    private fun log(line: String) {
        val stamp = DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.now().atZone(ZoneId.systemDefault()))
        platform.log(line)
        _state.update { it.copy(logs = (it.logs + "$stamp $line").takeLast(200)) }
    }

    private fun wake() {
        val tv = _state.value.active ?: return
        if (tv.mac.isBlank()) return
        // Callers include the main thread (keys, Matter, AppFunctions); the broadcast must not run there.
        scope.launch(Dispatchers.IO) { sendWake(tv.mac, tv.host) }
    }

    /**
     * The magic packet to every address a sleeping NIC might hear: the all-ones broadcast, the Wi-Fi subnet's own
     * broadcast (some routers drop 255.255.255.255), and the TV directly while the phone still has its ARP entry.
     */
    private fun sendWake(mac: String, host: String) {
        runCatching {
            val packet = WakeOnLan.packet(mac)
            val targets = buildList {
                add(InetAddress.getByName("255.255.255.255"))
                subnetBroadcast()?.let(::add)
                runCatching { InetAddress.getByName(host) }.getOrNull()?.takeIf { it is Inet4Address }?.let(::add)
            }.distinct()
            DatagramSocket().use { socket ->
                socket.broadcast = true
                for (target in targets) for (port in listOf(9, 7)) {
                    runCatching { socket.send(DatagramPacket(packet, packet.size, target, port)) }
                }
            }
            log("wake $mac → ${targets.joinToString { it.hostAddress.orEmpty() }}")
        }.onFailure { log("wake failed: ${it.javaClass.simpleName} ${it.message.orEmpty()}") }
    }

    private fun subnetBroadcast(): InetAddress? = platform.subnetBroadcast()

    private fun reachable(host: String, port: Int): Boolean = runCatching {
        java.net.Socket().use { it.connect(InetSocketAddress(host, port), 1_500) }
    }.isSuccess

    private fun pushWear(channel: Channel) {
        runCatching { platform.pushNowPlaying(_state.value.nicknames[channel.id] ?: channel.name.ifBlank { channel.numberLabel }, channel.program) }
    }

    private fun networkIssue(): LinkState? = platform.networkIssue()

    private fun multicast(): AutoCloseable? = platform.multicastLock()

    private fun searchLan(): List<dev.netcastremote.protocol.SsdpNotice> {
        val socket = MulticastSocket(null)
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(0))
        socket.timeToLive = 2
        socket.broadcast = true
        val group = InetAddress.getByName("239.255.255.250")
        val found = linkedMapOf<String, dev.netcastremote.protocol.SsdpNotice>()
        try {
            Ssdp.searchTargets.forEach { target ->
                val bytes = Ssdp.request(target).toByteArray()
                socket.send(DatagramPacket(bytes, bytes.size, group, 1900))
            }
            socket.soTimeout = 350
            val started = now()
            var deadline = started + 2_600
            var broadcastSent = false
            val buffer = ByteArray(4096)
            while (now() < deadline) {
                // The spec's fallback: if multicast got no answer, ask again by broadcast on 1990 and wait out another MX.
                if (!broadcastSent && found.isEmpty() && now() - started > 1_300) {
                    broadcastSent = true
                    deadline = now() + 2_600
                    val bytes = Ssdp.request(Ssdp.searchTargets.first(), broadcast = true).toByteArray()
                    listOfNotNull(InetAddress.getByName("255.255.255.255"), subnetBroadcast()).distinct().forEach { target ->
                        runCatching { socket.send(DatagramPacket(bytes, bytes.size, target, Ssdp.BROADCAST_PORT)) }
                    }
                    log("SSDP: no multicast reply, sent B-SEARCH")
                }
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val notice = Ssdp.parse(raw) ?: continue
                    if (Ssdp.interesting(notice)) found[notice.location] = notice
                } catch (_: SocketTimeoutException) {
                }
            }
        } finally {
            socket.close()
        }
        return found.values.toList()
    }

    private fun describe(notice: dev.netcastremote.protocol.SsdpNotice): DiscoveredTv? {
        val uri = runCatching { URI(notice.location) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 8080
        val path = (uri.rawPath?.ifBlank { "/" } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
        val body = runCatching { RawHttpClient().use { it.get(host, port, path).text } }.getOrDefault("")
        val desc = UdapParse.description(body, notice.server)
        val id = desc.uuid.ifBlank {
            notice.usn.substringBefore("::").removePrefix("uuid:").ifBlank { "$host:$port" }
        }
        return DiscoveredTv(
            id = id,
            name = desc.friendlyName.ifBlank { desc.modelName.ifBlank { host } },
            host = host,
            port = port,
            model = desc.modelName,
            webOs = desc.webOs,
            uuid = id,
        )
    }

    private fun seedOf(bytes: ByteArray): Int = platform.colorSeed(bytes) ?: 0xFFD0BCFF.toInt()

    private fun curve(value: Float, sensitivity: Float): Int {
        val magnitude = kotlin.math.abs(value)
        if (magnitude < 0.35f) return 0
        val shaped = kotlin.math.sign(value) * Math.pow(magnitude.toDouble(), 1.35).toFloat() * sensitivity
        return shaped.toInt()
    }

    private fun hashPin(salt: String, pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

private fun UiState.hydrate(disk: Persisted): UiState {
    val active = disk.tvs.find { it.id == disk.activeId }
    return hydrateLists(disk, active?.id).copy(
        tvs = disk.tvs,
        active = active,
        settings = disk.settings,
        macros = disk.macros.map { it.toMacro() },
        guide = disk.guide.map { it.toProgramme() },
    )
}

private fun UiState.hydrateLists(disk: Persisted, id: String?): UiState {
    val key = id ?: return this
    return copy(
        channels = disk.channels[key].orEmpty().map(ChannelDto::toChannel),
        favorites = disk.favorites[key].orEmpty(),
        recents = disk.recents[key].orEmpty(),
        zap = disk.zap[key].orEmpty(),
        nicknames = disk.nicknames[key].orEmpty(),
        logos = disk.logos[key].orEmpty(),
        inputNames = disk.inputNames[key].orEmpty(),
        inputMacros = disk.inputMacros[key].orEmpty(),
        favoriteApps = disk.favoriteApps[key].orEmpty(),
        recentApps = disk.recentApps[key].orEmpty(),
        deadApps = disk.deadApps[key].orEmpty(),
        customLayout = disk.layouts[key].orEmpty(),
        kidsChannels = disk.kidsChannels[key].orEmpty(),
    )
}

private fun json(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
