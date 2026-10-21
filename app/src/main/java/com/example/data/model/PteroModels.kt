package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// --- Client API Models ---
@JsonClass(generateAdapter = false)
data class PteroListResponse<T>(
    @Json(name = "object") val objectType: String? = null,
    @Json(name = "data") val data: List<T> = emptyList(),
    @Json(name = "meta") val meta: PteroMeta? = null
)

@JsonClass(generateAdapter = false)
data class PteroItemResponse<T>(
    @Json(name = "object") val objectType: String? = null,
    @Json(name = "attributes") val attributes: T
)

@JsonClass(generateAdapter = false)
data class PteroDataResponse<T>(
    @Json(name = "data") val data: T
)

@JsonClass(generateAdapter = false)
data class PteroMeta(
    @Json(name = "pagination") val pagination: PteroPagination? = null
)

@JsonClass(generateAdapter = false)
data class PteroPagination(
    @Json(name = "total") val total: Int = 0,
    @Json(name = "count") val count: Int = 0,
    @Json(name = "per_page") val perPage: Int = 0,
    @Json(name = "current_page") val currentPage: Int = 1,
    @Json(name = "total_pages") val totalPages: Int = 1
)

// --- Server Representation ---
@JsonClass(generateAdapter = false)
data class ServerItem(
    @Json(name = "object") val objectType: String? = null,
    @Json(name = "attributes") val attributes: ServerAttributes
)

@JsonClass(generateAdapter = false)
data class ServerAttributes(
    @Json(name = "server_owner") val serverOwner: Boolean = false,
    @Json(name = "identifier") val identifier: String = "",
    @Json(name = "uuid") val uuid: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "node") val node: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "is_suspended") val isSuspended: Boolean = false,
    @Json(name = "is_installing") val isInstalling: Boolean = false,
    @Json(name = "sftp_details") val sftpDetails: SftpDetails? = null,
    @Json(name = "limits") val limits: ServerLimits = ServerLimits(),
    @Json(name = "feature_limits") val featureLimits: FeatureLimits = FeatureLimits(),
    @Json(name = "relationships") val relationships: ServerRelationships? = null,
    @Json(name = "permissions") val permissions: List<String>? = null,
    @Json(name = "user_permissions") val userPermissions: List<String>? = null,
    val panelId: String = "",
    val panelLabel: String = "",
    val panelUrl: String = ""
) {
    val compositeKey: String
        get() = if (panelId.isNotBlank()) "$panelId:$identifier" else identifier

    fun canSendCommands(): Boolean {
        if (serverOwner) return true
        val perms = permissions ?: userPermissions
        if (perms == null) return true
        return perms.contains("control.console") || perms.contains("admin") || perms.contains("*")
    }

    fun canControlPower(signal: String? = null): Boolean {
        if (serverOwner) return true
        val perms = permissions ?: userPermissions
        if (perms == null) return true
        if (perms.contains("admin") || perms.contains("*")) return true
        return when (signal?.lowercase()) {
            "start" -> perms.contains("control.start")
            "stop" -> perms.contains("control.stop")
            "restart" -> perms.contains("control.restart")
            else -> perms.contains("control.start") || perms.contains("control.stop") || perms.contains("control.restart")
        }
    }
}

@JsonClass(generateAdapter = false)
data class SftpDetails(
    @Json(name = "ip") val ip: String? = null,
    @Json(name = "port") val port: Int? = null
)

@JsonClass(generateAdapter = false)
data class ServerLimits(
    @Json(name = "memory") val memory: Long = 0,
    @Json(name = "swap") val swap: Long = 0,
    @Json(name = "disk") val disk: Long = 0,
    @Json(name = "io") val io: Long = 500,
    @Json(name = "cpu") val cpu: Double = 0.0,
    @Json(name = "threads") val threads: String? = null
)

@JsonClass(generateAdapter = false)
data class FeatureLimits(
    @Json(name = "databases") val databases: Int = 0,
    @Json(name = "allocations") val allocations: Int = 0,
    @Json(name = "backups") val backups: Int = 0
)

@JsonClass(generateAdapter = false)
data class ServerRelationships(
    @Json(name = "allocations") val allocations: PteroListResponse<AllocationItem>? = null
)

@JsonClass(generateAdapter = false)
data class AllocationItem(
    @Json(name = "object") val objectType: String? = null,
    @Json(name = "attributes") val attributes: AllocationAttributes
)

@JsonClass(generateAdapter = false)
data class AllocationAttributes(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "ip") val ip: String = "",
    @Json(name = "ip_alias") val ipAlias: String? = null,
    @Json(name = "port") val port: Int = 0,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "is_default") val isDefault: Boolean = false
)

// --- Server Live Resource Stats ---
@JsonClass(generateAdapter = false)
data class ServerStatsAttributes(
    @Json(name = "current_state") val currentState: String = "offline",
    @Json(name = "is_suspended") val isSuspended: Boolean = false,
    @Json(name = "resources") val resources: ResourceStats = ResourceStats()
)

@JsonClass(generateAdapter = false)
data class NetworkStats(
    @Json(name = "rx_bytes") val rxBytes: Long = 0,
    @Json(name = "tx_bytes") val txBytes: Long = 0
)

@JsonClass(generateAdapter = false)
data class ResourceStats(
    @Json(name = "memory_bytes") val memoryBytes: Long = 0,
    @Json(name = "memory_limit_bytes") val memoryLimitBytes: Long = 0,
    @Json(name = "cpu_absolute") val cpuAbsolute: Double = 0.0,
    @Json(name = "disk_bytes") val diskBytes: Long = 0,
    @Json(name = "network_rx_bytes") val networkRxBytes: Long = 0,
    @Json(name = "network_tx_bytes") val networkTxBytes: Long = 0,
    @Json(name = "network") val network: NetworkStats? = null,
    @Json(name = "uptime") val uptimeMs: Long = 0,
    @Json(name = "state") val state: String? = null
) {
    val effectiveRxBytes: Long get() = network?.rxBytes ?: networkRxBytes
    val effectiveTxBytes: Long get() = network?.txBytes ?: networkTxBytes
}

// --- Power and Command Requests ---
@JsonClass(generateAdapter = false)
data class PowerSignalRequest(
    @Json(name = "signal") val signal: String
)

@JsonClass(generateAdapter = false)
data class ServerCommandRequest(
    @Json(name = "command") val command: String
)

// --- Daemon Websocket Credentials ---
@JsonClass(generateAdapter = false)
data class WebsocketAuthData(
    @Json(name = "token") val token: String,
    @Json(name = "socket") val socket: String
)

// --- File System Models ---
@JsonClass(generateAdapter = false)
data class FileItemWrapper(
    @Json(name = "object") val objectType: String? = null,
    @Json(name = "attributes") val attributes: FileAttributes
)

@JsonClass(generateAdapter = false)
data class FileAttributes(
    @Json(name = "name") val name: String = "",
    @Json(name = "mode") val mode: String = "",
    @Json(name = "size") val size: Long = 0,
    @Json(name = "is_file") val isFile: Boolean = true,
    @Json(name = "is_symlink") val isSymlink: Boolean = false,
    @Json(name = "mimetype") val mimetype: String = "",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "modified_at") val modifiedAt: String? = null
) {
    fun isArchive(): Boolean {
        if (!isFile) return false
        val lower = name.lowercase()
        return lower.endsWith(".zip") ||
                lower.endsWith(".tar.gz") ||
                lower.endsWith(".tgz") ||
                lower.endsWith(".tar") ||
                lower.endsWith(".rar") ||
                lower.endsWith(".gz") ||
                lower.endsWith(".7z")
    }

    fun isEditableText(): Boolean {
        if (!isFile) return false
        val lower = name.lowercase()
        return lower.endsWith(".txt") ||
                lower.endsWith(".yml") ||
                lower.endsWith(".yaml") ||
                lower.endsWith(".json") ||
                lower.endsWith(".properties") ||
                lower.endsWith(".cfg") ||
                lower.endsWith(".conf") ||
                lower.endsWith(".config") ||
                lower.endsWith(".ini") ||
                lower.endsWith(".env") ||
                lower.endsWith(".sh") ||
                lower.endsWith(".bat") ||
                lower.endsWith(".log") ||
                lower.endsWith(".xml") ||
                lower.endsWith(".md") ||
                lower.endsWith(".toml") ||
                lower.endsWith(".js") ||
                lower.endsWith(".ts") ||
                lower.endsWith(".py") ||
                lower.endsWith(".lua") ||
                lower.endsWith(".sql") ||
                mimetype.startsWith("text/")
    }
}

@JsonClass(generateAdapter = false)
data class CreateFolderRequest(
    @Json(name = "root") val root: String,
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = false)
data class RenameFileItem(
    @Json(name = "from") val from: String,
    @Json(name = "to") val to: String
)

@JsonClass(generateAdapter = false)
data class RenameFilesRequest(
    @Json(name = "root") val root: String,
    @Json(name = "files") val files: List<RenameFileItem>
)

@JsonClass(generateAdapter = false)
data class CopyFileRequest(
    @Json(name = "location") val location: String
)

@JsonClass(generateAdapter = false)
data class CompressFilesRequest(
    @Json(name = "root") val root: String,
    @Json(name = "files") val files: List<String>
)

@JsonClass(generateAdapter = false)
data class DecompressFileRequest(
    @Json(name = "root") val root: String,
    @Json(name = "file") val file: String
)

@JsonClass(generateAdapter = false)
data class DeleteFilesRequest(
    @Json(name = "root") val root: String,
    @Json(name = "files") val files: List<String>
)

@JsonClass(generateAdapter = false)
data class SignedUrlResponse(
    @Json(name = "object") val objectType: String? = null,
    @Json(name = "attributes") val attributes: SignedUrlAttributes
)

@JsonClass(generateAdapter = false)
data class SignedUrlAttributes(
    @Json(name = "url") val url: String = ""
)

// --- Backups Models ---
@JsonClass(generateAdapter = false)
data class BackupItemWrapper(
    @Json(name = "object") val objectType: String? = null,
    @Json(name = "attributes") val attributes: BackupAttributes
)

@JsonClass(generateAdapter = false)
data class BackupAttributes(
    @Json(name = "uuid") val uuid: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "ignored_files") val ignoredFiles: List<String> = emptyList(),
    @Json(name = "sha256_hash") val sha256Hash: String? = null,
    @Json(name = "bytes") val bytes: Long = 0,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "completed_at") val completedAt: String? = null,
    @Json(name = "is_successful") val isSuccessful: Boolean = false,
    @Json(name = "is_locked") val isLocked: Boolean = false
)

@JsonClass(generateAdapter = false)
data class CreateBackupRequest(
    @Json(name = "name") val name: String? = null,
    @Json(name = "is_locked") val isLocked: Boolean = false,
    @Json(name = "ignored") val ignored: String = ""
)

@JsonClass(generateAdapter = false)
data class RestoreBackupRequest(
    @Json(name = "truncate") val truncate: Boolean = false
)

@JsonClass(generateAdapter = false)
data class BackupDownloadAttributes(
    @Json(name = "url") val url: String = ""
)

// --- Saved Connection Model for Local and Cloud Storage ---
data class SavedConnection(
    val id: String = "",
    val label: String = "",
    val panelUrl: String = "",
    val apiToken: String = "",
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)

// --- Diagnostic Status Model ---
data class ConnectionDiagnostic(
    val panelId: String? = null,
    val panelUrl: String = "",
    val urlValid: Boolean = false,
    val panelReachable: Boolean? = null,
    val latencyMs: Long? = null,
    val tokenValid: Boolean? = null,
    val serverCount: Int? = null,
    val errorMessage: String? = null,
    val isTesting: Boolean = false,
    val testedAtTimestamp: Long = System.currentTimeMillis()
) {
    val isHealthy: Boolean
        get() = panelReachable == true && tokenValid == true && errorMessage.isNullOrBlank()

    val latencyRating: String
        get() = when {
            latencyMs == null -> "Failed"
            latencyMs < 150 -> "Optimal"
            latencyMs < 400 -> "Good"
            else -> "High"
        }
}

