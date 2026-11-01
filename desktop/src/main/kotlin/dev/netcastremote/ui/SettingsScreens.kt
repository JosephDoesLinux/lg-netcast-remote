@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.netcastremote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.DoNotTouch
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.netcastremote.model.AppInfo
import dev.netcastremote.model.Screen
import dev.netcastremote.model.UiState
import dev.netcastremote.session.TvRepository
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun SettingsPane(repo: TvRepository, state: UiState, modifier: Modifier = Modifier) {
    val settings = state.settings
    val tv = state.active
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val version = AppInfo.VERSION
    // Groups flow into two balanced columns on big windows, like Settings on a tablet.
    val columns = if (LocalWindowLayout.current.width == WidthClass.Expanded) 2 else 1
    Masonry(
        columns,
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
    ) {
        if (tv != null) {
            SegmentedGroup("This TV") {
                actionRow(tv.name, Icons.Rounded.Tv, listOf(tv.room, tv.model).filter { it.isNotBlank() }.joinToString(" · ")) { dialog = "rename" }
                actionRow(
                    "Wake-on-LAN",
                    Icons.Rounded.PowerSettingsNew,
                    if (tv.mac.isBlank()) "Experimental. Most NetCast sets drop off the network when off" else "Experimental · ${tv.mac}",
                ) { dialog = "mac" }
                actionRow("Pair again", Icons.Rounded.Restore, "Use this when the TV shows a code again") {
                    repo.rePair()
                    repo.open(Screen.Discover)
                }
                actionRow("Disconnect", Icons.Rounded.LinkOff) { repo.disconnect() }
                actionRow("Forget ${tv.name}", Icons.Rounded.Delete) { dialog = "forget" }
                actionRow("Add another TV", Icons.Rounded.Add) { repo.open(Screen.Discover) }
            }
        }
        SegmentedGroup("Remote") {
            actionRow("Opens on", Icons.Rounded.SettingsRemote, faceOfSetting(settings.layout).label) { dialog = "layout" }
            switchRow("Follow the TV", settings.autoLayout, Icons.Rounded.TouchApp, "Switch to the touchpad when the TV shows a pointer screen") {
                repo.updateSettings { copy(autoLayout = it) }
            }
            infoRow("Keyboard shortcuts", "Arrows and Enter steer, Esc goes back, + and - change volume, Page Up and Down change channel, M mutes, 0 to 9 type a channel.", Icons.Rounded.Keyboard)
        }
        SegmentedGroup("Look") {
            actionRow("Theme", Icons.Rounded.Palette, themeLabel(settings.theme)) { dialog = "theme" }
        }
        SegmentedGroup("Touchpad") {
            sliderRow("Pointer speed", settings.pointerSensitivity, 0.4f..2.2f, label = "${(settings.pointerSensitivity * 100).roundToInt()}%", icon = Icons.Rounded.Speed) {
                repo.updateSettings { copy(pointerSensitivity = it) }
            }
            switchRow("Left-handed", settings.leftHandedPointer, Icons.Rounded.SwapHoriz, supporting = "Flips the horizontal direction") { repo.updateSettings { copy(leftHandedPointer = it) } }
        }
        SegmentedGroup("Kids") {
            switchRow("Kids remote", settings.kidsMode, Icons.Rounded.ChildCare, "Big keys, no power key, a volume ceiling, adult apps hidden") {
                repo.updateSettings { copy(kidsMode = it) }
            }
            sliderRow("Volume ceiling", settings.kidsVolumeCap.toFloat(), 0f..state.volume.max.coerceAtLeast(10).toFloat(), label = "${settings.kidsVolumeCap}", icon = Icons.AutoMirrored.Rounded.VolumeDown) {
                repo.updateSettings { copy(kidsVolumeCap = it.roundToInt()) }
            }
            switchRow("Ask before adult apps", settings.adultLock, Icons.Rounded.Lock) { repo.updateSettings { copy(adultLock = it) } }
            actionRow(if (settings.pinHash.isBlank()) "Set a PIN" else "Change PIN", Icons.Rounded.Security) { dialog = "pin" }
        }
        SegmentedGroup("Screen preview") {
            switchRow("Screen preview", settings.previewEnabled, Icons.Rounded.Visibility, "A still of the TV picture beside the remote, kept on this computer") {
                repo.updateSettings { copy(previewEnabled = it) }
            }
            sliderRow("Preview refresh", settings.previewSeconds.toFloat(), 2f..20f, label = "Every ${settings.previewSeconds} s", icon = Icons.Rounded.Timer) {
                repo.updateSettings { copy(previewSeconds = it.roundToInt().coerceAtLeast(2)) }
            }
        }
        SegmentedGroup("Tools") {
            actionRow("Macros", Icons.Rounded.Movie, "Named sequences of keys, such as Movie night") { repo.open(Screen.Macros) }
            actionRow("Programme guide", Icons.Rounded.CalendarMonth, "Import an XMLTV file") { repo.open(Screen.Guide) }
            actionRow("Screen preview", Icons.Rounded.Screenshot) { repo.open(Screen.Preview) }
            actionRow("Sleep timer", Icons.Rounded.Bedtime, "Turn the TV off later") { dialog = "sleep" }
            actionRow("Diagnostics", Icons.Rounded.BugReport, "Connection details and the log") { repo.open(Screen.Diagnostics) }
        }
        SegmentedGroup("About") {
            actionRow(AppInfo.NAME, Icons.Rounded.Info, "Version $version · open source · how it was made") { repo.open(Screen.About) }
        }
    }
    when (dialog) {
        "rename" -> if (tv != null) RenameTvDialog(tv.name, tv.room, onDismiss = { dialog = null }) { name, room -> repo.rename(name, room) }
        "mac" -> MacDialog(tv?.mac.orEmpty(), onDismiss = { dialog = null }) { repo.setMac(it) }
        "pin" -> TextDialog("PIN for adult apps", "4 to 8 digits", "", onDismiss = { dialog = null }, keyboardType = KeyboardType.NumberPassword) {
            if (it.length in 4..8) repo.setPin(it)
        }
        "forget" -> if (tv != null) {
            AlertDialog(
                onDismissRequest = { dialog = null },
                icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                title = { Text("Forget ${tv.name}?") },
                text = { Text("Its pairing, channel names, favorites, and macros are removed from this computer. The TV will show a new code next time.") },
                confirmButton = {
                    TextButton(onClick = {
                        repo.forget(tv.id)
                        repo.open(Screen.Discover)
                        dialog = null
                    }) { Text("Forget") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
        "theme" -> ChoiceDialog(
            title = "Theme",
            options = listOf("system", "light", "dark").map(::themeLabel),
            selected = themeLabel(settings.theme),
            onDismiss = { dialog = null },
        ) { label -> repo.updateSettings { copy(theme = listOf("system", "light", "dark").first { themeLabel(it) == label }) } }
        "layout" -> ChoiceDialog(
            title = "The remote opens on",
            options = listOf(Face.Buttons, Face.Touchpad, Face.Media, Face.Teletext).map { it.label },
            selected = faceOfSetting(settings.layout).label,
            onDismiss = { dialog = null },
        ) { label -> repo.updateSettings { copy(layout = layoutOf(Face.entries.first { it.label == label })) } }
        "sleep" -> ChoiceDialog(
            title = "Turn the TV off in",
            options = listOf("15 minutes", "30 minutes", "1 hour", "90 minutes", "Cancel the timer"),
            selected = null,
            onDismiss = { dialog = null },
        ) { choice ->
            when (choice) {
                "15 minutes" -> repo.scheduleSleep(15)
                "30 minutes" -> repo.scheduleSleep(30)
                "1 hour" -> repo.scheduleSleep(60)
                "90 minutes" -> repo.scheduleSleep(90)
                else -> repo.cancelSleep()
            }
        }
    }
}

private fun themeLabel(theme: String) = when (theme) {
    "light" -> "Light"
    "dark" -> "Dark"
    else -> "Match the system"
}

private fun faceOfSetting(layout: String) = when (layout) {
    "pointer" -> Face.Touchpad
    "playback" -> Face.Media
    "teletext" -> Face.Teletext
    else -> Face.Buttons
}

private fun GroupScope.sliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    icon: ImageVector,
    onChange: (Float) -> Unit,
) = row { shapes ->
    StaticRow(
        shapes,
        leading = { Icon(icon, contentDescription = null) },
        trailing = { Text(label, style = MaterialTheme.typography.labelLarge) },
        supporting = { SettingSlider(value, range, onChange) },
    ) { Text(title) }
}

@Composable
private fun SettingSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val slider = remember(range) { SliderState(value.coerceIn(range), 0, null, range) }
    LaunchedEffect(value) { if (!slider.isDragging) slider.value = value.coerceIn(range) }
    slider.onValueChange = {
        slider.value = it
        onChange(it)
    }
    Slider(state = slider)
}

@Composable
private fun RenameTvDialog(name: String, room: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var newName by rememberSaveable { mutableStateOf(name) }
    var newRoom by rememberSaveable { mutableStateOf(room) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name and room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(newRoom, { newRoom = it }, label = { Text("Room") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(newName.trim(), newRoom.trim())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .selectable(selected = option == selected, role = Role.RadioButton, onClick = {
                                onPick(option)
                                onDismiss()
                            })
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(option, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun DiagnosticsPane(repo: TvRepository, state: UiState, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            SegmentedGroup("Connection") {
                infoRow("Status", linkCopy(state.link))
                infoRow("Event port", if (state.diagnostics.eventPort > 0) "${state.diagnostics.eventPort}" else "Not listening")
                infoRow(
                    "Events from the TV",
                    if (state.diagnostics.reachable) "Arriving. Changes show up right away." else "Not arriving. The remote checks the TV every few seconds instead.",
                )
                infoRow("Last request", state.diagnostics.http.ifBlank { "None yet" })
            }
        }
        if (state.diagnostics.ssdp.isNotBlank()) {
            item {
                SegmentedGroup("Last discovery reply") {
                    row { shapes ->
                        StaticRow(shapes) {
                            Text(state.diagnostics.ssdp.take(1200), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Log", style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f).padding(start = 16.dp))
                FilledTonalButton(onClick = {
                    copyToClipboard(repo.exportLog())
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Copy")
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.logs.isEmpty()) Text("Nothing logged yet.", style = MaterialTheme.typography.bodySmall)
                    state.logs.asReversed().forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
fun MacrosPane(repo: TvRepository, state: UiState, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("Movie night") }
    val mine = state.macros.filter { it.tvId == state.active?.id || it.tvId.isBlank() }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (state.recordingMacro) "Recording. Press keys on the remote, then come back and save." else "Record keys from the remote, then save them under a name.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = repo::toggleRecord) {
                        Icon(if (state.recordingMacro) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(if (state.recordingMacro) "Stop" else "Record")
                    }
                    Button(onClick = { repo.saveMacro(name) }, enabled = name.isNotBlank(), shapes = ButtonDefaults.shapes()) { Text("Save") }
                }
            }
        }
        if (mine.isEmpty()) {
            StatePanel(Icons.Rounded.Movie, "No macros yet", "A macro replays keys with pauses between them. Try one that switches input and sets the volume.", polygon = MaterialShapes.Clover8Leaf)
        } else {
            SegmentedGroup("Saved") {
                mine.forEach { macro ->
                    actionRow(
                        macro.name,
                        Icons.Rounded.PlayArrow,
                        "${macro.steps.size} steps · tap to run",
                        trailing = { IconButton(onClick = { repo.deleteMacro(macro.id) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete ${macro.name}") } },
                    ) { repo.runMacro(macro) }
                }
            }
        }
    }
}

@Composable
fun PreviewPane(repo: TvRepository, state: UiState, modifier: Modifier = Modifier) {
    val bitmap = remember(state.previewVersion) {
        repo.previewBytes?.let(::decodeImage)?.takeIf { it.width > 8 }
    }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = "The TV picture", modifier = Modifier.fillMaxSize())
            } else {
                Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No picture yet", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Text(
            "Stills stay on this computer and are never uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { repo.grabPreview() }, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Refresh")
        }
    }
}

@Composable
fun GuidePane(repo: TvRepository, state: UiState, modifier: Modifier = Modifier) {
    val pick = { pickTextFile("Import an XMLTV guide")?.let(repo::importGuide) }
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        if (state.guide.isEmpty()) {
            StatePanel(
                Icons.Rounded.CalendarMonth,
                "No guide yet",
                "The TV only knows what's on now. Import an XMLTV file for a full guide and reminders. It stays on this computer.",
                polygon = MaterialShapes.Gem,
            ) {
                Button(onClick = { pick() }, shapes = ButtonDefaults.shapes()) { Text("Import XMLTV") }
            }
        } else {
            Row(Modifier.padding(vertical = 8.dp)) {
                FilledTonalButton(onClick = { pick() }) { Text("Import another file") }
            }
            SegmentedGroup("Up next · tap for a reminder") {
                state.guide.take(100).forEach { programme ->
                    actionRow(
                        programme.title,
                        supporting = "${programme.channel} · ${time.format(Date(programme.startEpochMs))}",
                    ) { repo.remind(programme) }
                }
            }
        }
    }
}

@Composable
internal fun TvDetails(state: UiState) {
    val tv = state.active ?: return
    val paired = if (tv.pairedAt > 0) DateFormat.getDateInstance().format(Date(tv.pairedAt)) else "Not paired"
    val seen = if (tv.lastSeen > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(tv.lastSeen)) else "Never"
    SegmentedGroup("Details") {
        infoRow("Model", tv.model.ifBlank { "Unknown" }, Icons.Rounded.Tune)
        infoRow("Address", "${tv.host}:${tv.port}", Icons.Rounded.Router)
        infoRow("Paired", paired, Icons.Rounded.Link)
        infoRow("Last seen", seen, Icons.Rounded.History)
    }
}
