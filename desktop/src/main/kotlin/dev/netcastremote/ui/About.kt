package dev.netcastremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.netcastremote.model.AppInfo

/** What Clicker is, where its source lives, how it was made, and that LG has nothing to do with it. */
@Composable
fun AboutPane(version: String, modifier: Modifier = Modifier) {
    val links = LocalUriHandler.current
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MorphingHero(Icons.Rounded.SettingsRemote, Modifier.size(128.dp))
            Text(AppInfo.NAME, style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
            Text(AppInfo.TAGLINE, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text("Version $version", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        SegmentedGroup("Open source") {
            actionRow("Source code", Icons.Rounded.Code, AppInfo.REPO.removePrefix("https://")) { links.openUri(AppInfo.REPO) }
            actionRow("Report a problem or ask for a feature", Icons.Rounded.BugReport, "Issues on GitHub") { links.openUri(AppInfo.ISSUES) }
            actionRow("${AppInfo.LICENSE} license", Icons.Rounded.Gavel, "Free to use, change, and share") { links.openUri(AppInfo.LICENSE_URL) }
        }
        SegmentedGroup("How it was made") {
            infoRow(
                "Written by AI",
                "Clicker's code was written by AI coding agents, mostly Anthropic's Claude, directed and tested on a real 2012 LG TV by ${AppInfo.AUTHOR}.",
                Icons.Rounded.AutoAwesome,
            )
            infoRow(
                "Built on LG's own protocol",
                "It speaks UDAP 2.0 from LG's published NetCast developer documents, and the older ROAP for 2011 sets.",
                Icons.Rounded.Description,
            )
        }
        SegmentedGroup("Good to know") {
            infoRow("Private by design", "Clicker talks only to your TV, on your own network. No account, no cloud, no tracking.", Icons.Rounded.Lock)
            infoRow(
                "Not made by LG",
                "LG, NetCast, and webOS are trademarks of LG Electronics. Clicker is an independent project, not affiliated with or endorsed by LG.",
                Icons.Rounded.Info,
            )
        }
    }
}
