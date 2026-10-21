package com.example.data.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

data class CertFingerprints(val sha1: String, val sha256: String)

/**
 * Reads the fingerprint of the certificate that actually signed the installed APK,
 * so it can be compared with the fingerprints registered in the Firebase console.
 */
object SigningCertificate {

    /** SHA-1 fingerprints registered for com.skycord.pterodroid in Firebase project pterodroid-app. */
    val REGISTERED_SHA1: List<String> = listOf(
        "88:19:49:3C:67:48:04:99:B5:3B:F9:64:DB:A3:56:14:C4:9C:8A:1B"
    )

    fun isRegistered(sha1: String): Boolean =
        REGISTERED_SHA1.any { it.equals(sha1, ignoreCase = true) }

    fun read(context: Context): CertFingerprints? {
        return try {
            val pm = context.packageManager
            val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            }
            if (certBytes == null) null
            else CertFingerprints(
                sha1 = digest("SHA-1", certBytes),
                sha256 = digest("SHA-256", certBytes)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun digest(algorithm: String, bytes: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(bytes)
            .joinToString(":") { "%02X".format(it) }
}
