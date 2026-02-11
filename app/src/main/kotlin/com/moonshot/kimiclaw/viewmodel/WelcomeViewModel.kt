package com.moonshot.kimiclaw.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
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
 * @author : luxiaoyu@moonshot.cn
 * @date   : 2026/2/11 22:44
 */
class WelcomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val REQUEST_CODE_NOTIFICATION_SETTINGS = 1001
    }

    // 通知权限状态
    private val _notificationEnabled = MutableStateFlow(false)
    val notificationEnabled: StateFlow<Boolean> = _notificationEnabled

    // 打开通知设置的事件
    private val _openNotificationSettingsEvent = MutableSharedFlow<Intent>()
    val openNotificationSettingsEvent: SharedFlow<Intent> = _openNotificationSettingsEvent

    init {
        checkNotificationPermission()
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
}
