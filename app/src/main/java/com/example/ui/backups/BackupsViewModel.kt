package com.example.ui.backups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BackupItemWrapper
import com.example.data.model.ServerAttributes
import com.example.data.repository.PteroRepository
import com.example.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BackupsViewModel(
    private val repository: PteroRepository,
    val server: ServerAttributes
) : ViewModel() {

    private val _backupsState = MutableStateFlow<UiState<List<BackupItemWrapper>>>(UiState.Loading)
    val backupsState: StateFlow<UiState<List<BackupItemWrapper>>> = _backupsState.asStateFlow()

    private val _operationStatus = MutableStateFlow<UiState<String>>(UiState.Idle)
    val operationStatus: StateFlow<UiState<String>> = _operationStatus.asStateFlow()

    private val _downloadUrl = MutableStateFlow<String?>(null)
    val downloadUrl: StateFlow<String?> = _downloadUrl.asStateFlow()

    init {
        loadBackups()
    }

    fun loadBackups() {
        viewModelScope.launch {
            _backupsState.value = UiState.Loading
            try {
                val list = repository.listBackups(server.panelId, server.identifier)
                _backupsState.value = UiState.Success(list)
            } catch (e: Exception) {
                _backupsState.value = UiState.Error(e.localizedMessage ?: "Failed to load backups.")
            }
        }
    }

    fun createBackup(name: String?, isLocked: Boolean) {
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                repository.createBackup(server.panelId, server.identifier, name, isLocked)
                _operationStatus.value = UiState.Success("Backup creation started.")
                loadBackups()
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error(e.localizedMessage ?: "Failed to create backup.")
            }
        }
    }

    fun restoreBackup(backupUuid: String, truncate: Boolean) {
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val success = repository.restoreBackup(server.panelId, server.identifier, backupUuid, truncate)
                if (success) {
                    _operationStatus.value = UiState.Success("Backup restore signal dispatched.")
                } else {
                    _operationStatus.value = UiState.Error("Panel rejected restore request.")
                }
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error(e.localizedMessage ?: "Failed to restore backup.")
            }
        }
    }

    fun deleteBackup(backupUuid: String) {
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val success = repository.deleteBackup(server.panelId, server.identifier, backupUuid)
                if (success) {
                    _operationStatus.value = UiState.Success("Backup deleted.")
                    loadBackups()
                } else {
                    _operationStatus.value = UiState.Error("Failed to delete backup.")
                }
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error(e.localizedMessage ?: "Failed to delete backup.")
            }
        }
    }

    fun fetchDownloadUrl(backupUuid: String) {
        viewModelScope.launch {
            try {
                val url = repository.getBackupDownloadUrl(server.panelId, server.identifier, backupUuid)
                _downloadUrl.value = url
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error("Could not retrieve download link: ${e.message}")
            }
        }
    }

    fun clearDownloadUrl() {
        _downloadUrl.value = null
    }

    fun clearOperationStatus() {
        _operationStatus.value = UiState.Idle
    }
}
