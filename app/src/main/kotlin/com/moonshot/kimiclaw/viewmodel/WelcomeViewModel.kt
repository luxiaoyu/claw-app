package com.moonshot.kimiclaw.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * @desc   : Welcome页面ViewModel，管理权限状态
 */
class WelcomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val REQUEST_CODE_NOTIFICATION_SETTINGS = 1001
        const val REQUEST_CODE_BATTERY_OPTIMIZATION = 1002
    }

    // 通知权限状态
    private val _notificationEnabled = MutableStateFlow(false)
    val notificationEnabled: StateFlow<Boolean> = _notificationEnabled

    // 电池优化豁免状态（后台运行权限）
    private val _batteryOptimizationEnabled = MutableStateFlow(false)
    val batteryOptimizationEnabled: StateFlow<Boolean> = _batteryOptimizationEnabled

    // 打开通知设置的事件
    private val _openNotificationSettingsEvent = MutableSharedFlow<Intent>()
    val openNotificationSettingsEvent: SharedFlow<Intent> = _openNotificationSettingsEvent

    // 打开电池优化设置的事件
    private val _openBatteryOptimizationEvent = MutableSharedFlow<Intent>()
    val openBatteryOptimizationEvent: SharedFlow<Intent> = _openBatteryOptimizationEvent

    init {
        checkNotificationPermission()
        checkBatteryOptimizationStatus()
    }

    /**
     * 检查通知权限状态
     */
    fun checkNotificationPermission() {
        val context = getApplication<Application>().applicationContext
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        _notificationEnabled.value = enabled
    }

    /**
     * 点击通知权限卡片，触发打开通知设置页面
     */
    fun onNotificationPermissionClick() {
        if (_notificationEnabled.value) {
            return // 已启用，无需操作
        }
        viewModelScope.launch {
            val intent = createNotificationSettingsIntent()
            _openNotificationSettingsEvent.emit(intent)
        }
    }

    /**
     * 创建打开通知设置的 Intent
     */
    private fun createNotificationSettingsIntent(): Intent {
        val context = getApplication<Application>().applicationContext
        val intent = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
            intent.putExtra("app_package", context.packageName)
            intent.putExtra("app_uid", context.applicationInfo.uid)
        }
        return intent
    }

    /**
     * 从通知设置页面返回时调用
     */
    fun onReturnFromNotificationSettings() {
        checkNotificationPermission()
    }

    /**
     * 检查电池优化豁免状态
     */
    fun checkBatteryOptimizationStatus() {
        val context = getApplication<Application>().applicationContext
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        // isIgnoringBatteryOptimizations 返回 true 表示已豁免（后台运行权限已获取）
        val isIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true // Android 6.0 以下默认允许
        }
        _batteryOptimizationEnabled.value = isIgnoring
    }

    /**
     * 点击后台运行权限卡片，触发打开电池优化设置页面
     */
    fun onBatteryOptimizationClick() {
        if (_batteryOptimizationEnabled.value) {
            return // 已启用，无需操作
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            viewModelScope.launch {
                val intent = createBatteryOptimizationIntent()
                _openBatteryOptimizationEvent.emit(intent)
            }
        } else {
            // Android 6.0 以下无需申请，直接设为 true
            _batteryOptimizationEnabled.value = true
        }
    }

    /**
     * 创建请求电池优化豁免的 Intent
     */
    private fun createBatteryOptimizationIntent(): Intent {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:${context.packageName}")
        return intent
    }

    /**
     * 从电池优化设置页面返回时调用
     */
    fun onReturnFromBatteryOptimizationSettings() {
        checkBatteryOptimizationStatus()
    }
}
