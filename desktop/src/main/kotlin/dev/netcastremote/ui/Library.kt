@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package dev.netcastremote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.graphics.Color
import dev.netcastremote.model.LinkState
import dev.netcastremote.model.Screen
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.netcastremote.model.UiState
import dev.netcastremote.protocol.Channel
import dev.netcastremote.protocol.KeyCode
import dev.netcastremote.protocol.TvApp
import dev.netcastremote.session.TvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChannelsPane(repo: TvRepository, state: UiState, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("") }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    val allowed = state.channels.filter { !state.settings.kidsMode || state.kidsChannels.isEmpty() || it.id in state.kidsChannels }
    val kinds = allowed.map { it.kind }.filter { it.isNotBlank() }.distinct()
    val shown = allowed.filter { channel ->
        (kind.isBlank() || channel.kind.equals(kind, true)) &&
            (query.isBlank() || nameOf(state, channel).contains(query, true) || channel.numberLabel.contains(query))
    }
    val favorites = state.favorites.mapNotNull { id -> allowed.firstOrNull { it.id == id } }
    val recents = state.recents.mapNotNull { id -> allowed.firstOrNull { it.id == id } }.filter { it !in favorites }.take(10)
    val phase = phaseOf(state, hasItems = state.channels.isNotEmpty(), loading = state.loadingChannels, failed = state.channelsFailed)
    RefreshBox(state.loadingChannels, { repo.refreshChannels() }, modifier) {
        PhaseContent(phase, modifier = Modifier.fillMaxSize(), loading = "Loading channels", failed = {
            StatePanel(
                Icons.Rounded.CloudOff,
                "Couldn't load channels",
                "The TV didn't send its channel list. Pull down or try again.",
                polygon = MaterialShapes.Ghostish,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            ) { Button(onClick = { repo.refreshChannels() }, shapes = ButtonDefaults.shapes()) { Text("Try again") } }
        }, offline = { OfflineHint(repo, "Connect to the TV to see its channels.") }) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { SearchField(query, "Search channels") { query = it } }
            if (kinds.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        item { KindChip("All", kind.isBlank()) { kind = "" } }
                        items(kinds) { item -> KindChip(item.replaceFirstChar { it.uppercase() }, kind == item) { kind = item } }
                    }
                }
            }
            if (query.isBlank() && favorites.isNotEmpty()) {
                item { SectionLabel("Favorites") }
                item { ChannelCarousel(favorites, state, repo, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            if (query.isBlank() && recents.isNotEmpty()) {
                item { SectionLabel("Recent") }
                item { ChannelCarousel(recents, state, repo, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
            }
            if (shown.isNotEmpty()) item { SectionLabel(if (query.isBlank()) "All channels" else "Matches") }
            itemsIndexed(shown, key = { _, channel -> channel.id }) { index, channel ->
                val current = channel.id == state.channel?.id
                val starred = channel.id in state.favorites
                SegmentedListItem(
                    selected = current,
                    onClick = { repo.tune(channel) },
                    modifier = Modifier.animateItem().onSecondaryClick { renaming = channel.id },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = shown.size),
                    colors = cardRowColors(highlight = true),
                    onLongClick = { renaming = channel.id },
                    onLongClickLabel = "Rename",
                    leadingContent = { NumberBadge(channel.numberLabel, current) },
                    supportingContent = {
                        val line = listOf(if (current) "On now" else "", channel.program, channel.kind.replaceFirstChar { it.uppercase() })
                            .filter { it.isNotBlank() }.joinToString(" · ")
                        if (line.isNotBlank()) Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        IconToggleButton(checked = starred, onCheckedChange = { repo.toggleFavorite(channel) }) {
                            Icon(
                                if (starred) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                                contentDescription = if (starred) "Remove from favorites" else "Add to favorites",
                            )
                        }
                    },
                ) { Text(nameOf(state, channel), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (shown.isEmpty()) {
                item {
                    if (query.isNotBlank() || kind.isNotBlank()) {
                        StatePanel(Icons.Rounded.Search, "No matches", "Nothing matches “$query”. Long-press a channel to give it a name you'll search for.")
                    } else {
                        StatePanel(
                            Icons.Rounded.LiveTv,
                            "No channels yet",
                            "The TV hasn't been tuned. On the TV, open Home, then Settings, then Channels, and run Auto Tuning.",
                            polygon = MaterialShapes.Clover4Leaf,
                        ) {
                            Button(onClick = { repo.keyTap(KeyCode.HOME) }, shapes = ButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                androidx.compose.foundation.layout.Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Open Home on the TV")
                            }
                        }
                    }
                }
            }
        }
        }
    }
    renaming?.let { id ->
        val channel = state.channels.firstOrNull { it.id == id }
        if (channel != null) {
            TextDialog(
                title = "Rename ${channel.numberLabel}",
                label = "Name",
                initial = nameOf(state, channel),
                onDismiss = { renaming = null },
            ) { repo.nickname(channel, it) }
        }
    }
}

private fun nameOf(state: UiState, channel: Channel) = state.nicknames[channel.id] ?: channel.name.ifBlank { channel.numberLabel }

@Composable
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Clear, contentDescription = "Clear search") }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }) else null,
    )
}

@Composable
private fun NumberBadge(number: String, current: Boolean) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
        )
    }
}

/** Favorites and recents as a multi-browse carousel: large items up front, peeking ones behind. */
@Composable
private fun ChannelCarousel(channels: List<Channel>, state: UiState, repo: TvRepository, color: Color, ink: Color) {
    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { channels.size },
        preferredItemWidth = 156.dp,
        itemSpacing = 8.dp,
        modifier = Modifier.fillMaxWidth().height(112.dp).padding(bottom = 8.dp),
    ) { index ->
        val channel = channels[index]
        Surface(
            onClick = { repo.tune(channel) },
            color = color,
            contentColor = ink,
            modifier = Modifier.fillMaxSize().maskClip(MaterialTheme.shapes.extraLarge),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(channel.numberLabel, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1)
                Text(nameOf(state, channel), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun AppsPane(repo: TvRepository, state: UiState, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var options by rememberSaveable { mutableStateOf<String?>(null) }
    var locked by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = state.apps.filter { !(state.settings.kidsMode && it.adult) }
    val shown = visible.filter { it.name.contains(query, true) }
    val favorites = state.favoriteApps.mapNotNull { id -> visible.firstOrNull { it.auid == id } }
    val open: (TvApp) -> Unit = { app -> if (app.adult && state.settings.adultLock) locked = app.auid else repo.launchApp(app) }
    val phase = phaseOf(state, hasItems = state.apps.isNotEmpty(), loading = state.loadingApps, failed = state.appsFailed)
    RefreshBox(state.loadingApps, { repo.refreshApps() }, modifier) {
        PhaseContent(phase, modifier = Modifier.fillMaxSize(), loading = "Loading TV apps", failed = {
            StatePanel(
                Icons.Rounded.CloudOff,
                "Couldn't load apps",
                "The TV didn't answer. Pull down or try again.",
                polygon = MaterialShapes.Ghostish,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            ) { Button(onClick = { repo.refreshApps() }, shapes = ButtonDefaults.shapes()) { Text("Try again") } }
        }, offline = { OfflineHint(repo, "Connect to the TV to see its apps.") }) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 92.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { SearchField(query, "Search apps") { query = it } }
            if (query.isBlank() && favorites.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Favorites") }
                item(span = { GridItemSpan(maxLineSpan) }) { AppCarousel(repo, favorites, onOpen = open) { options = it.auid } }
            }
            if (shown.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel(if (query.isBlank()) "All apps" + if (state.appCount > 0) " · ${state.appCount}" else "" else "Matches")
                }
            }
            items(shown, key = { it.auid }) { app -> AppTile(repo, state, app, Modifier.animateItem(), onOpen = open) { options = app.auid } }
            if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (query.isNotBlank()) {
                        StatePanel(Icons.Rounded.Search, "No matches", "No TV app is called “$query”.")
                    } else {
                        StatePanel(Icons.Rounded.Apps, "No apps yet", "The TV didn't list any apps. Pull down to ask again.", polygon = MaterialShapes.Pentagon)
                    }
                }
            }
        }
        }
    }
    options?.let { id ->
        state.apps.firstOrNull { it.auid == id }?.let { app -> AppSheet(repo, state, app, onOpen = open) { options = null } }
    }
    locked?.let { id ->
        state.apps.firstOrNull { it.auid == id }?.let { app -> PinDialog(repo, state, app) { locked = null } }
    }
}

@Composable
private fun AppTile(repo: TvRepository, state: UiState, app: TvApp, modifier: Modifier = Modifier, onOpen: (TvApp) -> Unit, onOptions: () -> Unit) {
    val dead = state.deadApps[app.auid] != null
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .onSecondaryClick(onOptions)
            .combinedClickable(onClick = { onOpen(app) }, onLongClick = onOptions, onLongClickLabel = "Options")
            .padding(vertical = 8.dp)
            .alpha(if (dead) 0.55f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(repo, app)
        Text(
            app.name,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (dead) Text("May not open", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
}

/** The TV's own icon when it has one, otherwise the app's initial in a tonal shape. */
@Composable
private fun AppIcon(repo: TvRepository, app: TvApp, size: androidx.compose.ui.unit.Dp = 64.dp) {
    val bitmap by produceState<ImageBitmap?>(null, app.auid) {
        value = withContext(Dispatchers.IO) {
            repo.appIcon(app)?.let(::decodeImage)?.takeIf { it.width >= 8 }
        }
    }
    val shape = RoundedCornerShape(22.dp)
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription = null, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(
            Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun AppSheet(repo: TvRepository, state: UiState, app: TvApp, onOpen: (TvApp) -> Unit, onDismiss: () -> Unit) {
    val favorite = app.auid in state.favoriteApps
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetCards {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(repo, app, 56.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(app.name, style = MaterialTheme.typography.titleLargeEmphasized)
                    val note = state.deadApps[app.auid]?.let { "Last launch failed. The NetCast store closed years ago, so it may never open." }
                        ?: if (app.adult) "Adult" else if (app.category == 2) "Premium" else "My Apps"
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SegmentedGroup {
                actionRow("Open on the TV", Icons.Rounded.PlayArrow) {
                    onDismiss()
                    onOpen(app)
                }
                actionRow(if (favorite) "Remove from favorites" else "Add to favorites", if (favorite) Icons.Rounded.Star else Icons.Outlined.StarOutline) {
                    repo.toggleFavoriteApp(app)
                    onDismiss()
                }
                actionRow("Quit on the TV", Icons.Rounded.Close) {
                    repo.quitApp(app)
                    onDismiss()
                }
            }
        }
        }
    }
}

@Composable
private fun PinDialog(repo: TvRepository, state: UiState, app: TvApp, onDismiss: () -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        title = { Text("Open ${app.name}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (state.settings.pinHash.isBlank()) "This app is marked for adults." else "Enter your PIN to open this app.")
                if (state.settings.pinHash.isNotBlank()) {
                    OutlinedTextField(
                        pin,
                        { pin = it.filter(Char::isDigit).take(8) },
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.semantics { contentDescription = "PIN" },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (state.settings.pinHash.isBlank() || repo.checkPin(pin)) repo.launchApp(app)
                onDismiss()
            }) { Text("Open") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What a list screen shows: its content, or a full-screen state while there is none. */
private enum class Phase { Ready, Loading, Failed, Offline, Empty }

private fun phaseOf(state: UiState, hasItems: Boolean, loading: Boolean, failed: Boolean): Phase = when {
    hasItems -> Phase.Ready
    loading -> Phase.Loading
    state.link != LinkState.Online && state.link != LinkState.Demo -> Phase.Offline
    failed -> Phase.Failed
    else -> Phase.Empty
}

/** Cross-fades between states so a list arriving, or a failure, never pops in. Empty is drawn by the list itself. */
@Composable
private fun PhaseContent(
    phase: Phase,
    modifier: Modifier,
    loading: String,
    failed: @Composable () -> Unit,
    offline: @Composable () -> Unit,
    ready: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = phase,
        modifier = modifier,
        transitionSpec = { (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f)).togetherWith(fadeOut(motion.fastEffectsSpec())) },
        label = "phase",
    ) { shown ->
        // Scrollable even when it's a single panel, so pull-to-refresh works from every state.
        val panel: @Composable (@Composable () -> Unit) -> Unit = { body ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val viewport = maxHeight
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = viewport), contentAlignment = Alignment.Center) { body() }
            }
        }
        when (shown) {
            Phase.Ready, Phase.Empty -> ready()
            Phase.Loading -> panel { Busy(loading, null) }
            Phase.Failed -> panel(failed)
            Phase.Offline -> panel(offline)
        }
    }
}

@Composable
private fun OfflineHint(repo: TvRepository, body: String) {
    StatePanel(Icons.Rounded.WifiOff, "Not connected", body, polygon = MaterialShapes.Ghostish) {
        FilledTonalButton(onClick = { repo.open(Screen.Remote) }, shapes = ButtonDefaults.shapes()) { Text("Go to the remote") }
    }
}

/** Pull-to-refresh with the expressive morphing indicator. */
@Composable
private fun RefreshBox(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val pull = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = pull,
        indicator = { PullToRefreshDefaults.LoadingIndicator(state = pull, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter)) },
    ) { content() }
}

/** Favorite apps as a multi-browse carousel of their TV icons. */
@Composable
private fun AppCarousel(repo: TvRepository, apps: List<TvApp>, onOpen: (TvApp) -> Unit, onOptions: (TvApp) -> Unit) {
    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { apps.size },
        preferredItemWidth = 132.dp,
        itemSpacing = 8.dp,
        modifier = Modifier.fillMaxWidth().height(132.dp),
    ) { index ->
        val app = apps[index]
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.extraLarge)
                .onSecondaryClick { onOptions(app) }
                .combinedClickable(onClick = { onOpen(app) }, onLongClick = { onOptions(app) }, onLongClickLabel = "Options"),
        ) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                AppIcon(repo, app, 64.dp)
                Text(
                    app.name,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
