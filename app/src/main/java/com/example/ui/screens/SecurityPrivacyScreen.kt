package com.example.ui.screens

import android.content.Context
import androidx.biometric.BiometricManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.components.AppBubbleHost
import com.example.ui.components.BubbleController
import com.example.ui.components.BubbleType
import com.example.ui.theme.ExpressiveSerifFontFamily

@Composable
fun SecurityPrivacyScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAppLockEnabled by viewModel.isAppLockEnabled.collectAsStateWithLifecycle()
    val isBiometricsEnabled by viewModel.isBiometricsEnabled.collectAsStateWithLifecycle()
    val lockTimeoutSeconds by viewModel.lockTimeoutSeconds.collectAsStateWithLifecycle()
    val isHideRecentApps by viewModel.isHideRecentApps.collectAsStateWithLifecycle()

    // Check device biometric support
    val canUseBiometrics = remember(context) {
        val bm = BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        canAuth == BiometricManager.BIOMETRIC_SUCCESS
    }

    var showCreatePasscodeDialog by remember { mutableStateOf(false) }
    var showDisableConfirmDialog by remember { mutableStateOf(false) }
    var showChangePasscodeDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("security_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Security & Privacy",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = ExpressiveSerifFontFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Section: App Lock
        Text(
            text = "App Lock",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Toggle App Lock
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "App Lock",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Require 6-digit passcode to open app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isAppLockEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                showCreatePasscodeDialog = true
                            } else {
                                showDisableConfirmDialog = true
                            }
                        },
                        modifier = Modifier.testTag("switch_app_lock")
                    )
                }

                // Biometrics switch (shown only if device supports it)
                if (canUseBiometrics && isAppLockEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Fingerprint / Face unlock",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Unlock quickly using biometrics",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isBiometricsEnabled,
                            onCheckedChange = { viewModel.setBiometricsEnabled(it) },
                            modifier = Modifier.testTag("switch_biometrics")
                        )
                    }
                }

                // Change Passcode Row
                if (isAppLockEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showChangePasscodeDialog = true }
                            .padding(vertical = 4.dp),
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
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Pin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Change Passcode",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Update your 6-digit lock passcode",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Lock timeout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showTimeoutDialog = true }
                            .padding(vertical = 4.dp),
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
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Lock after",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val timeoutLabel = when (lockTimeoutSeconds) {
                                    0 -> "Immediately"
                                    30 -> "30 seconds"
                                    60 -> "1 minute"
                                    300 -> "5 minutes"
                                    else -> "$lockTimeoutSeconds seconds"
                                }
                                Text(
                                    text = timeoutLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section: Screen & Privacy Protection
        Text(
            text = "Screen Privacy",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Hide in Recent Apps",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Block screenshots & preview in task switcher",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isHideRecentApps,
                        onCheckedChange = { viewModel.setHideRecentApps(it) },
                        modifier = Modifier.testTag("switch_hide_recent_apps")
                    )
                }
            }
        }
    }

    // Dialog: Create Passcode
    if (showCreatePasscodeDialog) {
        PasscodeCreationDialog(
            onDismiss = { showCreatePasscodeDialog = false },
            onPasscodeCreated = { code ->
                viewModel.setNewPasscode(code)
                showCreatePasscodeDialog = false
                BubbleController.show("App lock enabled with 6-digit passcode", BubbleType.SUCCESS)
            }
        )
    }

    // Dialog: Disable Passcode Confirmation
    if (showDisableConfirmDialog) {
        var enteredCurrent by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showDisableConfirmDialog = false },
            title = { Text("Disable App Lock", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter your current 6-digit passcode to turn off app lock.")
                    OutlinedTextField(
                        value = enteredCurrent,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) enteredCurrent = it },
                        label = { Text("Current Passcode") },
                        isError = errorMessage != null,
                        supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (enteredCurrent.length == 6) {
                            viewModel.verifyPasscode(enteredCurrent) { success, _ ->
                                if (success) {
                                    viewModel.setAppLockEnabled(false)
                                    showDisableConfirmDialog = false
                                    BubbleController.show("App lock turned off", BubbleType.INFO)
                                } else {
                                    errorMessage = "Incorrect passcode"
                                }
                            }
                        }
                    },
                    enabled = enteredCurrent.length == 6
                ) {
                    Text("Disable", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Change Passcode
    if (showChangePasscodeDialog) {
        ChangePasscodeDialog(
            viewModel = viewModel,
            onDismiss = { showChangePasscodeDialog = false },
            onSuccess = {
                showChangePasscodeDialog = false
                BubbleController.show("Passcode updated successfully", BubbleType.SUCCESS)
            }
        )
    }

    // Dialog: Lock Timeout Selector
    if (showTimeoutDialog) {
        val options = listOf(
            0 to "Immediately",
            30 to "30 seconds",
            60 to "1 minute",
            300 to "5 minutes"
        )
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("Lock after", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (seconds, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setLockTimeoutSeconds(seconds)
                                    showTimeoutDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = lockTimeoutSeconds == seconds,
                                onClick = {
                                    viewModel.setLockTimeoutSeconds(seconds)
                                    showTimeoutDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun PasscodeCreationDialog(
    onDismiss: () -> Unit,
    onPasscodeCreated: (String) -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // 1: Enter, 2: Confirm
    var firstCode by remember { mutableStateOf("") }
    var secondCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (step == 1) "Create Passcode" else "Confirm Passcode", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (step == 1) "Enter a 6-digit passcode to protect Pterodroid."
                    else "Enter your 6-digit passcode again to confirm."
                )

                OutlinedTextField(
                    value = if (step == 1) firstCode else secondCode,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(6)
                        if (step == 1) {
                            firstCode = digits
                        } else {
                            secondCode = digits
                        }
                        errorMessage = null
                    },
                    label = { Text("6-Digit Passcode") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (step == 1) {
                        if (firstCode.length == 6) {
                            step = 2
                        } else {
                            errorMessage = "Passcode must be 6 digits"
                        }
                    } else {
                        if (secondCode == firstCode) {
                            onPasscodeCreated(firstCode)
                        } else {
                            errorMessage = "Passcodes do not match. Try again."
                            secondCode = ""
                        }
                    }
                },
                enabled = if (step == 1) firstCode.length == 6 else secondCode.length == 6
            ) {
                Text(if (step == 1) "Next" else "Save Passcode", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ChangePasscodeDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // 1: Current, 2: New, 3: Confirm
    var currentCode by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var confirmCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (step) {
                    1 -> "Enter Current Passcode"
                    2 -> "Enter New Passcode"
                    else -> "Confirm New Passcode"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when (step) {
                        1 -> "Enter your current 6-digit passcode."
                        2 -> "Choose a new 6-digit passcode."
                        else -> "Re-enter your new 6-digit passcode."
                    }
                )

                OutlinedTextField(
                    value = when (step) {
                        1 -> currentCode
                        2 -> newCode
                        else -> confirmCode
                    },
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(6)
                        when (step) {
                            1 -> currentCode = digits
                            2 -> newCode = digits
                            else -> confirmCode = digits
                        }
                        errorMessage = null
                    },
                    label = { Text("6-Digit Passcode") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (step) {
                        1 -> {
                            if (currentCode.length == 6) {
                                viewModel.verifyPasscode(currentCode) { ok, _ ->
                                    if (ok) {
                                        step = 2
                                    } else {
                                        errorMessage = "Incorrect current passcode"
                                    }
                                }
                            }
                        }
                        2 -> {
                            if (newCode.length == 6) {
                                step = 3
                            } else {
                                errorMessage = "Passcode must be 6 digits"
                            }
                        }
                        3 -> {
                            if (confirmCode == newCode) {
                                viewModel.setNewPasscode(newCode)
                                onSuccess()
                            } else {
                                errorMessage = "Passcodes do not match"
                                confirmCode = ""
                            }
                        }
                    }
                },
                enabled = when (step) {
                    1 -> currentCode.length == 6
                    2 -> newCode.length == 6
                    else -> confirmCode.length == 6
                }
            ) {
                Text(if (step < 3) "Next" else "Update Passcode", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
