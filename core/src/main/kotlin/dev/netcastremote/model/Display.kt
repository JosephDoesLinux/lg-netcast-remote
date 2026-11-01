package dev.netcastremote.model

import dev.netcastremote.protocol.Channel

/** NetCast reports a non-tuner source as a huge major number with minor -1 (for example 16368--1). */
fun Channel.isInput(): Boolean {
    val source = inputKey(this)
    return minor < 0 || major >= 16000 || listOf("HDMI", "Component", "Composite", "AV", "RGB").any { source.startsWith(it, ignoreCase = true) }
}

fun inputKey(channel: Channel): String = channel.label.ifBlank { channel.inputName }

/** "HDMI1" reads as "HDMI 1". */
fun prettyInput(raw: String): String = raw.replace(Regex("(?<=[A-Za-z])(?=\\d)"), " ").ifBlank { "External input" }

fun nowTitle(state: UiState): String {
    val channel = state.channel ?: return state.active?.name ?: "TV"
    if (channel.isInput()) {
        val key = inputKey(channel)
        return state.inputNames[key] ?: prettyInput(key)
    }
    val name = state.nicknames[channel.id] ?: channel.name
    return if (name.isBlank()) channel.numberLabel else "$name  ${channel.numberLabel}"
}

fun nowOverline(state: UiState): String {
    val channel = state.channel ?: return if (state.link == LinkState.Demo) "Demo TV" else "Now on the TV"
    if (channel.isInput()) return "Input"
    val source = state.inputNames[channel.label] ?: channel.label.ifBlank { channel.inputName }
    return listOf(source, channel.kind.replaceFirstChar { it.uppercase() }).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Live TV" }
}

fun statusLine(state: UiState): String = if (state.waking) "Turning on…" else when (state.link) {
    LinkState.Online -> "Connected"
    LinkState.Demo -> "Demo · nothing is sent"
    LinkState.Connecting, LinkState.Searching -> "Connecting…"
    LinkState.Asleep -> "Standby"
    LinkState.Pairing -> "Waiting for the code"
    LinkState.Idle, LinkState.Found -> "Not connected"
    else -> "Offline"
}
