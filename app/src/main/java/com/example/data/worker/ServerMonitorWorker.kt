package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.api.ApiClientFactory
import com.example.data.notifications.NotificationHelper
import com.example.data.storage.AlertPreferences
import com.example.data.storage.ConnectionPreferences
import com.example.data.storage.PanelAlertState
import com.example.data.storage.ServerAlertState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.TimeUnit

class ServerMonitorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val UNIQUE_PERIODIC_WORK = "pterodroid_server_monitor_work"
        private const val TAG = "ServerMonitorWorker"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<ServerMonitorWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            Log.d(TAG, "Periodic server monitoring work scheduled (15 min interval).")
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK)
            Log.d(TAG, "Periodic server monitoring work cancelled.")
        }

        fun runImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<ServerMonitorWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeRequest)
            Log.d(TAG, "Immediate server monitoring check enqueued.")
        }
    }

    override suspend fun doWork(): Result {
        // Skip check when signed out
        val currentUser = try {
            Firebase.auth.currentUser
        } catch (_: Exception) {
            null
        }
        if (currentUser == null) {
            Log.d(TAG, "User is signed out. Skipping server monitor check.")
            return Result.success()
        }

        val alertPrefs = AlertPreferences(applicationContext)
        val alertSettings = try {
            alertPrefs.alertSettings.first()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read alert settings: ${e.message}")
            return Result.success()
        }

        if (!alertSettings.alertsEnabled) {
            Log.d(TAG, "Alerts disabled by user. Skipping check.")
            return Result.success()
        }

        val connPrefs = ConnectionPreferences(applicationContext)
        val panels = try {
            connPrefs.savedConnections.first()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read saved connections: ${e.message}")
            emptyList()
        }

        if (panels.isEmpty()) {
            Log.d(TAG, "No panels saved. Skipping check.")
            return Result.success()
        }

        val serverStates = alertPrefs.getServerAlertStates().toMutableMap()
        val panelStates = alertPrefs.getPanelAlertStates().toMutableMap()
        val now = System.currentTimeMillis()
        val cooldown = AlertPreferences.COOLDOWN_MILLIS

        // Query each panel in parallel; one panel failing must NOT stop others
        coroutineScope {
            panels.map { panel ->
                async {
                    if (panel.panelUrl.isBlank() || panel.apiToken.isBlank()) return@async
                    try {
                        val client = ApiClientFactory.createClient(panel.panelUrl, panel.apiToken)
                        // Fetch servers with timeout
                        val serverListResponse = withTimeoutOrNull(15000L) {
                            client.listServers(page = 1)
                        } ?: throw IllegalStateException("Timeout reaching panel ${panel.label}")

                        // Panel succeeded
                        val prevPanelState = panelStates[panel.id]
                        if (prevPanelState != null && !prevPanelState.lastReachable) {
                            panelStates[panel.id] = prevPanelState.copy(lastReachable = true)
                        }

                        val servers = serverListResponse.data.map { it.attributes }

                        // Query resources of each server in parallel with timeout
                        servers.map { server ->
                            async {
                                val compositeKey = "${panel.id}:${server.identifier}"
                                try {
                                    val stats = withTimeoutOrNull(10000L) {
                                        client.getServerResources(server.identifier).attributes
                                    }
                                    val currentState = stats?.currentState ?: server.status ?: "offline"
                                    val isRunning = currentState.contains("running", ignoreCase = true)

                                    val existingState = serverStates[compositeKey] ?: ServerAlertState()
                                    val lastState = existingState.lastState
                                    var updatedState = existingState.copy(lastState = currentState)

                                    // 1. Server went offline / stopped / crashed (changed from running to not running)
                                    val wasRunning = lastState != null && lastState.contains("running", ignoreCase = true)
                                    if (wasRunning && !isRunning) {
                                        if (alertSettings.alertServerDown &&
                                            (now - existingState.lastDownAlertTimestamp >= cooldown || lastState != currentState)
                                        ) {
                                            val notifId = ("down_$compositeKey").hashCode()
                                            NotificationHelper.sendServerNotification(
                                                context = applicationContext,
                                                channelId = NotificationHelper.CHANNEL_SERVER_DOWN,
                                                title = "Server down",
                                                message = "${server.name} (${panel.label}) is $currentState",
                                                panelId = panel.id,
                                                serverIdentifier = server.identifier,
                                                notificationId = notifId
                                            )
                                            updatedState = updatedState.copy(lastDownAlertTimestamp = now)
                                        }
                                    }

                                    // 2. Server recovered and is running again
                                    val wasNotRunning = lastState != null && !lastState.contains("running", ignoreCase = true)
                                    if (wasNotRunning && isRunning) {
                                        if (alertSettings.alertServerRecovered &&
                                            (now - existingState.lastRecoveredAlertTimestamp >= cooldown || lastState != currentState)
                                        ) {
                                            val notifId = ("recovered_$compositeKey").hashCode()
                                            NotificationHelper.sendServerNotification(
                                                context = applicationContext,
                                                channelId = NotificationHelper.CHANNEL_RECOVERED,
                                                title = "Server recovered",
                                                message = "${server.name} (${panel.label}) is online and running",
                                                panelId = panel.id,
                                                serverIdentifier = server.identifier,
                                                notificationId = notifId
                                            )
                                            updatedState = updatedState.copy(lastRecoveredAlertTimestamp = now)
                                        }
                                    }

                                    // 3. High CPU, RAM or Disk above threshold
                                    if (stats != null && alertSettings.alertHighUsage) {
                                        // CPU
                                        val cpu = stats.resources.cpuAbsolute
                                        if (cpu >= alertSettings.cpuThreshold && (now - existingState.lastCpuAlertTimestamp >= cooldown)) {
                                            val notifId = ("cpu_$compositeKey").hashCode()
                                            NotificationHelper.sendServerNotification(
                                                context = applicationContext,
                                                channelId = NotificationHelper.CHANNEL_HIGH_USAGE,
                                                title = "High CPU usage",
                                                message = "${server.name} (${panel.label}) CPU usage: ${String.format(Locale.US, "%.1f", cpu)}% (Threshold: ${alertSettings.cpuThreshold}%)",
                                                panelId = panel.id,
                                                serverIdentifier = server.identifier,
                                                notificationId = notifId
                                            )
                                            updatedState = updatedState.copy(lastCpuAlertTimestamp = now)
                                        }

                                        // RAM
                                        val memLimitBytes = server.limits.memory * 1024L * 1024L
                                        val memBytes = stats.resources.memoryBytes
                                        if (memLimitBytes > 0) {
                                            val ramPercent = (memBytes.toDouble() / memLimitBytes.toDouble()) * 100.0
                                            if (ramPercent >= alertSettings.ramThreshold && (now - existingState.lastRamAlertTimestamp >= cooldown)) {
                                                val notifId = ("ram_$compositeKey").hashCode()
                                                NotificationHelper.sendServerNotification(
                                                    context = applicationContext,
                                                    channelId = NotificationHelper.CHANNEL_HIGH_USAGE,
                                                    title = "High RAM usage",
                                                    message = "${server.name} (${panel.label}) RAM usage: ${String.format(Locale.US, "%.1f", ramPercent)}% (Threshold: ${alertSettings.ramThreshold}%)",
                                                    panelId = panel.id,
                                                    serverIdentifier = server.identifier,
                                                    notificationId = notifId
                                                )
                                                updatedState = updatedState.copy(lastRamAlertTimestamp = now)
                                            }
                                        }

                                        // Disk
                                        val diskLimitBytes = server.limits.disk * 1024L * 1024L
                                        val diskBytes = stats.resources.diskBytes
                                        if (diskLimitBytes > 0) {
                                            val diskPercent = (diskBytes.toDouble() / diskLimitBytes.toDouble()) * 100.0
                                            if (diskPercent >= alertSettings.diskThreshold && (now - existingState.lastDiskAlertTimestamp >= cooldown)) {
                                                val notifId = ("disk_$compositeKey").hashCode()
                                                NotificationHelper.sendServerNotification(
                                                    context = applicationContext,
                                                    channelId = NotificationHelper.CHANNEL_HIGH_USAGE,
                                                    title = "High Disk usage",
                                                    message = "${server.name} (${panel.label}) Disk usage: ${String.format(Locale.US, "%.1f", diskPercent)}% (Threshold: ${alertSettings.diskThreshold}%)",
                                                    panelId = panel.id,
                                                    serverIdentifier = server.identifier,
                                                    notificationId = notifId
                                                )
                                                updatedState = updatedState.copy(lastDiskAlertTimestamp = now)
                                            }
                                        }
                                    }

                                    synchronized(serverStates) {
                                        serverStates[compositeKey] = updatedState
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed reading resources for server ${server.identifier}: ${e.message}")
                                }
                            }
                        }.awaitAll()

                    } catch (e: Exception) {
                        Log.e(TAG, "Panel check failed for ${panel.label}: ${e.message}")
                        // Whole panel unreachable or token rejected: send ONE notification per panel
                        if (alertSettings.alertPanelUnreachable) {
                            val existingPanel = panelStates[panel.id] ?: PanelAlertState()
                            val wasReachable = existingPanel.lastReachable
                            val lastAlert = existingPanel.lastUnreachableAlertTimestamp
                            if (wasReachable || (now - lastAlert >= cooldown)) {
                                val notifId = ("panel_${panel.id}").hashCode()
                                val cleanUrl = panel.panelUrl.removePrefix("https://").removePrefix("http://").removeSuffix("/")
                                NotificationHelper.sendPanelNotification(
                                    context = applicationContext,
                                    title = "Panel unreachable: ${panel.label}",
                                    message = "Cannot reach $cleanUrl or API token was rejected.",
                                    panelId = panel.id,
                                    notificationId = notifId
                                )
                                synchronized(panelStates) {
                                    panelStates[panel.id] = existingPanel.copy(
                                        lastReachable = false,
                                        lastUnreachableAlertTimestamp = now
                                    )
                                }
                            } else {
                                synchronized(panelStates) {
                                    panelStates[panel.id] = existingPanel.copy(lastReachable = false)
                                }
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        // Persist updated states
        alertPrefs.saveServerAlertStates(serverStates)
        alertPrefs.savePanelAlertStates(panelStates)

        return Result.success()
    }
}
