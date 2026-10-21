package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.FileAttributes
import com.example.data.model.FileItemWrapper
import com.example.data.model.ServerAttributes
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.ExpressiveHeader
import com.example.ui.components.FormatUtils
import com.example.ui.components.LoadingView
import com.example.ui.components.PteroTopBar
import com.example.ui.files.FilesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    mainViewModel: MainViewModel,
    filesViewModel: FilesViewModel,
    server: ServerAttributes,
    currentDirectory: String,
    filesState: UiState<List<FileItemWrapper>>,
    activeEditingFile: FileAttributes?,
    editorContent: String,
    operationStatus: UiState<String>
) {
    val context = LocalContext.current
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<FileAttributes?>(null) }
    var itemToDelete by remember { mutableStateOf<FileAttributes?>(null) }
    var itemToMove by remember { mutableStateOf<FileAttributes?>(null) }
    var itemToExtract by remember { mutableStateOf<FileAttributes?>(null) }

    val uploadProgress by filesViewModel.uploadProgress.collectAsStateWithLifecycle()
    val downloadUrlToShare by filesViewModel.downloadUrlToShare.collectAsStateWithLifecycle()
    val selectedItems by filesViewModel.selectedItems.collectAsStateWithLifecycle()
    val isSelectionMode = selectedItems.isNotEmpty()

    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showCompressSelectedDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) {
        filesViewModel.clearSelection()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            filesViewModel.uploadFiles(uris, context.contentResolver)
        }
    }

    LaunchedEffect(operationStatus) {
        if (operationStatus is UiState.Success) {
            com.example.ui.components.BubbleController.show(operationStatus.data, com.example.ui.components.BubbleType.SUCCESS)
            filesViewModel.clearOperationStatus()
        } else if (operationStatus is UiState.Error) {
            com.example.ui.components.BubbleController.show(operationStatus.message, com.example.ui.components.BubbleType.ERROR)
            filesViewModel.clearOperationStatus()
        }
    }

    LaunchedEffect(downloadUrlToShare) {
        downloadUrlToShare?.let { url ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Pterodactyl Download URL", url))
            com.example.ui.components.BubbleController.show("Direct download link copied to clipboard.", com.example.ui.components.BubbleType.SUCCESS)
            filesViewModel.clearDownloadUrl()
        }
    }

    if (activeEditingFile != null) {
        FileEditorScreen(
            fileName = activeEditingFile.name,
            content = editorContent,
            onContentChange = { filesViewModel.onEditorContentChange(it) },
            onSave = { filesViewModel.saveCurrentFile() },
            onClose = { filesViewModel.closeEditor() },
            isSaving = operationStatus is UiState.Loading
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.files_selected_count, selectedItems.size),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { filesViewModel.clearSelection() },
                            modifier = Modifier.testTag("cancel_selection_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val allNames = (filesState as? UiState.Success)?.data?.map { it.attributes.name } ?: emptyList()
                                filesViewModel.selectAll(allNames)
                            },
                            modifier = Modifier.testTag("select_all_files_button")
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { showCompressSelectedDialog = true },
                            modifier = Modifier.testTag("compress_selected_button")
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = "Compress", tint = Color(0xFFD49B35))
                        }
                        IconButton(
                            onClick = { showDeleteSelectedDialog = true },
                            modifier = Modifier.testTag("delete_selected_button")
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            } else {
                val currentUser by mainViewModel.currentUser.collectAsStateWithLifecycle()
                val userProfile by mainViewModel.userProfile.collectAsStateWithLifecycle()
                ExpressiveHeader(
                    title = stringResource(R.string.nav_files),
                    avatarUrl = userProfile?.photoUrl ?: currentUser?.photoUrl?.toString(),
                    displayName = userProfile?.displayName ?: currentUser?.displayName,
                    isSignedIn = currentUser != null,
                    onAvatarClick = { mainViewModel.openAccountSettings() }
                )
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
                if (uploadProgress != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = uploadProgress ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else if (operationStatus is UiState.Loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Path & Quick Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentDirectory != "/" && currentDirectory.isNotBlank()) {
                                IconButton(
                                    onClick = { filesViewModel.navigateUp() },
                                    modifier = Modifier.size(24.dp).testTag("navigate_up_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up", modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = currentDirectory,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.size(36.dp).testTag("upload_files_button")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Upload", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { showNewFolderDialog = true },
                            modifier = Modifier.size(36.dp).testTag("new_folder_button")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { showNewFileDialog = true },
                            modifier = Modifier.size(36.dp).testTag("new_file_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "New File", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { filesViewModel.loadFiles() },
                            modifier = Modifier.size(36.dp).testTag("refresh_files_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                when (filesState) {
                    is UiState.Loading -> {
                        LoadingView(message = "Reading directory contents…")
                    }
                    is UiState.Error -> {
                        Column(modifier = Modifier.padding(20.dp)) {
                            ErrorBanner(
                                message = filesState.message,
                                onRetry = { filesViewModel.loadFiles() }
                            )
                        }
                    }
                    is UiState.Success -> {
                        val fileList = filesState.data
                        if (fileList.isEmpty()) {
                            EmptyState(
                                icon = Icons.Outlined.FolderOpen,
                                title = stringResource(R.string.file_empty_folder),
                                description = "No files or subdirectories found in $currentDirectory.",
                                actionText = "Refresh",
                                onAction = { filesViewModel.loadFiles() }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(fileList, key = { it.attributes.name }) { item ->
                                    val attr = item.attributes
                                    val isSelected = selectedItems.contains(attr.name)
                                    ExpressiveFileItem(
                                        file = attr,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = isSelected,
                                        onToggleSelect = { filesViewModel.toggleSelection(attr.name) },
                                        onLongClick = { filesViewModel.toggleSelection(attr.name) },
                                        onClick = {
                                            if (isSelectionMode) {
                                                filesViewModel.toggleSelection(attr.name)
                                            } else {
                                                if (attr.isFile) {
                                                    if (attr.isEditableText()) {
                                                        filesViewModel.openFileForEditing(attr)
                                                    } else if (attr.isArchive()) {
                                                        itemToExtract = attr
                                                    } else {
                                                        filesViewModel.downloadFile(attr, context)
                                                    }
                                                } else {
                                                    filesViewModel.openDirectory(attr.name)
                                                }
                                            }
                                        },
                                        onEdit = { filesViewModel.openFileForEditing(attr) },
                                        onDownload = { filesViewModel.downloadFile(attr, context) },
                                        onCopy = { filesViewModel.copyItem(attr) },
                                        onMove = { itemToMove = attr },
                                        onCompress = { filesViewModel.compressItem(attr) },
                                        onExtract = { itemToExtract = attr },
                                        onRename = { itemToRename = attr },
                                        onDelete = { itemToDelete = attr }
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

    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.dialog_new_folder_title)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text(stringResource(R.string.hint_folder_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            filesViewModel.createFolder(folderName.trim())
                            showNewFolderDialog = false
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    enabled = folderName.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showNewFileDialog) {
        var newFileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.dialog_new_file_title)) },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    placeholder = { Text(stringResource(R.string.hint_file_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            filesViewModel.createNewFile(newFileName.trim())
                            showNewFileDialog = false
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    enabled = newFileName.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    itemToRename?.let { target ->
        var newName by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.dialog_rename_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newName != target.name) {
                            filesViewModel.rename(target.name, newName.trim())
                        }
                        itemToRename = null
                    },
                    shape = RoundedCornerShape(16.dp),
                    enabled = newName.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    itemToMove?.let { target ->
        var targetDir by remember { mutableStateOf(currentDirectory) }
        AlertDialog(
            onDismissRequest = { itemToMove = null },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.dialog_move_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Moving: ${target.name}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = targetDir,
                        onValueChange = { targetDir = it },
                        placeholder = { Text(stringResource(R.string.hint_move_destination)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetDir.isNotBlank()) {
                            filesViewModel.moveItem(target, targetDir.trim())
                        }
                        itemToMove = null
                    },
                    shape = RoundedCornerShape(16.dp),
                    enabled = targetDir.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToMove = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    itemToExtract?.let { target ->
        var extractDest by remember { mutableStateOf(currentDirectory) }
        AlertDialog(
            onDismissRequest = { itemToExtract = null },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.dialog_extract_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Extracting: ${target.name}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = extractDest,
                        onValueChange = { extractDest = it },
                        placeholder = { Text(stringResource(R.string.hint_extract_destination)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (extractDest.isNotBlank()) {
                            filesViewModel.decompressItem(target, extractDest.trim())
                        }
                        itemToExtract = null
                    },
                    shape = RoundedCornerShape(16.dp),
                    enabled = extractDest.isNotBlank()
                ) {
                    Text(stringResource(R.string.files_action_extract))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToExtract = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    itemToDelete?.let { target ->
        ConfirmationDialog(
            show = true,
            title = stringResource(R.string.dialog_delete_file_title),
            message = stringResource(R.string.dialog_delete_file_msg, target.name),
            confirmButtonText = stringResource(R.string.action_delete),
            isDestructive = true,
            onConfirm = {
                filesViewModel.deleteItem(target.name)
                itemToDelete = null
            },
            onDismiss = { itemToDelete = null }
        )
    }

    if (showDeleteSelectedDialog) {
        ConfirmationDialog(
            show = true,
            title = stringResource(R.string.dialog_delete_selected_title),
            message = stringResource(R.string.dialog_delete_selected_msg, selectedItems.size),
            confirmButtonText = stringResource(R.string.action_delete),
            isDestructive = true,
            onConfirm = {
                filesViewModel.deleteSelected(selectedItems.toList())
                showDeleteSelectedDialog = false
            },
            onDismiss = { showDeleteSelectedDialog = false }
        )
    }

    if (showCompressSelectedDialog) {
        var archiveName by remember { mutableStateOf("archive.zip") }
        AlertDialog(
            onDismissRequest = { showCompressSelectedDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.dialog_compress_archive_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.dialog_compress_archive_msg), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = archiveName,
                        onValueChange = { archiveName = it },
                        placeholder = { Text(stringResource(R.string.hint_archive_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("archive_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (archiveName.isNotBlank()) {
                            filesViewModel.compressSelected(selectedItems.toList(), archiveName)
                            showCompressSelectedDialog = false
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    enabled = archiveName.isNotBlank(),
                    modifier = Modifier.testTag("confirm_compress_button")
                ) {
                    Text(stringResource(R.string.files_action_compress))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCompressSelectedDialog = false },
                    modifier = Modifier.testTag("cancel_compress_button")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressiveFileItem(
    file: FileAttributes,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDownload: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect() else onClick()
                },
                onLongClick = onLongClick
            )
            .testTag("file_item_${file.name}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.size(24.dp).testTag("file_select_checkbox_${file.name}")
                    )
                }

                val icon = when {
                    !file.isFile -> Icons.Default.Folder
                    file.isArchive() -> Icons.Default.FolderZip
                    else -> Icons.Default.Description
                }
                val iconTint = when {
                    !file.isFile -> MaterialTheme.colorScheme.primary
                    file.isArchive() -> Color(0xFFD49B35)
                    else -> MaterialTheme.colorScheme.secondary
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )

                Column {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (!file.isFile) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (file.isFile) {
                        Text(
                            text = FormatUtils.formatBytes(file.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!isSelectionMode) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp).testTag("file_options_${file.name}")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(18.dp))
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        if (file.isEditableText()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.files_action_edit)) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                        }
                        if (file.isArchive()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.files_action_extract)) },
                                onClick = {
                                    menuExpanded = false
                                    onExtract()
                                },
                                leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null, tint = Color(0xFFD49B35)) }
                            )
                        }
                        if (file.isFile) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.files_action_download)) },
                                onClick = {
                                    menuExpanded = false
                                    onDownload()
                                },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.files_action_copy)) },
                            onClick = {
                                menuExpanded = false
                                onCopy()
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.files_action_move)) },
                            onClick = {
                                menuExpanded = false
                                onMove()
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.files_action_compress)) },
                            onClick = {
                                menuExpanded = false
                                onCompress()
                            },
                            leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.files_action_rename)) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.files_action_delete)) },
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
}

@Composable
fun FileEditorScreen(
    fileName: String,
    content: String,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    isSaving: Boolean
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PteroTopBar(
                title = stringResource(R.string.file_editor_title, fileName),
                onBack = onClose,
                actions = {
                    IconButton(
                        onClick = onSave,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("save_file_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = stringResource(R.string.action_save),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(14.dp)
        ) {
            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("file_content_editor"),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                ),
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}
