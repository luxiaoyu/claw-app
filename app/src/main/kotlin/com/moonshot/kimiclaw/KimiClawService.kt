package com.moonshot.kimiclaw

import com.termux.shared.logger.Logger
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.moonshot.kimiclaw.termux.ShellUtils
import com.moonshot.kimiclaw.termux.ShellUtils.DEFAULT_MAX_LOG_LINES
import com.moonshot.kimiclaw.termux.ShellUtils.TermuxEnvConfig
import com.moonshot.kimiclaw.termux.ShellUtils.createStandardTermuxEnvConfig

/**
 * KimiClaw 后台服务
 *
 * 基础保活策略：
 * 1. 前台服务（Foreground Service）+ 持久通知
 * 2. START_STICKY - 服务被杀后自动重启
 */
class KimiClawService : Service() {

    companion object {
        private const val LOG_TAG = "KimiClawService"
        private const val DEFAULT_TIMEOUT_MS = 60_000L
        private const val NOTIFICATION_CHANNEL_ID = "kimi_claw_service"
        private const val NOTIFICATION_ID = 1001

        /**
         * 启动服务的便捷方法
         */
        fun startService(context: Context) {
            val intent = Intent(context, KimiClawService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val binder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Service 级协程作用域
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("KimiClawService")
    )

    /**
     * 命令执行回调接口
     */
    fun interface CommandCallback {
        fun onResult(result: ShellUtils.ShellResult)
    }

    inner class LocalBinder : Binder() {
        fun getService(): KimiClawService = this@KimiClawService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Logger.logInfo(LOG_TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.logInfo(LOG_TAG, "onStartCommand, startId=$startId")
        startForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Logger.logInfo(LOG_TAG, "Service destroyed")
    }

    // ==================== 前台服务与通知 ====================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "KimiClaw 后台服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持 OpenClaw Gateway 在后台运行"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForeground() {
        val notification = buildNotification("KimiClaw 正在后台运行")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("KimiClaw")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateNotification(content: String) {
        val notification = buildNotification(content)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 执行 shell 命令
     * @param command 命令字符串
     * @param envConfig 环境变量配置
     * @param timeoutMs 超时时间（毫秒）
     * @param maxLogLines 最大日志行数
     * @param callback 结果回调（可选，默认空实现）
     * @return Job 协程任务，可用于取消
     */
    fun executeCommand(
        command: String,
        envConfig: TermuxEnvConfig = createStandardTermuxEnvConfig(),
        timeoutMs: Long = ShellUtils.DEFAULT_TIMEOUT_MS,
        maxLogLines: Int? = DEFAULT_MAX_LOG_LINES,
        callback: CommandCallback? = null
    ): Job {
        return serviceScope.launch {
            val result = ShellUtils.executeCommand(command, envConfig, timeoutMs, maxLogLines)

            Logger.logInfo(LOG_TAG, "Command: $command \n##########################\nexecuted: success=${result.success}, exitCode=${result.exitCode}")

            // 回调结果给调用方
            withContext(Dispatchers.Main) {
                callback?.onResult(result)
            }
        }
    }

}
