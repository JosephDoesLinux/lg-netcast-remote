package dev.netcastremote.model

import dev.netcastremote.protocol.Channel
import dev.netcastremote.protocol.Macro
import dev.netcastremote.protocol.MacroStep
import dev.netcastremote.protocol.Programme
import dev.netcastremote.protocol.TvApp
import dev.netcastremote.protocol.UiMode
import dev.netcastremote.protocol.VolumeInfo
import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val haptics: Boolean = true,
    val hapticLevel: Float = 1f,
    val keyClick: Boolean = false,
    val volumeRockerInApp: Boolean = true,
    val volumeRockerBackground: Boolean = false,
    val pocketGuard: Boolean = true,
    val flipMute: Boolean = false,
    val kidsMode: Boolean = false,
    val kidsVolumeCap: Int = 20,
    val showAdvancedKeys: Boolean = false,
    val autoLayout: Boolean = true,
    val layout: String = "clicker",
    val oneHand: Boolean = false,
    val leftHandedPointer: Boolean = false,
    val pointerSensitivity: Float = 1f,
    val airMouse: Boolean = false,
    val previewEnabled: Boolean = false,
    val previewSeconds: Int = 5,
    val securePreview: Boolean = true,
    val appLock: Boolean = false,
    val adultLock: Boolean = true,
    val muteOnCall: Boolean = true,
    val muteOnVoip: Boolean = false,
    val duckOnMedia: Boolean = false,
    val demo: Boolean = false,
    val backgroundSession: Boolean = false,
    val matterBridge: Boolean = false,
    val tileOpensApp: Boolean = false,
    val pinSalt: String = "",
    val pinHash: String = "",
    val batteryHintShown: Boolean = false,
    val wallpaperColors: Boolean = true,
    /** The welcome tour has been seen. Existing users with a saved TV never see it. */
    val onboarded: Boolean = false,
    /** "system", "light", or "dark". Android follows the system; the desktop app offers the choice. */
    val theme: String = "system",
)

@Serializable
data class SavedTv(
    val id: String,
    val name: String,
    val room: String = "Living room",
    val host: String,
    val port: Int = 8080,
    val pairingKey: String = "",
    val dialect: String = "UDAP",
    val model: String = "",
    val udapVersion: String = "",
    val uuid: String = id,
    val mac: String = "",
    val pairedAt: Long = 0,
    val lastSeen: Long = 0,
)

@Serializable
data class ChannelDto(
    val kind: String = "",
    val major: Int = 0,
    val minor: Int = 0,
    val displayMajor: Int = 0,
    val displayMinor: Int = 0,
    val sourceIndex: Int = 0,
    val physicalNum: Int = 0,
    val name: String = "",
    val program: String = "",
    val audioOnly: Boolean = false,
    val inputName: String = "",
    val label: String = "",
)

@Serializable
data class StepDto(
    val op: String,
    val a: String = "",
    val b: String = "",
    val c: String = "",
    val d: String = "",
    val ms: Long = 0,
)

@Serializable
data class MacroDto(
    val id: String,
    val name: String,
    val tvId: String,
    val steps: List<StepDto> = emptyList(),
)

@Serializable
data class ProgrammeDto(
    val channel: String,
    val title: String,
    val startEpochMs: Long,
    val stopEpochMs: Long,
)

@Serializable
data class Persisted(
    val tvs: List<SavedTv> = emptyList(),
    val activeId: String? = null,
    val settings: Settings = Settings(),
    val macros: List<MacroDto> = emptyList(),
    val channels: Map<String, List<ChannelDto>> = emptyMap(),
    val favorites: Map<String, List<String>> = emptyMap(),
    val recents: Map<String, List<String>> = emptyMap(),
    val zap: Map<String, List<String>> = emptyMap(),
    val nicknames: Map<String, Map<String, String>> = emptyMap(),
    val logos: Map<String, Map<String, String>> = emptyMap(),
    val inputNames: Map<String, Map<String, String>> = emptyMap(),
    val inputMacros: Map<String, Map<String, String>> = emptyMap(),
    val favoriteApps: Map<String, List<String>> = emptyMap(),
    val recentApps: Map<String, List<String>> = emptyMap(),
    val deadApps: Map<String, Map<String, String>> = emptyMap(),
    val layouts: Map<String, List<String>> = emptyMap(),
    val kidsChannels: Map<String, List<String>> = emptyMap(),
    val guide: List<ProgrammeDto> = emptyList(),
)

fun Channel.toDto() = ChannelDto(kind, major, minor, displayMajor, displayMinor, sourceIndex, physicalNum, name, program, audioOnly, inputName, label)

fun ChannelDto.toChannel() = Channel(kind, major, minor, displayMajor, displayMinor, sourceIndex, physicalNum, name, program, audioOnly, inputName, label)

fun Macro.toDto() = MacroDto(id, name, tvId, steps.map { StepDto(it.op, it.a, it.b, it.c, it.d, it.ms) })

fun MacroDto.toMacro() = Macro(id, name, tvId, steps.map { MacroStep(it.op, it.a, it.b, it.c, it.d, it.ms) })

fun Programme.toDto() = ProgrammeDto(channel, title, startEpochMs, stopEpochMs)

fun ProgrammeDto.toProgramme() = Programme(channel, title, startEpochMs, stopEpochMs)

enum class LinkState {
    Idle, Searching, Found, Connecting, Pairing, Online, Asleep, WrongWifi, Vpn, Isolated, TooMany, WebOs, Failed, Demo,
}

enum class Screen { Welcome, Discover, Remote, Channels, Apps, Settings, Diagnostics, Macros, Preview, Guide, About }

data class DiscoveredTv(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val model: String,
    val webOs: Boolean,
    val uuid: String,
)

data class KeyboardUi(
    val visible: Boolean,
    val password: Boolean,
    val text: String,
    val dirty: Boolean = false,
)

data class Diagnostics(
    val ssdp: String = "",
    val http: String = "",
    val eventPort: Int = 0,
    val reachable: Boolean = false,
)

data class UiState(
    val link: LinkState = LinkState.Idle,
    val message: String = "",
    val searching: Boolean = false,
    /** Wake-on-LAN sent; waiting for the TV's network to come up. */
    val waking: Boolean = false,
    val discovered: List<DiscoveredTv> = emptyList(),
    val tvs: List<SavedTv> = emptyList(),
    val active: SavedTv? = null,
    val channel: Channel? = null,
    val channels: List<Channel> = emptyList(),
    val volume: VolumeInfo = VolumeInfo(),
    val optimisticVolume: Int? = null,
    val uiMode: UiMode = UiMode.Unknown,
    val threeD: Boolean = false,
    val apps: List<TvApp> = emptyList(),
    val appCount: Int = 0,
    val keyboard: KeyboardUi? = null,
    val previewVersion: Int = 0,
    val settings: Settings = Settings(),
    val macros: List<Macro> = emptyList(),
    val logs: List<String> = emptyList(),
    val diagnostics: Diagnostics = Diagnostics(),
    val loadingChannels: Boolean = false,
    val loadingApps: Boolean = false,
    /** The last fetch threw, as opposed to the TV answering with an empty list. */
    val channelsFailed: Boolean = false,
    val appsFailed: Boolean = false,
    val ghostDigits: String = "",
    val recordingMacro: Boolean = false,
    val artSeed: Int? = null,
    val pocketNear: Boolean = false,
    val pendingShare: String? = null,
    val guide: List<Programme> = emptyList(),
    val screen: Screen = Screen.Discover,
    val uiVisible: Boolean = false,
    val favorites: List<String> = emptyList(),
    val recents: List<String> = emptyList(),
    val zap: List<String> = emptyList(),
    val nicknames: Map<String, String> = emptyMap(),
    val logos: Map<String, String> = emptyMap(),
    val inputNames: Map<String, String> = emptyMap(),
    val inputMacros: Map<String, String> = emptyMap(),
    val favoriteApps: List<String> = emptyList(),
    val recentApps: List<String> = emptyList(),
    val deadApps: Map<String, String> = emptyMap(),
    val customLayout: List<String> = emptyList(),
    val kidsChannels: List<String> = emptyList(),
    val undoMute: Boolean = false,
)

object Commands {
    const val ACTION = "dev.netcastremote.action.COMMAND"

    fun format(name: String): String = name
}
