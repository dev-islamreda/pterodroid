package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.ui.components.BubbleController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworkMonitor(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val tag = "NetworkMonitor"
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialOnlineState())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var debounceJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isStarted = false

    private fun checkInitialOnlineState(): Boolean {
        val cm = connectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun start() {
        if (isStarted) return
        isStarted = true

        val initialOnline = checkInitialOnlineState()
        _isOnline.value = initialOnline
        if (!initialOnline) {
            BubbleController.setOfflineState(true)
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                evaluateCapabilities(network)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                handleStateChange(hasInternet && isValidated)
            }

            override fun onLost(network: Network) {
                handleStateChange(false)
            }

            override fun onUnavailable() {
                handleStateChange(false)
            }
        }

        try {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
        } catch (e: Exception) {
            Log.w(tag, "Failed to register default network callback: ${e.message}")
            try {
                connectivityManager?.registerNetworkCallback(request, networkCallback!!)
            } catch (ex: Exception) {
                Log.e(tag, "Failed to register fallback network callback: ${ex.message}")
            }
        }
    }

    private fun evaluateCapabilities(network: Network) {
        val caps = connectivityManager?.getNetworkCapabilities(network)
        val online = caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        handleStateChange(online)
    }

    private fun handleStateChange(targetOnline: Boolean) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            // Debounce 1.5 seconds to prevent network flapping
            delay(1500L)
            if (_isOnline.value != targetOnline) {
                _isOnline.value = targetOnline
                BubbleController.setOfflineState(!targetOnline)
            }
        }
    }

    fun stop() {
        debounceJob?.cancel()
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(tag, "Failed to unregister network callback: ${e.message}")
            }
        }
        networkCallback = null
        isStarted = false
    }
}
