package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.MainViewModel
import com.example.ui.ScreenDestination
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.ExpressiveCategoryCard
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ExpressiveSerifFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    themeMode: String,
    appLanguage: String,
    isAiEnabled: Boolean,
    onBack: (() -> Unit)? = null
) {
    val allPanels by viewModel.allPanels.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val alertSettings by viewModel.alertSettings.collectAsStateWithLifecycle()
    var showSignOutDialog by remember { mutableStateOf(false) }
    var themeDropdownExpanded by remember { mutableStateOf(false) }
    var languageDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = ExpressiveSerifFontFamily,
                            fontSize = 28.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (onBack != null) onBack() else viewModel.navigateBack()
                        },
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(top = 10.dp, bottom = if (selectedServer != null) 110.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. CONNECTIONS PAGE BUTTON
            item {
                SettingsNavigationCard(
                    title = "Connections",
                    subtitle = if (allPanels.isEmpty()) "No connected panels yet" else "${allPanels.size} active panel connection${if (allPanels.size == 1) "" else "s"}",
                    icon = Icons.Default.Dns,
                    badgeText = "${allPanels.size}",
                    tintColor = MaterialTheme.colorScheme.primary,
                    testTag = "btn_nav_connections",
                    onClick = {
                        viewModel.navigateTo(ScreenDestination.CONNECTIONS)
                    }
                )
            }

            // 2. ALERTS & NOTIFICATIONS PAGE BUTTON
            item {
                SettingsNavigationCard(
                    title = "Alerts & Notifications",
                    subtitle = if (alertSettings.alertsEnabled) "Background monitoring active (every 15 min)" else "Background monitoring disabled",
                    icon = Icons.Default.Notifications,
                    badgeText = if (alertSettings.alertsEnabled) "Active" else "Off",
                    badgeActive = alertSettings.alertsEnabled,
                    tintColor = MaterialTheme.colorScheme.primary,
                    testTag = "btn_nav_alerts",
                    onClick = {
                        viewModel.navigateTo(ScreenDestination.ALERTS)
                    }
                )
            }

            // 3. THEME SELECTION
            item {
                ExpressiveCategoryCard(
                    title = "Theme",
                    tintColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
                ) {
                    val currentThemeLabel = when (themeMode) {
                        "DARK" -> "Dark Theme"
                        "LIGHT" -> "Light Theme"
                        else -> "System Default"
                    }
                    val currentThemeIcon = when (themeMode) {
                        "DARK" -> Icons.Default.DarkMode
                        "LIGHT" -> Icons.Default.LightMode
                        else -> Icons.Default.SettingsBrightness
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Choose the display color theme for Pterodroid.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val dropWidth = maxWidth
                            val arrowRotation by animateFloatAsState(
                                targetValue = if (themeDropdownExpanded) 180f else 0f,
                                animationSpec = tween(durationMillis = 200)
                            )

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { themeDropdownExpanded = !themeDropdownExpanded }
                                    .testTag("dropdown_theme_select")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = currentThemeIcon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Text(
                                            text = currentThemeLabel,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Theme",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.rotate(arrowRotation)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = themeDropdownExpanded,
                                onDismissRequest = { themeDropdownExpanded = false },
                                modifier = Modifier
                                    .width(dropWidth)
                                    .border(
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        RoundedCornerShape(18.dp)
                                    ),
                                shape = RoundedCornerShape(18.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 4.dp,
                                shadowElevation = 8.dp
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Dark Theme",
                                            fontWeight = if (themeMode == "DARK") FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (themeMode == "DARK") 0.2f else 0.08f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    trailingIcon = {
                                        if (themeMode == "DARK") {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (themeMode == "DARK") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent),
                                    onClick = {
                                        viewModel.setTheme(AppThemeMode.DARK)
                                        themeDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Light Theme",
                                            fontWeight = if (themeMode == "LIGHT") FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = if (themeMode == "LIGHT") 0.2f else 0.08f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.LightMode, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    trailingIcon = {
                                        if (themeMode == "LIGHT") {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (themeMode == "LIGHT") MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f) else Color.Transparent),
                                    onClick = {
                                        viewModel.setTheme(AppThemeMode.LIGHT)
                                        themeDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "System Default",
                                            fontWeight = if (themeMode == "SYSTEM") FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.SettingsBrightness, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    trailingIcon = {
                                        if (themeMode == "SYSTEM") {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (themeMode == "SYSTEM") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent),
                                    onClick = {
                                        viewModel.setTheme(AppThemeMode.SYSTEM)
                                        themeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 4. LANGUAGE SELECTION
            item {
                ExpressiveCategoryCard(
                    title = "Language",
                    tintColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
                ) {
                    val currentLangLabel = if (appLanguage == "ar") "العربية (Arabic)" else "English"

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Select application interface language.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val dropWidth = maxWidth
                            val arrowRotation by animateFloatAsState(
                                targetValue = if (languageDropdownExpanded) 180f else 0f,
                                animationSpec = tween(durationMillis = 200)
                            )

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { languageDropdownExpanded = !languageDropdownExpanded }
                                    .testTag("dropdown_language_select")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Language,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Text(
                                            text = currentLangLabel,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Language",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.rotate(arrowRotation)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = languageDropdownExpanded,
                                onDismissRequest = { languageDropdownExpanded = false },
                                modifier = Modifier
                                    .width(dropWidth)
                                    .border(
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        RoundedCornerShape(18.dp)
                                    ),
                                shape = RoundedCornerShape(18.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 4.dp,
                                shadowElevation = 8.dp
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "English",
                                            fontWeight = if (appLanguage == "en") FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (appLanguage == "en") 0.2f else 0.08f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    trailingIcon = {
                                        if (appLanguage == "en") {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (appLanguage == "en") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent),
                                    onClick = {
                                        viewModel.setLanguage("en")
                                        languageDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "العربية (Arabic)",
                                            fontWeight = if (appLanguage == "ar") FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = if (appLanguage == "ar") 0.2f else 0.08f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    trailingIcon = {
                                        if (appLanguage == "ar") {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (appLanguage == "ar") MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f) else Color.Transparent),
                                    onClick = {
                                        viewModel.setLanguage("ar")
                                        languageDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 5. CONNECTION DIAGNOSTICS SECTION
            item {
                SettingsNavigationCard(
                    title = "Connection Diagnostics",
                    subtitle = "Inspect latency, TLS certificates, and API tokens across connections.",
                    icon = Icons.Default.Speed,
                    tintColor = MaterialTheme.colorScheme.secondary,
                    testTag = "btn_nav_diagnostics",
                    onClick = {
                        val firstPanel = allPanels.firstOrNull()
                        if (firstPanel != null) {
                            viewModel.runDiagnosticsOnly(firstPanel.panelUrl, firstPanel.apiToken, firstPanel.id)
                        }
                        viewModel.navigateTo(ScreenDestination.DIAGNOSTICS)
                    }
                )
            }

            // 6. SIGN OUT OF GOOGLE & CLEAR LOCAL DATA
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .clickable { showSignOutDialog = true }
                        .testTag("logout_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sign Out & Clear Local Data",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Cancels background monitoring, clears alert state, and removes session cache.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        ConfirmationDialog(
            show = true,
            title = "Sign Out & Clear Local Data?",
            message = "This will stop background monitoring alerts, cancel periodic WorkManager tasks, and wipe local cached data.",
            confirmButtonText = "Sign Out",
            isDestructive = true,
            onConfirm = {
                showSignOutDialog = false
                viewModel.signOutGoogle()
            },
            onDismiss = { showSignOutDialog = false }
        )
    }
}

@Composable
private fun SettingsNavigationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tintColor: Color,
    badgeText: String? = null,
    badgeActive: Boolean = true,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(26.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(tintColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tintColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (badgeActive) tintColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (badgeActive) tintColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
