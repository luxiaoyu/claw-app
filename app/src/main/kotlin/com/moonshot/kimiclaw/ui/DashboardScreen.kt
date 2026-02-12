package com.moonshot.kimiclaw.ui

import com.termux.R
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonshot.kimiclaw.theme.lightBrandNormal
import com.moonshot.kimiclaw.theme.lightBubbleSurface
import com.moonshot.kimiclaw.theme.lightError
import com.moonshot.kimiclaw.theme.lightMainSurface
import com.moonshot.kimiclaw.theme.lightSuccess
import com.moonshot.kimiclaw.theme.lightSurface02
import com.moonshot.kimiclaw.theme.lightSurface06
import com.moonshot.kimiclaw.theme.lightTextCaption
import com.moonshot.kimiclaw.theme.lightTextPrimary
import com.moonshot.kimiclaw.theme.lightTextSecondary

/**
 * Dashboard 页面
 * 显示 OpenClaw Gateway 状态、Channels 连接状态和调试信息
 */
@Composable
fun DashboardScreen(
    gatewayStatus: GatewayStatus = GatewayStatus.STOPPED,
    uptime: String = "--",
    channelsStatus: ChannelsStatus = ChannelsStatus(),
    sshAccess: SshAccess = SshAccess(),
    isCheckingUpgrade: Boolean = false,
    isStartingGateway: Boolean = false,
    isStoppingGateway: Boolean = false,
    onCheckUpgrade: () -> Unit = {},
    onStartGateway: () -> Unit = {},
    onStopGateway: () -> Unit = {},
    onViewLogs: () -> Unit = {},
    onOpenTerminal: () -> Unit = {},
    onReportIssue: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(lightMainSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top bar with icon, title and check upgrade button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon and Title
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App Icon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(lightBrandNormal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.mipmap.ic_launcher),
                            contentDescription = "KimiClaw",
                            tint = lightBrandNormal,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "KimiClaw",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = lightTextPrimary,
                        letterSpacing = (-0.3).sp
                    )
                }

                // Check Upgrade Button
                ActionButton(
                    text = "Check Upgrade",
                    icon = if (isCheckingUpgrade) null else Icons.Default.Refresh,
                    isLoading = isCheckingUpgrade,
                    onClick = onCheckUpgrade,
                    containerColor = lightSurface02,
                    contentColor = lightTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // OpenClaw Gateway Card
            GatewayCard(
                status = gatewayStatus,
                uptime = uptime,
                isStarting = isStartingGateway,
                isStopping = isStoppingGateway,
                onStart = onStartGateway,
                onStop = onStopGateway
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Channels Card
            ChannelsCard(channelsStatus = channelsStatus)

            Spacer(modifier = Modifier.height(16.dp))

            // Debug Card
            DebugCard(
                sshAccess = sshAccess,
                onViewLogs = onViewLogs,
                onOpenTerminal = onOpenTerminal,
                onReportIssue = onReportIssue
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * OpenClaw Gateway 状态卡片
 */
@Composable
private fun GatewayCard(
    status: GatewayStatus,
    uptime: String,
    isStarting: Boolean,
    isStopping: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    DashboardCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header with status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gateway Icon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(lightSurface06),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Gateway",
                            tint = lightBrandNormal,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "OpenClaw Gateway",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = lightTextPrimary,
                            letterSpacing = (-0.2).sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        StatusIndicator(status = status)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Uptime info
            InfoRow(label = "Uptime", value = uptime)

            Spacer(modifier = Modifier.height(16.dp))

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    text = "Start",
                    icon = Icons.Default.PlayArrow,
                    isLoading = isStarting,
                    enabled = status != GatewayStatus.RUNNING && !isStarting && !isStopping,
                    onClick = onStart,
                    containerColor = lightSuccess,
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f)
                )

                ActionButton(
                    text = "Stop",
                    icon = Icons.Default.Stop,
                    isLoading = isStopping,
                    enabled = status == GatewayStatus.RUNNING && !isStarting && !isStopping,
                    onClick = onStop,
                    containerColor = lightError,
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Channels 状态卡片
 */
@Composable
private fun ChannelsCard(channelsStatus: ChannelsStatus) {
    DashboardCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Text(
                text = "Channels",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = lightTextPrimary,
                letterSpacing = (-0.2).sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Channel items
            ChannelItem(
                name = "Telegram",
                isConnected = channelsStatus.telegramConnected
            )

            Spacer(modifier = Modifier.height(12.dp))

            ChannelItem(
                name = "Discord",
                isConnected = channelsStatus.discordConnected
            )

            Spacer(modifier = Modifier.height(12.dp))

            ChannelItem(
                name = "Feishu",
                isConnected = channelsStatus.feishuConnected
            )
        }
    }
}

/**
 * 单个 Channel 状态项
 */
@Composable
private fun ChannelItem(name: String, isConnected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (name) {
                    "Telegram" -> Icons.Outlined.FlightTakeoff
//                    "Discord" -> Icons.Outlined.Discord
                    else -> Icons.Outlined.Notifications
                },
                contentDescription = name,
                tint = lightTextCaption,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = name,
                fontSize = 15.sp,
                color = lightTextPrimary,
                fontWeight = FontWeight.Medium
            )
        }

        ConnectionStatus(isConnected = isConnected)
    }
}

/**
 * Debug 卡片
 */
@Composable
private fun DebugCard(
    sshAccess: SshAccess,
    onViewLogs: () -> Unit,
    onOpenTerminal: () -> Unit,
    onReportIssue: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    DashboardCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Text(
                text = "Debug",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = lightTextPrimary,
                letterSpacing = (-0.2).sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // View Logs Button
            ActionButton(
                text = "查看 KimiClaw 日志",
                icon = Icons.Default.BugReport,
                onClick = onViewLogs,
                containerColor = lightSurface06,
                contentColor = lightTextPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // SSH Access Section
            Text(
                text = "SSH Access",
                fontSize = 14.sp,
                color = lightTextSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // SSH Info Card (clickable to copy)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(lightSurface06)
                    .clickable {
                        val sshText = "ssh -p ${sshAccess.port} ${sshAccess.ip}\nPassword: ${sshAccess.password}"
                        clipboardManager.setText(AnnotatedString(sshText))
                    }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ssh -p ${sshAccess.port} ${sshAccess.ip}",
                            fontSize = 14.sp,
                            color = lightTextPrimary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Password: ${sshAccess.password}",
                            fontSize = 14.sp,
                            color = lightTextSecondary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = lightTextCaption,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    text = "Open Terminal",
                    icon = Icons.Default.Computer,
                    onClick = onOpenTerminal,
                    containerColor = lightSurface06,
                    contentColor = lightTextPrimary,
                    modifier = Modifier.weight(1f)
                )

                ActionButton(
                    text = "一键上报问题",
                    icon = Icons.Default.BugReport,
                    onClick = onReportIssue,
                    containerColor = lightBrandNormal.copy(alpha = 0.1f),
                    contentColor = lightBrandNormal,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 通用 Dashboard Card 样式
 */
@Composable
private fun DashboardCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color.Black.copy(alpha = 0.06f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = lightBubbleSurface
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        content()
    }
}

/**
 * 通用 Action Button
 */
@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.5f))
            .clickable(enabled = enabled && !isLoading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        text.contains("Upgrade") -> "Checking..."
                        text.contains("Start") -> "Starting..."
                        text.contains("Stop") -> "Stopping..."
                        else -> "Loading..."
                    },
                    fontSize = 14.sp,
                    color = contentColor,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    fontSize = 14.sp,
                    color = contentColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 状态指示器
 */
@Composable
private fun StatusIndicator(status: GatewayStatus) {
    val (icon, text, color) = when (status) {
        GatewayStatus.RUNNING -> Triple(
            Icons.Default.CheckCircle,
            "Running",
            lightSuccess
        )

        GatewayStatus.STOPPED -> Triple(
            Icons.Default.Error,
            "Stopped",
            lightTextCaption
        )

        GatewayStatus.ERROR -> Triple(
            Icons.Default.Error,
            "Error",
            lightError
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 连接状态指示器
 */
@Composable
private fun ConnectionStatus(isConnected: Boolean) {
    val text = if (isConnected) "Connected" else "Disconnected"
    val color = if (isConnected) lightSuccess else lightTextCaption

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 信息行
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = lightTextSecondary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = lightTextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

// ==================== Data Classes ====================

enum class GatewayStatus {
    RUNNING,
    STOPPED,
    ERROR
}

data class ChannelsStatus(
    val telegramConnected: Boolean = false,
    val discordConnected: Boolean = false,
    val feishuConnected: Boolean = false
)

data class SshAccess(
    val ip: String = "127.0.0.1",
    val port: Int = 8022,
    val password: String = "kimiclaw"
)
