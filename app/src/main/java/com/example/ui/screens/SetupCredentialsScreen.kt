package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.api.UrlNormalizer
import com.example.data.model.SavedConnection
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.components.BotanicalCornerIllustration
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.ErrorBanner
import com.example.ui.components.ExpressiveHeader
import com.example.ui.components.PteroPilotLogo
import com.example.ui.theme.ExpressiveSerifFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupCredentialsScreen(
    viewModel: MainViewModel,
    savedConnections: List<SavedConnection>,
    connectState: UiState<Unit>,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val allPanels by viewModel.allPanels.collectAsStateWithLifecycle()

    var panelUrl by remember { mutableStateOf("") }
    var apiToken by remember { mutableStateOf("") }
    var profileLabel by remember { mutableStateOf("") }

    var urlError by remember { mutableStateOf<String?>(null) }
    var tokenError by remember { mutableStateOf<String?>(null) }
    var isTokenVisible by remember { mutableStateOf(false) }
    var showSavedSheet by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<SavedConnection?>(null) }

    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun validateInputs(): Pair<String, String>? {
        val rawUrl = panelUrl.trim()
        val rawToken = apiToken.trim()
        var valid = true

        if (rawUrl.isBlank()) {
            urlError = "Please enter your Panel URL."
            valid = false
        } else {
            try {
                UrlNormalizer.normalize(rawUrl)
                urlError = null
            } catch (e: Exception) {
                urlError = e.message ?: "Invalid URL format."
                valid = false
            }
        }

        if (rawToken.isBlank()) {
            tokenError = "Please enter your Client API Key."
            valid = false
        } else if (rawToken.startsWith("ptla_")) {
            tokenError = "Application keys (ptla_) are not supported. Use a Client API key (ptlc_)."
            valid = false
        } else {
            tokenError = null
        }

        if (!valid) return null
        return Pair(UrlNormalizer.normalize(rawUrl), rawToken)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .testTag("setup_credentials_screen")
    ) {
        BackHandler(enabled = allPanels.isNotEmpty()) {
            viewModel.cancelAddingPanel()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Expressive Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (allPanels.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.cancelAddingPanel() },
                        modifier = Modifier.testTag("add_panel_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                ExpressiveHeader(
                    title = "Add Panel",
                    avatarUrl = userProfile?.photoUrl ?: currentUser?.photoUrl?.toString(),
                    displayName = userProfile?.displayName ?: currentUser?.displayName,
                    isSignedIn = currentUser != null,
                    onAvatarClick = { viewModel.openAccountSettings() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Setup Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 480.dp)
                    .testTag("setup_card"),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    BotanicalCornerIllustration(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(110.dp),
                        tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PteroPilotLogo(size = 96.dp)

                        Text(
                            text = "Connect Pterodactyl Panel",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = ExpressiveSerifFontFamily,
                                fontSize = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Panel Name / Label
                        SetupExpressiveField(
                            value = profileLabel,
                            onValueChange = { profileLabel = it },
                            label = "Panel Name (e.g. EU Cluster, Production VPS)",
                            leadingIcon = Icons.Default.BookmarkBorder,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            testTag = "input_profile_label"
                        )

                        // Panel URL
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SetupExpressiveField(
                                value = panelUrl,
                                onValueChange = {
                                    panelUrl = it
                                    urlError = null
                                },
                                label = stringResource(R.string.label_panel_url),
                                leadingIcon = Icons.Default.Link,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                testTag = "input_panel_url"
                            )
                            if (urlError != null) {
                                Text(
                                    text = urlError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                                )
                            }
                        }

                        // Client API Key
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SetupExpressiveField(
                                value = apiToken,
                                onValueChange = {
                                    apiToken = it
                                    tokenError = null
                                },
                                label = stringResource(R.string.label_api_token),
                                leadingIcon = Icons.Default.Key,
                                isPassword = !isTokenVisible,
                                trailingAction = {
                                    IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                        Icon(
                                            imageVector = if (isTokenVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                testTag = "input_api_token"
                            )
                            if (tokenError != null) {
                                Text(
                                    text = tokenError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.token_help_text),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (connectState is UiState.Error) {
                            ErrorBanner(message = connectState.message)
                        }

                        val isLoading = connectState is UiState.Loading
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val validated = validateInputs()
                                if (validated != null) {
                                    val (cleanUrl, cleanToken) = validated
                                    viewModel.addPanel(
                                        url = cleanUrl,
                                        token = cleanToken,
                                        label = profileLabel.ifBlank { null }
                                    )
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_save_and_connect")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_connecting), fontWeight = FontWeight.Bold)
                            } else {
                                Text(
                                    text = "Connect & Add Panel",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Connected Panels Overview
            if (allPanels.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .widthIn(max = 480.dp)
                        .clickable { showSavedSheet = true }
                        .testTag("saved_panels_quick_card"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "${allPanels.size} Connected Panel(s)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "All active simultaneously in server list",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "View",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSavedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSavedSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Connected Panels",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = ExpressiveSerifFontFamily,
                        fontSize = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))
                allPanels.forEach { conn ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .testTag("saved_profile_${conn.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = conn.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = conn.panelUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = { profileToDelete = conn },
                                modifier = Modifier.testTag("delete_profile_${conn.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete Profile",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    profileToDelete?.let { target ->
        ConfirmationDialog(
            show = true,
            title = "Remove ${target.label}?",
            message = "Are you sure you want to remove this panel? Its servers will be removed from the server list.",
            confirmButtonText = "Remove",
            isDestructive = true,
            onConfirm = {
                viewModel.deletePanel(target.id)
                profileToDelete = null
            },
            onDismiss = { profileToDelete = null }
        )
    }
}

@Composable
fun SetupExpressiveField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    trailingAction: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag)
                )
            }
            if (trailingAction != null) {
                trailingAction()
            }
        }
    }
}
