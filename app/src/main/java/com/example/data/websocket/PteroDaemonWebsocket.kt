package com.example.data.websocket

import android.util.Log
import com.example.data.api.ApiClientFactory
import com.example.data.model.ResourceStats
import com.example.data.model.WebsocketAuthData
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

sealed class DaemonEvent {
    object Connected : DaemonEvent()
    object Authenticating : DaemonEvent()
    object AuthSuccess : DaemonEvent()
    data class AuthFailed(val reason: String) : DaemonEvent()
    data class StatusChange(val status: String) : DaemonEvent()
    data class ConsoleOutput(val line: String) : DaemonEvent()
    data class StatsUpdate(val stats: ResourceStats) : DaemonEvent()
    data class Disconnected(val reason: String) : DaemonEvent()
    data class Error(val message: String) : DaemonEvent()
    object TokenExpiring : DaemonEvent()
    object TokenExpired : DaemonEvent()
    data class Reconnecting(val attempt: Int, val delayMs: Long) : DaemonEvent()
}

sealed class WsConnectionState {
    object Disconnected : WsConnectionState()
    object Connecting : WsConnectionState()
    object Connected : WsConnectionState()
    object Authenticating : WsConnectionState()
    object Authenticated : WsConnectionState()
    data class Reconnecting(val attempt: Int) : WsConnectionState()
    data class AuthFailed(val reason: String) : WsConnectionState()
    data class Error(val message: String) : WsConnectionState()
}

@JsonClass(generateAdapter = false)
data class WsMessage(
    @Json(name = "event") val event: String,
    @Json(name = "args") val args: List<String?>? = null
)

class PteroDaemonWebsocket(
    private val scope: CoroutineScope
) {
    private val tag = "PteroDaemonWs"
    private var webSocket: WebSocket? = null
    private var currentAuthData: WebsocketAuthData? = null
    private var currentPanelUrl: String = ""
    private var isIntentionalClose = false
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private val maxReconnectAttempts = 5

    var onTokenRefreshNeeded: (suspend () -> WebsocketAuthData?)? = null

    private val _events = MutableSharedFlow<DaemonEvent>(replay = 50)
    val events: SharedFlow<DaemonEvent> = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow<WsConnectionState>(WsConnectionState.Disconnected)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _currentStatus = MutableStateFlow("offline")
    val currentStatus: StateFlow<String> = _currentStatus.asStateFlow()

    private val wsAdapter by lazy {
        ApiClientFactory.moshi.adapter(WsMessage::class.java)
    }

    private val resourceAdapter by lazy {
        ApiClientFactory.moshi.adapter(ResourceStats::class.java)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun connect(authData: WebsocketAuthData, panelUrl: String) {
        disconnect()
        currentAuthData = authData
        currentPanelUrl = panelUrl
        isIntentionalClose = false
        reconnectAttempt = 0
        openSocket(authData.socket, authData.token, panelUrl)
    }

    private fun openSocket(socketUrl: String, token: String, panelUrl: String) {
        try {
            _connectionState.value = WsConnectionState.Connecting
            val origin = if (panelUrl.endsWith("/")) panelUrl.dropLast(1) else panelUrl
            val request = Request.Builder()
                .url(socketUrl)
                .header("Origin", origin)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Pterodroid/1.0")
                .build()

            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(tag, "WebSocket successfully opened to $socketUrl")
                    _isConnected.value = true
                    _connectionState.value = WsConnectionState.Connected
                    scope.launch {
                        _events.emit(DaemonEvent.Connected)
                    }
                    sendAuth(token)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(tag, "WebSocket closing: $code / $reason")
                    _isConnected.value = false
                    _connectionState.value = WsConnectionState.Disconnected
                    scope.launch {
                        _events.emit(DaemonEvent.Disconnected(reason))
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(tag, "WebSocket closed: $code / $reason")
                    _isConnected.value = false
                    _connectionState.value = WsConnectionState.Disconnected
                    if (!isIntentionalClose) {
                        scheduleReconnect(isAuthError = (code == 4001 || code == 4003))
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(tag, "WebSocket failure: ${t.message}")
                    _isConnected.value = false
                    val errorMsg = t.message ?: "Connection lost"
                    _connectionState.value = WsConnectionState.Error(errorMsg)
                    scope.launch {
                        _events.emit(DaemonEvent.Error(errorMsg))
                    }
                    if (!isIntentionalClose) {
                        scheduleReconnect(isAuthError = (response?.code == 401 || response?.code == 403))
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(tag, "Failed to start websocket: ${e.message}")
            _connectionState.value = WsConnectionState.Error(e.message ?: "Failed to open socket")
            scope.launch {
                _events.emit(DaemonEvent.Error(e.message ?: "Failed to open socket"))
            }
        }
    }

    private fun handleIncomingMessage(rawText: String) {
        try {
            val msg = wsAdapter.fromJson(rawText) ?: return
            when (msg.event) {
                "auth success" -> {
                    reconnectAttempt = 0
                    _connectionState.value = WsConnectionState.Authenticated
                    scope.launch {
                        _events.emit(DaemonEvent.AuthSuccess)
                    }
                    requestLogs()
                    requestStats()
                }
                "status" -> {
                    val status = msg.args?.firstOrNull() ?: "offline"
                    _currentStatus.value = status
                    scope.launch {
                        _events.emit(DaemonEvent.StatusChange(status))
                    }
                }
                "console output" -> {
                    val line = msg.args?.firstOrNull() ?: ""
                    scope.launch {
                        _events.emit(DaemonEvent.ConsoleOutput(line))
                    }
                }
                "stats" -> {
                    val statsJson = msg.args?.firstOrNull()
                    if (statsJson != null) {
                        try {
                            val stats = resourceAdapter.fromJson(statsJson)
                            if (stats != null) {
                                if (stats.state != null) {
                                    _currentStatus.value = stats.state
                                }
                                scope.launch {
                                    _events.emit(DaemonEvent.StatsUpdate(stats))
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(tag, "Failed to parse daemon stats JSON: ${e.message}")
                        }
                    }
                }
                "token expiring" -> {
                    scope.launch {
                        _events.emit(DaemonEvent.TokenExpiring)
                    }
                }
                "token expired" -> {
                    scope.launch {
                        _events.emit(DaemonEvent.TokenExpired)
                    }
                    scheduleReconnect(isAuthError = true)
                }
                "jwt error" -> {
                    val reason = msg.args?.firstOrNull() ?: "JWT Authentication failed"
                    _connectionState.value = WsConnectionState.AuthFailed(reason)
                    scope.launch {
                        _events.emit(DaemonEvent.AuthFailed(reason))
                    }
                    scheduleReconnect(isAuthError = true)
                }
                "daemon error" -> {
                    val message = msg.args?.firstOrNull() ?: "Daemon internal error"
                    scope.launch {
                        _events.emit(DaemonEvent.Error(message))
                    }
                }
                else -> {
                    Log.d(tag, "Received event: ${msg.event}")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse ws message: ${e.message}")
        }
    }

    fun sendAuth(token: String) {
        _connectionState.value = WsConnectionState.Authenticating
        scope.launch {
            _events.emit(DaemonEvent.Authenticating)
        }
        val authMsg = WsMessage(
            event = "auth",
            args = listOf(token)
        )
        sendMessage(authMsg)
    }

    fun sendCommand(command: String) {
        val cmdMsg = WsMessage(
            event = "send command",
            args = listOf(command)
        )
        sendMessage(cmdMsg)
    }

    fun setPowerState(signal: String) {
        val stateMsg = WsMessage(
            event = "set state",
            args = listOf(signal)
        )
        sendMessage(stateMsg)
    }

    fun requestLogs() {
        val logMsg = WsMessage(
            event = "send logs",
            args = null
        )
        sendMessage(logMsg)
    }

    fun requestStats() {
        val statsMsg = WsMessage(
            event = "send stats",
            args = null
        )
        sendMessage(statsMsg)
    }

    private fun sendMessage(msg: WsMessage) {
        val json = wsAdapter.toJson(msg)
        webSocket?.send(json)
    }

    private fun scheduleReconnect(isAuthError: Boolean = false) {
        if (isIntentionalClose) return
        if (reconnectAttempt >= maxReconnectAttempts) {
            _connectionState.value = WsConnectionState.Error("Max reconnect attempts reached. Tap Reconnect to retry.")
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            reconnectAttempt++
            val delayMs = (1500L * (1L shl (reconnectAttempt - 1))).coerceAtMost(20000L)
            _connectionState.value = WsConnectionState.Reconnecting(reconnectAttempt)
            _events.emit(DaemonEvent.Reconnecting(reconnectAttempt, delayMs))
            delay(delayMs)
            if (!isIntentionalClose) {
                if (isAuthError && onTokenRefreshNeeded != null) {
                    val freshAuth = onTokenRefreshNeeded?.invoke()
                    if (freshAuth != null) {
                        currentAuthData = freshAuth
                    }
                }
                val auth = currentAuthData
                if (auth != null) {
                    openSocket(auth.socket, auth.token, currentPanelUrl)
                }
            }
        }
    }

    fun retryConnection() {
        isIntentionalClose = false
        reconnectAttempt = 0
        reconnectJob?.cancel()
        scope.launch(Dispatchers.IO) {
            val freshAuth = onTokenRefreshNeeded?.invoke() ?: currentAuthData
            if (freshAuth != null) {
                currentAuthData = freshAuth
                openSocket(freshAuth.socket, freshAuth.token, currentPanelUrl)
            }
        }
    }

    fun disconnect() {
        isIntentionalClose = true
        reconnectJob?.cancel()
        reconnectAttempt = 0
        try {
            webSocket?.close(1000, "User closed console")
        } catch (_: Exception) {
        }
        webSocket = null
        _isConnected.value = false
        _connectionState.value = WsConnectionState.Disconnected
    }
}
