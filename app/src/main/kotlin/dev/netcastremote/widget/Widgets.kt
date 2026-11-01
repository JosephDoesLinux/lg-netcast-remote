package dev.netcastremote.widget

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.netcastremote.MainActivity
import dev.netcastremote.R
import dev.netcastremote.session.NetCastService
import org.json.JSONObject
import java.io.File

private val Cmd = ActionParameters.Key<String>("cmd")

class WidgetCommand : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val command = parameters[Cmd] ?: return
        context.startForegroundService(Intent(context, NetCastService::class.java).putExtra("command", command))
    }
}

private fun command(value: String) = actionRunCallback<WidgetCommand>(actionParametersOf(Cmd to value))

class NowPlayingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = readSnap(context)
        provideContent { GlanceTheme { NowCard(snap) } }
    }
}

class TransportWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = readSnap(context)
        provideContent {
            GlanceTheme {
                Scaffold {
                    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                        Key(R.drawable.ic_channel_down, "Channel down", "channel_down")
                        Gap()
                        Key(R.drawable.ic_volume_down, "Volume down", "volume_down")
                        Gap()
                        Key(R.drawable.ic_volume_off, if (snap.mute) "Unmute" else "Mute", "mute", strong = snap.mute)
                        Gap()
                        Key(R.drawable.ic_volume_up, "Volume up", "volume_up")
                        Gap()
                        Key(R.drawable.ic_channel_up, "Channel up", "channel_up")
                    }
                }
            }
        }
    }
}

class FavoriteChannelsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = readSnap(context)
        provideContent {
            GlanceTheme {
                ListWidget("Channels", snap.favorites, "Star a channel in the app", prefix = "tune:")
            }
        }
    }
}

class FavoriteAppsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = readSnap(context)
        provideContent {
            GlanceTheme {
                ListWidget("TV apps", snap.apps, "Add favorite apps in the app", prefix = "launch:")
            }
        }
    }
}

class PreviewWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val file = File(context.cacheDir, "preview.jpg")
        val bitmap = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        provideContent {
            GlanceTheme {
                Scaffold(titleBar = { TitleBar(startIcon = ImageProvider(R.drawable.ic_stat), title = "TV screen") }) {
                    if (bitmap != null) {
                        Image(
                            ImageProvider(bitmap),
                            contentDescription = "Latest TV screen",
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp).padding(bottom = 12.dp),
                        )
                    } else {
                        Text("Turn on screen preview in the app", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                    }
                }
            }
        }
    }
}

class NowPlayingReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NowPlayingWidget()
}

class TransportReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TransportWidget()
}

class FavoriteChannelsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = FavoriteChannelsWidget()
}

class FavoriteAppsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = FavoriteAppsWidget()
}

class PreviewReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = PreviewWidget()
}

@Composable
private fun NowCard(snap: Snap) {
    Scaffold(
        titleBar = { TitleBar(startIcon = ImageProvider(R.drawable.ic_stat), title = snap.name) },
        modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
    ) {
        Column(GlanceModifier.fillMaxSize().padding(bottom = 12.dp)) {
            Text(
                snap.channel.ifBlank { snap.status },
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                maxLines = 1,
            )
            val detail = if (snap.channel.isBlank()) "" else snap.program.ifBlank { snap.status }
            if (detail.isNotBlank()) Text(detail, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Key(R.drawable.ic_volume_down, "Volume down", "volume_down")
                Gap()
                Key(R.drawable.ic_volume_off, if (snap.mute) "Unmute" else "Mute", "mute", strong = snap.mute)
                Gap()
                Key(R.drawable.ic_volume_up, "Volume up", "volume_up")
            }
        }
    }
}

@Composable
private fun ListWidget(title: String, names: List<String>, empty: String, prefix: String) {
    Scaffold(titleBar = { TitleBar(startIcon = ImageProvider(R.drawable.ic_stat), title = title) }) {
        Column(GlanceModifier.fillMaxSize().padding(bottom = 12.dp)) {
            if (names.isEmpty()) {
                Text(empty, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
            }
            names.forEach { name ->
                FilledButton(text = name, onClick = command(prefix + name), modifier = GlanceModifier.fillMaxWidth(), maxLines = 1)
                Spacer(GlanceModifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun Key(icon: Int, label: String, value: String, strong: Boolean = false) {
    CircleIconButton(
        imageProvider = ImageProvider(icon),
        contentDescription = label,
        onClick = command(value),
        backgroundColor = if (strong) GlanceTheme.colors.tertiaryContainer else GlanceTheme.colors.secondaryContainer,
        contentColor = if (strong) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onSecondaryContainer,
    )
}

@Composable
private fun Gap() = Spacer(GlanceModifier.width(8.dp))

private data class Snap(
    val name: String,
    val channel: String,
    val program: String,
    val status: String,
    val mute: Boolean,
    val online: Boolean,
    val favorites: List<String>,
    val apps: List<String>,
)

private fun emptySnap(context: Context) = Snap(context.getString(R.string.app_name), "", "", "Open the app to connect", false, false, emptyList(), emptyList())

private fun readSnap(context: Context): Snap {
    val file = File(context.filesDir, "widget-snap.json")
    if (!file.exists()) return emptySnap(context)
    return runCatching {
        val json = JSONObject(file.readText())
        Snap(
            name = json.optString("name", context.getString(R.string.app_name)),
            channel = json.optString("channel"),
            program = json.optString("program"),
            status = json.optString("status", if (json.optBoolean("online")) "Connected" else "Offline"),
            mute = json.optBoolean("mute"),
            online = json.optBoolean("online"),
            favorites = json.optJSONArray("favorites")?.let { array -> List(array.length()) { array.getString(it) } }.orEmpty(),
            apps = json.optJSONArray("apps")?.let { array -> List(array.length()) { array.getString(it) } }.orEmpty(),
        )
    }.getOrDefault(emptySnap(context))
}
