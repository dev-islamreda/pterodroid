package com.example.ui.console

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiCrashAnalyzer
import com.example.data.ai.LogAnalysisResult
import com.example.data.api.PteroApiException
import com.example.data.api.PteroErrorParser
import com.example.data.model.ResourceStats
import com.example.data.model.ServerAttributes
import com.example.data.repository.PteroRepository
import com.example.data.websocket.DaemonEvent
import com.example.data.websocket.WsConnectionState
import com.example.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

sealed interface GeminiAnalysisState {
    object Idle : GeminiAnalysisState
    object Thinking : GeminiAnalysisState
    data class Success(val result: LogAnalysisResult) : GeminiAnalysisState
    data class Error(val message: String) : GeminiAnalysisState
}

data class PowerConfirmationDialogData(
    val signal: String,
    val isDestructive: Boolean
)

class ConsoleViewModel(
    val repository: PteroRepository,
    val server: ServerAttributes
) : ViewModel() {

    private val maxLines = 500
    private val logBuffer = ArrayDeque<String>(maxLines)

    private val _consoleLogs = MutableStateFlow<List<String>>(emptyList())
    val consoleLogs: StateFlow<List<String>> = _consoleLogs.asStateFlow()

    val connectionState: StateFlow<WsConnectionState> = repository.daemonWebsocket.connectionState

    private val _connectionStatusText = MutableStateFlow("Connecting to daemon…")
    val connectionStatusText: StateFlow<String> = _connectionStatusText.asStateFlow()
    val connectionStatus: StateFlow<String> get() = connectionStatusText
    val isWsConnected: StateFlow<Boolean> get() = repository.daemonWebsocket.isConnected

    private val _serverStatus = MutableStateFlow(server.status ?: "offline")
    val serverStatus: StateFlow<String> = _serverStatus.asStateFlow()

    private val _liveStats = MutableStateFlow<ResourceStats?>(null)
    val liveStats: StateFlow<ResourceStats?> = _liveStats.asStateFlow()

    private val _autoScroll = MutableStateFlow(true)
    val autoScroll: StateFlow<Boolean> = _autoScroll.asStateFlow()

    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    private val _isSendingCommand = MutableStateFlow(false)
    val isSendingCommand: StateFlow<Boolean> = _isSendingCommand.asStateFlow()

    private val _powerActionState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val powerActionState: StateFlow<UiState<String>> = _powerActionState.asStateFlow()

    private val _pendingPowerDialog = MutableStateFlow<PowerConfirmationDialogData?>(null)
    val pendingPowerDialog: StateFlow<PowerConfirmationDialogData?> = _pendingPowerDialog.asStateFlow()

    private val _geminiState = MutableStateFlow<GeminiAnalysisState>(GeminiAnalysisState.Idle)
    val geminiState: StateFlow<GeminiAnalysisState> = _geminiState.asStateFlow()

    private var eventListenJob: Job? = null
    private var lastSentCommandTime = 0L
    private var lastSentCommandText = ""

    val canSendCommands: Boolean = server.canSendCommands()
    val canControlPower: Boolean = server.canControlPower()

    init {
        startWebsocketConnection()
        listenToEvents()
    }

    fun startWebsocketConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _connectionStatusText.value = "Connecting to daemon stream (${server.panelLabel.ifBlank { "Panel" }})…"
                val authData = repository.getWebsocketAuth(server.panelId, server.identifier)
                repository.connectServerWebsocket(server.panelId, server.identifier, authData)
            } catch (e: Exception) {
                _connectionStatusText.value = "Offline (${e.localizedMessage ?: "Daemon unreachable"})"
                appendLog("[SYSTEM] Daemon connection status: ${e.message}")
            }
        }
    }

    fun retryConnection() {
        appendLog("[SYSTEM] Reconnecting to server websocket…")
        repository.daemonWebsocket.retryConnection()
    }

    private fun listenToEvents() {
        eventListenJob?.cancel()
        eventListenJob = viewModelScope.launch {
            repository.daemonWebsocket.events.collect { event ->
                when (event) {
                    is DaemonEvent.Connected -> {
                        _connectionStatusText.value = "Connected to daemon"
                        appendLog("[DAEMON] Connection established. Authenticating…")
                    }
                    is DaemonEvent.Authenticating -> {
                        _connectionStatusText.value = "Authenticating with daemon…"
                    }
                    is DaemonEvent.AuthSuccess -> {
                        _connectionStatusText.value = "Authenticated & Streaming"
                        appendLog("[DAEMON] Authentication successful. Ready for commands.")
                    }
                    is DaemonEvent.StatusChange -> {
                        _serverStatus.value = event.status
                        appendLog("[STATUS] Server state: ${event.status.uppercase()}")
                    }
                    is DaemonEvent.ConsoleOutput -> {
                        val cleaned = TerminalLogFormatter.cleanAnsi(event.line)
                        if (cleaned.isNotBlank()) {
                            appendLog(cleaned)
                        }
                    }
                    is DaemonEvent.StatsUpdate -> {
                        _liveStats.value = event.stats
                        if (event.stats.state != null) {
                            _serverStatus.value = event.stats.state
                        }
                    }
                    is DaemonEvent.Disconnected -> {
                        _connectionStatusText.value = "Disconnected"
                        appendLog("[DAEMON] Socket disconnected: ${event.reason}")
                    }
                    is DaemonEvent.Error -> {
                        _connectionStatusText.value = "Socket Error"
                        appendLog("[DAEMON ERROR] ${event.message}")
                    }
                    is DaemonEvent.AuthFailed -> {
                        _connectionStatusText.value = "Authentication Failed"
                        appendLog("[DAEMON AUTH ERROR] ${event.reason}")
                    }
                    is DaemonEvent.TokenExpiring -> {
                        appendLog("[DAEMON] Auth token expiring, refreshing credentials…")
                        refreshToken()
                    }
                    is DaemonEvent.TokenExpired -> {
                        appendLog("[DAEMON] Auth token expired. Acquiring fresh token…")
                        startWebsocketConnection()
                    }
                    is DaemonEvent.Reconnecting -> {
                        _connectionStatusText.value = "Reconnecting (${event.attempt})…"
                        appendLog("[DAEMON] Connection lost. Reconnecting in ${event.delayMs / 1000}s (attempt #${event.attempt})…")
                    }
                }
            }
        }
    }

    private fun refreshToken() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newAuth = repository.getWebsocketAuth(server.panelId, server.identifier)
                repository.daemonWebsocket.sendAuth(newAuth.token)
                appendLog("[DAEMON] Refreshed authentication token submitted.")
            } catch (e: Exception) {
                appendLog("[SYSTEM] Failed to refresh token from panel: ${e.message}")
            }
        }
    }

    private fun appendLog(line: String) {
        val config = repository.getPanelConfig(server.panelId)
        val sanitized = PteroErrorParser.redactCredentials(line, config?.apiToken)
        val clean = sanitized.trimEnd('\r', '\n')
        if (clean.isBlank()) return
        synchronized(logBuffer) {
            if (logBuffer.size >= maxLines) {
                logBuffer.removeFirst()
            }
            logBuffer.addLast(clean)
            _consoleLogs.value = logBuffer.toList()
        }
    }

    fun onCommandChange(cmd: String) {
        _commandInput.value = cmd
    }

    fun sendCurrentCommand() {
        val cmd = _commandInput.value.trim()
        if (cmd.isBlank()) return
        if (!canSendCommands) {
            appendLog("[PERMISSION DENIED] You do not have permission to execute console commands.")
            return
        }
        val now = System.currentTimeMillis()
        if (_isSendingCommand.value || (now - lastSentCommandTime < 450 && cmd == lastSentCommandText)) {
            return
        }
        _isSendingCommand.value = true
        lastSentCommandTime = now
        lastSentCommandText = cmd
        appendLog("> $cmd")
        _commandInput.value = ""

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isWsAuth = repository.daemonWebsocket.connectionState.value is WsConnectionState.Authenticated
                if (isWsAuth) {
                    repository.daemonWebsocket.sendCommand(cmd)
                } else {
                    val ok = repository.sendConsoleCommand(server.panelId, server.identifier, cmd)
                    if (!ok) {
                        appendLog("[ERROR] Server panel rejected command.")
                    }
                }
            } catch (e: Exception) {
                appendLog("[ERROR] Failed to send command: ${e.localizedMessage}")
            } finally {
                delay(250)
                _isSendingCommand.value = false
            }
        }
    }

    fun requestPowerAction(signal: String) {
        if (!server.canControlPower(signal)) {
            appendLog("[PERMISSION DENIED] You lack permission to perform power action '$signal'.")
            _powerActionState.value = UiState.Error("You do not have permission to execute '$signal'.")
            return
        }
        val isDestructive = signal.equals("restart", ignoreCase = true) ||
                signal.equals("stop", ignoreCase = true) ||
                signal.equals("kill", ignoreCase = true)

        if (isDestructive) {
            _pendingPowerDialog.value = PowerConfirmationDialogData(signal, isDestructive = true)
        } else {
            executePowerAction(signal)
        }
    }

    fun confirmPowerAction(signal: String) {
        _pendingPowerDialog.value = null
        executePowerAction(signal)
    }

    fun dismissPowerDialog() {
        _pendingPowerDialog.value = null
    }

    private fun executePowerAction(signal: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _powerActionState.value = UiState.Loading
            appendLog("[SYSTEM] Sending power action: ${signal.uppercase()}…")
            try {
                val isWsAuth = repository.daemonWebsocket.connectionState.value is WsConnectionState.Authenticated
                if (isWsAuth) {
                    repository.daemonWebsocket.setPowerState(signal)
                }
                val success = repository.sendPowerSignal(server.panelId, server.identifier, signal)
                if (success) {
                    _powerActionState.value = UiState.Success("Signal '$signal' accepted by server.")
                    appendLog("[SYSTEM] Power signal '${signal.uppercase()}' acknowledged.")
                } else {
                    _powerActionState.value = UiState.Error("Server rejected power signal '$signal'.")
                    appendLog("[ERROR] Server rejected power signal '$signal'.")
                }
            } catch (e: PteroApiException) {
                _powerActionState.value = UiState.Error(e.message, e.category, e.statusCode)
                appendLog("[ERROR] Power action failed: ${e.message}")
            } catch (e: Exception) {
                _powerActionState.value = UiState.Error(e.message ?: "Failed to send signal.")
                appendLog("[ERROR] Power action failed: ${e.localizedMessage}")
            }
        }
    }

    fun clearPowerFeedback() {
        _powerActionState.value = UiState.Idle
    }

    fun toggleAutoScroll() {
        _autoScroll.value = !_autoScroll.value
    }

    fun clearLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
            _consoleLogs.value = emptyList()
        }
    }

    fun getLogsAsPlainText(): String {
        return synchronized(logBuffer) {
            _consoleLogs.value.joinToString("\n") { TerminalLogFormatter.cleanAnsi(it) }
        }
    }

    fun getLogsAsJson(): String {
        val cleanLogs = synchronized(logBuffer) {
            _consoleLogs.value.map { TerminalLogFormatter.cleanAnsi(it) }
        }
        val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
        return com.example.data.api.ApiClientFactory.moshi
            .adapter<List<String>>(type)
            .indent("  ")
            .toJson(cleanLogs)
    }

    fun downloadLogsAsJson(context: Context): Result<String> {
        return try {
            val jsonString = getLogsAsJson()
            val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
            val fileName = "console_logs_${server.panelId}_${server.identifier}_${System.currentTimeMillis()}.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = try {
                    context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                } catch (_: Exception) {
                    null
                }
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(jsonBytes)
                    }
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: context.filesDir
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val targetFile = File(downloadsDir, fileName)
                    FileOutputStream(targetFile).use { it.write(jsonBytes) }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, fileName)
                FileOutputStream(targetFile).use { it.write(jsonBytes) }
            }
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun analyzeWithGemini() {
        viewModelScope.launch {
            _geminiState.value = GeminiAnalysisState.Thinking
            val currentLogs = _consoleLogs.value
            val result = GeminiCrashAnalyzer.analyzeConsoleLogs(
                serverName = server.name,
                recentLogs = currentLogs
            )
            if (result.isSuccess) {
                _geminiState.value = GeminiAnalysisState.Success(result)
            } else {
                _geminiState.value = GeminiAnalysisState.Error(result.error ?: "Analysis failed.")
            }
        }
    }

    fun dismissGeminiAnalysis() {
        _geminiState.value = GeminiAnalysisState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        eventListenJob?.cancel()
        repository.daemonWebsocket.disconnect()
    }
}
