package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ConnectionDiagnostic
import com.example.data.model.SavedConnection
import com.example.ui.MainViewModel
import com.example.ui.ScreenDestination
import com.example.ui.theme.ExpressiveSerifFontFamily
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusRunning
import com.example.ui.theme.StatusStarting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: MainViewModel,
    diagnostic: ConnectionDiagnostic? = null
) {
    val allPanels by viewModel.allPanels.collectAsStateWithLifecycle()
    val allPanelsDiagnostics by viewModel.allPanelsDiagnostics.collectAsStateWithLifecycle()
    val isTestingAll by viewModel.isTestingAllDiagnostics.collectAsStateWithLifecycle()
    val currentTestingPanelId by viewModel.currentTestingPanelId.collectAsStateWithLifecycle()

    // Automatically trigger checking all panels and API keys one by one when entering the screen
    LaunchedEffect(allPanels) {
        if (allPanels.isNotEmpty() && !isTestingAll && allPanelsDiagnostics.isEmpty()) {
            viewModel.runAllDiagnostics()
        }
    }

    // Determine completion state across all panels
    val hasFinished by remember(isTestingAll, allPanels, allPanelsDiagnostics) {
        derivedStateOf {
            !isTestingAll && allPanels.isNotEmpty() &&
                allPanels.all { panel ->
                    val d = allPanelsDiagnostics[panel.id]
                    d != null && !d.isTesting && d.panelReachable != null
                }
        }
    }

    val totalPanels = allPanels.size
    val completedCount = allPanels.count { panel ->
        val d = allPanelsDiagnostics[panel.id]
        d != null && !d.isTesting && d.panelReachable != null
    }

    val reachableCount = allPanels.count { allPanelsDiagnostics[it.id]?.panelReachable == true }
    val validTokenCount = allPanels.count { allPanelsDiagnostics[it.id]?.tokenValid == true }
    val totalServersFound = allPanels.mapNotNull { allPanelsDiagnostics[it.id]?.serverCount }.sum()

    val avgLatency = remember(allPanelsDiagnostics) {
        val latencies = allPanels.mapNotNull { allPanelsDiagnostics[it.id]?.latencyMs }
        if (latencies.isNotEmpty()) latencies.average().toLong() else null
    }

    val isAllHealthy = hasFinished && allPanels.all { allPanelsDiagnostics[it.id]?.isHealthy == true }

    // Animated rotation for the refresh icon when actively testing
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    // Animated progress bar
    val animatedProgress by animateFloatAsState(
        targetValue = if (totalPanels > 0) completedCount.toFloat() / totalPanels else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "diag_progress"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.diagnostics_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = ExpressiveSerifFontFamily,
                            fontSize = 24.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("diagnostics_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (allPanels.isNotEmpty()) {
                        IconButton(
                            onClick = { if (!isTestingAll) viewModel.runAllDiagnostics() },
                            enabled = !isTestingAll,
                            modifier = Modifier.testTag("refresh_diagnostics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Diagnostics",
                                tint = if (isTestingAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = if (isTestingAll) Modifier.rotate(spinAngle) else Modifier
                            )
                        }
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
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==============================================================
            // 1. STATUS CARD (Appears on top after check connection results finish)
            // ==============================================================
            item(key = "status_card_header") {
                AnimatedVisibility(
                    visible = hasFinished && totalPanels > 0,
                    enter = fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                        expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleIn(initialScale = 0.94f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(animationSpec = tween(200)) + shrinkVertically() + scaleOut(targetScale = 0.94f)
                ) {
                    DiagnosticStatusSummaryCard(
                        isAllHealthy = isAllHealthy,
                        totalPanels = totalPanels,
                        reachableCount = reachableCount,
                        validTokenCount = validTokenCount,
                        totalServersFound = totalServersFound,
                        avgLatency = avgLatency
                    )
                }
            }

            // ==============================================================
            // 2. ACTIVE TESTING PROGRESS CARD (Shows animated progress while testing)
            // ==============================================================
            item(key = "testing_progress_card") {
                AnimatedVisibility(
                    visible = isTestingAll && totalPanels > 0,
                    enter = fadeIn(animationSpec = tween(300)) + expandVertically(),
                    exit = fadeOut(animationSpec = tween(250)) + shrinkVertically()
                ) {
                    val currentPanel = allPanels.firstOrNull { it.id == currentTestingPanelId }
                    val currentPanelIndex = if (currentPanel != null) {
                        allPanels.indexOfFirst { it.id == currentPanel.id } + 1
                    } else {
                        (completedCount + 1).coerceAtMost(totalPanels)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.5.dp
                                    )
                                    Text(
                                        text = stringResource(R.string.diag_status_testing),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "$currentPanelIndex / $totalPanels",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (currentPanel != null) {
                                Text(
                                    text = stringResource(
                                        R.string.diag_checking_panel,
                                        currentPanelIndex,
                                        totalPanels,
                                        currentPanel.label
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = currentPanel.panelUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            // ==============================================================
            // 3. EMPTY STATE (When no panels are configured)
            // ==============================================================
            if (allPanels.isEmpty()) {
                item(key = "empty_panels_state") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Text(
                                text = "No Panels Configured",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.diag_no_panels),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = { viewModel.navigateTo(ScreenDestination.SETUP_CREDENTIALS) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("add_panel_from_diag_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.diag_add_panel))
                            }
                        }
                    }
                }
            }

            // ==============================================================
            // 4. PANELS DIAGNOSTIC BREAKDOWN (Checked one by one without user picking)
            // ==============================================================
            if (allPanels.isNotEmpty()) {
                item(key = "panels_header_label") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Connection Details ($totalPanels ${if (totalPanels == 1) "Panel" else "Panels"})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (isTestingAll) {
                            Text(
                                text = "Checking sequentially…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(allPanels, key = { it.id }) { panel ->
                    val panelDiag = allPanelsDiagnostics[panel.id]
                    val isCurrentlyTesting = panel.id == currentTestingPanelId || panelDiag?.isTesting == true

                    PanelSequentialDiagnosticCard(
                        panel = panel,
                        diagnostic = panelDiag,
                        isCurrentlyTesting = isCurrentlyTesting
                    )
                }

                // ==============================================================
                // 5. BOTTOM RE-RUN ACTION BUTTON
                // ==============================================================
                item(key = "bottom_action_button") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { if (!isTestingAll) viewModel.runAllDiagnostics() },
                        enabled = !isTestingAll,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("run_all_diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = if (isTestingAll) Modifier
                                .size(20.dp)
                                .rotate(spinAngle) else Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isTestingAll) "Checking All Panels…" else stringResource(R.string.diag_run_all_test),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top Status Card displayed when all panel and API key connection checks complete.
 */
@Composable
fun DiagnosticStatusSummaryCard(
    isAllHealthy: Boolean,
    totalPanels: Int,
    reachableCount: Int,
    validTokenCount: Int,
    totalServersFound: Int,
    avgLatency: Long?
) {
    val containerColor by animateColorAsState(
        targetValue = if (isAllHealthy) {
            StatusRunning.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
        },
        animationSpec = tween(400),
        label = "status_container_color"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isAllHealthy) {
            StatusRunning.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        },
        animationSpec = tween(400),
        label = "status_border_color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostic_status_summary_card"),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Status icon, title, badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (isAllHealthy) StatusRunning.copy(alpha = 0.22f) else MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAllHealthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = if (isAllHealthy) "All Healthy" else "Issues Detected",
                            tint = if (isAllHealthy) StatusRunning else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isAllHealthy) {
                                stringResource(R.string.diag_status_all_healthy)
                            } else {
                                stringResource(R.string.diag_status_issues_found)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isAllHealthy) {
                                stringResource(R.string.diag_status_all_healthy_desc, totalPanels)
                            } else {
                                val failedCount = totalPanels - reachableCount.coerceAtMost(validTokenCount)
                                stringResource(R.string.diag_status_issues_desc, failedCount, totalPanels)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                thickness = 1.dp
            )

            // Metrics row: 4 key metrics pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiagnosticMetricPill(
                    label = stringResource(R.string.diag_summary_panels),
                    value = "$reachableCount / $totalPanels",
                    isSuccess = reachableCount == totalPanels,
                    modifier = Modifier.weight(1f)
                )
                DiagnosticMetricPill(
                    label = stringResource(R.string.diag_summary_tokens),
                    value = "$validTokenCount / $totalPanels",
                    isSuccess = validTokenCount == totalPanels,
                    modifier = Modifier.weight(1f)
                )
                DiagnosticMetricPill(
                    label = stringResource(R.string.diag_summary_latency),
                    value = if (avgLatency != null) "${avgLatency}ms" else "N/A",
                    isSuccess = avgLatency != null && avgLatency < 400,
                    modifier = Modifier.weight(1f)
                )
                DiagnosticMetricPill(
                    label = stringResource(R.string.diag_summary_servers),
                    value = "$totalServersFound",
                    isSuccess = totalServersFound > 0,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun DiagnosticMetricPill(
    label: String,
    value: String,
    isSuccess: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        border = BorderStroke(
            0.5.dp,
            if (isSuccess) StatusRunning.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * Individual Panel Diagnostic Card showing detailed checks sequentially evaluated.
 */
@Composable
fun PanelSequentialDiagnosticCard(
    panel: SavedConnection,
    diagnostic: ConnectionDiagnostic?,
    isCurrentlyTesting: Boolean
) {
    val isTested = diagnostic != null && !diagnostic.isTesting && diagnostic.panelReachable != null
    val isHealthy = diagnostic?.isHealthy == true

    // Pulse animation for the currently testing card border
    val infiniteTransition = rememberInfiniteTransition(label = "border_pulse_${panel.id}")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val cardBorder = when {
        isCurrentlyTesting -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
        isTested && isHealthy -> BorderStroke(1.dp, StatusRunning.copy(alpha = 0.35f))
        isTested && !isHealthy -> BorderStroke(1.dp, StatusOffline.copy(alpha = 0.45f))
        else -> BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    }

    val cardBackground by animateColorAsState(
        targetValue = when {
            isCurrentlyTesting -> MaterialTheme.colorScheme.surfaceContainerHigh
            isTested && !isHealthy -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(300),
        label = "card_bg_${panel.id}"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("panel_diagnostic_card_${panel.id}"),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = RoundedCornerShape(22.dp),
        border = cardBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Panel Icon, Title, URL, and Status Icon with AnimatedContent
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = panel.label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = panel.panelUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Animated status icon
                AnimatedContent(
                    targetState = when {
                        isCurrentlyTesting -> DiagnosticItemStatus.TESTING
                        isTested && isHealthy -> DiagnosticItemStatus.SUCCESS
                        isTested && !isHealthy -> DiagnosticItemStatus.FAILED
                        else -> DiagnosticItemStatus.QUEUED
                    },
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.8f)) togetherWith
                            (fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f))
                    },
                    label = "status_icon_${panel.id}"
                ) { status ->
                    when (status) {
                        DiagnosticItemStatus.TESTING -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Testing…",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        DiagnosticItemStatus.SUCCESS -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Operational",
                                tint = StatusRunning,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        DiagnosticItemStatus.FAILED -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Connection Failed",
                                tint = StatusOffline,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        DiagnosticItemStatus.QUEUED -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Queued",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Queued",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // Checks breakdown: reachability, API token, latency, servers
            if (isTested || isCurrentlyTesting) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    thickness = 1.dp
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Check 1: Panel Reachability
                    DiagnosticCheckRow(
                        icon = Icons.Default.Dns,
                        title = stringResource(R.string.diag_panel_reachability),
                        detail = when {
                            diagnostic?.panelReachable == true -> "HTTP/HTTPS Endpoint Reachable"
                            diagnostic?.panelReachable == false -> "Unreachable / Host Lookup Failed"
                            else -> "Testing endpoint reachability…"
                        },
                        status = when (diagnostic?.panelReachable) {
                            true -> CheckStatus.PASSED
                            false -> CheckStatus.FAILED
                            null -> CheckStatus.IN_PROGRESS
                        }
                    )

                    // Check 2: API Token Authentication
                    DiagnosticCheckRow(
                        icon = Icons.Default.Key,
                        title = stringResource(R.string.diag_token_auth),
                        detail = when {
                            diagnostic?.tokenValid == true -> "Client API Token Valid (ptlc_***)"
                            diagnostic?.tokenValid == false -> "API Token Rejected (401 Unauthorized)"
                            else -> "Verifying API key permissions…"
                        },
                        status = when (diagnostic?.tokenValid) {
                            true -> CheckStatus.PASSED
                            false -> CheckStatus.FAILED
                            null -> CheckStatus.IN_PROGRESS
                        }
                    )

                    // Check 3: Latency & Ping
                    DiagnosticCheckRow(
                        icon = Icons.Default.Timer,
                        title = stringResource(R.string.diag_latency),
                        detail = when {
                            diagnostic?.latencyMs != null -> "${diagnostic.latencyMs} ms (${diagnostic.latencyRating})"
                            diagnostic?.panelReachable == false -> "Failed to establish TCP/TLS connection"
                            else -> "Measuring round-trip API ping…"
                        },
                        status = when {
                            diagnostic?.latencyMs != null -> CheckStatus.PASSED
                            diagnostic?.panelReachable == false -> CheckStatus.FAILED
                            else -> CheckStatus.IN_PROGRESS
                        }
                    )

                    // Check 4: User Permissions & Server Count
                    DiagnosticCheckRow(
                        icon = Icons.Default.Storage,
                        title = stringResource(R.string.diag_user_permissions),
                        detail = when {
                            diagnostic?.serverCount != null -> "${diagnostic.serverCount} game server(s) accessible"
                            diagnostic?.tokenValid == false -> "Cannot discover servers without valid credentials"
                            else -> "Querying server inventory…"
                        },
                        status = when {
                            diagnostic?.serverCount != null -> CheckStatus.PASSED
                            diagnostic?.tokenValid == false -> CheckStatus.FAILED
                            else -> CheckStatus.IN_PROGRESS
                        }
                    )
                }
            }

            // Error message explanation card if an issue occurred
            if (!diagnostic?.errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Diagnostic Issue",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = diagnostic!!.errorMessage!!,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

enum class DiagnosticItemStatus {
    QUEUED,
    TESTING,
    SUCCESS,
    FAILED
}

enum class CheckStatus {
    PASSED,
    FAILED,
    IN_PROGRESS
}

@Composable
fun DiagnosticCheckRow(
    icon: ImageVector,
    title: String,
    detail: String,
    status: CheckStatus
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when (status) {
                    CheckStatus.PASSED -> MaterialTheme.colorScheme.primary
                    CheckStatus.FAILED -> MaterialTheme.colorScheme.error
                    CheckStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (status) {
            CheckStatus.PASSED -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Passed",
                    tint = StatusRunning,
                    modifier = Modifier.size(16.dp)
                )
            }
            CheckStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Failed",
                    tint = StatusOffline,
                    modifier = Modifier.size(16.dp)
                )
            }
            CheckStatus.IN_PROGRESS -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
