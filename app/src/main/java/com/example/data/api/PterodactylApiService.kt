package com.example.data.api

import com.example.data.model.BackupAttributes
import com.example.data.model.BackupDownloadAttributes
import com.example.data.model.BackupItemWrapper
import com.example.data.model.CompressFilesRequest
import com.example.data.model.CopyFileRequest
import com.example.data.model.CreateBackupRequest
import com.example.data.model.CreateFolderRequest
import com.example.data.model.DecompressFileRequest
import com.example.data.model.DeleteFilesRequest
import com.example.data.model.FileAttributes
import com.example.data.model.FileItemWrapper
import com.example.data.model.PowerSignalRequest
import com.example.data.model.PteroDataResponse
import com.example.data.model.PteroItemResponse
import com.example.data.model.PteroListResponse
import com.example.data.model.RenameFilesRequest
import com.example.data.model.RestoreBackupRequest
import com.example.data.model.ServerAttributes
import com.example.data.model.ServerCommandRequest
import com.example.data.model.ServerItem
import com.example.data.model.ServerStatsAttributes
import com.example.data.model.SignedUrlResponse
import com.example.data.model.WebsocketAuthData
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface PterodactylApiService {

    @GET("api/client")
    suspend fun listServers(
        @Query("page") page: Int = 1
    ): PteroListResponse<ServerItem>

    @GET("api/client/servers/{server}")
    suspend fun getServer(
        @Path("server") serverIdentifier: String
    ): PteroItemResponse<ServerAttributes>

    @GET("api/client/servers/{server}/resources")
    suspend fun getServerResources(
        @Path("server") serverIdentifier: String
    ): PteroItemResponse<ServerStatsAttributes>

    @POST("api/client/servers/{server}/power")
    suspend fun sendPowerSignal(
        @Path("server") serverIdentifier: String,
        @Body request: PowerSignalRequest
    ): Response<Unit>

    @POST("api/client/servers/{server}/command")
    suspend fun sendServerCommand(
        @Path("server") serverIdentifier: String,
        @Body request: ServerCommandRequest
    ): Response<Unit>

    @GET("api/client/servers/{server}/websocket")
    suspend fun getWebsocketAuth(
        @Path("server") serverIdentifier: String
    ): PteroDataResponse<WebsocketAuthData>

    // Files
    @GET("api/client/servers/{server}/files/list")
    suspend fun listFiles(
        @Path("server") serverIdentifier: String,
        @Query("directory") directory: String = "/"
    ): PteroDataResponse<List<FileItemWrapper>>

    @GET("api/client/servers/{server}/files/contents")
    suspend fun getFileContents(
        @Path("server") serverIdentifier: String,
        @Query("file") filePath: String
    ): ResponseBody

    @POST("api/client/servers/{server}/files/write")
    suspend fun writeFile(
        @Path("server") serverIdentifier: String,
        @Query("file") filePath: String,
        @Body content: RequestBody
    ): Response<Unit>

    @POST("api/client/servers/{server}/files/create-folder")
    suspend fun createFolder(
        @Path("server") serverIdentifier: String,
        @Body request: CreateFolderRequest
    ): Response<Unit>

    @PUT("api/client/servers/{server}/files/rename")
    suspend fun renameFiles(
        @Path("server") serverIdentifier: String,
        @Body request: RenameFilesRequest
    ): Response<Unit>

    @POST("api/client/servers/{server}/files/copy")
    suspend fun copyFile(
        @Path("server") serverIdentifier: String,
        @Body request: CopyFileRequest
    ): Response<Unit>

    @POST("api/client/servers/{server}/files/compress")
    suspend fun compressFiles(
        @Path("server") serverIdentifier: String,
        @Body request: CompressFilesRequest
    ): PteroItemResponse<FileAttributes>

    @POST("api/client/servers/{server}/files/decompress")
    suspend fun decompressFile(
        @Path("server") serverIdentifier: String,
        @Body request: DecompressFileRequest
    ): Response<Unit>

    @GET("api/client/servers/{server}/files/download")
    suspend fun getFileDownloadUrl(
        @Path("server") serverIdentifier: String,
        @Query("file") filePath: String
    ): SignedUrlResponse

    @GET("api/client/servers/{server}/files/upload")
    suspend fun getFileUploadUrl(
        @Path("server") serverIdentifier: String
    ): SignedUrlResponse

    @POST("api/client/servers/{server}/files/delete")
    suspend fun deleteFiles(
        @Path("server") serverIdentifier: String,
        @Body request: DeleteFilesRequest
    ): Response<Unit>

    // Backups
    @GET("api/client/servers/{server}/backups")
    suspend fun listBackups(
        @Path("server") serverIdentifier: String
    ): PteroListResponse<BackupItemWrapper>

    @POST("api/client/servers/{server}/backups")
    suspend fun createBackup(
        @Path("server") serverIdentifier: String,
        @Body request: CreateBackupRequest
    ): PteroItemResponse<BackupAttributes>

    @GET("api/client/servers/{server}/backups/{backup}/download")
    suspend fun getBackupDownload(
        @Path("server") serverIdentifier: String,
        @Path("backup") backupUuid: String
    ): PteroItemResponse<BackupDownloadAttributes>

    @POST("api/client/servers/{server}/backups/{backup}/restore")
    suspend fun restoreBackup(
        @Path("server") serverIdentifier: String,
        @Path("backup") backupUuid: String,
        @Body request: RestoreBackupRequest
    ): Response<Unit>

    @DELETE("api/client/servers/{server}/backups/{backup}")
    suspend fun deleteBackup(
        @Path("server") serverIdentifier: String,
        @Path("backup") backupUuid: String
    ): Response<Unit>
}
