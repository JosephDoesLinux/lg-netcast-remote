@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.netcastremote.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.netcastremote.R
import dev.netcastremote.integrate.Shortcuts
import dev.netcastremote.model.LinkState
import dev.netcastremote.model.Screen
import dev.netcastremote.model.UiState
import dev.netcastremote.model.prettyInput
import dev.netcastremote.model.statusLine
import dev.netcastremote.protocol.KeyCode
import dev.netcastremote.session.TvRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Whether keys play the system click. Provided once here so every key can read it. */
internal val LocalKeyClick = staticCompositionLocalOf { false }

private data class Destination(val screen: Screen, val label: String, val selected: ImageVector, val unselected: ImageVector)

private val destinations = listOf(
    Destination(Screen.Remote, "Remote", Icons.Rounded.SettingsRemote, Icons.Outlined.SettingsRemote),
    Destination(Screen.Channels, "Channels", Icons.Rounded.LiveTv, Icons.Outlined.LiveTv),
    Destination(Screen.Apps, "Apps", Icons.Rounded.Apps, Icons.Outlined.Apps),
)

/** Phones reach Settings from the top bar; the rail lists it with the other destinations. */
private val settingsDestination = Destination(Screen.Settings, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)

private val topLevel = setOf(Screen.Remote, Screen.Channels, Screen.Apps)
private val settingsScreens = setOf(Screen.Settings, Screen.Diagnostics, Screen.Macros, Screen.Preview, Screen.Guide, Screen.About)

/** First-run screens own the whole window: no rail, no bars. */
private val setup = setOf(Screen.Welcome, Screen.Discover)

private val androidFeatures = listOf(
    Feature(Icons.Rounded.Gamepad, "Every key, and then some", "D-pad, numbers, color keys, media keys, and teletext: everything on the original remote."),
    Feature(Icons.Rounded.TouchApp, "A touchpad for the pointer", "Drive the Magic Remote pointer, and scroll web pages with two fingers."),
    Feature(Icons.Rounded.Keyboard, "Type on the TV", "When the TV asks for text, your phone's keyboard pops up."),
    Feature(Icons.Rounded.LiveTv, "Channels and apps", "Search, favorite, and rename channels. Open TV apps by their icons."),
    Feature(Icons.AutoMirrored.Rounded.VolumeUp, "Volume buttons", "Your phone's volume buttons change the TV, with Android's own volume slider."),
    Feature(Icons.Rounded.Widgets, "Widgets and Quick Settings", "Mute, volume, and favorites without opening the app."),
)

/** Back follows the hierarchy: tools return to Settings, everything else returns to the remote. */
private fun parentOf(state: UiState): Screen? = when (state.screen) {
    Screen.Remote, Screen.Welcome -> null
    Screen.Discover -> if (state.active != null) Screen.Remote else Screen.Welcome.takeIf { state.tvs.isEmpty() }
    Screen.Channels, Screen.Apps, Screen.Settings -> Screen.Remote
    Screen.Diagnostics, Screen.Macros, Screen.Preview, Screen.Guide, Screen.About -> Screen.Settings
}

private fun depthOf(screen: Screen) = when (screen) {
    Screen.Welcome, Screen.Remote, Screen.Channels, Screen.Apps -> 0
    Screen.Discover, Screen.Settings -> 1
    Screen.Diagnostics, Screen.Macros, Screen.Preview, Screen.Guide, Screen.About -> 2
}

private fun titleOf(screen: Screen) = when (screen) {
    Screen.Settings -> "Settings"
    Screen.Diagnostics -> "Diagnostics"
    Screen.Macros -> "Macros"
    Screen.Preview -> "Screen preview"
    Screen.Guide -> "Programme guide"
    Screen.Discover -> "Add a TV"
    Screen.About -> "About"
    else -> ""
}

/** Reading width for text-heavy screens on big windows; the remote and the grids use the full width. */
private fun maxWidthOf(screen: Screen): Dp = when (screen) {
    Screen.Discover -> 640.dp
    Screen.Settings -> 1200.dp
    Screen.Diagnostics, Screen.Macros, Screen.Preview, Screen.Guide, Screen.About -> 840.dp
    else -> Dp.Unspecified
}

@Composable
fun NetCastRoot(repo: TvRepository, state: UiState) {
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var tvSheet by rememberSaveable { mutableStateOf(false) }
    var power by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        if (state.message.isBlank()) return@LaunchedEffect
        val undo = state.undoMute
        val result = snack.showSnackbar(
            state.message,
            actionLabel = if (undo) "Undo" else null,
            duration = if (undo) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (undo && result == SnackbarResult.ActionPerformed) repo.undoFlip()
        repo.clearMessage()
    }
    LaunchedEffect(state.channel?.id, state.recentApps.firstOrNull()) {
        val channel = state.channel?.let { state.nicknames[it.id] ?: it.numberLabel }
        val app = state.apps.firstOrNull { it.auid == state.recentApps.firstOrNull() }?.name
        Shortcuts.publish(context, channel, app)
    }

    // Predictive back: the screen shrinks with the gesture, then the parent slides in.
    val parent = parentOf(state)
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = parent != null) { events ->
        try {
            events.collect { backProgress = it.progress }
            parent?.let(repo::open)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            backProgress = 0f
        }
    }

    val topBarState = remember(state.screen) { TopAppBarState(-Float.MAX_VALUE, 0f, 0f) }
    val sub = state.screen !in topLevel && state.screen !in setup
    val scroll = if (sub) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    } else {
        TopAppBarDefaults.pinnedScrollBehavior(topBarState)
    }
    val live = state.link == LinkState.Online || state.link == LinkState.Demo
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }

    CompositionLocalProvider(LocalKeyClick provides state.settings.keyClick) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val layout = WindowLayout(
                width = widthClassOf(maxWidth),
                compactHeight = maxHeight < 480.dp,
                posture = rememberPosture(),
                desktop = maxWidth >= 1200.dp,
                landscape = maxWidth > maxHeight,
            )
            CompositionLocalProvider(LocalWindowLayout provides layout) {
                Row(Modifier.fillMaxSize()) {
                    if (layout.wide && state.screen !in setup) AppRail(repo, state, layout)
                    Scaffold(
                        modifier = Modifier.weight(1f).nestedScroll(scroll.nestedScrollConnection),
                        topBar = { TopBar(repo, state, layout, parent, scroll) { tvSheet = true } },
                        bottomBar = { if (!layout.wide && state.screen in topLevel) BottomNav(repo, state) },
                        snackbarHost = { SnackbarHost(snack) },
                    ) { padding ->
                        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
                        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                        val supporting = layout.width == WidthClass.Expanded && live && (state.screen == Screen.Channels || state.screen == Screen.Apps)
                        Row(Modifier.padding(padding).fillMaxSize()) {
                            AnimatedContent(
                                targetState = state.screen,
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer {
                                        val scale = 1f - 0.08f * backProgress
                                        scaleX = scale
                                        scaleY = scale
                                        shape = RoundedCornerShape((32 * backProgress).dp)
                                        clip = backProgress > 0f
                                    },
                                transitionSpec = {
                                    val from = depthOf(initialState)
                                    val to = depthOf(targetState)
                                    if (from == to) {
                                        (fadeIn(effects) + scaleIn(initialScale = 0.96f)).togetherWith(fadeOut(effects))
                                    } else {
                                        val forward = to > from
                                        (slideInHorizontally(spatial) { if (forward) it / 3 else -it / 3 } + fadeIn(effects))
                                            .togetherWith(slideOutHorizontally(spatial) { if (forward) -it / 3 else it / 3 } + fadeOut(effects))
                                    }
                                },
                                label = "screen",
                            ) { screen ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                    Box(Modifier.widthIn(max = maxWidthOf(screen)).fillMaxSize()) {
                                        when (screen) {
                                            Screen.Welcome -> WelcomePane(repo, androidFeatures, device = "phone")
                                            Screen.Discover -> DiscoverPane(repo, state)
                                            Screen.About -> AboutPane(version)
                                            Screen.Remote -> RemotePane(repo, state, onPower = { power = true })
                                            Screen.Channels -> ChannelsPane(repo, state)
                                            Screen.Apps -> AppsPane(repo, state)
                                            Screen.Settings -> SettingsPane(repo, state)
                                            Screen.Diagnostics -> DiagnosticsPane(repo, state)
                                            Screen.Macros -> MacrosPane(repo, state)
                                            Screen.Preview -> PreviewPane(repo, state, Modifier.verticalScroll(rememberScrollState()))
                                            Screen.Guide -> GuidePane(repo, state)
                                        }
                                    }
                                }
                            }
                            AnimatedVisibility(supporting, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                                MiniRemote(repo, state, Modifier.width(360.dp).padding(end = 16.dp, bottom = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (tvSheet) TvSheet(repo, state) { tvSheet = false }
    if (power) {
        val name = state.active?.name ?: "the TV"
        AlertDialog(
            onDismissRequest = { power = false },
            icon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) },
            title = { Text("Turn off $name?") },
            text = { Text("Turn it back on with the TV's own remote. ${context.getString(R.string.app_name)} reconnects by itself.") },
            confirmButton = {
                TextButton(onClick = {
                    power = false
                    repo.keyTap(KeyCode.POWER)
                }) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = { power = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TopBar(
    repo: TvRepository,
    state: UiState,
    layout: WindowLayout,
    parent: Screen?,
    scroll: TopAppBarScrollBehavior,
    onSwitchTv: () -> Unit,
) {
    val context = LocalContext.current
    when {
        state.screen in topLevel -> TopAppBar(
            title = {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Switch TV", onClick = onSwitchTv)
                        .semantics { role = Role.Button }
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(state.active?.name ?: context.getString(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                }
            },
            subtitle = { StatusLine(state) },
            actions = {
                if (!layout.wide) {
                    Tip("Settings") {
                        IconButton(onClick = { repo.open(Screen.Settings) }) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                    }
                }
            },
            scrollBehavior = scroll,
        )
        state.screen == Screen.Welcome -> Unit
        state.screen == Screen.Discover -> if (parent != null) {
            TopAppBar(title = {}, navigationIcon = { BackButton { repo.open(parent) } })
        }
        else -> LargeFlexibleTopAppBar(
            title = { Text(titleOf(state.screen)) },
            navigationIcon = { parent?.let { BackButton { repo.open(it) } } },
            scrollBehavior = scroll,
        )
    }
}

/** The status under the TV's name, with a dot so the state reads at a glance. */
@Composable
private fun StatusLine(state: UiState) {
    val scheme = MaterialTheme.colorScheme
    val dot by animateColorAsState(
        when {
            state.waking -> scheme.tertiary
            state.link == LinkState.Online -> scheme.primary
            state.link == LinkState.Demo -> scheme.tertiary
            state.link == LinkState.Connecting || state.link == LinkState.Searching || state.link == LinkState.Pairing -> scheme.secondary
            state.link == LinkState.Idle || state.link == LinkState.Found || state.link == LinkState.Asleep -> scheme.outline
            else -> scheme.error
        },
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "status dot",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(statusLine(state), maxLines = 1)
    }
}

@Composable
private fun BottomNav(repo: TvRepository, state: UiState) {
    ShortNavigationBar {
        destinations.forEach { item ->
            val selected = state.screen == item.screen
            ShortNavigationBarItem(
                selected = selected,
                onClick = { repo.open(item.screen) },
                icon = { Icon(if (selected) item.selected else item.unselected, contentDescription = null) },
                label = { Text(item.label) },
            )
        }
    }
}

/**
 * The expressive wide rail. Medium windows get the modal kind, which expands over the content;
 * expanded windows push the content aside, and desktop-sized ones start expanded.
 */
@Composable
private fun AppRail(repo: TvRepository, state: UiState, layout: WindowLayout) {
    val rail = rememberWideNavigationRailState(if (layout.desktop) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed)
    val scope = rememberCoroutineScope()
    val expanded = rail.targetValue == WideNavigationRailValue.Expanded
    val modal = layout.width == WidthClass.Medium
    val header: @Composable () -> Unit = {
        val label = if (expanded) "Collapse navigation" else "Expand navigation"
        Tip(label) {
            IconButton(onClick = { scope.launch { rail.toggle() } }, modifier = Modifier.padding(start = 24.dp)) {
                Icon(if (expanded) Icons.AutoMirrored.Rounded.MenuOpen else Icons.Rounded.Menu, contentDescription = label)
            }
        }
    }
    val items: @Composable () -> Unit = {
        (destinations + settingsDestination).forEach { item ->
            val selected = if (item.screen == Screen.Settings) state.screen in settingsScreens else state.screen == item.screen
            WideNavigationRailItem(
                selected = selected,
                onClick = {
                    repo.open(item.screen)
                    if (modal) scope.launch { rail.collapse() }
                },
                icon = { Icon(if (selected) item.selected else item.unselected, contentDescription = null) },
                label = { Text(item.label) },
                railExpanded = expanded,
            )
        }
    }
    if (modal) {
        ModalWideNavigationRail(state = rail, hideOnCollapse = false, header = header, content = items)
    } else {
        WideNavigationRail(state = rail, header = header, content = items)
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    Tip("Back") {
        IconButton(onClick = onClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
    }
}

/** The TV switcher, the way Google Home lists devices: who is connected, the other TVs, and inputs. */
@Composable
private fun TvSheet(repo: TvRepository, state: UiState, onDismiss: () -> Unit) {
    val tv = state.active
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetCards {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ShapeBadge(Icons.Rounded.Tv, polygon = MaterialShapes.Cookie6Sided, size = 64.dp)
                Column(Modifier.weight(1f)) {
                    Text(tv?.name ?: "No TV", style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(
                        listOfNotNull(tv?.room, statusLine(state)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SegmentedGroup("TVs") {
                state.tvs.filter { it.id != "demo" || state.link == LinkState.Demo }.forEach { other ->
                    val current = other.id == tv?.id
                    actionRow(
                        other.name,
                        Icons.Rounded.Tv,
                        listOf(other.room, other.host).filter { it.isNotBlank() && it != "demo" }.joinToString(" · "),
                        trailing = if (current) ({ Icon(Icons.Rounded.Check, contentDescription = "Current TV") }) else null,
                    ) {
                        if (!current) repo.activate(other.id)
                        onDismiss()
                    }
                }
                actionRow("Add a TV", Icons.Rounded.Add) {
                    repo.open(Screen.Discover)
                    onDismiss()
                }
            }
            val inputs = state.inputMacros.keys.ifEmpty { state.inputNames.keys }
            if (inputs.isNotEmpty()) {
                SegmentedGroup("Inputs") {
                    inputs.forEach { label ->
                        actionRow(
                            state.inputNames[label] ?: prettyInput(label),
                            Icons.AutoMirrored.Rounded.Input,
                            if (label in state.inputMacros) "Switch with its macro" else "Bind a macro in Macros to switch here",
                        ) {
                            repo.playInput(label)
                            onDismiss()
                        }
                    }
                }
            }
            if (tv != null) {
                SegmentedGroup("Connection") {
                    if (state.link == LinkState.Demo) {
                        actionRow("Leave the demo", Icons.Rounded.LinkOff) {
                            repo.leaveDemo()
                            onDismiss()
                        }
                    } else if (state.link == LinkState.Online) {
                        actionRow("Disconnect", Icons.Rounded.LinkOff) {
                            repo.disconnect()
                            onDismiss()
                        }
                    } else {
                        actionRow("Connect", Icons.Rounded.Refresh) {
                            repo.reconnect()
                            onDismiss()
                        }
                    }
                    actionRow("Settings", Icons.Rounded.Settings) {
                        repo.open(Screen.Settings)
                        onDismiss()
                    }
                }
                if (state.link != LinkState.Demo) TvDetails(state)
            }
        }
        }
    }
}
