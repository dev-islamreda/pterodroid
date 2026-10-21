package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class BubbleType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO,
    OFFLINE
}

data class BubbleMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val type: BubbleType = BubbleType.INFO,
    val durationMs: Long = if (type == BubbleType.ERROR) 5000L else 3500L,
    val isPersistent: Boolean = (type == BubbleType.OFFLINE)
)

object BubbleController {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var dismissJob: Job? = null
    private var lastMessageTime: Long = 0L
    private var lastMessageText: String = ""

    private val _currentBubble = MutableStateFlow<BubbleMessage?>(null)
    val currentBubble: StateFlow<BubbleMessage?> = _currentBubble.asStateFlow()

    private val queue = ArrayDeque<BubbleMessage>()
    private var isOfflineActive: Boolean = false

    fun isOffline(): Boolean = isOfflineActive

    fun setOfflineState(offline: Boolean) {
        if (offline == isOfflineActive) return
        isOfflineActive = offline
        if (offline) {
            dismissJob?.cancel()
            queue.clear()
            _currentBubble.value = BubbleMessage(
                text = "You're offline. Check your internet connection.",
                type = BubbleType.OFFLINE,
                durationMs = 0L,
                isPersistent = true
            )
        } else {
            // Turned back online
            dismissJob?.cancel()
            _currentBubble.value = BubbleMessage(
                text = "Back online",
                type = BubbleType.SUCCESS,
                durationMs = 3500L,
                isPersistent = false
            )
            scheduleAutoDismiss(3500L)
        }
    }

    fun show(
        message: String,
        type: BubbleType = BubbleType.INFO,
        durationMs: Long = if (type == BubbleType.ERROR) 5000L else 3500L
    ) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return

        // While offline, suppress extra bubbles for network errors from API calls
        if (isOfflineActive) {
            val lower = trimmed.lowercase()
            if (lower.contains("offline") ||
                lower.contains("unreachable") ||
                lower.contains("connect") ||
                lower.contains("timeout") ||
                lower.contains("network")
            ) {
                return
            }
        }

        val now = System.currentTimeMillis()
        if (trimmed == lastMessageText && (now - lastMessageTime < 2000L)) {
            // Ignore identical message within 2 seconds
            return
        }
        lastMessageText = trimmed
        lastMessageTime = now

        val bubble = BubbleMessage(
            text = trimmed,
            type = type,
            durationMs = durationMs,
            isPersistent = false
        )

        dismissJob?.cancel()
        if (queue.size >= 3) {
            queue.removeFirst()
        }
        _currentBubble.value = bubble
        scheduleAutoDismiss(durationMs)
    }

    fun dismiss() {
        dismissJob?.cancel()
        if (isOfflineActive) {
            _currentBubble.value = null
        } else if (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            _currentBubble.value = next
            scheduleAutoDismiss(next.durationMs)
        } else {
            _currentBubble.value = null
        }
    }

    private fun scheduleAutoDismiss(durationMs: Long) {
        if (durationMs <= 0L) return
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(durationMs)
            if (_currentBubble.value?.isPersistent != true) {
                dismiss()
            }
        }
    }
}

@Composable
fun AppBubbleHost(
    modifier: Modifier = Modifier
) {
    val bubble by BubbleController.currentBubble.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val dragOffsetY = remember { Animatable(0f) }
    var isSwipingOut by remember { mutableStateOf(false) }
    var lastBubble by remember { mutableStateOf<BubbleMessage?>(null) }

    LaunchedEffect(bubble) {
        if (bubble != null) {
            lastBubble = bubble
            dragOffsetY.snapTo(0f)
            isSwipingOut = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = bubble != null && !isSwipingOut,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 280)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(durationMillis = 280, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 220))
        ) {
            val current = bubble ?: lastBubble
            if (current != null) {
                val (icon, containerColor, contentColor, iconColor) = when (current.type) {
                    BubbleType.SUCCESS -> Quad(
                        Icons.Default.CheckCircle,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.primary
                    )
                    BubbleType.ERROR -> Quad(
                        Icons.Default.Error,
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                        MaterialTheme.colorScheme.error
                    )
                    BubbleType.WARNING -> Quad(
                        Icons.Default.Warning,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        MaterialTheme.colorScheme.secondary
                    )
                    BubbleType.INFO -> Quad(
                        Icons.Default.Info,
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.onSurface,
                        MaterialTheme.colorScheme.primary
                    )
                    BubbleType.OFFLINE -> Quad(
                        Icons.Default.WifiOff,
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.onErrorContainer,
                        MaterialTheme.colorScheme.error
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                        .alpha(((1f + (dragOffsetY.value / 250f)).coerceIn(0f, 1f)))
                        .pointerInput(current.id) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    if (dragAmount < 0 || dragOffsetY.value < 0) {
                                        coroutineScope.launch {
                                            dragOffsetY.snapTo((dragOffsetY.value + dragAmount).coerceAtMost(0f))
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (dragOffsetY.value < -40f) {
                                        isSwipingOut = true
                                        coroutineScope.launch {
                                            dragOffsetY.animateTo(
                                                targetValue = -350f,
                                                animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)
                                            )
                                            BubbleController.dismiss()
                                            dragOffsetY.snapTo(0f)
                                            isSwipingOut = false
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            dragOffsetY.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = current.text
                        }
                        .testTag("app_bubble_notification"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
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
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(iconColor.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = current.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    lineHeight = 18.sp
                                ),
                                color = contentColor
                            )
                        }
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    dragOffsetY.animateTo(
                                        targetValue = -250f,
                                        animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing)
                                    )
                                    BubbleController.dismiss()
                                    dragOffsetY.snapTo(0f)
                                }
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("bubble_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = contentColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
