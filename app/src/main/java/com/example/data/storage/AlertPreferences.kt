package com.example.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.api.ApiClientFactory
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.alertDataStore: DataStore<Preferences> by preferencesDataStore(name = "pterodroid_alert_prefs")

@JsonClass(generateAdapter = false)
data class AlertSettings(
    val alertsEnabled: Boolean = true,
    val alertServerDown: Boolean = true,
    val alertServerRecovered: Boolean = true,
    val alertHighUsage: Boolean = true,
    val alertPanelUnreachable: Boolean = true,
    val cpuThreshold: Int = 90,
    val ramThreshold: Int = 90,
    val diskThreshold: Int = 90
)

@JsonClass(generateAdapter = false)
data class ServerAlertState(
    val lastState: String? = null,
    val lastDownAlertTimestamp: Long = 0L,
    val lastRecoveredAlertTimestamp: Long = 0L,
    val lastCpuAlertTimestamp: Long = 0L,
    val lastRamAlertTimestamp: Long = 0L,
    val lastDiskAlertTimestamp: Long = 0L
)

@JsonClass(generateAdapter = false)
data class PanelAlertState(
    val lastReachable: Boolean = true,
    val lastUnreachableAlertTimestamp: Long = 0L
)

class AlertPreferences(private val context: Context) {

    companion object {
        val KEY_ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val KEY_ALERT_SERVER_DOWN = booleanPreferencesKey("alert_server_down")
        val KEY_ALERT_SERVER_RECOVERED = booleanPreferencesKey("alert_server_recovered")
        val KEY_ALERT_HIGH_USAGE = booleanPreferencesKey("alert_high_usage")
        val KEY_ALERT_PANEL_UNREACHABLE = booleanPreferencesKey("alert_panel_unreachable")
        val KEY_THRESHOLD_CPU = intPreferencesKey("threshold_cpu")
        val KEY_THRESHOLD_RAM = intPreferencesKey("threshold_ram")
        val KEY_THRESHOLD_DISK = intPreferencesKey("threshold_disk")
        val KEY_SERVERS_STATE_JSON = stringPreferencesKey("servers_alert_state_json")
        val KEY_PANELS_STATE_JSON = stringPreferencesKey("panels_alert_state_json")

        const val COOLDOWN_MILLIS = 3600_000L // 1 hour cooldown per server and alert type
    }

    private val serverStateMapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        ServerAlertState::class.java
    )
    private val serverStateAdapter by lazy {
        ApiClientFactory.moshi.adapter<Map<String, ServerAlertState>>(serverStateMapType)
    }

    private val panelStateMapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        PanelAlertState::class.java
    )
    private val panelStateAdapter by lazy {
        ApiClientFactory.moshi.adapter<Map<String, PanelAlertState>>(panelStateMapType)
    }

    val alertSettings: Flow<AlertSettings> = context.alertDataStore.data.map { prefs ->
        AlertSettings(
            alertsEnabled = prefs[KEY_ALERTS_ENABLED] ?: true,
            alertServerDown = prefs[KEY_ALERT_SERVER_DOWN] ?: true,
            alertServerRecovered = prefs[KEY_ALERT_SERVER_RECOVERED] ?: true,
            alertHighUsage = prefs[KEY_ALERT_HIGH_USAGE] ?: true,
            alertPanelUnreachable = prefs[KEY_ALERT_PANEL_UNREACHABLE] ?: true,
            cpuThreshold = prefs[KEY_THRESHOLD_CPU] ?: 90,
            ramThreshold = prefs[KEY_THRESHOLD_RAM] ?: 90,
            diskThreshold = prefs[KEY_THRESHOLD_DISK] ?: 90
        )
    }

    suspend fun setAlertsEnabled(enabled: Boolean) {
        context.alertDataStore.edit { it[KEY_ALERTS_ENABLED] = enabled }
    }

    suspend fun setAlertServerDown(enabled: Boolean) {
        context.alertDataStore.edit { it[KEY_ALERT_SERVER_DOWN] = enabled }
    }

    suspend fun setAlertServerRecovered(enabled: Boolean) {
        context.alertDataStore.edit { it[KEY_ALERT_SERVER_RECOVERED] = enabled }
    }

    suspend fun setAlertHighUsage(enabled: Boolean) {
        context.alertDataStore.edit { it[KEY_ALERT_HIGH_USAGE] = enabled }
    }

    suspend fun setAlertPanelUnreachable(enabled: Boolean) {
        context.alertDataStore.edit { it[KEY_ALERT_PANEL_UNREACHABLE] = enabled }
    }

    suspend fun setCpuThreshold(threshold: Int) {
        context.alertDataStore.edit { it[KEY_THRESHOLD_CPU] = threshold.coerceIn(10, 100) }
    }

    suspend fun setRamThreshold(threshold: Int) {
        context.alertDataStore.edit { it[KEY_THRESHOLD_RAM] = threshold.coerceIn(10, 100) }
    }

    suspend fun setDiskThreshold(threshold: Int) {
        context.alertDataStore.edit { it[KEY_THRESHOLD_DISK] = threshold.coerceIn(10, 100) }
    }

    suspend fun getServerAlertStates(): Map<String, ServerAlertState> {
        return try {
            val json = context.alertDataStore.data.map { it[KEY_SERVERS_STATE_JSON] }.kotlinx_first()
            if (!json.isNullOrBlank()) {
                serverStateAdapter.fromJson(json) ?: emptyMap()
            } else {
                emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun saveServerAlertStates(states: Map<String, ServerAlertState>) {
        context.alertDataStore.edit { prefs ->
            prefs[KEY_SERVERS_STATE_JSON] = serverStateAdapter.toJson(states)
        }
    }

    suspend fun getPanelAlertStates(): Map<String, PanelAlertState> {
        return try {
            val json = context.alertDataStore.data.map { it[KEY_PANELS_STATE_JSON] }.kotlinx_first()
            if (!json.isNullOrBlank()) {
                panelStateAdapter.fromJson(json) ?: emptyMap()
            } else {
                emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun savePanelAlertStates(states: Map<String, PanelAlertState>) {
        context.alertDataStore.edit { prefs ->
            prefs[KEY_PANELS_STATE_JSON] = panelStateAdapter.toJson(states)
        }
    }

    suspend fun clearAllAlertData() {
        context.alertDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private suspend fun <T> Flow<T>.kotlinx_first(): T {
        var result: T? = null
        var found = false
        this.collect {
            result = it
            found = true
            throw CancellationBypassException()
        }
        if (!found) throw NoSuchElementException("Flow was empty")
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private class CancellationBypassException : RuntimeException()
}
