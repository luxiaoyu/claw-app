package com.moonshot.kimiclaw.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.shared.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Dashboard 页面的 ViewModel
 * 管理 Logcat 日志等业务逻辑
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val LOG_TAG = "DashboardViewModel"
        private const val MAX_LOG_LINES = 500
        private const val REFRESH_INTERVAL_MS = 2000L
    }

    // Logcat 日志列表
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    // 是否正在加载
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    // 是否自动刷新
    private val _isAutoRefresh = MutableStateFlow(true)
    val isAutoRefresh: StateFlow<Boolean> = _isAutoRefresh

    // 自动刷新任务
    private var autoRefreshJob: Job? = null

    // 应用包名，用于过滤日志
    private val appPackageName = "com.termux"

    /**
     * 初始化并加载日志
     */
    fun initialize() {
        Logger.logDebug(LOG_TAG, "Initializing DashboardViewModel")
        loadLogs()
        startAutoRefresh()
    }

    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
        Logger.logDebug(LOG_TAG, "DashboardViewModel cleared")
    }

    /**
     * 加载日志（一次性）
     */
    fun loadLogs() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = withContext(Dispatchers.IO) {
                readLogcat()
            }
            _logs.value = result
            _isLoading.value = false
        }
    }

    /**
     * 刷新日志
     */
    fun refreshLogs() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                readLogcat()
            }
            _logs.value = result
        }
    }

    /**
     * 清除日志
     */
    fun clearLogs() {
        _logs.value = emptyList()
        Logger.logInfo(LOG_TAG, "Logs cleared by user")
    }

    /**
     * 切换自动刷新状态
     */
    fun toggleAutoRefresh() {
        _isAutoRefresh.value = !_isAutoRefresh.value
        if (_isAutoRefresh.value) {
            startAutoRefresh()
        } else {
            stopAutoRefresh()
        }
    }

    /**
     * 设置自动刷新状态
     */
    fun setAutoRefresh(enabled: Boolean) {
        _isAutoRefresh.value = enabled
        if (enabled) {
            startAutoRefresh()
        } else {
            stopAutoRefresh()
        }
    }

    /**
     * 复制日志到剪贴板
     */
    fun copyLogsToClipboard(): Boolean {
        return try {
            val context = getApplication<Application>().applicationContext
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val logText = _logs.value.joinToString("\n")
            val clip = ClipData.newPlainText("Logcat", logText)
            clipboard.setPrimaryClip(clip)
            
            // 显示 Toast
            Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
            
            Logger.logInfo(LOG_TAG, "Logs copied to clipboard (${_logs.value.size} lines)")
            true
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Failed to copy logs: ${e.message}")
            false
        }
    }

    /**
     * 开始自动刷新
     */
    private fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) {
            return
        }
        
        autoRefreshJob = viewModelScope.launch {
            Logger.logDebug(LOG_TAG, "Starting auto refresh (interval: ${REFRESH_INTERVAL_MS}ms)")
            while (isActive && _isAutoRefresh.value) {
                delay(REFRESH_INTERVAL_MS)
                if (isActive && _isAutoRefresh.value) {
                    val result = withContext(Dispatchers.IO) {
                        readLogcat()
                    }
                    _logs.value = result
                }
            }
        }
    }

    /**
     * 停止自动刷新
     */
    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        Logger.logDebug(LOG_TAG, "Auto refresh stopped")
    }

    /**
     * 读取本应用的 logcat（在 IO 线程执行）
     * @return 日志列表
     */
    private fun readLogcat(): List<String> {
        return try {
            // 使用 -v threadtime 显示时间戳，-d 表示 dump 后退出
            val process = Runtime.getRuntime().exec("logcat -d -v threadtime")
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            val newLogs = mutableListOf<String>()
            var line: String?
            
            // 正则表达式用于移除 PID 和 TID
            // 格式: "02-13 14:30:45.123  1234  5678 I TagName: Message"
            // 变成: "02-13 14:30:45.123 I TagName: Message"
            val pidTidPattern = Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+)\s+\d+\s+\d+\s+(.+)$""")

            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    // 过滤包含应用包名的行（按 Application name/package 过滤）
                    if (it.contains(appPackageName)) {
                        // 移除 PID 和 TID
                        val simplifiedLine = pidTidPattern.replace(it, "$1 $2")
                        newLogs.add(simplifiedLine)
                    }
                }
            }

            reader.close()

            // 返回最近 500 行，如果没有则返回提示信息
            if (newLogs.isEmpty()) {
                listOf("No logs found. Waiting for new log entries...")
            } else {
                newLogs.takeLast(MAX_LOG_LINES)
            }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Error reading logcat: ${e.message}")
            listOf(
                "Error reading logcat: ${e.message}",
                "Exception type: ${e.javaClass.simpleName}"
            )
        }
    }
}
