package com.example.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.security.KeystoreCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "pterodroid_security_prefs")

class SecurityPreferences(private val context: Context) {

    private val appLockEnabledKey = booleanPreferencesKey("app_lock_enabled")
    private val biometricsEnabledKey = booleanPreferencesKey("biometrics_enabled")
    private val passcodeHashEncKey = stringPreferencesKey("passcode_hash_enc")
    private val passcodeSaltEncKey = stringPreferencesKey("passcode_salt_enc")
    private val lockTimeoutKey = intPreferencesKey("lock_timeout_seconds")
    private val hideRecentAppsKey = booleanPreferencesKey("hide_recent_apps")
    private val failedAttemptsKey = intPreferencesKey("failed_attempts")
    private val lockoutUntilKey = longPreferencesKey("lockout_until_millis")

    val isAppLockEnabled: Flow<Boolean> = context.securityDataStore.data.map { it[appLockEnabledKey] ?: false }
    val isBiometricsEnabled: Flow<Boolean> = context.securityDataStore.data.map { it[biometricsEnabledKey] ?: false }
    val lockTimeoutSeconds: Flow<Int> = context.securityDataStore.data.map { it[lockTimeoutKey] ?: 0 }
    val isHideRecentApps: Flow<Boolean> = context.securityDataStore.data.map { it[hideRecentAppsKey] ?: false }
    val failedAttempts: Flow<Int> = context.securityDataStore.data.map { it[failedAttemptsKey] ?: 0 }
    val lockoutUntilMillis: Flow<Long> = context.securityDataStore.data.map { it[lockoutUntilKey] ?: 0L }

    suspend fun hasPasscode(): Boolean {
        val prefs = context.securityDataStore.data.first()
        val encHash = prefs[passcodeHashEncKey]
        return !encHash.isNullOrBlank()
    }

    suspend fun setPasscode(passcode: String) {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val hash = hashPasscode(passcode, salt)
        val saltB64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)

        val encHash = KeystoreCrypto.encrypt(hash)
        val encSalt = KeystoreCrypto.encrypt(saltB64)

        context.securityDataStore.edit { prefs ->
            prefs[passcodeHashEncKey] = encHash
            prefs[passcodeSaltEncKey] = encSalt
            prefs[appLockEnabledKey] = true
            prefs[failedAttemptsKey] = 0
            prefs[lockoutUntilKey] = 0L
        }
    }

    suspend fun verifyPasscode(candidate: String): Boolean {
        val prefs = context.securityDataStore.data.first()
        val encHash = prefs[passcodeHashEncKey] ?: return false
        val encSalt = prefs[passcodeSaltEncKey] ?: return false

        return try {
            val storedHash = KeystoreCrypto.decrypt(encHash)
            val saltB64 = KeystoreCrypto.decrypt(encSalt)
            val salt = android.util.Base64.decode(saltB64, android.util.Base64.NO_WRAP)
            val candidateHash = hashPasscode(candidate, salt)

            MessageDigest.isEqual(
                storedHash.toByteArray(Charsets.UTF_8),
                candidateHash.toByteArray(Charsets.UTF_8)
            )
        } catch (_: Exception) {
            false
        }
    }

    suspend fun recordFailedAttempt(): Long {
        var lockoutExpiry = 0L
        context.securityDataStore.edit { prefs ->
            val currentAttempts = (prefs[failedAttemptsKey] ?: 0) + 1
            prefs[failedAttemptsKey] = currentAttempts

            if (currentAttempts >= 5) {
                // Lock for 30s after 5 wrong attempts, double wait after each further failure (30, 60, 120...)
                val multiplier = 1L shl (currentAttempts - 5).coerceAtMost(6)
                val waitSeconds = 30L * multiplier
                lockoutExpiry = System.currentTimeMillis() + (waitSeconds * 1000L)
                prefs[lockoutUntilKey] = lockoutExpiry
            }
        }
        return lockoutExpiry
    }

    suspend fun recordSuccessfulUnlock() {
        context.securityDataStore.edit { prefs ->
            prefs[failedAttemptsKey] = 0
            prefs[lockoutUntilKey] = 0L
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.securityDataStore.edit { prefs ->
            prefs[appLockEnabledKey] = enabled
            if (!enabled) {
                prefs[failedAttemptsKey] = 0
                prefs[lockoutUntilKey] = 0L
            }
        }
    }

    suspend fun setBiometricsEnabled(enabled: Boolean) {
        context.securityDataStore.edit { prefs ->
            prefs[biometricsEnabledKey] = enabled
        }
    }

    suspend fun setLockTimeoutSeconds(seconds: Int) {
        context.securityDataStore.edit { prefs ->
            prefs[lockTimeoutKey] = seconds
        }
    }

    suspend fun setHideRecentApps(enabled: Boolean) {
        context.securityDataStore.edit { prefs ->
            prefs[hideRecentAppsKey] = enabled
        }
    }

    suspend fun clearAllSecurityData() {
        context.securityDataStore.edit { prefs ->
            prefs.remove(appLockEnabledKey)
            prefs.remove(biometricsEnabledKey)
            prefs.remove(passcodeHashEncKey)
            prefs.remove(passcodeSaltEncKey)
            prefs.remove(lockTimeoutKey)
            prefs.remove(hideRecentAppsKey)
            prefs.remove(failedAttemptsKey)
            prefs.remove(lockoutUntilKey)
        }
    }

    private fun hashPasscode(passcode: String, salt: ByteArray): String {
        val spec = PBEKeySpec(passcode.toCharArray(), salt, 12000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
    }
}
