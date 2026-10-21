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
import com.example.data.model.SavedConnection
import com.example.data.security.KeystoreCrypto
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pteropilot_prefs")

class ConnectionPreferences(private val context: Context) {

    private val savedConnectionsKey = stringPreferencesKey("saved_connections_json")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val languageKey = stringPreferencesKey("app_language")
    private val aiEnabledKey = booleanPreferencesKey("ai_enabled")
    private val refreshIntervalKey = intPreferencesKey("refresh_interval_sec")

    private val savedListType = Types.newParameterizedType(List::class.java, SavedConnection::class.java)
    private val savedListAdapter by lazy {
        ApiClientFactory.moshi.adapter<List<SavedConnection>>(savedListType)
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[themeModeKey] ?: "DARK" }
    val appLanguage: Flow<String> = context.dataStore.data.map { it[languageKey] ?: "en" }
    val isAiEnabled: Flow<Boolean> = context.dataStore.data.map { it[aiEnabledKey] ?: true }
    val refreshIntervalSec: Flow<Int> = context.dataStore.data.map { it[refreshIntervalKey] ?: 10 }

    val savedConnections: Flow<List<SavedConnection>> = context.dataStore.data.map { prefs ->
        val json = prefs[savedConnectionsKey] ?: return@map emptyList<SavedConnection>()
        try {
            val list = savedListAdapter.fromJson(json) ?: emptyList()
            list.map { conn ->
                conn.copy(apiToken = KeystoreCrypto.decrypt(conn.apiToken))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveOrUpdateConnection(conn: SavedConnection) {
        context.dataStore.edit { prefs ->
            val encryptedToken = KeystoreCrypto.encrypt(conn.apiToken.trim())
            val currentList = try {
                prefs[savedConnectionsKey]?.let { savedListAdapter.fromJson(it) } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val cleanId = if (conn.id.isNotBlank()) conn.id else UUID.randomUUID().toString()
            val existingIndex = currentList.indexOfFirst { it.id == cleanId || it.panelUrl.equals(conn.panelUrl.trim(), ignoreCase = true) }
            val updated = currentList.toMutableList()
            val toSave = conn.copy(
                id = if (existingIndex >= 0) updated[existingIndex].id else cleanId,
                apiToken = encryptedToken,
                lastUsedTimestamp = System.currentTimeMillis()
            )
            if (existingIndex >= 0) {
                updated[existingIndex] = toSave
            } else {
                updated.add(toSave)
            }
            prefs[savedConnectionsKey] = savedListAdapter.toJson(updated)
        }
    }

    suspend fun deleteSavedConnection(id: String) {
        context.dataStore.edit { prefs ->
            val currentList = try {
                prefs[savedConnectionsKey]?.let { savedListAdapter.fromJson(it) } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val updated = currentList.filterNot { it.id == id }
            prefs[savedConnectionsKey] = savedListAdapter.toJson(updated)
        }
    }

    suspend fun clearAllData() {
        context.dataStore.edit { prefs ->
            prefs.remove(savedConnectionsKey)
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[themeModeKey] = mode }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[languageKey] = lang }
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.dataStore.edit { it[aiEnabledKey] = enabled }
    }
}
