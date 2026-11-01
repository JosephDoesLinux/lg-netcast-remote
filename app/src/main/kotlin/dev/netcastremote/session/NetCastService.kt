package dev.netcastremote.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.graphics.drawable.Icon
import dev.netcastremote.MainActivity
import dev.netcastremote.R
import dev.netcastremote.SessionRuntime
import dev.netcastremote.model.Commands
import dev.netcastremote.model.LinkState
import dev.netcastremote.model.nowTitle
import dev.netcastremote.model.statusLine
import dev.netcastremote.protocol.KeyCode
import dev.netcastremote.protocol.MatterClusters
import dev.netcastremote.protocol.MiniHttpServer
import dev.netcastremote.protocol.Outgoing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NetCastService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null
    private var session: MediaSession? = null
    private var matter: MiniHttpServer? = null
    private var audio: AudioManager.OnModeChangedListener? = null
    private var volume: android.media.VolumeProvider? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTE_ID, notification(getString(R.string.app_name), "Connecting"), foregroundType())
        session = MediaSession(this, "NetCast").apply {
            setSessionActivity(PendingIntent.getActivity(this@NetCastService, 1, Intent(this@NetCastService, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = command("key:${KeyCode.PLAY.code}")
                override fun onPause() = command("key:${KeyCode.PAUSE.code}")
                override fun onSkipToNext() = command("channel_up")
                override fun onSkipToPrevious() = command("channel_down")
                override fun onStop() = command("key:${KeyCode.STOP.code}")
                override fun onCustomAction(action: String, extras: android.os.Bundle?) = command(action)
            })
            isActive = true
        }
        val manager = getSystemService(AudioManager::class.java)
        val listener = AudioManager.OnModeChangedListener { mode ->
            SessionRuntime.repository.setRinging(mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION)
        }
        audio = listener
        manager.addOnModeChangedListener(mainExecutor, listener)
        watch = scope.launch {
            SessionRuntime.repository.state.collectLatest { state ->
                val channel = state.channel
                val title = if (channel != null) nowTitle(state) else state.active?.name ?: getString(R.string.app_name)
                val text = channel?.program?.ifBlank { null } ?: statusLine(state)
                val note = notification(title, text.orEmpty())
                getSystemService(NotificationManager::class.java).notify(NOTE_ID, note)
                // Remote volume while the remote is open too, so the rocker shows Android's own panel with the TV's level.
                val remote = state.settings.volumeRockerBackground || (state.uiVisible && state.settings.volumeRockerInApp)
                val cap = if (state.settings.kidsMode) state.settings.kidsVolumeCap else state.volume.max
                updateSession(title, text.orEmpty(), state.volume.level, cap, remote)
                if (state.settings.matterBridge && (state.link == LinkState.Online || state.link == LinkState.Demo)) {
                    if (matter == null) matter = startMatter()
                } else {
                    matter?.close()
                    matter = null
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTE_ID, notification(getString(R.string.app_name), "Remote"), foregroundType())
        val command = intent?.getStringExtra("command")
        if (command != null) {
            scope.launch(Dispatchers.IO) {
                val repo = SessionRuntime.repository
                repo.ensureSession()
                repo.command(command)
                if (!repo.wantsService()) stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        remote.value = null
        watch?.cancel()
        audio?.let { getSystemService(AudioManager::class.java).removeOnModeChangedListener(it) }
        session?.release()
        matter?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun command(raw: String) {
        startService(Intent(this, NetCastService::class.java).putExtra("command", raw))
    }

    private fun updateSession(title: String, text: String, level: Int, max: Int, remoteVolume: Boolean) {
        val session = session ?: return
        session.setMetadata(
            android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, getString(R.string.app_name))
                .putString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, text)
                .build(),
        )
        // Android 13+ media controls come from the session, not notification actions: channel is prev/next,
        // and volume rides in the two custom slots.
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_STOP,
                )
                .addCustomAction(PlaybackState.CustomAction.Builder("volume_down", getString(R.string.volume_down), R.drawable.ic_volume_down).build())
                .addCustomAction(PlaybackState.CustomAction.Builder("volume_up", getString(R.string.volume_up), R.drawable.ic_volume_up).build())
                .addCustomAction(PlaybackState.CustomAction.Builder("mute", getString(R.string.mute), R.drawable.ic_volume_off).build())
                .setState(PlaybackState.STATE_PAUSED, 0, 1f)
                .build(),
        )
        if (!remoteVolume || max <= 0) {
            if (volume != null) {
                session.setPlaybackToLocal(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
                volume = null
            }
            remote.value = null
            return
        }
        val current = volume
        if (current == null || current.maxVolume != max) {
            val provider = object : android.media.VolumeProvider(VOLUME_CONTROL_ABSOLUTE, max, level.coerceIn(0, max)) {
                override fun onAdjustVolume(direction: Int) {
                    // Above the kids ceiling, up does nothing.
                    if (direction > 0 && currentVolume >= maxVolume) return
                    when {
                        direction > 0 -> command("volume_up")
                        direction < 0 -> command("volume_down")
                    }
                }

                override fun onSetVolumeTo(volume: Int) = command("volume:${volume.coerceIn(0, maxVolume)}")
            }
            session.setPlaybackToRemote(provider)
            volume = provider
        } else if (current.currentVolume != level.coerceIn(0, max)) {
            current.currentVolume = level.coerceIn(0, max)
        }
        remote.value = session.sessionToken
    }

    private fun notification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val mute = pending("mute")
        val up = pending("volume_up")
        val down = pending("volume_down")
        val style = Notification.MediaStyle().setMediaSession(session?.sessionToken)
        val builder = Notification.Builder(this, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setStyle(style)
            .addAction(action(getString(R.string.volume_down), R.drawable.ic_volume_down, down))
            .addAction(action(getString(R.string.mute), R.drawable.ic_volume_off, mute))
            .addAction(action(getString(R.string.volume_up), R.drawable.ic_volume_up, up))
        if (Build.VERSION.SDK_INT >= 36) builder.setShortCriticalText(title)
        return builder.build()
    }

    private fun action(label: String, icon: Int, intent: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(this, icon), label, intent).build()

    private fun pending(command: String): PendingIntent = PendingIntent.getService(
        this,
        command.hashCode(),
        Intent(this, NetCastService::class.java).putExtra("command", command),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun startMatter(): MiniHttpServer {
        val server = MiniHttpServer { request ->
            val line = request.body.toString(Charsets.UTF_8).trim()
            val parsed = MatterClusters.parseLine(line)
            val result = if (parsed == null) "bad request" else SessionRuntime.repository.matter(parsed)
            Outgoing(200, body = result.toByteArray(), contentType = "text/plain; charset=utf-8")
        }
        server.start()
        return server
    }

    private fun foregroundType(): Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK

    companion object {
        const val NOTE_ID = 7
        const val CHANNEL_SESSION = "session"
        const val CHANNEL_PAIRING = "pairing"
        const val CHANNEL_FAILURES = "failures"
        const val CHANNEL_GUIDE = "guide"

        private val remote = MutableStateFlow<MediaSession.Token?>(null)

        /** The session's token while it carries the TV's volume; the activity routes volume keys through it. */
        val remoteVolumeToken: StateFlow<MediaSession.Token?> = remote.asStateFlow()

        fun ensureChannels(manager: NotificationManager) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_SESSION, "Remote session", NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel(CHANNEL_PAIRING, "Pairing", NotificationManager.IMPORTANCE_DEFAULT))
            manager.createNotificationChannel(NotificationChannel(CHANNEL_FAILURES, "Connection", NotificationManager.IMPORTANCE_DEFAULT))
            manager.createNotificationChannel(NotificationChannel(CHANNEL_GUIDE, "Programme reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}
