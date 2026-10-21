package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.ExpressiveSerifFontFamily

@Composable
fun UserDisplayPicture(
    avatarUrl: String?,
    displayName: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    size: Dp = 44.dp,
    showStatusDot: Boolean = true,
    isSignedIn: Boolean = false
) {
    Box(
        modifier = modifier
            .size(48.dp) // Accessibility touch target
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(CircleShape)
                        .clickable { onClick() }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .border(
                    width = 1.5.dp,
                    color = if (isSignedIn) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "User Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                )
            } else if (!displayName.isNullOrBlank()) {
                val initial = displayName.trim().take(1).uppercase()
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.42f).sp
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User Account",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }

        // Active Cloud Sync Indicator Dot
        if (showStatusDot && isSignedIn) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun ExpressiveHeader(
    title: String,
    modifier: Modifier = Modifier,
    userBadgeText: String? = null,
    avatarUrl: String? = null,
    displayName: String? = null,
    isSignedIn: Boolean = false,
    onAvatarClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = ExpressiveSerifFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 34.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("expressive_header_title")
            )
            if (!userBadgeText.isNullOrBlank()) {
                Text(
                    text = userBadgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        UserDisplayPicture(
            avatarUrl = avatarUrl,
            displayName = displayName,
            isSignedIn = isSignedIn,
            onClick = onAvatarClick,
            modifier = Modifier.testTag("expressive_avatar_button")
        )
    }
}

@Composable
fun ExpressiveTipBanner(
    message: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Lightbulb
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .testTag("expressive_tip_banner"),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun BotanicalCornerIllustration(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    tintColor: Color = Color(0xFF6B8E6D)
) {
    Canvas(
        modifier = modifier.size(size)
    ) {
        val w = this.size.width
        val h = this.size.height

        val leaf1 = Path().apply {
            moveTo(w * 0.42f, h * 0.32f)
            cubicTo(w * 0.15f, h * 0.28f, w * 0.22f, h * 0.52f, w * 0.48f, h * 0.48f)
            cubicTo(w * 0.55f, h * 0.42f, w * 0.48f, h * 0.34f, w * 0.42f, h * 0.32f)
            close()
        }
        drawPath(leaf1, color = tintColor.copy(alpha = 0.70f), style = Fill)

        val leaf2 = Path().apply {
            moveTo(w * 0.58f, h * 0.38f)
            cubicTo(w * 0.52f, h * 0.12f, w * 0.82f, h * 0.16f, w * 0.72f, h * 0.42f)
            cubicTo(w * 0.68f, h * 0.48f, w * 0.60f, h * 0.42f, w * 0.58f, h * 0.38f)
            close()
        }
        drawPath(leaf2, color = tintColor.copy(alpha = 0.85f), style = Fill)

        val leaf3 = Path().apply {
            moveTo(w * 0.52f, h * 0.58f)
            cubicTo(w * 0.25f, h * 0.52f, w * 0.28f, h * 0.76f, w * 0.56f, h * 0.72f)
            cubicTo(w * 0.62f, h * 0.68f, w * 0.56f, h * 0.60f, w * 0.52f, h * 0.58f)
            close()
        }
        drawPath(leaf3, color = tintColor.copy(alpha = 0.65f), style = Fill)

        val leaf4 = Path().apply {
            moveTo(w * 0.68f, h * 0.54f)
            cubicTo(w * 0.72f, h * 0.32f, w * 1.05f, h * 0.42f, w * 0.92f, h * 0.78f)
            cubicTo(w * 0.85f, h * 0.88f, w * 0.72f, h * 0.65f, w * 0.68f, h * 0.54f)
            close()
        }
        drawPath(leaf4, color = tintColor.copy(alpha = 0.75f), style = Fill)
    }
}

@Composable
fun ExpressiveCategoryCard(
    title: String,
    modifier: Modifier = Modifier,
    tintColor: Color = Color(0xFF6B8E6D),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = ExpressiveSerifFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                BotanicalCornerIllustration(
                    modifier = Modifier.size(54.dp),
                    tintColor = tintColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
