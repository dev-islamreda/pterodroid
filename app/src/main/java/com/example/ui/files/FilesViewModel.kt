package com.example.ui.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FileAttributes
import com.example.data.model.FileItemWrapper
import com.example.data.model.ServerAttributes
import com.example.data.repository.PteroRepository
import com.example.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FilesViewModel(
    private val repository: PteroRepository,
    val server: ServerAttributes
) : ViewModel() {

    private val _currentDirectory = MutableStateFlow("/")
    val currentDirectory: StateFlow<String> = _currentDirectory.asStateFlow()

    private val _filesState = MutableStateFlow<UiState<List<FileItemWrapper>>>(UiState.Loading)
    val filesState: StateFlow<UiState<List<FileItemWrapper>>> = _filesState.asStateFlow()

    private val _activeEditingFile = MutableStateFlow<FileAttributes?>(null)
    val activeEditingFile: StateFlow<FileAttributes?> = _activeEditingFile.asStateFlow()

    private val _editorContent = MutableStateFlow("")
    val editorContent: StateFlow<String> = _editorContent.asStateFlow()

    private val _operationStatus = MutableStateFlow<UiState<String>>(UiState.Idle)
    val operationStatus: StateFlow<UiState<String>> = _operationStatus.asStateFlow()

    private val _uploadProgress = MutableStateFlow<String?>(null)
    val uploadProgress: StateFlow<String?> = _uploadProgress.asStateFlow()

    private val _downloadUrlToShare = MutableStateFlow<String?>(null)
    val downloadUrlToShare: StateFlow<String?> = _downloadUrlToShare.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<String>>(emptySet())
    val selectedItems: StateFlow<Set<String>> = _selectedItems.asStateFlow()

    init {
        loadFiles("/")
    }

    fun toggleSelection(name: String) {
        val current = _selectedItems.value.toMutableSet()
        if (current.contains(name)) {
            current.remove(name)
        } else {
            current.add(name)
        }
        _selectedItems.value = current
    }

    fun selectAll(names: List<String>) {
        _selectedItems.value = names.toSet()
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
    }

    fun deleteSelected(names: List<String>) {
        if (names.isEmpty()) return
        val current = _currentDirectory.value
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val success = repository.deleteFiles(server.panelId, server.identifier, current, names)
                if (success) {
                    _operationStatus.value = UiState.Success("Successfully deleted ${names.size} item(s).")
                    clearSelection()
                    loadFiles(current)
                } else {
                    _operationStatus.value = UiState.Error("Failed to delete selected items.")
                }
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error(e.localizedMessage ?: "Failed to delete selected items.")
            }
        }
    }

    fun compressSelected(names: List<String>, requestedArchiveName: String) {
        if (names.isEmpty()) return
        val current = _currentDirectory.value
        val cleanName = requestedArchiveName.trim().ifBlank { "archive.zip" }
        val targetName = if (cleanName.contains(".")) cleanName else "$cleanName.zip"

        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val created = repository.compressFiles(server.panelId, server.identifier, current, names)
                if (created.name != targetName && targetName.isNotBlank()) {
                    try {
                        repository.renameFile(server.panelId, server.identifier, current, created.name, targetName)
                    } catch (_: Exception) {
                    }
                }
                _operationStatus.value = UiState.Success("Archive '$targetName' created successfully.")
                clearSelection()
                loadFiles(current)
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error(e.localizedMessage ?: "Compression failed.")
            }
        }
    }

    fun loadFiles(directory: String = _currentDirectory.value) {
        _currentDirectory.value = directory
        clearSelection()
        viewModelScope.launch {
            _filesState.value = UiState.Loading
            try {
                val list = repository.listFiles(server.panelId, server.identifier, directory)
                val sorted = list.sortedWith(compareBy({ it.attributes.isFile }, { it.attributes.name.lowercase() }))
                _filesState.value = UiState.Success(sorted)
            } catch (e: Exception) {
                _filesState.value = UiState.Error(e.localizedMessage ?: "Failed to list files.")
            }
        }
    }

    fun openDirectory(dirName: String) {
        clearSelection()
        val current = _currentDirectory.value.removeSuffix("/")
        val nextDir = if (current.isEmpty()) "/$dirName" else "$current/$dirName"
        loadFiles(nextDir)
    }

    fun navigateUp() {
        clearSelection()
        val current = _currentDirectory.value.removeSuffix("/")
        if (current.isEmpty() || current == "/") return
        val lastSlash = current.lastIndexOf('/')
        val parent = if (lastSlash <= 0) "/" else current.substring(0, lastSlash)
        loadFiles(parent)
    }

    fun openFileForEditing(file: FileAttributes) {
        val current = _currentDirectory.value.removeSuffix("/")
        val fullPath = if (current.isEmpty()) "/${file.name}" else "$current/${file.name}"
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val content = repository.getFileContents(server.panelId, server.identifier, fullPath)
                _activeEditingFile.value = file
                _editorContent.value = content
                _operationStatus.value = UiState.Idle
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error("Failed to read file: ${e.message}")
            }
        }
    }

    fun onEditorContentChange(newContent: String) {
        _editorContent.value = newContent
    }

    fun saveCurrentFile() {
        val file = _activeEditingFile.value ?: return
        val current = _currentDirectory.value.removeSuffix("/")
        val fullPath = if (current.isEmpty()) "/${file.name}" else "$current/${file.name}"
        val content = _editorContent.value
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val success = repository.writeFile(server.panelId, server.identifier, fullPath, content)
                if (success) {
                    _operationStatus.value = UiState.Success("File saved successfully.")
                } else {
                    _operationStatus.value = UiState.Error("Panel rejected file save.")
                }
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error("Error saving file: ${e.message}")
            }
        }
    }

    fun closeEditor() {
        _activeEditingFile.value = null
        _editorContent.value = ""
        _operationStatus.value = UiState.Idle
    }

    fun createFolder(name: String) {
        val current = _currentDirectory.value
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val success = repository.createFolder(server.panelId, server.identifier, current, name)
                if (success) {
                    _operationStatus.value = UiState.Success("Folder created.")
                    loadFiles(current)
                } else {
                    _operationStatus.value = UiState.Error("Failed to create folder.")
                }
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error(e.localizedMessage ?: "Error creating folder.")
            }
        }
    }

    fun createNewFile(fileName: String, initialContent: String = "") {
        val current = _currentDirectory.value.removeSuffix("/")
        val fullPath = if (current.isEmpty()) "/${fileName}" else "$current/$fileName"
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val success = repository.writeFile(server.panelId, server.identifier, fullPath, initialContent)
                if (success) {
                    _operationStatus.value = UiState.Success("File created.")
                    loadFiles(_currentDirectory.value)
                } else {
                    _operationStatus.value = UiState.Error("Failed to create file.")
                }
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error(e.localizedMessage ?: "Error creating file.")
            }
        }
    }

    fun rename(oldName: String, newName: String) {
        val current = _currentDirectory.value
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val success = repository.renameFile(server.panelId, server.identifier, current, oldName, newName)
                if (success) {
                    _operationStatus.value = UiState.Success("Renamed successfully.")
                    loadFiles(current)
                } else {
                    _operationStatus.value = UiState.Error("Failed to rename item.")
                }
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error(e.localizedMessage ?: "Error renaming file.")
            }
        }
    }

    fun moveItem(item: FileAttributes, targetDirectory: String, newName: String = item.name) {
        val fromPath = item.name
        val cleanTarget = if (targetDirectory.startsWith("/")) targetDirectory else "/$targetDirectory"
        val toPath = if (cleanTarget == "/" || cleanTarget.isEmpty()) "/$newName" else "$cleanTarget/$newName"
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val success = repository.renameFile(server.panelId, server.identifier, _currentDirectory.value, fromPath, toPath)
                if (success) {
                    _operationStatus.value = UiState.Success("Moved '${item.name}' to $cleanTarget.")
                    loadFiles(_currentDirectory.value)
                } else {
                    _operationStatus.value = UiState.Error("Failed to move item.")
                }
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error(e.localizedMessage ?: "Error moving file.")
            }
        }
    }

    fun copyItem(item: FileAttributes) {
        val current = _currentDirectory.value.removeSuffix("/")
        val fullPath = if (current.isEmpty()) "/${item.name}" else "$current/${item.name}"
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val success = repository.copyFile(server.panelId, server.identifier, fullPath)
                if (success) {
                    _operationStatus.value = UiState.Success("Copy of '${item.name}' created.")
                    loadFiles(_currentDirectory.value)
                } else {
                    _operationStatus.value = UiState.Error("Failed to copy file.")
                }
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error(e.localizedMessage ?: "Error copying file.")
            }
        }
    }

    fun compressItem(item: FileAttributes) {
        val current = _currentDirectory.value
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val archive = repository.compressFiles(server.panelId, server.identifier, current, listOf(item.name))
                _operationStatus.value = UiState.Success("Archive '${archive.name}' generated.")
                loadFiles(current)
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error(e.localizedMessage ?: "Compression failed.")
            }
        }
    }

    fun decompressItem(archiveItem: FileAttributes, destinationDir: String = _currentDirectory.value) {
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val cleanDest = if (destinationDir.startsWith("/")) destinationDir else "/$destinationDir"
                val success = repository.decompressFile(server.panelId, server.identifier, cleanDest, archiveItem.name)
                if (success) {
                    _operationStatus.value = UiState.Success("Archive '${archiveItem.name}' extracted successfully.")
                    loadFiles(_currentDirectory.value)
                } else {
                    _operationStatus.value = UiState.Error("Extraction was rejected by panel.")
                }
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error(e.localizedMessage ?: "Extraction failed.")
            }
        }
    }

    fun deleteItem(name: String) {
        val current = _currentDirectory.value
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val success = repository.deleteFiles(server.panelId, server.identifier, current, listOf(name))
                if (success) {
                    _operationStatus.value = UiState.Success("Deleted '$name'.")
                    loadFiles(current)
                } else {
                    _operationStatus.value = UiState.Error("Failed to delete item.")
                }
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error(e.localizedMessage ?: "Error deleting item.")
            }
        }
    }

    fun uploadFiles(uris: List<Uri>, contentResolver: ContentResolver) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val total = uris.size
            var successCount = 0
            for ((index, uri) in uris.withIndex()) {
                val fileName = queryFileName(contentResolver, uri)
                _uploadProgress.value = "Uploading (${index + 1}/$total): $fileName"
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (bytes != null) {
                        val ok = repository.uploadFile(server.panelId, server.identifier, _currentDirectory.value, fileName, bytes)
                        if (ok) successCount++
                    }
                } catch (e: Exception) {
                    _operationStatus.value = UiState.Error("Failed uploading $fileName: ${e.message}")
                }
            }
            _uploadProgress.value = null
            _operationStatus.value = UiState.Success("Uploaded $successCount of $total files.")
            loadFiles(_currentDirectory.value)
        }
    }

    fun downloadFile(file: FileAttributes, context: Context) {
        val current = _currentDirectory.value.removeSuffix("/")
        val fullPath = if (current.isEmpty()) "/${file.name}" else "$current/${file.name}"
        viewModelScope.launch {
            _operationStatus.value = UiState.Loading
            try {
                val url = try {
                    repository.getFileDownloadUrl(server.panelId, server.identifier, fullPath)
                } catch (_: Exception) {
                    null
                }
                _downloadUrlToShare.value = url
                val bytes = repository.downloadFileBytes(server.panelId, server.identifier, fullPath)
                withContext(Dispatchers.IO) {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: context.filesDir
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val targetFile = File(downloadsDir, file.name)
                    FileOutputStream(targetFile).use { it.write(bytes) }
                }
                _operationStatus.value = UiState.Success("Downloaded '${file.name}' to Downloads folder.")
            } catch (e: Exception) {
                _operationStatus.value = UiState.Error("Download failed: ${e.message}")
            }
        }
    }

    private fun queryFileName(contentResolver: ContentResolver, uri: Uri): String {
        var name = uri.lastPathSegment ?: "uploaded_file"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex) ?: name
                }
            }
        } catch (_: Exception) {
        }
        return name
    }

    fun clearDownloadUrl() {
        _downloadUrlToShare.value = null
    }

    fun clearOperationStatus() {
        _operationStatus.value = UiState.Idle
    }
}
