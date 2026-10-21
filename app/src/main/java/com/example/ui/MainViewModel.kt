package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.ErrorCategory
import com.example.data.api.PteroApiException
import com.example.data.api.PteroErrorParser
import com.example.data.api.UrlNormalizer
import com.example.data.model.ConnectionDiagnostic
import com.example.data.model.SavedConnection
import com.example.data.model.ServerAttributes
import com.example.data.model.ServerItem
import com.example.data.model.ServerStatsAttributes
import com.example.data.notifications.NotificationHelper
import com.example.data.repository.FirebaseAuthRepository
import com.example.data.repository.PteroRepository
import com.example.data.storage.AlertPreferences
import com.example.data.storage.AlertSettings
import com.example.data.storage.ConnectionPreferences
import com.example.data.storage.SecurityPreferences
import com.example.data.worker.ServerMonitorWorker
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

sealed interface UiState<out T> {
    object Idle : UiState<Nothing>
    object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(
        val message: String,
        val category: ErrorCategory = ErrorCategory.GENERIC,
        val statusCode: Int? = null
    ) : UiState<Nothing>
}

data class SplashState(
    val isLoading: Boolean = true,
    val progress: Float = 0.1f,
    val statusMessage: String = "Initializing secure client…",
    val isFailed: Boolean = false,
    val errorMessage: String? = null
)

enum class ScreenDestination(val title: String) {
    SPLASH("Pterodroid"),
    GOOGLE_LOGIN("Sign In"),
    SETUP_CREDENTIALS("Add Panel"),
    SERVERS("Servers"),
    DASHBOARD("Dashboard"),
    CONSOLE("Console"),
    FILES("Files"),
    BACKUPS("Backups"),
    SETTINGS("Settings"),
    DIAGNOSTICS("Diagnostics"),
    ALERTS("Alerts & Notifications"),
    CONNECTIONS("Connections"),
    SECURITY_PRIVACY("Security & Privacy")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = ConnectionPreferences(application)
    val alertPreferences = AlertPreferences(application)
    val repository = PteroRepository(viewModelScope)
    val firebaseAuthRepo = FirebaseAuthRepository(application, viewModelScope)

    val currentUser = firebaseAuthRepo.currentUser
    val userProfile = firebaseAuthRepo.userProfile
    val isAuthLoading = firebaseAuthRepo.isLoading
    val authError = firebaseAuthRepo.authError
    val authDiagnostics = firebaseAuthRepo.authDiagnostics
    val syncedPanels = firebaseAuthRepo.syncedPanels
    val isCredentialsLoaded = firebaseAuthRepo.isCredentialsLoaded
    val isPanelsSyncLoaded = firebaseAuthRepo.isPanelsSyncLoaded

    var isManuallyAddingPanel: Boolean = false

    private val _currentScreen = MutableStateFlow(ScreenDestination.SPLASH)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    private val _previousScreen = MutableStateFlow<ScreenDestination?>(null)
    val previousScreen: StateFlow<ScreenDestination?> = _previousScreen.asStateFlow()

    private val navigationStack = ArrayDeque<ScreenDestination>()

    private val _selectedServer = MutableStateFlow<ServerAttributes?>(null)
    val selectedServer: StateFlow<ServerAttributes?> = _selectedServer.asStateFlow()

    val savedConnections: StateFlow<List<SavedConnection>> = preferences.savedConnections
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Merged reactive list of all active panels from local storage and cloud sync
    val allPanels: StateFlow<List<SavedConnection>> = combine(savedConnections, syncedPanels) { local, cloud ->
        val map = linkedMapOf<String, SavedConnection>()
        cloud.forEach { if (it.panelUrl.isNotBlank() && it.apiToken.isNotBlank()) map[it.id] = it }
        local.forEach { if (it.panelUrl.isNotBlank() && it.apiToken.isNotBlank() && !map.containsKey(it.id)) map[it.id] = it }
        map.values.toList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Per-panel server lists: panelId -> UiState<List<ServerItem>>
    private val _panelServersMap = MutableStateFlow<Map<String, UiState<List<ServerItem>>>>(emptyMap())
    val panelServersMap: StateFlow<Map<String, UiState<List<ServerItem>>>> = _panelServersMap.asStateFlow()

    private val _isRefreshingServers = MutableStateFlow(false)
    val isRefreshingServers: StateFlow<Boolean> = _isRefreshingServers.asStateFlow()

    // Aggregated list of all successfully loaded servers from all panels
    private val _allServers = MutableStateFlow<List<ServerItem>>(emptyList())
    val allServers: StateFlow<List<ServerItem>> = _allServers.asStateFlow()

    // Resources map keyed by compositeKey ("panelId:identifier")
    private val _serverResourcesMap = MutableStateFlow<Map<String, ServerStatsAttributes>>(emptyMap())
    val serverResourcesMap: StateFlow<Map<String, ServerStatsAttributes>> = _serverResourcesMap.asStateFlow()

    private val _connectState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val connectState: StateFlow<UiState<Unit>> = _connectState.asStateFlow()

    private val _diagnosticState = MutableStateFlow<ConnectionDiagnostic?>(null)
    val diagnosticState: StateFlow<ConnectionDiagnostic?> = _diagnosticState.asStateFlow()

    // Map of panelId -> ConnectionDiagnostic for all connected panels
    private val _allPanelsDiagnostics = MutableStateFlow<Map<String, ConnectionDiagnostic>>(emptyMap())
    val allPanelsDiagnostics: StateFlow<Map<String, ConnectionDiagnostic>> = _allPanelsDiagnostics.asStateFlow()

    private val _isTestingAllDiagnostics = MutableStateFlow(false)
    val isTestingAllDiagnostics: StateFlow<Boolean> = _isTestingAllDiagnostics.asStateFlow()

    private val _currentTestingPanelId = MutableStateFlow<String?>(null)
    val currentTestingPanelId: StateFlow<String?> = _currentTestingPanelId.asStateFlow()

    private val _showAccountSettings = MutableStateFlow(false)
    val showAccountSettings: StateFlow<Boolean> = _showAccountSettings.asStateFlow()

    val themeMode: StateFlow<String> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.Lazily, "DARK")

    val appLanguage: StateFlow<String> = preferences.appLanguage
        .stateIn(viewModelScope, SharingStarted.Lazily, "en")

    val isAiEnabled: StateFlow<Boolean> = preferences.isAiEnabled
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val alertSettings: StateFlow<AlertSettings> = alertPreferences.alertSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AlertSettings())

    val securityPreferences = SecurityPreferences(application)
    val isAppLockEnabled: StateFlow<Boolean> = securityPreferences.isAppLockEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isBiometricsEnabled: StateFlow<Boolean> = securityPreferences.isBiometricsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val lockTimeoutSeconds: StateFlow<Int> = securityPreferences.lockTimeoutSeconds
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val isHideRecentApps: StateFlow<Boolean> = securityPreferences.isHideRecentApps
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val failedAttempts: StateFlow<Int> = securityPreferences.failedAttempts
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val lockoutUntilMillis: StateFlow<Long> = securityPreferences.lockoutUntilMillis
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _splashState = MutableStateFlow(SplashState())
    val splashState: StateFlow<SplashState> = _splashState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        // Automatically route user when authenticated and schedule periodic monitoring
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    ServerMonitorWorker.schedulePeriodic(getApplication())
                    if (_currentScreen.value == ScreenDestination.GOOGLE_LOGIN) {
                        routeAuthenticatedUser()
                    }
                } else {
                    ServerMonitorWorker.cancelPeriodic(getApplication())
                }
            }
        }

        // Sync panel configs to repository whenever allPanels changes
        viewModelScope.launch {
            allPanels.collect { panels ->
                repository.syncPanels(panels)
                if (panels.isNotEmpty()) {
                    loadAllPanelsServers()
                    if (!isManuallyAddingPanel &&
                        (_currentScreen.value == ScreenDestination.SETUP_CREDENTIALS ||
                         _currentScreen.value == ScreenDestination.GOOGLE_LOGIN)
                    ) {
                        _currentScreen.value = ScreenDestination.SERVERS
                    }
                } else {
                    _panelServersMap.value = emptyMap()
                    _allServers.value = emptyList()
                    _serverResourcesMap.value = emptyMap()
                }
            }
        }

        // Start background telemetry resource polling for all visible servers
        startResourcePolling()
    }

    private fun startResourcePolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val currentServerList = _allServers.value
                if (currentServerList.isNotEmpty()) {
                    val updated = _serverResourcesMap.value.toMutableMap()
                    currentServerList.map { serverItem ->
                        async {
                            val attr = serverItem.attributes
                            val key = attr.compositeKey
                            try {
                                val stats = withTimeoutOrNull(4000L) {
                                    repository.getServerResources(attr.panelId, attr.identifier)
                                }
                                if (stats != null) {
                                    synchronized(updated) {
                                        updated[key] = stats
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }.awaitAll()
                    _serverResourcesMap.value = updated.toMap()
                }
                delay(10000L)
            }
        }
    }

    fun navigateTo(destination: ScreenDestination) {
        if (_currentScreen.value != destination) {
            navigationStack.addLast(_currentScreen.value)
            _previousScreen.value = _currentScreen.value
            _currentScreen.value = destination
        }
    }

    fun navigateBack() {
        while (navigationStack.isNotEmpty()) {
            val popped = navigationStack.removeLast()
            if (popped != _currentScreen.value && popped != ScreenDestination.SPLASH) {
                _previousScreen.value = navigationStack.lastOrNull()
                _currentScreen.value = popped
                return
            }
        }
        if (_selectedServer.value != null) {
            _currentScreen.value = ScreenDestination.DASHBOARD
        } else if (allPanels.value.isNotEmpty()) {
            _currentScreen.value = ScreenDestination.SERVERS
        } else {
            _currentScreen.value = ScreenDestination.SETUP_CREDENTIALS
        }
    }

    fun initializeApp(isOnline: Boolean) {
        viewModelScope.launch {
            _splashState.value = SplashState(isLoading = true, progress = 0.2f, statusMessage = "Initializing secure client…")
            delay(150)
            try {
                _splashState.value = SplashState(isLoading = true, progress = 0.45f, statusMessage = "Checking your account…")
                if (isOnline) {
                    withTimeoutOrNull(8000L) {
                        firebaseAuthRepo.isAuthInitialized.first { it }
                    }
                }
                val user = currentUser.value
                if (user != null && isOnline) {
                    _splashState.value = SplashState(isLoading = true, progress = 0.75f, statusMessage = "Loading your panels…")
                    withTimeoutOrNull(5000L) {
                        isPanelsSyncLoaded.first { it }
                    }
                }
                _splashState.value = SplashState(isLoading = true, progress = 1.0f, statusMessage = "Ready")
                delay(200)

                val hasLock = securityPreferences.hasPasscode() && isAppLockEnabled.value
                val destination = when {
                    currentUser.value != null -> {
                        if (allPanels.value.isNotEmpty()) ScreenDestination.SERVERS else ScreenDestination.SETUP_CREDENTIALS
                    }
                    savedConnections.value.isNotEmpty() -> ScreenDestination.SERVERS
                    else -> ScreenDestination.GOOGLE_LOGIN
                }

                if (hasLock && destination != ScreenDestination.GOOGLE_LOGIN) {
                    _isAppLocked.value = true
                }
                _currentScreen.value = destination
                _splashState.value = SplashState(isLoading = false, progress = 1.0f, statusMessage = "Ready")
            } catch (e: Exception) {
                _splashState.value = SplashState(
                    isLoading = false,
                    isFailed = true,
                    errorMessage = e.localizedMessage ?: "Failed to initialize"
                )
            }
        }
    }

    fun continueOffline() {
        viewModelScope.launch {
            val hasLock = securityPreferences.hasPasscode() && isAppLockEnabled.value
            val destination = when {
                currentUser.value != null -> {
                    if (allPanels.value.isNotEmpty()) ScreenDestination.SERVERS else ScreenDestination.SETUP_CREDENTIALS
                }
                savedConnections.value.isNotEmpty() -> ScreenDestination.SERVERS
                else -> ScreenDestination.GOOGLE_LOGIN
            }
            if (hasLock && destination != ScreenDestination.GOOGLE_LOGIN) {
                _isAppLocked.value = true
            }
            _currentScreen.value = destination
            _splashState.value = SplashState(isLoading = false, progress = 1.0f, statusMessage = "Ready")
        }
    }

    fun retryInitialization(isOnline: Boolean) {
        _splashState.value = SplashState(isLoading = true, progress = 0.1f, statusMessage = "Initializing secure client…")
        initializeApp(isOnline)
    }

    fun verifyPasscode(code: String, onResult: (Boolean, Long) -> Unit) {
        viewModelScope.launch {
            val valid = securityPreferences.verifyPasscode(code)
            if (valid) {
                securityPreferences.recordSuccessfulUnlock()
                _isAppLocked.value = false
                onResult(true, 0L)
            } else {
                val lockoutExpiry = securityPreferences.recordFailedAttempt()
                onResult(false, lockoutExpiry)
            }
        }
    }

    fun unlockApp() {
        viewModelScope.launch {
            securityPreferences.recordSuccessfulUnlock()
            _isAppLocked.value = false
        }
    }

    fun lockApp() {
        if (isAppLockEnabled.value &&
            _currentScreen.value != ScreenDestination.GOOGLE_LOGIN &&
            _currentScreen.value != ScreenDestination.SPLASH
        ) {
            _isAppLocked.value = true
        }
    }

    fun checkBackgroundLock(elapsedBackgroundMillis: Long) {
        val timeoutMillis = lockTimeoutSeconds.value * 1000L
        if (isAppLockEnabled.value &&
            _currentScreen.value != ScreenDestination.GOOGLE_LOGIN &&
            _currentScreen.value != ScreenDestination.SPLASH &&
            elapsedBackgroundMillis >= timeoutMillis
        ) {
            _isAppLocked.value = true
        }
    }

    fun setNewPasscode(code: String) {
        viewModelScope.launch {
            securityPreferences.setPasscode(code)
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityPreferences.setAppLockEnabled(enabled)
            if (!enabled) {
                _isAppLocked.value = false
            }
        }
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityPreferences.setBiometricsEnabled(enabled)
        }
    }

    fun setLockTimeoutSeconds(seconds: Int) {
        viewModelScope.launch {
            securityPreferences.setLockTimeoutSeconds(seconds)
        }
    }

    fun setHideRecentApps(enabled: Boolean) {
        viewModelScope.launch {
            securityPreferences.setHideRecentApps(enabled)
        }
    }

    fun forgotPasscodeAndSignOut() {
        viewModelScope.launch {
            securityPreferences.clearAllSecurityData()
            _isAppLocked.value = false
            signOutGoogle()
        }
    }

    fun startAddingPanel() {
        isManuallyAddingPanel = true
        _connectState.value = UiState.Idle
        _diagnosticState.value = null
        navigateTo(ScreenDestination.SETUP_CREDENTIALS)
    }

    fun cancelAddingPanel() {
        isManuallyAddingPanel = false
        if (allPanels.value.isNotEmpty()) {
            _currentScreen.value = ScreenDestination.SERVERS
        } else {
            _currentScreen.value = ScreenDestination.SETUP_CREDENTIALS
        }
    }

    fun routeAuthenticatedUser() {
        viewModelScope.launch {
            if (allPanels.value.isNotEmpty()) {
                isManuallyAddingPanel = false
                _currentScreen.value = ScreenDestination.SERVERS
                loadAllPanelsServers()
                return@launch
            }
            val user = currentUser.value
            if (user != null && !isPanelsSyncLoaded.value) {
                try {
                    withTimeoutOrNull(2500L) {
                        isPanelsSyncLoaded.first { it }
                    }
                } catch (_: Exception) {
                }
            }
            if (allPanels.value.isNotEmpty()) {
                isManuallyAddingPanel = false
                _currentScreen.value = ScreenDestination.SERVERS
                loadAllPanelsServers()
            } else {
                isManuallyAddingPanel = false
                _currentScreen.value = ScreenDestination.SETUP_CREDENTIALS
            }
        }
    }

    fun signInWithGoogle(
        context: Context,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        firebaseAuthRepo.signInWithGoogle(
            callingContext = context,
            onSuccess = {
                ServerMonitorWorker.schedulePeriodic(getApplication())
                routeAuthenticatedUser()
                onSuccess()
            },
            onError = onError
        )
    }

    fun refreshAllServers() {
        if (_isRefreshingServers.value) return
        _isRefreshingServers.value = true
        viewModelScope.launch {
            try {
                val minDelay = async { kotlinx.coroutines.delay(600L) }
                loadAllPanelsServers()
                minDelay.await()
            } finally {
                _isRefreshingServers.value = false
            }
        }
    }

    fun loadAllPanelsServers() {
        val panels = allPanels.value
        if (panels.isEmpty()) {
            _panelServersMap.value = emptyMap()
            _allServers.value = emptyList()
            return
        }
        viewModelScope.launch {
            val currentMap = _panelServersMap.value.toMutableMap()
            panels.forEach { panel ->
                if (currentMap[panel.id] !is UiState.Success) {
                    currentMap[panel.id] = UiState.Loading
                }
            }
            _panelServersMap.value = currentMap

            panels.map { panel ->
                async {
                    loadServersForPanel(panel.id)
                }
            }.awaitAll()
            recomputeAllServers()
        }
    }

    fun loadServersForPanel(panelId: String) {
        viewModelScope.launch {
            val currentMap = _panelServersMap.value.toMutableMap()
            currentMap[panelId] = UiState.Loading
            _panelServersMap.value = currentMap

            try {
                val servers = repository.getServers(panelId)
                val updatedMap = _panelServersMap.value.toMutableMap()
                updatedMap[panelId] = UiState.Success(servers)
                _panelServersMap.value = updatedMap
                recomputeAllServers()

                val selected = _selectedServer.value
                if (selected != null && selected.panelId == panelId) {
                    val fresh = servers.firstOrNull { it.attributes.identifier == selected.identifier }
                    if (fresh != null) {
                        _selectedServer.value = fresh.attributes
                    }
                }
            } catch (e: PteroApiException) {
                val updatedMap = _panelServersMap.value.toMutableMap()
                updatedMap[panelId] = UiState.Error(
                    message = e.message,
                    category = e.category,
                    statusCode = e.statusCode
                )
                _panelServersMap.value = updatedMap
                recomputeAllServers()
            } catch (e: Exception) {
                val parsed = PteroErrorParser.parse(e)
                val updatedMap = _panelServersMap.value.toMutableMap()
                updatedMap[panelId] = UiState.Error(
                    message = parsed.message,
                    category = parsed.category,
                    statusCode = parsed.statusCode
                )
                _panelServersMap.value = updatedMap
                recomputeAllServers()
            }
        }
    }

    private fun recomputeAllServers() {
        val list = mutableListOf<ServerItem>()
        _panelServersMap.value.values.forEach { state ->
            if (state is UiState.Success) {
                list.addAll(state.data)
            }
        }
        _allServers.value = list
    }

    fun addPanel(
        url: String,
        token: String,
        label: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val trimmedToken = token.trim().trim('"', '\'')
        if (trimmedToken.startsWith("ptla_")) {
            val msg = "Application API Key (starts with 'ptla_') cannot manage client servers. Please use a Client API Key (starts with 'ptlc_')."
            _connectState.value = UiState.Error(msg, ErrorCategory.UNAUTHORIZED, 401)
            onError(msg)
            return
        }

        viewModelScope.launch {
            _connectState.value = UiState.Loading
            val cleanUrl = try {
                UrlNormalizer.normalize(url)
            } catch (e: Exception) {
                val err = e.message ?: "Invalid panel URL syntax."
                _connectState.value = UiState.Error(err)
                onError(err)
                return@launch
            }

            val diag = repository.runDiagnostics(cleanUrl, trimmedToken)
            _diagnosticState.value = diag

            if (diag.panelReachable == true && diag.tokenValid == true) {
                val panelId = UUID.randomUUID().toString()
                val finalLabel = label?.trim()?.ifBlank { null }
                    ?: cleanUrl.removePrefix("https://").removePrefix("http://").removeSuffix("/")

                val conn = SavedConnection(
                    id = panelId,
                    label = finalLabel,
                    panelUrl = cleanUrl,
                    apiToken = trimmedToken,
                    lastUsedTimestamp = System.currentTimeMillis()
                )
                preferences.saveOrUpdateConnection(conn)
                try {
                    firebaseAuthRepo.saveOrUpdatePanelInCloud(conn)
                } catch (_: Exception) {
                }
                repository.registerPanel(conn)
                loadServersForPanel(panelId)
                _connectState.value = UiState.Success(Unit)
                isManuallyAddingPanel = false
                _currentScreen.value = ScreenDestination.SERVERS
                onSuccess()
            } else {
                val errorMsg = diag.errorMessage ?: "Failed to validate panel connection. Check URL and Client API Key."
                _connectState.value = UiState.Error(
                    message = errorMsg,
                    category = if (diag.tokenValid == false) ErrorCategory.UNAUTHORIZED else ErrorCategory.OFFLINE,
                    statusCode = if (diag.tokenValid == false) 401 else null
                )
                onError(errorMsg)
            }
        }
    }

    fun updatePanel(
        panelId: String,
        newLabel: String,
        newUrl: String? = null,
        newToken: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val existing = allPanels.value.firstOrNull { it.id == panelId } ?: return
        viewModelScope.launch {
            val updatedUrl = newUrl?.trim()?.takeIf { it.isNotBlank() }?.let {
                try { UrlNormalizer.normalize(it) } catch (_: Exception) { existing.panelUrl }
            } ?: existing.panelUrl

            val updatedToken = newToken?.trim()?.trim('"', '\'')?.takeIf { it.isNotBlank() } ?: existing.apiToken
            val updatedLabel = newLabel.trim().ifBlank { existing.label }

            val updatedConn = existing.copy(
                label = updatedLabel,
                panelUrl = updatedUrl,
                apiToken = updatedToken,
                lastUsedTimestamp = System.currentTimeMillis()
            )
            preferences.saveOrUpdateConnection(updatedConn)
            try {
                firebaseAuthRepo.saveOrUpdatePanelInCloud(updatedConn)
            } catch (_: Exception) {
            }
            repository.registerPanel(updatedConn)
            loadServersForPanel(panelId)
            onSuccess()
        }
    }

    fun deletePanel(panelId: String) {
        viewModelScope.launch {
            preferences.deleteSavedConnection(panelId)
            firebaseAuthRepo.deleteSavedPanelFromCloud(panelId)
            repository.unregisterPanel(panelId)
            val serversMap = _panelServersMap.value.toMutableMap()
            serversMap.remove(panelId)
            _panelServersMap.value = serversMap
            recomputeAllServers()

            val resources = _serverResourcesMap.value.toMutableMap()
            resources.keys.filter { it.startsWith("$panelId:") }.forEach { resources.remove(it) }
            _serverResourcesMap.value = resources

            if (_selectedServer.value?.panelId == panelId) {
                _selectedServer.value = null
                if (_currentScreen.value in setOf(ScreenDestination.DASHBOARD, ScreenDestination.CONSOLE, ScreenDestination.FILES, ScreenDestination.BACKUPS)) {
                    _currentScreen.value = ScreenDestination.SERVERS
                }
            }
            if (allPanels.value.isEmpty()) {
                _currentScreen.value = ScreenDestination.SETUP_CREDENTIALS
            }
        }
    }

    fun selectServer(server: ServerAttributes) {
        _selectedServer.value = server
        _currentScreen.value = ScreenDestination.DASHBOARD
    }

    fun openServerFromNotification(panelId: String, serverIdentifier: String) {
        viewModelScope.launch {
            // Find in current servers or load
            val existing = _allServers.value.firstOrNull {
                it.attributes.panelId == panelId && it.attributes.identifier == serverIdentifier
            }
            if (existing != null) {
                selectServer(existing.attributes)
            } else {
                try {
                    val server = repository.getServerDetails(panelId, serverIdentifier)
                    selectServer(server)
                } catch (_: Exception) {
                    navigateTo(ScreenDestination.SERVERS)
                }
            }
        }
    }

    fun runAllDiagnostics() {
        val panels = allPanels.value
        if (panels.isEmpty()) {
            _allPanelsDiagnostics.value = emptyMap()
            _isTestingAllDiagnostics.value = false
            _currentTestingPanelId.value = null
            return
        }

        viewModelScope.launch {
            _isTestingAllDiagnostics.value = true
            // Initialize all panels as queued/waiting
            val queuedMap = panels.associate { panel ->
                panel.id to ConnectionDiagnostic(
                    panelId = panel.id,
                    panelUrl = panel.panelUrl,
                    urlValid = true,
                    isTesting = false
                )
            }.toMutableMap()
            _allPanelsDiagnostics.value = queuedMap.toMap()

            // Run through panels one by one sequentially
            for ((index, panel) in panels.withIndex()) {
                _currentTestingPanelId.value = panel.id
                queuedMap[panel.id] = ConnectionDiagnostic(
                    panelId = panel.id,
                    panelUrl = panel.panelUrl,
                    urlValid = true,
                    isTesting = true
                )
                _allPanelsDiagnostics.value = queuedMap.toMap()
                _diagnosticState.value = queuedMap[panel.id]

                // Visual pacing pause to highlight active testing for the current panel
                delay(180L)

                val result = repository.runDiagnostics(panel.panelUrl, panel.apiToken, panel.id)
                queuedMap[panel.id] = result
                _allPanelsDiagnostics.value = queuedMap.toMap()
                _diagnosticState.value = result

                // Cadence delay before advancing to next panel
                if (index < panels.size - 1) {
                    delay(250L)
                }
            }

            _currentTestingPanelId.value = null
            _isTestingAllDiagnostics.value = false
        }
    }

    fun runDiagnosticsForPanel(panelId: String) {
        val panel = allPanels.value.firstOrNull { it.id == panelId } ?: return
        viewModelScope.launch {
            val testingDiag = ConnectionDiagnostic(
                panelId = panel.id,
                panelUrl = panel.panelUrl,
                urlValid = true,
                isTesting = true
            )
            _allPanelsDiagnostics.update { current ->
                current + (panel.id to testingDiag)
            }
            val result = repository.runDiagnostics(panel.panelUrl, panel.apiToken, panel.id)
            _allPanelsDiagnostics.update { current ->
                current + (panel.id to result)
            }
            if (_diagnosticState.value?.panelId == panel.id) {
                _diagnosticState.value = result
            }
        }
    }

    fun runDiagnosticsOnly(url: String, token: String, panelId: String? = null) {
        viewModelScope.launch {
            val testingDiag = ConnectionDiagnostic(
                panelId = panelId,
                panelUrl = url,
                urlValid = true,
                isTesting = true
            )
            _diagnosticState.value = testingDiag
            if (panelId != null) {
                _allPanelsDiagnostics.update { it + (panelId to testingDiag) }
            }
            val result = repository.runDiagnostics(url, token, panelId)
            _diagnosticState.value = result
            if (panelId != null) {
                _allPanelsDiagnostics.update { it + (panelId to result) }
            }
        }
    }

    fun setTheme(mode: AppThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode.name)
            firebaseAuthRepo.syncPreferencesToCloud(themeMode = mode.name)
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            preferences.setLanguage(lang)
            firebaseAuthRepo.syncPreferencesToCloud(appLanguage = lang)
        }
    }

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAiEnabled(enabled)
            firebaseAuthRepo.syncPreferencesToCloud(isAiEnabled = enabled)
        }
    }

    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            alertPreferences.setAlertsEnabled(enabled)
            if (enabled) {
                ServerMonitorWorker.schedulePeriodic(getApplication())
            } else {
                ServerMonitorWorker.cancelPeriodic(getApplication())
            }
        }
    }

    fun setAlertServerDown(enabled: Boolean) {
        viewModelScope.launch { alertPreferences.setAlertServerDown(enabled) }
    }

    fun setAlertServerRecovered(enabled: Boolean) {
        viewModelScope.launch { alertPreferences.setAlertServerRecovered(enabled) }
    }

    fun setAlertHighUsage(enabled: Boolean) {
        viewModelScope.launch { alertPreferences.setAlertHighUsage(enabled) }
    }

    fun setAlertPanelUnreachable(enabled: Boolean) {
        viewModelScope.launch { alertPreferences.setAlertPanelUnreachable(enabled) }
    }

    fun setCpuThreshold(threshold: Int) {
        viewModelScope.launch { alertPreferences.setCpuThreshold(threshold) }
    }

    fun setRamThreshold(threshold: Int) {
        viewModelScope.launch { alertPreferences.setRamThreshold(threshold) }
    }

    fun setDiskThreshold(threshold: Int) {
        viewModelScope.launch { alertPreferences.setDiskThreshold(threshold) }
    }

    fun runImmediateAlertCheck() {
        ServerMonitorWorker.runImmediate(getApplication())
    }

    fun sendTestNotification() {
        NotificationHelper.sendTestNotifications(getApplication())
    }

    fun openAccountSettings() {
        _showAccountSettings.value = true
    }

    fun closeAccountSettings() {
        _showAccountSettings.value = false
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            // Cancel background work and wipe stored alert state & settings
            ServerMonitorWorker.cancelPeriodic(getApplication())
            alertPreferences.clearAllAlertData()
            repository.clearAll()
            preferences.clearAllData()
            securityPreferences.clearAllSecurityData()
            _isAppLocked.value = false
            com.example.data.logging.ErrorReporter.clearLocalData(getApplication())
            navigationStack.clear()
            _previousScreen.value = null
            _selectedServer.value = null
            _panelServersMap.value = emptyMap()
            _allServers.value = emptyList()
            _serverResourcesMap.value = emptyMap()
            _connectState.value = UiState.Idle
            firebaseAuthRepo.signOut {
                _currentScreen.value = ScreenDestination.GOOGLE_LOGIN
            }
        }
    }

    fun clearAuthError() {
        firebaseAuthRepo.clearError()
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
