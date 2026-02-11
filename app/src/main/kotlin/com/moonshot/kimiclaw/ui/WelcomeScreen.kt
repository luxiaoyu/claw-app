package com.moonshot.kimiclaw.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonshot.kimiclaw.theme.lightBrandNormal
import com.moonshot.kimiclaw.theme.lightBubbleSurface
import com.moonshot.kimiclaw.theme.lightMainSurface
import com.moonshot.kimiclaw.theme.lightSuccess
import com.moonshot.kimiclaw.theme.lightSurface02
import com.moonshot.kimiclaw.theme.lightSurface06
import com.moonshot.kimiclaw.theme.lightTextCaption
import com.moonshot.kimiclaw.theme.lightTextPrimary
import com.moonshot.kimiclaw.theme.lightTextSecondary

/**
 * @desc   :
 * @author : luxiaoyu@moonshot.cn
 * @date   : 2026/2/11 22:24
 */

@Composable
fun WelcomeScreen(
    onNext: () -> Unit,
    onCheckUpgrade: () -> Unit
) {
    // Permission states
    var notificationEnabled by remember { mutableStateOf(false) }
    var backgroundEnabled by remember { mutableStateOf(false) }
    var batteryEnabled by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(lightMainSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Top bar with check upgrade button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                CheckUpgradeButton(onClick = onCheckUpgrade)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title with gradient-like effect using shadow
            Text(
                text = "Welcome to AndroidClaw",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = lightTextPrimary,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            Text(
                text = "AndroidClaw needs these permissions to run OpenClaw gateway reliably in background.",
                fontSize = 15.sp,
                color = lightTextSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Permission cards with refined styling
            PermissionCard(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                description = "Show gateway status and alerts",
                isEnabled = notificationEnabled,
                onToggle = { notificationEnabled = !notificationEnabled }
            )

            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                icon = Icons.Default.Refresh,
                title = "Background Running",
                description = "Keep gateway alive when app is in background",
                isEnabled = backgroundEnabled,
                onToggle = { backgroundEnabled = !backgroundEnabled }
            )

            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                icon = Icons.Default.BatteryFull,
                title = "Battery Optimization",
                description = "Set battery to unrestricted & allow background data",
                isEnabled = batteryEnabled,
                onToggle = { batteryEnabled = !batteryEnabled }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Next button with refined styling
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = lightBrandNormal.copy(alpha = 0.4f)
                    ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = lightBrandNormal
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Get Started",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun CheckUpgradeButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(lightSurface02)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = "Check Upgrade",
            fontSize = 13.sp,
            color = lightTextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color.Black.copy(alpha = 0.06f)
            )
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = lightBubbleSurface
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with refined background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(lightSurface06),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isEnabled) lightBrandNormal else lightTextCaption,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = lightTextPrimary,
                    letterSpacing = (-0.3).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = lightTextSecondary,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Status button
            PermissionStatusButton(isEnabled = isEnabled)
        }
    }
}

@Composable
fun PermissionStatusButton(isEnabled: Boolean) {
    if (isEnabled) {
        // Enabled state - show checkmark with green text
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(lightSuccess.copy(alpha = 0.1f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Enabled",
                tint = lightSuccess,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Enabled",
                fontSize = 13.sp,
                color = lightSuccess,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        // Disabled state - show allow button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(lightBrandNormal)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Allow",
                fontSize = 13.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
