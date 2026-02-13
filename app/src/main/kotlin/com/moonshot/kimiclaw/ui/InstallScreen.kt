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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonshot.kimiclaw.ui.theme.lightBrandNormal
import com.moonshot.kimiclaw.ui.theme.lightBubbleSurface
import com.moonshot.kimiclaw.ui.theme.lightError
import com.moonshot.kimiclaw.ui.theme.lightMainSurface
import com.moonshot.kimiclaw.ui.theme.lightSuccess
import com.moonshot.kimiclaw.ui.theme.lightSurface06
import com.moonshot.kimiclaw.ui.theme.lightTextCaption
import com.moonshot.kimiclaw.ui.theme.lightTextPrimary
import com.moonshot.kimiclaw.ui.theme.lightTextSecondary

/**
 * 安装状态
 */
sealed class InstallUiState {
    data object Installing : InstallUiState()
    data object Success : InstallUiState()
    data class Error(val message: String) : InstallUiState()
}

/**
 * InstallScreen - 显示安装进度和日志
 */
@Composable
fun InstallScreen(
    logs: List<String>,
    installState: InstallUiState,
    onRetry: () -> Unit,
    onContinue: () -> Unit
) {
    val scrollState = rememberScrollState()

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
            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = "Installing OpenClaw",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = lightTextPrimary,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            Text(
                text = when (installState) {
                    is InstallUiState.Installing -> "Setting up your environment..."
                    is InstallUiState.Success -> "Installation completed successfully!"
                    is InstallUiState.Error -> "Installation failed. Please check the logs below."
                },
                fontSize = 15.sp,
                color = lightTextSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Status Card
            InstallStatusCard(installState = installState)

            Spacer(modifier = Modifier.height(16.dp))

            // Log Console
            LogConsole(
                logs = logs,
                scrollState = scrollState,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            when (installState) {
                is InstallUiState.Installing -> {
                    // 安装中，显示不可点击的进度按钮
                    InstallingButton()
                }
                is InstallUiState.Success -> {
                    // 成功，显示 Continue 按钮
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor = lightBrandNormal.copy(alpha = 0.4f)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = lightSuccess
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Continue",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                is InstallUiState.Error -> {
                    // 失败，显示 Retry 按钮
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor = lightError.copy(alpha = 0.4f)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = lightError
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Retry",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InstallStatusCard(installState: InstallUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color.Black.copy(alpha = 0.06f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = when (installState) {
                is InstallUiState.Installing -> lightBubbleSurface
                is InstallUiState.Success -> lightSuccess.copy(alpha = 0.1f)
                is InstallUiState.Error -> lightError.copy(alpha = 0.1f)
            }
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when (installState) {
                            is InstallUiState.Installing -> lightSurface06
                            is InstallUiState.Success -> lightSuccess.copy(alpha = 0.2f)
                            is InstallUiState.Error -> lightError.copy(alpha = 0.2f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (installState) {
                    is InstallUiState.Installing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = lightBrandNormal,
                            strokeWidth = 2.dp
                        )
                    }
                    is InstallUiState.Success -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = lightSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is InstallUiState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = lightError,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Status Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (installState) {
                        is InstallUiState.Installing -> "Installing..."
                        is InstallUiState.Success -> "Installation Complete"
                        is InstallUiState.Error -> "Installation Failed"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = lightTextPrimary,
                    letterSpacing = (-0.3).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (installState) {
                        is InstallUiState.Installing -> "Please wait while we set up OpenClaw"
                        is InstallUiState.Success -> "You're all set!"
                        is InstallUiState.Error -> "Tap Retry to try again"
                    },
                    fontSize = 13.sp,
                    color = lightTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun LogConsole(
    logs: List<String>,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier
) {
    // 当日志变化时自动滚动到底部
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

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
                    text = "Console Output",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = lightTextCaption
                )
                Text(
                    text = "${logs.size} lines",
                    fontSize = 12.sp,
                    color = lightTextCaption
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
                    logs.forEach { line ->
                        Text(
                            text = line,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                line.contains("ERROR") -> lightError
                                line.contains("COMPLETE") || line.contains("DONE") -> lightSuccess
                                line.contains("STEP") -> lightBrandNormal
                                else -> Color(0xFFE0E0E0)
                            },
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallingButton() {
    Button(
        onClick = { },
        enabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = lightBrandNormal.copy(alpha = 0.4f)
            ),
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = lightBrandNormal.copy(alpha = 0.6f),
            disabledContentColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = Color.White,
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Installing...",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            letterSpacing = 0.5.sp
        )
    }
}
