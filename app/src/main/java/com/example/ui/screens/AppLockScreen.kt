package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.PteroPilotLogo
import com.example.ui.theme.ExpressiveSerifFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AppLockScreen(
    viewModel: MainViewModel,
    isBiometricsEnabled: Boolean,
    lockoutUntilMillis: Long,
    onRequestBiometrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var enteredPasscode by remember { mutableStateOf("") }
    var showForgotDialog by remember { mutableStateOf(false) }

    // Lockout countdown timer
    val currentTime = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockoutUntilMillis) {
        while (System.currentTimeMillis() < lockoutUntilMillis) {
            currentTime.value = System.currentTimeMillis()
            delay(500L)
        }
        currentTime.value = System.currentTimeMillis()
    }

    val isLockedOut = lockoutUntilMillis > currentTime.value
    val secondsRemaining = if (isLockedOut) {
        ((lockoutUntilMillis - currentTime.value) / 1000L).coerceAtLeast(1L)
    } else 0L

    // Shake animation on error
    val shakeOffset = remember { Animatable(0f) }

    fun triggerShake() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        coroutineScope.launch {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    (-24f) at 50 using FastOutLinearInEasing
                    24f at 100 using FastOutLinearInEasing
                    (-18f) at 160 using FastOutLinearInEasing
                    18f at 220 using FastOutLinearInEasing
                    (-10f) at 290 using FastOutLinearInEasing
                    10f at 350 using FastOutLinearInEasing
                    0f at 400
                }
            )
        }
    }

    fun handleDigitPress(digit: String) {
        if (isLockedOut) return
        if (enteredPasscode.length < 6) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val newCode = enteredPasscode + digit
            enteredPasscode = newCode

            if (newCode.length == 6) {
                viewModel.verifyPasscode(newCode) { success, _ ->
                    if (!success) {
                        enteredPasscode = ""
                        triggerShake()
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (enteredPasscode.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            enteredPasscode = enteredPasscode.dropLast(1)
        }
    }

    // Auto-prompt biometrics once on screen appearance if enabled
    LaunchedEffect(isBiometricsEnabled) {
        if (isBiometricsEnabled && !isLockedOut) {
            onRequestBiometrics()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("app_lock_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Header & Lock status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PteroPilotLogo(size = 64.dp)

                Text(
                    text = "Pterodroid Locked",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = ExpressiveSerifFontFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = if (isLockedOut) {
                        "Too many failed attempts"
                    } else {
                        "Enter your 6-digit passcode"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isLockedOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isLockedOut) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Try again in ${secondsRemaining}s",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // 6-digit Pin Dots with shake animation
            Row(
                modifier = Modifier
                    .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 6) {
                    val isFilled = i < enteredPasscode.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            // Number Pad (1-9, Biometrics, 0, Backspace)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val digits = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                digits.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { digit ->
                            KeypadButton(
                                text = digit,
                                enabled = !isLockedOut,
                                onClick = { handleDigitPress(digit) }
                            )
                        }
                    }
                }

                // Bottom row: Biometrics / Empty, 0, Backspace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBiometricsEnabled) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !isLockedOut) { onRequestBiometrics() }
                                .testTag("btn_biometric_unlock"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = "Fingerprint / Face unlock",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(72.dp))
                    }

                    KeypadButton(
                        text = "0",
                        enabled = !isLockedOut,
                        onClick = { handleDigitPress("0") }
                    )

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable(enabled = enteredPasscode.isNotEmpty()) { handleBackspace() }
                            .testTag("btn_keypad_backspace"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = if (enteredPasscode.isNotEmpty()) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Forgot Passcode Button
            TextButton(
                onClick = { showForgotDialog = true },
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .testTag("btn_forgot_passcode")
            ) {
                Text(
                    text = "Forgot passcode?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // Confirmation dialog for Forgot Passcode
    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = {
                Text("Reset Passcode & Sign Out?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("For security, resetting your passcode will wipe all local data and credentials, and sign you out of Pterodroid. You can sign back in with your Google account.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForgotDialog = false
                        viewModel.forgotPasscodeAndSignOut()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset & Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun KeypadButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .size(72.dp)
            .testTag("keypad_digit_$text")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}
