@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.netcastremote.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/** Material window size classes by width: phones, small tablets and unfolded foldables, then tablets and desktop windows. */
enum class WidthClass { Compact, Medium, Expanded }

fun widthClassOf(width: Dp) = when {
    width < 600.dp -> WidthClass.Compact
    width < 840.dp -> WidthClass.Medium
    else -> WidthClass.Expanded
}

/** How a foldable is held. The hinge is in window pixels, so callers offset it by their own position. */
sealed interface Posture {
    data object Flat : Posture

    /** Half open with the hinge across the screen: the top half stands up, the bottom half lies flat. */
    data class Tabletop(val hinge: Rect) : Posture

    /** Hinge down the middle, half open or across two screens: content goes either side of it. */
    data class Book(val hinge: Rect) : Posture
}

data class WindowLayout(
    val width: WidthClass,
    val compactHeight: Boolean,
    val posture: Posture,
    val desktop: Boolean,
    val landscape: Boolean,
) {
    /** Anything but a portrait phone gets the navigation rail. */
    val wide get() = width != WidthClass.Compact

    /** Room for two panes side by side: big windows, and medium ones lying on their side (phones in landscape). */
    val twoPane get() = width == WidthClass.Expanded || (wide && landscape)
}

internal val LocalWindowLayout = staticCompositionLocalOf { WindowLayout(WidthClass.Compact, false, Posture.Flat, false, false) }

@Composable
fun rememberPosture(): Posture {
    val activity = LocalActivity.current ?: return Posture.Flat
    val info by remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
        .collectAsStateWithLifecycle(initialValue = null)
    val fold = info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull() ?: return Posture.Flat
    val hinge = fold.bounds.run { Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()) }
    val bent = fold.state == FoldingFeature.State.HALF_OPENED
    return when {
        fold.orientation == FoldingFeature.Orientation.HORIZONTAL && bent -> Posture.Tabletop(hinge)
        fold.orientation == FoldingFeature.Orientation.VERTICAL && (bent || fold.isSeparating) -> Posture.Book(hinge)
        else -> Posture.Flat
    }
}

/**
 * Places each child in whichever of [columns] columns is shortest so far, so groups of different
 * heights pack into balanced columns on wide windows. One column behaves like a plain Column.
 */
@Composable
fun Masonry(columns: Int, modifier: Modifier = Modifier, gap: Dp = 24.dp, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val space = gap.roundToPx()
        val count = columns.coerceAtLeast(1)
        val width = ((constraints.maxWidth - space * (count - 1)) / count).coerceAtLeast(0)
        val heights = IntArray(count)
        val placed = measurables.map { measurable ->
            val column = heights.indices.minBy { heights[it] }
            val placeable = measurable.measure(Constraints(minWidth = width, maxWidth = width))
            val y = heights[column]
            heights[column] += placeable.height
            Triple(placeable, column, y)
        }
        layout(constraints.maxWidth, heights.maxOrNull() ?: 0) {
            placed.forEach { (placeable, column, y) -> placeable.place(column * (width + space), y) }
        }
    }
}

/** A plain tooltip for an icon-only control: it shows on mouse hover, and on long-press for touch. */
@Composable
fun Tip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}

/** A right-click with a mouse or touchpad, so desktop users reach the menu that long-press opens. */
fun Modifier.onSecondaryClick(action: () -> Unit): Modifier = pointerInput(action) {
    awaitEachGesture {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
            event.changes.forEach { it.consume() }
            action()
        }
    }
}
