package dev.netcastremote.session

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.getSystemService
import androidx.palette.graphics.Palette
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dev.netcastremote.integrate.AlarmReceiver
import dev.netcastremote.model.LinkState
import dev.netcastremote.model.Persisted
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress

/** The Android side of the shared session: DataStore, ConnectivityManager, exact alarms, and the watch. */
class AndroidPlatform(private val context: Context) : Platform {
    private val store = TvStore(context)

    override val cacheDir: File get() = context.cacheDir

    override suspend fun load(): Persisted = store.read()

    override suspend fun save(value: Persisted) = store.write(value)

    override fun log(line: String) {
        Log.i("NetCast", line)
    }

    override fun networkIssue(): LinkState? {
        val cm = context.getSystemService<ConnectivityManager>() ?: return LinkState.WrongWifi
        val network = cm.activeNetwork ?: return LinkState.WrongWifi
        val caps = cm.getNetworkCapabilities(network) ?: return LinkState.WrongWifi
        val onLan = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (!onLan) return LinkState.WrongWifi
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return LinkState.Vpn
        return null
    }

    override fun subnetBroadcast(): InetAddress? {
        val cm = context.getSystemService<ConnectivityManager>() ?: return null
        val address = cm.getLinkProperties(cm.activeNetwork)?.linkAddresses?.firstOrNull { it.address is Inet4Address } ?: return null
        return directedBroadcast(address.address, address.prefixLength)
    }

    /** Wi-Fi drops multicast to save power unless an app holds this lock, and SSDP replies are multicast-adjacent. */
    @Suppress("DEPRECATION")
    override fun multicastLock(): AutoCloseable? {
        val wifi = context.getSystemService<WifiManager>() ?: return null
        val lock = wifi.createMulticastLock("netcast").apply {
            setReferenceCounted(false)
            acquire()
        }
        return AutoCloseable { lock.release() }
    }

    override fun watchAddress(onChange: (String?) -> Unit) {
        val cm = context.getSystemService<ConnectivityManager>() ?: return
        cm.registerNetworkCallback(NetworkRequest.Builder().build(), object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) {
                onChange(props.linkAddresses.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }?.address?.hostAddress)
            }
        })
    }

    override fun colorSeed(jpeg: ByteArray): Int? {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        val palette = Palette.from(bitmap).maximumColorCount(8).generate()
        return palette.vibrantSwatch?.rgb ?: palette.dominantSwatch?.rgb
    }

    // Exact alarms wake AlarmReceiver, which works even after the app is closed, so [fire] isn't needed here.
    override fun scheduleReminder(title: String, id: Int, atMs: Long, fire: () -> Unit): Boolean {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra("title", title).putExtra("kind", "guide")
        return exact(atMs, PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    override fun scheduleSleep(atMs: Long, fire: () -> Unit): Boolean = exact(atMs, sleepIntent())

    override fun cancelSleep() {
        context.getSystemService<AlarmManager>()?.cancel(sleepIntent())
    }

    override fun pushNowPlaying(channel: String, program: String) {
        val request = PutDataMapRequest.create("/netcast/state").apply {
            dataMap.putString("channel", channel)
            dataMap.putString("program", program)
            dataMap.putLong("ts", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }

    private fun sleepIntent(): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra("kind", "sleep")
        return PendingIntent.getBroadcast(context, 42, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun exact(atMs: Long, pending: PendingIntent): Boolean {
        val alarm = context.getSystemService<AlarmManager>() ?: return false
        if (!alarm.canScheduleExactAlarms()) return false
        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending)
        return true
    }
}
