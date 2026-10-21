package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.BackupAttributes
import com.example.data.model.BackupItemWrapper
import com.example.data.model.ServerAttributes
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.backups.BackupsViewModel
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.ExpressiveHeader
import com.example.ui.components.FormatUtils
import com.example.ui.components.LoadingView
import com.example.ui.theme.ExpressiveSerifFontFamily
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusRunning
import com.example.ui.theme.StatusStarting

@Composable
fun BackupsScreen(
    mainViewModel: MainViewModel,
    backupsViewModel: BackupsViewModel,
    server: ServerAttributes,
    backupsState: UiState<List<BackupItemWrapper>>,
    operationStatus: UiState<String>,
    downloadUrl: String?
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var backupToRestore by remember { mutableStateOf<BackupAttributes?>(null) }
    var backupToDelete by remember { mutableStateOf<BackupAttributes?>(null) }

    LaunchedEffect(operationStatus) {
        if (operationStatus is UiState.Success) {
            com.example.ui.components.BubbleController.show(operationStatus.data, com.example.ui.components.BubbleType.SUCCESS)
            backupsViewModel.clearOperationStatus()
        } else if (operationStatus is UiState.Error) {
            com.example.ui.components.BubbleController.show(operationStatus.message, com.example.ui.components.BubbleType.ERROR)
            backupsViewModel.clearOperationStatus()
        }
    }

    LaunchedEffect(downloadUrl) {
        if (downloadUrl != null) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Backup Download Link", downloadUrl)
            clipboard.setPrimaryClip(clip)
            com.example.ui.components.BubbleController.show("Download URL copied to clipboard.", com.example.ui.components.BubbleType.SUCCESS)
            backupsViewModel.clearDownloadUrl()
        }
    }

    val currentUser by mainViewModel.currentUser.collectAsStateWithLifecycle()
    val userProfile by mainViewModel.userProfile.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ExpressiveHeader(
                    title = stringResource(R.string.nav_backups),
                    avatarUrl = userProfile?.photoUrl ?: currentUser?.photoUrl?.toString(),
                    displayName = userProfile?.displayName ?: currentUser?.displayName,
                    isSignedIn = currentUser != null,
                    onAvatarClick = { mainViewModel.openAccountSettings() }
                )

                // Quick Actions row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("create_backup_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Backup", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { backupsViewModel.loadBackups() },
                        modifier = Modifier.testTag("refresh_backups_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                when (backupsState) {
                    is UiState.Loading -> {
                        LoadingView(message = "Retrieving server backups…")
                    }
                    is UiState.Error -> {
                        Column(modifier = Modifier.padding(20.dp)) {
                            ErrorBanner(
                                message = backupsState.message,
                                onRetry = { backupsViewModel.loadBackups() }
                            )
                        }
                    }
                    is UiState.Success -> {
                        val backups = backupsState.data
                        if (backups.isEmpty()) {
                            EmptyState(
                                icon = Icons.Default.Archive,
                                title = stringResource(R.string.empty_backups),
                                description = "No backups have been created for this server yet.",
                                actionText = stringResource(R.string.action_create_backup),
                                onAction = { showCreateDialog = true }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(backups, key = { it.attributes.uuid }) { item ->
                                    val backup = item.attributes
                                    ExpressiveBackupCard(
                                        backup = backup,
                                        onDownload = { backupsViewModel.fetchDownloadUrl(backup.uuid) },
                                        onRestore = { backupToRestore = backup },
                                        onDelete = { backupToDelete = backup }
                                    )
                                }
                            }
                        }
                    }
                    is UiState.Idle -> {}
                }
            }
        }
    }

    if (showCreateDialog) {
        var backupName by remember { mutableStateOf("") }
        var isLocked by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.dialog_create_backup_title), fontFamily = ExpressiveSerifFontFamily) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = backupName,
                        onValueChange = { backupName = it },
                        label = { Text(stringResource(R.string.hint_backup_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = isLocked,
                            onCheckedChange = { isLocked = it }
                        )
                        Text(
                            text = stringResource(R.string.label_locked_backup),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        backupsViewModel.createBackup(backupName.trim(), isLocked)
                        showCreateDialog = false
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    backupToRestore?.let { target ->
        ConfirmationDialog(
            show = true,
            title = stringResource(R.string.dialog_restore_backup_title),
            message = stringResource(R.string.dialog_restore_backup_msg),
            confirmButtonText = stringResource(R.string.action_restore_backup),
            isDestructive = true,
            onConfirm = {
                backupsViewModel.restoreBackup(target.uuid, truncate = false)
                backupToRestore = null
            },
            onDismiss = { backupToRestore = null }
        )
    }

    backupToDelete?.let { target ->
        ConfirmationDialog(
            show = true,
            title = stringResource(R.string.dialog_delete_backup_title),
            message = stringResource(R.string.dialog_delete_backup_msg, target.name),
            confirmButtonText = stringResource(R.string.action_delete),
            isDestructive = true,
            onConfirm = {
                backupsViewModel.deleteBackup(target.uuid)
                backupToDelete = null
            },
            onDismiss = { backupToDelete = null }
        )
    }
}

@Composable
fun ExpressiveBackupCard(
    backup: BackupAttributes,
    onDownload: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup_card_${backup.uuid}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val (statusIcon, statusColor) = when {
                    backup.isSuccessful -> Icons.Default.CheckCircle to StatusRunning
                    backup.completedAt == null -> Icons.Default.Schedule to StatusStarting
                    else -> Icons.Default.Error to StatusOffline
                }
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = backup.name.ifBlank { backup.uuid },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (backup.isLocked) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = FormatUtils.formatBytes(backup.bytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        backup.createdAt?.let { dateStr ->
                            Text(
                                text = dateStr.take(10),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("backup_menu_${backup.uuid}")
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_download_backup)) },
                        onClick = {
                            menuExpanded = false
                            onDownload()
                        },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_restore_backup)) },
                        onClick = {
                            menuExpanded = false
                            onRestore()
                        },
                        leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null, tint = StatusStarting) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}
