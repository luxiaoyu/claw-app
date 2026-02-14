package com.moonshot.kimiclaw.ui

import com.termux.shared.logger.Logger
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.moonshot.kimiclaw.openclaw.KimiClawWebView
import com.moonshot.kimiclaw.openclaw.OpenClawConfig
import com.moonshot.kimiclaw.ui.theme.lightBrandNormal
import com.moonshot.kimiclaw.ui.theme.lightMainSurface
import com.moonshot.kimiclaw.ui.theme.lightSurface06
import com.moonshot.kimiclaw.ui.theme.lightTextPrimary

/**
 * OpenClaw Dashboard WebView 页面
 * 用于在应用内显示 OpenClaw Web Dashboard (http://127.0.0.1:18789/)
 *
 * @param url 要加载的 URL，默认为 OpenClaw Dashboard 地址
 * @param onBack 返回回调
 * @param isGatewayRunning Gateway 是否正在运行，用于显示错误提示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String = "http://127.0.0.1:18789/",
    onBack: () -> Unit,
    isGatewayRunning: Boolean = true
) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("OpenClaw Dashboard") }
    var canGoBack by remember { mutableStateOf(false) }

    // WebView 控制器 - 必须在所有 UI 组件之前定义
    var kimiclawWebView: KimiClawWebView? by remember { mutableStateOf(null) }

    // 获取 Gateway Token
    val gatewayToken = remember { OpenClawConfig.getGatewayToken() }

    Logger.logDebug("WebViewScreen", "Gateway token available: ${gatewayToken != null}")

    // 拦截系统返回键，返回 Dashboard
    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "OpenClaw Dashboard",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = lightTextPrimary
                        )
                        if (pageTitle.isNotEmpty() && pageTitle != "OpenClaw Dashboard") {
                            Text(
                                text = pageTitle,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = lightTextPrimary.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (canGoBack) {
                                // 如果 WebView 可以返回，则返回上一页
                                // 注意：这里需要获取 kimiclawWebView 实例调用 goBack()
                                // 由于 Compose 的限制，我们在 factory 中处理
                            }
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = lightTextPrimary
                        )
                    }
                },
                actions = {
                    // 刷新按钮
                    IconButton(
                        onClick = {
                            hasError = false
                            errorMessage = ""
                            kimiclawWebView?.reload()
                        },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = if (isLoading) lightTextPrimary.copy(alpha = 0.3f) else lightTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = lightMainSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(lightSurface06)
        ) {

            AndroidView(
                factory = { context ->
                    kimiclawWebView = KimiClawWebView.create(
                        context = context,
                        url = url,
                        gatewayToken = gatewayToken,
                        callbacks = object : KimiClawWebView.WebViewCallbacks {
                            override fun onPageStarted() {
                                isLoading = true
                                hasError = false
                            }

                            override fun onPageFinished(title: String, canGoBackPage: Boolean) {
                                isLoading = false
                                pageTitle = title
                                canGoBack = canGoBackPage
                            }

                            override fun onTitleChanged(title: String) {
                                pageTitle = title
                            }

                            override fun onError(message: String) {
                                isLoading = false
                                hasError = true
                                errorMessage = message
                            }
                        }
                    )
                    kimiclawWebView!!.getWebView()
                },
                modifier = Modifier.fillMaxSize()
            )

            // 加载指示器
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(lightSurface06.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = lightBrandNormal,
                        strokeWidth = 4.dp
                    )
                }
            }

            // 错误提示
            if (hasError) {
                ErrorView(
                    message = errorMessage,
                    isGatewayRunning = isGatewayRunning,
                    hasToken = gatewayToken != null,
                    onRetry = {
                        hasError = false
                        errorMessage = ""
                        kimiclawWebView?.reload()
                    },
                    onBack = onBack
                )
            }
        }
    }
}

/**
 * 错误显示视图
 */
@Composable
private fun ErrorView(
    message: String,
    isGatewayRunning: Boolean,
    hasToken: Boolean = true,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(lightSurface06),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 错误图标
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(com.moonshot.kimiclaw.ui.theme.lightError.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(16.dp))

            // 错误标题
            Text(
                text = "无法加载 Dashboard",
                style = MaterialTheme.typography.titleMedium,
                color = lightTextPrimary
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))

            // 错误信息
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = lightTextPrimary.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // 如果 Gateway 未运行，显示额外提示
            if (!isGatewayRunning) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "提示: OpenClaw Gateway 当前未启动，请先启动 Gateway",
                    style = MaterialTheme.typography.bodySmall,
                    color = com.moonshot.kimiclaw.ui.theme.lightBrandNormal
                )
            }

            // 如果没有 token，显示提示
            if (!hasToken) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "提示: 未找到 Gateway Token，可能需要重新配置 OpenClaw",
                    style = MaterialTheme.typography.bodySmall,
                    color = com.moonshot.kimiclaw.ui.theme.lightError
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))

            // 操作按钮
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 重试按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(lightBrandNormal)
                        .clickable { onRetry() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "重试",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))

                // 返回按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(lightSurface06)
                        .clickable { onBack() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "返回",
                        color = lightTextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
