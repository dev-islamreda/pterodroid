package com.example.data.api

import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

enum class ErrorCategory {
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    RATE_LIMITED,
    TIMEOUT,
    OFFLINE,
    SERVER_ERROR,
    GENERIC
}

sealed class PteroApiException(
    val category: ErrorCategory,
    val statusCode: Int?,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    class Unauthorized(message: String = "Authentication failed (401): The Client API Token is invalid or expired. Check your token in Account Settings.") :
        PteroApiException(ErrorCategory.UNAUTHORIZED, 401, message)
    class Forbidden(message: String = "Access denied (403): Your account lacks permission for this server or action.") :
        PteroApiException(ErrorCategory.FORBIDDEN, 403, message)
    class NotFound(message: String = "Resource not found (404): The server identifier or API endpoint does not exist.") :
        PteroApiException(ErrorCategory.NOT_FOUND, 404, message)
    class RateLimited(val retryAfterSeconds: Int? = null, message: String = "Rate limited (429): Too many requests to the panel. Please wait a moment before retrying.") :
        PteroApiException(ErrorCategory.RATE_LIMITED, 429, message)
    class Timeout(message: String = "Connection timed out: The Pterodactyl panel did not respond in time.") :
        PteroApiException(ErrorCategory.TIMEOUT, null, message)
    class Offline(message: String = "Panel unreachable: Cannot connect to the server. Check your internet connection and panel URL.") :
        PteroApiException(ErrorCategory.OFFLINE, null, message)
    class ServerError(code: Int, message: String = "Panel internal error ($code): The web server or database returned an error.") :
        PteroApiException(ErrorCategory.SERVER_ERROR, code, message)
    class Generic(code: Int?, message: String) :
        PteroApiException(ErrorCategory.GENERIC, code, message)
}

object PteroErrorParser {
    private val tokenRegex = Regex("(ptlc_[a-zA-Z0-9_-]{20,}|Bearer\\s+[a-zA-Z0-9_.-]+)", RegexOption.IGNORE_CASE)

    fun redactCredentials(input: String?, activeToken: String? = null): String {
        if (input.isNullOrBlank()) return ""
        var sanitized = input
        if (!activeToken.isNullOrBlank() && activeToken.length > 5) {
            sanitized = sanitized.replace(activeToken, "[REDACTED_TOKEN]")
        }
        return tokenRegex.replace(sanitized, "[REDACTED_CREDENTIAL]")
    }

    fun parse(throwable: Throwable, activeToken: String? = null): PteroApiException {
        if (throwable is CancellationException) {
            throw throwable
        }
        if (throwable is PteroApiException) {
            return throwable
        }
        if (throwable is HttpException) {
            val code = throwable.code()
            val responseBody = try {
                throwable.response()?.errorBody()?.string()
            } catch (_: Throwable) {
                null
            }
            val serverDetail = extractServerDetail(responseBody)
            val rawMsg = serverDetail ?: throwable.message()
            val sanitizedMsg = redactCredentials(rawMsg, activeToken)

            return when (code) {
                401 -> {
                    val detail = if (!serverDetail.isNullOrBlank()) ": $serverDetail" else ": The Client API Token is invalid or expired."
                    PteroApiException.Unauthorized("Authentication failed (401)$detail")
                }
                403 -> {
                    val detail = if (!serverDetail.isNullOrBlank()) ": $serverDetail" else ": Your account lacks permission for this server or action."
                    PteroApiException.Forbidden("Access denied (403)$detail")
                }
                404 -> {
                    val detail = if (!serverDetail.isNullOrBlank()) ": $serverDetail" else ": The server identifier or API endpoint does not exist."
                    PteroApiException.NotFound("Resource not found (404)$detail")
                }
                429 -> {
                    val retryAfter = throwable.response()?.headers()?.get("Retry-After")?.toIntOrNull()
                    val msg = if (retryAfter != null) {
                        "Rate limited (429): Too many requests. Please wait $retryAfter seconds."
                    } else {
                        "Rate limited (429): Too many requests to the panel. Please wait a moment."
                    }
                    PteroApiException.RateLimited(retryAfter, msg)
                }
                in 500..599 -> PteroApiException.ServerError(code, "Panel server error ($code): Server returned $sanitizedMsg")
                else -> PteroApiException.Generic(code, "HTTP Error ($code): $sanitizedMsg")
            }
        }
        if (throwable is SocketTimeoutException) {
            return PteroApiException.Timeout()
        }
        if (throwable is UnknownHostException || throwable is ConnectException) {
            return PteroApiException.Offline("Cannot reach panel host (${throwable.message ?: "Unknown host"}). Verify URL and network.")
        }
        if (throwable is IOException) {
            return PteroApiException.Offline(redactCredentials(throwable.localizedMessage ?: "Network error", activeToken))
        }
        val raw = throwable.localizedMessage ?: throwable.message ?: "An unexpected error occurred"
        return PteroApiException.Generic(null, redactCredentials(raw, activeToken))
    }

    private fun extractServerDetail(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val detailRegex = Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"")
            detailRegex.find(body)?.groupValues?.getOrNull(1)
        } catch (_: Throwable) {
            null
        }
    }
}
