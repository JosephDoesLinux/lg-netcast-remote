package dev.netcastremote.wear

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object WatchState {
    var channel by mutableStateOf("TV")
}

class WatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchState.channel = getSharedPreferences("netcast", 0).getString("channel", "TV") ?: "TV"
        setContent { WatchFace() }
    }
}

@Composable
private fun WatchFace() {
    val context = LocalContext.current
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(WatchState.channel)
            Button(onClick = { send(context, "volume_up") }) { Text("Vol +") }
            Button(onClick = { send(context, "volume_down") }) { Text("Vol −") }
            Button(onClick = { send(context, "mute") }) { Text("Mute") }
            Button(onClick = { send(context, "key:12") }) { Text("Up") }
            Button(onClick = { send(context, "key:20") }) { Text("OK") }
            Button(onClick = { send(context, "key:13") }) { Text("Down") }
            Button(onClick = { send(context, "channel_up") }) { Text("Ch +") }
            Button(onClick = { send(context, "channel_down") }) { Text("Ch −") }
        }
    }
}

private fun send(context: Context, command: String) {
    context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    CoroutineScope(Dispatchers.IO).launch {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        val bytes = command.toByteArray()
        nodes.forEach { node ->
            Wearable.getMessageClient(context).sendMessage(node.id, "/netcast/cmd", bytes).await()
        }
    }
}
