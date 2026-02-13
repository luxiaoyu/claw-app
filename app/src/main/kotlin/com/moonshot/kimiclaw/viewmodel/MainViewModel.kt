package com.moonshot.kimiclaw.viewmodel

import com.termux.app.TermuxSetup
import com.termux.shared.logger.Logger
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.moonshot.kimiclaw.KimiClawService
import com.moonshot.kimiclaw.openclaw.OpenClawConfig
import com.moonshot.kimiclaw.openclaw.OpenClawHelper
import com.moonshot.kimiclaw.termux.ShellUtils
import com.moonshot.kimiclaw.ui.ChannelsStatus
import com.moonshot.kimiclaw.ui.SshAccess

/**
 * MainActivity 的 ViewModel
 * 管理屏幕导航、Gateway 控制等业务逻辑（不包含安装逻辑）
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val LOG_TAG = "MainViewModel"
    }

    /**
     * 主屏幕状态
     */
    enum class MainScreen {
        WELCOME,      // 欢迎页面（权限检查）
        DASHBOARD,    // Dashboard 页面
        BOOTSTRAP,    // Bootstrap 安装中
        INSTALL,      // OpenClaw 安装页面
        LOGCAT,       // Logcat 日志页面
        TERMUX        // Termux 主界面
    }

    // 当前屏幕状态
    private val _currentScreen = MutableStateFlow(MainScreen.WELCOME)
    val currentScreen: StateFlow<MainScreen> = _currentScreen

    // Dashboard Gateway 状态
    private val _isStartingGateway = MutableStateFlow(false)
    val isStartingGateway: StateFlow<Boolean> = _isStartingGateway

    private val _isStoppingGateway = MutableStateFlow(false)
    val isStoppingGateway: StateFlow<Boolean> = _isStoppingGateway

    // Gateway 运行状态（用于刷新 Start/Stop 按钮）
    private val _isGatewayRunning = MutableStateFlow(false)
    val isGatewayRunning: StateFlow<Boolean> = _isGatewayRunning

    // Gateway 运行时间
    private val _gatewayUptime = MutableStateFlow("—")
    val gatewayUptime: StateFlow<String> = _gatewayUptime

    // SSH 访问信息
    private val _sshAccess = MutableStateFlow(SshAccess())
    val sshAccess: StateFlow<SshAccess> = _sshAccess

    // Channels 连接状态
    private val _channelsStatus = MutableStateFlow(ChannelsStatus())
    val channelsStatus: StateFlow<ChannelsStatus> = _channelsStatus

    // Dashboard Debug Card 显示状态（跨页面保持）
    private val _isDebugCardVisible = MutableStateFlow(false)
    val isDebugCardVisible: StateFlow<Boolean> = _isDebugCardVisible

    /**
     * 切换 Debug Card 显示状态
     */
    fun toggleDebugCard() {
        _isDebugCardVisible.value = !_isDebugCardVisible.value
    }

    // 定时检查 Gateway 状态的任务
    private var gatewayStatusCheckJob: Job? = null

    // Gateway 启动/停止任务（用于取消）
    private var gatewayStartJob: Job? = null
    private var gatewayStopJob: Job? = null

    // 导航事件
    private val _navigateToTermuxEvent = MutableSharedFlow<Unit>()
    val navigateToTermuxEvent: SharedFlow<Unit> = _navigateToTermuxEvent

    private val _navigateToInstallEvent = MutableSharedFlow<Unit>()
    val navigateToInstallEvent: SharedFlow<Unit> = _navigateToInstallEvent

    // 服务未绑定错误
    private val _serviceNotBoundError = MutableSharedFlow<String>()
    val serviceNotBoundError: SharedFlow<String> = _serviceNotBoundError

    // Gateway 启动结果
    private val _gatewayStartResult = MutableSharedFlow<Pair<Boolean, String?>>()
    val gatewayStartResult: SharedFlow<Pair<Boolean, String?>> = _gatewayStartResult

    // Gateway 停止结果
    private val _gatewayStopResult = MutableSharedFlow<Pair<Boolean, String?>>()
    val gatewayStopResult: SharedFlow<Pair<Boolean, String?>> = _gatewayStopResult

    // KimiClawService 实例（由 MainActivity 注入）
    private var kimiClawService: KimiClawService? = null
    private var isServiceBound: Boolean = false

    /**
     * 设置 KimiClawService 实例
     */
    fun setKimiClawService(service: KimiClawService?, bound: Boolean) {
        kimiClawService = service
        isServiceBound = bound
    }

    // ==================== 屏幕导航 ====================

    /**
     * 检查 Bootstrap 并决定跳转到哪个页面
     */
    fun checkBootstrapAndNavigate() {
        if (TermuxSetup.isBootstrapInstalled()) {
            Logger.logInfo(LOG_TAG, "Bootstrap already installed, proceeding to OpenClaw install")
            _currentScreen.value = MainScreen.INSTALL
        } else {
            Logger.logInfo(LOG_TAG, "Bootstrap not installed, starting setup")
            _currentScreen.value = MainScreen.BOOTSTRAP
        }
    }

    /**
     * Bootstrap 安装完成后进入安装页面
     */
    fun onBootstrapInstallComplete() {
        _currentScreen.value = MainScreen.INSTALL
    }

    /**
     * OpenClaw 安装完成后进入 Dashboard
     */
    fun onOpenClawInstallComplete() {
        openDashboard()
    }

    /**
     * 打开 Dashboard
     */
    fun openDashboard() {
        _currentScreen.value = MainScreen.DASHBOARD
    }

    /**
     * 导航到 Termux
     */
    fun navigateToTermux() {
        viewModelScope.launch {
            _navigateToTermuxEvent.emit(Unit)
        }
    }

    // ==================== Gateway 控制 ====================

    /**
     * 启动 Gateway
     */
    fun startGateway() {
        if (OpenClawConfig.hasAnyChannelConfig().not()) {
            OpenClawConfig.writeDefaultChannelConfig()
            Logger.logInfo(LOG_TAG, "No channel config found, wrote default config")
        }

        // 检查是否已有正在进行的启动任务
        if (gatewayStartJob?.isActive == true) {
            Logger.logWarn(LOG_TAG, "Gateway start already in progress")
            return
        }

        // 检查服务状态
        if (!isServiceBound || kimiClawService == null) {
            Logger.logError(LOG_TAG, "KimiClawService not bound, cannot start gateway")
            viewModelScope.launch {
                _serviceNotBoundError.emit("服务未连接，请稍后重试")
            }
            return
        }

        val context = getApplication<Application>().applicationContext
        val service = kimiClawService!!

        // 保存Job引用以便取消
        gatewayStartJob = OpenClawHelper.startGateway(
            context = context,
            service = service,
            onStart = { _isStartingGateway.value = true },
            onResult = { success, errorMsg ->
                // 先刷新状态确保UI同步，再重置isStarting
                refreshGatewayStatus()
                _isStartingGateway.value = false
                viewModelScope.launch {
                    _gatewayStartResult.emit(Pair(success, errorMsg))
                }
                gatewayStartJob = null
            }
        )
    }

    /**
     * 停止 Gateway
     */
    fun stopGateway() {
        // 检查是否已有正在进行的停止任务
        if (gatewayStopJob?.isActive == true) {
            Logger.logWarn(LOG_TAG, "Gateway stop already in progress")
            return
        }

        // 检查服务状态
        if (!isServiceBound || kimiClawService == null) {
            Logger.logError(LOG_TAG, "KimiClawService not bound, cannot stop gateway")
            viewModelScope.launch {
                _serviceNotBoundError.emit("服务未连接，请稍后重试")
            }
            return
        }

        val service = kimiClawService!!

        // 保存Job引用以便取消
        gatewayStopJob = OpenClawHelper.stopGateway(
            service = service,
            onStart = { _isStoppingGateway.value = true },
            onResult = { success, errorMsg ->
                // 先刷新状态确保UI同步，再重置isStopping
                refreshGatewayStatus()
                _isStoppingGateway.value = false
                viewModelScope.launch {
                    _gatewayStopResult.emit(Pair(success, errorMsg))
                }
                gatewayStopJob = null
            }
        )
    }

    /**
     * 取消正在进行的 Gateway 操作
     */
    fun cancelGatewayOperations() {
        gatewayStartJob?.cancel()
        gatewayStartJob = null
        gatewayStopJob?.cancel()
        gatewayStopJob = null
        _isStartingGateway.value = false
        _isStoppingGateway.value = false
    }

    // ==================== 检查升级（占位）====================

    fun checkUpgrade() {
        // TODO: 实现检查升级逻辑
        Logger.logInfo(LOG_TAG, "Check upgrade clicked")
    }

    // ==================== 查看日志 ====================

    fun viewLogs() {
        Logger.logInfo(LOG_TAG, "View logs clicked, navigating to LOGCAT screen")
        _currentScreen.value = MainScreen.LOGCAT
    }

    /**
     * 返回 Dashboard
     */
    fun navigateBackToDashboard() {
        _currentScreen.value = MainScreen.DASHBOARD
    }

    // ==================== 上报问题（占位）====================

    fun reportIssue() {
        // TODO: 实现上报问题逻辑
        Logger.logInfo(LOG_TAG, "Report issue clicked")
    }

    // ==================== SSH 信息 ====================

    /**
     * 加载 SSH 连接信息
     */
    fun loadSshInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val sshInfo = ShellUtils.getDeviceNetworkInfo()
            _sshAccess.value = SshAccess(
                ip = sshInfo.host,
                port = sshInfo.port,
                password = sshInfo.password ?: "<not set>"
            )
            Logger.logDebug(LOG_TAG, "SSH info loaded: ${sshInfo.host}:${sshInfo.port}")
        }
    }

    // ==================== Gateway 状态检查 ====================

    /**
     * 开始定时检查 Gateway 状态（每5秒）
     */
    fun startGatewayStatusCheck() {
        if (gatewayStatusCheckJob != null) {
            return // 已经在检查中
        }

        gatewayStatusCheckJob = viewModelScope.launch {
            Logger.logInfo(LOG_TAG, "Starting Gateway status check (every 5s)")

            while (isActive) {
                refreshGatewayStatus()
                delay(5_000L) // 每5秒检查一次
            }
        }
    }

    /**
     * 停止定时检查 Gateway 状态
     */
    fun stopGatewayStatusCheck() {
        gatewayStatusCheckJob?.cancel()
        gatewayStatusCheckJob = null
        Logger.logInfo(LOG_TAG, "Stopped Gateway status check")
    }

    /**
     * 立即刷新一次 Gateway 状态
     */
    fun refreshGatewayStatus() {
        OpenClawHelper.isGatewayRunning(kimiClawService) { running, _ ->
            _isGatewayRunning.value = running
            Logger.logDebug(LOG_TAG, "Gateway running status: $running")

            // 如果正在运行，获取运行时间
            if (running) {
                refreshGatewayUptime()
            } else {
                _gatewayUptime.value = "—"
            }
        }

        // 刷新 Channels 状态
        refreshChannelsStatus()
    }

    /**
     * 刷新 Channels 连接状态
     */
    private fun refreshChannelsStatus() {
        val status = OpenClawConfig.getChannelsStatus()
        _channelsStatus.value = status
        Logger.logDebug(LOG_TAG, "Channels status refreshed: telegram=${status.telegramConnected}, discord=${status.discordConnected}, feishu=${status.feishuConnected}")
    }

    /**
     * 刷新 Gateway 运行时间
     */
    private fun refreshGatewayUptime() {
        OpenClawHelper.getGatewayUptime(kimiClawService) { uptime ->
            _gatewayUptime.value = uptime
        }
    }
}
