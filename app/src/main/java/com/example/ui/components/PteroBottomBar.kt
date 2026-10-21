package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.ScreenDestination

/**
 * Modern floating pill-shaped bottom navigation bar.
 */
@Composable
fun PteroServerBottomBar(
    currentDestination: ScreenDestination,
    onNavigate: (ScreenDestination) -> Unit,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    displayName: String? = null,
    isSignedIn: Boolean = false,
    onAvatarClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .width(320.dp)
            .testTag("server_bottom_navigation")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("floating_nav_pill"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Dashboard
            val isDashboard = currentDestination == ScreenDestination.DASHBOARD
            FloatingNavItem(
                selected = isDashboard,
                onClick = { onNavigate(ScreenDestination.DASHBOARD) },
                icon = Icons.Filled.GridView,
                label = stringResource(R.string.nav_dashboard),
                testTag = "nav_tab_dashboard"
            )

            // 2. Console
            val isConsole = currentDestination == ScreenDestination.CONSOLE
            FloatingNavItem(
                selected = isConsole,
                onClick = { onNavigate(ScreenDestination.CONSOLE) },
                icon = Icons.Filled.Terminal,
                label = stringResource(R.string.nav_console),
                testTag = "nav_tab_console"
            )

            // 3. Files
            val isFiles = currentDestination == ScreenDestination.FILES
            FloatingNavItem(
                selected = isFiles,
                onClick = { onNavigate(ScreenDestination.FILES) },
                icon = Icons.Filled.Folder,
                label = stringResource(R.string.nav_files),
                testTag = "nav_tab_files"
            )

            // 4. Backups
            val isBackups = currentDestination == ScreenDestination.BACKUPS
            FloatingNavItem(
                selected = isBackups,
                onClick = { onNavigate(ScreenDestination.BACKUPS) },
                icon = Icons.Filled.CloudUpload,
                label = stringResource(R.string.nav_backups),
                testTag = "nav_tab_backups"
            )
        }
    }
}

@Composable
private fun RowScope.FloatingNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    testTag: String
) {
    val highlightColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        label = "pillHighlight"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        label = "iconColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        label = "textColor"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .testTag(testTag)
            .clip(RoundedCornerShape(percent = 50))
            .background(highlightColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 28.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                maxLines = 1,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
