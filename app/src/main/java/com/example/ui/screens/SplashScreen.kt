package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.components.BotanicalCornerIllustration
import com.example.ui.components.PteroPilotLogo
import com.example.ui.theme.ExpressiveSerifFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    viewModel: MainViewModel,
    appLanguage: String,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val splashState by viewModel.splashState.collectAsStateWithLifecycle()

    val logoScale = remember { Animatable(0.3f) }
    val logoAlpha = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    val contentSlide = remember { Animatable(20f) }

    val animatedProgress by animateFloatAsState(
        targetValue = splashState.progress.coerceIn(0f, 1f),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "splash_progress"
    )

    LaunchedEffect(Unit) {
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(
                1f,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = 280f)
            )
        }
        delay(200)
        launch {
            contentAlpha.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
        }
        launch {
            contentSlide.animateTo(0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f))
        }
        // Start initialization process
        viewModel.initializeApp(isOnline = isOnline)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("splash_screen_container"),
        contentAlignment = Alignment.Center
    ) {
        BotanicalCornerIllustration(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(180.dp),
            tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
        BotanicalCornerIllustration(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(140.dp),
            tintColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .scale(logoScale.value.coerceAtLeast(0.01f))
                        .alpha(logoAlpha.value.coerceIn(0f, 1f))
                        .testTag("splash_logo"),
                    contentAlignment = Alignment.Center
                ) {
                    PteroPilotLogo(size = 120.dp, animated = true)
                }

                Spacer(modifier = Modifier.height(28.dp))

                Column(
                    modifier = Modifier
                        .alpha(contentAlpha.value.coerceIn(0f, 1f))
                        .offset(y = contentSlide.value.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pterodroid",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = ExpressiveSerifFontFamily,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("splash_brand_title")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (appLanguage == "ar") {
                            "عميل خادم الألعاب التعبيري بتقنية Material 3"
                        } else {
                            "Material 3 Expressive Game Server Client"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(contentAlpha.value)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (splashState.isFailed) {
                    Text(
                        text = splashState.errorMessage ?: "Connection check failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.retryInitialization(isOnline) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("splash_retry_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                        OutlinedButton(
                            onClick = { viewModel.continueOffline() },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("splash_continue_offline_button")
                        ) {
                            Icon(Icons.Default.WifiOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Continue offline")
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }

                    Text(
                        text = splashState.statusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("splash_status_message")
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = if (isOnline) "v1.0 • Cloud Sync" else "v1.0 • Offline Mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
