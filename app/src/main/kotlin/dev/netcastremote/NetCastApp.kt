package dev.netcastremote

import android.app.Application
import android.app.NotificationManager
import androidx.glance.appwidget.updateAll
import dev.netcastremote.session.NetCastService
import dev.netcastremote.session.TvRepository
import dev.netcastremote.widget.FavoriteAppsWidget
import dev.netcastremote.widget.FavoriteChannelsWidget
import dev.netcastremote.widget.NowPlayingWidget
import dev.netcastremote.widget.PreviewWidget
import dev.netcastremote.widget.TransportWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NetCastApp : Application() {
    lateinit var repository: TvRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        repository = TvRepository(dev.netcastremote.session.AndroidPlatform(this), scope)
        SessionRuntime.repository = repository
        NetCastService.ensureChannels(getSystemService(NotificationManager::class.java))
        // Launch flips this several times within a second, from the main and IO threads. Stopping the service
        // before its startForeground has run crashes the app, so decisions are serialized on the main thread
        // and a stop waits to see whether it still stands.
        var serviceJob: Job? = null
        repository.onForeground = { needed ->
            scope.launch {
                serviceJob?.cancel()
                serviceJob = scope.launch {
                    if (!needed) delay(2_000)
                    val intent = android.content.Intent(this@NetCastApp, NetCastService::class.java)
                    if (needed) androidx.core.content.ContextCompat.startForegroundService(this@NetCastApp, intent)
                    else stopService(intent)
                }
            }
        }
        repository.onWidgets = {
            scope.launch(Dispatchers.IO) {
                val json = repository.widgetSnapshot()
                filesDir.resolve("widget-snap.json").writeText(json)
                NowPlayingWidget().updateAll(this@NetCastApp)
                TransportWidget().updateAll(this@NetCastApp)
                FavoriteChannelsWidget().updateAll(this@NetCastApp)
                FavoriteAppsWidget().updateAll(this@NetCastApp)
                PreviewWidget().updateAll(this@NetCastApp)
            }
        }
        dev.netcastremote.integrate.AudioDuck.install(this)
        repository.start()
    }
}

object SessionRuntime {
    lateinit var repository: TvRepository
}
