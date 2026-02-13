package com.moonshot.kimiclaw.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moonshot.kimiclaw.ui.InstallUiState
import com.termux.shared.logger.Logger
import androidx.lifecycle.application
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.moonshot.kimiclaw.openclaw.OpenClawInstaller
import com.moonshot.kimiclaw.openclaw.installSuspend

/**
 * 安装页面的 ViewModel
 * 管理 Bootstrap 和 OpenClaw 的安装逻辑
 */
class InstallViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val LOG_TAG = "InstallViewModel"
        private const val MAX_LOG_LINES = 500
    }

    // Bootstrap 安装状态
    private val _bootstrapStatus = MutableStateFlow("")
    val bootstrapStatus: StateFlow<String> = _bootstrapStatus

    // OpenClaw 安装日志
    private val _installLogs = MutableStateFlow<List<String>>(emptyList())
    val installLogs: StateFlow<List<String>> = _installLogs

    // OpenClaw 安装状态
    private val _installState = MutableStateFlow<InstallUiState>(InstallUiState.Installing)
    val installState: StateFlow<InstallUiState> = _installState

    // 安装完成事件
    private val _installCompleteEvent = MutableSharedFlow<Unit>()
    val installCompleteEvent: SharedFlow<Unit> = _installCompleteEvent

    // ==================== Bootstrap 安装 ====================

    /**
     * 更新 Bootstrap 状态
     */
    fun updateBootstrapStatus(status: String) {
        _bootstrapStatus.value = status
    }

    /**
     * Bootstrap 安装完成
     */
    fun onBootstrapComplete() {
        viewModelScope.launch {
            _installCompleteEvent.emit(Unit)
        }
    }

    // ==================== OpenClaw 安装 ====================

    // 标记安装是否已经开始（防止 Activity 重建导致重复启动）
    private var isInstallationStarted = false

    /**
     * 开始 OpenClaw 安装（只会启动一次）
     */
    fun startOpenClawInstallation() {
        if (isInstallationStarted) {
            Logger.logInfo(LOG_TAG, "Installation already started, skipping...")
            return
        }
        isInstallationStarted = true
        
        viewModelScope.launch {
            Logger.logInfo(LOG_TAG, "Starting OpenClaw installation...")
            clearInstallLogs()
            _installState.value = InstallUiState.Installing
            executeOpenClawInstallation()
        }
    }

    /**
     * 重置安装状态（用于重试）
     */
    fun resetInstallation() {
        isInstallationStarted = false
        clearInstallLogs()
        _installState.value = InstallUiState.Installing
    }

    private suspend fun executeOpenClawInstallation() {
        try {
            OpenClawInstaller.installSuspend(
                context = application,
                onLog = { line -> addInstallLog(line) },
                onError = { message -> onInstallError(message) },
                onComplete = { onInstallComplete() }
            )
        } catch (e: Exception) {
            Logger.logError("MainActivity", "Installation failed with exception: ${e.message}")
            onInstallError("Installation failed: ${e.message}")
        }
    }

    /**
     * 添加安装日志
     */
    fun addInstallLog(line: String) {
        val currentLogs = _installLogs.value.toMutableList()
        currentLogs.add(line)
        if (currentLogs.size > MAX_LOG_LINES) {
            currentLogs.removeAt(0)
        }
        _installLogs.value = currentLogs
    }

    /**
     * 清空安装日志
     */
    fun clearInstallLogs() {
        _installLogs.value = emptyList()
    }

    /**
     * 安装出错
     */
    fun onInstallError(message: String) {
        Logger.logError(LOG_TAG, "Installation error: $message")
        _installState.value = InstallUiState.Error(message)
    }

    /**
     * 安装完成
     */
    fun onInstallComplete() {
        Logger.logInfo(LOG_TAG, "Installation completed successfully")
        _installState.value = InstallUiState.Success
    }

    /**
     * 重试安装
     */
    fun retryInstallation() {
        clearInstallLogs()
        _installState.value = InstallUiState.Installing
    }

    /**
     * 安装完成后继续
     */
    fun onInstallContinue() {
        viewModelScope.launch {
            _installCompleteEvent.emit(Unit)
        }
    }
}
