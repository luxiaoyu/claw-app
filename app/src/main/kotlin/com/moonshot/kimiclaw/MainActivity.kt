package com.moonshot.kimiclaw

import com.termux.app.TermuxActivity
import com.termux.app.TermuxSetup
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.moonshot.kimiclaw.installer.OpenClawInstaller
import com.moonshot.kimiclaw.installer.installSuspend
import com.moonshot.kimiclaw.theme.lightMainSurface
import com.termux.shared.logger.Logger
import com.moonshot.kimiclaw.ui.InstallScreen
import com.moonshot.kimiclaw.ui.InstallUiState
import com.moonshot.kimiclaw.ui.WelcomeScreen
import com.moonshot.kimiclaw.viewmodel.WelcomeViewModel

/**
 * 主屏幕状态
 */
private enum class MainScreen {
    WELCOME,      // 欢迎页面（权限检查）
    BOOTSTRAP,    // Bootstrap 安装中
    INSTALL,      // OpenClaw 安装页面
    TERMUX        // Termux 主界面
}

class MainActivity : ComponentActivity() {

    private val viewModel: WelcomeViewModel by viewModels()

    // 用于启动通知设置页面并接收返回结果
    private val notificationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onReturnFromNotificationSettings()
    }

    // 用于启动电池优化设置页面并接收返回结果
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onReturnFromBatteryOptimizationSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        // 收集 ViewModel 事件
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.openNotificationSettingsEvent.collect { intent ->
                        notificationSettingsLauncher.launch(intent)
                    }
                }
                launch {
                    viewModel.openBatteryOptimizationEvent.collect { intent ->
                        batteryOptimizationLauncher.launch(intent)
                    }
                }
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = lightMainSurface
                ) {
                    MainContent()
                }
            }
        }
    }

    @Composable
    private fun MainContent() {
        var currentScreen by remember { mutableStateOf(MainScreen.WELCOME) }
        val logs = remember { mutableStateListOf<String>() }
        var installState by remember { mutableStateOf<InstallUiState>(InstallUiState.Installing) }
        var bootstrapStatus by remember { mutableStateOf("") }

        when (currentScreen) {
            MainScreen.WELCOME -> {
                WelcomeScreen(
                    viewModel = viewModel,
                    onInstallClick = {
                        // 检查 bootstrap 是否已安装
                        if (TermuxSetup.isBootstrapInstalled()) {
                            Logger.logInfo("MainActivity", "Bootstrap already installed, proceeding to OpenClaw install")
                            currentScreen = MainScreen.INSTALL
                        } else {
                            Logger.logInfo("MainActivity", "Bootstrap not installed, starting setup")
                            currentScreen = MainScreen.BOOTSTRAP
                        }
                    },
                    onCheckUpgrade = {
                        // Handle check upgrade
                    }
                )
            }

            MainScreen.BOOTSTRAP -> {
                // 显示 bootstrap 安装中状态
                LaunchedEffect(Unit) {
                    TermuxSetup.setupBootstrapIfNeeded(
                        activity = this@MainActivity,
                        onComplete = {
                            // Bootstrap 安装完成，继续安装 OpenClaw
                            currentScreen = MainScreen.INSTALL
                        },
                        onStatusUpdate = { status ->
                            bootstrapStatus = status
                        }
                    )
                }
                
                // 简单的加载界面
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = bootstrapStatus,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }

            MainScreen.INSTALL -> {
                // 启动安装流程
                LaunchedEffect(Unit) {
                    Logger.logInfo("MainActivity", "Install screen launched, starting installation...")
                    logs.clear()
                    installState = InstallUiState.Installing

                    try {
                        OpenClawInstaller.installSuspend(
                            context = this@MainActivity,
                            onLog = { line ->
                                logs.add(line)
                                if (logs.size > 500) {
                                    logs.removeAt(0)
                                }
                            },
                            onError = { message ->
                                Logger.logError("MainActivity", "Installation error: $message")
                                installState = InstallUiState.Error(message)
                            },
                            onComplete = {
                                Logger.logInfo("MainActivity", "Installation completed successfully")
                                installState = InstallUiState.Success
                            }
                        )
                    } catch (e: Exception) {
                        Logger.logError("MainActivity", "Installation failed with exception: ${e.message}")
                        installState = InstallUiState.Error("Installation failed: ${e.message}")
                    }
                }

                InstallScreen(
                    logs = logs,
                    installState = installState,
                    onRetry = {
                        // 重试安装
                        logs.clear()
                        installState = InstallUiState.Installing
                        lifecycleScope.launch {
                            OpenClawInstaller.installSuspend(
                                context = this@MainActivity,
                                onLog = { line ->
                                    logs.add(line)
                                    if (logs.size > 500) {
                                        logs.removeAt(0)
                                    }
                                },
                                onError = { message ->
                                    installState = InstallUiState.Error(message)
                                },
                                onComplete = {
                                    installState = InstallUiState.Success
                                }
                            )
                        }
                    },
                    onContinue = {
                        // 安装完成，进入 Termux
                        currentScreen = MainScreen.TERMUX
                    }
                )
            }

            MainScreen.TERMUX -> {
                // 跳转到 TermuxActivity
                LaunchedEffect(Unit) {
                    val intent = Intent(this@MainActivity, TermuxActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkNotificationPermission()
        viewModel.checkBatteryOptimizationStatus()
    }
}
