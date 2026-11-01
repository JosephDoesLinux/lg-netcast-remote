@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.netcastremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/** An icon inside one of the expressive Material shapes, used for heroes and empty states. */
@Composable
fun ShapeBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    size: Dp = 96.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val shape = polygon.toShape()
    Box(modifier.size(size).clip(shape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.42f))
    }
}

/** Centered illustration, headline, explanation, and actions for empty, offline, and permission states. */
@Composable
fun StatePanel(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShapeBadge(icon, polygon = polygon, size = 112.dp, container = container, content = content)
        Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Rows of a segmented group. Each row receives the shape for its position. */
class GroupScope {
    internal val rows = mutableListOf<@Composable (ListItemShapes) -> Unit>()

    fun row(content: @Composable (ListItemShapes) -> Unit) {
        rows += content
    }
}

/** A titled group of segmented list items, the grouped-card look of Android 17 Settings. */
@Composable
fun SegmentedGroup(title: String? = null, modifier: Modifier = Modifier, build: GroupScope.() -> Unit) {
    val group = GroupScope().apply(build)
    if (group.rows.isEmpty()) return
    Column(modifier) {
        if (title != null) SectionLabel(title)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            group.rows.forEachIndexed { index, row ->
                row(ListItemDefaults.segmentedShapes(index = index, count = group.rows.size))
            }
        }
    }
}

/** The card tone for grouped rows. Sheets sit on a raised surface already, so they provide a higher tone. */
val LocalCardColor = compositionLocalOf<Color?> { null }

/** Content of a bottom sheet whose grouped rows need to stand out from the sheet itself. */
@Composable
fun SheetCards(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCardColor provides MaterialTheme.colorScheme.surfaceContainerHigh, content = content)
}

/**
 * Every row of a group sits on the same tonal card. Dark mode's surface is true black, so the default
 * segmented color would hide the cards. With [highlight] off, a checked switch row keeps the plain card
 * and only the switch shows its state, as in Android Settings.
 */
@Composable
fun cardRowColors(highlight: Boolean = false): ListItemColors {
    val scheme = MaterialTheme.colorScheme
    val card = LocalCardColor.current ?: scheme.surfaceContainer
    return if (highlight) {
        ListItemDefaults.segmentedColors(containerColor = card)
    } else {
        ListItemDefaults.segmentedColors(
            containerColor = card,
            selectedContainerColor = card,
            selectedContentColor = scheme.onSurface,
            selectedLeadingContentColor = scheme.onSurfaceVariant,
            selectedTrailingContentColor = scheme.onSurfaceVariant,
            selectedSupportingContentColor = scheme.onSurfaceVariant,
        )
    }
}

/** The expressive switch: a check in the thumb when on. */
@Composable
fun CheckSwitch(checked: Boolean, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        thumbContent = if (checked) ({ Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }) else null,
    )
}

fun GroupScope.switchRow(
    title: String,
    checked: Boolean,
    icon: ImageVector? = null,
    supporting: String? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) = row { shapes ->
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        shapes = shapes,
        enabled = enabled,
        colors = cardRowColors(),
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { CheckSwitch(checked, enabled) },
    ) { Text(title) }
}

fun GroupScope.actionRow(
    title: String,
    icon: ImageVector? = null,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) = row { shapes ->
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        onLongClick = onLongClick,
        colors = cardRowColors(),
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = trailing,
    ) { Text(title) }
}

fun GroupScope.infoRow(title: String, value: String, icon: ImageVector? = null) = row { shapes ->
    SegmentedListItem(
        shapes = shapes,
        colors = cardRowColors(),
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        supportingContent = { Text(value) },
    ) { Text(title) }
}
