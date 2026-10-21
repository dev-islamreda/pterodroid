package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.R
import com.example.data.model.ServerAttributes
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.components.BubbleController
import com.example.ui.components.BubbleType
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.ExpressiveHeader
import com.example.ui.console.ConsoleViewModel
import com.example.ui.console.GeminiAnalysisState
import com.example.ui.console.TerminalLogFormatter

@Composable
fun ConsoleScreen(
    mainViewModel: MainViewModel,
    consoleViewModel: ConsoleViewModel,
    server: ServerAttributes,
    logs: List<String>,
    connectionStatus: String,
    isWsConnected: Boolean,
    autoScroll: Boolean,
    commandInput: String,
    geminiState: GeminiAnalysisState
) {
    val listState = rememberLazyListState()
    val isSendingCommand by consoleViewModel.isSendingCommand.collectAsStateWithLifecycle()
    val powerActionState by consoleViewModel.powerActionState.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val isKeyboardOpen = imeBottomPx > 0

    var showRestartDialog by remember { mutableStateOf(false) }
    var showKillDialog by remember { mutableStateOf(false) }

    // Long-press popup menu state & tap position
    var isMenuExpanded by remember { mutableStateOf(false) }
    var menuPressOffset by remember { mutableStateOf(Offset.Zero) }

    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(logs.size, autoScroll) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    LaunchedEffect(powerActionState) {
        if (powerActionState is UiState.Success) {
            BubbleController.show(
                (powerActionState as UiState.Success<String>).data,
                BubbleType.SUCCESS
            )
            consoleViewModel.clearPowerFeedback()
        } else if (powerActionState is UiState.Error) {
            BubbleController.show(
                (powerActionState as UiState.Error).message,
                BubbleType.ERROR
            )
            consoleViewModel.clearPowerFeedback()
        }
    }

    val currentUser by mainViewModel.currentUser.collectAsStateWithLifecycle()
    val userProfile by mainViewModel.userProfile.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ExpressiveHeader(
                title = stringResource(R.string.nav_console),
                avatarUrl = userProfile?.photoUrl ?: currentUser?.photoUrl?.toString(),
                displayName = userProfile?.displayName ?: currentUser?.displayName,
                isSignedIn = currentUser != null,
                onAvatarClick = { mainViewModel.openAccountSettings() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (isKeyboardOpen) 0.dp else 84.dp)
                    .imePadding()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (powerActionState is UiState.Loading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp)
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Main Terminal Output Card - Gained full height from removed buttons row
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .testTag("console_output_card"),
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .semantics {
                                onLongClick(label = "More console options") {
                                    menuPressOffset = Offset(100f, 100f)
                                    isMenuExpanded = true
                                    true
                                }
                                customActions = listOf(
                                    CustomAccessibilityAction("More console options") {
                                        menuPressOffset = Offset(100f, 100f)
                                        isMenuExpanded = true
                                        true
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = { offset ->
                                        menuPressOffset = offset
                                    },
                                    onLongPress = { offset ->
                                        menuPressOffset = offset
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isMenuExpanded = true
                                    }
                                )
                            }
                    ) {
                        if (logs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.console_empty_output),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp)
                                        .testTag("console_empty_text")
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                contentPadding = PaddingValues(bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                items(logs) { line ->
                                    TerminalLine(line, isDark = isDark)
                                }
                            }
                        }

                        // Long-Press Dropdown Menu positioned at the press point
                        val menuOffsetDp = with(density) {
                            DpOffset(
                                x = menuPressOffset.x.toDp().coerceAtLeast(8.dp),
                                y = menuPressOffset.y.toDp().coerceAtLeast(8.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false },
                            offset = menuOffsetDp,
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier.testTag("console_long_press_menu")
                        ) {
                            // a. Clear console
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Clear console",
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                enabled = logs.isNotEmpty(),
                                onClick = {
                                    isMenuExpanded = false
                                    consoleViewModel.clearLogs()
                                },
                                modifier = Modifier.testTag("menu_clear_console")
                            )

                            // b. Copy logs
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Copy logs",
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                enabled = logs.isNotEmpty(),
                                onClick = {
                                    isMenuExpanded = false
                                    val text = consoleViewModel.getLogsAsPlainText()
                                    clipboardManager.setText(AnnotatedString(text))
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Console Logs", text))
                                    BubbleController.show(
                                        context.getString(R.string.console_logs_copied),
                                        BubbleType.INFO
                                    )
                                },
                                modifier = Modifier.testTag("menu_copy_logs")
                            )

                            // c. Download logs
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Download logs",
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                enabled = logs.isNotEmpty(),
                                onClick = {
                                    isMenuExpanded = false
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val result = consoleViewModel.downloadLogsAsJson(context)
                                        withContext(Dispatchers.Main) {
                                            result.onSuccess { fileName ->
                                                BubbleController.show(
                                                    context.getString(R.string.console_logs_downloaded, fileName),
                                                    BubbleType.SUCCESS
                                                )
                                            }.onFailure { e ->
                                                BubbleController.show(
                                                    context.getString(R.string.console_logs_download_failed, e.localizedMessage ?: "Unknown error"),
                                                    BubbleType.ERROR
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("menu_download_logs")
                            )

                            // d. Divider
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // e. Start (Green)
                            val canStart = powerActionState !is UiState.Loading && consoleViewModel.canControlPower
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Start",
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (canStart) Color(0xFF456B49) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (canStart) Color(0xFF456B49) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                enabled = canStart,
                                onClick = {
                                    isMenuExpanded = false
                                    consoleViewModel.requestPowerAction("start")
                                },
                                modifier = Modifier.testTag("menu_power_start")
                            )

                            // f. Restart (Orange)
                            val canRestart = powerActionState !is UiState.Loading && consoleViewModel.canControlPower
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Restart",
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (canRestart) Color(0xFFC0704D) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = if (canRestart) Color(0xFFC0704D) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                enabled = canRestart,
                                onClick = {
                                    isMenuExpanded = false
                                    showRestartDialog = true
                                },
                                modifier = Modifier.testTag("menu_power_restart")
                            )

                            // g. Kill (Red)
                            val canKill = powerActionState !is UiState.Loading && consoleViewModel.canControlPower
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Kill",
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (canKill) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = if (canKill) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                enabled = canKill,
                                onClick = {
                                    isMenuExpanded = false
                                    showKillDialog = true
                                },
                                modifier = Modifier.testTag("menu_power_kill")
                            )
                        }
                    }

                    // Command Input Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 14.dp, top = 4.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(start = 14.dp, end = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (commandInput.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.console_command_placeholder),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        ),
                                        maxLines = 1
                                    )
                                }
                                BasicTextField(
                                    value = commandInput,
                                    onValueChange = { consoleViewModel.onCommandChange(it) },
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    singleLine = true,
                                    enabled = !isSendingCommand && consoleViewModel.canSendCommands,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = {
                                        if (commandInput.isNotBlank()) {
                                            consoleViewModel.sendCurrentCommand()
                                        }
                                    }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("console_command_input")
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            val isSendEnabled = commandInput.isNotBlank() && !isSendingCommand && consoleViewModel.canSendCommands
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSendEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable(
                                        enabled = isSendEnabled,
                                        onClick = { consoleViewModel.sendCurrentCommand() }
                                    )
                                    .testTag("console_send_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSendingCommand) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = stringResource(R.string.action_send),
                                        tint = if (isSendEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Static tip under the console box (in place of old button row)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 4.dp, bottom = if (isKeyboardOpen) 8.dp else 14.dp)
                        .testTag("console_hold_tip")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hold the console to open more options",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showRestartDialog) {
        ConfirmationDialog(
            show = true,
            title = stringResource(R.string.dialog_console_restart_title),
            message = stringResource(R.string.dialog_console_restart_msg),
            confirmButtonText = stringResource(R.string.console_power_restart),
            isDestructive = false,
            onConfirm = {
                showRestartDialog = false
                consoleViewModel.confirmPowerAction("restart")
            },
            onDismiss = { showRestartDialog = false }
        )
    }

    if (showKillDialog) {
        ConfirmationDialog(
            show = true,
            title = stringResource(R.string.dialog_console_kill_title),
            message = stringResource(R.string.dialog_console_kill_msg),
            confirmButtonText = stringResource(R.string.console_power_kill),
            isDestructive = true,
            onConfirm = {
                showKillDialog = false
                consoleViewModel.confirmPowerAction("kill")
            },
            onDismiss = { showKillDialog = false }
        )
    }
}

@Composable
fun TerminalLine(line: String, isDark: Boolean = true) {
    val clean = TerminalLogFormatter.cleanAnsi(line)
    val color = TerminalLogFormatter.getLineColor(clean, isDark = isDark)
    Text(
        text = clean,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
}
