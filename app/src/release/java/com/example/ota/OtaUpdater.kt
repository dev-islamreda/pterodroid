package com.example.ota

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Self-updater for APKs published by the GitHub workflow.
 * Reads ota.json, shows an update dialog over any screen, downloads the APK,
 * verifies its SHA-256 and hands it to the system installer.
 * Lives only in the release build (app/src/release), so AI Studio zips never overwrite it.
 */
object OtaUpdater : Application.ActivityLifecycleCallbacks {

    private const val MANIFEST_URL = "https://dev-islamreda.github.io/pterodroid/ota.json"
    private const val PREFS = "ota_prefs"
    private const val KEY_REQUIRED = "required_version_code"
    private const val CHECK_INTERVAL_MS = 10 * 60 * 1000L

    private class Remote(
        val versionCode: Long,
        val versionName: String,
        val apkUrl: String,
        val sha256: String,
        val minSupported: Long,
        val notes: String
    )

    private val main = Handler(Looper.getMainLooper())
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private lateinit var app: Application
    private var started = false
    private var current: Activity? = null
    private var dialog: AlertDialog? = null
    private var dialogHost: Activity? = null
    private var remote: Remote? = null
    private var lastCheck = 0L
    private var checking = false
    private var downloading = false
    private var snoozedVersion = -1L

    fun start(application: Application) {
        if (started) return
        started = true
        app = application
        application.registerActivityLifecycleCallbacks(this)
    }

    // ---------- lifecycle ----------

    override fun onActivityResumed(activity: Activity) {
        current = activity
        maybeCheck()
        refreshDialog()
    }

    override fun onActivityPaused(activity: Activity) {
        if (current === activity) current = null
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (dialogHost === activity) {
            dialog?.dismiss()
            dialog = null
            dialogHost = null
        }
        if (current === activity) current = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    // ---------- checking ----------

    private fun installedCode(): Long {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) return info.longVersionCode
        @Suppress("DEPRECATION")
        val legacy = info.versionCode.toLong()
        return legacy
    }

    private fun maybeCheck() {
        val now = System.currentTimeMillis()
        if (checking || downloading || now - lastCheck < CHECK_INTERVAL_MS) return
        checking = true
        Thread {
            try {
                val r = fetchRemote()
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_REQUIRED, r.minSupported).apply()
                main.post {
                    remote = r
                    lastCheck = System.currentTimeMillis()
                    checking = false
                    refreshDialog()
                }
            } catch (e: Exception) {
                main.post { checking = false }
            }
        }.start()
    }

    private fun fetchRemote(): Remote {
        val req = Request.Builder().url(MANIFEST_URL).header("Cache-Control", "no-cache").build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code)
            val text = resp.body?.string() ?: throw IOException("Empty response")
            val j = JSONObject(text)
            Remote(
                versionCode = j.getLong("versionCode"),
                versionName = j.optString("versionName"),
                apkUrl = j.getString("apkUrl"),
                sha256 = j.getString("sha256").lowercase(),
                minSupported = j.optLong("minSupportedVersionCode", 0L),
                notes = j.optString("releaseNotes")
            )
        }
    }

    // ---------- dialog ----------

    private fun refreshDialog() {
        val host = current ?: return
        if (host.isFinishing) return
        val installed = try {
            installedCode()
        } catch (e: Exception) {
            return
        }
        val r = remote
        val stored = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_REQUIRED, 0L)
        val required = maxOf(stored, r?.minSupported ?: 0L)
        val forced = required > installed
        val available = r != null && r.versionCode > installed

        if (!forced && !available) {
            dialog?.dismiss()
            dialog = null
            return
        }
        if (!forced && r != null && snoozedVersion == r.versionCode) return
        if (dialog?.isShowing == true && dialogHost === host) return
        showDialog(host, forced, r)
    }

    private fun message(r: Remote?, forced: Boolean): String {
        val sb = StringBuilder()
        sb.append(if (forced) "You must update to keep using the app." else "A new version is available.")
        if (r != null) {
            sb.append("\n\nVersion ").append(r.versionName)
            if (r.notes.isNotBlank()) sb.append("\n").append(r.notes)
        }
        return sb.toString()
    }

    private fun showDialog(host: Activity, forced: Boolean, r: Remote?) {
        dialog?.dismiss()
        val builder = AlertDialog.Builder(host)
            .setTitle(if (forced) "Update required" else "Update available")
            .setMessage(message(r, forced))
            .setCancelable(false)
            .setPositiveButton("Update", null)
        if (!forced) {
            builder.setNegativeButton("Later") { d, _ ->
                snoozedVersion = r?.versionCode ?: -1L
                d.dismiss()
            }
        }
        val dlg = builder.create()
        dlg.setCanceledOnTouchOutside(false)
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { startUpdate(host, dlg) }
        dialog = dlg
        dialogHost = host
    }

    // ---------- download + install ----------

    private fun startUpdate(host: Activity, dlg: AlertDialog) {
        if (downloading) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !host.packageManager.canRequestPackageInstalls()) {
            dlg.setMessage("Allow this app to install updates on the next screen, then come back and tap Update again.")
            host.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + host.packageName))
            )
            return
        }
        downloading = true
        val button = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
        button.isEnabled = false
        dlg.setMessage("Starting download...")
        Thread {
            try {
                val r = remote ?: fetchRemote()
                val apk = download(r) { pct -> main.post { dlg.setMessage("Downloading... $pct%") } }
                main.post {
                    remote = r
                    downloading = false
                    button.isEnabled = true
                    dlg.setMessage("Download verified. If the installer did not open, tap Update again.")
                    installApk(current ?: host, apk)
                }
            } catch (e: Exception) {
                main.post {
                    downloading = false
                    button.isEnabled = true
                    dlg.setMessage(
                        "Update failed: " + (e.message ?: "unknown error") +
                            "\n\nCheck your connection and tap Update to retry."
                    )
                }
            }
        }.start()
    }

    private fun download(r: Remote, onProgress: (Int) -> Unit): File {
        val dir = File(app.cacheDir, "ota")
        dir.mkdirs()
        val out = File(dir, "update.apk")
        if (out.exists()) out.delete()

        val req = Request.Builder().url(r.apkUrl).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed (HTTP " + resp.code + ")")
            val body = resp.body ?: throw IOException("Empty download")
            val total = body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastPct = -1
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (!hex.equals(r.sha256, ignoreCase = true)) {
                out.delete()
                throw IOException("Checksum mismatch. The download was corrupted, please try again.")
            }
        }
        return out
    }

    private fun installApk(host: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(host, host.packageName + ".ota-files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        host.startActivity(intent)
    }
}
