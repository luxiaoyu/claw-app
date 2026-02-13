package com.moonshot.kimiclaw.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moonshot.kimiclaw.theme.lightMainSurface
import com.moonshot.kimiclaw.theme.lightSuccess
import com.moonshot.kimiclaw.theme.lightSurface02
import com.moonshot.kimiclaw.theme.lightTextPrimary
import com.moonshot.kimiclaw.theme.lightTextSecondary
import com.moonshot.kimiclaw.viewmodel.DashboardViewModel

/**
 * Logcat 日志页面
 * 显示本应用的 logcat 日志
 */
@Composable
fun LogcatScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit = {}
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // 初始化
    DisposableEffect(Unit) {
        viewModel.initialize()
        onDispose {
            viewModel.setAutoRefresh(false)
        }
    }

    // 当日志变化时自动滚动到底部
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(lightMainSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(lightSurface02)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = lightTextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Title
                Text(
                    text = "Logcat",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = lightTextPrimary
                )

                // Action Buttons Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Clear Button
                    IconButton(
                        onClick = { viewModel.clearLogs() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Logs",
                            tint = lightTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Copy Logs Button
                    IconButton(
                        onClick = { viewModel.copyLogsToClipboard() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Logs",
                            tint = lightTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Log Console
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LogcatConsole(
                    logs = logs,
                    scrollState = scrollState,
                    lineCount = logs.size,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "● Auto Refresh",
                    fontSize = 12.sp,
                    color = lightSuccess
                )

                Text(
                    text = "${logs.size} lines",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Logcat 控制台组件（参考 InstallScreen 的 LogConsole）
 */
@Composable
private fun LogcatConsole(
    logs: List<String>,
    scrollState: androidx.compose.foundation.ScrollState,
    lineCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color.Black.copy(alpha = 0.06f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E) // 深色终端背景
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Console Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Application Logs",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFAAAAAA)
                )

                Text(
                    text = "$lineCount lines",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Log Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0D0D0D))
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            text = "No logs available...",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF888888),
                            lineHeight = 16.sp
                        )
                    } else {
                        logs.forEach { line ->
                            Text(
                                text = line,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = getLogColor(line),
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(vertical = 0.5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 根据日志内容返回对应颜色
 * Android logcat threadtime 格式: "02-13 14:30:45.123  1234  5678 I TagName: Message"
 * 日志级别是单独的一个字母 (E/W/I/D/V)，前后有空格
 */
private fun getLogColor(line: String): Color {
    // 匹配 logcat threadtime 格式中的日志级别
    // 格式: 时间 PID TID 级别 Tag: Message
    // 例如: "02-13 14:30:45.123  1234  5678 I TagName: Message"
    // 格式: "02-13 14:30:45.123 I TagName: Message" (PID/TID 已被移除)
    val logLevelPattern = Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+\s+([EWIDVF])\s+""")
    val matchResult = logLevelPattern.find(line)
    val logLevel = matchResult?.groupValues?.getOrNull(1)

    return when (logLevel) {
        "E" -> Color(0xFFFF5252) // Error - Bright Red
        "W" -> Color(0xFFFFB74D) // Warn - Bright Orange
        "I" -> Color(0xFF64B5F6) // Info - Bright Blue
        "D" -> Color(0xFF81C784) // Debug - Bright Green
        "V" -> Color(0xFFBDBDBD) // Verbose - Light Gray
        "F" -> Color(0xFFFF1744) // Fatal - Deep Red
        else -> {
            // 备用检测：通过其他关键词判断
            when {
                line.contains(" ERROR") || line.contains("[ERROR]") || line.contains("Fatal") -> Color(0xFFFF5252)
                line.contains(" WARN") || line.contains("[WARN]") -> Color(0xFFFFB74D)
                line.contains(" INFO") || line.contains("[INFO]") -> Color(0xFF64B5F6)
                line.contains(" DEBUG") || line.contains("[DEBUG]") -> Color(0xFF81C784)
                line.contains(" VERBOSE") || line.contains("[VERBOSE]") -> Color(0xFFBDBDBD)
                line.contains("SUCCESS") || line.contains("COMPLETE") || line.contains("DONE") -> Color(0xFF69F0AE)
                line.contains("STEP") || line.contains("START") -> Color(0xFF448AFF)
                else -> Color(0xFFFFFFFF) // White
            }
        }
    }
}
