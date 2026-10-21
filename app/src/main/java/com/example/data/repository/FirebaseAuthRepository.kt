package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.example.data.security.SigningCertificate
import com.example.data.model.SavedConnection
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val provider: String = "google.com",
    val themeMode: String = "DARK",
    val appLanguage: String = "en",
    val isAiEnabled: Boolean = true
)

data class AuthDiagnostics(
    val exceptionClass: String,
    val exceptionType: String? = null,
    val statusCode: Int? = null,
    val statusCodeName: String? = null,
    val rawMessage: String = "",
    val causes: List<String> = emptyList(),
    val isCancellation: Boolean = false,
    val isDeveloperConfigError: Boolean = false,
    val userFacingMessage: String = "",
    val formattedFullReport: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
)

class FirebaseAuthRepository(
    private val context: Context,
    private val appScope: CoroutineScope
) {
    private val tag = "FirebaseAuthRepo"
    private val auth: FirebaseAuth = Firebase.auth
    private val credentialManager: CredentialManager by lazy { CredentialManager.create(context) }
    private val firestore: FirebaseFirestore by lazy {
        val configuredId = try {
            context.getString(R.string.firestore_database_id)
        } catch (_: Exception) {
            null
        }
        if (!configuredId.isNullOrBlank() && configuredId != "(default)") {
            try {
                FirebaseFirestore.getInstance(configuredId)
            } catch (e: Exception) {
                Log.w(tag, "Failed to get Firestore with configured database ID $configuredId, falling back to default instance", e)
                FirebaseFirestore.getInstance()
            }
        } else {
            FirebaseFirestore.getInstance()
        }
    }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authDiagnostics = MutableStateFlow<AuthDiagnostics?>(null)
    val authDiagnostics: StateFlow<AuthDiagnostics?> = _authDiagnostics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _syncedPanels = MutableStateFlow<List<SavedConnection>>(emptyList())
    val syncedPanels: StateFlow<List<SavedConnection>> = _syncedPanels.asStateFlow()

    private val _isCredentialsLoaded = MutableStateFlow(false)
    val isCredentialsLoaded: StateFlow<Boolean> = _isCredentialsLoaded.asStateFlow()

    private val _isPanelsSyncLoaded = MutableStateFlow(false)
    val isPanelsSyncLoaded: StateFlow<Boolean> = _isPanelsSyncLoaded.asStateFlow()

    private val _isAuthInitialized = MutableStateFlow(false)
    val isAuthInitialized: StateFlow<Boolean> = _isAuthInitialized.asStateFlow()

    private var userDocListener: ListenerRegistration? = null
    private var panelsListener: ListenerRegistration? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user
            _isAuthInitialized.value = true
            if (user != null) {
                attachFirestoreListeners(user.uid)
                syncInitialUserProfile(user)
            } else {
                detachFirestoreListeners()
                _userProfile.value = null
                _syncedPanels.value = emptyList()
                _isCredentialsLoaded.value = true
                _isPanelsSyncLoaded.value = true
            }
        }
    }

    private fun detachFirestoreListeners() {
        userDocListener?.remove()
        userDocListener = null
        panelsListener?.remove()
        panelsListener = null
    }

    private fun attachFirestoreListeners(userId: String) {
        detachFirestoreListeners()
        _isCredentialsLoaded.value = false
        _isPanelsSyncLoaded.value = false

        val userDoc = firestore.collection("users").document(userId)
        userDocListener = userDoc.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(tag, "Firestore user listener error: ${error.message}")
                _isCredentialsLoaded.value = true
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profile = UserProfile(
                    uid = userId,
                    email = snapshot.getString("email") ?: auth.currentUser?.email ?: "",
                    displayName = snapshot.getString("displayName") ?: auth.currentUser?.displayName ?: "User",
                    photoUrl = snapshot.getString("photoUrl") ?: auth.currentUser?.photoUrl?.toString() ?: "",
                    provider = "google.com",
                    themeMode = snapshot.getString("themeMode") ?: "DARK",
                    appLanguage = snapshot.getString("appLanguage") ?: "en",
                    isAiEnabled = snapshot.getBoolean("isAiEnabled") ?: true
                )
                _userProfile.value = profile
            }
            _isCredentialsLoaded.value = true
        }

        val panelsColl = firestore.collection("users").document(userId).collection("saved_panels")
        panelsListener = panelsColl.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(tag, "Firestore saved_panels listener error: ${error.message}")
                _isPanelsSyncLoaded.value = true
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val panels = snapshot.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val label = doc.getString("label") ?: "Pterodactyl Panel"
                    val url = doc.getString("panelUrl") ?: return@mapNotNull null
                    val token = doc.getString("apiToken") ?: return@mapNotNull null
                    val lastUsed = doc.getLong("lastUsedTimestamp") ?: System.currentTimeMillis()
                    SavedConnection(
                        id = id,
                        label = label,
                        panelUrl = url,
                        apiToken = token,
                        lastUsedTimestamp = lastUsed
                    )
                }
                _syncedPanels.value = panels
                _isPanelsSyncLoaded.value = true
            }
        }
    }

    private fun syncInitialUserProfile(user: FirebaseUser) {
        appScope.launch(Dispatchers.IO) {
            try {
                val userDoc = firestore.collection("users").document(user.uid)
                val payload = hashMapOf<String, Any>(
                    "userId" to user.uid,
                    "email" to (user.email ?: ""),
                    "displayName" to (user.displayName ?: user.email?.substringBefore("@") ?: "User"),
                    "provider" to "google.com",
                    "photoUrl" to (user.photoUrl?.toString() ?: ""),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDoc.set(payload, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w(tag, "Failed to sync initial user profile: ${e.message}")
            }
        }
    }

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun signInWithGoogle(
        callingContext: Context,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            val err = "Google Web Client ID missing from configuration."
            _authError.value = err
            onError(err)
            return
        }

        _isLoading.value = true
        _authError.value = null

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        val targetContext = callingContext.findActivity() ?: callingContext

        appScope.launch {
            try {
                Log.d(tag, "Invoking credentialManager.getCredential with client_id=$clientId")
                val result = credentialManager.getCredential(targetContext, request)
                val credential = result.credential
                Log.d(tag, "Received credential of type: ${credential.type}")

                var googleIdToken: String? = null
                if (credential is CustomCredential) {
                    try {
                        googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    } catch (e: Exception) {
                        Log.w(tag, "GoogleIdTokenCredential.createFrom failed: ${e.message}")
                    }
                    if (googleIdToken.isNullOrBlank()) {
                        googleIdToken = credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN")
                            ?: credential.data.getString("id_token")
                            ?: credential.data.getString("idToken")
                    }
                    if (googleIdToken.isNullOrBlank()) {
                        for (key in credential.data.keySet()) {
                            val v = credential.data.getString(key)
                            if (!v.isNullOrBlank() && (key.contains("token", ignoreCase = true) || v.startsWith("eyJ"))) {
                                googleIdToken = v
                                break
                            }
                        }
                    }
                }

                if (!googleIdToken.isNullOrBlank()) {
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = withContext(Dispatchers.IO) {
                        auth.signInWithCredential(authCredential).await()
                    }
                    val user = authResult.user ?: auth.currentUser
                    _currentUser.value = user
                    _isLoading.value = false
                    _authError.value = null
                    Log.d(tag, "Successfully signed in with Google: ${user?.email}")
                    if (user != null) {
                        attachFirestoreListeners(user.uid)
                        syncInitialUserProfile(user)
                    }
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                } else {
                    val err = "Unable to process Google credential (type: ${credential.type})."
                    _isLoading.value = false
                    _authError.value = err
                    withContext(Dispatchers.Main) {
                        onError(err)
                    }
                }
            } catch (e: GetCredentialCancellationException) {
                val diag = extractDiagnostics(e)
                Log.w(tag, "Google Sign-In cancelled / aborted by Play Services:\n${diag.formattedFullReport}")
                _isLoading.value = false
                _authDiagnostics.value = diag
                _authError.value = diag.userFacingMessage
                withContext(Dispatchers.Main) {
                    onError(diag.userFacingMessage)
                }
            } catch (e: GetCredentialException) {
                val diag = extractDiagnostics(e)
                Log.e(tag, "CredentialManager error: ${e.type} - ${e.message}\n${diag.formattedFullReport}", e)
                _isLoading.value = false
                _authDiagnostics.value = diag
                _authError.value = diag.userFacingMessage
                withContext(Dispatchers.Main) {
                    onError(diag.userFacingMessage)
                }
            } catch (e: Exception) {
                val diag = extractDiagnostics(e)
                Log.e(tag, "Google Sign-In unexpected error:\n${diag.formattedFullReport}", e)
                _isLoading.value = false
                _authDiagnostics.value = diag
                _authError.value = diag.userFacingMessage
                withContext(Dispatchers.Main) {
                    onError(diag.userFacingMessage)
                }
            }
        }
    }

    suspend fun saveOrUpdatePanelInCloud(conn: SavedConnection) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        val cleanUrl = conn.panelUrl.trim()
        val cleanToken = conn.apiToken.trim()
        val cleanLabel = conn.label.trim().ifBlank { cleanUrl.removePrefix("https://").removePrefix("http://").removeSuffix("/") }
        val panelId = if (conn.id.isNotBlank()) conn.id else UUID.randomUUID().toString()

        try {
            val panelDoc = firestore.collection("users")
                .document(uid)
                .collection("saved_panels")
                .document(panelId)

            val panelPayload = hashMapOf<String, Any>(
                "id" to panelId,
                "userId" to uid,
                "label" to cleanLabel,
                "panelUrl" to cleanUrl,
                "apiToken" to cleanToken,
                "lastUsedTimestamp" to System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            panelDoc.set(panelPayload, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(tag, "Error saving panel credentials to Firestore: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteSavedPanelFromCloud(panelId: String) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        try {
            firestore.collection("users")
                .document(uid)
                .collection("saved_panels")
                .document(panelId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(tag, "Error deleting panel from Firestore: ${e.message}")
        }
    }

    suspend fun syncPreferencesToCloud(
        themeMode: String? = null,
        appLanguage: String? = null,
        isAiEnabled: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        try {
            val userDoc = firestore.collection("users").document(uid)
            val payload = hashMapOf<String, Any>()
            themeMode?.let { payload["themeMode"] = it }
            appLanguage?.let { payload["appLanguage"] = it }
            isAiEnabled?.let { payload["isAiEnabled"] = it }
            payload["updatedAt"] = FieldValue.serverTimestamp()
            userDoc.set(payload, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w(tag, "Error syncing preferences to Firestore: ${e.message}")
        }
    }

    fun signOut(onComplete: () -> Unit = {}) {
        auth.signOut()
        detachFirestoreListeners()
        _currentUser.value = null
        _userProfile.value = null
        _syncedPanels.value = emptyList()
        appScope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {
            } finally {
                onComplete()
            }
        }
    }

    fun clearError() {
        _authError.value = null
        _authDiagnostics.value = null
    }

    private fun extractDiagnostics(e: Throwable): AuthDiagnostics {
        val exceptionClass = e::class.qualifiedName ?: e.javaClass.name
        val credentialType = (e as? GetCredentialException)?.type
        val rawMessage = e.message ?: e.localizedMessage ?: "No error message provided"
        val causes = mutableListOf<String>()
        var curr = e.cause
        var extractedStatusCode: Int? = null

        try {
            val code = e.javaClass.getMethod("getStatusCode").invoke(e) as? Int
            if (code != null) extractedStatusCode = code
        } catch (_: Throwable) {}

        while (curr != null) {
            val causeName = curr::class.qualifiedName ?: curr.javaClass.name
            causes.add("$causeName: ${curr.message}")
            if (extractedStatusCode == null) {
                try {
                    val code = curr.javaClass.getMethod("getStatusCode").invoke(curr) as? Int
                    if (code != null) extractedStatusCode = code
                } catch (_: Throwable) {}
            }
            curr = curr.cause
        }

        if (extractedStatusCode == null) {
            val codeRegex = Regex("""\b(28\d{3}|12500|10|16|7)\b""").find(rawMessage)
            if (codeRegex != null) {
                extractedStatusCode = codeRegex.value.toIntOrNull()
            }
        }

        val statusCodeName = when (extractedStatusCode) {
            28444 -> "DEVELOPER_CONSOLE_NOT_CONFIGURED (Fingerprint / Package mismatch in Google Cloud)"
            16 -> "CANCELED (Google Play Services dismissed intent or user cancelled)"
            10 -> "DEVELOPER_ERROR (OAuth Client ID misconfiguration in Google Cloud Console)"
            12500 -> "SIGN_IN_FAILED (Google Play Services configuration or account error)"
            7 -> "NETWORK_ERROR (Unable to communicate with Google Play Services)"
            else -> if (extractedStatusCode != null) "STATUS_CODE_$extractedStatusCode" else null
        }

        val isCancel = e is GetCredentialCancellationException || extractedStatusCode == 16
        val isDevError = extractedStatusCode == 28444 || extractedStatusCode == 10 ||
                rawMessage.contains("Developer console", ignoreCase = true) ||
                rawMessage.contains("DEVELOPER_ERROR", ignoreCase = true)

        val codeBadge = if (extractedStatusCode != null) " [Code $extractedStatusCode: $statusCodeName]" else ""
        val typeBadge = if (!credentialType.isNullOrBlank()) " ($credentialType)" else ""
        val simpleName = exceptionClass.substringAfterLast('.')

        val activeProjectId = try { context.getString(R.string.project_id) } catch (_: Exception) { "pterodroid-app" }
        val activeWebClientId = try { context.getString(R.string.default_web_client_id) } catch (_: Exception) { "Not available" }

        val cert = SigningCertificate.read(context)
        val actualSha1 = cert?.sha1 ?: "unavailable"
        val actualSha256 = cert?.sha256 ?: "unavailable"
        val sha1Registered = cert != null && SigningCertificate.isRegistered(cert.sha1)

        val userFacing = buildString {
            append("$simpleName$codeBadge$typeBadge: $rawMessage.")
            if (isCancel || isDevError || extractedStatusCode == 28444) {
                if (cert != null && !sha1Registered) {
                    append("\n\nLikely cause: this installed APK is signed with SHA-1 $actualSha1, which is NOT registered for package 'com.skycord.pterodroid' in Firebase project '$activeProjectId'. Add this SHA-1 and the SHA-256 shown in the full report under Firebase Console > Project settings > your Android app, download google-services.json again, rebuild and reinstall.")
                } else if (cert != null) {
                    append("\n\nThis APK's signing SHA-1 ($actualSha1) matches the fingerprint registered in Firebase project '$activeProjectId', so the signing key is not the problem. Other possible causes: the new Android OAuth client has not propagated yet (can take hours), no usable Google account / outdated Google Play services on the device, or the OAuth consent screen is in Testing without this account as a test user.")
                } else {
                    append("\n\nCould not read this APK's signing certificate, so the fingerprint could not be compared with Firebase.")
                }
            }
        }

        val fullReport = buildString {
            appendLine("=== GOOGLE SIGN-IN ERROR DIAGNOSTICS ===")
            appendLine("Exception Class: $exceptionClass")
            if (!credentialType.isNullOrBlank()) {
                appendLine("Credential Type: $credentialType")
            }
            if (extractedStatusCode != null) {
                appendLine("Status Code: $extractedStatusCode ($statusCodeName)")
            }
            appendLine("Raw Message: $rawMessage")
            if (causes.isNotEmpty()) {
                appendLine("Cause Chain:")
                causes.forEach { appendLine("  -> $it") }
            }
            appendLine("----------------------------------------")
            appendLine("App Environment Details:")
            appendLine("Application ID / Package: com.skycord.pterodroid")
            appendLine("Target Firebase Project: $activeProjectId")
            appendLine("Installed APK signing SHA-1:   $actualSha1")
            appendLine("Installed APK signing SHA-256: $actualSha256")
            appendLine("Registered SHA-1 (Firebase):   ${SigningCertificate.REGISTERED_SHA1.joinToString()}")
            appendLine("Installed key matches registered SHA-1: ${if (cert == null) "unknown" else sha1Registered.toString()}")
            appendLine("Web Client ID (Type 3): $activeWebClientId")
            appendLine("========================================")
        }

        return AuthDiagnostics(
            exceptionClass = exceptionClass,
            exceptionType = credentialType,
            statusCode = extractedStatusCode,
            statusCodeName = statusCodeName,
            rawMessage = rawMessage,
            causes = causes,
            isCancellation = isCancel,
            isDeveloperConfigError = isDevError,
            userFacingMessage = userFacing,
            formattedFullReport = fullReport
        )
    }
}
