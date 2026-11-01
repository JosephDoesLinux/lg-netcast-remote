package dev.netcastremote.integrate

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dev.netcastremote.MainActivity
import dev.netcastremote.R
import dev.netcastremote.SessionRuntime
import dev.netcastremote.model.Commands
import dev.netcastremote.model.LinkState
import dev.netcastremote.model.nowTitle
import dev.netcastremote.model.statusLine
import dev.netcastremote.protocol.Dlna
import dev.netcastremote.protocol.MiniHttpServer
import dev.netcastremote.protocol.Outgoing
import dev.netcastremote.protocol.RawHttpClient
import dev.netcastremote.protocol.Ssdp
import dev.netcastremote.session.NetCastService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URI

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Commands.ACTION -> {
                val command = intent.getStringExtra("command") ?: return
                context.startForegroundService(Intent(context, NetCastService::class.java).putExtra("command", command))
            }
            else -> {
                val kind = intent.getStringExtra("kind")
                val title = intent.getStringExtra("title")
                if (kind == "guide" && title != null) {
                    val manager = context.getSystemService(android.app.NotificationManager::class.java)
                    NetCastService.ensureChannels(manager)
                    manager.notify(
                        title.hashCode(),
                        NotificationCompat.Builder(context, NetCastService.CHANNEL_GUIDE)
                            .setSmallIcon(R.drawable.ic_stat)
                            .setContentTitle(title)
                            .setContentText(context.getString(R.string.starting_soon))
                            .build(),
                    )
                }
                runCatching { SessionRuntime.repository.handleAlarm(kind, title) }
            }
        }
    }
}

class TvTileService : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        val state = runCatching { SessionRuntime.repository.state.value }.getOrNull()
        tile.label = state?.active?.name ?: getString(R.string.app_name)
        val online = state?.link == LinkState.Online || state?.link == LinkState.Demo
        tile.subtitle = when {
            state == null -> getString(R.string.asleep)
            online && state.channel != null -> nowTitle(state)
            else -> statusLine(state)
        }
        tile.state = if (online) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        val opens = runCatching { SessionRuntime.repository.state.value.settings.tileOpensApp }.getOrDefault(false)
        if (opens) {
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_IMMUTABLE)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(pending) else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } else {
            startForegroundService(Intent(this, NetCastService::class.java).putExtra("command", "mute"))
        }
    }
}

class PhoneWearService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val command = event.data.toString(Charsets.UTF_8)
        CoroutineScope(Dispatchers.IO).launch {
            val repo = SessionRuntime.repository
            repo.ensureSession()
            repo.command(command)
        }
    }
}

class VoipMuteService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification) {
        if (!runCatching { SessionRuntime.repository.state.value.settings.muteOnVoip }.getOrDefault(false)) return
        val category = sbn.notification.category
        val call = category == android.app.Notification.CATEGORY_CALL || sbn.notification.extras.getString(android.app.Notification.EXTRA_TEMPLATE) == "call"
        if (call) SessionRuntime.repository.setRinging(true)
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification) {
        if (sbn.notification.category == android.app.Notification.CATEGORY_CALL) {
            SessionRuntime.repository.setRinging(false)
        }
    }
}

object Shortcuts {
    fun publish(context: Context, channel: String?, app: String?) {
        val dynamic = buildList(2) {
            if (!channel.isNullOrBlank()) {
                add(
                    ShortcutInfoCompat.Builder(context, "last-channel")
                        .setShortLabel(channel)
                        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_stat))
                        .setIntent(Intent(context, MainActivity::class.java).setAction(Commands.ACTION).putExtra("command", "tune:$channel"))
                        .build(),
                )
            }
            if (!app.isNullOrBlank()) {
                add(
                    ShortcutInfoCompat.Builder(context, "last-app")
                        .setShortLabel(app)
                        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_stat))
                        .setIntent(Intent(context, MainActivity::class.java).setAction(Commands.ACTION).putExtra("command", "launch:$app"))
                        .build(),
                )
            }
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, dynamic)
    }
}

object DlnaCaster {
    private var server: MiniHttpServer? = null
    private var file: File? = null

    fun stop() {
        server?.close()
        server = null
        file?.delete()
        file = null
    }

    suspend fun cast(context: Context, uri: Uri, title: String): String {
        stop()
        val dest = File(context.cacheDir, "cast.bin")
        context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            ?: return "Nothing to play."
        file = dest
        val ip = wifiIp(context) ?: return "Join the same Wi-Fi as the TV."
        val httpServer = MiniHttpServer { request ->
            if (request.path.startsWith("/media")) Outgoing(200, body = dest.readBytes(), contentType = "application/octet-stream")
            else Outgoing(404, reason = "Not Found")
        }
        httpServer.start()
        server = httpServer
        val renderer = findRenderer() ?: return "No DLNA renderer answered. Open Smart Share on the TV and try again."
        val media = "http://$ip:${httpServer.port}/media"
        val http = RawHttpClient()
        val headers = mapOf("SOAPAction" to "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"")
        http.post(renderer.host, renderer.port, renderer.control, Dlna.setUri(media, title).toByteArray(), "text/xml; charset=utf-8", false, headers)
        http.post(
            renderer.host,
            renderer.port,
            renderer.control,
            Dlna.play().toByteArray(),
            "text/xml; charset=utf-8",
            false,
            mapOf("SOAPAction" to "\"urn:schemas-upnp-org:service:AVTransport:1#Play\""),
        )
        http.close()
        return "Playing on ${renderer.name}."
    }

    private data class Renderer(val name: String, val host: String, val port: Int, val control: String)

    private fun findRenderer(): Renderer? {
        val socket = MulticastSocket(null)
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(0))
        val group = InetAddress.getByName("239.255.255.250")
        val request = Ssdp.request("urn:schemas-upnp-org:device:MediaRenderer:1").toByteArray()
        socket.send(DatagramPacket(request, request.size, group, 1900))
        socket.soTimeout = 400
        val deadline = android.os.SystemClock.elapsedRealtime() + 2_000
        val buffer = ByteArray(4096)
        val http = RawHttpClient()
        try {
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val notice = Ssdp.parse(String(packet.data, 0, packet.length, Charsets.UTF_8)) ?: continue
                    val uri = URI(notice.location)
                    val body = http.get(uri.host, if (uri.port > 0) uri.port else 80, uri.rawPath ?: "/").text
                    val control = Dlna.controlUrl(body) ?: continue
                    val resolved = if (control.startsWith("http")) URI(control) else URI("http://${uri.host}:${if (uri.port > 0) uri.port else 80}$control")
                    return Renderer(
                        notice.server.ifBlank { uri.host },
                        resolved.host,
                        if (resolved.port > 0) resolved.port else 80,
                        resolved.rawPath ?: control,
                    )
                } catch (_: SocketTimeoutException) {
                }
            }
        } finally {
            socket.close()
            http.close()
        }
        return null
    }

    private fun wifiIp(context: Context): String? {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses.firstOrNull { it.address is java.net.Inet4Address && !it.address.isLoopbackAddress }?.address?.hostAddress
    }
}

object AudioDuck {
    fun install(context: Context) {
        val manager = context.getSystemService(AudioManager::class.java)
        manager.registerAudioPlaybackCallback(object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>) {
                val other = configs.any { it.audioAttributes.usage == android.media.AudioAttributes.USAGE_MEDIA }
                runCatching { SessionRuntime.repository.duck(other) }
            }
        }, android.os.Handler(android.os.Looper.getMainLooper()))
    }
}
