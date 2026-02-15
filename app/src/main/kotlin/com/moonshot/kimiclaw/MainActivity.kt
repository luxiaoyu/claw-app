package com.moonshot.kimiclaw

import com.termux.app.TermuxActivity
import com.termux.app.TermuxSetup
import com.termux.shared.logger.Logger
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.moonshot.kimiclaw.openclaw.OpenClawHelper
import com.moonshot.kimiclaw.ui.DashboardScreen
import com.moonshot.kimiclaw.ui.GatewayStatus
import com.moonshot.kimiclaw.ui.InstallScreen
import com.moonshot.kimiclaw.ui.LogcatScreen
import com.moonshot.kimiclaw.ui.VideoSplashScreen
import com.moonshot.kimiclaw.ui.WelcomeScreen
import com.moonshot.kimiclaw.ui.WebViewScreen
import com.moonshot.kimiclaw.ui.theme.lightMainSurface
import com.moonshot.kimiclaw.viewmodel.DashboardViewModel
import com.moonshot.kimiclaw.viewmodel.InstallViewModel
import com.moonshot.kimiclaw.viewmodel.MainViewModel
import com.moonshot.kimiclaw.viewmodel.WelcomeViewModel

/**
 * 主 Activity
 * 只负责 UI 渲染和系统级操作（Service 绑定、Activity 跳转等）
 * 业务逻辑在 ViewModel 中处理
 */
class MainActivity : ComponentActivity() {

    private val welcomeViewModel: WelcomeViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private val installViewModel: InstallViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()

    // KimiClawService 绑定
    private var kimiClawService: KimiClawService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as KimiClawService.LocalBinder
            kimiClawService = binder.getService()
            isServiceBound = true
            mainViewModel.setKimiClawService(kimiClawService, isServiceBound)
            Logger.logInfo("MainActivity", "KimiClawService connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            kimiClawService = null
            isServiceBound = false
            mainViewModel.setKimiClawService(null, false)
            Logger.logInfo("MainActivity", "KimiClawService disconnected")
        }
    }

    // Activity Result Launchers
    private val notificationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        welcomeViewModel.onReturnFromNotificationSettings()
    }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        welcomeViewModel.onReturnFromBatteryOptimizationSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        bindKimiClawService()
        collectViewModelEvents()

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

    override fun onDestroy() {
        super.onDestroy()
        // 取消正在进行的 Gateway 操作
        mainViewModel.cancelGatewayOperations()
        // 停止定时检查
        mainViewModel.stopGatewayStatusCheck()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    override fun onResume() {
        super.onResume()
        welcomeViewModel.checkNotificationPermission()
        welcomeViewModel.checkBatteryOptimizationStatus()
    }

    private fun bindKimiClawService() {
        KimiClawService.startService(this)
        val intent = Intent(this, KimiClawService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun collectViewModelEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // WelcomeViewModel 事件
                launch {
                    welcomeViewModel.openNotificationSettingsEvent.collect { intent ->
                        notificationSettingsLauncher.launch(intent)
                    }
                }
                launch {
                    welcomeViewModel.openBatteryOptimizationEvent.collect { intent ->
                        batteryOptimizationLauncher.launch(intent)
                    }
                }

                // MainViewModel 事件
                launch {
                    mainViewModel.navigateToTermuxEvent.collect {
                        navigateToTermux()
                    }
                }
                launch {
                    mainViewModel.serviceNotBoundError.collect { message ->
                        Logger.logError("MainActivity", message)
                        android.widget.Toast.makeText(this@MainActivity, "OpenClaw Gateway 启动失败: $message", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                launch {
                    mainViewModel.gatewayStartResult.collect { (success, errorMsg) ->
                        if (!success) {
                            val toastMsg = errorMsg ?: "未知错误"
                            android.widget.Toast.makeText(this@MainActivity, "OpenClaw Gateway 启动失败: $toastMsg", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }

                // InstallViewModel 事件
                launch {
                    installViewModel.installCompleteEvent.collect {
                        // 安装完成，跳转到 Dashboard
                        mainViewModel.openDashboard()
                    }
                }
            }
        }
    }

    private fun navigateToTermux() {
        val intent = Intent(this, TermuxActivity::class.java)
        startActivity(intent)
        finish()
    }

    @Composable
    private fun MainContent() {
        val currentScreen by mainViewModel.currentScreen.collectAsStateWithLifecycle()
        val isSplashOverlayVisible by mainViewModel.isSplashOverlayVisible.collectAsStateWithLifecycle()

        // 启动时检查 OpenClaw 安装状态，决定初始页面
        LaunchedEffect(Unit) {
            if (OpenClawHelper.isOpenclawInstalled()) {
                Logger.logInfo("MainActivity", "OpenClaw is installed, opening Dashboard")
                mainViewModel.openDashboard()
            } else {
                Logger.logInfo("MainActivity", "OpenClaw is not installed, showing Welcome screen")
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // 底层页面内容
            when (currentScreen) {
                MainViewModel.MainScreen.WELCOME -> WelcomeScreenContent()
                MainViewModel.MainScreen.DASHBOARD -> DashboardScreenContent()
                MainViewModel.MainScreen.BOOTSTRAP -> BootstrapScreenContent()
                MainViewModel.MainScreen.INSTALL -> InstallScreenContent()
                MainViewModel.MainScreen.LOGCAT -> LogcatScreenContent()
                MainViewModel.MainScreen.TERMUX -> TermuxScreenContent()
                MainViewModel.MainScreen.WEBVIEW_DASHBOARD -> WebViewDashboardScreenContent()
            }

            // 视频开屏覆盖层（显示在最上层）
            if (isSplashOverlayVisible) {
                SplashOverlayContent()
            }
        }
    }

    @Composable
    private fun SplashOverlayContent() {
        VideoSplashScreen(
            onVideoComplete = {
                // 视频播放完成后隐藏覆盖层，露出下面的页面
                mainViewModel.hideSplashOverlay()
            },
            minDisplayTimeMs = 2000L,  // 最少显示2秒
            autoAdvanceDelayMs = 500L   // 视频结束后延迟500ms隐藏
        )
    }

    @Composable
    private fun WelcomeScreenContent() {
        WelcomeScreen(
            viewModel = welcomeViewModel,
            onInstallClick = { mainViewModel.checkBootstrapAndNavigate() },
            onCheckUpgrade = { mainViewModel.checkUpgrade() },
            onOpenDashboard = { mainViewModel.openDashboard() }
        )
    }

    @Composable
    private fun DashboardScreenContent() {
        val isStartingGateway by mainViewModel.isStartingGateway.collectAsStateWithLifecycle()
        val isStoppingGateway by mainViewModel.isStoppingGateway.collectAsStateWithLifecycle()
        val isGatewayRunning by mainViewModel.isGatewayRunning.collectAsStateWithLifecycle()
        val gatewayUptime by mainViewModel.gatewayUptime.collectAsStateWithLifecycle()
        val sshAccess by mainViewModel.sshAccess.collectAsStateWithLifecycle()
        val isDebugCardVisible by mainViewModel.isDebugCardVisible.collectAsStateWithLifecycle()
        val channelsStatus by mainViewModel.channelsStatus.collectAsStateWithLifecycle()
        val isDashboardReady by mainViewModel.isDashboardReady.collectAsStateWithLifecycle()

        // 进入 Dashboard 时加载 SSH 信息和开始定时检查，离开时停止
        LaunchedEffect(Unit) {
            mainViewModel.loadSshInfo()
            mainViewModel.startGatewayStatusCheck()
        }

        DashboardScreen(
            gatewayStatus = if (isGatewayRunning) GatewayStatus.RUNNING else GatewayStatus.STOPPED,
            uptime = gatewayUptime,
            channelsStatus = channelsStatus,
            sshAccess = sshAccess,
            isStartingGateway = isStartingGateway,
            isStoppingGateway = isStoppingGateway,
            isDebugCardVisible = isDebugCardVisible,
            isDashboardReady = isDashboardReady,
            onCheckUpgrade = { mainViewModel.checkUpgrade() },
            onStartGateway = { mainViewModel.startGateway() },
            onStopGateway = { mainViewModel.stopGateway() },
            onToggleDebugCard = { mainViewModel.toggleDebugCard() },
            onViewLogs = { mainViewModel.viewLogs() },
            onOpenTerminal = { mainViewModel.navigateToTermux() },
            onReportIssue = { mainViewModel.reportIssue() },
            onOpenDashboard = { mainViewModel.openWebViewDashboard() }
        )
    }

    @Composable
    private fun BootstrapScreenContent() {
        val bootstrapStatus by installViewModel.bootstrapStatus.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            TermuxSetup.setupBootstrapIfNeeded(
                activity = this@MainActivity,
                onComplete = {
                    installViewModel.onBootstrapComplete()
                    // Bootstrap 完成后跳转到 INSTALL 页面
                    mainViewModel.onBootstrapInstallComplete()
                },
                onStatusUpdate = { status -> installViewModel.updateBootstrapStatus(status) }
            )
        }

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

    @Composable
    private fun InstallScreenContent() {
        val logs by installViewModel.installLogs.collectAsStateWithLifecycle()
        val installState by installViewModel.installState.collectAsStateWithLifecycle()

        // 首次进入页面时启动安装和显示视频
        LaunchedEffect(Unit) {
            // 先启动安装（在后台协程中运行）
            installViewModel.startOpenClawInstallation()
            // 延迟 2000ms 确保安装协程启动并运行后再显示视频
            kotlinx.coroutines.delay(2000)
            // 再显示视频覆盖层（视频播放期间安装在后台进行）
            mainViewModel.showSplashAtInstallStart()
        }

        InstallScreen(
            logs = logs,
            installState = installState,
            onRetry = {
                lifecycleScope.launch {
                    // 先隐藏当前视频
                    mainViewModel.hideSplashOverlay()
                    // 重置视频标记
                    mainViewModel.resetInstallSplashFlag()
                    // 重置安装状态并重新启动
                    installViewModel.resetInstallation()
                    installViewModel.startOpenClawInstallation()
                }
            },
            onContinue = {
                // 触发安装完成事件，通过事件回调跳转到 Dashboard
                installViewModel.onInstallContinue()
            }
        )
    }

    @Composable
    private fun TermuxScreenContent() {
        LaunchedEffect(Unit) {
            navigateToTermux()
        }
    }

    @Composable
    private fun LogcatScreenContent() {
        LogcatScreen(
            viewModel = dashboardViewModel,
            onBack = { mainViewModel.navigateBackToDashboard() }
        )
    }

    @Composable
    private fun WebViewDashboardScreenContent() {
        val isGatewayRunning by mainViewModel.isGatewayRunning.collectAsStateWithLifecycle()

        WebViewScreen(
            url = "http://127.0.0.1:18789/",
            onBack = { mainViewModel.navigateBackFromWebViewDashboard() },
            isGatewayRunning = isGatewayRunning
        )
    }

}
