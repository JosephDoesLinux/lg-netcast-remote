package dev.netcastremote.integrate

import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionService
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appsearch.GenericDocument
import android.content.pm.SigningInfo
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import dev.netcastremote.SessionRuntime
import dev.netcastremote.protocol.MatterCluster
import dev.netcastremote.protocol.MatterInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Tools a paired 2012 LG NetCast television exposes to Gemini and other on-device agents.
 * The phone and the television must be on the same Wi-Fi, and the television must already be paired.
 * Volume changes are stepped because the television has no set-level command.
 * Power-on uses Wake-on-LAN, which needs the TV's MAC address saved in the app.
 */
class TvFunctionService : AppFunctionService() {
    /**
     * Turn the paired television on or off.
     * Pass on=true to wake it over Wi-Fi, on=false to send power while it is awake.
     */
    override fun onExecuteFunction(
        request: ExecuteAppFunctionRequest,
        callingPackage: String,
        callingPackageSigningInfo: SigningInfo,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>,
    ) {
        val params = request.parameters
        CoroutineScope(Dispatchers.IO).launch {
            val repo = SessionRuntime.repository
            repo.ensureSession()
            val result = when (request.functionIdentifier) {
                "power" -> {
                    val on = if (params.getPropertyNames().contains("on")) params.getPropertyBoolean("on") else null
                    repo.matter(MatterInvoke(MatterCluster.OnOff, if (on == null) "toggle" else if (on) "on" else "off"))
                }
                "mute" -> repo.matter(MatterInvoke(MatterCluster.KeypadInput, "SendKey", mapOf("key" to "MUTE"))).let { "muted" }
                "setVolume" -> {
                    val level = params.getPropertyLong("level").toInt()
                    repo.matter(MatterInvoke(MatterCluster.LevelControl, "MoveToLevel", mapOf("level" to level.toString())))
                }
                "channelUp" -> repo.matter(MatterInvoke(MatterCluster.Channel, "up"))
                "channelDown" -> repo.matter(MatterInvoke(MatterCluster.Channel, "down"))
                "tune" -> repo.matter(
                    MatterInvoke(
                        MatterCluster.Channel,
                        "change",
                        mapOf(
                            "number" to params.string("number"),
                            "name" to params.string("name"),
                        ),
                    ),
                )
                "launchApp" -> repo.matter(MatterInvoke(MatterCluster.MediaPlayback, "launch", mapOf("name" to params.string("name"))))
                "runMacro" -> repo.matter(MatterInvoke(MatterCluster.MediaPlayback, "macro", mapOf("name" to params.string("name"))))
                else -> {
                    callback.onError(AppFunctionException(AppFunctionException.ERROR_FUNCTION_NOT_FOUND, request.functionIdentifier))
                    return@launch
                }
            }
            callback.onResult(
                ExecuteAppFunctionResponse(
                    GenericDocument.Builder<GenericDocument.Builder<*>>("dev.netcastremote", request.functionIdentifier, "result")
                        .setPropertyString(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE, result)
                        .build(),
                ),
            )
        }
    }
}

private fun GenericDocument.string(name: String): String =
    if (getPropertyNames().contains(name)) getPropertyString(name).orEmpty() else ""
