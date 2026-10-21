package com.example

import com.example.data.api.ErrorCategory
import com.example.data.api.PteroApiException
import com.example.data.api.PteroErrorParser
import com.example.data.api.UrlNormalizer
import com.example.data.model.FileAttributes
import com.example.data.security.KeystoreCrypto
import com.example.ui.components.FormatUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class NexaPanelUnitTest {

    @Test
    fun testUrlNormalizer_addsHttpsAndTrailingSlash() {
        val input = "panel.pterodactyl.app"
        val normalized = UrlNormalizer.normalize(input)
        assertEquals("https://panel.pterodactyl.app/", normalized)
    }

    @Test
    fun testUrlNormalizer_preservesHttpAndPort() {
        val input = "http://192.168.1.100:8080"
        val normalized = UrlNormalizer.normalize(input)
        assertEquals("http://192.168.1.100:8080/", normalized)
    }

    @Test
    fun testUrlNormalizer_stripsAccidentalClientApiPath() {
        val input = "https://panel.example.com/api/client"
        val normalized = UrlNormalizer.normalize(input)
        assertEquals("https://panel.example.com/", normalized)
    }

    @Test
    fun testUrlNormalizer_requireHttpsForRemote_allowsHttps() {
        val result = UrlNormalizer.requireHttpsForRemote("panel.pterodactyl.app")
        assertEquals("https://panel.pterodactyl.app/", result)
    }

    @Test
    fun testUrlNormalizer_requireHttpsForRemote_allowsLocalHttp() {
        val localhost = UrlNormalizer.requireHttpsForRemote("http://localhost:8080")
        assertEquals("http://localhost:8080/", localhost)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testUrlNormalizer_requireHttpsForRemote_rejectsRemoteHttp() {
        UrlNormalizer.requireHttpsForRemote("http://panel.unencrypted-production.com")
    }

    @Test
    fun testErrorParser_handles401Unauthorized() {
        val errorBody = "{\"errors\":[{\"code\":\"UnauthorizedHttpException\",\"detail\":\"Invalid token\"}]}".toResponseBody("application/json".toMediaType())
        val httpException = HttpException(Response.error<Unit>(401, errorBody))
        val parsed = PteroErrorParser.parse(httpException)
        assertTrue(parsed is PteroApiException.Unauthorized)
        assertEquals(ErrorCategory.UNAUTHORIZED, parsed.category)
        assertEquals(401, parsed.statusCode)
    }

    @Test
    fun testCredentialRedaction_stripsApiTokenFromErrors() {
        val secretToken = "ptlc_abcdef1234567890abcdef1234567890"
        val rawMessage = "Failed connecting with Bearer $secretToken at host panel.example.com"
        val sanitized = PteroErrorParser.redactCredentials(rawMessage, secretToken)
        assertFalse(sanitized.contains(secretToken))
        assertTrue(sanitized.contains("[REDACTED_CREDENTIAL]") || sanitized.contains("[REDACTED_TOKEN]"))
    }

    @Test
    fun testKeystoreCrypto_encryptionRoundTrip() {
        val originalToken = "ptlc_secret_game_server_token_12345"
        val encrypted = KeystoreCrypto.encrypt(originalToken)
        assertFalse(encrypted.contains(originalToken))
        assertTrue(encrypted.startsWith("enc:v1:"))
        val decrypted = KeystoreCrypto.decrypt(encrypted)
        assertEquals(originalToken, decrypted)
    }

    @Test
    fun testFileAttributes_archiveDetection() {
        val zipFile = FileAttributes(name = "server_world.zip", isFile = true)
        val normalFile = FileAttributes(name = "server.properties", isFile = true)
        assertTrue(zipFile.isArchive())
        assertFalse(normalFile.isArchive())
    }

    @Test
    fun testFormatBytes() {
        assertEquals("0 B", FormatUtils.formatBytes(0))
        assertEquals("1.0 KB", FormatUtils.formatBytes(1024))
        assertEquals("1.0 MB", FormatUtils.formatBytes(1024 * 1024))
    }

    @Test
    fun testFormatUptime() {
        assertEquals("Offline", FormatUtils.formatUptime(0))
        val oneHourThirtyMins = (90 * 60 * 1000).toLong()
        val formatted = FormatUtils.formatUptime(oneHourThirtyMins)
        assertTrue(formatted.contains("1h") && formatted.contains("30m"))
    }
}
