package dev.netcastremote

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.media.session.MediaController
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import android.util.Rational
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.netcastremote.integrate.DlnaCaster
import dev.netcastremote.model.Commands
import dev.netcastremote.model.Screen
import dev.netcastremote.protocol.KeyCode
import dev.netcastremote.session.NetCastService
import dev.netcastremote.ui.NetCastRoot
import dev.netcastremote.ui.NetCastTheme
import dev.netcastremote.ui.StatePanel
import dev.netcastremote.model.nowTitle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), SensorEventListener {
    private val repo by lazy { (application as NetCastApp).repository }
    private var sensor: SensorManager? = null
    private var faceDown = false
    private var gyro = false
    private var locked by mutableStateOf(false)
    private var unlocking = false
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState?.getBoolean("unlocked") == true) stoppedAt = SystemClock.elapsedRealtime()
        handleIntent(intent)
        // While the session carries the TV's volume, the system sends this activity's volume keys to it
        // and shows its own volume panel with the TV's level.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NetCastService.remoteVolumeToken.collect { token ->
                    mediaController = token?.let { MediaController(this@MainActivity, it) }
                }
            }
        }
        setContent {
            val state by repo.state.collectAsState()
            if (state.settings.securePreview && state.screen == Screen.Preview) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            NetCastTheme(this, state.artSeed, state.settings.wallpaperColors) {
                if (locked) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        StatePanel(Icons.Rounded.Lock, "${getString(R.string.app_name)} is locked", "Unlock with your fingerprint, face, or screen lock.") {
                            Button(onClick = ::unlock) { Text("Unlock") }
                        }
                    }
                } else {
                    NetCastRoot(repo, state)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Lock on a fresh start or after half a minute away, not on every trip to a picker or permission dialog.
        val away = stoppedAt == 0L || SystemClock.elapsedRealtime() - stoppedAt > 30_000
        if (repo.state.value.settings.appLock && (locked || away)) {
            locked = true
            unlock()
        }
    }

    override fun onStop() {
        stoppedAt = SystemClock.elapsedRealtime()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("unlocked", !locked)
    }

    override fun onResume() {
        super.onResume()
        repo.setVisible(true)
        sensor = getSystemService(SensorManager::class.java)
        val settings = repo.state.value.settings
        if (settings.pocketGuard) sensor?.registerListener(this, sensor?.getDefaultSensor(Sensor.TYPE_PROXIMITY), SensorManager.SENSOR_DELAY_NORMAL)
        if (settings.flipMute) sensor?.registerListener(this, sensor?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
        if (settings.airMouse) sensor?.registerListener(this, sensor?.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        repo.setVisible(false)
        sensor?.unregisterListener(this)
        super.onPause()
    }

    private fun unlock() {
        if (unlocking) return
        unlocking = true
        BiometricPrompt.Builder(this)
            .setTitle("Unlock ${getString(R.string.app_name)}")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
            .build()
            .authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        unlocking = false
                        locked = false
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        unlocking = false
                    }
                },
            )
    }

    override fun onSensorChanged(event: SensorEvent) {
        val settings = repo.state.value.settings
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> if (settings.pocketGuard) repo.setPocket(event.values[0] < (event.sensor.maximumRange / 2f).coerceAtLeast(1f))
            Sensor.TYPE_ACCELEROMETER -> if (settings.flipMute) {
                val down = event.values[2] < -7f
                if (down && !faceDown) repo.flipMute()
                faceDown = down
            }
            Sensor.TYPE_GYROSCOPE -> if (settings.airMouse && gyro) {
                repo.pointerMove(-event.values[2] * 12f, -event.values[0] * 12f)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (locked) return super.onKeyDown(keyCode, event)
        if (repo.state.value.pocketNear) return true
        shortcutFor(keyCode, event)?.let { key ->
            if (event.repeatCount == 0) {
                if (key.family == KeyCode.Family.DIGIT) repo.digit(key) else repo.keyDown(key)
            }
            return true
        }
        val mapped = mapKey(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (mapped.family == KeyCode.Family.VOLUME) {
            // With a TV session attached, the window hands volume keys to it and the system panel shows the TV.
            if (mediaController != null || !repo.state.value.settings.volumeRockerInApp) return super.onKeyDown(keyCode, event)
        }
        // keyDown runs its own accelerating repeat, so the OS auto-repeats are dropped.
        if (event.repeatCount == 0) repo.keyDown(mapped)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!locked && shortcutFor(keyCode, event) != null) {
            repo.keyUp()
            return true
        }
        val mapped = mapKey(keyCode)
        if (mapped != null && !locked && !(mapped.family == KeyCode.Family.VOLUME && mediaController != null)) {
            repo.keyUp()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (event.source and android.view.InputDevice.SOURCE_STYLUS == android.view.InputDevice.SOURCE_STYLUS &&
            event.action == android.view.MotionEvent.ACTION_HOVER_MOVE
        ) {
            repo.pointerMove(event.x - event.rawX, event.y - event.rawY)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun handleIntent(intent: Intent?) {
        val command = intent?.getStringExtra("command")
        if (intent?.action == Commands.ACTION && command != null) {
            startForegroundService(Intent(this, NetCastService::class.java).putExtra("command", command))
        }
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val stream = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            when {
                text != null -> repo.shareText(text)
                stream != null -> lifecycleScope.launch {
                    val message = DlnaCaster.cast(this@MainActivity, stream, intent.getStringExtra(Intent.EXTRA_TITLE) ?: "Phone")
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }
        if (intent?.action == "dev.netcastremote.SHORTCUT_POINTER") {
            repo.updateSettings { copy(layout = "pointer") }
            repo.open(Screen.Remote)
        }
        gyro = intent?.action == "dev.netcastremote.SHORTCUT_POINTER"
    }

    /**
     * Keyboard shortcuts for desktop windows and tablets with keyboards. Only on the remote screen and
     * only keys no focused field took, so typing in a search box or dialog is never sent to the TV.
     */
    private fun shortcutFor(code: Int, event: KeyEvent): KeyCode? {
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return null
        if (repo.state.value.screen != Screen.Remote) return null
        return when (code) {
            KeyEvent.KEYCODE_ESCAPE -> KeyCode.BACK
            KeyEvent.KEYCODE_H -> KeyCode.HOME
            KeyEvent.KEYCODE_M -> KeyCode.MUTE
            KeyEvent.KEYCODE_I -> KeyCode.INPUT
            KeyEvent.KEYCODE_E -> KeyCode.EXIT
            KeyEvent.KEYCODE_G -> KeyCode.EPG
            KeyEvent.KEYCODE_SPACE -> KeyCode.PAUSE
            KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD -> KeyCode.VOLUME_UP
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> KeyCode.VOLUME_DOWN
            KeyEvent.KEYCODE_PAGE_UP -> KeyCode.CHANNEL_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> KeyCode.CHANNEL_DOWN
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> KeyCode.valueOf("NUM_${code - KeyEvent.KEYCODE_0}")
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> KeyCode.valueOf("NUM_${code - KeyEvent.KEYCODE_NUMPAD_0}")
            else -> null
        }
    }

    /** Lists the shortcuts in the system's keyboard shortcuts helper (Meta + /). */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        fun item(label: String, key: Int) = KeyboardShortcutInfo(label, key, 0)
        data += KeyboardShortcutGroup(
            "TV remote",
            listOf(
                item("Move", KeyEvent.KEYCODE_DPAD_UP),
                item("OK", KeyEvent.KEYCODE_ENTER),
                item("Back", KeyEvent.KEYCODE_ESCAPE),
                item("Home", KeyEvent.KEYCODE_H),
                item("Exit", KeyEvent.KEYCODE_E),
                item("Input", KeyEvent.KEYCODE_I),
                item("Guide", KeyEvent.KEYCODE_G),
                item("Mute", KeyEvent.KEYCODE_M),
                item("Volume up", KeyEvent.KEYCODE_EQUALS),
                item("Volume down", KeyEvent.KEYCODE_MINUS),
                item("Channel up", KeyEvent.KEYCODE_PAGE_UP),
                item("Channel down", KeyEvent.KEYCODE_PAGE_DOWN),
                item("Pause", KeyEvent.KEYCODE_SPACE),
                item("Channel number", KeyEvent.KEYCODE_0),
            ),
        )
    }

    private fun mapKey(code: Int): KeyCode? = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> KeyCode.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> KeyCode.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> KeyCode.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> KeyCode.RIGHT
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> KeyCode.OK
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B -> null
        KeyEvent.KEYCODE_VOLUME_UP -> KeyCode.VOLUME_UP
        KeyEvent.KEYCODE_VOLUME_DOWN -> KeyCode.VOLUME_DOWN
        KeyEvent.KEYCODE_MEDIA_PLAY -> KeyCode.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> KeyCode.PAUSE
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> KeyCode.PAUSE
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> KeyCode.CHANNEL_UP
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> KeyCode.CHANNEL_DOWN
        KeyEvent.KEYCODE_MEDIA_STOP -> KeyCode.STOP
        KeyEvent.KEYCODE_MEDIA_REWIND -> KeyCode.REWIND
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> KeyCode.FAST_FORWARD
        else -> null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}

/** The small sheet a lock-screen widget opens: volume, mute, and channel, nothing that needs unlocking. */
class LockedControlsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        val repo = (application as NetCastApp).repository
        setContent {
            val state by repo.state.collectAsState()
            NetCastTheme(this, null, state.settings.wallpaperColors) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(nowTitle(state), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            ControlGroup(
                                listOf(
                                    Control(Icons.Rounded.Remove, "Volume down") { repo.keyTap(KeyCode.VOLUME_DOWN) },
                                    Control(Icons.AutoMirrored.Rounded.VolumeOff, if (state.volume.mute) "Unmute" else "Mute", checked = state.volume.mute) { repo.keyTap(KeyCode.MUTE) },
                                    Control(Icons.Rounded.Add, "Volume up") { repo.keyTap(KeyCode.VOLUME_UP) },
                                ),
                            )
                            ControlGroup(
                                listOf(
                                    Control(Icons.Rounded.KeyboardArrowDown, "Channel down") { repo.keyTap(KeyCode.CHANNEL_DOWN) },
                                    Control(Icons.Rounded.KeyboardArrowUp, "Channel up") { repo.keyTap(KeyCode.CHANNEL_UP) },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private class Control(val icon: ImageVector, val label: String, val checked: Boolean = false, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ControlGroup(controls: List<Control>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        controls.forEachIndexed { index, control ->
            ToggleButton(
                checked = control.checked,
                onCheckedChange = { control.onClick() },
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    controls.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Icon(control.icon, contentDescription = control.label, modifier = Modifier.padding(ButtonDefaults.IconSpacing)) }
        }
    }
}

/**
 * Picture-in-picture mini remote. A PiP window takes no touches, so its controls are
 * system RemoteActions in the PiP menu; the window itself just shows what's on.
 */
class MiniRemoteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as NetCastApp).repository
        setContent {
            val state by repo.state.collectAsState()
            NetCastTheme(this, null, state.settings.wallpaperColors) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                        Text(nowTitle(state), style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(
                listOf(
                    action("volume_down", R.drawable.ic_volume_down, getString(R.string.volume_down)),
                    action("mute", R.drawable.ic_volume_off, getString(R.string.mute)),
                    action("volume_up", R.drawable.ic_volume_up, getString(R.string.volume_up)),
                ),
            )
            .build()
        setPictureInPictureParams(params)
        enterPictureInPictureMode(params)
    }

    private fun action(command: String, icon: Int, label: String): RemoteAction = RemoteAction(
        Icon.createWithResource(this, icon),
        label,
        label,
        PendingIntent.getService(
            this,
            command.hashCode(),
            Intent(this, NetCastService::class.java).putExtra("command", command),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    )
}
