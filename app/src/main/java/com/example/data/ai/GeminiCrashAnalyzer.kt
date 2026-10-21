package com.example.data.ai

import android.util.Log
import com.example.BuildConfig
import com.example.data.api.ApiClientFactory
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = false)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = false)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = false)
data class GeminiPart(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = false)
data class GeminiGenerationConfig(
    @Json(name = "thinkingConfig") val thinkingConfig: GeminiThinkingConfig? = null,
    @Json(name = "temperature") val temperature: Float? = null
)

@JsonClass(generateAdapter = false)
data class GeminiThinkingConfig(
    @Json(name = "thinkingLevel") val thinkingLevel: String = "high"
)

@JsonClass(generateAdapter = false)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null,
    @Json(name = "error") val error: GeminiApiError? = null
)

@JsonClass(generateAdapter = false)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent? = null,
    @Json(name = "finishReason") val finishReason: String? = null
)

@JsonClass(generateAdapter = false)
data class GeminiApiError(
    @Json(name = "message") val message: String? = null,
    @Json(name = "code") val code: Int? = null
)

data class LogAnalysisResult(
    val analysisText: String,
    val isSuccess: Boolean,
    val error: String? = null
)

object GeminiCrashAnalyzer {

    private const val TAG = "GeminiCrashAnalyzer"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    private const val MODEL_NAME = "gemini-2.5-flash"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val reqAdapter by lazy { ApiClientFactory.moshi.adapter(GeminiRequest::class.java) }
    private val respAdapter by lazy { ApiClientFactory.moshi.adapter(GeminiResponse::class.java) }

    suspend fun analyzeConsoleLogs(
        serverName: String,
        recentLogs: List<String>
    ): LogAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            (field.get(null) as? String) ?: ""
        } catch (_: Exception) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "your_api_key_here") {
            return@withContext LogAnalysisResult(
                analysisText = "",
                isSuccess = false,
                error = "Gemini API key is not configured in the Secrets panel."
            )
        }

        val logSnippet = recentLogs.takeLast(70).joinToString("\n")
        if (logSnippet.isBlank()) {
            return@withContext LogAnalysisResult(
                analysisText = "No log output available to analyze.",
                isSuccess = true,
                error = null
            )
        }

        val prompt = """
            You are an expert game server systems administrator and debugger for Pterodactyl-managed servers.
            Analyze the following console log snippet for server "$serverName".
            
            Look for:
            1. Crash stack traces, fatal exceptions, out of memory (OOM), or SIGKILL/SIGTERM indicators.
            2. Missing dependencies, corrupted world files, plugin/mod incompatibilities, or binding port conflicts.
            3. Server health bottlenecks.
            
            Provide a clean, structured diagnostic with:
            - **Status / Diagnosis**: Clear one-line summary of what happened.
            - **Severity**: Critical, Warning, or Normal.
            - **Root Cause**: Specific error explanation.
            - **Actionable Fix**: Exact steps to resolve (e.g. command to run, file to edit, memory to allocate).
            
            Console Logs:
            ```
            $logSnippet
            ```
        """.trimIndent()

        val requestBodyObject = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            generationConfig = GeminiGenerationConfig(
                thinkingConfig = GeminiThinkingConfig(thinkingLevel = "high"),
                temperature = 0.2f
            ),
            systemInstruction = GeminiContent(
                parts = listOf(
                    GeminiPart(text = "You are a professional server diagnostic engine specializing in Pterodactyl nodes, Docker containers, Minecraft, SteamCMD, and Linux game servers.")
                )
            )
        )

        val jsonRequest = reqAdapter.toJson(requestBodyObject)
        val endpointUrl = "$BASE_URL$MODEL_NAME:generateContent?key=$apiKey"

        try {
            val httpRequest = Request.Builder()
                .url(endpointUrl)
                .post(jsonRequest.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API error ${response.code}: $responseBody")
                return@withContext LogAnalysisResult(
                    analysisText = "",
                    isSuccess = false,
                    error = "Gemini API request failed (${response.code}): ${response.message}"
                )
            }

            val parsedResponse = respAdapter.fromJson(responseBody)
            val candidateText = parsedResponse?.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }
                ?.joinToString("\n")
                ?.trim()

            if (!candidateText.isNullOrBlank()) {
                LogAnalysisResult(
                    analysisText = candidateText,
                    isSuccess = true,
                    error = null
                )
            } else {
                LogAnalysisResult(
                    analysisText = "No diagnostic insight could be extracted from the server logs.",
                    isSuccess = false,
                    error = parsedResponse?.error?.message ?: "Empty response from Gemini."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini call exception: ${e.message}", e)
            LogAnalysisResult(
                analysisText = "",
                isSuccess = false,
                error = "Failed to reach Gemini: ${e.localizedMessage}"
            )
        }
    }
}
