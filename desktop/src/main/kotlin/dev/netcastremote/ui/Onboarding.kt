@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.netcastremote.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import dev.netcastremote.model.AppInfo
import dev.netcastremote.session.TvRepository
import kotlinx.coroutines.launch

/** One line of "what you get" on the tour. Each app lists the features its platform has. */
data class Feature(val icon: ImageVector, val title: String, val body: String)

private const val PAGES = 4

/**
 * The first-run tour: what Clicker is, which TVs it fits, what it does, and what to have ready.
 * It ends in a search for the TV, or in the demo TV for a look around.
 */
@Composable
fun WelcomePane(repo: TvRepository, features: List<Feature>, device: String, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES - 1
    Column(modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { scope.launch { pager.animateScrollToPage(PAGES - 1) } }, enabled = !last) { Text(if (last) "" else "Skip") }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val viewport = maxHeight
                Column(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = viewport)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                ) {
                    when (page) {
                        0 -> HeroPage()
                        1 -> CompatibilityPage()
                        2 -> FeaturesPage(features)
                        else -> ReadyPage(device)
                    }
                }
            }
        }
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageDots(pager.currentPage)
            if (last) {
                Button(
                    onClick = { repo.finishOnboarding(demo = false) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
                    Text("Find my TV", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                }
                TextButton(onClick = { repo.finishOnboarding(demo = true) }) { Text("Look around with a demo TV first") }
            } else {
                FilledTonalButton(
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                ) {
                    Text("Next", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun HeroPage() {
    MorphingHero(Icons.Rounded.SettingsRemote, Modifier.size(200.dp))
    Text(
        AppInfo.ERA.uppercase(),
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    Text(AppInfo.NAME, style = MaterialTheme.typography.displaySmallEmphasized, textAlign = TextAlign.Center)
    Text(
        "A modern remote for older LG Smart TVs, the ones LG's current app doesn't support. It talks straight to your TV over your home network: no account, no cloud, no ads.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CompatibilityPage() {
    Text("Will it work with my TV?", style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
    Text(
        "Look for the model number on the sticker on the back of the TV, such as 47LM6200. The letters after the size tell you the series.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FitCard(
            Icons.Rounded.CheckCircle,
            "Works · NetCast 3.0 and 4.0",
            "2012 and 2013 Smart TVs, such as the LM, LS and PM series (2012) and LA, LN and PN (2013). Tested on a 47LM6200.",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        FitCard(
            Icons.Rounded.Help,
            "Should work · NetCast 2.0",
            "2011 Smart TVs, such as the LW and LV series. They use LG's older ROAP protocol, which hasn't been tried on a real set yet.",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        FitCard(
            Icons.Rounded.Block,
            "Won't work · webOS",
            "LG TVs from 2014 on speak a different protocol. LG's ThinQ app covers those.",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FitCard(icon: ImageVector, title: String, body: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FeaturesPage(features: List<Feature>) {
    Text("Everything the old app did, and more", style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
    val shapes = listOf(MaterialShapes.Cookie6Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Pentagon, MaterialShapes.Cookie9Sided, MaterialShapes.Flower)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        features.forEachIndexed { index, feature ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(
                    feature.icon,
                    polygon = shapes[index % shapes.size],
                    size = 56.dp,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Column(Modifier.weight(1f)) {
                    Text(feature.title, style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(feature.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReadyPage(device: String) {
    ShapeBadge(Icons.Rounded.Wifi, polygon = MaterialShapes.Cookie12Sided, size = 120.dp)
    Text("Before you start", style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Step(Icons.Rounded.PowerSettingsNew, "Turn the TV on", "Fully on, not in standby. It can't be woken over the network.")
        Step(Icons.Rounded.Wifi, "Same network", "The TV and this $device need to be on the same Wi-Fi or home network.")
        Step(Icons.Rounded.Pin, "Watch the TV", "It shows a pairing code the first time. You'll type it here.")
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Page position as dots; the current one stretches into a pill. */
@Composable
private fun PageDots(current: Int) {
    Row(
        Modifier.semantics { contentDescription = "Page ${current + 1} of $PAGES" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PAGES) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) 28.dp else 8.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dot width")
            val color by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "dot color",
            )
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

/** The tour's hero: an icon in a shape that slowly breathes between two Material shapes while turning. */
@Composable
internal fun MorphingHero(icon: ImageVector, modifier: Modifier = Modifier) {
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }
    val motion = rememberInfiniteTransition(label = "hero")
    val progress by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "morph")
    val turn by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "turn")
    val container = MaterialTheme.colorScheme.primaryContainer
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = turn }
                .clip(MorphShape(morph, progress))
                .background(container),
        )
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.fillMaxSize(0.42f))
    }
}

/** A [Morph] frozen at [progress], scaled from its unit square to the layout. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress, Path())
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}
