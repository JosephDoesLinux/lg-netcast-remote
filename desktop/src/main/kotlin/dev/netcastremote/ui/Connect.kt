@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.netcastremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.netcastremote.model.LinkState
import dev.netcastremote.model.UiState
import dev.netcastremote.session.TvRepository

@Composable
fun DiscoverPane(repo: TvRepository, state: UiState, modifier: Modifier = Modifier) {
    var manual by rememberSaveable { mutableStateOf(false) }
    val problem = state.link in setOf(
        LinkState.WrongWifi, LinkState.Vpn, LinkState.Isolated, LinkState.TooMany, LinkState.WebOs, LinkState.Failed, LinkState.Asleep,
    )
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShapeBadge(Icons.Rounded.Tv, size = 120.dp)
                Text("Connect your LG TV", style = MaterialTheme.typography.headlineLargeEmphasized, textAlign = TextAlign.Center)
                Text(
                    "Use the same Wi-Fi as a 2012 or 2013 NetCast TV, and make sure it's on.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (state.link == LinkState.Pairing) {
            item { PairingPanel(repo, Modifier.widthIn(max = 520.dp)) }
        }
        item {
            Button(
                onClick = { repo.search() },
                enabled = !state.searching,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(min = 56.dp),
            ) {
                if (state.searching) {
                    LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                }
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(if (state.searching) "Searching your network…" else "Search for TVs", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (problem && !state.searching) {
            item {
                Surface(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
                        Text(linkCopy(state.link), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                SegmentedGroup("Found on this network") {
                    state.discovered.forEach { card ->
                        actionRow(
                            title = card.name,
                            icon = Icons.Rounded.Tv,
                            supporting = if (card.webOs) "webOS · uses a different protocol" else listOf(card.model, card.host).filter { it.isNotBlank() }.joinToString(" · "),
                        ) { repo.choose(card) }
                    }
                }
                SegmentedGroup("Your TVs") {
                    state.tvs.filter { it.id != "demo" }.forEach { tv ->
                        val connected = tv.id == state.active?.id && state.link == LinkState.Online
                        actionRow(
                            title = tv.name,
                            icon = Icons.Rounded.Tv,
                            supporting = listOf(tv.room, tv.host).filter { it.isNotBlank() }.joinToString(" · "),
                            trailing = if (connected) ({ Icon(Icons.Rounded.Check, contentDescription = "Connected") }) else null,
                        ) { repo.activate(tv.id) }
                    }
                }
                SegmentedGroup("Other ways to connect") {
                    actionRow("Enter the TV's IP address", Icons.Rounded.Router, "For networks that hide devices from search") { manual = true }
                    actionRow("Try the demo remote", Icons.Rounded.PlayCircle, "No TV needed. Nothing is sent anywhere.") { repo.useDemo() }
                }
            }
        }
    }
    if (manual) ManualDialog(repo) { manual = false }
}

/** Six boxes in three pairs, matching how the TV shows the code. Submits on the sixth digit. */
@Composable
private fun PairingPanel(repo: TvRepository, modifier: Modifier = Modifier) {
    var code by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Enter the code on your TV", style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center)
            Text(
                "It's on screen for about a minute.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
            BasicTextField(
                value = code,
                onValueChange = { typed ->
                    code = typed.filter(Char::isDigit).take(6)
                    if (code.length == 6) repo.submitCode(code)
                },
                modifier = Modifier.focusRequester(focus).semantics { contentDescription = "Pairing code, 6 digits" },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                singleLine = true,
                decorationBox = { _ ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(6) { index ->
                            val current = index == code.length
                            Box(
                                Modifier
                                    .size(width = 44.dp, height = 56.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                                    .border(
                                        width = if (current) 2.dp else 1.dp,
                                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(14.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(code.getOrNull(index)?.toString().orEmpty(), style = MaterialTheme.typography.headlineSmallEmphasized)
                            }
                            if (index % 2 == 1 && index < 5) Spacer(Modifier.width(8.dp))
                        }
                    }
                },
            )
            Row {
                TextButton(onClick = { code = "" }) { Text("Clear") }
                TextButton(onClick = {
                    code = ""
                    repo.rePair()
                }) { Text("Show a new code") }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun ManualDialog(repo: TvRepository, onDismiss: () -> Unit) {
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("8080") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Router, contentDescription = null) },
        title = { Text("Connect by address") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Find the address in the TV's network settings.")
                OutlinedTextField(
                    host,
                    { host = it.trim() },
                    label = { Text("IP address") },
                    placeholder = { Text("192.168.1.20") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    port,
                    { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    repo.manual(host, port.toIntOrNull() ?: 8080)
                    onDismiss()
                },
                enabled = host.isNotBlank(),
            ) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun linkCopy(link: LinkState): String = when (link) {
    LinkState.Idle -> "Search for the TV, or enter its address."
    LinkState.Searching -> "Looking for a NetCast TV."
    LinkState.Found -> "Choose a television."
    LinkState.Connecting -> "Connecting…"
    LinkState.Pairing -> "Enter the code the TV is showing."
    LinkState.Online -> "Connected."
    LinkState.Asleep -> "The TV ended the session, usually because it was turned off."
    LinkState.WrongWifi -> "This phone isn't on Wi-Fi."
    LinkState.Vpn -> "A VPN is blocking discovery. Turn it off and search again."
    LinkState.Isolated -> "Nothing answered. Guest Wi-Fi and access-point isolation hide the TV from the phone. Use the same network, and make sure the TV is on."
    LinkState.TooMany -> "The TV already has as many controllers as it allows."
    LinkState.WebOs -> "This is a webOS TV. This app speaks the 2012 NetCast protocol and stops here."
    LinkState.Failed -> "It may be off, or on a different Wi-Fi network."
    LinkState.Demo -> "Demo TV. Nothing is sent to a real set."
}
