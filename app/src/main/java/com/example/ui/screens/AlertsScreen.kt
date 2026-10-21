package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.notifications.NotificationHelper
import com.example.ui.MainViewModel
import com.example.ui.components.BubbleController
import com.example.ui.components.BubbleType
import com.example.ui.components.ExpressiveCategoryCard
import com.example.ui.theme.ExpressiveSerifFontFamily
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val alertSettings by viewModel.alertSettings.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            BubbleController.show("Notification permission granted.", BubbleType.SUCCESS)
        } else {
            BubbleController.show("Notifications disabled in device settings.", BubbleType.WARNING)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Alerts & Notifications",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = ExpressiveSerifFontFamily,
                            fontSize = 26.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("alerts_back_button")
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
            contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Android 13+ permission banner
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !NotificationHelper.hasNotificationPermission(context)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notification Permission Required",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Allow notifications so Pterodroid can alert you when servers stop or reach thresholds.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Grant", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Master toggle: Background monitoring (15-min periodic WorkManager)
            item {
                ExpressiveCategoryCard(
                    title = "Background Monitoring",
                    tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Periodic Server Checks",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Queries all panels and servers every 15 minutes using WorkManager only.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = alertSettings.alertsEnabled,
                                onCheckedChange = { viewModel.setAlertsEnabled(it) },
                                modifier = Modifier.testTag("toggle_alerts_master")
                            )
                        }
                    }
                }
            }

            // Individual Alert Type Toggles
            item {
                ExpressiveCategoryCard(
                    title = "Alert Triggers (1h Cooldown)",
                    tintColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AlertToggleRow(
                            title = "Server down",
                            subtitle = "Notify when a server goes offline, stops, or crashes (running -> not running).",
                            checked = alertSettings.alertServerDown,
                            enabled = alertSettings.alertsEnabled,
                            onCheckedChange = { viewModel.setAlertServerDown(it) },
                            testTag = "toggle_alert_server_down"
                        )
                        AlertToggleRow(
                            title = "Server recovered",
                            subtitle = "Notify when a server comes back online and is running again.",
                            checked = alertSettings.alertServerRecovered,
                            enabled = alertSettings.alertsEnabled,
                            onCheckedChange = { viewModel.setAlertServerRecovered(it) },
                            testTag = "toggle_alert_server_recovered"
                        )
                        AlertToggleRow(
                            title = "High resource usage",
                            subtitle = "Notify when CPU, RAM or Disk exceeds your thresholds.",
                            checked = alertSettings.alertHighUsage,
                            enabled = alertSettings.alertsEnabled,
                            onCheckedChange = { viewModel.setAlertHighUsage(it) },
                            testTag = "toggle_alert_high_usage"
                        )
                        AlertToggleRow(
                            title = "Panel unreachable",
                            subtitle = "Notify once per panel when unreachable or token is rejected.",
                            checked = alertSettings.alertPanelUnreachable,
                            enabled = alertSettings.alertsEnabled,
                            onCheckedChange = { viewModel.setAlertPanelUnreachable(it) },
                            testTag = "toggle_alert_panel_unreachable"
                        )
                    }
                }
            }

            // Resource Alert Thresholds
            item {
                ExpressiveCategoryCard(
                    title = "Resource Thresholds",
                    tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThresholdSliderRow(
                            label = "CPU Usage Threshold",
                            value = alertSettings.cpuThreshold,
                            onValueChange = { viewModel.setCpuThreshold(it) },
                            enabled = alertSettings.alertsEnabled && alertSettings.alertHighUsage,
                            testTag = "slider_threshold_cpu"
                        )
                        ThresholdSliderRow(
                            label = "RAM Usage Threshold",
                            value = alertSettings.ramThreshold,
                            onValueChange = { viewModel.setRamThreshold(it) },
                            enabled = alertSettings.alertsEnabled && alertSettings.alertHighUsage,
                            testTag = "slider_threshold_ram"
                        )
                        ThresholdSliderRow(
                            label = "Disk Space Threshold",
                            value = alertSettings.diskThreshold,
                            onValueChange = { viewModel.setDiskThreshold(it) },
                            enabled = alertSettings.alertsEnabled && alertSettings.alertHighUsage,
                            testTag = "slider_threshold_disk"
                        )
                    }
                }
            }

            // Quick Actions: "Check Now" & "Test Alert"
            item {
                ExpressiveCategoryCard(
                    title = "Test & Verification",
                    tintColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.runImmediateAlertCheck()
                                    BubbleController.show("Server check started in background.", BubbleType.SUCCESS)
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_alerts_check_now")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.sendTestNotification()
                                    BubbleController.show("Test alert dispatched.", BubbleType.INFO)
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_alerts_send_test")
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Alert", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }

                        // Battery optimization notice
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BatteryAlert,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Battery Optimization Notice",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "Android may delay background checks when battery saver or Doze mode is active. To guarantee punctual checks every 15 minutes, allow unrestricted background battery usage.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { openBatteryOptimizationSettings(context) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_battery_optimization_settings")
                                ) {
                                    Text("Battery Optimization Settings", fontSize = 12.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun ThresholdSliderRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean,
    testTag: String
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = "${sliderValue.roundToInt()}%",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChange(sliderValue.roundToInt()) },
            valueRange = 50f..100f,
            steps = 9,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun openBatteryOptimizationSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
