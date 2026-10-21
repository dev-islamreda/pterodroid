package com.example.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.ErrorCategory
import com.example.data.api.PteroApiException
import com.example.data.api.PteroErrorParser
import com.example.data.model.ServerAttributes
import com.example.data.model.ServerStatsAttributes
import com.example.data.repository.PteroRepository
import com.example.ui.UiState
import com.example.ui.components.BubbleController
import com.example.ui.components.BubbleType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ServerViewModel(
    private val repository: PteroRepository,
    val server: ServerAttributes
) : ViewModel() {

    private val _statsState = MutableStateFlow<UiState<ServerStatsAttributes>>(UiState.Loading)
    val statsState: StateFlow<UiState<ServerStatsAttributes>> = _statsState.asStateFlow()

    private val _serverDetails = MutableStateFlow<ServerAttributes>(server)
    val serverDetails: StateFlow<ServerAttributes> = _serverDetails.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _powerActionState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val powerActionState: StateFlow<UiState<String>> = _powerActionState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        startPollingResources()
    }

    fun startPollingResources() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val stats = repository.getServerResources(server.panelId, server.identifier)
                    _statsState.value = UiState.Success(stats)
                    delay(5000)
                } catch (e: CancellationException) {
                    // Coroutine was cancelled cleanly (e.g. pull-to-refresh started or screen left)
                    break
                } catch (e: PteroApiException) {
                    when (e) {
                        is PteroApiException.RateLimited -> {
                            val waitSeconds = e.retryAfterSeconds ?: 15
                            _statsState.value = UiState.Error(
                                message = e.message,
                                category = ErrorCategory.RATE_LIMITED,
                                statusCode = 429
                            )
                            delay(waitSeconds * 1000L)
                        }
                        is PteroApiException.Unauthorized -> {
                            _statsState.value = UiState.Error(
                                message = e.message,
                                category = ErrorCategory.UNAUTHORIZED,
                                statusCode = 401
                            )
                            break
                        }
                        is PteroApiException.Forbidden -> {
                            _statsState.value = UiState.Error(
                                message = e.message,
                                category = ErrorCategory.FORBIDDEN,
                                statusCode = 403
                            )
                            break
                        }
                        is PteroApiException.Timeout -> {
                            _statsState.value = UiState.Error(
                                message = e.message,
                                category = ErrorCategory.TIMEOUT
                            )
                            delay(8000)
                        }
                        is PteroApiException.Offline -> {
                            _statsState.value = UiState.Error(
                                message = e.message,
                                category = ErrorCategory.OFFLINE
                            )
                            delay(8000)
                        }
                        is PteroApiException.ServerError -> {
                            _statsState.value = UiState.Error(
                                message = e.message,
                                category = ErrorCategory.SERVER_ERROR,
                                statusCode = e.statusCode
                            )
                            delay(10000)
                        }
                        else -> {
                            _statsState.value = UiState.Error(
                                message = e.message,
                                category = e.category,
                                statusCode = e.statusCode
                            )
                            delay(6000)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) break
                    val parsed = PteroErrorParser.parse(e)
                    _statsState.value = UiState.Error(
                        message = parsed.message,
                        category = parsed.category,
                        statusCode = parsed.statusCode
                    )
                    delay(8000)
                }
            }
        }
    }

    fun stopPollingResources() {
        pollingJob?.cancel()
    }

    /**
     * Manual pull-to-refresh:
     * 1. Fetches server resources immediately and updates statsState.
     * 2. Reloads server details (name, allocations, status) from the panel.
     * 3. Guarantees ~600ms minimum display time so quick refreshes don't flicker.
     * 4. Restarts the 5-second resource polling timer so next automatic update doesn't clash.
     * 5. If refresh fails, shows error in-app bubble and keeps old data on screen.
     * 6. Ignores pulls while a power action is in progress.
     */
    fun refresh() {
        // 6. Ignore pulls while a power action is in progress
        if (_powerActionState.value is UiState.Loading) {
            return
        }

        // 5. If device is offline, stop indicator immediately and show offline bubble
        if (BubbleController.isOffline()) {
            BubbleController.show("You're offline. Check your internet connection.", BubbleType.OFFLINE)
            return
        }

        if (_isRefreshing.value) return
        _isRefreshing.value = true

        // 4. Do not start extra polling loops: cancel current polling loop
        stopPollingResources()

        viewModelScope.launch {
            val minDelay = async { delay(600L) }

            var statsResult: ServerStatsAttributes? = null
            var statsError: Throwable? = null
            var detailsResult: ServerAttributes? = null
            var detailsError: Throwable? = null

            val statsDeferred = async {
                try {
                    repository.getServerResources(server.panelId, server.identifier)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    statsError = e
                    null
                }
            }

            val detailsDeferred = async {
                try {
                    repository.getServerDetails(server.panelId, server.identifier)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    detailsError = e
                    null
                }
            }

            try {
                statsResult = statsDeferred.await()
                detailsResult = detailsDeferred.await()
                minDelay.await() // Ensure minimum 600ms display
            } catch (e: CancellationException) {
                _isRefreshing.value = false
                startPollingResources()
                return@launch
            }

            _isRefreshing.value = false

            // Update on success
            if (statsResult != null) {
                _statsState.value = UiState.Success(statsResult)
            }
            if (detailsResult != null) {
                _serverDetails.value = detailsResult
            }

            // Error handling: if either call failed and is not cancelled, show error as in-app bubble and keep old data
            val failure = statsError ?: detailsError
            if (failure != null && failure !is CancellationException) {
                try {
                    val parsed = PteroErrorParser.parse(failure)
                    BubbleController.show(parsed.message, BubbleType.ERROR)
                } catch (_: CancellationException) {
                }
            }

            // 4. Restart the 5-second polling timer
            startPollingResources()
        }
    }

    fun sendPowerSignal(signal: String) {
        viewModelScope.launch {
            _powerActionState.value = UiState.Loading
            try {
                val success = repository.sendPowerSignal(server.panelId, server.identifier, signal)
                if (success) {
                    _powerActionState.value = UiState.Success("Signal '$signal' sent successfully.")
                    delay(1200)
                    try {
                        val stats = repository.getServerResources(server.panelId, server.identifier)
                        _statsState.value = UiState.Success(stats)
                    } catch (_: Exception) {
                    }
                } else {
                    _powerActionState.value = UiState.Error(
                        message = "Panel rejected power signal '$signal'."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PteroApiException) {
                _powerActionState.value = UiState.Error(
                    message = e.message,
                    category = e.category,
                    statusCode = e.statusCode
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val parsed = PteroErrorParser.parse(e)
                _powerActionState.value = UiState.Error(
                    message = parsed.message,
                    category = parsed.category,
                    statusCode = parsed.statusCode
                )
            }
        }
    }

    fun clearPowerFeedback() {
        _powerActionState.value = UiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        stopPollingResources()
    }
}
