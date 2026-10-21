package com.example

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.notifications.NotificationHelper
import com.example.ui.MainViewModel
import com.example.ui.ScreenDestination
import com.example.ui.backups.BackupsViewModel
import com.example.ui.components.AccountSettingsSheet
import com.example.ui.components.PteroServerBottomBar
import com.example.ui.console.ConsoleViewModel
import com.example.ui.files.FilesViewModel
import com.example.ui.screens.AlertsScreen
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.BackupsScreen
import com.example.ui.screens.ConnectionsScreen
import com.example.ui.screens.ConsoleScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.GoogleSignInScreen
import com.example.ui.screens.SecurityPrivacyScreen
import com.example.ui.screens.ServerDashboardScreen
import com.example.ui.screens.ServerListScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupCredentialsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.server.ServerViewModel
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.PterodroidTheme
import java.util.Locale

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_PANEL_ID = "extra_panel_id"
        const val EXTRA_SERVER_ID = "extra_server_id"
        const val EXTRA_NAVIGATE_DASHBOARD = "extra_navigate_dashboard"
    }

    private var pendingNotificationIntent: Intent? = null
    var networkMonitor: com.example.data.network.NetworkMonitor? = null
        private set

    private var backgroundTimestampMillis: Long = 0L
    private var currentViewModel: MainViewModel? = null

    fun promptBiometrics(onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setSubtitle("Use your fingerprint or face to unlock")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .setNegativeButtonText("Use passcode")
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    override fun onStop() {
        super.onStop()
        backgroundTimestampMillis = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (backgroundTimestampMillis > 0L) {
            val elapsed = System.currentTimeMillis() - backgroundTimestampMillis
            currentViewModel?.checkBackgroundLock(elapsed)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create channels on startup
        NotificationHelper.createNotificationChannels(this)
        com.example.data.logging.ErrorReporter.initialize(applicationContext)
        networkMonitor = com.example.data.network.NetworkMonitor(applicationContext).apply { start() }
        pendingNotificationIntent = intent

        setContent {
            val mainViewModel: MainViewModel = viewModel()
            currentViewModel = mainViewModel

            val themeModeStr by mainViewModel.themeMode.collectAsStateWithLifecycle()
            val appLanguage by mainViewModel.appLanguage.collectAsStateWithLifecycle()
            val currentUser by mainViewModel.currentUser.collectAsStateWithLifecycle()
            val showAccountSettings by mainViewModel.showAccountSettings.collectAsStateWithLifecycle()
            val isHideRecentApps by mainViewModel.isHideRecentApps.collectAsStateWithLifecycle()

            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            // Dynamic FLAG_SECURE toggle
            LaunchedEffect(isHideRecentApps) {
                if (isHideRecentApps) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val themeMode = remember(themeModeStr) {
                try {
                    AppThemeMode.valueOf(themeModeStr)
                } catch (_: Exception) {
                    AppThemeMode.DARK
                }
            }

            val context = LocalContext.current

            // Notification permission request on Android 13+
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* Granted or denied handled gracefully */ }

            LaunchedEffect(currentUser) {
                if (currentUser != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (!NotificationHelper.hasNotificationPermission(context)) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            // Handle incoming notification click intents
            LaunchedEffect(pendingNotificationIntent) {
                val currentIntent = pendingNotificationIntent
                if (currentIntent != null && currentIntent.getBooleanExtra(EXTRA_NAVIGATE_DASHBOARD, false)) {
                    val panelId = currentIntent.getStringExtra(EXTRA_PANEL_ID)
                    val serverId = currentIntent.getStringExtra(EXTRA_SERVER_ID)
                    if (!panelId.isNullOrBlank() && !serverId.isNullOrBlank()) {
                        mainViewModel.openServerFromNotification(panelId, serverId)
                    }
                    pendingNotificationIntent = null
                }
            }

            LaunchedEffect(appLanguage) {
                val locale = Locale(appLanguage)
                Locale.setDefault(locale)
                val config = Configuration(context.resources.configuration)
                config.setLocale(locale)
                @Suppress("DEPRECATION")
                context.resources.updateConfiguration(config, context.resources.displayMetrics)
            }

            val layoutDirection = if (appLanguage == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                PterodroidTheme(themeMode = themeMode) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        PteroAppNavigation(
                            mainViewModel = mainViewModel,
                            isOnline = networkMonitor?.isOnline?.value ?: true
                        )

                        AccountSettingsSheet(
                            show = showAccountSettings,
                            sheetState = sheetState,
                            viewModel = mainViewModel,
                            onDismiss = { mainViewModel.closeAccountSettings() }
                        )

                        com.example.ui.components.AppBubbleHost()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkMonitor?.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationIntent = intent
    }
}

@Composable
fun PteroAppNavigation(
    mainViewModel: MainViewModel,
    isOnline: Boolean
) {
    val context = LocalContext.current
    val currentUser by mainViewModel.currentUser.collectAsStateWithLifecycle()
    val currentScreen by mainViewModel.currentScreen.collectAsStateWithLifecycle()
    val selectedServer by mainViewModel.selectedServer.collectAsStateWithLifecycle()
    val savedConnections by mainViewModel.savedConnections.collectAsStateWithLifecycle()
    val connectState by mainViewModel.connectState.collectAsStateWithLifecycle()
    val panelServersMap by mainViewModel.panelServersMap.collectAsStateWithLifecycle()
    val allServers by mainViewModel.allServers.collectAsStateWithLifecycle()
    val allPanels by mainViewModel.allPanels.collectAsStateWithLifecycle()
    val serverResourcesMap by mainViewModel.serverResourcesMap.collectAsStateWithLifecycle()
    val diagnosticState by mainViewModel.diagnosticState.collectAsStateWithLifecycle()
    val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
    val appLanguage by mainViewModel.appLanguage.collectAsStateWithLifecycle()
    val isAiEnabled by mainViewModel.isAiEnabled.collectAsStateWithLifecycle()

    val isAppLocked by mainViewModel.isAppLocked.collectAsStateWithLifecycle()
    val isBiometricsEnabled by mainViewModel.isBiometricsEnabled.collectAsStateWithLifecycle()
    val lockoutUntilMillis by mainViewModel.lockoutUntilMillis.collectAsStateWithLifecycle()

    // Reduced motion detection
    val isReducedMotion = remember(context) {
        val durationScale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
        } catch (_: Exception) {
            1.0f
        }
        durationScale == 0f
    }

    // Back handling
    BackHandler(
        enabled = currentScreen != ScreenDestination.SETUP_CREDENTIALS &&
                currentScreen != ScreenDestination.GOOGLE_LOGIN &&
                currentScreen != ScreenDestination.SPLASH
    ) {
        when (currentScreen) {
            ScreenDestination.CONSOLE,
            ScreenDestination.FILES,
            ScreenDestination.BACKUPS -> {
                mainViewModel.navigateTo(ScreenDestination.DASHBOARD)
            }
            ScreenDestination.DASHBOARD -> {
                mainViewModel.navigateTo(ScreenDestination.SERVERS)
            }
            ScreenDestination.SECURITY_PRIVACY,
            ScreenDestination.DIAGNOSTICS,
            ScreenDestination.ALERTS,
            ScreenDestination.CONNECTIONS,
            ScreenDestination.SETTINGS -> {
                mainViewModel.navigateBack()
            }
            ScreenDestination.SERVERS -> {
                if (allPanels.isEmpty()) {
                    mainViewModel.navigateTo(ScreenDestination.SETUP_CREDENTIALS)
                }
            }
            else -> {}
        }
    }

    LaunchedEffect(currentScreen) {
        com.example.data.logging.ErrorReporter.updateCurrentScreen(currentScreen.name)
    }

    // Gating & auto-advance: Protected server management screens require auth or setup
    LaunchedEffect(currentUser, currentScreen, allPanels, savedConnections) {
        if (currentUser != null) {
            if (currentScreen == ScreenDestination.GOOGLE_LOGIN) {
                mainViewModel.routeAuthenticatedUser()
            } else if (currentScreen == ScreenDestination.SETUP_CREDENTIALS &&
                !mainViewModel.isManuallyAddingPanel &&
                allPanels.isNotEmpty()
            ) {
                mainViewModel.navigateTo(ScreenDestination.SERVERS)
            }
        } else if (
            currentScreen != ScreenDestination.SPLASH &&
            currentScreen != ScreenDestination.GOOGLE_LOGIN &&
            currentScreen != ScreenDestination.SETUP_CREDENTIALS &&
            currentScreen != ScreenDestination.SECURITY_PRIVACY &&
            savedConnections.isEmpty()
        ) {
            mainViewModel.navigateTo(ScreenDestination.GOOGLE_LOGIN)
        }
    }

    val serverNavDestinations = remember {
        setOf(
            ScreenDestination.DASHBOARD,
            ScreenDestination.CONSOLE,
            ScreenDestination.FILES,
            ScreenDestination.BACKUPS
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                // Splash to next screen transition: cut without animation on reduced motion, fade otherwise
                if (initialState == ScreenDestination.SPLASH) {
                    if (isReducedMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(250))
                    }
                } else if (initialState == ScreenDestination.SERVERS && targetState == ScreenDestination.DASHBOARD) {
                    (slideInVertically(
                        initialOffsetY = { (it * 0.16f).toInt() },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(
                        animationSpec = tween(300)
                    )).togetherWith(
                        scaleOut(
                            targetScale = 0.96f,
                            animationSpec = tween(180)
                        ) + fadeOut(
                            animationSpec = tween(180)
                        )
                    )
                } else if (initialState == ScreenDestination.DASHBOARD && targetState == ScreenDestination.SERVERS) {
                    (slideInVertically(
                        initialOffsetY = { (-it * 0.1f).toInt() },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(
                        animationSpec = tween(240)
                    )).togetherWith(
                        slideOutVertically(
                            targetOffsetY = { (it * 0.16f).toInt() },
                            animationSpec = tween(200)
                        ) + scaleOut(
                            targetScale = 0.92f,
                            animationSpec = tween(200)
                        ) + fadeOut(
                            animationSpec = tween(180)
                        )
                    )
                } else if (initialState in serverNavDestinations && targetState in serverNavDestinations) {
                    (fadeIn(
                        animationSpec = tween(
                            durationMillis = 280,
                            delayMillis = 35,
                            easing = FastOutSlowInEasing
                        )
                    ) + scaleIn(
                        initialScale = 0.98f,
                        animationSpec = tween(
                            durationMillis = 280,
                            delayMillis = 35,
                            easing = FastOutSlowInEasing
                        )
                    )).togetherWith(
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = 90,
                                easing = FastOutLinearInEasing
                            )
                        ) + scaleOut(
                            targetScale = 0.98f,
                            animationSpec = tween(
                                durationMillis = 90,
                                easing = FastOutLinearInEasing
                            )
                        )
                    )
                } else {
                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(180))
                }
            },
            label = "MainScreenAnimation",
            modifier = Modifier.fillMaxSize()
        ) { targetScreen ->
            when (targetScreen) {
                ScreenDestination.SPLASH -> {
                    SplashScreen(
                        viewModel = mainViewModel,
                        appLanguage = appLanguage,
                        isOnline = isOnline
                    )
                }
                ScreenDestination.GOOGLE_LOGIN -> {
                    GoogleSignInScreen(viewModel = mainViewModel)
                }
                ScreenDestination.SETUP_CREDENTIALS -> {
                    SetupCredentialsScreen(
                        viewModel = mainViewModel,
                        savedConnections = savedConnections,
                        connectState = connectState
                    )
                }
                ScreenDestination.SERVERS -> {
                    ServerListScreen(
                        viewModel = mainViewModel,
                        panelServersMap = panelServersMap,
                        allServers = allServers,
                        serverResourcesMap = serverResourcesMap,
                        panels = allPanels
                    )
                }
                ScreenDestination.DASHBOARD -> {
                    selectedServer?.let { server ->
                        val serverViewModel: ServerViewModel = viewModel(
                            key = "server_${server.compositeKey}",
                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                    return ServerViewModel(mainViewModel.repository, server) as T
                                }
                            }
                        )
                        val statsState by serverViewModel.statsState.collectAsStateWithLifecycle()
                        val powerState by serverViewModel.powerActionState.collectAsStateWithLifecycle()

                        ServerDashboardScreen(
                            mainViewModel = mainViewModel,
                            serverViewModel = serverViewModel,
                            server = server,
                            statsState = statsState,
                            powerState = powerState
                        )
                    } ?: ServerListScreen(
                        viewModel = mainViewModel,
                        panelServersMap = panelServersMap,
                        allServers = allServers,
                        serverResourcesMap = serverResourcesMap,
                        panels = allPanels
                    )
                }
                ScreenDestination.CONSOLE -> {
                    selectedServer?.let { server ->
                        val consoleViewModel: ConsoleViewModel = viewModel(
                            key = "console_${server.compositeKey}",
                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                    return ConsoleViewModel(mainViewModel.repository, server) as T
                                }
                            }
                        )
                        val logs by consoleViewModel.consoleLogs.collectAsStateWithLifecycle()
                        val connectionStatus by consoleViewModel.connectionStatus.collectAsStateWithLifecycle()
                        val isWsConnected by consoleViewModel.isWsConnected.collectAsStateWithLifecycle()
                        val autoScroll by consoleViewModel.autoScroll.collectAsStateWithLifecycle()
                        val commandInput by consoleViewModel.commandInput.collectAsStateWithLifecycle()
                        val geminiState by consoleViewModel.geminiState.collectAsStateWithLifecycle()

                        ConsoleScreen(
                            mainViewModel = mainViewModel,
                            consoleViewModel = consoleViewModel,
                            server = server,
                            logs = logs,
                            connectionStatus = connectionStatus,
                            isWsConnected = isWsConnected,
                            autoScroll = autoScroll,
                            commandInput = commandInput,
                            geminiState = geminiState
                        )
                    } ?: mainViewModel.navigateTo(ScreenDestination.SERVERS)
                }
                ScreenDestination.FILES -> {
                    selectedServer?.let { server ->
                        val filesViewModel: FilesViewModel = viewModel(
                            key = "files_${server.compositeKey}",
                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                    return FilesViewModel(mainViewModel.repository, server) as T
                                }
                            }
                        )
                        val currentDirectory by filesViewModel.currentDirectory.collectAsStateWithLifecycle()
                        val filesState by filesViewModel.filesState.collectAsStateWithLifecycle()
                        val activeEditingFile by filesViewModel.activeEditingFile.collectAsStateWithLifecycle()
                        val editorContent by filesViewModel.editorContent.collectAsStateWithLifecycle()
                        val operationStatus by filesViewModel.operationStatus.collectAsStateWithLifecycle()

                        FileManagerScreen(
                            mainViewModel = mainViewModel,
                            filesViewModel = filesViewModel,
                            server = server,
                            currentDirectory = currentDirectory,
                            filesState = filesState,
                            activeEditingFile = activeEditingFile,
                            editorContent = editorContent,
                            operationStatus = operationStatus
                        )
                    } ?: mainViewModel.navigateTo(ScreenDestination.SERVERS)
                }
                ScreenDestination.BACKUPS -> {
                    selectedServer?.let { server ->
                        val backupsViewModel: BackupsViewModel = viewModel(
                            key = "backups_${server.compositeKey}",
                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                    return BackupsViewModel(mainViewModel.repository, server) as T
                                }
                            }
                        )
                        val backupsState by backupsViewModel.backupsState.collectAsStateWithLifecycle()
                        val operationStatus by backupsViewModel.operationStatus.collectAsStateWithLifecycle()
                        val downloadUrl by backupsViewModel.downloadUrl.collectAsStateWithLifecycle()

                        BackupsScreen(
                            mainViewModel = mainViewModel,
                            backupsViewModel = backupsViewModel,
                            server = server,
                            backupsState = backupsState,
                            operationStatus = operationStatus,
                            downloadUrl = downloadUrl
                        )
                    } ?: mainViewModel.navigateTo(ScreenDestination.SERVERS)
                }
                ScreenDestination.SETTINGS -> {
                    SettingsScreen(
                        viewModel = mainViewModel,
                        themeMode = themeMode,
                        appLanguage = appLanguage,
                        isAiEnabled = isAiEnabled,
                        onBack = { mainViewModel.navigateBack() }
                    )
                }
                ScreenDestination.SECURITY_PRIVACY -> {
                    SecurityPrivacyScreen(
                        viewModel = mainViewModel,
                        onBack = { mainViewModel.navigateBack() }
                    )
                }
                ScreenDestination.DIAGNOSTICS -> {
                    DiagnosticsScreen(
                        viewModel = mainViewModel,
                        diagnostic = diagnosticState
                    )
                }
                ScreenDestination.ALERTS -> {
                    AlertsScreen(
                        viewModel = mainViewModel,
                        onBack = { mainViewModel.navigateBack() }
                    )
                }
                ScreenDestination.CONNECTIONS -> {
                    ConnectionsScreen(
                        viewModel = mainViewModel,
                        onBack = { mainViewModel.navigateBack() }
                    )
                }
            }
        }

        val isServerNavScreen = currentScreen in serverNavDestinations
        val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
        val isKeyboardOpen = imeBottomPx > 0

        if (isServerNavScreen && selectedServer != null && !isKeyboardOpen) {
            PteroServerBottomBar(
                currentDestination = currentScreen,
                onNavigate = { mainViewModel.navigateTo(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
            )
        }

        // App Lock Fullscreen Overlay: when locked, nothing from the app is visible behind it
        if (isAppLocked) {
            AppLockScreen(
                viewModel = mainViewModel,
                isBiometricsEnabled = isBiometricsEnabled,
                lockoutUntilMillis = lockoutUntilMillis,
                onRequestBiometrics = {
                    (context as? MainActivity)?.promptBiometrics(
                        onSuccess = { mainViewModel.unlockApp() }
                    )
                }
            )
        }
    }
}
