@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.netcastremote.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import dev.netcastremote.R
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SliderState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.netcastremote.model.LinkState
import dev.netcastremote.model.Screen
import dev.netcastremote.model.UiState
import dev.netcastremote.model.inputKey
import dev.netcastremote.model.isInput
import dev.netcastremote.model.nowOverline
import dev.netcastremote.model.nowTitle
import dev.netcastremote.model.prettyInput
import dev.netcastremote.protocol.Channel
import dev.netcastremote.protocol.KeyCode
import dev.netcastremote.protocol.UiMode
import dev.netcastremote.protocol.WakeOnLan
import dev.netcastremote.session.TvRepository
import kotlin.math.abs
import kotlin.math.roundToInt

/** The remote's faces. The TV can suggest one; the user's pick wins until the TV changes mode again. */
enum class Face(val label: String) { Buttons("Buttons"), Touchpad("Touchpad"), Media("Media"), Teletext("Teletext"), Kids("Kids") }

private fun faceOf(layout: String) = when (layout) {
    "pointer" -> Face.Touchpad
    "playback" -> Face.Media
    "teletext" -> Face.Teletext
    else -> Face.Buttons
}

internal fun layoutOf(face: Face) = when (face) {
    Face.Touchpad -> "pointer"
    Face.Media -> "playback"
    Face.Teletext -> "teletext"
    else -> "clicker"
}

/** Android 17 gates LAN traffic behind a runtime permission for apps that target it. */
internal val localNetworkPermission: String? =
    if (Build.VERSION.SDK_INT >= 37) Manifest.permission.ACCESS_LOCAL_NETWORK else null

@Composable
fun RemotePane(repo: TvRepository, state: UiState, onPower: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var lanAllowed by remember {
        mutableStateOf(localNetworkPermission == null || ContextCompat.checkSelfPermission(context, localNetworkPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val askLan = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        lanAllowed = granted
        if (granted) repo.reconnect()
    }
    val live = state.link == LinkState.Online || state.link == LinkState.Demo
    Box(modifier.fillMaxSize()) {
        when {
            !lanAllowed && state.link != LinkState.Demo -> StatePanel(
                icon = Icons.Rounded.Router,
                title = "Allow access to your TV",
                body = "Android asks before an app talks to devices on your Wi-Fi. ${context.getString(R.string.app_name)} only talks to the TV you paired.",
                modifier = Modifier.align(Alignment.Center),
            ) {
                Button(onClick = { localNetworkPermission?.let(askLan::launch) }, shapes = ButtonDefaults.shapes()) { Text("Allow") }
            }
            live -> RemoteSurface(repo, state, onPower)
            else -> OfflinePanel(repo, state, Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun OfflinePanel(repo: TvRepository, state: UiState, modifier: Modifier) {
    val name = state.active?.name ?: "your TV"
    val app = LocalContext.current.getString(R.string.app_name)
    val paired = state.active?.pairingKey?.isNotBlank() == true
    val hasMac = state.active?.mac?.isNotBlank() == true
    Column(modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.waking) {
            Busy("Waking $name", "Sent Wake-on-LAN. Waiting up to 40 seconds for its network.") {
                TextButton(onClick = repo::cancelWake) { Text("Cancel") }
            }
        } else when (state.link) {
            LinkState.Connecting, LinkState.Searching -> Busy("Connecting to $name", null)
            LinkState.Asleep, LinkState.Failed -> StatePanel(
                icon = if (state.link == LinkState.Asleep) Icons.Rounded.Bedtime else Icons.Rounded.PowerSettingsNew,
                title = if (state.link == LinkState.Asleep) "$name is off" else "$name isn't answering",
                body = "Turn it on with its own remote. $app connects as soon as it's on." +
                    if (state.link == LinkState.Failed) " If it's already on, check it's on the same Wi-Fi as this phone." else "",
                polygon = MaterialShapes.Sunny,
            ) {
                if (paired) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LoadingIndicator(Modifier.size(32.dp))
                        Text("Watching for the TV", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FilledTonalButton(onClick = repo::reconnect, shapes = ButtonDefaults.shapes()) { Text("Try now") }
                if (hasMac) OutlinedButton(onClick = repo::wakeTv, shapes = ButtonDefaults.shapes()) { Text("Try Wake-on-LAN") }
                TextButton(onClick = { repo.open(Screen.Diagnostics) }) { Text("Diagnostics") }
                TextButton(onClick = { repo.open(Screen.Discover) }) { Text("Choose another TV") }
            }
            LinkState.Pairing -> StatePanel(
                icon = Icons.Rounded.Dialpad,
                title = "Finish pairing",
                body = "$name is showing a code to type on this phone.",
            ) {
                Button(onClick = { repo.open(Screen.Discover) }, shapes = ButtonDefaults.shapes()) { Text("Enter the code") }
            }
            LinkState.Idle -> StatePanel(
                icon = Icons.Rounded.Router,
                title = "Not connected",
                body = if (state.active == null) "Add a TV to start." else "Connect to $name to use the remote.",
            ) {
                if (state.active != null) Button(onClick = repo::reconnect, shapes = ButtonDefaults.shapes()) { Text("Connect") }
                TextButton(onClick = { repo.open(Screen.Discover) }) { Text(if (state.active == null) "Add a TV" else "Choose another TV") }
            }
            else -> StatePanel(
                icon = Icons.Rounded.WifiOff,
                title = "Can't reach $name",
                body = linkCopy(state.link),
                polygon = MaterialShapes.Ghostish,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Button(onClick = repo::reconnect, shapes = ButtonDefaults.shapes()) { Text("Try again") }
                TextButton(onClick = { repo.open(Screen.Diagnostics) }) { Text("Diagnostics") }
                TextButton(onClick = { repo.open(Screen.Discover) }) { Text("Choose another TV") }
            }
        }
    }
}

/** A morphing-shape loading state with a line of explanation, for waits the user started. */
@Composable
internal fun Busy(title: String, body: String?, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ContainedLoadingIndicator(Modifier.size(96.dp))
        Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        actions()
    }
}

@Composable
private fun RemoteSurface(repo: TvRepository, state: UiState, onPower: () -> Unit) {
    val layout = LocalWindowLayout.current
    val auto = when {
        state.settings.kidsMode -> Face.Kids
        state.settings.autoLayout && state.uiMode == UiMode.TouchPad -> Face.Touchpad
        else -> faceOf(state.settings.layout)
    }
    var picked by rememberSaveable { mutableStateOf<Face?>(null) }
    var seenMode by rememberSaveable { mutableStateOf(state.uiMode) }
    LaunchedEffect(state.uiMode) {
        if (state.uiMode != seenMode) {
            seenMode = state.uiMode
            picked = null
        }
    }
    val face = if (state.settings.kidsMode) Face.Kids else picked ?: auto
    var numbers by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    // Where this pane sits in the window, so a fold's hinge (in window pixels) can be placed inside it.
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    val info: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NowCard(repo, state, onPower)
            VolumeBar(repo, state)
            if (face != Face.Kids) FaceSwitch(face) { picked = it }
        }
    }
    val keys: @Composable () -> Unit = {
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = face,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            label = "face",
        ) { shown ->
            when (shown) {
                Face.Touchpad -> TouchpadFace(repo, state)
                Face.Media -> MediaFace(repo, state)
                Face.Teletext -> TeletextFace(repo, state)
                Face.Kids -> KidsFace(repo, state)
                Face.Buttons -> {
                    val custom = if (state.settings.layout == "custom") state.customLayout.mapNotNull(KeyCode::fromName) else emptyList()
                    if (custom.isEmpty()) ButtonsFace(repo, state, onNumbers = { numbers = true }, onMore = { more = true })
                    else KeyGrid(custom, repo, state)
                }
            }
        }
    }
    val preview: @Composable () -> Unit = {
        if (state.settings.previewEnabled) PreviewPane(repo, state, Modifier.fillMaxWidth())
    }

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val posture = layout.posture
        when {
            posture is Posture.Tabletop -> {
                // Half-folded on a table: glanceable info stands up, the keys lie flat under the thumb.
                val top = with(density) { (posture.hinge.top - origin.y).toDp() }.coerceIn(120.dp, maxHeight - 120.dp)
                val hinge = with(density) { posture.hinge.height.toDp() }
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.height(top).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        info()
                        preview()
                    }
                    Spacer(Modifier.height(hinge))
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = 520.dp)) { keys() }
                    }
                }
            }
            posture is Posture.Book || layout.twoPane -> {
                // Side by side. On a book-held foldable the split is the hinge, so nothing sits in the crease.
                val split = (posture as? Posture.Book)?.let { with(density) { (it.hinge.left - origin.x).toDp() } }
                val gap = (posture as? Posture.Book)?.let { with(density) { it.hinge.width.toDp() } } ?: 24.dp
                // Both panes are centered vertically, so the halves balance on tall and wide windows alike.
                val viewport = maxHeight
                Row(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    Column(
                        (if (split != null) Modifier.width((split - 24.dp).coerceAtLeast(200.dp)) else Modifier.weight(1f))
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = viewport)
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        info()
                        preview()
                    }
                    Spacer(Modifier.width(gap))
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        Column(
                            Modifier
                                .widthIn(max = 480.dp)
                                .verticalScroll(rememberScrollState())
                                .heightIn(min = viewport)
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.Center,
                        ) { keys() }
                    }
                }
            }
            else -> {
                val viewport = maxHeight
                // Status and volume sit up top to glance at; the face is pinned to the bottom, in thumb reach.
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = viewport)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    info()
                    Column(Modifier.padding(top = 16.dp + if (state.settings.oneHand) 72.dp else 0.dp)) { keys() }
                }
            }
        }
    }
    if (numbers) NumberSheet(repo, state) { numbers = false }
    if (more) MoreSheet(repo, state) { more = false }
    if (state.keyboard?.visible == true) KeyboardSheet(repo, state)
}

/** A compact remote beside Channels and Apps on big windows, so browsing and steering happen together. */
@Composable
internal fun MiniRemote(repo: TvRepository, state: UiState, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(nowOverline(state), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(nowTitle(state), style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            VolumeBar(repo, state)
            Cluster(repo, state, maxPad = 184.dp)
            KeyRow(listOf(KeyCode.BACK, KeyCode.HOME, KeyCode.EXIT), repo, state)
        }
    }
}

@Composable
private fun NowCard(repo: TvRepository, state: UiState, onPower: () -> Unit) {
    val channel = state.channel
    var naming by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(nowOverline(state), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(nowTitle(state), style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val program = channel?.program.orEmpty()
                if (program.isNotBlank()) Text(program, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val flags = listOfNotNull("3D".takeIf { state.threeD }, "Audio only".takeIf { channel?.audioOnly == true })
                if (flags.isNotEmpty()) Text(flags.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                if (channel != null && channel.isInput() && inputKey(channel).isNotBlank()) {
                    TextButton(onClick = { naming = true }, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.inputNames[inputKey(channel)] == null) "Name this input" else "Rename input")
                    }
                }
            }
            if (!state.settings.kidsMode) Tip("Turn off the TV") {
                FilledIconButton(
                    onClick = onPower,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Power", modifier = Modifier.size(28.dp)) }
            }
        }
    }
    if (naming && channel != null) {
        val key = inputKey(channel)
        TextDialog(
            title = "Name ${prettyInput(key)}",
            label = "Name, such as Apple TV",
            initial = state.inputNames[key].orEmpty(),
            onDismiss = { naming = false },
        ) { repo.setInputName(key, it) }
    }
}

@Composable
private fun VolumeBar(repo: TvRepository, state: UiState) {
    // The drag only moves the thumb. Keys go to the TV once, on release, so a drag cannot stack volume steps.
    var dragged by remember { mutableStateOf<Int?>(null) }
    val ceiling = if (state.settings.kidsMode) state.settings.kidsVolumeCap else state.volume.max
    val target = dragged ?: state.optimisticVolume
    val shown = (target ?: state.volume.level).coerceIn(state.volume.min, ceiling.coerceAtLeast(state.volume.min + 1))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tip(if (state.volume.mute) "Unmute" else "Mute") {
            FilledTonalIconToggleButton(
                checked = state.volume.mute,
                onCheckedChange = { repo.keyTap(KeyCode.MUTE) },
                shapes = IconButtonDefaults.toggleableShapes(),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    if (state.volume.mute) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "Mute",
                )
            }
        }
        val range = state.volume.min.toFloat()..ceiling.coerceAtLeast(state.volume.min + 1).toFloat()
        val slider = remember(range) { SliderState(shown.toFloat(), 0, range) }
        LaunchedEffect(shown) { if (!slider.isDragging) slider.value = shown.toFloat() }
        Slider(
            state = slider,
            onValueChange = {
                slider.value = it
                dragged = it.roundToInt()
            },
            onValueChangeFinished = {
                dragged?.let(repo::setVolumeLevel)
                dragged = null
            },
            modifier = Modifier.weight(1f).semantics { contentDescription = "TV volume" },
        )
        Text(
            if (state.volume.mute && target == null) "Muted" else "$shown",
            modifier = Modifier.widthIn(min = 44.dp),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = if (target != null && target != state.volume.level) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun FaceSwitch(face: Face, onPick: (Face) -> Unit) {
    val faces = listOf(Face.Buttons, Face.Touchpad, Face.Media, Face.Teletext)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        faces.forEachIndexed { index, item ->
            val checked = face == item
            ToggleButton(
                checked = checked,
                onCheckedChange = { onPick(item) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    faces.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                // The picked face grows its icon, so the choice reads without relying on color.
                AnimatedVisibility(checked) {
                    Icon(faceIcon(item), contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(18.dp))
                }
                Text(item.label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun faceIcon(face: Face): ImageVector = when (face) {
    Face.Touchpad -> Icons.Rounded.TouchApp
    Face.Media -> Icons.Rounded.PlayCircle
    Face.Teletext -> Icons.Rounded.Subtitles
    else -> Icons.Rounded.Gamepad
}

private val RockerWidth = 72.dp

@Composable
private fun Cluster(repo: TvRepository, state: UiState, maxPad: Dp = 260.dp) {
    val haptics = state.settings.haptics
    // The D-pad and rockers stay physically left and right in RTL locales.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val pad = (maxWidth - RockerWidth * 2 - 24.dp).coerceIn(120.dp, maxPad)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Rocker(repo, haptics, KeyCode.VOLUME_UP, KeyCode.VOLUME_DOWN, "VOL", Icons.Rounded.Add, Icons.Rounded.Remove, pad)
                DPad(repo, haptics, pad)
                Rocker(repo, haptics, KeyCode.CHANNEL_UP, KeyCode.CHANNEL_DOWN, "CH", Icons.Rounded.KeyboardArrowUp, Icons.Rounded.KeyboardArrowDown, pad)
            }
        }
    }
}

@Composable
private fun ButtonsFace(repo: TvRepository, state: UiState, onNumbers: () -> Unit, onMore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KeyRow(listOf(KeyCode.BACK, KeyCode.HOME, KeyCode.MENU, KeyCode.EXIT), repo, state)
        Cluster(repo, state)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InputSplit(repo, state, Modifier.weight(1.5f))
            LabeledKey(KeyCode.NETCAST, repo, state, Modifier.weight(1f))
            SheetKey(Icons.Rounded.Dialpad, "123", "Number pad", state, Modifier.weight(1f), onNumbers)
            SheetKey(Icons.Rounded.MoreHoriz, "More", "More keys", state, Modifier.weight(1f), onMore)
        }
    }
}

/**
 * Input as a split button: the main half sends the Input key, the arrow lists input shortcuts.
 * UDAP has no command for a specific input, so a shortcut is a macro bound to that input's name.
 */
@Composable
private fun InputSplit(repo: TvRepository, state: UiState, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val haptics = state.settings.haptics
    val view = LocalView.current
    val turn by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chevron")
    // The layout sizes its halves to their content, so give them the slot: a slim arrow, the rest for Input.
    BoxWithConstraints(modifier) {
        val arrow = 40.dp
        val gap = 2.dp
        SplitButtonLayout(
            spacing = gap,
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(
                    onClick = {
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        repo.keyTap(KeyCode.INPUT)
                    },
                    modifier = Modifier.width(maxWidth - arrow - gap).heightIn(min = 64.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { IconLabel(keyIcon(KeyCode.INPUT), shortLabel(KeyCode.INPUT)) }
            },
            trailingButton = {
                SplitButtonDefaults.TonalTrailingButton(
                    checked = open,
                    onCheckedChange = { open = it },
                    modifier = Modifier.width(arrow).heightIn(min = 64.dp).semantics { contentDescription = "Input shortcuts" },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize).graphicsLayer { rotationZ = turn },
                    )
                }
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val bound = state.inputMacros.keys
            bound.forEach { label ->
                DropdownMenuItem(
                    text = { Text(state.inputNames[label] ?: prettyInput(label)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Input, contentDescription = null) },
                    onClick = {
                        open = false
                        repo.playInput(label)
                    },
                )
            }
            if (bound.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Open the TV's input list") },
                leadingIcon = { Icon(Icons.Rounded.Tv, contentDescription = null) },
                onClick = {
                    open = false
                    repo.keyTap(KeyCode.INPUT)
                },
            )
            DropdownMenuItem(
                text = { Text(if (bound.isEmpty()) "Make an input shortcut" else "Edit input shortcuts") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = {
                    open = false
                    repo.open(Screen.Macros)
                },
            )
        }
    }
}

@Composable
private fun KidsFace(repo: TvRepository, state: UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Cluster(repo, state)
        KeyRow(listOf(KeyCode.BACK), repo, state)
        KeyGrid(KeyCode.digits, repo, state, height = 72.dp)
    }
}

@Composable
private fun MediaFace(repo: TvRepository, state: UiState) {
    val haptics = state.settings.haptics
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteKey(KeyCode.REWIND, repo, haptics, Modifier.weight(1f).height(88.dp)) { KeyFace(KeyCode.REWIND, big = true) }
            listOf(KeyCode.PLAY, KeyCode.PAUSE).forEach { key ->
                RemoteKey(
                    key,
                    repo,
                    haptics,
                    Modifier.weight(1.3f).height(104.dp),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                ) { KeyFace(key, big = true) }
            }
            RemoteKey(KeyCode.FAST_FORWARD, repo, haptics, Modifier.weight(1f).height(88.dp)) { KeyFace(KeyCode.FAST_FORWARD, big = true) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(KeyCode.SKIP_BACK, KeyCode.STOP, KeyCode.SKIP_FORWARD).forEach { key ->
                RemoteKey(key, repo, haptics, Modifier.weight(1f).height(64.dp)) { KeyFace(key) }
            }
            RemoteKey(
                KeyCode.RECORD,
                repo,
                haptics,
                Modifier.weight(1f).height(64.dp),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            ) { KeyFace(KeyCode.RECORD) }
        }
        KeyRow(listOf(KeyCode.RECORDING_LIST, KeyCode.INFO, KeyCode.REPEAT, KeyCode.BACK), repo, state)
    }
}

@Composable
private fun TeletextFace(repo: TvRepository, state: UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ColorRow(repo, state.settings.haptics, height = 56.dp)
        KeyGrid(listOf(KeyCode.TELETEXT, KeyCode.TEXT_OPTION, KeyCode.INFO, KeyCode.MARK, KeyCode.BACK, KeyCode.EXIT), repo, state, height = 52.dp)
        KeyGrid(KeyCode.digits, repo, state)
    }
}

/** One-finger drag moves the TV pointer, a tap clicks, two fingers scroll. */
@Composable
private fun TouchpadFace(repo: TvRepository, state: UiState) {
    val view = LocalView.current
    var dragging by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(Unit) {
        repo.pointer(true)
        onDispose {
            if (dragging) repo.drag(false)
            repo.pointer(false)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.05f)
                .clip(RoundedCornerShape(36.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .semantics {
                    contentDescription = "Touchpad"
                    onClick(label = "Click") { repo.pointerClick(); true }
                }
                .pointerInput(state.settings.pointerSensitivity, state.settings.leftHandedPointer) {
                    val notch = 36.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var fingers = 1
                        var travel = 0f
                        var scroll = 0f
                        var lastTime = down.uptimeMillis
                        do {
                            val event = awaitPointerEvent()
                            lastTime = event.changes.maxOf { it.uptimeMillis }
                            val pressed = event.changes.filter { it.pressed }
                            fingers = maxOf(fingers, pressed.size)
                            if (pressed.size >= 2) {
                                // Natural scrolling: fingers moving up scroll the page down.
                                scroll += pressed.map { it.positionChange().y }.average().toFloat()
                                while (abs(scroll) >= notch) {
                                    repo.pointerWheel(up = scroll > 0)
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    scroll -= if (scroll > 0) notch else -notch
                                }
                            } else if (pressed.size == 1 && fingers == 1) {
                                val delta = pressed[0].positionChange()
                                travel += delta.getDistance()
                                if (delta != Offset.Zero) repo.pointerMove(delta.x, delta.y)
                            }
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        val held = lastTime - down.uptimeMillis
                        if (fingers == 1 && travel < viewConfiguration.touchSlop && held < viewConfiguration.longPressTimeoutMillis) {
                            if (state.settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            repo.pointerClick()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (dragging) "Dragging" else "Touchpad", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Drag to move · Tap to click · Two fingers to scroll",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledKey(KeyCode.BACK, repo, state, Modifier.weight(1f))
            LabeledKey(KeyCode.HOME, repo, state, Modifier.weight(1f))
            ToggleButton(
                checked = dragging,
                onCheckedChange = {
                    dragging = it
                    repo.drag(it)
                },
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
            ) { Text("Drag", maxLines = 1) }
            LabeledKey(KeyCode.EXIT, repo, state, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DPad(repo: TvRepository, haptics: Boolean, size: Dp) {
    val inset = size * 0.05f
    val long = size * 0.34f
    val short = size * 0.24f
    val clear = Color.Transparent
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        RemoteKey(KeyCode.UP, repo, haptics, Modifier.align(Alignment.TopCenter).padding(top = inset).size(long, short), container = clear) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(32.dp))
        }
        RemoteKey(KeyCode.DOWN, repo, haptics, Modifier.align(Alignment.BottomCenter).padding(bottom = inset).size(long, short), container = clear) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(32.dp))
        }
        RemoteKey(KeyCode.LEFT, repo, haptics, Modifier.align(Alignment.CenterStart).padding(start = inset).size(short, long), container = clear) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(32.dp))
        }
        RemoteKey(KeyCode.RIGHT, repo, haptics, Modifier.align(Alignment.CenterEnd).padding(end = inset).size(short, long), container = clear) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(32.dp))
        }
        RemoteKey(
            KeyCode.OK,
            repo,
            haptics,
            Modifier.align(Alignment.Center).size(size * 0.38f),
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
            pressedContainer = MaterialTheme.colorScheme.tertiary,
            pressedContent = MaterialTheme.colorScheme.onTertiary,
        ) { Text("OK", style = MaterialTheme.typography.titleLargeEmphasized) }
    }
}

@Composable
private fun Rocker(
    repo: TvRepository,
    haptics: Boolean,
    up: KeyCode,
    down: KeyCode,
    caption: String,
    upIcon: ImageVector,
    downIcon: ImageVector,
    height: Dp,
) {
    Column(
        Modifier
            .width(RockerWidth)
            .height(height)
            .clip(RoundedCornerShape(RockerWidth / 2))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RemoteKey(up, repo, haptics, Modifier.fillMaxWidth().weight(1f), container = Color.Transparent) { Icon(upIcon, contentDescription = null) }
        Text(caption, style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RemoteKey(down, repo, haptics, Modifier.fillMaxWidth().weight(1f), container = Color.Transparent) { Icon(downIcon, contentDescription = null) }
    }
}

@Composable
private fun KeyRow(keys: List<KeyCode>, repo: TvRepository, state: UiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { key -> LabeledKey(key, repo, state, Modifier.weight(1f)) }
    }
}

/** Tonal key with an icon over a short label, for the keys whose icon alone is ambiguous. */
@Composable
private fun LabeledKey(key: KeyCode, repo: TvRepository, state: UiState, modifier: Modifier = Modifier) {
    RemoteKey(
        key,
        repo,
        state.settings.haptics,
        modifier.heightIn(min = 64.dp),
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
    ) { IconLabel(keyIcon(key), shortLabel(key)) }
}

/** Looks like a key but opens a sheet on the phone; nothing is sent to the TV. */
@Composable
private fun SheetKey(icon: ImageVector, label: String, description: String, state: UiState, modifier: Modifier, onClick: () -> Unit) {
    KeySurface(
        description = description,
        haptic = HapticFeedbackConstants.VIRTUAL_KEY,
        haptics = state.settings.haptics,
        modifier = modifier.heightIn(min = 64.dp),
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
        onTap = onClick,
    ) { IconLabel(icon, label) }
}

@Composable
private fun IconLabel(icon: ImageVector?, label: String) {
    Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        icon?.let { Icon(it, contentDescription = null) }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ColorRow(repo: TvRepository, haptics: Boolean, height: Dp = 48.dp) {
    // A letter on each key as well as its color, so the keys are not told apart by color alone.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyCode.colorOrder.forEach { key ->
            val (fill, ink, letter) = when (key) {
                KeyCode.RED -> Triple(Color(0xFFB3261E), Color.White, "R")
                KeyCode.GREEN -> Triple(Color(0xFF1B5E20), Color.White, "G")
                KeyCode.YELLOW -> Triple(Color(0xFFF9A825), Color.Black, "Y")
                else -> Triple(Color(0xFF1565C0), Color.White, "B")
            }
            RemoteKey(key, repo, haptics, Modifier.weight(1f).heightIn(min = height), container = fill, content = ink, pressedContainer = fill, pressedContent = ink) {
                Text(letter, style = MaterialTheme.typography.titleMediumEmphasized)
            }
        }
    }
}

@Composable
private fun KeyGrid(keys: List<KeyCode>, repo: TvRepository, state: UiState, height: Dp = 56.dp, columns: Int = 3) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.distinct().chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        val slot = favoriteIndex(key)
                        RemoteKey(
                            key,
                            repo,
                            state.settings.haptics,
                            Modifier.weight(1f).heightIn(min = height),
                            onLong = slot?.let { index -> { repo.favoriteSlot(index) } },
                        ) { KeyFace(key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberSheet(repo: TvRepository, state: UiState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = fullSheet()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                state.ghostDigits.ifEmpty { "Channel number" },
                modifier = Modifier.fillMaxWidth(),
                style = if (state.ghostDigits.isEmpty()) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displayMediumEmphasized,
                color = if (state.ghostDigits.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                "Hold a number to jump to that favorite.",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            KeyGrid(KeyCode.digits, repo, state, height = 64.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = repo::backspaceDigits, enabled = state.ghostDigits.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Text("Delete")
                }
                Button(
                    onClick = {
                        repo.commitDigits()
                        onDismiss()
                    },
                    enabled = state.ghostDigits.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(2f).heightIn(min = 56.dp),
                ) { Text("Go") }
            }
        }
    }
}

private val moreKeys = listOf(
    KeyCode.FAVORITE, KeyCode.FLASHBACK, KeyCode.INFO,
    KeyCode.EPG, KeyCode.PROGRAM_LIST, KeyCode.SUBTITLE,
    KeyCode.AUDIO_DESCRIPTION, KeyCode.RESERVATIONS, KeyCode.MY_APPS,
    KeyCode.LIVE_TV, KeyCode.SIMPLINK, KeyCode.QUICK_MENU,
    KeyCode.ASPECT, KeyCode.AV_MODE, KeyCode.ENERGY,
    KeyCode.THREE_D, KeyCode.THREE_D_LR, KeyCode.MARK,
    KeyCode.PIP, KeyCode.PIP_UP, KeyCode.PIP_DOWN,
    KeyCode.PIP_SWAP, KeyCode.TELETEXT, KeyCode.TEXT_OPTION,
)

@Composable
private fun MoreSheet(repo: TvRepository, state: UiState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("More keys", style = MaterialTheme.typography.titleLargeEmphasized)
            ColorRow(repo, state.settings.haptics)
            KeyGrid(moreKeys, repo, state, height = 52.dp)
        }
    }
}

/** Shown while the TV has a text field focused. Every edit is sent as the whole string. */
@Composable
private fun KeyboardSheet(repo: TvRepository, state: UiState) {
    val keyboard = state.keyboard ?: return
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@rememberLauncherForActivityResult
        repo.editText("Editing", keyboard.text + spoken)
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
        }
    }
    ModalBottomSheet(onDismissRequest = repo::closeKeyboard, sheetState = fullSheet()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (keyboard.password) "Password on the TV" else "Typing on the TV", style = MaterialTheme.typography.titleLargeEmphasized)
            OutlinedTextField(
                value = keyboard.text,
                onValueChange = { repo.editText("Editing", it) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                visualTransformation = if (keyboard.password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (keyboard.password) KeyboardType.Password else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!keyboard.password) {
                    FilledTonalButton(onClick = { mic.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Dictate")
                    }
                }
                FilledTonalButton(onClick = {
                    val pasted = context.getSystemService(android.content.ClipboardManager::class.java)
                        ?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (pasted.isNotEmpty()) repo.editText("Editing", keyboard.text + pasted)
                }) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Paste")
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = repo::closeKeyboard, shapes = ButtonDefaults.shapes()) { Text("Done") }
            }
            // Inside the sheet: its content is a separate window, so the field is attached by the time this runs.
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
    }
}

/** A sheet that opens fully, for content that should not stop half way. */
@Composable
internal fun fullSheet(): SheetState =
    rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

@Composable
internal fun TextDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, keyboardType: KeyboardType = KeyboardType.Text, onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value,
                { value = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(value.trim())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks for the MAC that Wake-on-LAN needs. Android hides other devices' MACs from apps, so it's copied from the TV once. */
@Composable
internal fun MacDialog(initial: String, onDismiss: () -> Unit, confirm: String = "Save", onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val mac = WakeOnLan.normalize(value)
    val invalid = value.isNotBlank() && mac == null
    val removing = value.isBlank() && initial.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) },
        title = { Text("Turn on from this phone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Waking the TV over Wi-Fi needs its MAC address. Android doesn't let apps read it from the network, " +
                        "so copy it from the TV once: Settings › Support › Product/Service Info, or your router's list of devices.",
                )
                OutlinedTextField(
                    value,
                    { value = it },
                    label = { Text("MAC address") },
                    placeholder = { Text("A1:B2:C3:D4:E5:F6") },
                    singleLine = true,
                    isError = invalid,
                    supportingText = { if (invalid) Text("Six pairs of 0–9 and A–F") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = mac != null || removing, onClick = {
                onSave(mac.orEmpty())
                onDismiss()
            }) { Text(if (removing) "Remove" else confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * One remote key. Digits and keys given [onTap] fire on tap; the rest are held,
 * so arrows, volume, and channel repeat in the repository until release.
 */
@Composable
internal fun RemoteKey(
    key: KeyCode,
    repo: TvRepository,
    haptics: Boolean,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
    pressedContainer: Color = MaterialTheme.colorScheme.primary,
    pressedContent: Color = MaterialTheme.colorScheme.onPrimary,
    description: String = key.label,
    onTap: (() -> Unit)? = null,
    onLong: (() -> Unit)? = null,
    face: @Composable () -> Unit,
) {
    val typed = onTap != null || key.family == KeyCode.Family.DIGIT
    val tap = onTap ?: { if (key.family == KeyCode.Family.DIGIT) repo.digit(key) else repo.keyTap(key) }
    KeySurface(
        description = description,
        haptic = hapticFor(key),
        haptics = haptics,
        modifier = modifier,
        container = container,
        content = content,
        pressedContainer = pressedContainer,
        pressedContent = pressedContent,
        onHold = if (typed) null else { down -> if (down) repo.keyDown(key) else repo.keyUp() },
        onTap = tap,
        onLong = onLong,
        face = face,
    )
}

/**
 * The pressable surface behind every key. Pressing morphs the corners with the
 * expressive spring and lights the key; [onHold] gets press and release for keys
 * that repeat, otherwise [onTap] fires on tap. TalkBack gets a plain click either way.
 */
@Composable
internal fun KeySurface(
    description: String,
    haptic: Int,
    haptics: Boolean,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
    pressedContainer: Color = MaterialTheme.colorScheme.primary,
    pressedContent: Color = MaterialTheme.colorScheme.onPrimary,
    onHold: ((Boolean) -> Unit)? = null,
    onTap: () -> Unit,
    onLong: (() -> Unit)? = null,
    face: @Composable () -> Unit,
) {
    val view = LocalView.current
    val click = LocalKeyClick.current
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    val long by rememberUpdatedState(onLong)
    var pressed by remember { mutableStateOf(false) }
    val corner by animateFloatAsState(if (pressed) 24f else 50f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "corner percent")
    val fill by animateColorAsState(if (pressed) pressedContainer else container, MaterialTheme.motionScheme.fastEffectsSpec(), label = "fill")
    val ink by animateColorAsState(if (pressed) pressedContent else content, MaterialTheme.motionScheme.fastEffectsSpec(), label = "ink")
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(percent = corner.roundToInt().coerceIn(0, 100)))
            .background(fill)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { tap(); true }
                if (onLong != null) onLongClick { long?.invoke(); true }
            }
            .pointerInput(onHold != null, haptics, click, onLong != null) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        if (haptics) view.performHapticFeedback(haptic)
                        if (click) view.playSoundEffect(SoundEffectConstants.CLICK)
                        hold?.invoke(true)
                        tryAwaitRelease()
                        hold?.invoke(false)
                        pressed = false
                    },
                    onTap = if (onHold == null) { _ -> tap() } else null,
                    onLongPress = if (onLong != null) { _ -> long?.invoke() } else null,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) { face() }
    }
}

/** Long-pressing 1 through 9, then 0, tunes the first ten favorites. */
private fun favoriteIndex(key: KeyCode): Int? =
    key.label.toIntOrNull()?.takeIf { key.family == KeyCode.Family.DIGIT }?.let { (it + 9) % 10 }

@Composable
private fun KeyFace(key: KeyCode, big: Boolean = false) {
    val icon = keyIcon(key)
    when {
        icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(if (big) 36.dp else 24.dp))
        key.family == KeyCode.Family.DIGIT -> Text(if (key == KeyCode.DASH) "–" else key.label, style = MaterialTheme.typography.titleLargeEmphasized)
        else -> {
            val ink = LocalContentColor.current
            BasicText(
                shortLabel(key),
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center),
                maxLines = 2,
                color = { ink },
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
            )
        }
    }
}

internal fun keyIcon(key: KeyCode): ImageVector? = when (key) {
    KeyCode.POWER -> Icons.Rounded.PowerSettingsNew
    KeyCode.UP -> Icons.Rounded.KeyboardArrowUp
    KeyCode.DOWN -> Icons.Rounded.KeyboardArrowDown
    KeyCode.LEFT -> Icons.AutoMirrored.Rounded.KeyboardArrowLeft
    KeyCode.RIGHT -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
    KeyCode.BACK -> Icons.AutoMirrored.Rounded.ArrowBack
    KeyCode.HOME -> Icons.Rounded.Home
    KeyCode.MENU -> Icons.Rounded.Settings
    KeyCode.EXIT -> Icons.Rounded.Close
    KeyCode.MUTE -> Icons.AutoMirrored.Rounded.VolumeOff
    KeyCode.FAVORITE -> Icons.Rounded.Star
    KeyCode.FLASHBACK -> Icons.Rounded.History
    KeyCode.NETCAST -> Icons.Rounded.SmartDisplay
    KeyCode.INPUT -> Icons.AutoMirrored.Rounded.Input
    KeyCode.PLAY -> Icons.Rounded.PlayArrow
    KeyCode.PAUSE -> Icons.Rounded.Pause
    KeyCode.STOP -> Icons.Rounded.Stop
    KeyCode.REWIND -> Icons.Rounded.FastRewind
    KeyCode.FAST_FORWARD -> Icons.Rounded.FastForward
    KeyCode.SKIP_BACK -> Icons.Rounded.SkipPrevious
    KeyCode.SKIP_FORWARD -> Icons.Rounded.SkipNext
    KeyCode.RECORD -> Icons.Rounded.FiberManualRecord
    KeyCode.RECORDING_LIST -> Icons.Rounded.VideoLibrary
    KeyCode.REPEAT -> Icons.Rounded.Repeat
    KeyCode.INFO -> Icons.Rounded.Info
    else -> null
}

internal fun shortLabel(key: KeyCode): String = when (key) {
    KeyCode.FAVORITE -> "Favorite"
    KeyCode.FLASHBACK -> "Previous"
    KeyCode.INPUT -> "Input"
    KeyCode.PIP -> "PIP"
    KeyCode.RECORDING_LIST -> "Recordings"
    KeyCode.INFO -> "Info"
    else -> key.label.substringBefore(',')
}

private fun hapticFor(key: KeyCode): Int = when (key.family) {
    KeyCode.Family.POWER -> HapticFeedbackConstants.LONG_PRESS
    KeyCode.Family.VOLUME, KeyCode.Family.CHANNEL -> HapticFeedbackConstants.CLOCK_TICK
    KeyCode.Family.DIGIT -> HapticFeedbackConstants.KEYBOARD_TAP
    else -> HapticFeedbackConstants.VIRTUAL_KEY
}
