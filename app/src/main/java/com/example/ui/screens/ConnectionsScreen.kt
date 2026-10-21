package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SavedConnection
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.ExpressiveCategoryCard
import com.example.ui.theme.ExpressiveSerifFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val allPanels by viewModel.allPanels.collectAsStateWithLifecycle()
    val panelServersMap by viewModel.panelServersMap.collectAsStateWithLifecycle()
    var panelToEdit by remember { mutableStateOf<SavedConnection?>(null) }
    var panelToDelete by remember { mutableStateOf<SavedConnection?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Connections",
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
                        modifier = Modifier.testTag("connections_back_button")
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
            item {
                ExpressiveCategoryCard(
                    title = "Connected Panels (${allPanels.size})",
                    tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (allPanels.isEmpty()) {
                            Text(
                                text = "No panels connected yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            allPanels.forEach { panel ->
                                val count = (panelServersMap[panel.id] as? UiState.Success)?.data?.size ?: 0
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Dns,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = panel.label,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$count server(s) • ${panel.panelUrl.removePrefix("https://").removePrefix("http://").removeSuffix("/")}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.runDiagnosticsOnly(panel.panelUrl, panel.apiToken, panel.id)
                                                    viewModel.navigateTo(com.example.ui.ScreenDestination.DIAGNOSTICS)
                                                },
                                                modifier = Modifier.size(34.dp).testTag("diagnostics_panel_${panel.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Speed,
                                                    contentDescription = "Test Connection",
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { panelToEdit = panel },
                                                modifier = Modifier.size(34.dp).testTag("edit_panel_${panel.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Panel",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { panelToDelete = panel },
                                                modifier = Modifier.size(34.dp).testTag("delete_panel_${panel.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Delete,
                                                    contentDescription = "Delete Panel",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { viewModel.startAddingPanel() },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_add_panel_from_connections")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Another Panel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Edit Panel Dialog
    panelToEdit?.let { panel ->
        var editLabel by remember { mutableStateOf(panel.label) }
        var editUrl by remember { mutableStateOf(panel.panelUrl) }
        var editToken by remember { mutableStateOf(panel.apiToken) }

        AlertDialog(
            onDismissRequest = { panelToEdit = null },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text("Edit Panel", fontFamily = ExpressiveSerifFontFamily, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editLabel,
                        onValueChange = { editLabel = it },
                        label = { Text("Panel Name / Label") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_panel_label_input")
                    )
                    OutlinedTextField(
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        label = { Text("Panel URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_panel_url_input")
                    )
                    OutlinedTextField(
                        value = editToken,
                        onValueChange = { editToken = it },
                        label = { Text("Client API Key") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_panel_token_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editLabel.isNotBlank()) {
                            viewModel.updatePanel(
                                panelId = panel.id,
                                newLabel = editLabel.trim(),
                                newUrl = editUrl.trim(),
                                newToken = editToken.trim()
                            )
                            panelToEdit = null
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    enabled = editLabel.isNotBlank() && editUrl.isNotBlank() && editToken.isNotBlank(),
                    modifier = Modifier.testTag("confirm_edit_panel_button")
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { panelToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Panel Dialog
    panelToDelete?.let { panel ->
        ConfirmationDialog(
            show = true,
            title = "Remove ${panel.label}?",
            message = "This will remove '${panel.label}' and disconnect all of its servers from the list.",
            confirmButtonText = "Remove Panel",
            isDestructive = true,
            onConfirm = {
                viewModel.deletePanel(panel.id)
                panelToDelete = null
            },
            onDismiss = { panelToDelete = null }
        )
    }
}
