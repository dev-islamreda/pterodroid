package com.example.ui.screens

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ResourceStats
import com.example.data.model.ServerAttributes
import com.example.data.model.ServerStatsAttributes
import com.example.ui.MainViewModel
import com.example.ui.ScreenDestination
import com.example.ui.UiState
import com.example.ui.components.BubbleController
import com.example.ui.components.BubbleType
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.ExpressiveCategoryCard
import com.example.ui.components.ExpressiveHeader
import com.example.ui.components.FormatUtils
import com.example.ui.components.ServerSwitcherDialog
import com.example.ui.components.StatusBadge
import com.example.ui.server.ServerViewModel
import com.example.ui.theme.ExpressiveSerifFontFamily
import com.example.ui.theme.StatusRunning
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDashboardScreen(
    mainViewModel: MainViewModel,
    serverViewModel: ServerViewModel,
    server: ServerAttributes,
    statsState: UiState<ServerStatsAttributes>,
    powerState: UiState<String>
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var showServerSwitcher by remember { mutableStateOf(false) }
    var showShutdownDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showStartDialog by remember { mutableStateOf(false) }

    val allServers by mainViewModel.allServers.collectAsStateWithLifecycle()
    val isRefreshing by serverViewModel.isRefreshing.collectAsStateWithLifecycle()
    val currentServer by serverViewModel.serverDetails.collectAsStateWithLifecycle()

    // Reduced motion check
    val isReducedMotion = remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }

    LaunchedEffect(powerState) {
        if (powerState is UiState.Success) {
            BubbleController.show(powerState.data, BubbleType.SUCCESS)
            serverViewModel.clearPowerFeedback()
        } else if (powerState is UiState.Error) {
            BubbleController.show(powerState.message, BubbleType.ERROR)
            serverViewModel.clearPowerFeedback()
        }
    }

    val stats = (statsState as? UiState.Success)?.data
    val resources = stats?.resources ?: ResourceStats()
    val currentState = stats?.currentState ?: currentServer.status ?: "offline"

    val currentUser by mainViewModel.currentUser.collectAsStateWithLifecycle()
    val userProfile by mainViewModel.userProfile.collectAsStateWithLifecycle()

    val isPowerActionInProgress = powerState is UiState.Loading
    val lazyListState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ExpressiveHeader(
                title = stringResource(R.string.nav_dashboard),
                avatarUrl = userProfile?.photoUrl ?: currentUser?.photoUrl?.toString(),
                displayName = userProfile?.displayName ?: currentUser?.displayName,
                isSignedIn = currentUser != null,
                onAvatarClick = { mainViewModel.openAccountSettings() }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (!isPowerActionInProgress) {
                    serverViewModel.refresh()
                }
            },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .testTag("dashboard_pull_to_refresh_box"),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 10.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. POWER CONTROLS CARD
                item {
                    ExpressiveCategoryCard(
                        title = "Power Controls",
                        tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentServer.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Node: ${currentServer.node} • ${currentServer.identifier}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                // Animated transition for server state status badge
                                AnimatedContent(
                                    targetState = currentState,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(200))
                                    },
                                    label = "status_badge_transition"
                                ) { targetStatus ->
                                    StatusBadge(status = targetStatus)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // START BUTTON
                                Button(
                                    onClick = {
                                        if (currentState.lowercase().contains("running")) {
                                            showStartDialog = true
                                        } else {
                                            serverViewModel.sendPowerSignal("start")
                                        }
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF456B49),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("power_start")
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Start Server",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // RESTART BUTTON
                                Button(
                                    onClick = { showRestartDialog = true },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFC0704D),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("power_restart")
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Restart Server",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // SHUTDOWN BUTTON
                                Button(
                                    onClick = { showShutdownDialog = true },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("power_shutdown")
                                ) {
                                    Icon(
                                        Icons.Default.PowerSettingsNew,
                                        contentDescription = "Stop Server",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. RESOURCE USAGE CARD WITH ANIMATED METRICS
                item {
                    ExpressiveCategoryCard(
                        title = "Resource Usage",
                        tintColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Live Uptime",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text(
                                            text = FormatUtils.formatUptime(resources.uptimeMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // CPU with animated values
                            val cpuLimit = currentServer.limits.cpu
                            val targetCpuPercent = resources.cpuAbsolute
                            val animatedCpuPercent by animateFloatAsState(
                                targetValue = targetCpuPercent.toFloat(),
                                animationSpec = if (isReducedMotion) snapAnimation() else tween(durationMillis = 400, easing = FastOutSlowInEasing),
                                label = "animated_cpu_value"
                            )

                            val cpuProgress = if (cpuLimit > 0) {
                                (animatedCpuPercent / cpuLimit).toFloat().coerceIn(0f, 1f)
                            } else {
                                (animatedCpuPercent / 100f).coerceIn(0f, 1f)
                            }
                            val cpuLimitFormatted = if (cpuLimit > 0) "/ ${cpuLimit.toInt()}%" else "/ Unlimited"

                            ExpressiveResourceRow(
                                icon = Icons.Default.Speed,
                                title = stringResource(R.string.stat_cpu),
                                currentValue = String.format("%.1f%%", animatedCpuPercent),
                                limitValue = cpuLimitFormatted,
                                progress = cpuProgress,
                                color = MaterialTheme.colorScheme.primary,
                                testTag = "resource_cpu"
                            )

                            // Memory with animated values
                            val memBytes = resources.memoryBytes
                            val animatedMemBytes by animateFloatAsState(
                                targetValue = memBytes.toFloat(),
                                animationSpec = if (isReducedMotion) snapAnimation() else tween(durationMillis = 400, easing = FastOutSlowInEasing),
                                label = "animated_mem_value"
                            )
                            val memLimitBytes = currentServer.limits.memory * 1024L * 1024L
                            val memProgress = if (memLimitBytes > 0) {
                                (animatedMemBytes / memLimitBytes.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            val memLimitFormatted = if (memLimitBytes > 0) "/ ${FormatUtils.formatBytes(memLimitBytes)}" else "/ Unlimited"

                            ExpressiveResourceRow(
                                icon = Icons.Default.Memory,
                                title = stringResource(R.string.stat_memory),
                                currentValue = FormatUtils.formatBytes(animatedMemBytes.toLong()),
                                limitValue = memLimitFormatted,
                                progress = memProgress,
                                color = MaterialTheme.colorScheme.secondary,
                                testTag = "resource_memory"
                            )

                            // Disk with animated values
                            val diskBytes = resources.diskBytes
                            val animatedDiskBytes by animateFloatAsState(
                                targetValue = diskBytes.toFloat(),
                                animationSpec = if (isReducedMotion) snapAnimation() else tween(durationMillis = 400, easing = FastOutSlowInEasing),
                                label = "animated_disk_value"
                            )
                            val diskLimitBytes = currentServer.limits.disk * 1024L * 1024L
                            val diskProgress = if (diskLimitBytes > 0) {
                                (animatedDiskBytes / diskLimitBytes.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            val diskLimitFormatted = if (diskLimitBytes > 0) "/ ${FormatUtils.formatBytes(diskLimitBytes)}" else "/ Unlimited"

                            ExpressiveResourceRow(
                                icon = Icons.Default.Storage,
                                title = stringResource(R.string.stat_disk),
                                currentValue = FormatUtils.formatBytes(animatedDiskBytes.toLong()),
                                limitValue = diskLimitFormatted,
                                progress = diskProgress,
                                color = Color(0xFFD49B35),
                                testTag = "resource_disk"
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // Network Transfer Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "RX", tint = StatusRunning, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = stringResource(R.string.stat_rx, FormatUtils.formatBytes(resources.networkRxBytes)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "TX", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = stringResource(R.string.stat_tx, FormatUtils.formatBytes(resources.networkTxBytes)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. INFORMATION CARD
                item {
                    ExpressiveCategoryCard(
                        title = "Information",
                        tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardDetailRow("Server Name", currentServer.name)
                            DashboardDetailRow("Node", currentServer.node)
                            DashboardDetailRow("Identifier", currentServer.identifier)
                            val alloc = currentServer.relationships?.allocations?.data?.firstOrNull()?.attributes
                            if (alloc != null) {
                                DashboardDetailRow("Primary Address", "${alloc.ip}:${alloc.port}")
                            }
                        }
                    }
                }
            }
        }
    }

    ServerSwitcherDialog(
        show = showServerSwitcher,
        servers = allServers,
        currentServerId = currentServer.identifier,
        onSelectServer = { target ->
            mainViewModel.selectServer(target)
            showServerSwitcher = false
        },
        onViewAllServers = {
            mainViewModel.navigateTo(ScreenDestination.SERVERS)
            showServerSwitcher = false
        },
        onDismiss = { showServerSwitcher = false }
    )

    if (showShutdownDialog) {
        AlertDialog(
            onDismissRequest = { showShutdownDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text(stringResource(R.string.dialog_shutdown_title), fontFamily = ExpressiveSerifFontFamily)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.dialog_shutdown_msg, currentServer.name), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = {
                            serverViewModel.sendPowerSignal("stop")
                            showShutdownDialog = false
                        },
                        modifier = Modifier.fillMaxWidth().testTag("action_graceful_stop_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(stringResource(R.string.action_graceful_stop), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            serverViewModel.sendPowerSignal("kill")
                            showShutdownDialog = false
                        },
                        modifier = Modifier.fillMaxWidth().testTag("action_force_kill_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_force_kill), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showShutdownDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    ConfirmationDialog(
        show = showRestartDialog,
        title = stringResource(R.string.dialog_restart_title),
        message = stringResource(R.string.dialog_restart_msg, currentServer.name),
        confirmButtonText = stringResource(R.string.power_restart),
        isDestructive = false,
        onConfirm = {
            serverViewModel.sendPowerSignal("restart")
            showRestartDialog = false
        },
        onDismiss = { showRestartDialog = false }
    )

    ConfirmationDialog(
        show = showStartDialog,
        title = stringResource(R.string.dialog_start_title),
        message = stringResource(R.string.dialog_start_msg, currentServer.name),
        confirmButtonText = stringResource(R.string.power_start),
        isDestructive = false,
        onConfirm = {
            serverViewModel.sendPowerSignal("start")
            showStartDialog = false
        },
        onDismiss = { showStartDialog = false }
    )
}

private fun snapAnimation() = tween<Float>(durationMillis = 0)

@Composable
private fun ExpressiveResourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    currentValue: String,
    limitValue: String,
    progress: Float,
    color: Color,
    testTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
                }
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = currentValue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = limitValue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun DashboardDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
