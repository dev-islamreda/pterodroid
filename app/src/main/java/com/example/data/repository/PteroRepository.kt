package com.example.data.repository

import android.util.Log
import com.example.data.api.ApiClientFactory
import com.example.data.api.PteroApiException
import com.example.data.api.PteroErrorParser
import com.example.data.api.PterodactylApiService
import com.example.data.api.UrlNormalizer
import com.example.data.model.BackupAttributes
import com.example.data.model.BackupItemWrapper
import com.example.data.model.ConnectionDiagnostic
import com.example.data.model.CreateBackupRequest
import com.example.data.model.CreateFolderRequest
import com.example.data.model.DeleteFilesRequest
import com.example.data.model.CompressFilesRequest
import com.example.data.model.CopyFileRequest
import com.example.data.model.DecompressFileRequest
import com.example.data.model.FileAttributes
import com.example.data.model.FileItemWrapper
import com.example.data.model.PowerSignalRequest
import com.example.data.model.RenameFileItem
import com.example.data.model.RenameFilesRequest
import com.example.data.model.RestoreBackupRequest
import com.example.data.model.SavedConnection
import com.example.data.model.ServerAttributes
import com.example.data.model.ServerCommandRequest
import com.example.data.model.ServerItem
import com.example.data.model.ServerStatsAttributes
import com.example.data.model.WebsocketAuthData
import com.example.data.websocket.PteroDaemonWebsocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

class PteroRepository(
    private val appScope: CoroutineScope
) {
    private val tag = "PteroRepository"
    private val panelClients = ConcurrentHashMap<String, PterodactylApiService>()
    private val panelConfigs = ConcurrentHashMap<String, SavedConnection>()

    val daemonWebsocket: PteroDaemonWebsocket by lazy {
        PteroDaemonWebsocket(appScope)
    }

    fun hasPanels(): Boolean = panelConfigs.isNotEmpty()

    fun getAllPanels(): List<SavedConnection> = panelConfigs.values.toList()

    fun getPanelConfig(panelId: String): SavedConnection? = panelConfigs[panelId]

    fun registerPanel(panel: SavedConnection) {
        if (panel.panelUrl.isBlank() || panel.apiToken.isBlank()) return
        val cleanUrl = try { UrlNormalizer.normalize(panel.panelUrl) } catch (_: Exception) { panel.panelUrl }
        val cleanToken = panel.apiToken.trim().trim('"', '\'')
        val updated = panel.copy(panelUrl = cleanUrl, apiToken = cleanToken)
        panelConfigs[panel.id] = updated
        panelClients[panel.id] = ApiClientFactory.createClient(cleanUrl, cleanToken)
    }

    fun unregisterPanel(panelId: String) {
        panelConfigs.remove(panelId)
        panelClients.remove(panelId)
    }

    fun syncPanels(panels: List<SavedConnection>) {
        val currentIds = panels.map { it.id }.toSet()
        panelConfigs.keys.filter { it !in currentIds }.forEach { id ->
            panelConfigs.remove(id)
            panelClients.remove(id)
        }
        panels.forEach { panel ->
            registerPanel(panel)
        }
    }

    fun clearAll() {
        daemonWebsocket.disconnect()
        panelClients.clear()
        panelConfigs.clear()
    }

    private fun getService(panelId: String): PterodactylApiService {
        val service = panelClients[panelId]
        if (service != null) return service
        val config = panelConfigs[panelId]
        if (config != null) {
            val created = ApiClientFactory.createClient(config.panelUrl, config.apiToken)
            panelClients[panelId] = created
            return created
        }
        throw PteroApiException.Unauthorized("Panel $panelId is not connected or credentials not found.")
    }

    suspend fun runDiagnostics(rawUrl: String, rawToken: String, panelId: String? = null): ConnectionDiagnostic = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val cleanedUrl = try {
            UrlNormalizer.normalize(rawUrl)
        } catch (e: Exception) {
            return@withContext ConnectionDiagnostic(
                panelId = panelId,
                panelUrl = rawUrl,
                urlValid = false,
                errorMessage = e.message ?: "Invalid URL format."
            )
        }
        val trimmedToken = rawToken.trim().trim('"', '\'')
        if (trimmedToken.isBlank()) {
            return@withContext ConnectionDiagnostic(
                panelId = panelId,
                panelUrl = cleanedUrl,
                urlValid = true,
                tokenValid = false,
                errorMessage = "API token cannot be empty."
            )
        }

        try {
            val testClient = ApiClientFactory.createClient(cleanedUrl, trimmedToken)
            val serverListResponse = testClient.listServers(page = 1)
            val latency = System.currentTimeMillis() - startTime
            val count = serverListResponse.meta?.pagination?.total ?: serverListResponse.data.size
            ConnectionDiagnostic(
                panelId = panelId,
                panelUrl = cleanedUrl,
                urlValid = true,
                panelReachable = true,
                latencyMs = latency,
                tokenValid = true,
                serverCount = count,
                errorMessage = null
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val parsedError = PteroErrorParser.parse(e, trimmedToken)
            val isReachable = parsedError !is PteroApiException.Offline && parsedError !is PteroApiException.Timeout
            val isTokenValid = parsedError !is PteroApiException.Unauthorized
            ConnectionDiagnostic(
                panelId = panelId,
                panelUrl = cleanedUrl,
                urlValid = true,
                panelReachable = isReachable,
                latencyMs = if (isReachable) latency else null,
                tokenValid = isTokenValid,
                serverCount = null,
                errorMessage = parsedError.message
            )
        }
    }

    suspend fun getServers(panelId: String): List<ServerItem> = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        val token = config?.apiToken
        try {
            val service = getService(panelId)
            val firstPage = service.listServers(page = 1)
            val totalPages = firstPage.meta?.pagination?.totalPages ?: 1
            val rawServers = firstPage.data.toMutableList()
            val maxPagesToFetch = totalPages.coerceAtMost(10)
            for (p in 2..maxPagesToFetch) {
                try {
                    val nextPage = service.listServers(page = p)
                    rawServers.addAll(nextPage.data)
                } catch (e: Exception) {
                    Log.w(tag, "Failed to load page $p of servers for panel $panelId: ${e.message}")
                    break
                }
            }
            // Decorate each server with its panel details
            rawServers.map { item ->
                item.copy(
                    attributes = item.attributes.copy(
                        panelId = panelId,
                        panelLabel = config?.label ?: panelId,
                        panelUrl = config?.panelUrl ?: ""
                    )
                )
            }
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, token)
        }
    }

    suspend fun getServerDetails(panelId: String, identifier: String): ServerAttributes = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val response = getService(panelId).getServer(identifier)
            response.attributes.copy(
                panelId = panelId,
                panelLabel = config?.label ?: panelId,
                panelUrl = config?.panelUrl ?: ""
            )
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun getServerResources(panelId: String, identifier: String): ServerStatsAttributes = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val response = getService(panelId).getServerResources(identifier)
            response.attributes
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun sendPowerSignal(panelId: String, identifier: String, signal: String): Boolean = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val response = getService(panelId).sendPowerSignal(identifier, PowerSignalRequest(signal))
            if (!response.isSuccessful) {
                val errorMsg = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                throw PteroApiException.ServerError(
                    code = response.code(),
                    message = "Server rejected power action '$signal': HTTP ${response.code()}${if (errorMsg != null) " - $errorMsg" else ""}"
                )
            }
            true
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun sendConsoleCommand(panelId: String, identifier: String, command: String): Boolean = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val response = getService(panelId).sendServerCommand(identifier, ServerCommandRequest(command))
            if (!response.isSuccessful) {
                val errorMsg = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                throw PteroApiException.ServerError(
                    code = response.code(),
                    message = "Server rejected command: HTTP ${response.code()}${if (errorMsg != null) " - $errorMsg" else ""}"
                )
            }
            true
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun getWebsocketAuth(panelId: String, identifier: String): WebsocketAuthData = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val response = getService(panelId).getWebsocketAuth(identifier)
            response.data
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    fun connectServerWebsocket(panelId: String, identifier: String, authData: WebsocketAuthData) {
        val config = panelConfigs[panelId] ?: return
        daemonWebsocket.onTokenRefreshNeeded = {
            try {
                getWebsocketAuth(panelId, identifier)
            } catch (e: Exception) {
                Log.e(tag, "Failed to refresh daemon token: ${e.message}")
                null
            }
        }
        daemonWebsocket.connect(authData, config.panelUrl)
    }

    suspend fun listFiles(panelId: String, identifier: String, directory: String): List<FileItemWrapper> = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val cleanDir = if (directory.startsWith("/")) directory else "/$directory"
            val response = getService(panelId).listFiles(identifier, cleanDir)
            response.data
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun getFileContents(panelId: String, identifier: String, filePath: String): String = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val body = getService(panelId).getFileContents(identifier, filePath)
            body.string()
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun writeFile(panelId: String, identifier: String, filePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val body = content.toRequestBody("text/plain".toMediaType())
            val response = getService(panelId).writeFile(identifier, filePath, body)
            response.isSuccessful
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun createFolder(panelId: String, identifier: String, root: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val response = getService(panelId).createFolder(identifier, CreateFolderRequest(root, name))
            response.isSuccessful
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun renameFile(panelId: String, identifier: String, root: String, from: String, to: String): Boolean = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val response = getService(panelId).renameFiles(
                identifier,
                RenameFilesRequest(root = root, files = listOf(RenameFileItem(from = from, to = to)))
            )
            response.isSuccessful
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun deleteFiles(panelId: String, identifier: String, root: String, fileNames: List<String>): Boolean = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val response = getService(panelId).deleteFiles(
                identifier,
                DeleteFilesRequest(root = root, files = fileNames)
            )
            response.isSuccessful
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun copyFile(panelId: String, identifier: String, location: String): Boolean = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val cleanLocation = if (location.startsWith("/")) location else "/$location"
            val response = getService(panelId).copyFile(identifier, CopyFileRequest(location = cleanLocation))
            response.isSuccessful
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun compressFiles(panelId: String, identifier: String, root: String, files: List<String>): FileAttributes = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val cleanRoot = if (root.startsWith("/")) root else "/$root"
            val response = getService(panelId).compressFiles(identifier, CompressFilesRequest(root = cleanRoot, files = files))
            response.attributes
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun decompressFile(panelId: String, identifier: String, root: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val cleanRoot = if (root.startsWith("/")) root else "/$root"
            val response = getService(panelId).decompressFile(identifier, DecompressFileRequest(root = cleanRoot, file = fileName))
            response.isSuccessful
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun getFileDownloadUrl(panelId: String, identifier: String, filePath: String): String = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val cleanPath = if (filePath.startsWith("/")) filePath else "/$filePath"
            val response = getService(panelId).getFileDownloadUrl(identifier, cleanPath)
            response.attributes.url
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun downloadFileBytes(panelId: String, identifier: String, filePath: String): ByteArray = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val cleanPath = if (filePath.startsWith("/")) filePath else "/$filePath"
            val signedUrl = try {
                getFileDownloadUrl(panelId, identifier, cleanPath)
            } catch (_: Exception) {
                null
            }
            if (!signedUrl.isNullOrBlank()) {
                val okHttp = ApiClientFactory.createRawOkHttpClient(config?.apiToken ?: "")
                val req = Request.Builder().url(signedUrl).build()
                val resp = okHttp.newCall(req).execute()
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null) return@withContext bytes
                }
            }
            val body = getService(panelId).getFileContents(identifier, cleanPath)
            body.bytes()
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun uploadFile(
        panelId: String,
        identifier: String,
        directory: String,
        fileName: String,
        fileBytes: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val cleanDir = if (directory.startsWith("/")) directory else "/$directory"
            val uploadUrl = try {
                val resp = getService(panelId).getFileUploadUrl(identifier)
                resp.attributes.url
            } catch (e: Exception) {
                Log.w(tag, "Could not obtain signed upload URL: ${e.message}")
                null
            }
            if (!uploadUrl.isNullOrBlank()) {
                try {
                    val rawClient = ApiClientFactory.createRawOkHttpClient(config?.apiToken ?: "")
                    val fileBody = fileBytes.toRequestBody("application/octet-stream".toMediaType())
                    val multipartBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("files", fileName, fileBody)
                        .build()
                    val targetUrl = if (uploadUrl.contains("?")) {
                        "$uploadUrl&directory=${java.net.URLEncoder.encode(cleanDir, "UTF-8")}"
                    } else {
                        "$uploadUrl?directory=${java.net.URLEncoder.encode(cleanDir, "UTF-8")}"
                    }
                    val postRequest = Request.Builder()
                        .url(targetUrl)
                        .post(multipartBody)
                        .build()
                    val response = rawClient.newCall(postRequest).execute()
                    if (response.isSuccessful) {
                        return@withContext true
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Wings direct upload exception: ${e.message}, falling back to panel write")
                }
            }
            val fullPath = if (cleanDir == "/" || cleanDir.isEmpty()) "/$fileName" else "$cleanDir/$fileName"
            val body = fileBytes.toRequestBody("application/octet-stream".toMediaType())
            val fallbackResponse = getService(panelId).writeFile(identifier, fullPath, body)
            fallbackResponse.isSuccessful
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun listBackups(panelId: String, identifier: String): List<BackupItemWrapper> = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val response = getService(panelId).listBackups(identifier)
            response.data
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun createBackup(panelId: String, identifier: String, name: String?, isLocked: Boolean): BackupAttributes = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val response = getService(panelId).createBackup(
                identifier,
                CreateBackupRequest(name = if (name.isNullOrBlank()) null else name, isLocked = isLocked)
            )
            response.attributes
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun getBackupDownloadUrl(panelId: String, identifier: String, backupUuid: String): String = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val response = getService(panelId).getBackupDownload(identifier, backupUuid)
            response.attributes.url
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun restoreBackup(panelId: String, identifier: String, backupUuid: String, truncate: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val response = getService(panelId).restoreBackup(identifier, backupUuid, request = RestoreBackupRequest(truncate))
            response.isSuccessful
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }

    suspend fun deleteBackup(panelId: String, identifier: String, backupUuid: String): Boolean = withContext(Dispatchers.IO) {
        val config = panelConfigs[panelId]
        try {
            val response = getService(panelId).deleteBackup(identifier, backupUuid)
            response.isSuccessful
        } catch (e: Throwable) {
            throw PteroErrorParser.parse(e, config?.apiToken)
        }
    }
}
