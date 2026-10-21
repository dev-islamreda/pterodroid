package com.example.data.logging

import android.content.Context
import android.os.Build
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.data.api.PteroApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ErrorReporter {

    private const val DISCORD_WEBHOOK_URL =
        "https://discord.com/api/webhooks/1554817230045450321/FJ8Kwaop-SRtaYqRDKQA6wAEn2aI0XaNuVeJhNg8V1DoJ2GFbcsBPxFLXLpFW2lB3Y6T"
    private const val PREFS_NAME = "error_reporter_prefs"
    private const val KEY_ENABLED = "error_reports_enabled"
    private const val KEY_INSTALL_ID = "anonymous_install_id"
    private const val CRASH_FILE_NAME = "crash_pending_report.json"
    private const val MIN_INTERVAL_MS = 30_000L // At most 1 report per 30 seconds
    private const val TRACE_COOLDOWN_MS = 600_000L // 10 minutes duplicate trace suppression
    private const val MAX_DAILY_REPORTS = 20

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastReportTime = 0L
    private val recentTraces = ConcurrentHashMap<String, Long>()
    private var dailyDate: String = ""
    private var dailyCount = 0
    private var currentScreenName: String = "MAIN"
    private var appContext: Context? = null
    private var defaultUncaughtHandler: Thread.UncaughtExceptionHandler? = null
    private var isInitialized = false

    private val tokenRegex = Regex("(ptlc_[a-zA-Z0-9_-]{10,}|Bearer\\s+[a-zA-Z0-9_.-]+|AIza[0-9A-Za-z-_]{35})", RegexOption.IGNORE_CASE)
    private val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val urlRegex = Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        val app = context.applicationContext
        appContext = app

        // Check and send pending saved crash report on app start
        scope.launch {
            checkAndSendPendingCrash(app)
        }

        // Setup uncaught exception handler
        defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (isReportingEnabled(app) && !isNormalUserSituation(throwable)) {
                    val reportJson = buildReportPayload(
                        throwable = throwable,
                        screen = currentScreenName,
                        isCrash = true
                    )
                    saveCrashToDisk(app, reportJson)
                }
            } catch (_: Throwable) {
            } finally {
                defaultUncaughtHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun updateCurrentScreen(screenName: String) {
        currentScreenName = screenName
    }

    fun isReportingEnabled(context: Context? = appContext): Boolean {
        val ctx = context ?: return false
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    fun setReportingEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getAnonymousInstallId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_INSTALL_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString().take(12)
            prefs.edit().putString(KEY_INSTALL_ID, id).apply()
        }
        return id
    }

    fun clearLocalData(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        try {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            if (file.exists()) file.delete()
        } catch (_: Exception) {
        }
    }

    fun reportHandledError(
        throwable: Throwable,
        screen: String = currentScreenName,
        extraContext: String? = null
    ) {
        val ctx = appContext ?: return
        if (!isReportingEnabled(ctx)) return
        if (isNormalUserSituation(throwable)) return

        scope.launch {
            try {
                val payload = buildReportPayload(throwable, screen, isCrash = false, extraContext = extraContext)
                sendToDiscord(payload)
            } catch (_: Throwable) {
            }
        }
    }

    private fun isNormalUserSituation(t: Throwable): Boolean {
        if (t is CancellationException) return true
        if (t is GetCredentialCancellationException) return true
        if (t is UnknownHostException || t is ConnectException || t is SocketTimeoutException) return true
        if (t is PteroApiException.Offline ||
            t is PteroApiException.Timeout ||
            t is PteroApiException.Unauthorized ||
            t is PteroApiException.Forbidden ||
            t is PteroApiException.RateLimited
        ) {
            return true
        }
        val msg = (t.message ?: "").lowercase()
        if (msg.contains("user cancelled") ||
            msg.contains("canceled") ||
            msg.contains("offline") ||
            msg.contains("timed out") ||
            msg.contains("401") ||
            msg.contains("403") ||
            msg.contains("429")
        ) {
            return true
        }
        return false
    }

    fun redact(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var s = input
        s = tokenRegex.replace(s, "[token]")
        s = emailRegex.replace(s, "[email]")
        s = urlRegex.replace(s, "[url]")
        return s
    }

    private fun buildReportPayload(
        throwable: Throwable,
        screen: String,
        isCrash: Boolean,
        extraContext: String? = null
    ): String {
        val ctx = appContext
        val installId = if (ctx != null) getAnonymousInstallId(ctx) else "anonymous"
        val appVersion = "1.0"
        val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val device = "${Build.MANUFACTURER.capitalize(Locale.ROOT)} ${Build.MODEL}"
        val exceptionClass = throwable.javaClass.simpleName
        val exceptionMessage = redact(throwable.message ?: "No error message")
        val rawStackTrace = throwable.stackTraceToString()
        val stackLines = rawStackTrace.lines().take(25).joinToString("\n")
        val sanitizedStack = redact(stackLines)

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val timestamp = sdf.format(Date())

        val title = if (isCrash) "🚨 Android Crash Report" else "⚠️ Handled Exception Report"
        val color = if (isCrash) 15158332 else 15844367 // Red for crash, Gold/Orange for handled

        val fields = JSONArray().apply {
            put(JSONObject().apply {
                put("name", "App Version")
                put("value", appVersion)
                put("inline", true)
            })
            put(JSONObject().apply {
                put("name", "Android OS")
                put("value", androidVersion)
                put("inline", true)
            })
            put(JSONObject().apply {
                put("name", "Device")
                put("value", device)
                put("inline", true)
            })
            put(JSONObject().apply {
                put("name", "Screen")
                put("value", screen)
                put("inline", true)
            })
            put(JSONObject().apply {
                put("name", "Install ID")
                put("value", installId)
                put("inline", true)
            })
            put(JSONObject().apply {
                put("name", "Timestamp")
                put("value", timestamp)
                put("inline", false)
            })
            put(JSONObject().apply {
                put("name", "Exception")
                put("value", "$exceptionClass: $exceptionMessage")
                put("inline", false)
            })
            if (!extraContext.isNullOrBlank()) {
                put(JSONObject().apply {
                    put("name", "Context")
                    put("value", redact(extraContext).take(200))
                    put("inline", false)
                })
            }
            put(JSONObject().apply {
                put("name", "Stack Trace (top 25 lines)")
                put("value", "```\n${sanitizedStack.take(950)}\n```")
                put("inline", false)
            })
        }

        val embed = JSONObject().apply {
            put("title", title)
            put("color", color)
            put("fields", fields)
        }

        val root = JSONObject().apply {
            put("allowed_mentions", JSONObject().apply { put("parse", JSONArray()) })
            put("embeds", JSONArray().apply { put(embed) })
        }

        return root.toString()
    }

    private fun saveCrashToDisk(context: Context, payloadJson: String) {
        try {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            file.writeText(payloadJson, Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun checkAndSendPendingCrash(context: Context) {
        try {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            if (file.exists()) {
                val payload = file.readText(Charsets.UTF_8)
                file.delete()
                if (isReportingEnabled(context)) {
                    sendToDiscord(payload)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun sendToDiscord(payloadJson: String) {
        val now = System.currentTimeMillis()
        // Rate limit: at most 1 report per 30 seconds
        if (now - lastReportTime < MIN_INTERVAL_MS) {
            return
        }

        // Daily limit: max 20 reports per calendar day
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
        if (dailyDate != today) {
            dailyDate = today
            dailyCount = 0
        }
        if (dailyCount >= MAX_DAILY_REPORTS) {
            return
        }

        // Cooldown: No identical stack trace within 10 minutes
        val hash = payloadJson.hashCode().toString()
        val lastSeen = recentTraces[hash]
        if (lastSeen != null && (now - lastSeen < TRACE_COOLDOWN_MS)) {
            return
        }

        lastReportTime = now
        recentTraces[hash] = now
        dailyCount++

        // Clean older trace hashes
        recentTraces.entries.removeIf { now - it.value > TRACE_COOLDOWN_MS }

        try {
            val body = payloadJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(DISCORD_WEBHOOK_URL)
                .post(body)
                .build()
            val response = httpClient.newCall(request).execute()
            response.close()
        } catch (_: Exception) {
            // Drop silently on failure or 429; never crash
        }
    }
}
